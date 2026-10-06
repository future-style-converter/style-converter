#!/bin/bash
# Wave 52 lane L8 — EXECUTED mutation proofs for the Compose pins, run in the
# ISOLATED HEAD export (HEAD + this lane's files + hunk-for-L4-1 + seam-1), so
# no shared-tree file is ever touched. Per mutation: sha the file, apply the
# perl substitution (must change the file), run the named test class, expect
# FAILURE, restore the byte-exact backup, re-sha (must match).
set -u
E=$1; LOG=$2
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
C=runtimes/compose/src/main/java/com/styleconverter/runtime
mutate() { # id file perl-expr test-class
  local id=$1 f=$E/$2 expr=$3 test=$4
  local before; before=$(shasum -a 256 "$f" | cut -d' ' -f1)
  cp "$f" "$f.bak"
  perl -0pi -e "$expr" "$f"
  if cmp -s "$f" "$f.bak"; then echo "$id NO-CHANGE (pattern missed)" >> "$LOG"; mv "$f.bak" "$f"; return; fi
  (cd "$E/apps/android-harness" && ./gradlew -q :runtime:testDebugUnitTest --tests "$test" > "$E/mut-$id.log" 2>&1); local rc=$?
  mv "$f.bak" "$f"
  local after; after=$(shasum -a 256 "$f" | cut -d' ' -f1)
  local verdict; [ $rc -ne 0 ] && verdict="FAILS-AS-EXPECTED" || verdict="STILL-GREEN(!)"
  local shaok; [ "$before" = "$after" ] && shaok=SHA-OK || shaok=SHA-MISMATCH
  local why; why=$(grep -m2 -E "AssertionError|expected:|ComparisonFailure|e: " "$E/mut-$id.log" | tr '\n' ' ' | cut -c1-200)
  echo "$id $2 :: $test :: $verdict (rc=$rc) $shaok :: $why" >> "$LOG"
}
mutate C1 $C/spacing/ChUnitMetrics.kt 's/else if \(defaultTypefaceLoader != null\) "inter"\n/\n/' com.styleconverter.runtime.spacing.ChUnitMetricsTest
mutate C2 $C/spacing/ChUnitMetrics.kt 's/if \(inlineAxisUpright\) verticalAdvance\(typeface, fontSizePx\) else horizontalAdvance/horizontalAdvance/' com.styleconverter.runtime.spacing.ChUnitMetricsTest
mutate C3 $C/typography/text/VerticalInlineAxis.kt 's/return vertical && textOrientation == TextOrientationValue.UPRIGHT/return vertical/' com.styleconverter.runtime.typography.text.VerticalInlineAxisTest
mutate C4 $C/typography/text/VerticalInlineAxis.kt 's/return icbBlockExtentPx\?\.takeIf \{ it\.isFinite\(\) && it > 0\.0 \}/return null/' com.styleconverter.runtime.typography.text.VerticalInlineAxisTest
mutate C5 $C/core/renderer/VerticalRunIntrinsics.kt 's/fallbackPx = fallbackBudgetPx,/fallbackPx = null,/' com.styleconverter.runtime.core.renderer.UprightOrthogonalBudgetTest
mutate C6 $C/widgets/UAWidgetsResolve.kt 's/if \(hasZeroUsedBox\(component\.properties\)\) \{/if (false \&\& hasZeroUsedBox(component.properties)) {/' com.styleconverter.runtime.widgets.UAWidgetsResolveTest
mutate C7 $C/StyleApplier.kt 's/inlineAxisUpright = com\.styleconverter\.runtime\.typography\.text\.VerticalInlineAxis\n\s*\.chAdvanceIsVertical\(config\.writingMode\.writingMode, config\.writingMode\.textOrientation\),/inlineAxisUpright = false,/' com.styleconverter.runtime.spacing.ChUnitInlineAxisSpacingContextTest
echo "done $(date +%T)" >> "$LOG"
