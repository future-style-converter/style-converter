#!/usr/bin/env python3
# tools/titan/results/wave53-canvas-root/skeptic-build-spec.py — writes the L3
# skeptic's mutation spec (skeptic-mutations.json): the lane's own 36 mutations
# (mutations.json, replayed verbatim as edits, 'expect: red' on the SAME named
# tests) plus the skeptic's EXTRA probes of branches no lane mutation reaches
# ('expect: probe' — outcome recorded, not asserted). Commands are rewritten so
# red is read from structured reports (see skeptic-mutate.py).
import json, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
DD = '{DD}'                                                    # xcodebuild derivedDataPath: filled from $SK_DD at run time
lane = json.load(open(os.path.join(HERE, 'mutations.json')))
G21 = 'export JAVA_HOME=$(/usr/libexec/java_home -v 21); '
XC = ("xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' "
      "-derivedDataPath " + DD + " -only-testing:StyleConverterRuntimeTests/{cls} 2>&1")
CMD = {
  'web-runtime': ('vitest', 'npx vitest run tests/background/RootBackgroundPropagation.test.ts', 'runtimes/web'),
  'web-gallery': ('vitest', 'npx vitest run tests/ui/ComposedCanvasRootBackgroundImage.test.tsx', 'apps/web-harness'),
  'web-B': ('vitest', 'npx vitest run tests/ui/ComposedCanvasTableBody.test.tsx', 'apps/web-harness'),
  'compose-A': ('gradle', G21 + "./gradlew :app:testDebugUnitTest --tests '*ComposedCanvasRootBackground*'", 'apps/android-harness'),
  'compose-B': ('gradle', G21 + "./gradlew --continue :runtime:testDebugUnitTest --tests '*TableBodyForest*' :app:testDebugUnitTest --tests '*ComposedCanvasTableBody*'", 'apps/android-harness'),
  'swift-A': ('xcode', XC.replace('{cls}', 'WPTCaptureModeTests'), '.'),
  'swift-B': ('xcode', XC.replace('{cls}', 'TableBodyForestTests'), '.'),
}
spec = []
for batch, s in lane.items():
    kind, cmd, cwd = CMD[batch]
    for m in s['mutations']:
        spec.append(dict(id=f"{batch}:{m['id']}", file=m['file'], old=m['old'], new=m['new'], kind=kind, cmd=cmd,
                         cwd=cwd, tests=m['red'], expect='red'))
