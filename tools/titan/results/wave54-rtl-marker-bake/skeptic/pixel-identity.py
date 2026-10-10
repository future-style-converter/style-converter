#!/usr/bin/env python3
# skeptic/pixel-identity.py — decoded-pixel identity of P's documents between two runs, per platform
# (the brief's [ident] claim re-derived), plus the counter-suffix rows 0-207 crop. Compares decoded RGBA
# bytes (not getbbox, which reads alpha only on RGBA in current Pillow). Usage: pixel-identity.py <runA> <runB>
import sys
from PIL import Image, ImageChops
A, B = sys.argv[1], sys.argv[2]
R = 'tools/titan/runs/{}/sections/{}/{}/wpt__{}.png'
docs = [('css-text', 'css-text__bidi__bidi-lines-001'), ('css-text', 'css-text__bidi__bidi-lines-002'),
        ('css-anchor-position', 'css-anchor-position__anchor-center-safe-rtl'), ('css-counter-styles', 'css-counter-styles__counter-suffix')]
for sec, stem in docs:
    for plat in ['screenshots', 'ios-screenshots', 'android-screenshots']:
        a = Image.open(R.format(A, sec, plat, stem)).convert('RGBA'); b = Image.open(R.format(B, sec, plat, stem)).convert('RGBA')
        same = a.size == b.size and a.tobytes() == b.tobytes()
        bb = None if same or a.size != b.size else ImageChops.difference(a.convert('RGB'), b.convert('RGB')).getbbox()
        top = a.crop((0, 0, a.width, 208)).tobytes() == b.crop((0, 0, b.width, 208)).tobytes()
        print(f"{stem:46} {plat:20} identical={same} diff-bbox={bb} rows0-207-identical={top}")
