#!/usr/bin/env python3
# tools/titan/results/wave54-plan/compose-wpt-content-box-cb.geometry.py — the post-gate GEOMETRY probe for the wave-54
# "compose-wpt-content-box-cb" lane (brief: compose-wpt-content-box-cb.md; scout: queue-scout-layout.md).
#
# Why: 9 of the lane's 10 Android target cells PASS today (P 0.9649-0.9966) while the abspos box sized `100%` of a
# padded/bordered content-box parent is 20-30 px short, so the fix shows in geometry, not in the verdict. Each line ends
# in "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)"; the ref row must print OK (exit 1 otherwise).
#
# Rule (CSS 2.1 §10.1 / §10.2: a percentage width resolves against the containing block's width, which for a
# content-box parent is its DECLARED width; css-position-3 §3.1 abspos → padding box): the strict-green (0,128,0)±8
# bbox equals the ref's ±1 px; the strict-red (abspos-autopos failure ink) and the backdrop-source pink
# (255,127,255)±12 (the nested-clip failure ink) pixel counts equal the ref's (0) within 2 % of the green mass.
#
# Usage: python3 tools/titan/results/wave54-plan/compose-wpt-content-box-cb.geometry.py [run-id]  (default wave53-final)
# Decodes 4 PNGs per test × 10 tests.
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import paths, load, bbox, near  # noqa: E402

run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
green = lambda p: p[0] < 8 and abs(p[1] - 128) < 8 and p[2] < 8
bad = lambda p: (p[0] > 200 and p[1] < 80 and p[2] < 80) or (p[0] > 240 and abs(p[1] - 127) < 12 and p[2] > 240)
TESTS = [('css-flexbox', f'abspos/abspos-autopos-{m}') for m in ('htb-ltr', 'htb-rtl', 'vlr-ltr', 'vlr-rtl', 'vrl-ltr', 'vrl-rtl')]
TESTS += [('filter-effects', f'backdrop-filter-nested-border-radius-clip{s}') for s in ('', '-2', '-3', '-4')]
ref_failed = False
for section, test in TESTS:
    ref = None
    for label, p in paths(run, section, test):
        im, px = load(p)
        if im is None:
            print(f'{test} {label:8} MISSING'); continue
        w, h = im.size
        gb, gn = bbox(px, w, h, green)
        _, bn = bbox(px, w, h, bad)
        m = {'green': gb, 'greenPx': gn, 'failInkPx': bn}
        if label == 'ref':
            ref = m
        why = None
        if (gb is None) != (ref['green'] is None) or (gb and not near(gb, ref['green'], 1)):
            why = f'green {gb} vs ref {ref["green"]}'
        elif abs(bn - ref['failInkPx']) > max(2, 0.02 * ref['greenPx']):
            why = f'failure ink {bn} px vs ref {ref["failInkPx"]}'
        print(f'{test} {label:8} {m} → ' + ('GEOMETRY OK' if why is None else f'GEOMETRY WRONG ({why})'))
        if label == 'ref' and why is not None:
            ref_failed = True
sys.exit(1 if ref_failed else 0)
