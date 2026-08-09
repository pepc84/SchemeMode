#!/usr/bin/env bash
set -euo pipefail
P4="${PROCESSING4_HOME:-$HOME/Projects/processing4}"
SRC="$(cd "$(dirname "$0")/../src/java/processing/mode/scheme" && pwd)"
TARGET="$P4/java/src/processing/mode/scheme"
mkdir -p "$TARGET"
rm -f "$TARGET"/*.java 2>/dev/null || true
for f in "$SRC"/*.java; do
  ln -sf "$f" "$TARGET/$(basename "$f")" && echo "  linked $(basename "$f")"
done
echo "Done: $TARGET"
