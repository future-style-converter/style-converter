#!/usr/bin/env bash
# S1: an isolated web export of HEAD (runtimes/web + apps/web-harness); node_modules are REAL dirs of
# per-entry symlinks into the shared tree's install, so vite/vitest caches land in the export, and
# @style-converter/web points at the export's own runtimes/web.
set -euo pipefail; shopt -s nullglob dotglob
R=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
X=$1; rm -rf "$X"; mkdir -p "$X"; cd "$X"
git -C "$R" archive HEAD runtimes/web apps/web-harness package.json | tar -x
mk(){ local src=$1 dst=$2; mkdir -p "$dst"; for e in "$src"/*; do b=$(basename "$e"); case "$b" in .vite|.vitest|.cache|@style-converter) continue;; esac; ln -s "$e" "$dst/$b"; done; }
mk "$R/node_modules" "$X/node_modules"; mkdir -p "$X/node_modules/@style-converter"; ln -s "$X/runtimes/web" "$X/node_modules/@style-converter/web"
[ -d "$R/apps/web-harness/node_modules" ] && mk "$R/apps/web-harness/node_modules" "$X/apps/web-harness/node_modules"
[ -d "$R/runtimes/web/node_modules" ] && mk "$R/runtimes/web/node_modules" "$X/runtimes/web/node_modules"
ls -A "$R/apps/web-harness/node_modules" "$R/runtimes/web/node_modules" | head
echo "export ready: $X"
