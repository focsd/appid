#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
ROOT="$HOME/.com.focsd.appid"
ENVIRONMENT_VERSION="4"
PROGRESS_TOKEN="${1:-}"
BUILDER_B64="${2:-}"
CHECKER_B64="${3:-}"
ANDROID_PLATFORM_URL="https://dl.google.com/android/repository/platform-35_r02.zip"
ANDROID_PLATFORM_SHA256="0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0"

progress() {
    [ -n "$PROGRESS_TOKEN" ] || return 0
    local android_user
    android_user=$(($(id -u) / 100000))
    /system/bin/am broadcast \
        --user "$android_user" \
        -a com.focsd.appid.BUILD_PROGRESS \
        -p com.focsd.appid \
        --es token "$PROGRESS_TOKEN" \
        --es stage "$1" \
        --es detail "${2:-}" </dev/null >/dev/null 2>&1 || true
}

progress_log() {
    [ -n "$PROGRESS_TOKEN" ] || return 0
    local android_user
    android_user=$(($(id -u) / 100000))
    /system/bin/am broadcast \
        --user "$android_user" \
        -a com.focsd.appid.BUILD_PROGRESS \
        -p com.focsd.appid \
        --es token "$PROGRESS_TOKEN" \
        --es log "$1" </dev/null >/dev/null 2>&1 || true
}

run_logged() {
    local last_log=-2
    local status
    set +e
    "$@" 2>&1 | while IFS= read -r line; do
        printf '%s\n' "$line"
        if [ $((SECONDS - last_log)) -ge 2 ]; then
            progress_log "$line"
            last_log=$SECONDS
        fi
    done
    status="${PIPESTATUS[0]}"
    set -e
    return "$status"
}

configure_main_repository() {
    local repository_url="$1"
    local repository_label="$2"
    local sources="$PREFIX/etc/apt/sources.list"
    mkdir -p "$(dirname "$sources")"
    if [ -f "$sources" ] && [ ! -f "$sources.com.focsd.appid-backup" ]; then
        cp "$sources" "$sources.com.focsd.appid-backup"
    fi
    printf 'deb %s stable main\n' "$repository_url" > "$sources"
    printf 'Using Termux repository: %s (%s)\n' "$repository_label" "$repository_url"
    progress "Setup 2/4" "Using $repository_label; updating package metadata…"
}

install_toolchain() {
    run_logged apt-get \
        -o Acquire::Retries=3 \
        -o Acquire::https::Timeout=30 \
        update
    run_logged dpkg \
        --force-confdef \
        --force-confold \
        --configure -a
    run_logged apt-get \
        -o Acquire::Retries=3 \
        -o Acquire::https::Timeout=30 \
        -o Dpkg::Options::=--force-confdef \
        -o Dpkg::Options::=--force-confold \
        install -y openjdk-21 aapt d8 apksigner zip unzip openssl
}

ensure_android_platform() {
    local android_jar="$ROOT/android.jar"
    local archive="$ROOT/platform-35_r02.zip"
    local downloader="$ROOT/DownloadAndroidPlatform.java"
    if [ -s "$android_jar" ]; then
        printf 'Android SDK platform JAR is already installed.\n'
        return 0
    fi

    progress "Setup 2/4" "Downloading Android SDK Platform 35 (about 64 MB)…"
    printf 'Downloading Android SDK Platform 35 from Google...\n'
    cat > "$downloader" <<'JAVA'
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DownloadAndroidPlatform {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[1]);
        Files.deleteIfExists(output);
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(args[0]))
                .timeout(java.time.Duration.ofMinutes(15))
                .build();
        HttpResponse<Path> response = client.send(
                request, HttpResponse.BodyHandlers.ofFile(output));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
    }
}
JAVA
    local attempt
    for attempt in 1 2 3; do
        if java "$downloader" "$ANDROID_PLATFORM_URL" "$archive"; then
            break
        fi
        if [ "$attempt" -eq 3 ]; then
            printf 'Android platform download failed after 3 attempts.\n' >&2
            return 1
        fi
        printf 'Download attempt %s failed; retrying...\n' "$attempt" >&2
        sleep 2
    done
    rm -f "$downloader"
    printf '%s  %s\n' "$ANDROID_PLATFORM_SHA256" "$archive" | sha256sum --check -
    unzip -p "$archive" android-35/android.jar > "$android_jar.tmp"
    if [ ! -s "$android_jar.tmp" ]; then
        printf 'Android SDK archive did not contain android-35/android.jar.\n' >&2
        exit 42
    fi
    mv "$android_jar.tmp" "$android_jar"
    rm -f "$archive"
    printf 'Android SDK platform JAR installed: %s\n' "$android_jar"
}

