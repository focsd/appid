#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TITLE="${1:-Pause}"
PACKAGE_ID="${2:-com.focsd.appid.macospreview}"
COLOR_HEX="${3:-345995}"
HOST_STATE="$PROJECT_ROOT/build/com.focsd.appid-placeholder"
HOST_OUTPUT="$PROJECT_ROOT/artifacts/com.focsd.appid-placeholder"
BUILDER="$PROJECT_ROOT/app/src/main/assets/build_placeholder.sh"

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JDK_HOME="$JAVA_HOME"
elif [ -x "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" ]; then
    JDK_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"
elif [ -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" ]; then
    JDK_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
else
    printf 'ERROR: Android Studio JDK was not found. Set JAVA_HOME and retry.\n' >&2
    exit 2
fi

if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
    SDK_ROOT="$ANDROID_SDK_ROOT"
elif [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
    SDK_ROOT="$ANDROID_HOME"
elif [ -d "$HOME/Library/Android/sdk" ]; then
    SDK_ROOT="$HOME/Library/Android/sdk"
else
    printf 'ERROR: Android SDK was not found. Set ANDROID_SDK_ROOT and retry.\n' >&2
    exit 3
fi

PLATFORM_JAR="$SDK_ROOT/platforms/android-35/android.jar"
BUILD_TOOLS="$SDK_ROOT/build-tools/35.0.0"
for required in "$PLATFORM_JAR" "$BUILD_TOOLS/aapt" "$BUILD_TOOLS/d8" \
        "$BUILD_TOOLS/apksigner" "$BUILD_TOOLS/zipalign"; do
    if [ ! -e "$required" ]; then
        printf 'ERROR: required Android SDK component is missing: %s\n' "$required" >&2
        exit 4
    fi
done

mkdir -p "$HOST_STATE" "$HOST_OUTPUT"
cp -f "$PLATFORM_JAR" "$HOST_STATE/android.jar"

TITLE_B64="$(printf '%s' "$TITLE" | base64 | tr -d '\r\n')"
export JAVA_HOME="$JDK_HOME"
export PATH="$JDK_HOME/bin:$BUILD_TOOLS:$PATH"

printf 'Running the on-device placeholder builder on macOS first...\n'
FOCSD_APPID_ROOT="$HOST_STATE" \
PREFIX="$HOST_STATE/host-prefix" \
bash "$BUILDER" \
    --package "$PACKAGE_ID" \
    --title-b64 "$TITLE_B64" \
    --color "$COLOR_HEX" \
    --install 0

BUILT_APK="$HOST_STATE/output/$PACKAGE_ID-placeholder.apk"
FINAL_APK="$HOST_OUTPUT/$PACKAGE_ID-placeholder.apk"
if [ ! -s "$BUILT_APK" ]; then
    printf 'ERROR: builder completed without producing an APK.\n' >&2
    exit 5
fi
cp -f "$BUILT_APK" "$FINAL_APK"
chmod 644 "$FINAL_APK"

"$BUILD_TOOLS/apksigner" verify --verbose "$FINAL_APK" >/dev/null
BADGING="$("$BUILD_TOOLS/aapt" dump badging "$FINAL_APK")"
printf '%s\n' "$BADGING" | grep -q "package: name='$PACKAGE_ID'" || {
    printf 'ERROR: generated APK package ID did not match.\n' >&2
    exit 6
}
printf '%s\n' "$BADGING" | grep -q 'application-icon-' || {
    printf 'ERROR: generated APK has no launcher icon.\n' >&2
    exit 7
}

APK_BYTES="$(wc -c < "$FINAL_APK" | tr -d ' ')"
if [ "$APK_BYTES" -gt 60000 ]; then
    printf 'ERROR: generated APK is too large for the Android handoff: %s bytes.\n' \
        "$APK_BYTES" >&2
    exit 8
fi

printf '\nmacOS placeholder preflight passed.\n'
printf 'APK: %s\n' "$FINAL_APK"
printf 'Size: %s bytes\n' "$APK_BYTES"
printf 'Package: %s\n' "$PACKAGE_ID"
printf 'Title: %s\n' "$TITLE"
