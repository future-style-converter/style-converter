#!/usr/bin/env python3
# Skeptic (wave 54 L5) — PNG evidence that neither native gives an abspos <h1> its UA .67em margin:
# first heading ink band (y>=100) top, ref vs web/ios/android, for inset-001 (wire carries the
# extractor-baked MarginTop 21.44) against inset-005/-006/-014 (wire: Position ABSOLUTE only).
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.abspath(os.path.join(HERE, *(['..'] * 5)))
sys.path.insert(0, os.path.join(ROOT, 'tools/titan/results/wave53-plan'))
from geometry_common import paths, load, dark, bands
for t, y0 in (('text-decoration-inset-001', 100), ('text-decoration-inset-005', 100), ('text-decoration-inset-006', 100), ('text-decoration-inset-014', 100)):
    row = []
    for label, p in paths('wave53-final', 'css-text-decor', t):
        im, px = load(p); w, h = im.size
        b = bands(px, 0, w, y0, min(h, 330), dark, gap=2)
        row.append(f'{label}: first band {b[0] if b else None}')
    print(t, ' | '.join(row))
