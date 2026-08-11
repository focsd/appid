#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck disable=SC1091
source "$SCRIPT_DIR/release_lib.sh"

usage() {
    printf 'Usage: %s [--ref vVERSION] [--rebuild-image]\n' "$(basename "$0")" >&2
    exit 2
}

git_ref=
rebuild_image=false
while [ "$#" -gt 0 ]; do
    case "$1" in
        --ref) [ "$#" -ge 2 ] || usage; git_ref=$2; shift 2 ;;
        --rebuild-image) rebuild_image=true; shift ;;
        *) usage ;;
    esac
done

repo_root=$(release_repo_root)
cd "$repo_root"
release_require_tool docker
release_require_clean_tree

version_name=$(release_read_version_name "$repo_root/app/build.gradle")
version_code=$(release_read_version_code "$repo_root/app/build.gradle")
[ -n "$git_ref" ] || git_ref="v$version_name"
[ "$git_ref" = "v$version_name" ] \
    || release_die "$git_ref does not match versionName $version_name"
git rev-parse --verify --quiet "$git_ref^{commit}" >/dev/null \
    || release_die "local tag not found: $git_ref"
remote_commit=$(git ls-remote origin "refs/tags/$git_ref^{}" | awk 'NR == 1 { print $1 }')
if [ -z "$remote_commit" ]; then
    remote_commit=$(git ls-remote origin "refs/tags/$git_ref" | awk 'NR == 1 { print $1 }')
fi
[ -n "$remote_commit" ] || release_die "$git_ref is not published on origin"
[ "$remote_commit" = "$(git rev-list -n 1 "$git_ref")" ] \
    || release_die "local and origin $git_ref do not resolve to the same commit"

artifact_apk="$repo_root/artifacts/AppId-v$version_name-$version_code-unsigned.apk"
[ -s "$artifact_apk" ] \
    || release_die "run ./scripts/verify_fdroid_release.sh --ref $git_ref first"
release_require_artifact_provenance "$artifact_apk" "$remote_commit"
local_hash=$(release_sha256 "$artifact_apk")

docker_image=${FDROID_RELEASE_IMAGE:-appid/fdroid-release:2.4.2-android35}
if [ "$rebuild_image" = true ] || ! docker image inspect "$docker_image" >/dev/null 2>&1; then
    docker build --platform linux/amd64 \
        --file "$repo_root/docker/fdroid/Dockerfile" \
        --tag "$docker_image" "$repo_root/docker/fdroid"
fi

temp_root=$(mktemp -d "${TMPDIR:-/tmp}/appid-fdroid.XXXXXX")
cleanup() {
    rm -rf "$temp_root"
}
trap cleanup EXIT INT TERM
mkdir -p "$temp_root/metadata" "$temp_root/config"

FDROID_SOURCE_COMMIT=$remote_commit \
    ./scripts/render_fdroid_metadata.sh >"$temp_root/metadata/com.focsd.appid.yml"
cp "$repo_root/docker/fdroid/categories.yml" "$temp_root/config/categories.yml"
printf 'sdk_path: /opt/android-sdk\n' >"$temp_root/config.yml"

docker run --rm --platform linux/amd64 \
    --volume "$temp_root:/build" \
    --workdir /build \
    "$docker_image" \
    /bin/bash -c "set -euo pipefail
        fdroid readmeta
        fdroid rewritemeta com.focsd.appid
        fdroid lint com.focsd.appid
        git clone --no-checkout https://github.com/focsd/appid.git build/com.focsd.appid
        fdroid build --verbose --test --no-tarball com.focsd.appid:$version_code"

docker_apk="$temp_root/tmp/com.focsd.appid_${version_code}.apk"
[ -s "$docker_apk" ] || release_die "F-Droid APK not found: $docker_apk"
docker_hash=$(release_sha256 "$docker_apk")
[ "$docker_hash" = "$local_hash" ] \
    || release_die "Docker/F-Droid APK differs: $docker_hash != $local_hash"

cp "$docker_apk" "$repo_root/artifacts/AppId-v$version_name-$version_code-fdroid.apk"
printf 'Pinned Docker F-Droid build passed.\nTag: %s\nSHA-256: %s\n' "$git_ref" "$docker_hash"
