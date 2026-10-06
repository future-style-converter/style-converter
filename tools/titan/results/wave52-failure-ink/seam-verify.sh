#!/bin/bash
# Wave 52 (lane L3 · failure-ink) — verify ONE seam patch under the PLAN §0 /
# lane-rule (2) per-file lock, then leave the seam file byte-identical to HEAD.
# WHY a script: the lock dance (take → sha → apply → focused tests → restore →
# sha → release) must never release a lock this lane does not own — the
# 2026-10-05 17:11 incident (an inline `mkdir L && {…}; rmdir L` released
# another lane's lock for ~1 s before it was recreated) is why the release is
# a trap armed ONLY after this process's own mkdir succeeded.
# Usage: seam-verify.sh <seam-path> <patch> <log> <test command…>
set -u
ROOT=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
cd "$ROOT" || exit 2
SEAM=$1; PATCH=$2; LOG=$3; shift 3
LOCK=$ROOT/tools/titan/runs/wave52-lock/$(basename "$SEAM")
# Poll every 30 s for up to 20 min (lane rule 2); a lock older than 30 min on a
# CLEAN seam file is stale and may be removed (and is noted in the log).
deadline=$((SECONDS + 1200))
until mkdir "$LOCK" 2>/dev/null; do
  if [ -n "$(find "$LOCK" -maxdepth 0 -mmin +30 2>/dev/null)" ] && git diff --quiet -- "$SEAM"; then
    echo "stale lock (>30 min, seam clean) removed: $LOCK $(date '+%F %T')" >> "$LOG"
    rmdir "$LOCK" 2>/dev/null
    continue
  fi
  if [ $SECONDS -gt $deadline ]; then echo "seam verification blocked by lock $(date '+%F %T')" >> "$LOG"; exit 3; fi
  sleep 30
done
# From here on the lock is OURS — and only now is the release armed.
trap 'rmdir "$LOCK" && echo "lock released $(date +%T)" >> "$LOG"' EXIT
{
  echo "=== $(basename "$PATCH") verify $(date '+%F %T') — lock taken: $LOCK ==="
  # The holder before us must have restored the file; never apply onto a dirty seam.
  if ! git diff --quiet -- "$SEAM"; then echo "ABORT: seam file dirty with the lock free"; exit 4; fi
  before=$(shasum -a 256 "$SEAM" | cut -d' ' -f1); echo "sha before: $before"
  git apply "$PATCH" && echo "patch applied" || { echo "ABORT: patch does not apply"; exit 5; }
  "$@"; echo "test command exit=$?"
  # Restore exactly HEAD's bytes, then prove it.
  git show "HEAD:$SEAM" > "$SEAM"
  after=$(shasum -a 256 "$SEAM" | cut -d' ' -f1); echo "sha after restore: $after"
  [ "$before" = "$after" ] && echo "sha equal: yes" || echo "sha equal: NO"
} >> "$LOG" 2>&1
