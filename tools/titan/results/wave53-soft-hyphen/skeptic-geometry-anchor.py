#!/usr/bin/env python3
# tools/titan/results/wave53-soft-hyphen/skeptic-geometry-anchor.py — wave 53 L2 SKEPTIC repro: can the pre-registered
# soft-hyphen.geometry.py probe print GEOMETRY OK for a CORRECT Android picture at all?
# It re-runs the probe's own boxes()/check() logic (geometry_common.dark = channel sum < 300) on Android captures that are
# ALREADY correct today — css-text/hyphens/hyphens-span-002 android (the plan's "anchor for correct", P 0.9943, the picture
# PLAN §2 L2 predicts span-001 android will become) and hyphens-out-of-flow-002 android boxes 1/2/7 (brief §2: "correct:
# high‐ / way") — and then at alternative ink predicates (channel sum < 450 / < 600, and max channel < 200), printing the per-box line-1 ink extent, so the darkness of
# Android's anti-aliased hyphen glyph is measured rather than argued. Read-only; decodes 8 PNGs.
import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import paths, load, orange, bands, near
run = sys.argv[1] if len(sys.argv) > 1 else 'wave52-ship'
def boxes(px, w, h, pred):
    # Same box/ink banding as soft-hyphen.geometry.py, with the ink predicate as a parameter.
    return [((y0, y1, x0, x1), bands(px, x0 + 3, x1 - 2, y0 + 3, y1 - 2, pred, gap=0))
            for (y0, y1, x0, x1) in bands(px, 0, w, 95, h, orange, gap=0)]
def check(found, ref):
    # soft-hyphen.geometry.py's check(), verbatim semantics (height ±1, ink edges ±2).
    if len(found) != len(ref): return f'boxes {len(found)}/{len(ref)}'
    for i, ((b, ls), (rb, rls)) in enumerate(zip(found, ref)):
        if not near(b[1] - b[0], rb[1] - rb[0], 1): return f'box {i+1} height {b[1]-b[0]+1} vs ref {rb[1]-rb[0]+1}'
        if len(ls) != len(rls): return f'box {i+1} {len(ls)} ink lines vs ref {len(rls)}'
        for j, (l, rl) in enumerate(zip(ls, rls)):
            if not near(l[2], rl[2], 2) or not near(l[3], rl[3], 2): return f'box {i+1} line {j+1} ink x{l[2]}-{l[3]} vs ref x{rl[2]}-{rl[3]}'
    return None
for limit in (300, 450, 600, "max<200"):
    pred = (lambda p: max(p[:3]) < 200) if limit == "max<200" else (lambda p, L=limit: p[0] + p[1] + p[2] < L)
    for test in ('hyphens/hyphens-span-002', 'hyphens/hyphens-span-001', 'hyphens/hyphens-out-of-flow-001', 'hyphens/hyphens-out-of-flow-002'):
        ref = None
        for label, p in paths(run, 'css-text', test):
            im, px = load(p)
            if im is None: continue
            found = boxes(px, *im.size, pred)
            if label == 'ref': ref = found
            why = check(found, ref)
            l1 = [ls[0][2:] if ls else None for _, ls in found]
            print(f'{limit if isinstance(limit, str) else "sum<" + str(limit)} {test} {label:8} line1-ink {l1} → {"GEOMETRY OK" if why is None else "GEOMETRY WRONG (" + why + ")"}')
