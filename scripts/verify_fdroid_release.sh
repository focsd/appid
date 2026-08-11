#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck disable=SC1091
source "$SCRIPT_DIR/release_lib.sh"

usage() {
    printf 'Usage: %s [--ref GIT_REF] [--metadata-only]\n' "$(basename "$0")" >&2
    exit 2
}

git_ref=
metadata_only=false
while [ "$#" -gt 0 ]; do
    case "$1" in
        --ref) [ "$#" -ge 2 ] || usage; git_ref=$2; shift 2 ;;
        --metadata-only) metadata_only=true; shift ;;
        *) usage ;;
    esac
done

repo_root=$(release_repo_root)
work_root=$repo_root
temp_root=
first_apk=
source_commit=$(git -C "$repo_root" rev-parse HEAD)
source_dirty=false
cleanup() {
    [ -z "$temp_root" ] || rm -rf "$temp_root"
    [ -z "$first_apk" ] || rm -f "$first_apk"
}
trap cleanup EXIT INT TERM

if [ -n "$git_ref" ]; then
    release_require_tool git
    git -C "$repo_root" rev-parse --verify --quiet "$git_ref^{commit}" >/dev/null \
        || release_die "unknown Git ref: $git_ref"
    temp_root=$(mktemp -d "${TMPDIR:-/tmp}/appid-release.XXXXXX")
    git -C "$repo_root" archive "$git_ref" | tar -x -C "$temp_root"
    work_root=$temp_root
    source_commit=$(git -C "$repo_root" rev-parse "$git_ref^{commit}")
else
    [ -z "$(git -C "$repo_root" status --porcelain --untracked-files=all)" ] || source_dirty=true
fi

version_name=$(release_read_version_name "$work_root/app/build.gradle")
version_code=$(release_read_version_code "$work_root/app/build.gradle")
release_validate_version_name "$version_name"
release_validate_version_code "$version_code"
release_require_changelog "$work_root" "$version_code"

if [ -n "$git_ref" ] && printf '%s' "$git_ref" | grep -q '^v'; then
    [ "$git_ref" = "v$version_name" ] \
        || release_die "tag $git_ref does not match versionName $version_name"
fi

(cd "$work_root" && FDROID_SOURCE_COMMIT=$source_commit ./scripts/render_fdroid_metadata.sh >/dev/null)
printf 'Release metadata: AppId %s (%s), tag v%s\n' "$version_name" "$version_code" "$version_name"

[ "$metadata_only" = false ] || exit 0

release_require_tool shellcheck
release_require_tool unzip
release_setup_java
release_setup_android_sdk

(
    cd "$work_root"
    ./scripts/check_termux_scripts.sh
    bash -n app/src/main/assets/*.sh scripts/*.sh termux/*.sh
    shellcheck app/src/main/assets/*.sh scripts/*.sh termux/*.sh

    env -u FOCSD_APPID_KEYSTORE \
        -u FOCSD_APPID_KEY_ALIAS \
        -u FOCSD_APPID_STORE_PASSWORD \
        -u FOCSD_APPID_KEY_PASSWORD \
        ./gradlew --no-daemon clean testDebugUnitTest lintDebug lintRelease assembleRelease
)

apk="$work_root/app/build/outputs/apk/release/app-release-unsigned.apk"
[ -s "$apk" ] || release_die "unsigned release APK not found: $apk"
if unzip -Z1 "$apk" | grep -Eq '^META-INF/[^/]+\.(RSA|DSA|EC|SF)$'; then
    release_die 'release APK unexpectedly contains a signing certificate'
fi

first_apk=$(mktemp "${TMPDIR:-/tmp}/appid-first.apk.XXXXXX")
cp "$apk" "$first_apk"
first_hash=$(release_sha256 "$first_apk")

(
    cd "$work_root"
    env -u FOCSD_APPID_KEYSTORE \
        -u FOCSD_APPID_KEY_ALIAS \
        -u FOCSD_APPID_STORE_PASSWORD \
        -u FOCSD_APPID_KEY_PASSWORD \
        ./gradlew --no-daemon clean assembleRelease
)
second_hash=$(release_sha256 "$apk")
[ "$first_hash" = "$second_hash" ] \
    || release_die "APK is not deterministic: $first_hash != $second_hash"

artifact_dir="$repo_root/artifacts"
mkdir -p "$artifact_dir"
artifact_apk="$artifact_dir/AppId-v$version_name-$version_code-unsigned.apk"
cp "$apk" "$artifact_apk"
printf '%s  %s\n' "$second_hash" "$(basename "$artifact_apk")" >"$artifact_apk.sha256"
printf 'commit=%s\ndirty=%s\nversionName=%s\nversionCode=%s\nsha256=%s\n' \
    "$source_commit" "$source_dirty" "$version_name" "$version_code" "$second_hash" \
    >"$artifact_apk.provenance"
rm -f "$first_apk"
first_apk=

printf 'Release gate passed twice with identical APK bytes.\n'
printf 'APK: %s\nSHA-256: %s\n' "$artifact_apk" "$second_hash"
[ "$source_dirty" = false ] \
    || printf 'Note: this artifact records a dirty source tree and cannot be tagged.\n'
