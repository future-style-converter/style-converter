#!/usr/bin/env bash
# tools/titan/results/wave55-instruments/revert-states.sh — PLAN §0 "Revert states compile" (round 1, S12) for lane L7.
# Every L7 unit's revertOrder is the unit alone (expectations.json lanes["L7-instruments"].revertUnits[*].revertOrder),
# so the states a revert can produce are FULL (every delivered patch) and FULL − U for each unit U. For each state, on
# the lane's OWN worktree: `git checkout -- .`, `git clean -fd` of the lane's owned NEW paths only, `git apply` of the
# kept patches (in landing order), then compile and run the focused suites present in that state:
#   tooling  node --test over the present tooling pins (wire-differential, bidi-bake, label-chrome-check);
#   U3       bash -n of the runner and its self-test (the self-test itself: FULL and FULL − U3 only — it rewrites its record);
#   web      tsc --noEmit (runtimes/web, src + tests) + vitest over the present web pins + the wave-54 word-join test;
#   schema   node schema/conformance/run.mjs (39 goldens);
#   compose  gradle :runtime:testDebugUnitTest over the present Compose pins (FULL − U6-compose: the base classes);
#   swift    xcodebuild (Catalyst, <wt>/.dd) over the present Swift pins, in the states whose runtimes/swiftui tree
#            differs from the last one built (FULL, FULL − U6-swift); every other state's tree hash is printed and
#            must equal FULL's (byte-identical Swift sources and tests = the same build).
# Ends in FULL. Usage: bash revert-states.sh <lane worktree>   (base = its HEAD, 611c9f93). Writes nothing outside it.
set -uo pipefail
WT="$(cd -P "${1:?lane worktree}" && pwd -P)"; LD="$(cd -P "$(dirname "$0")" && pwd -P)"; cd "$WT" || exit 2
ORDER=(U1 U2 U3 U4 U6-compose U6-swift U6-web U6-tooling U5)              # landing-manifest.json seq 1-9
NEW=(tools/titan/wire-differential.sh tools/titan/wire-differential.mjs tools/titan/wire-differential.test.mjs
  runtimes/web/tests/renderer/InlineRunsLatitudeCitation.test.ts runtimes/web/tests/renderer/InertOutOfFlowWordJoin.branches.test.tsx
  runtimes/compose/src/test/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipelineClampHyphenTest.kt
  runtimes/compose/src/test/java/com/styleconverter/runtime/typography/wrapping/SoftHyphenCutsTest.kt
  runtimes/compose/src/test/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlockBranchesTest.kt
  runtimes/swiftui/Tests/StyleConverterRuntimeTests/HyphenateCharacterCascadeTests.swift
  runtimes/swiftui/Tests/StyleConverterRuntimeTests/OutOfFlowContainingBlockBranchesTests.swift
  tools/visual/label-chrome-check.test.mjs)
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
has() { [[ -e "$1" ]]; }                                                   # is a pin file present in this state?
tree_hash() { git ls-files -co --exclude-standard -- "$1" | LC_ALL=C sort | xargs shasum -a 256 2>/dev/null | shasum -a 256 | cut -c1-16; }
LAST_SWIFT=""
TMPF="$(mktemp)"; TMPS="$(mktemp)"                                        # scratch files under $TMPDIR, removed at the end
state() {   # <name> <unit to drop or ->
  local name="$1" drop="$2" u p rc
  echo; echo "=== state $name ==="
  git checkout -q -- . && git clean -fdq -- "${NEW[@]}" || { echo "revertStatesCompiled: $name: RESET FAILED"; return 1; }
  for u in "${ORDER[@]}"; do
    [[ "$u" == "$drop" ]] && continue; p="$LD/unit-$u.patch"; [[ -f "$p" ]] || continue
    git apply "$p" || { echo "revertStatesCompiled: $name: APPLY FAILED ($u)"; return 1; }
  done
  echo "applied: $(for u in "${ORDER[@]}"; do [[ "$u" != "$drop" && -f "$LD/unit-$u.patch" ]] && printf '%s ' "$u"; done)"
  local t=() w=() res=""
  for f in tools/titan/wire-differential.test.mjs tools/titan/bidi-bake.test.mjs tools/visual/label-chrome-check.test.mjs; do has "$f" && t+=("$f"); done
  node --test "${t[@]}" > "$TMPF" 2>&1; rc=$?; res+=" tooling[$(grep -E '^# (pass|fail) ' "$TMPF" | tr '\n' ' ' | tr -s ' ')rc $rc]"
  bash -n tools/titan/results/wave55-gate/ab-arms55.sh && bash -n tools/titan/results/wave55-gate/ab-arms55.selftest.sh; res+=" U3-syntax[rc $?]"
  if [[ "$name" == FULL || "$drop" == U3 ]]; then
    cp tools/titan/results/wave55-gate/ab-arms55.selftest.out.txt "$TMPS"
    bash tools/titan/results/wave55-gate/ab-arms55.selftest.sh > /dev/null 2>&1; rc=$?
    res+=" U3-selftest[$(tail -1 tools/titan/results/wave55-gate/ab-arms55.selftest.out.txt | cut -c20-90) rc $rc]"
    cp "$TMPS" tools/titan/results/wave55-gate/ab-arms55.selftest.out.txt   # the record stays the patch's
  fi
  for f in tests/renderer/InlineRunsLatitudeCitation.test.ts tests/renderer/InertOutOfFlowWordJoin.branches.test.tsx tests/renderer/InertOutOfFlowWordJoin.test.tsx; do has "runtimes/web/$f" && w+=("$f"); done
  (cd runtimes/web && npx tsc --noEmit) > "$TMPF" 2>&1; res+=" web-tsc[rc $?]"
  (cd runtimes/web && npx vitest run "${w[@]}") > "$TMPF" 2>&1; rc=$?; res+=" web[$(grep -E '^ +Tests ' "$TMPF" | tr -s ' ' | sed 's/^ //')rc $rc]"
  node schema/conformance/run.mjs > "$TMPF" 2>&1; rc=$?; res+=" schema[$(grep -c '^ok ' "$TMPF") ok, rc $rc]"
  local c=()
  if has runtimes/compose/src/test/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlockBranchesTest.kt; then
    c=(typography.wrapping.HyphenateCharacterExtractorTest typography.wrapping.PreBreakPipelineClampHyphenTest typography.wrapping.SoftHyphenCutsTest layout.position.OutOfFlowContainingBlockBranchesTest)
  else c=(typography.wrapping.HyphenateCharacterExtractorTest layout.position.OutOfFlowContainingBlockTest); fi
  local args=(); for k in "${c[@]}"; do args+=(--tests "com.styleconverter.runtime.$k"); done
  (cd apps/android-harness && nice -n 5 ./gradlew :runtime:testDebugUnitTest --console=plain "${args[@]}") > "$TMPF" 2>&1; rc=$?
  res+=" compose[$(grep -E '^> Task :runtime:(compileDebugUnitTestKotlin|testDebugUnitTest)' "$TMPF" | sed 's/> Task :runtime://' | tr '\n' ' ')$(grep -E 'BUILD (SUCCESSFUL|FAILED)' "$TMPF" | cut -c1-16) rc $rc; ${#c[@]} classes]"
  local sh; sh="$(tree_hash runtimes/swiftui)"
  if [[ "$sh" != "$LAST_SWIFT" ]]; then
    local s=(); if has runtimes/swiftui/Tests/StyleConverterRuntimeTests/OutOfFlowContainingBlockBranchesTests.swift; then
      s=(-only-testing:StyleConverterRuntimeTests/HyphenateCharacterCascadeTests -only-testing:StyleConverterRuntimeTests/OutOfFlowContainingBlockBranchesTests)
    else s=(-only-testing:StyleConverterRuntimeTests/OutOfFlowContainingBlockTests); fi
    nice -n 5 xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' -derivedDataPath "$WT/.dd" "${s[@]}" > "$TMPF" 2>&1; rc=$?
    res+=" swift[tree $sh built: $(grep -E 'Executed [0-9]+ tests' "$TMPF" | tail -1 | sed 's/ in .*//' | tr -s ' \t' ' ') $(grep -oE '\*\* TEST [A-Z]+ \*\*' "$TMPF") rc $rc]"
    LAST_SWIFT="$sh"
  else res+=" swift[tree $sh = the last built tree, byte-identical]"; fi
  echo "revertStatesCompiled: $name:$res"
}
state FULL -
for u in "${ORDER[@]}"; do state "FULL-$u" "$u"; done
state FULL -
echo; echo "final: $(git status --porcelain --untracked-files=no | wc -l | tr -d ' ') tracked changes, HEAD $(git rev-parse --short HEAD)"
rm -f "$TMPF" "$TMPS"
