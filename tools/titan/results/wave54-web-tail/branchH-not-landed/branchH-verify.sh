#!/usr/bin/env bash
# tools/titan/results/wave54-web-tail/branchH-not-landed/branchH-verify.sh — wave 54 lane L6: verify seam-2 (branch H) as it would land:
# WITHOUT the W1 runtime files (set aside to a backup, HEAD versions restored), WITH seam-2 (under the lock, via
# seam-verify.sh), then the W1 runtime files put back byte-exact (sha256 logged). Usage: branchH-verify.sh <log> <command…>
set -u
TREE=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
L=$TREE/tools/titan/results/wave54-web-tail
LOG=$1; shift
BK=$(mktemp -d)
cd "$TREE"
MODF="runtimes/web/src/renderer/InlineRuns.ts runtimes/web/src/renderer/NodeRenderer.ts"
NEWF="runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts runtimes/web/tests/renderer/InertOutOfFlowWordJoin.test.tsx"
echo "== W1 runtime files before set-aside" >>"$LOG"; shasum -a 256 $MODF $NEWF >>"$LOG"
for f in $MODF $NEWF; do mkdir -p "$BK/$(dirname "$f")"; cp -p "$f" "$BK/$f"; done
putback() {
  for f in $MODF $NEWF; do cp -p "$BK/$f" "$f"; done
  echo "== W1 runtime files after put-back" >>"$LOG"; shasum -a 256 $MODF $NEWF >>"$LOG"; rm -rf "$BK"
}
trap putback EXIT
for f in $MODF; do git show "HEAD:$f" > "$f"; done
rm -f $NEWF
echo "== W1 runtime files set aside (HEAD versions in place)" >>"$LOG"
"$L/seam-verify.sh" "$L/branchH-not-landed/seam-2.patch" "$LOG" -- "$*"
