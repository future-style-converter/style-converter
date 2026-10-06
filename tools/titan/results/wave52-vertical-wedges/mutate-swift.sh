#!/bin/bash
# Wave 52 lane L8 — EXECUTED mutation proofs for the SwiftUI pins, run in an
# ISOLATED HEAD export of the Swift package (HEAD + this lane's Swift files),
# so no shared-tree file is ever touched. Per mutation: sha, perl
# substitution (must change the file), Catalyst -only-testing the named
# classes, expect FAILURE, restore the byte-exact backup, re-sha.
set -u
X=$1; DD=$2; LOG=$3
T=runtimes/swiftui/Sources/StyleConverterRuntime
run() { (cd "$X" && xcodebuild test -scheme StyleConverterRuntime \
  -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' -derivedDataPath "$DD" "$@") ; }
mutate() { # id file perl-expr test-class…
  local id=$1 rel=$2 f=$X/$2 expr=$3; shift 3
  local only=(); for c in "$@"; do only+=("-only-testing:StyleConverterRuntimeTests/$c"); done
  local before; before=$(shasum -a 256 "$f" | cut -d' ' -f1)
  cp "$f" "$f.bak"; perl -0pi -e "$expr" "$f"
  if cmp -s "$f" "$f.bak"; then echo "$id NO-CHANGE (pattern missed)" >> "$LOG"; mv "$f.bak" "$f"; return; fi
  run "${only[@]}" > "$X/mut-$id.log" 2>&1; local rc=$?
  mv "$f.bak" "$f"
  local after; after=$(shasum -a 256 "$f" | cut -d' ' -f1)
  local verdict; [ $rc -ne 0 ] && verdict="FAILS-AS-EXPECTED" || verdict="STILL-GREEN(!)"
  local shaok; [ "$before" = "$after" ] && shaok=SHA-OK || shaok=SHA-MISMATCH
  local why; why=$(grep -E "error: -\[|error: .*\.swift:[0-9]+:[0-9]+: error" "$X/mut-$id.log" | sed -E 's/.*\] : //' | head -2 | tr '\n' ' ' | cut -c1-220)
  echo "$id $rel :: $* :: $verdict (rc=$rc) $shaok :: $why" >> "$LOG"
}
# Baseline: everything green on the unmutated export first.
run -only-testing:StyleConverterRuntimeTests/ChUnitInlineAxisTests -only-testing:StyleConverterRuntimeTests/VerticalInlineAxisTests \
    -only-testing:StyleConverterRuntimeTests/UAWidgetsTests -only-testing:StyleConverterRuntimeTests/UAWidgetZeroBoxRasterTests > "$X/baseline.log" 2>&1
echo "baseline exit=$? $(grep -m1 -E 'Executed [0-9]+ tests, with' "$X/baseline.log" | tail -1)" >> "$LOG"
mutate S1 $T/StyleEngine/spacing/ChUnitMetrics.swift 's/return interFont\(size: size\) \?\? \(UIFont\.systemFont\(ofSize: size\) as CTFont\)/return UIFont.systemFont(ofSize: size) as CTFont/' ChUnitInlineAxisTests
mutate S2 $T/StyleEngine/spacing/ChUnitMetrics.swift 's/if inlineAxisUpright \{ return verticalAdvance\(font\) \}//' ChUnitInlineAxisTests
mutate S3 $T/StyleEngine/typography/writing/VerticalInlineAxis.swift 's/return vertical && textOrientation == \.upright/return vertical/' VerticalInlineAxisTests
mutate S4 $T/Renderer/VerticalTextFlowLayout.swift 's/fallbackPx: budgetOverridePx\.flatMap \{ \$0\.isFinite \? Double\(\$0\) : nil \}\)/fallbackPx: nil)/' ChUnitInlineAxisTests
mutate S5 $T/StyleEngine/widgets/UAWidgetsResolve.swift 's/if hasZeroUsedBox\(properties\) \{/if false, hasZeroUsedBox(properties) {/' UAWidgetsTests UAWidgetZeroBoxRasterTests
mutate S6 $T/Renderer/StyleBuilder.swift 's/inlineAxisUpright: VerticalInlineAxis\.chAdvanceIsVertical\(\n\s*writingMode: [^\n]*\n\s*textOrientation: [^\n]*\.mixed\)\)/inlineAxisUpright: false)/' ChUnitInlineAxisTests
mutate S7 $T/StyleEngine/typography/writing/VerticalInlineAxis.swift 's/if let icb = icbBlockExtentPx, icb\.isFinite, icb > 0 \{ return icb \}//' VerticalInlineAxisTests
echo "done $(date +%T)" >> "$LOG"
