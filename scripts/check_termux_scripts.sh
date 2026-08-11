#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CANONICAL="$PROJECT_ROOT/app/src/main/assets"
REFERENCE="$PROJECT_ROOT/termux"
FAILED=0

for name in build_placeholder.sh setup_termux.sh check_environment.sh; do
    if ! cmp -s "$CANONICAL/$name" "$REFERENCE/$name"; then
        printf 'OUT OF SYNC: termux/%s (run scripts/sync_termux_scripts.sh)\n' "$name" >&2
        FAILED=1
    fi
done

exit "$FAILED"
