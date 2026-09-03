#!/usr/bin/env bash
set -euo pipefail

SERIAL="${ANDROID_SERIAL:-}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PACKAGE="com.focsd.appid"
ACTIVITY="$PACKAGE/.MainActivity"
INCLUDE_BUILD=0
for arg in "$@"; do
    case "$arg" in
        --include-build) INCLUDE_BUILD=1 ;;
        -h|--help) printf 'Usage: %s [--include-build]\n' "$0"; exit 0 ;;
        *) printf 'Unknown option: %s\n' "$arg" >&2; exit 2 ;;
    esac
done
ADB_ARGS=()
[ -n "$SERIAL" ] && ADB_ARGS=(-s "$SERIAL")
adb_cmd() { "$ADB" "${ADB_ARGS[@]}" "$@"; }
dump_ui() {
    adb_cmd shell uiautomator dump /sdcard/appid-ui.xml >/dev/null
    adb_cmd exec-out cat /sdcard/appid-ui.xml
}
tap_text() {
    local wanted="$1" xml bounds x y
    xml="$(dump_ui)"
    bounds="$(WANTED="$wanted" printf '%s' "$xml" | WANTED="$wanted" perl -0777 -ne 'while (/<node\b[^>]*?text="([^"]*)"[^>]*?bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/g) { print "$2,$3,$4,$5\n" if $1 eq $ENV{WANTED} }' | head -1)"
    [ -n "$bounds" ] || { printf 'FAIL: not visible: %s\n' "$wanted" >&2; return 1; }
    IFS=, read -r x1 y1 x2 y2 <<< "$bounds"
    x=$(( (x1 + x2) / 2 )); y=$(( (y1 + y2) / 2 ))
    adb_cmd shell input tap "$x" "$y"
    printf 'PASS: %s\n' "$wanted"
    sleep 0.35
}
assert_text() {
    local wanted="$1"
    WANTED="$wanted" dump_ui | WANTED="$wanted" perl -0777 -ne 'exit 0 if /text="\Q$ENV{WANTED}\E"/; exit 1' \
        && printf 'PASS: visible: %s\n' "$wanted" \
        || { printf 'FAIL: not visible: %s\n' "$wanted" >&2; return 1; }
}
adb_cmd get-state | grep -qx device
adb_cmd shell input keyevent KEYCODE_WAKEUP
adb_cmd shell input keyevent KEYCODE_MENU
adb_cmd shell input swipe 540 1800 540 500 300
adb_cmd shell wm dismiss-keyguard 2>/dev/null || true
adb_cmd shell am force-stop "$PACKAGE"
adb_cmd shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 1
assert_text 'CREATE'
assert_text 'Builder ready'
tap_text '⋮'
tap_text 'About'
tap_text 'Close'
tap_text '⋮'
tap_text 'Setup & tools'
for label in \
    '1. Copy first-run Termux command' '2. Open Termux' \
    '3. Grant AppId Termux permission' 'Check dependencies now' \
    'Copy console' 'Clear console' "Copy today's saved log" \
    'Import platform ZIP or android.jar' 'Clear imported platform source' \
    'Copy manual environment bootstrap' 'Allow AppId to install APKs'; do
    tap_text "$label"
    adb_cmd shell input keyevent KEYCODE_BACK >/dev/null || true
done
adb_cmd shell input keyevent KEYCODE_BACK >/dev/null || true
sleep 0.4
tap_text '⋮'
tap_text 'APK library'
adb_cmd shell input keyevent KEYCODE_BACK >/dev/null || true
tap_text '⋮'
tap_text 'Installed apps'
adb_cmd shell input keyevent KEYCODE_BACK >/dev/null || true
if [ "$INCLUDE_BUILD" -eq 1 ]; then
    tap_text 'CREATE'
    printf 'PASS: CREATE invoked (operation may still be running)\n'
else
    printf 'SKIP: CREATE (rerun with --include-build)\n'
fi
printf 'Device button smoke test completed.\n'
