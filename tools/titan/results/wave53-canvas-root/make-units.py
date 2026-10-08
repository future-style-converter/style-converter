#!/usr/bin/env python3
# tools/titan/results/wave53-canvas-root/make-units.py — L3's four REVERT UNITS as patches.
#
# PLAN.md §4 step 6 / §6 rule 6: L3 lands as four commits — A, B-web, B-ios, B-android — so the
# probe-gated native item-B call sites can each be reverted by a commit revert. Three harness files
# carry hunks of two units (the gallery: A + B-web; ScreenshotCaptureScreen.kt: A + B-android;
# CaptureCanvas.swift: A + B-ios). This script derives each file's A-only state from the tree by
# removing the B hunks (exact-string edits, each asserted to match once), builds the four states in
# a throwaway git repo seeded with HEAD's bytes, and writes unit-<name>.patch per step. It then
# replays all four patches on HEAD's bytes and checks the result is byte-identical to the tree, and
# checks each B patch reverse-applies alone on the final state. Read-only on the shared tree.
import hashlib, os, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))

G = 'apps/web-harness/src/ui/ComposedCaptureGallery.tsx'
K = 'apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt'
C = 'apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift'
UNITS = [
    ('A', [
        'runtimes/web/src/engine/background/RootBackgroundPropagation.ts',
        'runtimes/web/tests/background/RootBackgroundPropagation.test.ts',
        'apps/web-harness/tests/ui/ComposedCanvasRootBackgroundImage.test.tsx',
        'runtimes/compose/src/main/java/com/styleconverter/runtime/background/RootBackgroundPropagation.kt',
        'apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasRootBackgroundTest.kt',
        'runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/background/RootBackgroundPropagation.swift',
        'runtimes/swiftui/Tests/StyleConverterRuntimeTests/WPTCaptureModeTests.swift',
        G, K, C]),
    ('B-web', ['apps/web-harness/src/ui/CanvasTableBody.ts',
               'apps/web-harness/tests/ui/ComposedCanvasTableBody.test.tsx', G]),
    ('B-ios', ['runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/table/TableBodyForest.swift',
               'runtimes/swiftui/Tests/StyleConverterRuntimeTests/TableBodyForestTests.swift', C]),
    ('B-android', ['runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableBodyForest.kt',
                   'runtimes/compose/src/test/java/com/styleconverter/runtime/table/TableBodyForestTest.kt',
                   'apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasTableBodyTest.kt',
                   K]),
]

# The B hunks, as (final text, A-only text) edits per shared file.
B_EDITS = {
    G: [
        ("// wave-53 lane L3 (item B) — a `display: table` body makes the flow wrapper\n"
         "// below a table box, so Chrome runs CSS 2.1 §17.2.1's fixup itself.\n"
         "import { resolveCanvasTableBody } from './CanvasTableBody';\n", ""),
        ("  // wave-53 L3 (item B): the body's table-box declarations, or null for every\n"
         "  // non-table body (1434 of 1435 documents — their markup is unchanged).\n"
         "  const canvasTableBody = React.useMemo(() => resolveCanvasTableBody(doc), [doc]);\n", ""),
        ("        {/* wave-53 lane L3 (item B) — a TABLE body's wrapper is a table box\n"
         "            (`display: table` + the body's border-spacing), emitted even at\n"
         "            zero margin: the wrapper IS the body's box, so Chrome wraps the\n"
         "            non-row roots in an anonymous row + cells (CSS 2.1 §17.2.1 rule\n"
         "            2) and drops a cell root's margin (§8.3) — s-11-1-1b-006's td\n"
         "            lands at the reference's (8, 40). See CanvasTableBody.ts. */}\n"
         "        {canvasTableBody || canvasMargin.top !== 0 || canvasMargin.right !== 0\n",
         "        {canvasMargin.top !== 0 || canvasMargin.right !== 0\n"),
        ("              display: canvasTableBody ? canvasTableBody.display : 'flow-root',\n",
         "              display: 'flow-root',\n"),
        ("              // Written only for a table body that declares one (key absent otherwise).\n"
         "              ...(canvasTableBody?.borderSpacing ? { borderSpacing: canvasTableBody.borderSpacing } : {}),\n", ""),
    ],
    K: [
        ("    // wave-53 L3 (item B) rides it too: a `display: table` body's in-flow\n"
         "    // run becomes ONE synthetic table (CSS 2.1 §17.2.1 rule 2) — see\n"
         "    // composedCanvasRoots, the whole rewrite as one testable function.\n", ""),
        (" * wave-53 lane L3 — the two forest rewrites the composed canvas adds AFTER the\n",
         " * wave-53 lane L3 — the forest rewrite the composed canvas adds AFTER the\n"),
        (" * does not concern, so 1431 of the 1435 documents come back unchanged:\n",
         " * does not concern, so 1432 of the 1435 documents come back unchanged:\n"),
        (" *  - item B: a table body's in-flow run becomes one synthetic table (runtime\n"
         " *    TableBodyForest, CSS 2.1 §17.2.1 rule 2 + §8.3).\n", ""),
        (" * here touches one). One function so ComposedCanvasTableBodyTest pins the\n",
         " * here touches one). One function so ComposedCanvasRootBackgroundTest pins the\n"),
        ("    // Item B: a table body's in-flow run as one synthetic table (identity otherwise).\n"
         "    return com.styleconverter.runtime.table.TableBodyForest.rewrite(owned)\n",
         "    return owned\n"),
    ],
    C: [
        ("        // wave-53 lane L3 (item B): a `display: table` body's in-flow run is\n"
         "        // split as ONE synthetic table (runtime TableBodyForest, CSS 2.1\n"
         "        // §17.2.1 rule 2 + §8.3) — the same array for every other body. It\n"
         "        // commutes with the margin strip and the T6 map (those rewrite only\n"
         "        // the body-root and hoisted roots, which it leaves untouched).\n", ""),
        ("            TableBodyForest.rewrite(\n"
         "                RootBackgroundPropagation.withCanvasOwnedRootBackground(document.components, rootImagePlan)),\n",
         "            RootBackgroundPropagation.withCanvasOwnedRootBackground(document.components, rootImagePlan),\n"),
    ],
}


