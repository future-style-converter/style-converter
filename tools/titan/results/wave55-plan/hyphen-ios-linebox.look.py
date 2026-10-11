#!/usr/bin/env python3
# tools/titan/results/wave55-plan/hyphen-ios-linebox.look.py — wave-55 L1 look sheets (read-only on runs/refs).
# One sheet per test: columns ref | ios wave54-final | ios wave54-ab-drop-U3b | ios replay Rm (the unit's model) | web
# wave54-final, the <div> crop (x 0-110, y 80-470) at 2x, with a 1-px red guide at every ref line-band top so a
# one-row drift is visible by eye. Output: hyphen-ios-linebox.look-<t>.png beside this file.
import os
from PIL import Image, ImageDraw
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
HERE = os.path.dirname(os.path.abspath(__file__))
REFS = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/'
        'white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text')
cap = lambda run, d, t: f'{ROOT}/tools/titan/runs/{run}/sections/css-text/{d}/wpt__css-text__hyphens__hyphenate-character-{t}.png'
X0, X1, Y0, Y1, S = 0, 110, 80, 470, 2
for t in ('001', '002', '003', '004'):
    cols = [('ref', f'{REFS}/hyphens__hyphenate-character-{t}.png'),
            ('ios final', cap('wave54-final', 'ios-screenshots', t)),
            ('ios drop-U3b', cap('wave54-ab-drop-U3b', 'ios-screenshots', t)),
            ('ios Rm (model)', f'{HERE}/hyphen-ios-linebox-replay/{t}-ios-Rm.png'),
            ('web final', cap('wave54-final', 'screenshots', t))]
    ref = Image.open(cols[0][1]).convert('RGB'); rp = ref.load()
    tops, inband = [], False
    for y in range(Y0 + 20, Y1):
        hit = any(max(rp[x, y]) < 110 for x in range(X0, X1))
        if hit and not inband:
            tops.append(y)
        inband = hit if hit else (inband and any(max(rp[x, y + d]) < 110 for d in (1, 2) for x in range(X0, X1)))
    W, H = (X1 - X0) * S, (Y1 - Y0) * S
    sheet = Image.new('RGB', (len(cols) * (W + 8), H + 24), (255, 255, 255))
    d = ImageDraw.Draw(sheet)
    for i, (lab, p) in enumerate(cols):
        im = Image.open(p).convert('RGB').crop((X0, Y0, X1, Y1)).resize((W, H), Image.NEAREST)
        dd = ImageDraw.Draw(im)
        for y in tops:
            dd.line([(0, (y - Y0) * S), (W, (y - Y0) * S)], fill=(255, 0, 0))
        sheet.paste(im, (i * (W + 8), 24))
        d.text((i * (W + 8) + 2, 4), lab, fill=(0, 0, 0))
    sheet.save(f'{HERE}/hyphen-ios-linebox.look-{t}.png')
    print(t, 'ref line tops (red guides):', tops)
