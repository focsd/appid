#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck disable=SC1091
source "$SCRIPT_DIR/release_lib.sh"

usage() {
    printf 'Usage: %s [--keystore PATH] [--alias ALIAS]\n' "$(basename "$0")" >&2
    exit 2
}

keystore=
key_alias=appid
while [ "$#" -gt 0 ]; do
    case "$1" in
        --keystore) [ "$#" -ge 2 ] || usage; keystore=$2; shift 2 ;;
        --alias) [ "$#" -ge 2 ] || usage; key_alias=$2; shift 2 ;;
        *) usage ;;
    esac
done

repo_root=$(release_repo_root)
cd "$repo_root"
release_require_tool gh
release_require_tool keytool
release_require_tool docker
release_require_clean_tree

version_name=$(release_read_version_name "$repo_root/app/build.gradle")
version_code=$(release_read_version_code "$repo_root/app/build.gradle")
tag="v$version_name"
changelog="$repo_root/fastlane/metadata/android/en-US/changelogs/$version_code.txt"
release_require_changelog "$repo_root" "$version_code"

if [ -z "$keystore" ]; then
    default_keystore="$HOME/.config/appid/appid-release.jks"
    read -r -p "Publisher keystore [$default_keystore]: " keystore_input
    keystore=${keystore_input:-$default_keystore}
fi
[ -f "$keystore" ] || release_die "keystore not found: $keystore"

read -r -p "Publisher key alias [$key_alias]: " alias_input
key_alias=${alias_input:-$key_alias}

printf 'Release: AppId %s (%s), tag %s\n' "$version_name" "$version_code" "$tag"
printf 'Keystore: %s\n' "$keystore"
read -r -s -p 'Keystore password: ' FOCSD_APPID_STORE_PASSWORD
printf '\n'
read -r -s -p 'Key password: ' FOCSD_APPID_KEY_PASSWORD
printf '\n'
export FOCSD_APPID_KEYSTORE="$keystore"
export FOCSD_APPID_KEY_ALIAS="$key_alias"
export FOCSD_APPID_STORE_PASSWORD
export FOCSD_APPID_KEY_PASSWORD

cleanup() {
    [ -z "${secret_env:-}" ] || rm -f "$secret_env"
    unset FOCSD_APPID_KEYSTORE FOCSD_APPID_KEY_ALIAS \
        FOCSD_APPID_STORE_PASSWORD FOCSD_APPID_KEY_PASSWORD
}
trap cleanup EXIT INT TERM

expected_fingerprint=8c54543c74d42f5cc96b24027c30d89f7e38f8595f4056c8976b253fdfa603a8
certificate_fingerprint=$(keytool -list -v \
    -keystore "$FOCSD_APPID_KEYSTORE" \
    -alias "$FOCSD_APPID_KEY_ALIAS" \
    -storepass:env FOCSD_APPID_STORE_PASSWORD 2>/dev/null \
    | sed -nE 's/^[[:space:]]*SHA-?256:[[:space:]]*//p' \
    | tr -d ':' | tr '[:upper:]' '[:lower:]')
[ "$certificate_fingerprint" = "$expected_fingerprint" ] \
    || release_die "keystore certificate fingerprint does not match the configured publisher key (detected: ${certificate_fingerprint:-unavailable})"
printf 'Certificate fingerprint verified.\n'

git rev-parse --verify --quiet "$tag^{commit}" >/dev/null \
    || release_die "local tag not found: $tag"
remote_commit=$(git ls-remote origin "refs/tags/$tag^{}" | awk 'NR == 1 { print $1 }')
[ -n "$remote_commit" ] || release_die "$tag is not published on origin"
[ "$remote_commit" = "$(git rev-list -n 1 "$tag")" ] \
    || release_die "local and origin $tag do not resolve to the same commit"

./scripts/verify_fdroid_release.sh --ref "$tag"

fdroid_image=${FDROID_RELEASE_IMAGE:-appid/fdroid-release:2.4.2-android35}
if ! docker image inspect "$fdroid_image" >/dev/null 2>&1; then
    docker build --platform linux/amd64 \
        --file "$repo_root/docker/fdroid/Dockerfile" \
        --tag "$fdroid_image" "$repo_root/docker/fdroid"
fi

umask 077
secret_env=$(mktemp "${TMPDIR:-/tmp}/appid-publisher-env.XXXXXX")
printf 'FOCSD_APPID_KEYSTORE=/run/secrets/appid-release.jks\n' >"$secret_env"
{
    printf 'FOCSD_APPID_KEY_ALIAS=%s\n' "$FOCSD_APPID_KEY_ALIAS"
    printf 'FOCSD_APPID_STORE_PASSWORD=%s\n' "$FOCSD_APPID_STORE_PASSWORD"
    printf 'FOCSD_APPID_KEY_PASSWORD=%s\n' "$FOCSD_APPID_KEY_PASSWORD"
} >>"$secret_env"

docker run --rm --platform linux/amd64 \
    --volume "$repo_root:/workspace" \
    --volume "$keystore:/run/secrets/appid-release.jks:ro" \
    --env-file "$secret_env" \
    --workdir /workspace \
    "$fdroid_image" \
    /bin/bash -c 'set -euo pipefail
        ./gradlew --no-daemon clean lintRelease testDebugUnitTest assemblePublisherRelease'

publisher_apk="$repo_root/app/build/outputs/apk/release/app-release.apk"
[ -s "$publisher_apk" ] || release_die "publisher APK not found: $publisher_apk"
artifact_dir="$repo_root/artifacts"
mkdir -p "$artifact_dir"
release_asset="$artifact_dir/AppId-v$version_name.apk"
cp "$publisher_apk" "$release_asset"

release_setup_android_sdk
apksigner="$ANDROID_SDK_ROOT/build-tools/35.0.0/apksigner"
[ -x "$apksigner" ] || release_die "apksigner not found: $apksigner"
"$apksigner" verify "$release_asset" >/dev/null \
    || release_die "publisher APK signature verification failed"

if gh release view "$tag" >/dev/null 2>&1; then
    if gh release view "$tag" --json assets --jq '.assets[].name' \
        | grep -Fxq "$(basename "$release_asset")"; then
        read -r -p "GitHub asset already exists. Replace it? [y/N] " replace_asset
        case "$replace_asset" in
            y|Y|yes|YES) ;;
            *) release_die 'refusing to replace the existing GitHub asset' ;;
        esac
    fi
    gh release upload "$tag" "$release_asset" --clobber
else
    gh release create "$tag" "$release_asset" \
        --title "AppId $version_name" \
        --notes-file "$changelog"
fi

./scripts/verify_fdroid_docker.sh --ref "$tag"
FDROID_SOURCE_COMMIT="$remote_commit" \
    ./scripts/render_fdroid_metadata.sh >"$artifact_dir/com.focsd.appid.yml"

printf '\nF-Droid publishing preparation complete.\n'
printf 'GitHub release: https://github.com/focsd/appid/releases/tag/%s\n' "$tag"
printf 'fdroiddata candidate: %s\n' "$artifact_dir/com.focsd.appid.yml"
