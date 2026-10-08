#!/usr/bin/env bash
# tools/titan/results/wave53-canvas-root/owned-shas.sh — sha256 of every path L3 created or edited,
# as landed (the orchestrator compares these before committing the four revert units).
cd "$(dirname "$0")/../../../.." || exit 1
for p in \
  runtimes/web/src/engine/background/RootBackgroundPropagation.ts \
  runtimes/web/tests/background/RootBackgroundPropagation.test.ts \
  apps/web-harness/src/ui/ComposedCaptureGallery.tsx \
  apps/web-harness/src/ui/CanvasTableBody.ts \
  apps/web-harness/tests/ui/ComposedCanvasRootBackgroundImage.test.tsx \
  apps/web-harness/tests/ui/ComposedCanvasTableBody.test.tsx \
  runtimes/compose/src/main/java/com/styleconverter/runtime/background/RootBackgroundPropagation.kt \
  runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableBodyForest.kt \
  runtimes/compose/src/test/java/com/styleconverter/runtime/table/TableBodyForestTest.kt \
  apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt \
  apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasRootBackgroundTest.kt \
  apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasTableBodyTest.kt \
  runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/background/RootBackgroundPropagation.swift \
  runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/table/TableBodyForest.swift \
  runtimes/swiftui/Tests/StyleConverterRuntimeTests/WPTCaptureModeTests.swift \
  runtimes/swiftui/Tests/StyleConverterRuntimeTests/TableBodyForestTests.swift \
  apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift; do
  printf '%s  %5s lines  %s\n' "$(shasum -a 256 "$p" | cut -c1-16)" "$(wc -l < "$p" | tr -d ' ')" "$p"
done
