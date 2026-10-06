#!/usr/bin/env bash
# verify-seam.sh <patch> <seam path> <test command...> — the PLAN §0 seam
# verification sequence for lane L11: take the per-file lock (mkdir), sha256
# the seam file, git apply the patch, run the focused tests, restore the file
# from HEAD, re-sha (must be equal), release the lock. Polls the lock every
# 30 s for up to 20 min. Appends its record to seam-verification.log.
set -u
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
PATCH=$1; SEAM=$2; shift 2
LOCK=$ROOT/tools/titan/runs/wave52-lock/$(basename "$SEAM")
LOG=$ROOT/tools/titan/results/wave52-all-reset-postload-colour/seam-verification.log
GOT=0                                                           # 1 once mkdir succeeds
for i in $(seq 1 40); do if mkdir "$LOCK" 2>/dev/null; then GOT=1; break; fi; sleep 30; done
[ $GOT = 1 ] || { echo "seam verification blocked by lock $(basename "$SEAM")" | tee -a "$LOG"; exit 3; }
cd "$ROOT"
SHA0=$(shasum -a 256 "$SEAM" | cut -d' ' -f1)
# The seam must be at HEAD before we touch it (seam files are never left edited).
[ "$SHA0" = "$(git show HEAD:"$SEAM" | shasum -a 256 | cut -d' ' -f1)" ] || { rmdir "$LOCK"; echo "seam $SEAM not at HEAD — abort" | tee -a "$LOG"; exit 5; }
git apply "$PATCH" || { rmdir "$LOCK"; echo "apply FAILED $PATCH" | tee -a "$LOG"; exit 4; }
"$@" > /tmp/l11-seam.out 2>&1; RC=$?
git show HEAD:"$SEAM" > "$SEAM"                                  # restore
SHA1=$(shasum -a 256 "$SEAM" | cut -d' ' -f1)
rmdir "$LOCK"
{ echo "== $(date -u +%FT%TZ) $(basename "$PATCH") on $SEAM: tests exit $RC; sha256 before=$SHA0 after=$SHA1 $( [ "$SHA0" = "$SHA1" ] && echo IDENTICAL || echo MISMATCH )";
  grep -E 'tests completed|FAILED|BUILD|error:|\*\* TEST|Executed [0-9]+ test|Test Suite .* (passed|failed)|^e: ' /tmp/l11-seam.out | head -20; } >> "$LOG"
exit $RC
