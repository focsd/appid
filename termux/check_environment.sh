#!/data/data/com.termux/files/usr/bin/bash
set -u

PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
ROOT="$HOME/.com.focsd.appid"
EXPECTED_ENVIRONMENT_VERSION="5"
PROGRESS_TOKEN="${1:-}"
REPORT=""
MISSING=0

append() {
    printf -v REPORT '%s%s\n' "$REPORT" "$1"
}

mark_missing() {
    append "MISSING  $1"
    MISSING=$((MISSING + 1))
}

package_status() {
    local package="$1"
    local result
    result="$(dpkg-query -W -f='${db:Status-Abbrev} ${Version}' "$package" 2>/dev/null || true)"
    if [[ "$result" == ii* ]]; then
        append "OK       package $package: ${result#ii }"
    else
        mark_missing "package $package"
    fi
}

command_status() {
    local command="$1"
    local version=""
    if ! command -v "$command" >/dev/null 2>&1; then
        mark_missing "command $command"
        return
    fi
    case "$command" in
        javac) version="$(javac -version 2>&1 | head -n 1)" ;;
        d8) version="$(d8 --version 2>&1 | head -n 1)" ;;
        aapt) version="$(aapt version 2>&1 | head -n 1)" ;;
        apksigner) version="$(apksigner version 2>&1 | head -n 1)" ;;
        zip) version="$(zip -v 2>&1 | head -n 2 | tail -n 1)" ;;
        *) version="$(command -v "$command")" ;;
    esac
    append "OK       command $command: $version"
}

artifact_status() {
    local label="$1"
    local path="$2"
    if [ -s "$path" ]; then
        append "OK       $label: $path"
    else
        mark_missing "$label: $path"
    fi
}

send_report() {
    local stage="$1"
    local detail="$2"
    [ -n "$PROGRESS_TOKEN" ] || return 0
    local android_user
    android_user=$(($(id -u) / 100000))
    /system/bin/am broadcast \
        --user "$android_user" \
        -a com.focsd.appid.BUILD_PROGRESS \
        -p com.focsd.appid \
        --es token "$PROGRESS_TOKEN" \
        --es stage "$stage" \
        --es detail "$detail" \
        --es report "$REPORT" </dev/null >/dev/null 2>&1 || true
}

append "AppId Termux environment"
append ""
append "Required packages"
for package in openjdk-21 aapt d8 apksigner zip; do
    package_status "$package"
done

append ""
append "Required commands"
for command in bash base64 java javac jar d8 aapt apksigner zip keytool sed tr head date cp mkdir wc sha256sum; do
    command_status "$command"
done

append ""
append "Required artifacts"
if [ -s "$ROOT/android.jar" ]; then
    append "OK       Android platform JAR: $ROOT/android.jar"
elif [ -s "$PREFIX/share/java/android.jar" ]; then
    append "OK       Android platform JAR: $PREFIX/share/java/android.jar"
elif [ -s "$PREFIX/share/aapt/android.jar" ]; then
    append "OK       Android platform JAR: $PREFIX/share/aapt/android.jar"
else
    mark_missing "Android platform JAR"
fi
artifact_status "builder" "$ROOT/build_placeholder.sh"
artifact_status "template DEX" "$ROOT/template/dex/classes.dex"
if [ -f "$ROOT/template/template-version" ] &&
        [ "$(tr -d '\r\n' < "$ROOT/template/template-version")" = "2" ]; then
    append "OK       replacement template: version 2"
else
    mark_missing "replacement template version 2 (run repair)"
fi
if [ -s "$ROOT/template/icon-generator/com/focsd/appid/icon/IconGenerator.class" ] &&
        [ -f "$ROOT/template/icon-generator-version" ] &&
        [ "$(tr -d '\r\n' < "$ROOT/template/icon-generator-version")" = "2" ]; then
    append "OK       launcher icon renderer: version 2"
else
    mark_missing "launcher icon renderer version 2 (run repair)"
fi
artifact_status "signing keystore" "$ROOT/placeholder-signing.keystore"
artifact_status "signing password" "$ROOT/placeholder-signing.pass"

if [ -f "$ROOT/environment-version" ] &&
        [ "$(tr -d '\r\n' < "$ROOT/environment-version")" = "$EXPECTED_ENVIRONMENT_VERSION" ]; then
    append "OK       environment schema: $EXPECTED_ENVIRONMENT_VERSION"
else
    mark_missing "environment schema $EXPECTED_ENVIRONMENT_VERSION (run repair)"
fi

append ""
append "Integration and optional capabilities"
if grep -Eq '^[[:space:]]*allow-external-apps[[:space:]]*=[[:space:]]*true[[:space:]]*$' \
        "$HOME/.termux/termux.properties" 2>/dev/null; then
    append "OK       allow-external-apps=true"
else
    mark_missing "allow-external-apps=true"
fi

if [ -d "$HOME/storage/downloads" ] && [ -w "$HOME/storage/downloads" ]; then
    append "OK       shared Downloads output"
else
    append "OPTIONAL shared Downloads unavailable; APKs stay in private output"
fi
for command in zipalign termux-setup-storage; do
    if command -v "$command" >/dev/null 2>&1; then
        append "OPTIONAL $command: available"
    else
        append "OPTIONAL $command: unavailable"
    fi
done

append ""
if [ "$MISSING" -eq 0 ]; then
    append "READY    All required dependencies are available."
    FINAL_STAGE="Environment ready"
    FINAL_DETAIL="All required dependencies are available."
else
    append "REPAIR   $MISSING required item(s) are missing."
    FINAL_STAGE="Environment incomplete"
    FINAL_DETAIL="$MISSING required item(s) need repair."
fi

printf '%s' "$REPORT"
send_report "$FINAL_STAGE" "$FINAL_DETAIL"
