#!/usr/bin/env python3
# tools/titan/results/wave53-float-avoid/mutate-compose.py — EXECUTES the L4 mutations on the Compose planner.
# Why: PLAN.md §0 "Pins can fail" — every pin must be proven able to fail by a mutation of the mechanism, run red →
# restore byte-exact (sha256) → green. Each mutation is one exact-substring edit of FloatAvoidPlan.kt (asserted to
# match once), then the focused JVM suite, then the original bytes are written back and re-hashed.
# Usage (from the repo root, JDK 21): python3 tools/titan/results/wave53-float-avoid/mutate-compose.py [M…]
import glob, hashlib, json, os, subprocess, sys
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
SRC = os.path.join(ROOT, 'runtimes/compose/src/main/java/com/styleconverter/runtime/layout/FloatAvoidPlan.kt')
RESULTS = os.path.join(ROOT, 'runtimes/compose/build/test-results/testDebugUnitTest')
OUT = os.path.join(os.path.dirname(__file__), 'mutations-compose.json')
MUTATIONS = {
    # M1 — drop the inline-size containment requirement of the G7 contained arm.
    'M1': ('if (!c.inlineSize || c.size || c.blockSize || c.paint || clips(bp)) return false',
           'if (c.size || c.blockSize || c.paint || clips(bp)) return false'),
    # M2 — closed band intervals (a float ending at y touches a band starting at y).
    'M2': ('val hit = rects.filter { it[3] > y && (it[1] < y + h || it[1] <= y) }',
           'val hit = rects.filter { it[3] >= y && it[1] <= y + h }'),
    # M3 — right floats placed at x = 0 (no right-edge hugging).
    'M3': ('val x = if (isLeft) l else r - fw', 'val x = if (isLeft) l else 0.0'),
    # M4 — delete the G7 bail: any non-px width is taken as 0.
    'M4': ('val used = pxWidth ?: if (containedIntrinsic(bfc, bp)) 0.0 else return null', 'val used = pxWidth ?: 0.0'),
    # M5 — delete the G5 Clear bail.
    'M5': ('        if (hasWire(container, "Clear")) return null                 // G5 — any Clear below is W5\'s\n', ''),
    # M7 — report max(float bottom, BFC bottom) whatever the container (the §10.6.7 rule applied to every box).
    'M7': ('val height = if (shape.containerIsBfc) maxOf(bfcBottom, floatBottom) else bfcBottom',
           'val height = maxOf(bfcBottom, floatBottom)'),
    # M8 — take the container width from the incoming proposal (358) instead of the root's own 400.
    'M8': ('val w = shape.containerWidthPx?.let { it * scale } ?: incomingWidth', 'val w = incomingWidth'),
    # M9 — delete the G8 own-paint bail.
    'M9': ('        if (pxWidth == null && bp.any { paints(it.first) }) return null\n', ''),
    # M10 — delete the G2 attach rule (any nested container may plan).
    'M10': ('        if (container.slot?.parent != null && !containerBfc) return null\n', ''),
}


def sha(b): return hashlib.sha256(b).hexdigest()


def run_suite():
    # The focused suite the plan grants this lane (FloatAvoid* is the pin class; the others are controls).
    env = dict(os.environ, JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21']).decode().strip())
    for f in glob.glob(os.path.join(RESULTS, '*Float*.xml')): os.remove(f)
    rc = subprocess.call(['./gradlew', ':runtime:testDebugUnitTest', '--tests', '*FloatAvoid*', '--tests', '*FloatClearance*',
                          '--tests', '*FloatRowPacking*', '-q'], cwd=os.path.join(ROOT, 'apps/android-harness'), env=env,
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    red, total = [], 0
    for f in glob.glob(os.path.join(RESULTS, '*Float*.xml')):
        r = ET.parse(f).getroot()
        for tc in r.findall('testcase'):
            total += 1
            fl = tc.find('failure')
            if fl is not None: red.append(f"{r.get('name').split('.')[-1]}::{tc.get('name')} :: {(fl.get('message') or '')[:160]}")
    return rc, total, red


orig = open(SRC, 'rb').read()
h0 = sha(orig)
log = {'file': os.path.relpath(SRC, ROOT), 'sha256': h0, 'runs': []}
try:
    for m in (sys.argv[1:] or list(MUTATIONS)):
        old, new = MUTATIONS[m]
        text = orig.decode()
        assert text.count(old) == 1, f'{m}: anchor matched {text.count(old)}x'
        open(SRC, 'w').write(text.replace(old, new))
        rc, total, red = run_suite()
        open(SRC, 'wb').write(orig)
        assert sha(open(SRC, 'rb').read()) == h0
        print(f'{m}: rc={rc} tests={total} red={len(red)}'); [print('   ', r) for r in red]
        log['runs'].append({'mutation': m, 'rc': rc, 'tests': total, 'red': red, 'restoredSha256': h0})
finally:
    # Always restore the original bytes, even on an interrupt.
    open(SRC, 'wb').write(orig)
rc, total, red = run_suite()
print(f'GREEN after restore: rc={rc} tests={total} red={len(red)} sha256={sha(open(SRC, "rb").read())}')
log['green'] = {'rc': rc, 'tests': total, 'red': red, 'sha256': sha(open(SRC, 'rb').read())}
json.dump(log, open(OUT, 'w'), indent=1)
