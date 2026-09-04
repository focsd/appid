#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck disable=SC1091
source "$SCRIPT_DIR/release_lib.sh"

usage() {
    printf 'Usage: %s VERSION VERSION_CODE CHANGELOG_FILE\n' "$(basename "$0")" >&2
    printf 'Example: %s 0.12.1 31 /tmp/0.12.1.txt\n' "$(basename "$0")" >&2
    exit 2
}

[ "$#" -eq 3 ] || usage
next_version=$1
next_code=$2
changelog_source=$3

release_validate_version_name "$next_version"
release_validate_version_code "$next_code"
[ -s "$changelog_source" ] || release_die "missing or empty changelog source: $changelog_source"
grep -q '[^[:space:]]' "$changelog_source" || release_die 'changelog source contains only whitespace'

repo_root=$(release_repo_root)
cd "$repo_root"
release_require_clean_tree

gradle_file="$repo_root/app/build.gradle"
current_version=$(release_read_version_name "$gradle_file")
current_code=$(release_read_version_code "$gradle_file")
[ "$next_version" != "$current_version" ] || release_die "versionName is already $next_version"
[ "$next_code" -gt "$current_code" ] \
    || release_die "versionCode must be greater than $current_code"

changelog_target="$repo_root/fastlane/metadata/android/en-US/changelogs/$next_code.txt"
[ ! -e "$changelog_target" ] || release_die "changelog already exists: $changelog_target"

temp_gradle=$(mktemp "${TMPDIR:-/tmp}/appid-build.gradle.XXXXXX")
trap 'rm -f "$temp_gradle"' EXIT
cp -p "$gradle_file" "$temp_gradle"
NEXT_VERSION=$next_version NEXT_CODE=$next_code perl -0pi -e '
    my $version_count = s/(versionName\s*=\s*)\x27[^\x27]+\x27/${1}\x27$ENV{NEXT_VERSION}\x27/;
    my $code_count = s/(versionCode\s*=\s*)\d+/${1}$ENV{NEXT_CODE}/;
    die "expected one versionName replacement\n" unless $version_count == 1;
    die "expected one versionCode replacement\n" unless $code_count == 1;
' "$temp_gradle"

mv "$temp_gradle" "$gradle_file"
cp "$changelog_source" "$changelog_target"
trap - EXIT

printf 'Prepared AppId %s (%s).\n' "$next_version" "$next_code"
printf 'Review and commit:\n  %s\n  %s\n' "$gradle_file" "$changelog_target"
printf 'Then run: ./scripts/verify_fdroid_release.sh\n'
