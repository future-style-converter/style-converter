#!/usr/bin/env python3
# tools/titan/results/wave53-plan/float-avoid.geometry.py — the L4 (float-avoid) post-gate GEOMETRY probe.
#
# Why: contain-inline-size-bfc-floats-001 and display-flow-root-002 PASS today on both natives with the BFC stacked
# below every float (DEGENERATE, 0.95-0.97), so "P ≈ 0.998" can only be told from "P unchanged" by where the orange bar
# and the outline land. PLAN.md §6 R6 reads this probe (plan-skeptic must-fix 2). Each line ends in "→ GEOMETRY OK"
# or "→ GEOMETRY WRONG (<why>)".
#
# Usage: python3 tools/titan/results/wave53-plan/float-avoid.geometry.py [run-id]     (default wave52-ship)
# Expected after L4 on wave53-probe / wave53-final, ios and android:
#   contain-inline-size-bfc-floats-001 <p> … → GEOMETRY OK   (orange x16-215 y288-307, over float 3)
#   contain-inline-size-bfc-floats-002 <p> … → GEOMETRY OK   (orange x16-315 y88-107)
#   display-flow-root-002 <p>              … → GEOMETRY OK   (outline x265-266 y115-316; float 1 from x166)
# web rows are controls (L4 changes no web capture). The ref rows must print GEOMETRY OK (exit 1 otherwise).
# Decodes 12 PNGs.
import sys
from geometry_common import paths, load, orange, bbox, near

run = sys.argv[1] if len(sys.argv) > 1 else 'wave52-ship'
ref_failed = False
blue = lambda p: p[0] < 40 and p[1] < 40 and p[2] > 200        # the floats (CSS `blue`)
black = lambda p: max(p) < 64                                    # the flow-root's 1-px black outline (below the text)


def blue_profile(px, w, h):
    # The floats' silhouette as runs of rows sharing one (xmin, xmax) of blue: [(top, bottom, xmin, xmax)]. A right
    # float placed from the 358-px canvas instead of the 400-px container (PLAN P7 / brief R3) moves a run's xmin by
    # 42 px; the orange painted over float 3 (Appendix E step 7 above step 5) cuts its rows — both show here.
    out = []
    for y in range(h):
        xs = [x for x in range(w) if blue(px[x, y])]
        key = (min(xs), max(xs)) if xs else None
        if key and out and out[-1][1] == y - 1 and out[-1][2:] == key:
            out[-1] = (out[-1][0], y) + key
        elif key:
            out.append((y, y) + key)
    return out


def check(px, w, h, ref):
    # CSS 2.1 §9.5: the BFC root must not overlap any float's margin box; it goes to the first band whose gap fits its
    # used inline size (0 under contain: inline-size). Rules: orange bbox ±1 px, outline bbox (black below y90, i.e.
    # under the instruction text) ±1 px, the blue silhouette's runs equal in number and ±1 px in every coordinate.
    ob, _ = bbox(px, w, h, orange)
    kb, _ = bbox(px, w, h, black, (0, 90, w, h))
    prof = blue_profile(px, w, h)
    m = {'orange': ob, 'outline': kb, 'blueRuns': len(prof)}
    r = ref or dict(m, profile=prof)
    if (ob is None) != (r['orange'] is None) or (ob and not near(ob, r['orange'], 1)):
        return m, prof, f'orange {ob} vs ref {r["orange"]}'
    if (kb is None) != (r['outline'] is None) or (kb and not near(kb, r['outline'], 1)):
        return m, prof, f'outline {kb} vs ref {r["outline"]}'
    rp = r['profile']
    if len(prof) != len(rp) or any(not near(a, b, 1) for a, b in zip(prof, rp)):
        diff = next(((a, b) for a, b in zip(prof, rp) if not near(a, b, 1)), (len(prof), len(rp)))
        return m, prof, f'blue silhouette {diff[0]} vs ref {diff[1]}'
    return m, prof, None


for section, test in (('css-contain', 'contain-inline-size-bfc-floats-001'),
                      ('css-contain', 'contain-inline-size-bfc-floats-002'),
                      ('css-display', 'display-flow-root-002')):
    ref = None
    for label, p in paths(run, section, test):
        im, px = load(p)
        if im is None:
            print(f'{test} {label:8} MISSING {p}'); continue
        m, prof, why = check(px, *im.size, ref)
        if label == 'ref':
            ref = dict(m, profile=prof)
        verdict = 'GEOMETRY OK' if why is None else f'GEOMETRY WRONG ({why})'
        print(f'{test} {label:8} {m} → {verdict}')
        if label == 'ref' and why is not None:
            ref_failed = True
sys.exit(1 if ref_failed else 0)
