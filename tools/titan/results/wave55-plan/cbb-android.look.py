#!/usr/bin/env python3
# tools/titan/results/wave55-plan/cbb-android.look.py — read-only pixel read-out behind cbb-android.md §1/§2.
#
# Why: the wave-54 record names the residue on backdrop-filter-nested-border-radius-clip-3 android as "a 20-px source
# (the nested clip's second border band is the suspect)". The green bbox alone cannot say WHICH box is short. This
# reads the three bands the test paints — #outer blue (102,204,255), #middle grey (204,204,204), the inverted
# backdrop green (0,128,0) — along one row (y = 80, through the box's vertical middle) and one column (x = 120), on the
# frozen ref, the wave54-final android capture (base: neither unit), the wave54-probe android capture (U1 = 5b333c8a
# only) and the wave54-final ios/web captures. Each run of a colour prints as [start..end] (inclusive). The picture
# question it answers: does #middle overflow #outer (ref: no blue and no grey right of the green) or sit inside it?
#
# Usage: /opt/homebrew/bin/python3.12 tools/titan/results/wave55-plan/cbb-android.look.py
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import paths, load  # noqa: E402

TEST = 'backdrop-filter-nested-border-radius-clip-3'
# Colour classes (±10 per channel; the test paints flat colours, antialiasing only at the rounded corners).
CLASSES = [('blue', (102, 204, 255)), ('grey', (204, 204, 204)), ('green', (0, 128, 0)), ('pink', (255, 127, 255)),
           ('dark', (51, 51, 51)), ('white', (255, 255, 255))]


def cls(p):
    for name, c in CLASSES:
        if all(abs(p[i] - c[i]) <= 10 for i in range(3)):
            return name
    return '?'


def runs(seq):
    """[(name, start, end)] of consecutive equal classes."""
    out = []
    for i, n in seq:
        if out and out[-1][0] == n and out[-1][2] == i - 1:
            out[-1][2] = i
        else:
            out.append([n, i, i])
    return ' '.join(f'{n}[{a}..{b}]' for n, a, b in out if n != 'white')


for run, labels in (('wave54-final', ('ref', 'web', 'ios', 'android')), ('wave54-probe', ('android',))):
    for label, p in paths(run, 'filter-effects', TEST):
        if label not in labels:
            continue
        im, px = load(p)
        if im is None:
            print(f'{run:13} {label:8} MISSING')
            continue
        row = runs([(x, cls(px[x, 80])) for x in range(0, 260)])
        col = runs([(y, cls(px[120, y])) for y in range(0, 160)])
        print(f'{run:13} {label:8} row y=80: {row}')
        print(f'{"":13} {"":8} col x=120: {col}')
