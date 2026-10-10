#!/bin/bash
# Skeptic L6: run the focused harness files in export tree $1 (+ RS files when present).
SK=$(cd "$(dirname "$0")" && pwd); t=$1
FILES="tests/sdui/ComponentRenderer.interInlineWs.test.tsx tests/ui/ComposedCanvasContainment.test.tsx tests/ui/ComposedCanvasDirection.test.tsx tests/ui/ComposedCanvasIcbClip.test.tsx tests/ui/ComposedCanvasIcbFlowRoot.test.tsx tests/ui/ComposedCanvasMargin.test.tsx tests/ui/ComposedCanvasPadding.test.tsx tests/ui/ComposedCanvasRootBackgroundImage.test.tsx tests/ui/ComposedCanvasRootClip.test.tsx tests/ui/ComposedCanvasTableBody.test.tsx tests/ui/ComposedCanvasWritingMode.test.tsx tests/ui/ComposedCaptureGallery.test.tsx tests/ui/LabelChrome.test.tsx tests/ui/LabelChrome.wpt.test.tsx"
cd $SK/$t/apps/web-harness
for f in tests/ui/ComposedRootSeparator.test.ts tests/ui/ComposedRootSeparatorWire.test.tsx; do [ -f $f ] && FILES="$FILES $f"; done
$SK/$t/node_modules/.bin/vitest run --reporter=verbose $FILES 2>&1 | grep -E 'Test Files|Tests  |FAIL| × ' | head -30
