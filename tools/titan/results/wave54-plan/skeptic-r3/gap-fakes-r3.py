# skeptic-r3: count-, bbox- and run-preserving fakes for flex-zero-gap-rules.geometry.py (fix r2). Each starts from
# the frozen ref of css-gaps/flex/flex-gap-decorations-033 and writes web/ios/android copies under
# skeptic-r3/fake-gap-<name>/sections/css-gaps/<dir>/. Colours are the ref's own (rule red (223,28,34)); a vacated
# pixel takes what the wave53-final iOS capture (which paints no rules at all) has at that pixel, i.e. the true
# background under the rule.
#   vshift5: line 2's single column-rule segment (x111-120, y71-110) moved DOWN 5 px (y76-115), over the second row
#            rule (red over blue at y111-115): every row read (y20/95/145) and every column read (x40/90/140) is
#            unchanged, the red count is unchanged, blue -50 (< 2 % of 3000), both bboxes unchanged.
#   short6:  line 2's segment shortened to y71-104 (red -60 = 2.7 %): must print WRONG (calibrates the 2 % tolerance).
#   line1-short4: line 1's two segments end 4 px early (y16-56; red -80 = 3.6 %): must print WRONG.
import os
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..', '..'))
REF = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/'
       'css-gaps/flex__flex-gap-decorations-033.png')
UNDER = f'{ROOT}/tools/titan/runs/wave53-final/sections/css-gaps/ios-screenshots/wpt__css-gaps__flex__flex-gap-decorations-033.png'
base = Image.open(REF).convert('RGB'); px0 = base.load()
under = Image.open(UNDER).convert('RGB').load()
RED = px0[115, 90]
def write(name, im):
    for d in ('screenshots', 'ios-screenshots', 'android-screenshots'):
        p = f'{HERE}/fake-gap-{name}/sections/css-gaps/{d}'
        os.makedirs(p, exist_ok=True)
        im.save(f'{p}/wpt__css-gaps__flex__flex-gap-decorations-033.png')
im = base.copy(); px = im.load()
for y in range(71, 76):
    for x in range(111, 121): px[x, y] = under[x, y]
for y in range(111, 116):
    for x in range(111, 121): px[x, y] = RED
write('vshift5', im)
im = base.copy(); px = im.load()
for y in range(105, 111):
    for x in range(111, 121): px[x, y] = under[x, y]
write('short6', im)
im = base.copy(); px = im.load()
for y in range(57, 61):
    for x in list(range(61, 71)) + list(range(111, 121)): px[x, y] = under[x, y]
write('line1-short4', im)
print('rule red', RED, '· background under the vacated rows', under[115, 72])
# line2-short4: line 2's segment ends 4 px early (y71-106; red -40 = 1.8 % < 2 %, the count tolerance): every row and
# column read unchanged — the largest single-segment truncation the count tolerance admits.
im = base.copy(); px = im.load()
for y in range(107, 111):
    for x in range(111, 121): px[x, y] = under[x, y]
write('line2-short4', im)
