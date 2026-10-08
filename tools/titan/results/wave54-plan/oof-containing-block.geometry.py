#!/usr/bin/env python3
# tools/titan/results/wave54-plan/oof-containing-block.geometry.py — the post-gate GEOMETRY probe for the wave-54
# "oof-containing-block" lane (brief: oof-containing-block.md; scout: queue-scout-layout.md).
#
# Why: six of the lane's twelve native target cells PASS today (P 0.959-0.984) with the test's red failure ink on
# the canvas, so "P → faithful" can only be told from "P unchanged" by WHERE the green box lands. Each line ends in
# "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)". The ref row of every test must print OK (exit 1 otherwise — the
# rule describes the picture the test asks for); the wave53-final native rows print WRONG (the rule can fail).
#
# Rule (css-position-3 §3.1 static position / css-contain-2 §3.2 layout containment / filter-effects-2 §3): the
# strict-green (0,128,0)±8 bbox equals the ref's ±1 px, the strict-red (r>200,g<80,b<80) pixel count equals the
# ref's ±2 % and, when the ref has red, its bbox equals the ref's ±1 px (backdrop-filter-containing-block's red
# abspos square is REQUIRED ink, clipped at the ICB).
#
# Usage: python3 tools/titan/results/wave54-plan/oof-containing-block.geometry.py [run-id]   (default wave53-final)
# Decodes 4 PNGs per test × 7 tests.
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import paths, load, bbox, near  # noqa: E402  (shared wave-53 probe helpers)

run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
green = lambda p: p[0] < 8 and abs(p[1] - 128) < 8 and p[2] < 8
red = lambda p: p[0] > 200 and p[1] < 80 and p[2] < 80
TESTS = [
    ('css-position', 'position-relative-003'),                         # M1: auto-inset fixed under relpos spans
    ('CSS2', 'abspos/static-fixed-inside-abspos'),                       # M1: auto-inset fixed under an abspos box
    ('css-position', 'position-relative-004'),                         # M2: fixed under contain: paint
    ('css-position', 'change-insets-inside-strict-containment-nested'),  # M2: fixed under contain: strict (nested)
    ('css-contain', 'contain-content-003'),                             # M2: abspos under contain: content (Compose)
    ('css-contain', 'contain-content-011'),                             # M2: same shape + a counter (web too: "25")
    ('filter-effects', 'backdrop-filter-containing-block'),             # M2: fixed + abspos under backdrop-filter
]
ref_failed = False
for section, test in TESTS:
    ref = None
    for label, p in paths(run, section, test):
        im, px = load(p)
        if im is None:
            print(f'{test} {label:8} MISSING'); continue
        w, h = im.size
        gb, gn = bbox(px, w, h, green)
        rb, rn = bbox(px, w, h, red)
        m = {'green': gb, 'greenPx': gn, 'red': rb, 'redPx': rn}
        if label == 'ref':
            ref = m
        why = None
        if (gb is None) != (ref['green'] is None) or (gb and not near(gb, ref['green'], 1)):
            why = f'green {gb} vs ref {ref["green"]}'
        elif abs(rn - ref['redPx']) > max(2, 0.02 * ref['redPx']):
            why = f'red {rn} px vs ref {ref["redPx"]}'
        elif ref['red'] and rb and not near(rb, ref['red'], 1):
            why = f'red {rb} vs ref {ref["red"]}'
        print(f'{test} {label:8} {m} → ' + ('GEOMETRY OK' if why is None else f'GEOMETRY WRONG ({why})'))
        if label == 'ref' and why is not None:
            ref_failed = True
sys.exit(1 if ref_failed else 0)
