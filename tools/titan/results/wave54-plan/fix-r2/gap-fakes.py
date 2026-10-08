#!/usr/bin/env python3
# fix r2 (R2-M1): two more adversarial pictures for flex-zero-gap-rules.geometry.py, built from the frozen ref, each
# keeping every red/blue pixel COUNT (so only the per-line / per-column runs can catch them):
#   fake-gap-blue : in item column 2 (x71-110) the first row rule (y61-70) is swapped with y71-80 (blue moved 10 px down
#                   inside line 2; the blue bbox is unchanged because columns 1/3 keep y61-70)
#   fake-gap-line3: line 3's left rule segment (x61-70, y121-165) is swapped with the white cells at x86-95 (inside item
#                   2 of line 3): same counts, same red bbox, rows 20/95 unchanged — only row 145 sees it
# Each is written as a run dir fix-r2/<name>/sections/css-gaps/<platform dir>/ (all three platforms the same picture).
import os
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..', '..'))
REF = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/'
       'css-gaps/flex__flex-gap-decorations-033.png')
def swap(im, box_a, dy=0, dx=0):
    """Swap the rectangle box_a=(x0,y0,x1,y1) (exclusive ends) with the same-size rectangle moved by (dx, dy)."""
    x0, y0, x1, y1 = box_a
    a = im.crop(box_a); b = im.crop((x0 + dx, y0 + dy, x1 + dx, y1 + dy))
    im.paste(b, (x0, y0)); im.paste(a, (x0 + dx, y0 + dy))
for name, (box, dx, dy) in {'fake-gap-blue': ((71, 61, 111, 71), 0, 10), 'fake-gap-line3': ((61, 121, 71, 166), 25, 0)}.items():
    im = Image.open(REF).convert('RGB'); swap(im, box, dy, dx)
    for d in ('screenshots', 'ios-screenshots', 'android-screenshots'):
        out = os.path.join(HERE, name, 'sections', 'css-gaps', d); os.makedirs(out, exist_ok=True)
        im.save(os.path.join(out, 'wpt__css-gaps__flex__flex-gap-decorations-033.png'))
    print(name, 'written')
