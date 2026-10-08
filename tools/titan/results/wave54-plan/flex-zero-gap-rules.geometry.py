#!/usr/bin/env python3
# tools/titan/results/wave54-plan/flex-zero-gap-rules.geometry.py — the post-gate GEOMETRY probe for the wave-54
# "flex-zero-gap-rules" lane (brief: flex-zero-gap-rules.md; scout: queue-scout-layout.md).
#
# Why: css-gaps/flex/flex-gap-decorations-033 ("flex column and row gaps are painted with 0px gaps") fails on both
# natives at f 0.94 with EVERY other pixel identical to the ref — the 5 200 rule pixels are simply absent. The probe
# pins the rules themselves. Each line ends in "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)"; the ref row must print
# OK (exit 1 otherwise).
#
# Rule (css-gap-decorations-1: a gap decoration is centred in its gap and painted even when the gap is 0): the
# strict-red (column rules) and strict-blue (row rules) pixel counts equal the ref's ±2 % and their bboxes ±1 px,
# and the per-column red runs on the row through the first line equal the ref's ±1 px.
#
# Usage: python3 tools/titan/results/wave54-plan/flex-zero-gap-rules.geometry.py [run-id]   (default wave53-final)
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import paths, load, bbox, near  # noqa: E402

run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
red = lambda p: p[0] > 200 and p[1] < 80 and p[2] < 80
blue = lambda p: p[0] < 80 and p[1] < 80 and p[2] > 200
ref_failed = False
for section, test in (('css-gaps', 'flex/flex-gap-decorations-033'),):
    ref = None
    for label, p in paths(run, section, test):
        im, px = load(p)
        if im is None:
            print(f'{test} {label:8} MISSING'); continue
        w, h = im.size
        rb, rn = bbox(px, w, h, red)
        bb, bn = bbox(px, w, h, blue)
        row = 20  # a row through the first flex line (items start at y16)
        runs, cur = [], None
        for x in range(w):
            if red(px[x, row]):
                cur = [x, x] if cur is None else [cur[0], x]
            elif cur is not None:
                runs.append(tuple(cur)); cur = None
        m = {'red': rb, 'redPx': rn, 'blue': bb, 'bluePx': bn, 'redRunsRow20': runs}
        if label == 'ref':
            ref = m
        why = None
        for k, bk in (('redPx', 'red'), ('bluePx', 'blue')):
            if abs(m[k] - ref[k]) > max(2, 0.02 * ref[k]):
                why = f'{k} {m[k]} vs ref {ref[k]}'; break
            if ref[bk] and m[bk] and not near(m[bk], ref[bk], 1):
                why = f'{bk} {m[bk]} vs ref {ref[bk]}'; break
        if why is None and (len(runs) != len(ref['redRunsRow20']) or any(not near(a, b, 1) for a, b in zip(runs, ref['redRunsRow20']))):
            why = f'red runs {runs} vs ref {ref["redRunsRow20"]}'
        print(f'{test} {label:8} {m} → ' + ('GEOMETRY OK' if why is None else f'GEOMETRY WRONG ({why})'))
        if label == 'ref' and why is not None:
            ref_failed = True
sys.exit(1 if ref_failed else 0)
