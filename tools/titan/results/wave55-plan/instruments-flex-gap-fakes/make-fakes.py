#!/usr/bin/env python3
# tools/titan/results/wave55-plan/instruments-flex-gap-fakes/make-fakes.py — wave-55 L7 (brief instruments.md §7): the
# fake set the wave-54 "Wave 54 lessons" rule asks of a gating geometry probe ("the target ink removed, each run shifted
# +-2-3 px, each segment shortened, a count-preserving rearrangement"), for the ROW-RULE (blue) axis that R3V-S1 found
# unread. Built from the frozen ref in its own colours, exactly as wave54-plan/verify-r3/gap/make-fakes.py builds the
# two recorded fakes; a vacated pixel takes the wave53-final iOS capture's pixel (no rules painted = the true background).
# Each picture is written as all three platforms of css-gaps/flex/flex-gap-decorations-033.
#   blue-r1-lshort2     row rule 1 (y61-70, x16-165) starts 2 px late (x18-165): count -20, bbox kept by rule 2
#   blue-r1-split1      row rule 1 loses one column at x100 (two runs x16-99 / x101-165): count -10
#   blue-r1-moved5      COUNT-PRESERVING: row rule 1's x161-165 end block moved 10 px down (x161-165, y71-80)
#   blue-r2-hshift2     row rule 2 shifted 2 px right as a whole (x18-167): bbox +2 (the wave-54 probe already reds it)
#   blue-none           the target ink removed: both row rules painted as background
#   blue-r1-short1      TOLERANCE CONTROL: row rule 1 ends 1 px early (x16-164) — inside +-1 px, must stay OK
import os
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..', '..'))
REF = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/'
       'css-gaps/flex__flex-gap-decorations-033.png')
UNDER = f'{ROOT}/tools/titan/runs/wave53-final/sections/css-gaps/ios-screenshots/wpt__css-gaps__flex__flex-gap-decorations-033.png'
base = Image.open(REF).convert('RGB'); px0 = base.load(); under = Image.open(UNDER).convert('RGB').load()
BLUE = px0[40, 65]                       # row-rule ink as the ref paints it (the same read as the verify-r3 maker)


def write(name, im):
    # One picture, three platform folders: the probe reads web / ios / android from the section-runner layout.
    for d in ('screenshots', 'ios-screenshots', 'android-screenshots'):
        p = f'{HERE}/fake-{name}/sections/css-gaps/{d}'; os.makedirs(p, exist_ok=True)
        im.save(f'{p}/wpt__css-gaps__flex__flex-gap-decorations-033.png')


def vacate(px, xs, ys):
    # Paint the true background (the rule-less iOS capture) over a block.
    for x in xs:
        for y in ys:
            px[x, y] = under[x, y]


im = base.copy(); px = im.load(); vacate(px, range(16, 18), range(61, 71)); write('blue-r1-lshort2', im)
im = base.copy(); px = im.load(); vacate(px, (100,), range(61, 71)); write('blue-r1-split1', im)
im = base.copy(); px = im.load(); vacate(px, range(161, 166), range(61, 71))
for x in range(161, 166):
    for y in range(71, 81):
        px[x, y] = BLUE
write('blue-r1-moved5', im)
im = base.copy(); px = im.load()
row2 = {(x, y): px0[x, y] for x in range(16, 166) for y in range(111, 121)}   # the rule as painted (red crossings kept)
vacate(px, range(16, 18), range(111, 121))
for (x, y), c in row2.items():
    px[x + 2, y] = c
write('blue-r2-hshift2', im)
im = base.copy(); px = im.load()
for x in range(16, 166):
    for y in list(range(61, 71)) + list(range(111, 121)):
        if px0[x, y][2] > 200 and px0[x, y][0] < 80:   # only the blue ink; the red column rules never cross a row gap
            px[x, y] = under[x, y]
write('blue-none', im)
im = base.copy(); px = im.load(); vacate(px, (165,), range(61, 71)); write('blue-r1-short1', im)
print('BLUE', BLUE, 'under', under[17, 65], under[100, 65], under[163, 65], under[163, 75])
