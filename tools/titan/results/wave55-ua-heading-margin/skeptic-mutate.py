#!/usr/bin/env python3
# Wave 55 · L4 SKEPTIC — independent replay of every L4 pin mutation (the lane's 28, re-typed here from
# the note's table with the same literals) plus the skeptic's own extra mutations (X*), each EXECUTED in
# the skeptic worktree: bracket line `MUTATING <file> sha256 <pre>` appended to skeptic-mutations.txt
# BEFORE the edit, the focused suite run (expect RED naming the pin), the file restored byte-exact,
# `RESTORED <file> sha256 <post>` appended (post == pre asserted), the suite run again (expect GREEN).
# A mutation that stays GREEN is reported as SURVIVED (an untested branch), never hidden.
#   skeptic-mutate.py <worktree> k|s <name,…|all>
import hashlib, os, re, shutil, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
LOG = os.path.join(HERE, 'skeptic-mutations.txt')
WT, PLAT, WHICH = sys.argv[1], sys.argv[2], sys.argv[3].split(',')
K = 'runtimes/compose/src/main/java/com/styleconverter/runtime/'
S = 'runtimes/swiftui/Sources/StyleConverterRuntime/'
M, MS = K + 'spacing/UaPositionedHeadingMargin.kt', S + 'StyleEngine/spacing/UAPositionedHeadingMargin.swift'
CR, CRS = K + 'core/renderer/ComponentRenderer.kt', S + 'Renderer/ComponentRenderer.swift'
G, GS = K + 'typography/UAHeadingFoldGate.kt', S + 'StyleEngine/typography/UAHeadingFoldGate.swift'
RT = 'runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleTest.kt'
GATE_ANCHOR = "        // The element's composed children (null / empty = a leaf)."
SEAM4 = ('hasElementChildren: UAHeadingFoldGate.standsDown(\n                component: component,\n'
         '                inFlowChildren: inFlowChildren,\n                mergedPreUA: mergedPreUA))')