def sh(cmd, cwd, inp=None):
    r = subprocess.run(cmd, cwd=cwd, shell=True, capture_output=True, text=True, input=inp)
    if r.returncode != 0:
        sys.exit(f'FAILED: {cmd}\n{r.stdout}{r.stderr}')
    return r.stdout


def a_only(path, text):
    for final, aonly in B_EDITS[path]:
        assert text.count(final) == 1, (path, final[:60])
        text = text.replace(final, aonly)
    # Unit A must not name an item-B symbol (it lands, and is probed, without them).
    assert 'TableBodyForest' not in text and 'CanvasTableBody' not in text, f'B symbol left in A-only {path}'
    return text


def main():
    head = sh('git rev-parse --short HEAD', ROOT).strip()
    tree = {p: open(os.path.join(ROOT, p)).read() for _, ps in UNITS for p in ps}
    work = tempfile.mkdtemp(prefix='l3-units-')
    sh('git init -q && git config user.email l3@local && git config user.name l3', work)

    def put(p, text):
        full = os.path.join(work, p); os.makedirs(os.path.dirname(full), exist_ok=True)
        open(full, 'w').write(text)

    # State 0: HEAD's bytes of every pre-existing touched path.
    for p in sorted(tree):
        r = subprocess.run(f'git show HEAD:{p}', cwd=ROOT, shell=True, capture_output=True, text=True)
        if r.returncode == 0: put(p, r.stdout)
    sh('git add -A && git commit -q --allow-empty -m base', work)
    # Unit states, in landing order; a shared file takes its A-only text in unit A.
    done = set()
    for name, paths in UNITS:
        for p in paths:
            put(p, a_only(p, tree[p]) if (name == 'A' and p in B_EDITS) else tree[p])
            done.add(p)
        sh(f'git add -A && git commit -q -m {name}', work)
        diff = sh('git diff HEAD~1 HEAD', work)
        hdr = (f'# wave-53 lane L3 · canvas-root — revert unit {name} (PLAN.md §4 step 6, §6 rule 6)\n'
               f'# base: campaign/applier-campaign {head} '
               f'{"(HEAD)" if name == "A" else "+ the preceding L3 units (A → B-web → B-ios → B-android)"}\n'
               f'# apply: git apply tools/titan/results/wave53-canvas-root/unit-{name}.patch\n')
        open(os.path.join(HERE, f'unit-{name}.patch'), 'w').write(hdr + diff)
        print(f'unit-{name}.patch: {len(paths)} paths, {diff.count(chr(10))} lines')
    # Replay check: all four patches on HEAD's bytes == the tree, byte for byte.
    sh('git checkout -q HEAD~4 2>/dev/null || git checkout -q $(git rev-list --max-parents=0 HEAD)', work)
    for name, _ in UNITS:
        sh(f'git apply {os.path.join(HERE, f"unit-{name}.patch")}', work)
    for p in sorted(done):
        a = hashlib.sha256(open(os.path.join(work, p), 'rb').read()).hexdigest()
        b = hashlib.sha256(open(os.path.join(ROOT, p), 'rb').read()).hexdigest()
        assert a == b, f'replay mismatch {p}'
    print(f'replay: 4 patches on HEAD bytes == tree for all {len(done)} paths (sha256)')
    # Each B unit reverse-applies alone on the final state (its own revert commit).
    for name in ('B-web', 'B-ios', 'B-android'):
        sh(f'git apply -R --check {os.path.join(HERE, f"unit-{name}.patch")}', work)
    print('B-web / B-ios / B-android each reverse-apply alone on the final state')


main()
