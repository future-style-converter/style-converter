#!/usr/bin/env python3
# hyphenate-character.replay-b-radius.py — wave-54 planning replay of the BR-HEIGHT
# hunk on the four passing line-clamp carriers outside the family
# (css-overflow/line-clamp/block-ellipsis-002/-004/-005/-006), whose canvases hold
# nothing but the clamped text, so a row cut is a faithful model of "the 20px
# blank line after a text-ending br is gone". Each wave53-final capture gets every
# blank gap >= 20px (a normal inter-line gap is ~8px; one stacked 20px br adds 20)
# in its top 200 rows (>= 20px: one stacked br; a span border can eat the slack) shrunk by 20px, at most as many gaps as the census counts
# affected brs for that test (hyphenate-character.census.json brHeight[].n).
# Output: hyphenate-character-replay/<stem>-<plat>-B.png; prints the cuts.
import os, json
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '../../../..'))
OUT = os.path.join(HERE, 'hyphenate-character-replay')
SEC = f'{ROOT}/tools/titan/runs/wave53-final/sections/css-overflow'
DIRS = {'web': 'screenshots', 'ios': 'ios-screenshots', 'android': 'android-screenshots'}
census = json.load(open(os.path.join(HERE, 'hyphenate-character.census.json')))
n_by = {x['test']: x['n'] for x in census['brHeight']}
for t in ('002', '004', '005', '006'):
    stem = f'line-clamp__block-ellipsis-{t}'
    n = n_by[f'css-overflow/line-clamp/block-ellipsis-{t}.html']
    for plat, d in DIRS.items():
        im = Image.open(f'{SEC}/{d}/wpt__css-overflow__{stem}.png').convert('RGBA')
        g = im.convert('L'); px = g.load(); w, h = g.size
        ink = [any(px[x, y] < 200 for x in range(w)) for y in range(h)]
        first = next(y for y in range(h) if ink[y])
        gaps, start = [], None
        for y in range(first, 200):
            if not ink[y] and start is None: start = y
            if ink[y] and start is not None:
                if y - start >= 20: gaps.append(start)
                start = None
        gaps = gaps[:n]
        out = Image.new('RGBA', (w, h), (255, 255, 255, 255)); src, dst = 0, 0
        for s in gaps:
            out.paste(im.crop((0, src, w, s)), (0, dst)); dst += s - src; src = s + 20
        out.paste(im.crop((0, src, w, h)), (0, dst))
        out.save(f'{OUT}/{stem}-{plat}-B.png')
        print(stem, plat, 'affected brs', n, 'cut at', gaps)
