#!/usr/bin/env python3
# wave-54 plan, family compose-table-body-cell — the two corpus pictures that corroborate the traced mechanism on
# shipped wave53-final captures (read-only, PIL):
#   D1  the TableApplier demo stroke on a DECLARED `display: table`: percentage-sizing-of-table-cell-children-003
#       android draws a 1-px black rim around its 100x100 green cell (the ref has none);
#   D2  the first auto cell takes the composed-WPT block fill: baseline-empty-cell-001 android shows one wide box and
#       no second (empty) cell, where the ref has two (vertical stroke columns 16 / 73 / 76).
#   006 the wave53-probe android picture, row by row: a 1-px outlined 20x20 box at x24-43 and the square at x44-63.
# Usage: python3 compose-table-body-cell.corroborate.py [run]   (default wave53-final; 006 always reads wave53-probe)
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import paths, load, bbox  # noqa: E402

run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
print(f'D1 css-tables/height-distribution/percentage-sizing-of-table-cell-children-003 ({run})')
for label, p in paths(run, 'css-tables', 'height-distribution/percentage-sizing-of-table-cell-children-003'):
    im, px = load(p); w, h = im.size
    blk = bbox(px, w, h, lambda q: q[0] + q[1] + q[2] < 60, (0, 80, w, h))      # below the two text lines
    grn = bbox(px, w, h, lambda q: q[1] > 100 and q[0] < 60 and q[2] < 60)
    print(f'  {label:8} black below y80 {blk}   green {grn}')
print(f'D2 css-tables/baseline-empty-cell-001 ({run}) — columns holding >= 20 dark px (cell border strokes)')
for label, p in paths(run, 'css-tables', 'baseline-empty-cell-001'):
    im, px = load(p); w, h = im.size
    cols = [x for x in range(w) if sum(1 for y in range(h) if sum(px[x, y]) < 300) >= 20]
    print(f'  {label:8} {cols}')
print('006 CSS2/css21-errata/s-11-1-1b-006 wave53-probe android, rows 55-76, x 20-69 (# = sum<30)')
_, px = load(paths('wave53-probe', 'CSS2', 'css21-errata/s-11-1-1b-006')[3][1])
for y in range(55, 77):
    print(f'  {y:3d} ' + ''.join('#' if sum(px[x, y]) < 30 else '.' for x in range(20, 70)))
