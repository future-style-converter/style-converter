#!/usr/bin/env python3
# fix r3 (plan-skeptic round 3, R3-S3): two MORE pictures for flex-zero-gap-rules.geometry.py whose only error is a
# column-rule segment's VERTICAL extent, each smaller than the skeptic's (vshift5 / line2-short4) and on a different
# segment, so the fixed probe is shown to pin every segment at ±1 px rather than to have learnt two fakes. Built from the
# frozen ref of css-gaps/flex/flex-gap-decorations-033 in its own colours; a vacated pixel takes what the wave53-final
# iOS capture (which paints no rules) has there, i.e. the true background under the rule (skeptic-r3/gap-fakes-r3.py).
#   vshift3-up:   line 2's single segment (x111-120, y71-110) moved UP 3 px (y68-107), painted over the first row rule
#                 (red over blue at y68-70): red count unchanged, blue -30 (1 % of 3000), both bboxes unchanged, rows
#                 y20/95/145 and columns x40/90/140 unchanged.
#   line3-short3: line 3's LEFT segment (x61-70, y121-165) ends 3 px early (y121-162): red -30 (1.4 % < the 2 % count
#                 tolerance), the red bbox still ends at y165 (line 3's right segment), every row and column read unchanged.
# Each is written as a run dir fix-r3/fake-gap-<name>/sections/css-gaps/<platform dir>/ (all three platforms the same).
import os
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..', '..'))
REF = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/'
       'css-gaps/flex__flex-gap-decorations-033.png')
UNDER = f'{ROOT}/tools/titan/runs/wave53-final/sections/css-gaps/ios-screenshots/wpt__css-gaps__flex__flex-gap-decorations-033.png'
base = Image.open(REF).convert('RGB'); px0 = base.load()
under = Image.open(UNDER).convert('RGB').load()
RED = px0[115, 90]                       # the ref's own column-rule red, read inside line 2's segment


def write(name, im):
    for d in ('screenshots', 'ios-screenshots', 'android-screenshots'):
        p = f'{HERE}/fake-gap-{name}/sections/css-gaps/{d}'
        os.makedirs(p, exist_ok=True)
        im.save(f'{p}/wpt__css-gaps__flex__flex-gap-decorations-033.png')


im = base.copy(); px = im.load()
for x in range(111, 121):
    for y in range(108, 111): px[x, y] = under[x, y]     # vacate the segment's last 3 rows
    for y in range(68, 71): px[x, y] = RED               # and paint 3 rows above it, over the row rule
write('vshift3-up', im)

im = base.copy(); px = im.load()
for x in range(61, 71):
    for y in range(163, 166): px[x, y] = under[x, y]     # line 3's left segment loses its last 3 rows
write('line3-short3', im)
print('rule red', RED, '· background under the vacated rows', under[115, 109], under[65, 164])