TABLE = [
    # (name, platform, file, [(old, new)…] | '@HEAD', expected failing pin substring | None = expect SURVIVE)
    ('A1-painted-basis', 'k', M, [('val face = uaCascadeFacePx(tag, own, preUa)',
      'val face = (com.styleconverter.runtime.core.variables.DynamicValueResolver.fontSizePxOf(merged) ?: 16f).takeIf { true }')], '32px UA face'),
    ('A2-em-base-16', 'k', M, [('UAElementFontRule.emBasePx(preUa)).toFloat()', '16.0).toFloat()')], 'monospace abspos h1'),
    ('A3-root-table', 'k', M, [('JsonPrimitive(em * face)', 'JsonPrimitive(uaVerticalBlockMargins(sourceTag).first)')], 'monospace abspos h1'),
    ('A4-no-edge-guard', 'k', M, [('val top = !declaresUaChildTop(types)', 'val top = true')], 'inset-001'),
    ('A5-ignore-enabled', 'k', M, [('if (!enabled) return merged', 'if (false) return merged')], 'capture off'),
    ('A6-no-oof-test', 'k', M, [('if (!ComponentRenderer.isOutOfFlowChild(own)) return merged', 'if (false) return merged')], 'in flow'),
    ('A7-widen-table', 'k', M, [('if (tag !in HEADINGS) return skip(', 'if (false) return skip('),
      ('UAElementFontRule.sizeMultiplier(tag) ?: return null', 'UAElementFontRule.sizeMultiplier(tag) ?: 1.0')], 's-11-1-1b-005'),
    ('AS1-head-renderer', 'k', CR, '@HEAD', 'runs once'),
    ('AS2-enabled-true', 'k', CR, [('            rawProperties,\n            enabled = LocalWptCaptureMode.current,',
      '            rawProperties,\n            enabled = true,')], 'runs once'),
    ('AS3-fed-preUa', 'k', CR, [('            listUaProperties,\n            rawProperties,\n            enabled',
      '            listUaProperties,\n            listUaProperties,\n            enabled')], 'runs once'),
    ('G1-gate-always-false', 'k', G, [(GATE_ANCHOR, '        if (true) return false\n' + GATE_ANCHOR)], 'inset-011'),
    ('G2-gate-always-true', 'k', G, [(GATE_ANCHOR, '        if (true) return true\n' + GATE_ANCHOR)], 'block-in-inline'),
    ('M3-old-has-children-gate', 'k', G, [(GATE_ANCHOR, '        if (true) return !component.children.isNullOrEmpty()\n' + GATE_ANCHOR)], 'inset-005'),
    ('GS0-seam3-absent', 'k', RT, [('standsDown = UAHeadingFoldGate.standsDown(site.component, site.mergedPreUa),\n    )',
      'standsDown = UAElementFontRule.headingStandsDown(site.component),\n    )')], 'fold gate'),
    ('GS3-head-renderer-seam3', 'k', CR, [('            standsDown = com.styleconverter.runtime.typography.UAHeadingFoldGate\n                .standsDown(component, listUaProperties),',
      '            standsDown = com.styleconverter.runtime.typography.UAElementFontRule.headingStandsDown(component),')], 'wraps the list UA rule'),
    ('M5-ua-step-writes-color', 'k', K + 'typography/UAElementFontRule.kt',
     [('        // Either `merged` untouched (both halves guarded) or a new list.\n        return out',
       '        // Either `merged` untouched (both halves guarded) or a new list.\n        return out.map { if (it.type == "Color") IRProperty("Color", JsonObject(mapOf("srgb" to JsonObject(mapOf("r" to JsonPrimitive(0), "g" to JsonPrimitive(0), "b" to JsonPrimitive(1)))))) else it }')], 'soundness'),
    # ── skeptic extras (Compose) ──
    ('XK1-bottom-guard', 'k', M, [('val bottom = !declaresUaChildBottom(types)', 'val bottom = true')], 'inset-001'),
    ('XK2-parent-relative-resolved', 'k', M, [('return a.takeIf { a == b }', 'return a')], 'author sizes'),
    ('XK3-no-vertical-check', 'k', M, [('if (isVertical(preUa)) return skip(', 'if (false) return skip(')], 'vertical'),
    ('XK4-relative-counts', 'k', M, [('if (!ComponentRenderer.isOutOfFlowChild(own)) return merged',
      'if (own.none { it.type == "Position" }) return merged')], 'RELATIVE'),
    ('XK5-no-breadcrumb', 'k', M, [('if (logged.add(key)) {', 'if (false) {')], 's-11-1-1b-005'),
    ('XK6-margin-px-rounded', 'k', M, [('JsonPrimitive(em * face)', 'JsonPrimitive(kotlin.math.round(em * face))')], '32px UA face'),
    # ── U1-ios / seam 2 ──
    ('I1-painted-basis', 's', MS, [('guard let face = uaCascadeFacePx(tag: tag, own: own, preUA: preUA) else {',
      'guard let face = Optional(merged.first(where: { $0.type == "FontSize" }).flatMap { ValueExtractors.extractPx($0.data) } ?? 16) else {')], 'testInset005_006_011'),
    ('I2-em-base-16', 's', MS, [('UAElementFontRule.emBasePx(merged: preUA))', '16)')], 'testInset014'),
    ('I3-root-table', 's', MS, [('let leaf = IRValue.object(["px": .double(Double(em * face))])',
      'let leaf = IRValue.object(["px": .double(Double(UABlockMargin.vertical(forTag: sourceTag).top))])')], 'testInset014'),
    ('I4-no-edge-guard', 's', MS, [('let top = !UABlockMargin.declaresBlockMarginTop(own)', 'let top = true')], 'testInset001'),
    ('I5-ignore-enabled', 's', MS, [('guard enabled else { return merged }', 'guard true else { return merged }')], 'testCaptureOff'),
    ('I6-no-oof-test', 's', MS, [('guard position == .absolute || position == .fixed else { return merged }',
      'guard true else { return merged }')], 'testInFlowHeadings'),
    ('I7-widen-table', 's', MS, [('guard headings.contains(tag) else {', 'guard true else {'),
      ('guard let multiplier = UAElementFontRule.sizeMultiplier(forTag: tag) else { return nil }',
       'let multiplier = UAElementFontRule.sizeMultiplier(forTag: tag) ?? 1.0')], 'testS1111b005'),
    ('IS1-head-renderer', 's', CRS, '@HEAD', 'testTheMarginStepRunsOnce'),
    ('IS2-enabled-true', 's', CRS, [('enabled: wptCaptureMode)\n    }', 'enabled: true)\n    }')], 'testTheMarginStepRunsOnce'),
    ('IS3-fed-preUA', 's', CRS, [('merged: fontProperties,', 'merged: mergedPreUA,')], 'testTheMarginStepRunsOnce'),
    ('M3s-old-has-children-gate', 's', GS, [('return flow == nil', 'return true')], 'testInset005FoldedHostTakesTheFace'),
    ('M5s-ua-step-writes-color', 's', S + 'StyleEngine/typography/UAElementFontRule.swift',
     [('        return out\n    }\n\n    /// Replace the FIRST',
       '        return out.map { $0.type == "Color" ? IRProperty(type: "Color", data: .object(["srgb": .object(["r": .double(0), "g": .double(0), "b": .double(1)])])) : $0 }\n    }\n\n    /// Replace the FIRST')],
     'testFoldVerdictIsInvariantUnderTheUAFace'),
    # ── skeptic extras (SwiftUI) ──
    ('XS1-bottom-guard', 's', MS, [('let bottom = !UABlockMargin.declaresBlockMarginBottom(own)', 'let bottom = true')], 'testInset001'),
    ('XS2-parent-relative-resolved', 's', MS, [('return a == b ? a : nil', 'return a')], 'testAuthorSizes'),
    ('XS3-no-vertical-check', 's', MS, [('guard !isVertical(preUA) else {', 'guard true else {')], 'testVertical'),
    ('XS4-seam4-absent', 's', CRS, [(SEAM4, 'hasElementChildren: component.children?.isEmpty == false)')], None),
    ('XS5-relative-counts', 's', MS, [('guard position == .absolute || position == .fixed else { return merged }',
      'guard position != nil else { return merged }')], 'testRelativeSticky'),
    ('XS6-no-breadcrumb', 's', MS, [('let first = logged.insert(key).inserted', 'let first = logged.contains(key)')], 'testS1111b005'),
]
KT = ['*UaPositionedHeadingMargin*', '*UAElementFontRule*', '*UAHeadingFoldGate*']
ST = ['UAPositionedHeadingMarginTests', 'UAPositionedHeadingMarginSeamWiringTests', 'UAElementFontRuleTests', 'UAHeadingFoldGateTests']


