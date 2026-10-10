#!/usr/bin/env python3
"""tools/titan/results/wave54-hyphenate-character/look.py — wave 54 lane L3: a side-by-side LOOK sheet
(ref | web | ios | android, top 480 rows, 2px grey gutters) of one test, from a recorded run and the frozen refs.
Usage: python3 look.py <run> <section> <stem-tail e.g. hyphens__hyphenate-character-001> <out.png>"""
import sys, os
from PIL import Image
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../../..'))
REF = f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin'
run, sec, tail, out = sys.argv[1:5]
S = f'{ROOT}/tools/titan/runs/{run}/sections/{sec}'
cap = f'wpt__{sec}__{tail}.png'
paths = [f'{REF}/{sec}/{tail}.png', f'{S}/screenshots/{cap}', f'{S}/ios-screenshots/{cap}', f'{S}/android-screenshots/{cap}']
ims = [Image.open(p).convert('RGB').crop((0, 0, 390, 480)) for p in paths]
sheet = Image.new('RGB', (4 * 390 + 3 * 2, 480), (160, 160, 160))
for k, im in enumerate(ims): sheet.paste(im, (k * 392, 0))
sheet.save(out); print(out, [os.path.basename(p) for p in paths])
