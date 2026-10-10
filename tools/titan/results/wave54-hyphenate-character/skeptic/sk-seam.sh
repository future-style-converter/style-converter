#!/usr/bin/env bash
# Skeptic: apply ONE L3 seam patch to the shared tree under the per-file lock, run a command, restore byte-exact from HEAD.
# Usage: sk-seam.sh <seam-path> <patch> -- <cmd…>
set -uo pipefail
R="$(cd "$(dirname "$0")/../../../../.." && pwd)"; cd "$R"; LOG=tools/titan/results/wave54-hyphenate-character/skeptic/sk-seam.log
seam="$1"; patch="$2"; shift 2; [ "$1" = "--" ] && shift
LOCK=tools/titan/runs/wave54-lock/$(basename "$seam"); until mkdir "$LOCK" 2>/dev/null; do echo "lock $LOCK held; wait 60s"; sleep 60; done
h=$(git show HEAD:$seam | shasum -a 256 | cut -d' ' -f1); b=$(shasum -a 256 $seam | cut -d' ' -f1)
restore() { git show HEAD:$seam > $seam; a=$(shasum -a 256 $seam | cut -d' ' -f1); echo "restored $a $([ "$a" = "$h" ] && echo BYTE-EXACT-TO-HEAD || echo MISMATCH)" | tee -a $LOG; rmdir "$LOCK"; }
trap restore EXIT
echo "=== $(date '+%F %T') $patch on $seam HEAD $h before $b" | tee -a $LOG
[ "$b" = "$h" ] || { echo "seam differs from HEAD: refuse" | tee -a $LOG; exit 7; }
git apply "$patch" || exit 6; echo "applied $(shasum -a 256 $seam | cut -d' ' -f1)" | tee -a $LOG
out=$("$@" 2>&1); rc=$?; echo "$out" | tail -12 | tee -a $LOG; echo "command exit=$rc" | tee -a $LOG; exit $rc
