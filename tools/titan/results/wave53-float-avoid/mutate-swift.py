#!/usr/bin/env python3
# tools/titan/results/wave53-float-avoid/mutate-swift.py — EXECUTES the L4 mutations on the SwiftUI planner.
# Twin of mutate-compose.py (same mutation ids, same intent): one exact-substring edit of FloatAvoidPlan.swift, the
# Catalyst FloatAvoidPlanTests class, then the original bytes written back and re-hashed (sha256). M6 (the seam
# branch) is executed separately — it needs the seam patch, see _seam2-verify.log and the lane note.
# Usage (repo root): python3 tools/titan/results/wave53-float-avoid/mutate-swift.py <derivedDataPath> [M…]
import hashlib, json, os, re, subprocess, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
SRC = os.path.join(ROOT, 'runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/FloatAvoidPlan.swift')
OUT = os.path.join(os.path.dirname(__file__), 'mutations-swift.json')
DD = sys.argv[1]
MUTATIONS = {
    'M1': ('guard c.contains("INLINE_SIZE"), c.isDisjoint(', 'guard c.isDisjoint('),
    'M2': ('let hit = rects.filter { $0.b > y && ($0.t < y + h || $0.t <= y) }', 'let hit = rects.filter { $0.b >= y && $0.t <= y + h }'),
    'M3': ('let x = isLeft ? l : r - fw', 'let x = isLeft ? l : 0'),
    'M4': ('guard let used = pxWidth ?? (containedIntrinsic(bfc) ? 0 : nil) else { return nil }', 'let used = pxWidth ?? 0'),
    'M5': ('        if hasWire(container, "Clear") { return nil }                  // G5 — any Clear below is W5\'s\n', ''),
    'M7': ('let height = shape.containerIsBfc ? max(bfcBottom, floatBottom) : bfcBottom', 'let height = max(bfcBottom, floatBottom)'),
    'M8': ('let w = shape.containerWidthPx.map { $0 * scale } ?? incomingWidth', 'let w = incomingWidth'),
    'M9': ('        if pxWidth == nil && bp.contains(where: { paints($0.type) }) { return nil }\n', ''),
    'M10': ('        if container.slot?.parent != nil && !containerBfc { return nil }\n', ''),
}


def sha(b): return hashlib.sha256(b).hexdigest()


def run_suite():
    # The pure pin class only: the mutations touch the planner, not the seam.
    p = subprocess.run(['xcodebuild', 'test', '-scheme', 'StyleConverterRuntime', '-destination',
                        'platform=macOS,variant=Mac Catalyst,arch=arm64', '-derivedDataPath', DD,
                        '-only-testing:StyleConverterRuntimeTests/FloatAvoidPlanTests'],
                       cwd=ROOT, capture_output=True, text=True)
    log = p.stdout + p.stderr
    failed = sorted(set(re.findall(r"Test Case '-\[StyleConverterRuntimeTests\.FloatAvoidPlanTests (\w+)\]' failed", log)))
    passed = set(re.findall(r"Test Case '-\[StyleConverterRuntimeTests\.FloatAvoidPlanTests (\w+)\]' passed", log))
    errs = [l.strip()[-200:] for l in log.splitlines() if ': error: ' in l][:12]
    return p.returncode, len(passed) + len(failed), failed, errs


orig = open(SRC, 'rb').read()
h0 = sha(orig)
log = {'file': os.path.relpath(SRC, ROOT), 'sha256': h0, 'runs': []}
try:
    for m in (sys.argv[2:] or list(MUTATIONS)):
        old, new = MUTATIONS[m]
        text = orig.decode()
        assert text.count(old) == 1, f'{m}: anchor matched {text.count(old)}x'
        open(SRC, 'w').write(text.replace(old, new))
        rc, total, red, errs = run_suite()
        open(SRC, 'wb').write(orig)
        assert sha(open(SRC, 'rb').read()) == h0
        print(f'{m}: rc={rc} tests={total} red={red}'); [print('   ', e) for e in errs]
        log['runs'].append({'mutation': m, 'rc': rc, 'tests': total, 'red': red, 'errors': errs, 'restoredSha256': h0})
finally:
    open(SRC, 'wb').write(orig)  # always restore, even on an interrupt
rc, total, red, errs = run_suite()
print(f'GREEN after restore: rc={rc} tests={total} red={red} sha256={sha(open(SRC, "rb").read())}')
log['green'] = {'rc': rc, 'tests': total, 'red': red, 'sha256': sha(open(SRC, 'rb').read())}
json.dump(log, open(OUT, 'w'), indent=1)
