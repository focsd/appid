#!/data/data/com.termux/files/usr/bin/bash
set -eu
ROOT="$HOME/.com.focsd.appid"
if [ -d "$ROOT" ]; then
    rm -rf "$ROOT"
    printf 'Removed AppId Termux environment: %s\n' "$ROOT"
else
    printf 'AppId Termux environment was already absent: %s\n' "$ROOT"
fi
