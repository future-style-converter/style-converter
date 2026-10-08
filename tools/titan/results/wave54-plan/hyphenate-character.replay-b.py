#!/usr/bin/env python3
# hyphenate-character.replay-b.py — wave-54 planning replay, the "B only" arm.
# Takes each wave53-final capture of hyphenate-character-00{1..4} AS IS (its own
# glyphs, breaks and non-bold <p>) and removes ONLY the extra blank line the
# extractor's 20px br adds before groups 3 and 4: the g2|g3 and g3|g4 blank bands
# are shrunk to the capture's own g1|g2 band (the one-blank-line gap the ref
# paints everywhere). Output: hyphenate-character-replay/<t>-<plat>-B.png.
# Prints the gaps it found so the cut is auditable.
import os
from PIL import Image
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../../..'))
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'hyphenate-character-replay')
SEC = f'{ROOT}/tools/titan/runs/wave53-final/sections/css-text'
DIRS = {'web': 'screenshots', 'ios': 'ios-screenshots', 'android': 'android-screenshots'}
DIV0 = {'001': 100, '002': 100, '003': 80, '004': 80}
def gaps(im, y0):
    g = im.convert('L'); px = g.load(); w, h = g.size
    ink = [any(px[x, y] < 160 for x in range(w)) for y in range(h)]
    last = max(y for y in range(h) if ink[y])
    out, start = [], None
    for y in range(y0, last + 1):
        if not ink[y] and start is None: start = y
        if ink[y] and start is not None:
            if y - start >= 20: out.append((start, y))
            start = None
    return out
for t in DIV0:
    for plat, d in DIRS.items():
        im = Image.open(f'{SEC}/{d}/wpt__css-text__hyphens__hyphenate-character-{t}.png').convert('RGBA')
        gs = gaps(im, DIV0[t] + 15)
        if len(gs) != 3:
            print(t, plat, 'UNEXPECTED gaps', gs); continue
        base = gs[0][1] - gs[0][0]
        cuts = [(a, b, (b - a) - base) for a, b in gs[1:]]
        rows = []
        y = 0
        for a, b, n in cuts:
            rows.append((y, a + 1)); y = a + 1 + n
        rows.append((y, 600))
        out = Image.new('RGBA', (390, 600), (255, 255, 255, 255)); dy = 0
        for a, b in rows:
            out.paste(im.crop((0, a, 390, b)), (0, dy)); dy += b - a
        out.save(f'{OUT}/{t}-{plat}-B.png')
        print(t, plat, 'gaps', gs, 'cut', [n for _, _, n in cuts])
