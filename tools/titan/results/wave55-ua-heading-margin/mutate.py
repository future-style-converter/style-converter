#!/usr/bin/env python3
# Wave 55 lane L4 — EXECUTED pin mutations, bracketed in the lane note (PLAN §0 "Mutations are
# bracketed"): for each mutation, `MUTATING <file> sha256 <pre>` is appended to _note.md BEFORE the
# edit; one exact literal is swapped (asserted unique); the focused suite runs and must be RED with
# the named pin among its failures; the file is restored byte-exact from memory and
# `RESTORED <file> sha256 <post>` (post == pre, asserted) is appended; the suite runs again and
# must be GREEN. Outputs: mutations/<name>.out.txt (+ .red.log / .green.log).
#   mutate.py <lane-worktree> <name>[,<name>…] | all
import hashlib, json, os, re, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
NOTE = os.path.join(HERE, '_note.md')
OUTD = os.path.join(HERE, 'mutations')
WT = sys.argv[1]
os.makedirs(OUTD, exist_ok=True)
K = 'runtimes/compose/src/main/java/com/styleconverter/runtime/'
KT = 'runtimes/compose/src/test/java/com/styleconverter/runtime/'
S = 'runtimes/swiftui/Sources/StyleConverterRuntime/'
M = K + 'spacing/UaPositionedHeadingMargin.kt'
MS = S + 'StyleEngine/spacing/UAPositionedHeadingMargin.swift'
CR, CRS = K + 'core/renderer/ComponentRenderer.kt', S + 'Renderer/ComponentRenderer.swift'
G = K + 'typography/UAHeadingFoldGate.kt'
GS = S + 'StyleEngine/typography/UAHeadingFoldGate.swift'
RT = KT + 'typography/UAElementFontRuleTest.kt'
RTS = 'runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAHeadingFoldGateTests.swift'
KTESTS = ['*UaPositionedHeadingMargin*', '*UAElementFontRule*', '*UAHeadingFoldGate*']
STESTS = ['UAPositionedHeadingMarginTests', 'UAPositionedHeadingMarginSeamWiringTests',
          'UAElementFontRuleTests', 'UAHeadingFoldGateTests']
