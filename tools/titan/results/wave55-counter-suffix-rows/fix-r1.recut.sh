#!/bin/bash
# fix-r1.recut.sh — wave-55 L5 FIX lane (fix-r1): re-cut EVERY L5 patch (PLAN §8 step 3: a resumed / fix lane deletes its
# patches and re-cuts all of them) from a clean 611c9f93 worktree, in landing order, so each patch is cut on exactly the tree
# it lands on (the four ComponentRenderer.swift seams and the two ComponentRenderer.kt seams stack).
# How: the worktree is reset to its base (checkout -- . + clean of the lane's new-file dirs: the throwaway worktree only, never
# the shared tree); a TEMPORARY index (GIT_INDEX_FILE, read-tree HEAD — no commit, no stash) snapshots each step's paths before
# the step, the step is applied (the previous cut's patch; for unit-U3-ios and seam-4 the fix-r1 bytes of their one changed
# file are copied over it), and the patch body is `git diff` of that snapshot against the worktree (new files intent-to-add, so a
# creation hunk). The header lines are carried over from the previous cut (they stay true) plus one fix-r1 line.
# Usage: fix-r1.recut.sh <worktree> <previous-patch-dir> <fix-r1-files-dir> <out-dir>
#   <fix-r1-files-dir> holds FontVariantNumericApplier.swift and SeamReachabilityTest.kt as fix-r1 leaves them.
set -euo pipefail
WT=$1; PREV=$2; FIX=$3; OUT=$4
cd "$WT"
SW=runtimes/swiftui/Sources/StyleConverterRuntime
ST=runtimes/swiftui/Tests/StyleConverterRuntimeTests
KM=runtimes/compose/src/main/java/com/styleconverter/runtime
KT=runtimes/compose/src/test/java/com/styleconverter/runtime
# reset the throwaway worktree to 611c9f93 (tracked files + the untracked files of the lane's new-file directories)
git checkout -- .
git clean -fdq -- "$SW/StyleEngine/lists" "$ST" "$KM/lists" "$KT/lists"
[[ -z "$(git status --porcelain --untracked-files=all -- runtimes tools/titan)" ]] || { echo "worktree not clean at base"; exit 1; }
TI=$(mktemp); rm -f "$TI"; export GIT_INDEX_FILE=$TI; git read-tree HEAD
# step table: name | paths (space-separated) | new files among them
steps=(
 "unit-U1-ios|$SW/StyleEngine/lists/ListMarkerBidi.swift $ST/ListMarkerBidiTests.swift|$SW/StyleEngine/lists/ListMarkerBidi.swift $ST/ListMarkerBidiTests.swift"
 "seam-1|$SW/Renderer/ComponentRenderer.swift $ST/ListMarkerBidiSeamRasterTests.swift|$ST/ListMarkerBidiSeamRasterTests.swift"
 "unit-U2o-ios|$SW/StyleEngine/lists/ListMarkerRow.swift $SW/StyleEngine/lists/ListMarkerSuffixGap.swift $ST/ListMarkerSuffixGapTests.swift|$SW/StyleEngine/lists/ListMarkerSuffixGap.swift $ST/ListMarkerSuffixGapTests.swift"
 "seam-2|$SW/Renderer/ComponentRenderer.swift $ST/ListMarkerSuffixGapHangRasterTests.swift|$ST/ListMarkerSuffixGapHangRasterTests.swift"
 "seam-3|$SW/Renderer/ComponentRenderer.swift $ST/ListMarkerSuffixGapInsideRasterTests.swift|$ST/ListMarkerSuffixGapInsideRasterTests.swift"
 "unit-U2o-android|$KM/lists/ListMarkerRow.kt $KM/lists/ListMarkerSuffixGap.kt $KT/lists/ListMarkerSuffixGapTest.kt|$KM/lists/ListMarkerSuffixGap.kt $KT/lists/ListMarkerSuffixGapTest.kt"
 "seam-4|$KM/core/renderer/ComponentRenderer.kt $KT/core/renderer/SeamReachabilityTest.kt $KT/lists/ListMarkerSuffixGapHangSeamTest.kt|$KT/lists/ListMarkerSuffixGapHangSeamTest.kt"
 "seam-5|$KM/core/renderer/ComponentRenderer.kt $KT/lists/ListMarkerSuffixGapRowSeamTest.kt|$KT/lists/ListMarkerSuffixGapRowSeamTest.kt"
 "unit-U3-ios|$SW/Renderer/StyleBuilder.swift $SW/StyleEngine/typography/TypographyAggregate.swift $SW/StyleEngine/typography/font-variant/FontVariantNumericApplier.swift $ST/FontVariantNumericTabularTests.swift|$ST/FontVariantNumericTabularTests.swift"
 "seam-6|$SW/Renderer/ComponentRenderer.swift $ST/FontVariantNumericTabularRasterTests.swift|$ST/FontVariantNumericTabularRasterTests.swift"
 "unit-D|$KM/lists/ListMarkerOutsideHang.kt $SW/StyleEngine/lists/KoreanHangulFormal.swift $SW/StyleEngine/lists/ListMarkerOutsideHang.swift $SW/StyleEngine/lists/ListMarkerStyle.swift $SW/StyleEngine/lists/ListMarkerText.swift tools/titan/counter-style-table.mjs|"
 "unit-U1-ios-LRM|$SW/StyleEngine/lists/ListMarkerBidi.swift $ST/ListMarkerBidiTests.swift|"
)
for row in "${steps[@]}"; do
  IFS='|' read -r name paths news <<< "$row"
  # snapshot the step's paths as they are BEFORE the step (an absent new file stays absent from the temp index)
  for p in $paths; do [[ -e "$p" ]] && git add -- "$p"; done
  git apply "$PREV/$name.patch"
  case $name in
    unit-U3-ios) cp "$FIX/FontVariantNumericApplier.swift" "$SW/StyleEngine/typography/font-variant/FontVariantNumericApplier.swift" ;;
    seam-4)      cp "$FIX/SeamReachabilityTest.kt" "$KT/core/renderer/SeamReachabilityTest.kt" ;;
  esac
  for n in $news; do git add -N -- "$n"; done
  { awk '/^diff --git/{exit} {print}' "$PREV/$name.patch"
    case $name in
      unit-U3-ios) echo "# fix-r1 re-cut: FontVariantNumericApplier.swift's header now states the run-wide tnum radius (period.tf / two.tf; skeptic defect 1 + nit 4) — comment-only" ;;
      seam-4)      echo "# fix-r1 re-cut: the SeamReachabilityTest.kt comment no longer claims seam 4 is the ONLY caller (seam 5 calls it too; skeptic nit 3) — comment-only" ;;
      *)           echo "# fix-r1 re-cut (PLAN §8 step 3): every hunk identical to the builder's cut (only the index line's hash abbreviation may differ)" ;;
    esac
    git diff -- $paths; } > "$OUT/$name.patch"
  echo "cut $name: $(grep -c '^diff --git' "$OUT/$name.patch") file(s)"
done
# the contingency is cut ON TOP of the full series: take it back off so the worktree ends in the FULL state
git apply -R "$PREV/unit-U1-ios-LRM.patch"
rm -f "$TI"
