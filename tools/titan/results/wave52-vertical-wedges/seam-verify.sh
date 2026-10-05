#!/bin/bash
# Wave 52 lane L8 — the PLAN §0 / task rule (2) seam-verification sequence,
# scripted so it is repeatable and its log is evidence: take the per-file
# lock, sha the seam, apply the lane patch, run the focused pins, restore the
# seam from HEAD (and remove any file the patch created), re-sha (must match),
# release the lock. Usage: seam-verify.sh <patch> <seam path> <log> -- <test cmd…>
set -u
R=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
PATCH=$1; SEAM=$2; LOG=$3; shift 4
L=$R/tools/titan/runs/wave52-lock/$(basename "$SEAM")
cd "$R" || exit 2
mkdir -p "$R/tools/titan/runs/wave52-lock"
# Poll every 30 s for up to 20 min (40 tries) for the per-file lock.
got=0
for i in $(seq 1 40); do
  if mkdir "$L" 2>/dev/null; then got=1; break; fi
  echo "$(date +%T) lock busy (try $i)" >> "$LOG"; sleep 30
done
[ $got = 1 ] || { echo "LOCK-TIMEOUT: seam verification blocked by lock" >> "$LOG"; exit 3; }
echo "$(date +%T) lock acquired" >> "$LOG"
# The seam must be clean (== HEAD) before our patch goes on.
if ! git diff --quiet -- "$SEAM"; then echo "SEAM-DIRTY after lock" >> "$LOG"; rmdir "$L"; exit 4; fi
before=$(shasum -a 256 "$SEAM" | cut -d' ' -f1); echo "sha before $before" >> "$LOG"
# Files the patch CREATES (`--- /dev/null`): removed again on restore.
created=$(awk '/^--- \/dev\/null/{getline; sub(/^\+\+\+ b\//,""); print}' "$PATCH")
git apply "$PATCH" >> "$LOG" 2>&1 || { echo "APPLY-FAILED" >> "$LOG"; rmdir "$L"; exit 5; }
echo "$(date +%T) applied $PATCH (created: $created)" >> "$LOG"
"$@" >> "$LOG" 2>&1; rc=$?
echo "$(date +%T) tests exit=$rc" >> "$LOG"
# Restore exactly HEAD, drop created files, re-sha.
git show "HEAD:$SEAM" > "$SEAM"
for f in $created; do rm -f "$f"; done
after=$(shasum -a 256 "$SEAM" | cut -d' ' -f1); echo "sha after  $after" >> "$LOG"
[ "$before" = "$after" ] && echo "SHA-OK" >> "$LOG" || echo "SHA-MISMATCH" >> "$LOG"
rmdir "$L"; echo "$(date +%T) lock released" >> "$LOG"
exit $rc