# (name, file, old literal, new literal, platform, a failing pin the red run must name)
MUTATIONS = [
    # (name, file, [(old, new), …] or '@HEAD' (the file's bytes at the base tree), platform, a pin the red run must name)
    # ── U1-android (UaPositionedHeadingMargin.kt + its seam) ──
    ('A1-painted-basis', M, [('val face = uaCascadeFacePx(tag, own, preUa)',
      'val face = (com.styleconverter.runtime.core.variables.DynamicValueResolver.fontSizePxOf(merged) ?: 16f).takeIf { true }')],
     'k', 'takes 0_67 x its 32px UA face'),
    ('A2-em-base-16', M, [('UAElementFontRule.emBasePx(preUa)).toFloat()', '16.0).toFloat()')], 'k', 'monospace abspos h1'),
    ('A3-root-table', M, [('JsonPrimitive(em * face)', 'JsonPrimitive(uaVerticalBlockMargins(sourceTag).first)')], 'k',
     'monospace abspos h1'),
    ('A4-no-edge-guard', M, [('val top = !declaresUaChildTop(types)', 'val top = true')], 'k', 'inset-001'),
    ('A5-ignore-enabled', M, [('if (!enabled) return merged', 'if (false) return merged')], 'k', 'capture off'),
    ('A6-no-oof-test', M, [('if (!ComponentRenderer.isOutOfFlowChild(own)) return merged', 'if (false) return merged')],
     'k', 'in flow'),
    ('A7-widen-table', M, [('if (tag !in HEADINGS) return skip(', 'if (false) return skip('),
      ('UAElementFontRule.sizeMultiplier(tag) ?: return null', 'UAElementFontRule.sizeMultiplier(tag) ?: 1.0')],
     'k', 's-11-1-1b-005'),
    ('AS1-head-renderer', CR, '@HEAD', 'k', 'runs once'),
    ('AS2-enabled-true', CR, [('            rawProperties,\n            enabled = LocalWptCaptureMode.current,',
      '            rawProperties,\n            enabled = true,')], 'k', 'runs once'),
    ('AS3-fed-preUa', CR, [('            listUaProperties,\n            rawProperties,\n            enabled',
      '            listUaProperties,\n            listUaProperties,\n            enabled')], 'k', 'runs once'),
    # ── U2-android (UAHeadingFoldGate.kt, the retargeted rule pins, seam 3) ──
    ('G1-gate-always-false', G, [('        // The element\'s composed children (null / empty = a leaf).',
      '        if (true) return false\n        // The element\'s composed children (null / empty = a leaf).')], 'k', 'inset-011'),
    ('G2-gate-always-true', G, [('        // The element\'s composed children (null / empty = a leaf).',
      '        if (true) return true\n        // The element\'s composed children (null / empty = a leaf).')], 'k', 'block-in-inline'),
    ('M3-old-has-children-gate', G, [('        // The element\'s composed children (null / empty = a leaf).',
      '        if (true) return !component.children.isNullOrEmpty()\n        // The element\'s composed children (null / empty = a leaf).')],
     'k', 'inset-005'),
    ('GS0-seam3-absent', RT, [('standsDown = UAHeadingFoldGate.standsDown(site.component, site.mergedPreUa),\n    )',
      'standsDown = UAElementFontRule.headingStandsDown(site.component),\n    )')], 'k', 'fold gate'),
    ('GS3-head-renderer-seam3', CR, [('            standsDown = com.styleconverter.runtime.typography.UAHeadingFoldGate\n                .standsDown(component, listUaProperties),',
      '            standsDown = com.styleconverter.runtime.typography.UAElementFontRule.headingStandsDown(component),')],
     'k', 'wraps the list UA rule'),
    ('M5-ua-step-writes-color', K + 'typography/UAElementFontRule.kt',
     [('        // Either `merged` untouched (both halves guarded) or a new list.\n        return out',
       '        // Either `merged` untouched (both halves guarded) or a new list.\n        return out.map { if (it.type == "Color") IRProperty("Color", JsonObject(mapOf("srgb" to JsonObject(mapOf("r" to JsonPrimitive(0), "g" to JsonPrimitive(0), "b" to JsonPrimitive(1)))))) else it }')],
     'k', 'soundness'),
    # ── U1-ios (UAPositionedHeadingMargin.swift + seam 2) ──
    ('I1-painted-basis', MS, [('guard let face = uaCascadeFacePx(tag: tag, own: own, preUA: preUA) else {',
      'guard let face = Optional(merged.first(where: { $0.type == "FontSize" }).flatMap { ValueExtractors.extractPx($0.data) } ?? 16) else {')],
     's', 'testInset005_006_011'),
    ('I2-em-base-16', MS, [('UAElementFontRule.emBasePx(merged: preUA))', '16)')], 's', 'testInset014'),
    ('I3-root-table', MS, [('let leaf = IRValue.object(["px": .double(Double(em * face))])',
      'let leaf = IRValue.object(["px": .double(Double(UABlockMargin.vertical(forTag: sourceTag).top))])')], 's', 'testInset014'),
    ('I4-no-edge-guard', MS, [('let top = !UABlockMargin.declaresBlockMarginTop(own)', 'let top = true')], 's', 'testInset001'),
    ('I5-ignore-enabled', MS, [('guard enabled else { return merged }', 'guard true else { return merged }')], 's',
     'testCaptureOff'),
    ('I6-no-oof-test', MS, [('guard position == .absolute || position == .fixed else { return merged }',
      'guard true else { return merged }')], 's', 'testInFlowHeadings'),
    ('I7-widen-table', MS, [('guard headings.contains(tag) else {', 'guard true else {'),
      ('guard let multiplier = UAElementFontRule.sizeMultiplier(forTag: tag) else { return nil }',
       'let multiplier = UAElementFontRule.sizeMultiplier(forTag: tag) ?? 1.0')], 's', 'testS1111b005'),
    ('IS1-head-renderer', CRS, '@HEAD', 's', 'testTheMarginStepRunsOnce'),
    ('IS2-enabled-true', CRS, [('enabled: wptCaptureMode)\n    }', 'enabled: true)\n    }')], 's', 'testTheMarginStepRunsOnce'),
    ('IS3-fed-preUA', CRS, [('merged: fontProperties,', 'merged: mergedPreUA,')], 's', 'testTheMarginStepRunsOnce'),
    # ── U2-ios (UAHeadingFoldGate.swift + seam 4) ──
    ('M3s-old-has-children-gate', GS, [('return flow == nil', 'return true')], 's', 'testInset005FoldedHostTakesTheFace'),
    ('M5s-ua-step-writes-color', S + 'StyleEngine/typography/UAElementFontRule.swift',
     [('        return out\n    }\n\n    /// Replace the FIRST',
       '        return out.map { $0.type == "Color" ? IRProperty(type: "Color", data: .object(["srgb": .object(["r": .double(0), "g": .double(0), "b": .double(1)])])) : $0 }\n    }\n\n    /// Replace the FIRST')],
     's', 'testFoldVerdictIsInvariantUnderTheUAFace'),
]

