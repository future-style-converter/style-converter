#!/bin/bash
# wave-55 L2 (cbb-android) — PLAN §0 "Revert states compile" (round 1, S12): every state the units' revertOrder chains
# (U2 → U1, the pair) can produce, plus the rule-2b states (owned files compile WITHOUT the seam patch), is rebuilt on a
# clean 611c9f93 checkout of the LANE WORKTREE (named on _note.md's TREES: line — never the shared tree), compiled, and run
# through the focused suite of PLAN §2 L2 "May run". Prints one `revertStatesCompiled:` line per state.
# Usage: revert-states.sh [state ...]   (default: all)
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WT="$(sed -n 's/^TREES: //p' "$HERE/_note.md" | head -1)"
OUT="$HERE/revert-states"; mkdir -p "$OUT"
NEW=(runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/ContainingBlockBands.kt
     runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt
     runtimes/compose/src/test/java/com/styleconverter/runtime/sizing/PercentContentBoxBandTest.kt
     runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/ChildContainingBlockSeamWiringTest.kt)
clean() {  # back to 611c9f93 on the lane's paths: drop intent-to-add entries, restore tracked files, remove the new files
  git -C "$WT" rm --cached -q --ignore-unmatch -- "${NEW[@]}" >/dev/null
  git -C "$WT" checkout -- runtimes/compose
  for f in "${NEW[@]}"; do rm -f "$WT/$f"; done
  [ -z "$(git -C "$WT" status --porcelain -- runtimes/compose)" ] || { echo "worktree not clean"; git -C "$WT" status --short; exit 2; }
}
state() {  # state <name> <patch...>: apply the kept patches in landing order, build + focused suite
  local name=$1; shift
  clean
  for p in "$@"; do git -C "$WT" apply --check "$HERE/$p" && git -C "$WT" apply "$HERE/$p" || { echo "revertStatesCompiled: $name: APPLY FAILED ($p)"; return; }; done
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)
  rm -rf "$WT/runtimes/compose/build/test-results/testDebugUnitTest"
  (cd "$WT/apps/android-harness" && nice ./gradlew :runtime:testDebugUnitTest --tests '*DynamicValueResolver*' --tests '*ChildContainingBlockSeamWiring*' --tests '*PercentContentBoxBand*' --tests '*ContentBox*' --tests '*BoxSizing*' --tests '*SizingMinMaxClamp*' --tests '*SizingOverflowWidth*' --tests '*WptBoxSizingDefault*' 2>&1) | sed "s|$WT|<lane worktree>|g" > "$OUT/$name.txt"
  local rc=${PIPESTATUS[0]} n=0 fl=0 sk=0
  for f in "$WT"/runtimes/compose/build/test-results/testDebugUnitTest/*.xml; do
    [ -f "$f" ] || continue
    t=$(grep -o '<testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$f")
    echo "$t" | sed 's/<testsuite name="com.styleconverter.runtime.//' >> "$OUT/$name.txt"
    n=$((n + $(echo "$t" | sed 's/.*tests="\([0-9]*\)".*/\1/'))); fl=$((fl + $(echo "$t" | sed 's/.*failures="\([0-9]*\)".*/\1/') + $(echo "$t" | sed 's/.*errors="\([0-9]*\)".*/\1/'))); sk=$((sk + $(echo "$t" | sed 's/.*skipped="\([0-9]*\)".*/\1/')))
  done
  echo "revertStatesCompiled: $name [$*]: gradle rc=$rc, $n tests, $fl failed, $sk skipped ($([ $rc -eq 0 ] && [ $fl -eq 0 ] && echo GREEN || echo RED)) — revert-states/$name.txt"
}
want=("$@"); [ ${#want[@]} -eq 0 ] && want=(A-full B-revert-U2 C-revert-U1 D-U1-no-seam E-U1-U2-no-seam F-U2-alone)
for s in "${want[@]}"; do case $s in
  A-full)          state A-full unit-U1.patch seam-1.patch unit-U2.patch ;;      # landed: U1 (+ seam 1) then U2
  B-revert-U2)     state B-revert-U2 unit-U1.patch seam-1.patch ;;             # revertOrder step 1 (also the U1-landed-alone moment)
  C-revert-U1)     state C-revert-U1 ;;                                         # revertOrder step 2 = 611c9f93
  D-U1-no-seam)    state D-U1-no-seam unit-U1.patch ;;                          # rule 2b: owned files without the seam patch
  E-U1-U2-no-seam) state E-U1-U2-no-seam unit-U1.patch unit-U2.patch ;;        # rule 2b with U2
  F-U2-alone)      state F-U2-alone unit-U2.patch ;;                            # never landed (the pair) — compile evidence only
esac; done
