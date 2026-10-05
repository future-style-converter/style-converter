#!/bin/bash
# Wave 52 lane L8 — FIX PASS: EXECUTED mutation proofs for the two skeptic
# fixes (M-A FontFamily.Default routing; M-C capture gate), run in an
# ISOLATED HEAD export (HEAD + this lane's files + hunk-for-L4-1 + seam-1 +
# seam-3), so no shared-tree file is ever touched. Per mutation: sha the
# file, apply the perl substitution (must change the file), run the named
# test class, expect FAILURE on an assertion, restore the byte-exact backup,
# re-sha (must match). Usage: mutate-fix.sh <export root> <log>
set -u
E=$1; LOG=$2
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
C=runtimes/compose/src/main/java/com/styleconverter/runtime
mutate() { # id file perl-expr test-class
  local id=$1 f=$E/$2 expr=$3 test=$4
  local before; before=$(shasum -a 256 "$f" | cut -d' ' -f1)
  cp "$f" "$f.bak"
  perl -0pi -e "$expr" "$f"
  # A pattern that misses would "pass" vacuously — refuse it loudly.
  if cmp -s "$f" "$f.bak"; then echo "$id NO-CHANGE (pattern missed)" >> "$LOG"; mv "$f.bak" "$f"; return; fi
  (cd "$E/apps/android-harness" && ./gradlew -q :runtime:testDebugUnitTest --tests "$test" > "$E/mut-$id.log" 2>&1); local rc=$?
  mv "$f.bak" "$f"
  local after; after=$(shasum -a 256 "$f" | cut -d' ' -f1)
  local verdict; [ $rc -ne 0 ] && verdict="FAILS-AS-EXPECTED" || verdict="STILL-GREEN(!)"
  local shaok; [ "$before" = "$after" ] && shaok=SHA-OK || shaok=SHA-MISMATCH
  # The failing assertion text, from the JUnit XML (proves assertion, not compile).
  local why; why=$(grep -h -o -E '<failure message="[^"]{0,200}" type="[^"]*"' "$E"/runtimes/compose/build/test-results/testDebugUnitTest/TEST-"$test".xml 2>/dev/null | head -2 | tr '\n' ' ')
  [ -z "$why" ] && why=$(grep -m2 -E "^e: |error:" "$E/mut-$id.log" | tr '\n' ' ' | cut -c1-200)
  echo "$id $2 :: $test :: $verdict (rc=$rc) $shaok :: $why" >> "$LOG"
}
CM=com.styleconverter.runtime.spacing.ChUnitMetricsTest
# C8 — the pre-fix gate: every non-singleton, non-document family keys "inter".
mutate C8 $C/spacing/ChUnitMetrics.kt 's/else if \(paintsInter\(fontFamily\) && defaultTypefaceLoader != null\) "inter"/else if (defaultTypefaceLoader != null) "inter"/' $CM
# C9 — the pre-fix route: FontFamily.Default measured through the Inter loader.
mutate C9 $C/spacing/ChUnitMetrics.kt 's/\?: if \(paintsInter\(fontFamily\)\) defaultMeasuringTypeface\(\) else android\.graphics\.Typeface\.DEFAULT/?: defaultMeasuringTypeface()/' $CM
# C11 — InterFontFamily dropped from paintsInter (a stack naming Inter measures Roboto).
mutate C11 $C/spacing/ChUnitMetrics.kt 's/fontFamily == null \|\| fontFamily == com\.styleconverter\.runtime\.typography\.InterFontFamily/fontFamily == null/' $CM
# C1b — the whole M-A key arm removed (the original C1, re-cut on the fixed line).
mutate C1b $C/spacing/ChUnitMetrics.kt 's/\n\s*else if \(paintsInter\(fontFamily\) && defaultTypefaceLoader != null\) "inter"//' $CM
# C10 — the §7.3.1 fallback no longer capture-gated (the pre-fix product behaviour).
mutate C10 $C/core/renderer/VerticalTextFlowLayout.kt 's/if \(!wptCaptureMode\) null/if (false) null/' com.styleconverter.runtime.core.renderer.UprightOrthogonalBudgetTest
echo "done $(date +%T)" >> "$LOG"