def sha(p):
    return hashlib.sha256(open(p, 'rb').read()).hexdigest()

def note(line):
    # Appended BEFORE / AFTER the edit so a killed run leaves the sha256 to restore against.
    with open(NOTE, 'a', encoding='utf-8') as f:
        f.write(line + '\n')

def run(platform, log):
    # The focused suite of the touched runtime, in the lane worktree only.
    if platform == 'k':
        args = ['nice', './gradlew', ':runtime:testDebugUnitTest'] + sum((['--tests', t] for t in KTESTS), [])
        env = dict(os.environ, JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21'], text=True).strip())
        p = subprocess.run(args, cwd=os.path.join(WT, 'apps/android-harness'), env=env, capture_output=True, text=True)
    else:
        args = ['nice', 'xcodebuild', 'test', '-scheme', 'StyleConverterRuntime', '-destination',
                'platform=macOS,variant=Mac Catalyst,arch=arm64', '-derivedDataPath', os.path.join(WT, '.dd')] + \
               [f'-only-testing:StyleConverterRuntimeTests/{t}' for t in STESTS]
        p = subprocess.run(args, cwd=WT, capture_output=True, text=True)
    open(log, 'w').write(p.stdout + p.stderr)
    text = p.stdout + p.stderr
    if platform == 'k':
        failed = re.findall(r'^(\w+) > (.+) FAILED$', text, re.M)
        summary = re.findall(r'(\d+) tests completed(?:, (\d+) failed)?', text)
        res = os.path.join(WT, 'runtimes/compose/build/test-results/testDebugUnitTest')
        counts = []
        for fn in sorted(os.listdir(res)) if os.path.isdir(res) else []:
            # Only the per-class XML reports (the dir also holds `binary/`).
            if not fn.endswith('.xml'):
                continue
            m = re.search(r'tests="(\d+)" skipped="\d+" failures="(\d+)" errors="(\d+)"', open(os.path.join(res, fn)).read())
            if m:
                counts.append(f'{fn[5:-4].split(".")[-1]} {m.group(1)}/{int(m.group(2)) + int(m.group(3))}')
        return p.returncode, [f'{a} > {b}' for a, b in failed], '; '.join(counts) or str(summary)
    failed = sorted(set(re.findall(r"error: -\[StyleConverterRuntimeTests\.(\w+) (\w+)\]", text)))
    ex = re.findall(r'Executed (\d+) tests?, with (\d+) failures?', text)
    return p.returncode, [f'{a}.{b}' for a, b in failed], (f'Executed {ex[-1][0]}, {ex[-1][1]} failures' if ex else 'no Executed line')

names = sys.argv[2].split(',')
for name, rel, edits, platform, pin in MUTATIONS:
    if names != ['all'] and name not in names:
        continue
    path = os.path.join(WT, rel)
    orig = open(path, 'rb').read()
    pre = sha(path)
    if edits == '@HEAD':
        # The seam file's bytes at the base tree: the renderer with no lane hunk at all.
        mutated = subprocess.check_output(['git', '-C', WT, 'show', f'611c9f93:{rel}'])
    else:
        text = orig.decode('utf-8')
        for old, new in edits:
            assert text.count(old) == 1, f'{name}: literal not unique / absent in {rel}: {old[:60]!r}'
            text = text.replace(old, new)
        mutated = text.encode('utf-8')
    note(f'MUTATING {rel} sha256 {pre} ({name})')
    open(path, 'wb').write(mutated)
    mut = sha(path)
    try:
        rc_red, failed, counts_red = run(platform, os.path.join(OUTD, f'{name}.red.log'))
    finally:
        # Restore byte-exact whatever happened in the red run.
        open(path, 'wb').write(orig)
    post = sha(path)
    assert post == pre, f'{name}: restore not byte-exact'
    note(f'RESTORED {rel} sha256 {post} ({name})')
    rc_green, failed_green, counts_green = run(platform, os.path.join(OUTD, f'{name}.green.log'))
    red_ok = rc_red != 0 and any(pin in f for f in failed)
    rec = {'name': name, 'file': rel, 'pre': pre, 'mutated': mut, 'post': post, 'red_rc': rc_red,
           'red_failures': failed, 'red_counts': counts_red, 'green_rc': rc_green, 'green_counts': counts_green,
           'verdict': 'RED-THEN-GREEN' if red_ok and rc_green == 0 else 'NOT-PROVEN'}
    open(os.path.join(OUTD, f'{name}.out.txt'), 'w').write(json.dumps(rec, indent=1) + '\n')
    print(f"{name}: {rec['verdict']} red rc={rc_red} ({len(failed)} failing: {failed[:4]}) green rc={rc_green} [{counts_green}]")
