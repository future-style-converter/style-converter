#!/usr/bin/env bash
# A web export of the WORKING TREE (runtimes/web + apps/web-harness + schema), node_modules as per-entry links.
set -euo pipefail; shopt -s nullglob dotglob
R=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
X=$1; rm -rf "$X"; mkdir -p "$X/runtimes" "$X/apps"; cd "$X"
rsync -a --exclude node_modules --exclude dist "$R/runtimes/web" runtimes/; rsync -a --exclude node_modules --exclude dist --exclude public/fixtures "$R/apps/web-harness" apps/
rsync -a "$R/schema" .; cp "$R/package.json" .
mk(){ local src=$1 dst=$2; mkdir -p "$dst"; for e in "$src"/*; do b=$(basename "$e"); case "$b" in .vite|.vitest|.cache|@style-converter) continue;; esac; ln -s "$e" "$dst/$b"; done; }
mk "$R/node_modules" "$X/node_modules"; mkdir -p "$X/node_modules/@style-converter"; ln -s "$X/runtimes/web" "$X/node_modules/@style-converter/web"
[ -d "$R/apps/web-harness/node_modules" ] && mk "$R/apps/web-harness/node_modules" "$X/apps/web-harness/node_modules"
[ -d "$R/runtimes/web/node_modules" ] && mk "$R/runtimes/web/node_modules" "$X/runtimes/web/node_modules"
echo "export ready: $X"
