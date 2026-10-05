#!/bin/bash
# Wave 52 (lane L7 · static-position) — verify a seam patch STACK under the
# PLAN §0 / lane-rule (2) per-file lock, then leave the seam file
# byte-identical to HEAD. Adapted from wave52-failure-ink/seam-verify.sh (its
# lock release is a trap armed only after OUR mkdir succeeded — the
# 2026-10-05 17:11 lock-release incident), extended to apply several patches
# in order: the prior lanes' patches first (PLAN §4: L2, then L3), then L7's.
# Usage: seam-verify.sh <seam-path> <log> <patch1,patch2,…> <test command…>
set -u
ROOT=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
cd "$ROOT" || exit 2
SEAM=$1; LOG=$2; PATCHES=$3; shift 3
LOCK=$ROOT/tools/titan/runs/wave52-lock/$(basename "$SEAM")
# Poll every 30 s for up to 20 min; a lock older than 30 min on a CLEAN seam
# file is stale and may be removed (and is noted in the log).
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
  echo "=== verify [$PATCHES] $(date '+%F %T') — lock taken: $LOCK ==="
  # Never apply onto a dirty seam: the previous holder must have restored it.
  if ! git diff --quiet -- "$SEAM"; then echo "ABORT: seam file dirty with the lock free"; exit 4; fi
  before=$(shasum -a 256 "$SEAM" | cut -d' ' -f1); echo "sha before: $before"
  IFS=',' read -r -a list <<< "$PATCHES"
  ok=1
  created=()
  last=$((${#list[@]} - 1))
  for i in "${!list[@]}"; do
    p=${list[$i]}
    # The LAST patch is this lane's own: applied WHOLE (its source-pin test
    # file ships inside it). Every file it creates must not pre-exist, and is
    # removed again after the run.
    if [ "$i" = "$last" ]; then
      for f in $(awk '/^new file mode/{getline; getline; sub(/^\+\+\+ b\//,""); print}' "$p"); do
        if [ -e "$f" ]; then echo "ABORT: $f already exists"; ok=0; fi
        created+=("$f")
      done
      [ $ok = 1 ] || break
      if git apply "$p"; then echo "applied (whole): $p"; else echo "ABORT: does not apply: $p"; ok=0; fi
      break
    fi
    # --include: ONLY the seam file's hunks. A prior lane's patch may also
    # create a test file (L3's 18:51 re-cut adds VerticalBlockFlowSeamWiringTest.kt);
    # applying that here would leave a stray file the seam restore below never
    # removes (2026-10-05 19:01:01 incident, this lane — the file was deleted
    # again at once and the incident is recorded in _note.md).
    if git apply --include="$SEAM" "$p"; then echo "applied (seam hunks only): $p"; else echo "ABORT: does not apply: $p"; ok=0; break; fi
  done
  if [ $ok = 1 ]; then "$@"; echo "test command exit=$?"; fi
  # Remove every file this lane's patch created (never anything else).
  for f in "${created[@]}"; do [ -e "$f" ] && rm "$f" && echo "removed created file: $f"; done
  # Restore exactly HEAD's bytes, then prove it.
  git show "HEAD:$SEAM" > "$SEAM"
  after=$(shasum -a 256 "$SEAM" | cut -d' ' -f1); echo "sha after restore: $after"
  [ "$before" = "$after" ] && echo "sha equal: yes" || echo "sha equal: NO"
  git diff --quiet -- "$SEAM" && echo "git diff clean: yes" || echo "git diff clean: NO"
} >> "$LOG" 2>&1