W_ALL = ('vitest', 'npx vitest run tests/ui/ComposedCanvasRootBackgroundImage.test.tsx tests/ui/ComposedCanvasTableBody.test.tsx tests/ui/ComposedCanvasMargin.test.tsx', 'apps/web-harness')
WR = CMD['web-runtime']; CA = (CMD['compose-A'][0], G21 + "./gradlew --continue :runtime:testDebugUnitTest --tests '*WptCanvas*' --tests '*TableBodyForest*' :app:testDebugUnitTest --tests '*ComposedCanvas*'", 'apps/android-harness')
SA = ('xcode', XC.replace('{cls}', 'WPTCaptureModeTests') .replace('2>&1', '-only-testing:StyleConverterRuntimeTests/TableBodyForestTests 2>&1'), '.')
GAL = 'apps/web-harness/src/ui/ComposedCaptureGallery.tsx'; WRT = 'runtimes/web/src/engine/background/RootBackgroundPropagation.ts'
CTB = 'apps/web-harness/src/ui/CanvasTableBody.ts'
KRT = 'runtimes/compose/src/main/java/com/styleconverter/runtime/background/RootBackgroundPropagation.kt'
KTB = 'runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableBodyForest.kt'
KSC = 'apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt'
SRT = 'runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/background/RootBackgroundPropagation.swift'
STB = 'runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/table/TableBodyForest.swift'
X = [  # (id, file, old, new, (kind, cmd, cwd))
  ('XW1 framed base 0', GAL, 'rootImageOnIcb ? 0 : CANVAS_FRAME_PX)', '0)', W_ALL),
  ('XW2 attachment verbatim', WRT, "backgroundAttachment: Array(n).fill('scroll').join(', '),", "backgroundAttachment: plan.attachments.join(', '),", WR),
  ('XW2b attachment verbatim (gallery pins)', WRT, "backgroundAttachment: Array(n).fill('scroll').join(', '),", "backgroundAttachment: plan.attachments.join(', '),", W_ALL),
  ('XW3 TABLE_CELL body is a table', CTB, "TABLE: 'table', INLINE_TABLE: 'inline-table',", "TABLE: 'table', INLINE_TABLE: 'inline-table', TABLE_CELL: 'table',", W_ALL),
  ('XW5 wrapper display flow-root', GAL, "display: canvasTableBody ? canvasTableBody.display : 'flow-root',", "display: 'flow-root',", W_ALL),
  ('XW6 no-wrapper branch unstripped', GAL, 'canvasRoots.map((root, i) => (', 'roots.map((root, i) => (', W_ALL),
  ('XW7 clip branch ignored', GAL, '(!rootImagePlan.uniform || !!canvasClipPath)', '(!rootImagePlan.uniform)', W_ALL),
  ('XW9 repeat gate dropped', WRT, 'return list.every(entryRepeatsBoth);', 'return true;', WR),
  ('XW10 gradient always uniform', WRT, 'return keys.length > 0 && keys.every((k) => k !== null && k === keys[0]);', 'return true;', WR),
  ('XC1 frame overpaint dropped', KRT, 'if (plan.uniform) return m ', 'return m ', CA),
  ('XC2 canvas base 0', KRT, 'layerConfigs(props, plan, frame.value).asReversed()', 'layerConfigs(props, plan, 0f).asReversed()', CA),
  ('XC3 layer order not reversed', KRT, 'layerConfigs(props, plan, frame.value).asReversed().forEach', 'layerConfigs(props, plan, frame.value).forEach', CA),
  ('XC4 android call site removed', KSC, '            .then(rootImageModifier)\n', '\n', CA),
  ('XC5 paintsOwnEdges bail dropped', KTB, '            !isZeroPx(p.data)  ', '            false && !isZeroPx(p.data)  ', CA),
  ('XC6 proper-non-cell bail dropped', KTB, 'run.any { displayOf(it) in PROPER_NON_CELL }', 'false', CA),
  ('XC7 repeat gate dropped', KRT, '&& rawList(props, "BackgroundRepeat").all(::entryRepeatsBoth)', '', CA),
  ('XC8 gradient always uniform', KRT, 'return keys.isNotEmpty() && keys.all { it != "?" && it == keys[0] }', 'return true', CA),
  ('XS1 non-uniform not padded', SRT, '.padding(plan.uniform ? 0 : frame)', '.padding(0)', SA),
  ('XS3 layer order not reversed', SRT, 'ForEach(Array((0..<n).reversed()), id: \\.self)', 'ForEach(Array(0..<n), id: \\.self)', SA),
  ('XS4 paintsOwnEdges bail dropped', STB, '                && !isZeroPx(p.data)', '                && false', SA),
  ('XS5 proper-non-cell bail dropped', STB, '!run.contains(where: { properNonCell.contains(displayOf($0) ?? "") })', 'true', SA),
  ('XS6 repeat gate dropped', SRT, '&& rawList(props, "BackgroundRepeat").allSatisfy(entryRepeatsBoth)', '', SA),
]
for i, f, o, n, (kind, cmd, cwd) in X:
    spec.append(dict(id=i.split()[0], label=i, file=f, old=o, new=n, kind=kind, cmd=cmd, cwd=cwd, tests=[], expect='probe'))
json.dump(spec, open(os.path.join(HERE, 'skeptic-mutations.json'), 'w'), indent=1)
print(len(spec), 'mutations written')
