#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck disable=SC1091
source "$SCRIPT_DIR/release_lib.sh"

mode=full
case "${1:-}" in
    '') ;;
    --full) mode=full ;;
    --build-block) mode=build-block ;;
    *) printf 'Usage: %s [--full|--build-block]\n' "$(basename "$0")" >&2; exit 2 ;;
esac

repo_root=$(release_repo_root)
version_name=$(release_read_version_name "$repo_root/app/build.gradle")
version_code=$(release_read_version_code "$repo_root/app/build.gradle")
release_validate_version_name "$version_name"
release_validate_version_code "$version_code"
release_require_changelog "$repo_root" "$version_code"
source_commit=${FDROID_SOURCE_COMMIT:-}
if [ -z "$source_commit" ] && git -C "$repo_root" rev-parse HEAD >/dev/null 2>&1; then
    source_commit=$(git -C "$repo_root" rev-parse HEAD)
fi
printf '%s' "$source_commit" | grep -Eq '^[0-9a-f]{40}$' \
    || release_die 'set FDROID_SOURCE_COMMIT to the full 40-character source commit hash'

if [ "$mode" = full ]; then
    cat <<EOF
Categories:
  - Development
License: MPL-2.0
AuthorName: FOCSD
AuthorWebSite: https://focsd.com
WebSite: https://focsd.com
SourceCode: https://github.com/focsd/appid
IssueTracker: https://github.com/focsd/appid/issues
Changelog: https://github.com/focsd/appid/releases

AutoName: AppId

RepoType: git
Repo: https://github.com/focsd/appid.git
Binaries: https://github.com/focsd/appid/releases/download/v%v/AppId-v%v.apk

Builds:
  - versionName: $version_name
    versionCode: $version_code
    commit: $source_commit
    subdir: app
    gradle:
      - yes

AllowedAPKSigningKeys: 8c54543c74d42f5cc96b24027c30d89f7e38f8595f4056c8976b253fdfa603a8

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: $version_name
CurrentVersionCode: $version_code
EOF
else
    cat <<EOF
  - versionName: $version_name
    versionCode: $version_code
    commit: $source_commit
    subdir: app
    gradle:
      - yes

CurrentVersion: $version_name
CurrentVersionCode: $version_code
EOF
fi
