#!/usr/bin/env bash

# Shared helpers for AppId's release scripts. This file is sourced, not executed.

release_repo_root() {
    cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd
}

release_die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

release_require_tool() {
    command -v "$1" >/dev/null 2>&1 || release_die "required command not found: $1"
}

release_setup_java() {
    local candidate
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        return
    fi
    if command -v java >/dev/null 2>&1 && java -version >/dev/null 2>&1; then
        return
    fi
    for candidate in \
        "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
        "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
        "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"; do
        if [ -x "$candidate/bin/java" ]; then
            export JAVA_HOME=$candidate
            export PATH="$JAVA_HOME/bin:$PATH"
            return
        fi
    done
    release_die 'Java was not found; set JAVA_HOME to JDK 17 or newer'
}

release_setup_android_sdk() {
    local candidate
    for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" \
        "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        if [ -n "$candidate" ] && [ -f "$candidate/platforms/android-35/android.jar" ] \
            && [ -d "$candidate/build-tools/35.0.0" ]; then
            export ANDROID_HOME=$candidate
            export ANDROID_SDK_ROOT=$candidate
            return
        fi
    done
    release_die 'Android SDK Platform 35 and Build Tools 35.0.0 were not found; set ANDROID_HOME'
}

release_read_version_name() {
    local gradle_file=$1
    local value
    value=$(sed -nE "s/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*'([^']+)'.*/\\1/p" "$gradle_file")
    [ "$(printf '%s\n' "$value" | awk 'NF { count++ } END { print count + 0 }')" -eq 1 ] \
        || release_die "expected one versionName in $gradle_file"
    printf '%s\n' "$value"
}

release_read_version_code() {
    local gradle_file=$1
    local value
    value=$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' "$gradle_file")
    [ "$(printf '%s\n' "$value" | awk 'NF { count++ } END { print count + 0 }')" -eq 1 ] \
        || release_die "expected one versionCode in $gradle_file"
    printf '%s\n' "$value"
}

release_validate_version_name() {
    printf '%s\n' "$1" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$' \
        || release_die "invalid version name: $1"
}

release_validate_version_code() {
    printf '%s\n' "$1" | grep -Eq '^[1-9][0-9]*$' || release_die "invalid positive version code: $1"
}

release_require_clean_tree() {
    [ -z "$(git status --porcelain --untracked-files=all)" ] \
        || release_die 'working tree is not clean; commit or stash changes first'
}

release_require_changelog() {
    local repo_root=$1
    local version_code=$2
    local changelog="$repo_root/fastlane/metadata/android/en-US/changelogs/$version_code.txt"
    [ -s "$changelog" ] || release_die "missing or empty changelog: $changelog"
    grep -q '[^[:space:]]' "$changelog" || release_die "changelog contains only whitespace: $changelog"
}

release_sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

release_read_provenance() {
    local provenance_file=$1
    local key=$2
    awk -F= -v wanted="$key" '$1 == wanted { print substr($0, index($0, "=") + 1); exit }' \
        "$provenance_file"
}

release_require_artifact_provenance() {
    local artifact_apk=$1
    local expected_commit=$2
    local provenance_file="$artifact_apk.provenance"
    [ -s "$provenance_file" ] \
        || release_die "release provenance is missing; rerun verification: $provenance_file"
    [ "$(release_read_provenance "$provenance_file" dirty)" = false ] \
        || release_die 'release APK was built from a dirty working tree; rerun verification after committing'
    [ "$(release_read_provenance "$provenance_file" commit)" = "$expected_commit" ] \
        || release_die 'release APK was verified from a different commit; rerun verification'
    [ "$(release_read_provenance "$provenance_file" sha256)" = "$(release_sha256 "$artifact_apk")" ] \
        || release_die 'release APK does not match its verification provenance'
}
