#!/usr/bin/env bash
# land-units.sh --check read-only proof (round 2): throwaway worktree; snapshot (HEAD, index sha256 + mtime, refs, stash,
# status --untracked-files=all) before/after, a find -newer sweep, and canaries (untracked loose copy at a CREATED path, the
# MODIFIED file dirty, a stray file in the lane dir, a stray file outside it) whose sha256 must be unchanged. Plus the round-1
# M3 delivery rules (a stale patch the ORDER table does not know; a patch re-cut after the note) as read-only refusals.
set -uo pipefail
WT="$1"; cd "$WT" || exit 9
export TMPDIR="$(dirname "$WT")/lu-tmp"; mkdir -p "$TMPDIR"
gs() { git --no-optional-locks status --porcelain=v1 --untracked-files=all; }
snap() { echo "HEAD $(git rev-parse HEAD)"; echo "index $(shasum -a 256 "$(git rev-parse --git-path index)" | cut -c1-16) mtime $(stat -f %m "$(git rev-parse --git-path index)")";
         echo "refs $(git for-each-ref --format='%(refname) %(objectname)' | shasum -a 256 | cut -c1-16)"; echo "stash $(git stash list 2>/dev/null | shasum -a 256 | cut -c1-16)"; echo "worktrees $(git worktree list --porcelain | shasum -a 256 | cut -c1-16)"; gs; }
F=runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BordersFacade.kt
NEW1=runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt
L=tools/titan/results/wave55-android-paint-order
LU=tools/titan/results/wave55-plan/land-units.sh
newer() { find . -path ./.git -prune -o -newer "$1" -print | grep -v '^\.$'; }
check() { # <label> <args…>
  local label="$1"; shift
  snap > "$TMPDIR/$label.before"; touch "$TMPDIR/$label.stamp"; sleep 1
  bash $LU "$@" > "$TMPDIR/$label.out" 2>&1; local rc=$?
  snap > "$TMPDIR/$label.after"
  echo "### $label: land-units.sh $* -> exit $rc"
  grep -E "ERROR|error|WARN|REFUSE|INTERNAL|SUMMARY|CUMULATIVE|  ok      [0-9]+ patches|revert \[|nothing was touched|die|land-units: " "$TMPDIR/$label.out" | sed "s#$WT#<wt>#g; s#$TMPDIR#<tmp>#g" | cut -c1-210 | head -14
  cmp -s "$TMPDIR/$label.before" "$TMPDIR/$label.after" && echo "  snapshot (HEAD, index sha256+mtime, refs, stash, worktrees, status --untracked-files=all): IDENTICAL" || { echo "  snapshot DIFFERS:"; diff "$TMPDIR/$label.before" "$TMPDIR/$label.after"; }
  echo "  files newer than the pre-run stamp in the work tree (.git excluded): $(newer "$TMPDIR/$label.stamp" | wc -l | tr -d ' ') $(newer "$TMPDIR/$label.stamp" | head -3 | tr '\n' ' ')"
  echo "  export dirs left in TMPDIR: $(ls -d "$TMPDIR"/land-units.* 2>/dev/null | wc -l | tr -d ' ')"
}
canaries() { shasum -a 256 "$NEW1" "$F" "$L/stray-canary.txt" stray-root-canary.txt 2>/dev/null | cut -c1-16 | tr '\n' ' '; }
check A-clean --check --only L6
echo; echo "### canaries: untracked loose copy at U1's CREATED path, the U2 file dirty, a stray in the lane dir, a stray at the tree root"
echo "CANARY loose copy" > "$NEW1"; echo "// CANARY dirty edit" >> "$F"; echo "CANARY stray" > "$L/stray-canary.txt"; echo "CANARY root" > stray-root-canary.txt
touch -t 202610110000 "$L/stray-canary.txt"
B4="$(canaries)"; echo "  canary sha256 before: $B4"
check B-canaries --check --only L6
echo "  canary sha256 after:  $(canaries) $( [[ "$(canaries)" == "$B4" ]] && echo '(byte-identical)' || echo '(CHANGED)')"
check C-real-path-refuses --only L6
echo "  canary sha256 after:  $(canaries) $( [[ "$(canaries)" == "$B4" ]] && echo '(byte-identical)' || echo '(CHANGED)')"
# restore the canaries by plain file operations (no git), then the delivery-rule refusals
rm -f "$NEW1" "$L/stray-canary.txt" stray-root-canary.txt; cp "$(dirname "$WT")/lu-src/F.orig" "$F"
echo; echo "### canaries removed (plain rm / cp of the original bytes): status now: $(gs | wc -l | tr -d ' ') entr(y/ies) — $(gs | tr '\n' ' ')"
cp "$L/unit-U3.patch" "$L/unit-U9.patch"; touch -t 202610110000 "$L/unit-U9.patch"
check D-stale-patch --check --only L6
rm -f "$L/unit-U9.patch"
cp "$L/unit-U2.patch" "$TMPDIR/u2.keep"; sed -i '' 's/synthetic U2 line/synthetic U2 line, re-cut/' "$L/unit-U2.patch"; touch -t 202610110000 "$L/unit-U2.patch"
check E-recut-after-note --check --only L6
cp "$TMPDIR/u2.keep" "$L/unit-U2.patch"; touch -t 202610110000 "$L/unit-U2.patch"
sed -i '' 's/^STATUS: COMPLETE/STATUS: PARTIAL — killed/' "$L/_note.md"; touch -t 202610110000 "$L/_note.md"
check F-partial-note --check --only L6
sed -i '' 's/^STATUS: PARTIAL — killed/STATUS: COMPLETE/' "$L/_note.md"; touch -t 202610110000 "$L/_note.md"
check G-restored --check --only L6
