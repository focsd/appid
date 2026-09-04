#!/usr/bin/env bash
set -euo pipefail

SERIAL="${ANDROID_SERIAL:-}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PACKAGE="com.focsd.appid"
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
    bounds="$(WANTED="$wanted" printf '%s' "$xml" | WANTED="$wanted" perl -0777 -ne 'while (/<node\b[^>]*?text="([^"]*)"[^>]*?bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/g) { my ($label,$x1,$y1,$x2,$y2)=($1,$2,$3,$4,$5); $label =~ s/&amp;/&/g; print "$x1,$y1,$x2,$y2\n" if $label eq $ENV{WANTED} }' | head -1)"
    [ -n "$bounds" ] || { printf 'FAIL: not visible: %s\n' "$wanted" >&2; return 1; }
    IFS=, read -r x1 y1 x2 y2 <<< "$bounds"
    x=$(( (x1 + x2) / 2 )); y=$(( (y1 + y2) / 2 ))
    adb_cmd shell input tap "$x" "$y"
    printf 'PASS: %s\n' "$wanted"
    sleep 0.8
}
tap_description() {
    local wanted="$1" xml bounds x y
    xml="$(dump_ui)"
    bounds="$(WANTED="$wanted" printf '%s' "$xml" | WANTED="$wanted" perl -0777 -ne 'while (/<node\b[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/g) { my ($label,$x1,$y1,$x2,$y2)=($1,$2,$3,$4,$5); $label =~ s/&amp;/&/g; print "$x1,$y1,$x2,$y2\n" if $label eq $ENV{WANTED} }' | head -1)"
    [ -n "$bounds" ] || { printf 'FAIL: description not visible: %s\n' "$wanted" >&2; return 1; }
    IFS=, read -r x1 y1 x2 y2 <<< "$bounds"
    x=$(( (x1 + x2) / 2 )); y=$(( (y1 + y2) / 2 ))
    adb_cmd shell input tap "$x" "$y"
    printf 'PASS: %s\n' "$wanted"
    sleep 0.8
}
assert_text() {
    local wanted="$1"
    if has_text "$wanted"; then
        printf 'PASS: visible: %s\n' "$wanted"
    else
        printf 'FAIL: not visible: %s\n' "$wanted" >&2
        return 1
    fi
}
has_text() {
    local wanted="$1"
    WANTED="$wanted" dump_ui | WANTED="$wanted" perl -0777 -ne 's/&amp;/&/g; exit 0 if /text="\Q$ENV{WANTED}\E"/; exit 1'
}
assert_text_scroll() {
    local wanted="$1"
    for _ in 1 2 3 4 5 6 7 8; do
        if has_text "$wanted"; then
            printf 'PASS: visible after scroll: %s\n' "$wanted"
            return 0
        fi
        adb_cmd shell input swipe 540 1750 540 1150 250
        sleep 0.35
    done
    printf 'FAIL: not found after scrolling: %s\n' "$wanted" >&2
    return 1
}
assert_any_text() {
    local wanted
    for wanted in "$@"; do
        if WANTED="$wanted" dump_ui | WANTED="$wanted" perl -0777 -ne 's/&amp;/&/g; exit 0 if /text="\Q$ENV{WANTED}\E"/; exit 1'; then
            printf 'PASS: visible: %s\n' "$wanted"
            return 0
        fi
    done
    printf 'FAIL: none of the expected labels are visible: %s\n' "$*" >&2
    return 1
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
assert_any_text '✓ Builder ready' 'Setup required • Open Setup & tools from the menu'
tap_description 'More options'
tap_text 'About'
assert_text 'About AppId'
tap_text 'OK'
adb_cmd shell am force-stop "$PACKAGE"
adb_cmd shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 1
tap_description 'Setup & tools'
assert_text 'Setup & tools'
assert_text '1. Copy first-run Termux command'
assert_text '2. Open Termux'
assert_any_text '3. Grant AppId Termux permission' '3. Check Termux compatibility'
tap_text '4. Install / repair environment'
assert_text 'Install build environment?'
assert_text 'CONTINUE'
tap_text 'CANCEL'
assert_text_scroll 'Grant Termux storage access'
assert_text_scroll 'Check dependencies now'
assert_text_scroll 'Copy console'
assert_text_scroll 'Clear console'
assert_text_scroll "Copy today's saved log"
assert_text_scroll 'Import platform ZIP or android.jar'
assert_text_scroll 'Clear imported platform source'
assert_text_scroll 'Copy manual environment bootstrap'
assert_text_scroll 'Allow AppId to install APKs'
adb_cmd shell input keyevent KEYCODE_BACK >/dev/null || true
sleep 0.4
tap_description 'APK library'
assert_text 'Built APK library'
adb_cmd shell input keyevent KEYCODE_BACK >/dev/null || true
tap_description 'More options'
tap_text 'Installed apps'
assert_text 'App IDs, screen time and storage'
adb_cmd shell input keyevent KEYCODE_BACK >/dev/null || true
if [ "$INCLUDE_BUILD" -eq 1 ]; then
    tap_text 'CREATE'
    printf 'PASS: CREATE invoked (operation may still be running)\n'
else
    printf 'SKIP: CREATE (rerun with --include-build)\n'
fi
printf 'Device button smoke test completed.\n'
