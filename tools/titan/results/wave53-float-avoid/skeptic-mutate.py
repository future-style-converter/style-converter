#!/usr/bin/env python3
# skeptic-mutate.py — wave 53 L4 SKEPTIC's own mutation replay (never the lane's mutate-*.py).
# Each mutation is one exact-substring edit of the lane's planner (asserted to match once), then the focused suite,
# then the original bytes are written back and re-hashed (sha256 must equal the pre-run hash), then a final green run.
# The S* mutations are the skeptic's additions: they probe branches no lane pin names (an S mutation that stays GREEN
# is a SURVIVOR — an unpinned branch). Usage, from the repo root, JDK 21 on PATH for Gradle:
#   python3 tools/titan/results/wave53-float-avoid/skeptic-mutate.py kt  [M…|S…]
#   python3 tools/titan/results/wave53-float-avoid/skeptic-mutate.py swift [M…|S…]
import glob, hashlib, json, os, re, subprocess, sys
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
HERE = os.path.dirname(os.path.abspath(__file__))
KT = os.path.join(ROOT, 'runtimes/compose/src/main/java/com/styleconverter/runtime/layout/FloatAvoidPlan.kt')
SW = os.path.join(ROOT, 'runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/FloatAvoidPlan.swift')
KT_M = {
    'M1': ('if (!c.inlineSize || c.size', 'if (c.size'),                                  # contained arm w/o inline-size
    'M2': ('it[3] > y && (it[1] < y + h || it[1] <= y)', 'it[3] >= y && it[1] <= y + h'), # closed bands
    'M3': ('if (isLeft) l else r - fw', 'if (isLeft) l else 0.0'),                       # right floats at x=0
    'M4': ('pxWidth ?: if (containedIntrinsic(bfc, bp)) 0.0 else return null', 'pxWidth ?: 0.0'),  # G7 bail gone
    'M5': ('if (hasWire(container, "Clear")) return null', 'if (false) return null'),   # G5 Clear bail gone
    'M7': ('if (shape.containerIsBfc) maxOf(bfcBottom, floatBottom) else bfcBottom', 'maxOf(bfcBottom, floatBottom)'),
    'M8': ('shape.containerWidthPx?.let { it * scale } ?: incomingWidth', 'incomingWidth'),
    'M9': ('if (pxWidth == null && bp.any { paints(it.first) }) return null', 'if (false) return null'),
    'M10': ('if (container.slot?.parent != null && !containerBfc) return null', 'if (false) return null'),
    # --- skeptic additions -------------------------------------------------------------------------------------
    'S1': ('shape.containerWidthPx?.let { it * scale }', 'shape.containerWidthPx?.let { it }'),  # density scale gone
    'S2': ('if (shape.containerIsBfc) maxOf(bfcBottom, floatBottom) else bfcBottom', 'bfcBottom'),  # §10.6.7 arm gone
    'S3': ('(it[1] < y + h || it[1] <= y)', '(it[1] < y + h)'),                           # zero-height band clause
    'S4': ('val used = shape.bfcInlineSizePx * scale', 'val used = shape.bfcInlineSizePx'),  # BFC size scale gone
    'S5': ('if (floats.any { keyword(pairs(it), "Display") !in setOf(null, "BLOCK") }) return null', ''),  # float display
}
SW_M = {
    'M1': ('c.contains("INLINE_SIZE"), ', ''),
    'M2': ('$0.b > y && ($0.t < y + h || $0.t <= y)', '$0.b >= y && $0.t <= y + h'),
    'M3': ('isLeft ? l : r - fw', 'isLeft ? l : 0'),
    'M4': ('pxWidth ?? (containedIntrinsic(bfc) ? 0 : nil)', 'pxWidth ?? Optional(0.0)'),
    'M5': ('if hasWire(container, "Clear") { return nil }', 'if false { return nil }'),
    'M7': ('shape.containerIsBfc ? max(bfcBottom, floatBottom) : bfcBottom', 'max(bfcBottom, floatBottom)'),
    'M8': ('shape.containerWidthPx.map { $0 * scale } ?? incomingWidth', 'incomingWidth'),
    'M9': ('if pxWidth == nil && bp.contains(where: { paints($0.type) }) { return nil }', 'if false { return nil }'),
    'M10': ('if container.slot?.parent != nil && !containerBfc { return nil }', 'if false { return nil }'),
    'S2': ('shape.containerIsBfc ? max(bfcBottom, floatBottom) : bfcBottom', 'bfcBottom'),
    'S3': ('($0.t < y + h || $0.t <= y)', '($0.t < y + h)'),
}

