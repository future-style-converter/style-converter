#!/usr/bin/env python3
# Plan fix r1 (S2/S3): scan the frozen refs and the wave53-final captures of every RS box-sizing / semi-replaced test for
# their ATOM ROW BANDS (rows below the instruction text holding ink) and, per band, the run starts on the band's middle
# row. Read-only; decides the per-band rule of web-root-separator.geometry.py and the new keys' (y0, split).
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '..', 'wave53-plan'))
from geometry_common import paths, load, bands  # noqa: E402
ink = lambda p: not (p[0] > 240 and p[1] > 240 and p[2] > 240)
def starts(px, w, y):
    return [x for x in range(1, w) if ink(px[x, y]) and not ink(px[x - 1, y])] + ([0] if ink(px[0, y]) else [])
run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
T = [('css-ui', f'box-sizing-0{n}', 80) for n in ('07','08','09','10','11','13','14','15','16','17','18','19','20','21','22','24','25')] + \
    [('css-position', 'position-absolute-semi-replaced-stretch-other', 30), ('css-position', 'position-absolute-semi-replaced-stretch-input', 30)]
for sec, t, y0 in T:
    for label, path in paths(run, sec, t):
        im, px = load(path)
        if im is None: print(t, label, 'MISSING'); continue
        w, h = im.size
        b = bands(px, 0, w, y0, min(h, 1344), ink, gap=2)
        row = [(bt, bb, sorted(starts(px, w, (bt + bb) // 2))) for bt, bb, _, _ in b]
        print(f'{t:<48} {label:<8} {len(b)} bands ' + ' '.join(f'[{bt}-{bb} {s}]' for bt, bb, s in row[:12]))
