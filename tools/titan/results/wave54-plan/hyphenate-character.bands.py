#!/usr/bin/env python3
# hyphenate-character.bands.py — wave-54 planning measurement (read-only).
# Prints the ink row-bands (y0..y1, x0..x1) of each hyphenate-character-00{1..4}
# PNG — the frozen ref and the wave53-final web / ios / android captures — so
# the brief's line-count, group-gap and glyph-extent claims are executed
# look-ups, not eyeballing. Ink = luminance < 160 (black text on white).
# Usage: python3 hyphenate-character.bands.py [run-id]   (default wave53-final)
import sys, os
from PIL import Image
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../../..'))
RUN = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
REF = f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text'
SEC = f'{ROOT}/tools/titan/runs/{RUN}/sections/css-text'
def bands(path, thr=160):
    im = Image.open(path).convert('L'); w, h = im.size; px = im.load()
    rows = []
    for y in range(h):
        xs = [x for x in range(w) if px[x, y] < thr]
        rows.append((min(xs), max(xs)) if xs else None)
    out, cur = [], None
    for y, r in enumerate(rows + [None]):
        if r and cur is None: cur = [y, y, r[0], r[1]]
        elif r: cur[1] = y; cur[2] = min(cur[2], r[0]); cur[3] = max(cur[3], r[1])
        elif cur: out.append(tuple(cur)); cur = None
    return out
for t in ('001', '002', '003', '004'):
    srcs = [('ref', f'{REF}/hyphens__hyphenate-character-{t}.png')] + [
        (p, f'{SEC}/{d}/wpt__css-text__hyphens__hyphenate-character-{t}.png')
        for p, d in (('web', 'screenshots'), ('ios', 'ios-screenshots'), ('android', 'android-screenshots'))]
    for name, p in srcs:
        b = bands(p)
        print(f'{t} {name:8s} {len(b):2d} bands:', ' '.join(f'y{a}-{z}/x{x0}-{x1}' for a, z, x0, x1 in b))