def sha(b): return hashlib.sha256(b).hexdigest()

def run_kt():
    env = dict(os.environ, JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21']).decode().strip())
    res = os.path.join(ROOT, 'runtimes/compose/build/test-results/testDebugUnitTest')
    for f in glob.glob(os.path.join(res, '*.xml')):
        if 'Float' in f: os.remove(f)
    rc = subprocess.call(['./gradlew', ':runtime:testDebugUnitTest', '--tests', '*FloatAvoid*', '--tests',
                          '*FloatClearance*', '--tests', '*FloatRowPacking*', '-q'],
                         cwd=os.path.join(ROOT, 'apps/android-harness'), env=env,
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    red, total = [], 0
    for f in glob.glob(os.path.join(res, '*Float*.xml')):
        r = ET.parse(f).getroot()
        for tc in r.findall('testcase'):
            total += 1
            fl = tc.find('failure')
            if fl is not None: red.append(f"{tc.get('name')} :: {(fl.get('message') or '')[:140]}")
    return rc, total, red

def run_sw():
    log = os.path.join(HERE, '_skeptic-xcb.log')
    cmd = ['xcodebuild', 'test', '-scheme', 'StyleConverterRuntime', '-destination',
           'platform=macOS,variant=Mac Catalyst,arch=arm64', '-only-testing:StyleConverterRuntimeTests/FloatAvoidPlanTests']
    rc = subprocess.call(cmd, cwd=ROOT, stdout=open(log, 'w'), stderr=subprocess.STDOUT)
    text = open(log, errors='replace').read()
    red = sorted(set(re.findall(r"error: -\[StyleConverterRuntimeTests\.FloatAvoidPlanTests (\w+)\]", text)))
    m = re.findall(r'Executed (\d+) tests?, with (\d+) failures?', text)
    total = int(m[-1][0]) if m else 0
    return rc, total, red

def main():
    plat = sys.argv[1]
    src, muts, run = (KT, KT_M, run_kt) if plat == 'kt' else (SW, SW_M, run_sw)
    orig = open(src, 'rb').read(); h0 = sha(orig)
    out = {'file': os.path.relpath(src, ROOT), 'sha256Before': h0, 'runs': []}
    try:
        for m in (sys.argv[2:] or list(muts)):
            old, new = muts[m]
            t = orig.decode()
            assert t.count(old) == 1, f'{m}: anchor matched {t.count(old)}x'
            open(src, 'w').write(t.replace(old, new))
            rc, total, red = run()
            open(src, 'wb').write(orig)
            assert sha(open(src, 'rb').read()) == h0, 'restore failed'
            verdict = 'RED' if red else ('BUILD-FAIL' if rc else 'GREEN (SURVIVOR)')
            print(f'{plat} {m}: rc={rc} tests={total} red={len(red)} -> {verdict}', flush=True)
            for r in red: print('    ', r, flush=True)
            out['runs'].append({'mutation': m, 'edit': [old, new], 'rc': rc, 'tests': total, 'red': red,
                                'verdict': verdict, 'restoredSha256': sha(open(src, 'rb').read())})
    finally:
        open(src, 'wb').write(orig)  # always restore, even on interrupt
    rc, total, red = run()
    h1 = sha(open(src, 'rb').read())
    print(f'{plat} GREEN after restore: rc={rc} tests={total} red={len(red)} sha256 {h0} -> {h1}', flush=True)
    out['green'] = {'rc': rc, 'tests': total, 'red': red, 'sha256After': h1}
    json.dump(out, open(os.path.join(HERE, f'skeptic-mutations-{plat}.json'), 'w'), indent=1)

main()
