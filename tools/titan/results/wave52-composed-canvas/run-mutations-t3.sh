#!/usr/bin/env bash
# wave-52 lane L2 — the T3 skeptic-fix mutation set, replayable (fix pass).
# Each line: one exact single-site mutation, the focused test class, the
# assertion-evidence kind. mutate.py restores byte-exact and logs the
# mutated text + failing assertion to mutations.log. Usage: bash run-mutations-t3.sh [android|swift|all]
set -u
# Repo root from this script's location (tools/titan/results/<lane>/).
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
# The runner and the two harness/runtime source files under mutation.
M="$ROOT/tools/titan/results/wave52-composed-canvas/mutate.py"
SCS="$ROOT/apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt"
UBM="$ROOT/apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/UaBlockMargins.kt"
CRS="$ROOT/runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/ComposedRootStack.swift"
# Fresh JUnit XML lands here; the class suffix narrows it to the focused report.
XML="$ROOT/apps/android-harness/app/build/test-results/testDebugUnitTest"
WHICH="${1:-all}"
# JDK 21 for Gradle (CLAUDE.md quick start).
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"

# One Android mutation: id, file, old, new, test class (simple name).
android() {
  python3 "$M" "$1" "$2" "$3" "$4" --evidence "junit:$XML:$5" -- \
    bash -c "cd '$ROOT/apps/android-harness' && ./gradlew -q :app:testDebugUnitTest --tests 'com.styleconverter.test.screenshot.$5'"
}
# One Catalyst mutation: id, old, new, test class (ComposedRootStack.swift only).
swift() {
  python3 "$M" "$1" "$CRS" "$2" "$3" --evidence xcode -- \
    bash -c "cd '$ROOT' && xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' -only-testing:StyleConverterRuntimeTests/$4"
}

if [ "$WHICH" = android ] || [ "$WHICH" = all ]; then
  # ScreenshotCaptureScreen.kt wiring (source-scan class).
  android ZO-M1 "$SCS" 'Box(modifier = Modifier.zIndex(1f)) { inFlow() }' 'Box(modifier = Modifier) { inFlow() }' ComposedCanvasRc1ZOrderSourceTest
  android ZO-M2 "$SCS" 'composedRootsPaintingAboveFlow(roots, hostActive)' 'composedRootsPaintingAboveFlow(roots, false)' ComposedCanvasRc1ZOrderSourceTest
  android ZO-M3 "$SCS" 'withCanvasOwnedBodyMargin(roots, canvasMargin).map(::withUaBlockMarginOnHoistedRoot)' 'withCanvasOwnedBodyMargin(roots, canvasMargin)' ComposedCanvasRc1ZOrderSourceTest
  android ZO-M4 "$SCS" '        composedRootsPaintingAboveFlow(roots, hostActive)' '        roots.map { root -> isComposedStaticPositionRoot(root, hostActive) }' ComposedCanvasRc1ZOrderSourceTest
  # UaBlockMargins.kt lift rules (verbatim-IR class).
  android UB-M6 "$UBM" 'com.styleconverter.runtime.core.placement.ItemPlacementExtractor.zIndex(root.properties) == null &&' 'true &&' UaBlockMarginsTest
  android UB-M7 "$UBM" 'paintsAsLayer(roots[j])' 'false' UaBlockMarginsTest
  android UB-M8 "$UBM" 'root.children.isNullOrEmpty() && root._text.isNullOrEmpty() &&' 'true && root._text.isNullOrEmpty() &&' UaBlockMarginsTest
fi
if [ "$WHICH" = swift ] || [ "$WHICH" = all ]; then
  # ComposedRootStack.swift lift rules (Catalyst, the runtime's own test class).
  swift SW-M8 '&& ItemPlacementExtractor.extract(from: root.properties).paint.zIndex == nil' '&& true' ComposedRootStackTests
  swift SW-M9 '!lifted[j] && paintsAsLayer(flow[j])' '!lifted[j] && false' ComposedRootStackTests
  swift SW-M10 '&& (root.children ?? []).isEmpty && (root.text ?? "").isEmpty' '&& true && (root.text ?? "").isEmpty' ComposedRootStackTests
fi