on_exit() {
    local code="$?"
    if [ "$code" -ne 0 ]; then
        progress "Failed" "Builder setup stopped with exit code $code."
    fi
}
trap on_exit EXIT

mkdir -p "$ROOT" "$ROOT/output" "$ROOT/template"

printf '\n=== AppId Termux setup ===\n'
progress "Setup 1/4" "Installing the AppId builder…"
if [ -z "$BUILDER_B64" ] || [ -z "$CHECKER_B64" ]; then
    printf 'Setup payload is missing. Rebuild/reinstall AppId and retry.\n' >&2
    exit 40
fi
printf '%s' "$BUILDER_B64" | base64 -d > "$ROOT/build_placeholder.sh.tmp"
printf '%s' "$CHECKER_B64" | base64 -d > "$ROOT/check_environment.sh.tmp"
if [ ! -s "$ROOT/build_placeholder.sh.tmp" ] || [ ! -s "$ROOT/check_environment.sh.tmp" ]; then
    printf 'Setup payload could not be decoded. Retry environment setup.\n' >&2
    exit 41
fi
mv "$ROOT/build_placeholder.sh.tmp" "$ROOT/build_placeholder.sh"
mv "$ROOT/check_environment.sh.tmp" "$ROOT/check_environment.sh"
chmod 700 "$ROOT/build_placeholder.sh" "$ROOT/check_environment.sh"
printf 'Builder and dependency checker installed.\n'

toolchain_ready() {
    local command
    for command in java javac jar d8 aapt apksigner zip keytool unzip sha1sum; do
        command -v "$command" >/dev/null 2>&1 || return 1
    done
    [ -s "$ROOT/android.jar" ]
}

if toolchain_ready; then
    printf 'Android build toolchain is already installed; skipping package update.\n'
    progress "Setup 2/4" "Android build tools are ready."
else
    printf 'Updating package metadata...\n'
    configure_main_repository \
        "https://packages.termux.dev/apt/termux-main" \
        "Termux primary"
    if ! install_toolchain; then
        printf '\nPrimary repository failed; retrying with the Warsaw mirror...\n' >&2
        progress "Setup 2/4" "Primary repository failed; retrying with the Warsaw mirror…"
        configure_main_repository \
            "https://ftp.icm.edu.pl/pub/Linux/dist/termux/termux-main" \
            "Warsaw fallback"
        install_toolchain
    fi
fi

ensure_android_platform

progress "Setup 3/4" "Checking shared storage access…"
if [ ! -e "$HOME/storage/shared" ]; then
    printf '\nRequesting Termux shared-storage access. Android may show a permission dialog...\n'
    termux-setup-storage || true
fi

printf '\nPreparing the reusable placeholder template...\n'
progress "Setup 4/4" "Preparing the reusable app template…"
"$ROOT/build_placeholder.sh" --prepare-only --progress-token "$PROGRESS_TOKEN"

printf '\n=== Setup complete ===\n'
printf 'Builder: %s\n' "$ROOT/build_placeholder.sh"
printf 'Generated APKs will be copied to Downloads/com.focsd.appid when shared storage is available.\n'
printf 'You can now return to AppId.\n'
printf '%s\n' "$ENVIRONMENT_VERSION" > "$ROOT/environment-version"
progress "Setup complete" "The placeholder builder is ready; auditing dependencies…"
"$ROOT/check_environment.sh" "$PROGRESS_TOKEN"
