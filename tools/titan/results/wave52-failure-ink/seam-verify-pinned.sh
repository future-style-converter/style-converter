#!/bin/bash
# Wave 52 (lane L3 · failure-ink, fix pass) — verify a seam patch that ALSO
# ships a new source-pin test file, with its negative controls, under the
# PLAN §0 / lane-rule (2) per-file lock; leave the tree byte-identical to HEAD.
# WHY a second script: seam-verify.sh restores only the seam file, but the
# re-cut seam-1/seam-2 patches create a test file too, and the negative
# controls (seam at HEAD bytes with the pin present; consumer conjunct
# deleted) must run while the lock is still ours. The release is a trap armed
# only after this process's own mkdir succeeded (the 17:11 incident, §8).
# Usage: seam-verify-pinned.sh <seam> <patch> <newfile> <log> <green-cmd> <pin-cmd> [<sed-mutation>]
set -u
ROOT=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
cd "$ROOT" || exit 2
SEAM=$1; PATCH=$2; NEWFILE=$3; LOG=$4; GREEN=$5; PIN=$6; MUT=${7:-}
LOCK=$ROOT/tools/titan/runs/wave52-lock/$(basename "$SEAM")
# Poll every 30 s for up to 20 min; a >30-min lock on a CLEAN seam is stale.
deadline=$((SECONDS + 1200))
until mkdir "$LOCK" 2>/dev/null; do
  if [ -n "$(find "$LOCK" -maxdepth 0 -mmin +30 2>/dev/null)" ] && git diff --quiet -- "$SEAM"; then
    echo "stale lock (>30 min, seam clean) removed: $LOCK $(date '+%F %T')" >> "$LOG"
    rmdir "$LOCK" 2>/dev/null; continue
  fi
  if [ $SECONDS -gt $deadline ]; then echo "seam verification blocked by lock $(date '+%F %T')" >> "$LOG"; exit 3; fi
  sleep 30
done
# The lock is OURS from here; restore-then-release is the exit trap. The
# restore touches the files ONLY if this run applied the patch (APPLIED=1):
# an abort on a seam someone else left dirty must not overwrite their bytes.
APPLIED=0
restore() {
  if [ "$APPLIED" = 1 ]; then
    git show "HEAD:$SEAM" > "$SEAM"; rm -f "$NEWFILE"
    echo "restored: sha $(shasum -a 256 "$SEAM" | cut -d' ' -f1); new file present: $([ -e "$NEWFILE" ] && echo yes || echo no)"
    git diff --quiet -- "$SEAM" && echo "git diff --quiet: clean" || echo "git diff --quiet: DIRTY"
  fi
  rmdir "$LOCK" && echo "lock released $(date +%T)"
}
trap 'restore >> "$LOG" 2>&1' EXIT
{
  echo "=== $(basename "$PATCH") pinned verify $(date '+%F %T') — lock taken: $LOCK"
  # Never apply onto a dirty seam or over an existing pin file.
  if ! git diff --quiet -- "$SEAM"; then echo "ABORT: seam file dirty with the lock free"; exit 4; fi
  if [ -e "$NEWFILE" ]; then echo "ABORT: $NEWFILE already exists"; exit 4; fi
  before=$(shasum -a 256 "$SEAM" | cut -d' ' -f1); echo "sha before: $before"
  git apply "$PATCH" && { APPLIED=1; echo "patch applied"; } || { echo "ABORT: patch does not apply"; exit 5; }
  applied=$(mktemp); cp "$SEAM" "$applied"
  echo "--- GREEN: seam + pin applied"; bash -c "$GREEN"; echo "green command exit=$?"
  echo "--- NEGATIVE CONTROL 1: seam at HEAD bytes, pin kept $(date +%T)"
  git show "HEAD:$SEAM" > "$SEAM"; bash -c "$PIN"; echo "pin command exit=$? (expected non-zero)"
  if [ -n "$MUT" ]; then
    echo "--- NEGATIVE CONTROL 2: seam applied, then sed '$MUT' $(date +%T)"
    cp "$applied" "$SEAM"; sed -i '' "$MUT" "$SEAM"; diff "$applied" "$SEAM"
    bash -c "$PIN"; echo "pin command exit=$? (expected non-zero)"
  fi
  rm -f "$applied"
} >> "$LOG" 2>&1
