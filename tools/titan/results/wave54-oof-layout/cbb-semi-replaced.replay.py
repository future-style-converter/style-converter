# L4 (CBB-android) blast-radius replay — the three css-position
# position-absolute-semi-replaced-stretch-{button,input,other} ANDROID captures,
# which the behavioural CBB census (DynamicValueResolverTest `CBB census`,
# cbb-census.out.txt) shows reading the corrected containing block: each
# `top/right/bottom/left: 3px` stretch child of a 3px-bordered content-box
# parent grows 138x88 -> 144x94 (344x94 for the 350-wide rows) with its
# top-left fixed (AbsposInsetStretch against 150x100 instead of 144x94).
# The replay moves each lime outline rectangle's right and bottom edges by
# +6 px (erase the old lime ring, redraw a 2-px ring on the grown bbox) and
# leaves every other pixel alone: the controls' own (wrong) native chrome is
# not this unit's. Read-only on the run dir; writes beside this script.
# usage: python3 cbb-semi-replaced.replay.py
import os
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
CAP = os.path.join(ROOT, 'tools/titan/runs/wave53-final/sections/css-position/android-screenshots')
OUT = os.path.join(HERE, 'cbb-semi-replaced-replay')
GROW = 6  # the two bands (2 x 3 px border) the frozen reading subtracted

def lime(p):
    # The outline is `lime` (0,255,0); a tolerance keeps edge AA pixels in.
    r, g, b = p[:3]
    return g > 180 and r < 90 and b < 90

def rings(im):
    # Connected components of lime pixels (4-neighbour flood fill), as bboxes.
    w, h = im.size; px = im.load(); seen = set(); out = []
    for y in range(h):
        for x in range(w):
            if (x, y) in seen or not lime(px[x, y]):
                continue
            stack = [(x, y)]; seen.add((x, y)); xs = []; ys = []; pts = []
            while stack:
                cx, cy = stack.pop(); xs.append(cx); ys.append(cy); pts.append((cx, cy))
                for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                    if 0 <= nx < w and 0 <= ny < h and (nx, ny) not in seen and lime(px[nx, ny]):
                        seen.add((nx, ny)); stack.append((nx, ny))
            # Only rectangle-sized components are outlines (skip AA specks).
            if max(xs) - min(xs) > 40 and max(ys) - min(ys) > 40:
                out.append(((min(xs), min(ys), max(xs), max(ys)), pts))
    return out

for t in ('button', 'input', 'other'):
    stem = f'wpt__css-position__position-absolute-semi-replaced-stretch-{t}.png'
    im = Image.open(os.path.join(CAP, stem)).convert('RGB'); px = im.load(); w, h = im.size
    found = rings(im)
    for (x0, y0, x1, y1), pts in found:
        # Erase the old ring (white: the stretch box has no background here).
        for (x, y) in pts:
            px[x, y] = (255, 255, 255)
        # Redraw a 2-px ring on the grown bbox, clipped to the capture.
        nx1, ny1 = min(x1 + GROW, w - 1), min(y1 + GROW, h - 1)
        for x in range(x0, nx1 + 1):
            for y in (y0, y0 + 1, ny1 - 1, ny1):
                px[x, y] = (0, 255, 0)
        for y in range(y0, ny1 + 1):
            for x in (x0, x0 + 1, nx1 - 1, nx1):
                px[x, y] = (0, 255, 0)
        print(t, 'ring', (x0, y0, x1, y1), '->', (x0, y0, nx1, ny1))
    os.makedirs(OUT, exist_ok=True)
    im.save(os.path.join(OUT, f'android-{t}.png'))
    print(t, 'rings', len(found))
