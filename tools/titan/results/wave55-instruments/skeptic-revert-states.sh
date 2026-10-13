#!/bin/bash
# skeptic-revert-states.sh — the L7 SKEPTIC's own re-run of every revert state (PLAN §0 "Revert states compile"),
# written independently of the lane's revert-states.sh. Every L7 revertOrder chain is the unit alone, so the states are
# FULL and FULL − U for each delivered unit (U1 delivered no patch: FULL − U1 = FULL).
# Usage: bash skeptic-revert-states.sh <skeptic worktree at 611c9f93> <dir holding the eight unit-*.patch>
# Per state: reset the worktree (tracked files back to HEAD, the 11 new L7 paths removed), apply the kept patches in
# landing order, then: tooling node --test over the present L7 test files; bash -n of the U3 runner + self-test; web
# tsc --noEmit + vitest over the present renderer pins; schema conformance; Compose and Swift ONLY when their source
# tree hash differs from the last one built here (the hash is printed either way), over the pin classes present.
# Every rc is the runner's own exit status, captured before any filter (no PIPESTATUS read after a $(…)).
# Needs $COMPOSE_RUN / $SWIFT_RUN: scripts that take the test class list in $TESTS and print one "Tests …" verdict line.
set -uo pipefail
WT="$1"; PD="$2"; cd "$WT" || exit 2
NEW=(tools/titan/wire-differential.mjs tools/titan/wire-differential.sh tools/titan/wire-differential.test.mjs
  runtimes/web/tests/renderer/InlineRunsLatitudeCitation.test.ts runtimes/web/tests/renderer/InertOutOfFlowWordJoin.branches.test.tsx
  tools/visual/label-chrome-check.test.mjs
  runtimes/compose/src/test/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlockBranchesTest.kt
  runtimes/compose/src/test/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipelineClampHyphenTest.kt
  runtimes/compose/src/test/java/com/styleconverter/runtime/typography/wrapping/SoftHyphenCutsTest.kt
  runtimes/swiftui/Tests/StyleConverterRuntimeTests/HyphenateCharacterCascadeTests.swift
  runtimes/swiftui/Tests/StyleConverterRuntimeTests/OutOfFlowContainingBlockBranchesTests.swift)
ORDER=(U2 U3 U4 U6-compose U6-swift U6-web U6-tooling U5)          # landing-manifest seq 2..9 (U1: no patch)
LAST_KT=""; LAST_SW=""                                             # the source-tree hashes last compiled here
treehash() { git ls-files -co --exclude-standard -- "$@" | sort | xargs shasum -a 256 | shasum -a 256 | cut -c1-16; }
state() {                                                          # $1 = name, $2 = the dropped unit ('' = FULL)
  local name="$1" drop="$2" res=""
  git checkout -q -- . && git clean -fdq -- "${NEW[@]}" || { echo "revertStatesCompiled: $name: RESET FAILED"; return; }
  for u in "${ORDER[@]}"; do [[ "$u" == "$drop" ]] && continue; git apply "$PD/unit-$u.patch" || { echo "revertStatesCompiled: $name: APPLY FAILED $u"; return; }; done
  local t=(); for f in tools/titan/wire-differential.test.mjs tools/titan/bidi-bake.test.mjs tools/visual/label-chrome-check.test.mjs; do [[ -f $f ]] && t+=("$f"); done
  local o rc; o="$(node --test "${t[@]}" 2>&1)"; rc=$?; res+=" tooling[$(grep -E '^# (pass|fail)' <<<"$o" | tr '\n' ' ')rc $rc]"
  bash -n tools/titan/results/wave55-gate/ab-arms55.sh && bash -n tools/titan/results/wave55-gate/ab-arms55.selftest.sh; res+=" U3-syntax[rc $?]"
  (cd runtimes/web && npx tsc --noEmit >/dev/null 2>&1); res+=" web-tsc[rc $?]"
  local w=(); for f in runtimes/web/tests/renderer/InertOutOfFlowWordJoin.test.tsx runtimes/web/tests/renderer/InertOutOfFlowWordJoin.branches.test.tsx runtimes/web/tests/renderer/InlineRunsLatitudeCitation.test.ts; do [[ -f $f ]] && w+=("${f#runtimes/web/}"); done
  o="$(cd runtimes/web && npx vitest run "${w[@]}" 2>&1)"; rc=$?; res+=" web[$(grep -E '^ +Tests ' <<<"$o" | sed -E 's/ +/ /g;s/^ //') rc $rc]"
  o="$(node schema/conformance/run.mjs 2>&1)"; rc=$?; res+=" schema[$(tail -1 <<<"$o" | cut -c1-60) rc $rc]"
  local kt sw; kt="$(treehash runtimes/compose/src)"; sw="$(treehash runtimes/swiftui Package.swift)"
  if [[ "$kt" != "$LAST_KT" ]]; then
    local ct=(com.styleconverter.runtime.typography.wrapping.HyphenateCharacterExtractorTest com.styleconverter.runtime.layout.position.OutOfFlowContainingBlockTest)
    [[ -f "${NEW[6]}" ]] && ct+=(com.styleconverter.runtime.layout.position.OutOfFlowContainingBlockBranchesTest com.styleconverter.runtime.typography.wrapping.PreBreakPipelineClampHyphenTest com.styleconverter.runtime.typography.wrapping.SoftHyphenCutsTest)
    o="$(TESTS="${ct[*]}" "$COMPOSE_RUN")"; rc=$?; res+=" compose[tree $kt built: $(tail -1 <<<"$o") rc $rc]"; LAST_KT="$kt"
  else res+=" compose[tree $kt = the last built tree]"; fi
  if [[ "$sw" != "$LAST_SW" ]]; then
    local st=(OutOfFlowContainingBlockTests); [[ -f "${NEW[9]}" ]] && st=(HyphenateCharacterCascadeTests OutOfFlowContainingBlockBranchesTests OutOfFlowContainingBlockTests)
    o="$(TESTS="${st[*]}" "$SWIFT_RUN")"; rc=$?; res+=" swift[tree $sw built: $(tr '\n' ' ' <<<"$o")rc $rc]"; LAST_SW="$sw"
  else res+=" swift[tree $sw = the last built tree]"; fi
  echo "revertStatesCompiled: $name:$res"
}
state FULL ''
for u in "${ORDER[@]}"; do state "FULL-$u" "$u"; done
state FULL ''                                                      # end in FULL (every delivered patch applied)
