#!/usr/bin/env bash
# tools/titan/results/wave54-web-tail/seam-verify.sh — wave 54 lane L6: verify a seam patch's pins
# under the per-file lock (build-workflow.js rule 2), then restore the seam byte-exact.
#   seam-verify.sh <patch> <log> -- <command…>      (the command runs with the patch applied, cwd = tree)
# Holds tools/titan/runs/wave54-lock/ComponentRenderer.tsx (mkdir is the mutex; waits 60 s per retry),
# records sha256 of every touched existing file before / after, and ALWAYS restores (trap):
# `git show HEAD:<path> > <path>` for modified files, `rm` for files the patch created.
set -u
TREE=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
PATCH=$1; LOG=$2; shift 3
LOCK=$TREE/tools/titan/runs/wave54-lock/ComponentRenderer.tsx
cd "$TREE"
# The files the patch touches: modified (exist at HEAD) vs created (new file mode).
MOD=$(git apply --numstat "$PATCH" | awk '{print $3}' | while read -r f; do git cat-file -e "HEAD:$f" 2>/dev/null && echo "$f"; done)
NEW=$(git apply --numstat "$PATCH" | awk '{print $3}' | while read -r f; do git cat-file -e "HEAD:$f" 2>/dev/null || echo "$f"; done)
until mkdir "$LOCK" 2>/dev/null; do echo "lock busy — waiting 60 s" >>"$LOG"; sleep 60; done
APPLIED=0
restore() {
  # Only a tree this script patched is restored — an abort before apply leaves every file untouched.
  if [ "$APPLIED" = 1 ]; then
    for f in $MOD; do git show "HEAD:$f" > "$f"; done
    for f in $NEW; do rm -f "$f"; done
  fi
  echo "== sha256 after restore" >>"$LOG"; for f in $MOD; do shasum -a 256 "$f" >>"$LOG"; done
  for f in $NEW; do [ -e "$f" ] && echo "STILL PRESENT $f" >>"$LOG" || echo "removed $f" >>"$LOG"; done
  rmdir "$LOCK"; echo "== lock released $(date '+%F %T')" >>"$LOG"
}
trap restore EXIT
echo "== lock taken $(date '+%F %T'); patch $PATCH" >>"$LOG"
echo "== sha256 before" >>"$LOG"; for f in $MOD; do shasum -a 256 "$f" >>"$LOG"; done
# Every modified file must equal HEAD before we apply (never stack on a foreign edit).
for f in $MOD; do git diff --quiet HEAD -- "$f" || { echo "ABORT: $f differs from HEAD" >>"$LOG"; exit 9; }; done
git apply "$PATCH" || { echo "ABORT: apply failed" >>"$LOG"; exit 8; }
APPLIED=1
echo "== applied; running: $*" >>"$LOG"
bash -c "$*" >>"$LOG" 2>&1; rc=$?
echo "== command exit $rc" >>"$LOG"
exit $rc
