#!/usr/bin/env bash
# land-units.sh --check is read-only: proven in the THROWAWAY worktree with (a) git status --porcelain + HEAD + index sha256
# + refs before/after, (b) a find -newer sweep of the whole work tree (any file written by the check shows up), and
# (c) stray-file canaries: an untracked file at a path a delivered patch CREATES, a tracked file a delivered patch MODIFIES
# left dirty, and an untracked stray in the lane dir — each must be reported and left byte-identical.
set -uo pipefail
WT="$1"; OUT="$2"; cd "$WT" || exit 9
export TMPDIR="$(dirname "$WT")/teeth/lu-tmp"; mkdir -p "$TMPDIR"
gs() { git --no-optional-locks status --porcelain=v1 --untracked-files=all; }
snap() { echo "HEAD $(git rev-parse HEAD)"; echo "index $(shasum -a 256 "$(git rev-parse --git-path index)" | cut -c1-16) mtime $(stat -f %m "$(git rev-parse --git-path index)")";
         echo "refs $(git for-each-ref --format='%(refname) %(objectname)' | shasum -a 256 | cut -c1-16)"; echo "stash $(git stash list 2>/dev/null | shasum -a 256 | cut -c1-16)"; gs; }
F=runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BordersFacade.kt
T=runtimes/compose/src/test/java/com/styleconverter/runtime/effects/EffectsClipFilterOrderTest.kt
L=tools/titan/results/wave55-android-paint-order
LU=tools/titan/results/wave55-plan/land-units.sh
{
echo "### run 1: --check --only L6 on a clean delivery (3 synthetic L6 patches)"
snap > "$TMPDIR/s1.before"; touch "$TMPDIR/stamp1"; sleep 1
bash $LU --check --only L6 > "$TMPDIR/r1.out" 2>&1; echo "exit $?"
grep -E "ERROR|WARN|ok |alone|CUMULATIVE|revert|REFUSE|INTERNAL|RESULT|checks:|^  [0-9]" "$TMPDIR/r1.out" | cut -c1-200 | head -40
snap > "$TMPDIR/s1.after"; cmp -s "$TMPDIR/s1.before" "$TMPDIR/s1.after" && echo "snapshot (HEAD, index sha+mtime, refs, stash, status --untracked-files=all): IDENTICAL" || { echo "snapshot DIFFERS:"; diff "$TMPDIR/s1.before" "$TMPDIR/s1.after"; }
echo "files newer than the pre-run stamp in the work tree (excluding .git): $(find . -path ./.git -prune -o -newer "$TMPDIR/stamp1" -print | grep -v '^\.$' | wc -l | tr -d ' ')"
find . -path ./.git -prune -o -newer "$TMPDIR/stamp1" -print | grep -v '^\.$' | head -5
echo
echo "### run 2: the same with three canaries (an untracked copy where U3 CREATES, the U2 file dirty, a stray in the lane dir)"
mkdir -p "$(dirname $T)"; echo "CANARY untracked loose copy" > $T
echo "// CANARY dirty edit" >> $F
echo "CANARY stray" > $L/stray-canary.txt
C1=$(shasum -a 256 $T | cut -c1-16); C2=$(shasum -a 256 $F | cut -c1-16); C3=$(shasum -a 256 $L/stray-canary.txt | cut -c1-16)
echo "canary sha256 before: $C1 $C2 $C3"
snap > "$TMPDIR/s2.before"; touch "$TMPDIR/stamp2"; sleep 1
bash $LU --check --only L6 > "$TMPDIR/r2.out" 2>&1; echo "exit $?"
grep -E "ERROR|WARN|INTERNAL" "$TMPDIR/r2.out" | cut -c1-200 | head -20
snap > "$TMPDIR/s2.after"; cmp -s "$TMPDIR/s2.before" "$TMPDIR/s2.after" && echo "snapshot: IDENTICAL" || { echo "snapshot DIFFERS:"; diff "$TMPDIR/s2.before" "$TMPDIR/s2.after"; }
echo "canary sha256 after:  $(shasum -a 256 $T | cut -c1-16) $(shasum -a 256 $F | cut -c1-16) $(shasum -a 256 $L/stray-canary.txt | cut -c1-16)"
echo "files newer than the pre-run stamp (excluding .git): $(find . -path ./.git -prune -o -newer "$TMPDIR/stamp2" -print | grep -v '^\.$' | wc -l | tr -d ' ')"
echo
echo "### run 3: the REAL path (no --check) on the dirty canary tree must refuse before touching anything"
snap > "$TMPDIR/s3.before"
LAND_FAULT_AT= bash $LU --only L6 > "$TMPDIR/r3.out" 2>&1; echo "exit $?"
grep -E "ERROR|REFUS|refus|land-units:|STOP|INTERNAL" "$TMPDIR/r3.out" | cut -c1-200 | head -8
snap > "$TMPDIR/s3.after"; cmp -s "$TMPDIR/s3.before" "$TMPDIR/s3.after" && echo "snapshot: IDENTICAL" || { echo "snapshot DIFFERS:"; diff "$TMPDIR/s3.before" "$TMPDIR/s3.after"; }
echo "canary sha256 after:  $(shasum -a 256 $T | cut -c1-16) $(shasum -a 256 $F | cut -c1-16) $(shasum -a 256 $L/stray-canary.txt | cut -c1-16)"
} > "$OUT" 2>&1
cat "$OUT"
