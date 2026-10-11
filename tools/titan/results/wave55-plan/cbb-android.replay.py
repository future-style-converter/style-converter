#!/usr/bin/env python3
# tools/titan/results/wave55-plan/cbb-android.replay.py — pixel replay of unit U2 on Android's OWN pixels (cbb-android.md §6).
#
# Why: the closing-gate prediction for backdrop-filter-nested-border-radius-clip-3 android must not be read off the
# ref. This takes the wave54-probe android capture (U1 = 5b333c8a on the tree: #middle drawn as a 200×100 BORDER box,
# #inner 180×80, green clipped to it — cbb-android.look.out.txt) and re-lays it to the geometry U1+U2 must draw:
#   * #middle's border box grows 200×100 → 220×120 (its 10+10 padding band OUTSIDE the 100%), so #inner = 200×100;
#     done by splitting Android's own picture at the green's middle (x 125 | y 75) and moving the right / bottom halves
#     +20 px (the columns / rows in the gap repeat the split line, which is flat green inside #inner);
#   * #outer (overflow: hidden, padding box x16..235 / y16..135) clips #middle's overflow: everything right of x 235 or
#     below y 135 becomes the white canvas;
#   * the outer's three radius-10 corners right/bottom (where Android's moved pixels carry no outer rounding) are taken
#     from the ref's 10×10 corner squares — ~21 px each, the only ref pixels in the replay.
# Output: cbb-android-replay/android-clip-3.png (scored by cbb-android.replay-score.mjs).
import os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
REF = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/'
       'filter-effects/backdrop-filter-nested-border-radius-clip-3.png')
CAP = (f'{ROOT}/tools/titan/runs/wave54-probe/sections/filter-effects/android-screenshots/'
       'wpt__filter-effects__backdrop-filter-nested-border-radius-clip-3.png')
probe = Image.open(CAP).convert('RGB')
ref = Image.open(REF).convert('RGB')
W, H = probe.size
src, rpx = probe.load(), ref.load()
out = Image.new('RGB', (W, H), (255, 255, 255))
o = out.load()
SPLIT_X, SPLIT_Y, D = 125, 75, 20   # split inside the flat green; D = #middle's 10+10 padding band per axis


def from_probe(x, y):
    """The probe pixel that lands at (x, y) after the +D move of everything right of SPLIT_X / below SPLIT_Y."""
    sx = x if x <= SPLIT_X else (x - D if x - D > SPLIT_X else SPLIT_X)
    sy = y if y <= SPLIT_Y else (y - D if y - D > SPLIT_Y else SPLIT_Y)
    return src[sx, sy]


for y in range(H):
    for x in range(W):
        inside_outer = 16 <= x <= 235 and 16 <= y <= 135
        if inside_outer:
            o[x, y] = from_probe(x, y)
        elif x < 16 or y < 16 or x > 260 or y > 160:
            o[x, y] = src[x, y]          # the canvas outside the test box: Android's own (white) pixels
        # else: the clipped overflow band right of / below #outer stays white
# The outer's radius-10 corners where the move carried no outer rounding (top-right, bottom-right, bottom-left).
for (x0, y0) in ((226, 16), (226, 126), (16, 126)):
    for y in range(y0, y0 + 10):
        for x in range(x0, x0 + 10):
            o[x, y] = rpx[x, y]
os.makedirs(f'{HERE}/cbb-android-replay', exist_ok=True)
out.save(f'{HERE}/cbb-android-replay/android-clip-3.png')
print('wrote cbb-android-replay/android-clip-3.png', W, H)
