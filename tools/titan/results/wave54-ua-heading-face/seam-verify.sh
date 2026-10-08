#!/usr/bin/env bash
# Wave 54 lane L5 — verify a seam patch under the per-file mutex (build-workflow rule 2):
#   seam-verify.sh <patch> <seam-path> <label> -- <focused test command...>
# mkdir lock (retry every 60 s while held) → sha256 → git apply → run the focused suite →
# restore the seam file byte-exact from HEAD → sha256 → rmdir lock. Never leaves the seam modified.
set -u
ROOT=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
patch=$1; seam=$2; label=$3; shift 4
lock="$ROOT/tools/titan/runs/wave54-lock/$(basename "$seam")"
mkdir -p "$ROOT/tools/titan/runs/wave54-lock"
until mkdir "$lock" 2>/dev/null; do echo "[$label] lock $lock held — waiting 60 s"; sleep 60; done
trap 'git -C "$ROOT" show HEAD:"$seam" > "$ROOT/$seam"; rmdir "$lock" 2>/dev/null' EXIT
before=$(shasum -a 256 "$ROOT/$seam" | cut -d" " -f1)
head_sha=$(git -C "$ROOT" show HEAD:"$seam" | shasum -a 256 | cut -d" " -f1)
echo "[$label] lock taken; seam sha256 before=$before (HEAD $head_sha)"
git -C "$ROOT" apply "$ROOT/$patch" && echo "[$label] patch applied: $(shasum -a 256 "$ROOT/$seam" | cut -d" " -f1)"
( cd "$ROOT" && "$@" ); rc=$?
echo "[$label] focused suite rc=$rc"
git -C "$ROOT" show HEAD:"$seam" > "$ROOT/$seam"
after=$(shasum -a 256 "$ROOT/$seam" | cut -d" " -f1)
echo "[$label] restored seam sha256 after=$after $( [ "$after" = "$head_sha" ] && echo BYTE-EXACT-HEAD || echo MISMATCH )"
rmdir "$lock" && echo "[$label] lock released"
trap - EXIT
exit $rc
