#!/usr/bin/env python3
# Skeptic (wave 54, L3): ref | web | ios | android sheet of one test (full height up to H rows), own script.
# Usage: sk-sheet.py <run> <section> <tail> <out.png> [H]
import sys, os
from PIL import Image
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../../../..'))
REF = f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin'
run, sec, tail, out = sys.argv[1:5]; H = int(sys.argv[5]) if len(sys.argv) > 5 else 600
S = f'{ROOT}/tools/titan/runs/{run}/sections/{sec}'; cap = f'wpt__{sec}__{tail}.png'.replace('/', '__')
paths = [f'{REF}/{sec}/{tail}.png', f'{S}/screenshots/{cap}', f'{S}/ios-screenshots/{cap}', f'{S}/android-screenshots/{cap}']
ims = []
for p in paths:
    im = Image.open(p).convert('RGB'); print(os.path.basename(os.path.dirname(p)), im.size)
    ims.append(im.crop((0, 0, 400, H)))
sheet = Image.new('RGB', (4 * 404, H), (255, 0, 255))
for k, im in enumerate(ims): sheet.paste(im, (k * 404, 0))
sheet.save(out)
