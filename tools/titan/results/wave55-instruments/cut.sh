#!/usr/bin/env bash
# Cut L7's eight unit patches in the lane worktree ($1), into the lane dir ($2): new files as creation hunks.
set -euo pipefail
WT="$1"; LD="$2"; cd "$WT"
CR=runtimes/compose/src/test/java/com/styleconverter/runtime; SW=runtimes/swiftui/Tests/StyleConverterRuntimeTests
paths() { case "$1" in
  U2) echo "tools/titan/wire-differential.sh tools/titan/wire-differential.mjs tools/titan/wire-differential.test.mjs" ;;
  U3) echo "tools/titan/results/wave55-gate/ab-arms55.sh tools/titan/results/wave55-gate/ab-arms55.selftest.sh tools/titan/results/wave55-gate/ab-arms55.selftest.out.txt" ;;
  U4) echo "schema/spec/03-children.md runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts runtimes/web/src/renderer/InlineRuns.ts runtimes/web/tests/renderer/InlineRunsLatitudeCitation.test.ts apps/web-harness/src/ui/ComposedRootSeparator.ts" ;;
  U5) echo "tools/titan/bidi-marker-paint.mjs tools/titan/bidi-bake.test.mjs" ;;
  U6-compose) echo "$CR/typography/wrapping/HyphenateCharacterExtractorTest.kt $CR/typography/wrapping/PreBreakPipelineClampHyphenTest.kt $CR/typography/wrapping/SoftHyphenCutsTest.kt $CR/layout/position/OutOfFlowContainingBlockBranchesTest.kt" ;;
  U6-swift) echo "$SW/HyphenateCharacterCascadeTests.swift $SW/OutOfFlowContainingBlockBranchesTests.swift" ;;
  U6-web) echo "runtimes/web/tests/renderer/InertOutOfFlowWordJoin.branches.test.tsx" ;;
  U6-tooling) echo "tools/visual/label-chrome-check.test.mjs" ;;
esac; }
UNITS="U2 U3 U4 U5 U6-compose U6-swift U6-web U6-tooling"
ALL=""; for u in $UNITS; do ALL+=" $(paths $u)"; done
NEW=$(git status --porcelain --untracked-files=all -- $ALL | awk '$1=="??"{print $2}')
echo "owned files: $(echo $ALL | wc -w) · new: $(echo $NEW | wc -w)"
shasum -a 256 $ALL > "$3"
git add -N -- $NEW
for u in U2 U3 U4 U5 U6-compose U6-swift U6-web U6-tooling; do
  git diff --binary HEAD -- $(paths $u) > "$LD/unit-$u.patch"
  echo "$u: $(grep -c '^diff --git' "$LD/unit-$u.patch") files ($(grep -c '^new file mode' "$LD/unit-$u.patch") new), $(wc -l < "$LD/unit-$u.patch" | tr -d ' ') lines"
done
git rm --cached -q -- $NEW
git status --porcelain --untracked-files=no | wc -l | sed 's/^ */tracked changes after cut: /'