def sha(p):
    return hashlib.sha256(open(p, 'rb').read()).hexdigest()


def log(line):
    with open(LOG, 'a') as f:
        f.write(line + '\n')


def run(plat):
    # Returns (rc, failing test names, summary) of the focused suite.
    if plat == 'k':
        res = os.path.join(WT, 'runtimes/compose/build/test-results/testDebugUnitTest')
        shutil.rmtree(res, ignore_errors=True)
        env = dict(os.environ, JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21'], text=True).strip())
        p = subprocess.run(['nice', './gradlew', ':runtime:testDebugUnitTest'] + sum((['--tests', t] for t in KT), []),
                           cwd=os.path.join(WT, 'apps/android-harness'), env=env, capture_output=True, text=True)
        fails, counts = [], []
        for fn in sorted(os.listdir(res)) if os.path.isdir(res) else []:
            if not fn.endswith('.xml'):
                continue
            x = open(os.path.join(res, fn)).read()
            m = re.search(r'tests="(\d+)" skipped="\d+" failures="(\d+)" errors="(\d+)"', x)
            counts.append(f'{fn[5:-4].split(".")[-1]} {m.group(1)}/{int(m.group(2)) + int(m.group(3))}')
            for tc in re.finditer(r'<testcase name="([^"]+)" classname="([^"]+)"[^>]*?(/>|>(.*?)</testcase>)', x, re.S):
                if tc.group(4) and ('<failure' in tc.group(4) or '<error' in tc.group(4)):
                    fails.append(tc.group(2).split('.')[-1] + ' > ' + tc.group(1).replace('&apos;', "'"))
        compile_err = 'Compilation error' in p.stdout + p.stderr or 'compileDebugKotlin FAILED' in p.stdout + p.stderr
        return p.returncode, fails, ('COMPILE-ERROR ' if compile_err else '') + '; '.join(counts)
    args = ['nice', 'xcodebuild', 'test', '-scheme', 'StyleConverterRuntime', '-destination',
            'platform=macOS,variant=Mac Catalyst,arch=arm64', '-derivedDataPath', os.path.join(WT, '.dd')] + \
           [f'-only-testing:StyleConverterRuntimeTests/{t}' for t in ST]
    p = subprocess.run(args, cwd=WT, capture_output=True, text=True)
    t = p.stdout + p.stderr
    fails = sorted(set(re.findall(r"Test Case '-\[StyleConverterRuntimeTests\.(\w+ \w+)\]' failed", t)))
    ex = re.findall(r'Executed (\d+) tests?, with (\d+) failures?', t)
    ce = 'COMPILE-ERROR ' if ('error:' in t and not ex) else ''
    return p.returncode, fails, ce + (f'Executed {ex[-1][0]}, {ex[-1][1]} failures' if ex else 'no Executed line')


for name, plat, rel, edits, pin in TABLE:
    if plat != PLAT or (WHICH != ['all'] and name not in WHICH):
        continue
    path = os.path.join(WT, rel)
    orig = open(path, 'rb').read()
    pre = sha(path)
    if edits == '@HEAD':
        mutated = subprocess.check_output(['git', '-C', WT, 'show', f'611c9f93:{rel}'])
    else:
        text = orig.decode()
        for old, new in edits:
            assert text.count(old) == 1, f'{name}: literal not unique/absent: {old[:70]!r}'
            text = text.replace(old, new)
        mutated = text.encode()
    log(f'MUTATING {rel} sha256 {pre} ({name})')
    try:
        open(path, 'wb').write(mutated)
        rc_r, f_r, c_r = run(plat)
    finally:
        open(path, 'wb').write(orig)
    post = sha(path)
    assert post == pre, f'{name}: restore not byte-exact'
    log(f'RESTORED {rel} sha256 {post} ({name})')
    rc_g, f_g, c_g = run(plat)
    if pin is None:
        verdict = 'SURVIVED (green under mutation: an unpinned branch)' if rc_r == 0 and not f_r else 'RED (unexpected)'
    else:
        hit = any(pin in f for f in f_r)
        verdict = 'RED-THEN-GREEN' if rc_r != 0 and hit and rc_g == 0 and not f_g else \
            ('SURVIVED' if rc_r == 0 else f'NOT-PROVEN (pin {pin!r} not among failures)' if rc_g == 0 else 'GREEN-RUN-FAILED')
    line = f'{name}: {verdict} | red rc={rc_r} [{c_r}] failing={f_r} | green rc={rc_g} [{c_g}] failing={f_g}'
    log('  ' + line)
    print(line, flush=True)
