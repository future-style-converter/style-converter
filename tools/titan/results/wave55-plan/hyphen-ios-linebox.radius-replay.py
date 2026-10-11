#!/usr/bin/env python3
# tools/titan/results/wave55-plan/hyphen-ios-linebox.radius-replay.py — wave-55 L1 planning replay of the unit's
# NON-target radius (hyphen-ios-linebox.census.out.txt + .facemetrics.out.txt), iOS only, read-only on the runs.
#
# Which census documents can move at all: only those whose face × size gives Blink's rounded line box ≠ today's
# ceil'd natural box at their line count. For single-line runs (every radius run but the four targets):
#   DejaVu 16 / 20, Inter 16 / 36 → ceil(raw) == Blink (19/24/20/44): byte-identical by the model;
#   Linux Libertine 36 (boundary-shaping-001…008, `font: 36px test`) → 42 → 41: each stacked run 1 px shorter;
#   Inter 92 (text-decoration-dotted-001/-002: the leading split resolves the default design, Inter) → 112 → 111.
# Replay: re-place the stacked boxes 2..n of each such capture UP by (k−1) px (box k's top moves by the sum of the
# 1 px shortfalls above it), using whole-row regions split at the midpoints between the stacked boxes' ink bands.
# Output: hyphen-ios-linebox-replay/radius-<stem>-ios-U.png (scored by hyphen-ios-linebox.radius-score.mjs).
import os
from PIL import Image
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'hyphen-ios-linebox-replay')
TESTS = [('css-text', f'boundary-shaping__boundary-shaping-00{i}') for i in range(1, 9)] + \
        [('css-text-decor', 'text-decoration-dotted-001'), ('css-text-decor', 'text-decoration-dotted-002')]
Y0 = {'css-text': 15, 'css-text-decor': 75}   # below the <p> preamble of the decor tests; boundary-shaping has none
# Blank rows that separate two STACKED boxes: boundary-shaping's 36px runs sit 18-20 rows apart; a decor div's glyphs and
# its own dotted underline sit 10 rows apart while two divs sit 74 apart (the 46px collapsed margin), hence 30 there.
GAP = {'css-text': 8, 'css-text-decor': 30}


def ink(p):
    return max(p) < 110 or (p[0] > 200 and p[1] < 80 and p[2] < 80)   # black glyph cores or the red underline


for sec, stem in TESTS:
    p = f'{ROOT}/tools/titan/runs/wave54-final/sections/{sec}/ios-screenshots/wpt__{sec}__{stem}.png'
    im = Image.open(p).convert('RGB'); px = im.load(); w, h = im.size
    rows = [y for y in range(Y0[sec], h) if any(ink(px[x, y]) for x in range(w))]
    bands, cur = [], None
    for y in rows:   # stacked boxes: bands separated by more than GAP[sec] blank rows
        if cur and y - cur[1] <= GAP[sec]:
            cur[1] = y
        else:
            if cur: bands.append(cur)
            cur = [y, y]
    if cur: bands.append(cur)
    out = Image.new('RGB', (w, h), (255, 255, 255))
    out.paste(im.crop((0, 0, w, Y0[sec])), (0, 0))
    for i, (a, b) in enumerate(bands):
        top = Y0[sec] if i == 0 else (bands[i - 1][1] + a) // 2 + 1
        bot = h if i == len(bands) - 1 else (b + bands[i + 1][0]) // 2 + 1
        out.paste(im.crop((0, top, w, bot)), (0, top - i))
    out.save(f'{OUT}/radius-{stem}-ios-U.png')
    print(f'{sec}/{stem}: stacked bands {[tuple(x) for x in bands]} → shifted up by {list(range(len(bands)))}')
