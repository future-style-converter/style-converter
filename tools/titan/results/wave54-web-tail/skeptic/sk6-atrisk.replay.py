#!/usr/bin/env python3
# Skeptic (wave 54 L6) replay of RS on the AT-RISK web passes the brief never replayed: shift the ink that
# follows the separator's insertion point right by s px (s = 4, 5: one Inter 16-px space, measured x146->x151
# on box-sizing-007) in the wave53-final web capture. Scored by sk6-atrisk.replay-score.mjs with the gate's
# own diffWebVsRef. Region = (x0, y0, y1): everything at x >= x0 on rows [y0, y1) moves.
import sys
from PIL import Image
C = 'tools/titan/runs/wave53-final/sections'
OUT = sys.argv[1]
CASES = {
    # 1 separator between the two .ib roots: `ccc` (web x46) follows `bbb` (web x17-44), line 2 (rows ~98-116).
    'baseline-with-orthogonal-flow-001': ('css-writing-modes', 'baseline-with-orthogonal-flow-001', 45, 96, 122),
    # 1 separator between the two spans: `not green` (web x61) follows `green` (x16-59).
    'attr-style-sharing-1': ('css-values', 'attr-style-sharing-1', 60, 0, 60),
    # 2 separators; the hidden input's leading one collapses at line start: the file input (web x53) moves.
    'appearance-auto-input-non-widget-001': ('css-ui', 'appearance-auto-input-non-widget-001', 52, 0, 60),
    # 2 separators: the green abspos square follows the first (blank) inline-block (web x116, ref x121).
    'static-inside-inline-block': ('CSS2', 'abspos/static-inside-inline-block', 110, 80, 200),
}
for name, (sec, stem, x0, y0, y1) in CASES.items():
    src = f'{C}/{sec}/screenshots/wpt__{sec}__{stem.replace("/", "__")}.png'
    im = Image.open(src).convert('RGBA')
    for s in (4, 5):
        out = im.copy()
        band = im.crop((x0, y0, im.width - s, y1))
        # The vacated strip takes the canvas white (every capture here has a white canvas).
        out.paste((255, 255, 255, 255), (x0, y0, x0 + s, y1))
        out.paste(band, (x0 + s, y0))
        out.save(f'{OUT}/{name}.shift{s}.png')
    print(name, src)
