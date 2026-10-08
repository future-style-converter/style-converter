#!/usr/bin/env python3
# verify-r3 (plan-skeptic round 3 verifier): extra pictures for flex-zero-gap-rules.geometry.py, built from the frozen ref
# in its own colours; a vacated pixel takes the wave53-final iOS capture's pixel (no rules painted = the true background).
#   ref-as-run          all three platforms = the ref itself: a correct picture must print OK x3
#   red-l1r-down2       line 1's RIGHT column-rule segment (x111-120, y16-60) moved 2 px down (y18-62, over row rule 1)
#   red-l3r-up2         line 3's RIGHT segment (x111-120, y121-165) starts 2 px early (y119-165, over row rule 2)
#   blue-r1-short5      row rule 1 (y61-70, x16-165) ends 5 px early (x16-160): the ROW-rule horizontal extent
#   blue-r2-lshort4     row rule 2 (y111-120, x16-165) starts 4 px late (x20-165)
import os
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..', '..', '..'))
REF = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/'
       'css-gaps/flex__flex-gap-decorations-033.png')
UNDER = f'{ROOT}/tools/titan/runs/wave53-final/sections/css-gaps/ios-screenshots/wpt__css-gaps__flex__flex-gap-decorations-033.png'
base = Image.open(REF).convert('RGB'); px0 = base.load(); under = Image.open(UNDER).convert('RGB').load()
RED, BLUE = px0[115, 90], px0[40, 65]
def write(name, im):
    for d in ('screenshots', 'ios-screenshots', 'android-screenshots'):
        p = f'{HERE}/fake-{name}/sections/css-gaps/{d}'; os.makedirs(p, exist_ok=True)
        im.save(f'{p}/wpt__css-gaps__flex__flex-gap-decorations-033.png')
write('ref-as-run', base.copy())
im = base.copy(); px = im.load()
for x in range(111, 121):
    for y in (16, 17): px[x, y] = under[x, y]
    for y in (61, 62): px[x, y] = RED
write('red-l1r-down2', im)
im = base.copy(); px = im.load()
for x in range(111, 121):
    for y in (119, 120): px[x, y] = RED
write('red-l3r-up2', im)
im = base.copy(); px = im.load()
for x in range(161, 166):
    for y in range(61, 71): px[x, y] = under[x, y]
write('blue-r1-short5', im)
im = base.copy(); px = im.load()
for x in range(16, 20):
    for y in range(111, 121): px[x, y] = under[x, y]
write('blue-r2-lshort4', im)
print('RED', RED, 'BLUE', BLUE, 'under', under[163, 65], under[18, 115], under[115, 16])
