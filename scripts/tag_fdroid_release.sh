#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck disable=SC1091
source "$SCRIPT_DIR/release_lib.sh"

mode=dry-run
case "${1:-}" in
    '') ;;
    --dry-run) mode=dry-run ;;
    --create) mode=create ;;
    --push) mode=push ;;
    *) printf 'Usage: %s [--dry-run|--create|--push]\n' "$(basename "$0")" >&2; exit 2 ;;
esac

repo_root=$(release_repo_root)
cd "$repo_root"
release_require_clean_tree

[ "$(git branch --show-current)" = main ] || release_die 'release tags must be created from main'
origin_url=$(git remote get-url origin)
printf '%s\n' "$origin_url" | grep -Eq 'github\.com[:/]focsd/appid(\.git)?$' \
    || release_die "origin is not the canonical AppId repository: $origin_url"

version_name=$(release_read_version_name "$repo_root/app/build.gradle")
version_code=$(release_read_version_code "$repo_root/app/build.gradle")
release_validate_version_name "$version_name"
release_validate_version_code "$version_code"
release_require_changelog "$repo_root" "$version_code"
tag="v$version_name"
artifact_apk="$repo_root/artifacts/AppId-v$version_name-$version_code-unsigned.apk"
artifact_hash="$artifact_apk.sha256"
[ -s "$artifact_apk" ] || release_die "run ./scripts/verify_fdroid_release.sh first; missing $artifact_apk"
[ -s "$artifact_hash" ] || release_die "release checksum is missing: $artifact_hash"
expected_hash=$(awk 'NR == 1 { print $1 }' "$artifact_hash")
[ "$(release_sha256 "$artifact_apk")" = "$expected_hash" ] \
    || release_die 'release APK checksum verification failed'
release_require_artifact_provenance "$artifact_apk" "$(git rev-parse HEAD)"

if git rev-parse --verify --quiet "refs/tags/$tag" >/dev/null; then
    [ "$(git rev-list -n 1 "$tag")" = "$(git rev-parse HEAD)" ] \
        || release_die "$tag already points to a different commit"
    tag_exists=true
else
    tag_exists=false
fi

printf 'Release: AppId %s (%s)\nCommit:  %s\nTag:     %s\n' \
    "$version_name" "$version_code" "$(git rev-parse HEAD)" "$tag"

if [ "$mode" = dry-run ]; then
    printf 'Dry run only. Use --create for a local annotated tag or --push to tag and publish main.\n'
    exit 0
fi

if [ "$tag_exists" = false ]; then
    git tag -a "$tag" -m "AppId $version_name"
    printf 'Created annotated tag %s.\n' "$tag"
fi

if [ "$mode" = push ]; then
    git push origin main "$tag"
    printf 'Published main and %s to origin.\n' "$tag"
    printf 'Validate published source with: ./scripts/verify_fdroid_release.sh --ref %s\n' "$tag"
fi
