#!/bin/bash
# Wave 52 lane L8 — M-E mutation proofs (both twins), in the isolated exports.
# Kotlin: E = Compose export; Swift: X = Swift export carrying seam-2 + seam-4.
set -u
E=$1; X=$2; DD=$3; LOG=$4
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
K=$E/runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableBoxTree.kt
W=$X/runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/table/TableBoxTree.swift
# K-ME: columnWidthsPx maps every chain to null (the pre-wave-52 drop).
b=$(shasum -a 256 "$K" | cut -d' ' -f1); cp "$K" "$K.bak"
perl -0pi -e 's/chain\.firstNotNullOfOrNull \{ columnWidthPx\(it, inherited\) \}/null/' "$K"
cmp -s "$K" "$K.bak" && echo "K-ME NO-CHANGE" >> "$LOG"
(cd "$E/apps/android-harness" && ./gradlew -q :runtime:testDebugUnitTest --tests com.styleconverter.runtime.table.TableColumnWidthsTest > "$E/mut-K-ME.log" 2>&1); rc=$?
mv "$K.bak" "$K"; a=$(shasum -a 256 "$K" | cut -d' ' -f1)
echo "K-ME TableBoxTree.kt columnWidthsPx→null :: TableColumnWidthsTest :: $([ $rc -ne 0 ] && echo FAILS-AS-EXPECTED || echo 'STILL-GREEN(!)') (rc=$rc) $([ "$b" = "$a" ] && echo SHA-OK || echo SHA-MISMATCH) :: $(grep -m1 -E 'tests completed' "$E/mut-K-ME.log")" >> "$LOG"
# S-ME: the Swift twin, same mutation.
b=$(shasum -a 256 "$W" | cut -d' ' -f1); cp "$W" "$W.bak"
perl -0pi -e 's/chain\.lazy\.compactMap \{ columnWidthPx\(\$0, inherited: inherited\) \}\.first/nil/' "$W"
cmp -s "$W" "$W.bak" && echo "S-ME NO-CHANGE" >> "$LOG"
(cd "$X" && xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' -derivedDataPath "$DD" \
  -only-testing:StyleConverterRuntimeTests/TableColumnWidthsTests -only-testing:StyleConverterRuntimeTests/UprightColTableRasterTests > "$X/mut-S-ME.log" 2>&1); rc=$?
mv "$W.bak" "$W"; a=$(shasum -a 256 "$W" | cut -d' ' -f1)
echo "S-ME TableBoxTree.swift columnWidthsPx→nil :: TableColumnWidthsTests UprightColTableRasterTests :: $([ $rc -ne 0 ] && echo FAILS-AS-EXPECTED || echo 'STILL-GREEN(!)') (rc=$rc) $([ "$b" = "$a" ] && echo SHA-OK || echo SHA-MISMATCH) :: $(grep -E 'error: -\[' "$X/mut-S-ME.log" | sed -E 's/.*\] : //' | head -2 | tr '\n' ' ' | cut -c1-200)" >> "$LOG"
# S4-SEAM: seam-4's renderer hunks absent, raster pin kept → width must fail.
C=$X/runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift
b=$(shasum -a 256 "$C" | cut -d' ' -f1); cp "$C" "$C.bak"
perl -0pi -e 's/if let w = tableCellColumnWidths\[component\.id\], s\.size\.minWidth == nil \{/if false, let w = tableCellColumnWidths[component.id], s.size.minWidth == nil {/' "$C"
cmp -s "$C" "$C.bak" && echo "S4-SEAM NO-CHANGE" >> "$LOG"
(cd "$X" && xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' -derivedDataPath "$DD" \
  -only-testing:StyleConverterRuntimeTests/UprightColTableRasterTests > "$X/mut-S4-SEAM.log" 2>&1); rc=$?
mv "$C.bak" "$C"; a=$(shasum -a 256 "$C" | cut -d' ' -f1)
echo "S4-SEAM seam-4 cell fold disabled :: UprightColTableRasterTests :: $([ $rc -ne 0 ] && echo FAILS-AS-EXPECTED || echo 'STILL-GREEN(!)') (rc=$rc) $([ "$b" = "$a" ] && echo SHA-OK || echo SHA-MISMATCH) :: $(grep -E 'error: -\[|\[L8\]' "$X/mut-S4-SEAM.log" | sed -E 's/.*\] : //' | head -2 | tr '\n' ' ' | cut -c1-200)" >> "$LOG"
# S2-SEAM: seam-2's pin veto disabled → the orange raster must fail on height.
b=$(shasum -a 256 "$C" | cut -d' ' -f1); cp "$C" "$C.bak"
perl -0pi -e 's/\|\| wptUnbreakableRun \|\| broken\.preBroken\) && !uprightPlans/|| wptUnbreakableRun || broken.preBroken)/' "$C"
cmp -s "$C" "$C.bak" && echo "S2-SEAM NO-CHANGE" >> "$LOG"
(cd "$X" && xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' -derivedDataPath "$DD" \
  -only-testing:StyleConverterRuntimeTests/UprightChOrangeRasterTests > "$X/mut-S2-SEAM.log" 2>&1); rc=$?
mv "$C.bak" "$C"; a=$(shasum -a 256 "$C" | cut -d' ' -f1)
echo "S2-SEAM seam-2 line-box pin veto disabled :: UprightChOrangeRasterTests :: $([ $rc -ne 0 ] && echo FAILS-AS-EXPECTED || echo 'STILL-GREEN(!)') (rc=$rc) $([ "$b" = "$a" ] && echo SHA-OK || echo SHA-MISMATCH) :: $(grep -E 'error: -\[|\[L8\]' "$X/mut-S2-SEAM.log" | sed -E 's/.*\] : //' | head -2 | tr '\n' ' ' | cut -c1-200)" >> "$LOG"
echo "done $(date +%T)" >> "$LOG"
