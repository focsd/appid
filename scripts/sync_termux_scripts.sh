#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CANONICAL="$PROJECT_ROOT/app/src/main/assets"
REFERENCE="$PROJECT_ROOT/termux"

for name in build_placeholder.sh setup_termux.sh check_environment.sh; do
    cp "$CANONICAL/$name" "$REFERENCE/$name"
    chmod 755 "$REFERENCE/$name"
done

printf 'Synchronized Termux scripts from app/src/main/assets to termux/.\n'
