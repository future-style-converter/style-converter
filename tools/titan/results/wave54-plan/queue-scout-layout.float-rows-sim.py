#!/usr/bin/env python3
# tools/titan/results/wave54-plan/queue-scout-layout.float-rows-sim.py
# READ-ONLY (writes only beside itself): would packing the body-level float ROOTS into rows alone flip
# css-writing-modes/abs-pos-border-offset-001/-002 on the natives? Splits each native capture's vertical stack of
# `.cb` float boxes into individual boxes (gray (128,128,128) border rows: 1-px top + 3-px bottom), re-composites them
# at the REF's box positions on white (= a perfect float-row packer that changes nothing inside a box), writes
# queue-scout-layout.float-rows/<test>-<platform>-rows.png, and prints per-box identity against the ref.
# Score the composites with: node queue-scout-layout.float-rows-score.mjs
# Usage: python3 queue-scout-layout.float-rows-sim.py [run=wave53-final]
import os, sys
import numpy as np
from PIL import Image
from scipy import ndimage
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '../../../..'))
REF = ROOT + '/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-writing-modes/'
run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
S = f'{ROOT}/tools/titan/runs/{run}/sections/css-writing-modes/'
OUT = os.path.join(HERE, 'queue-scout-layout.float-rows'); os.makedirs(OUT, exist_ok=True)
gray = lambda a: (abs(a[..., 0] - 128) < 6) & (abs(a[..., 1] - 128) < 6) & (abs(a[..., 2] - 128) < 6)
def boxes(p):
    a = np.array(Image.open(p).convert('RGB')).astype(int)
    lab, _ = ndimage.label(a.sum(axis=2) < 740)
    out = []
    for s in ndimage.find_objects(lab):
        h, w = s[0].stop - s[0].start, s[1].stop - s[1].start
        if h < 30 or w < 30: continue
        sub = a[s]; full = gray(sub).all(axis=1)
        runs, i = [], 0
        while i < h:
            if full[i]:
                j = i
                while j < h and full[j]: j += 1
                runs.append((i, j)); i = j
            else: i += 1
        tops, bots = [], []
        for i, j in runs:
            if i == 0: tops.append(0)
            if j - i == 4 and i > 0: bots.append(i + 2); tops.append(i + 3)
            if j == h: bots.append(h - 1)
        if len(tops) == len(bots) and tops:
            for t, b in zip(tops, bots): out.append((s[0].start + t, s[1].start, b - t + 1, w, sub[t:b + 1]))
        else: out.append((s[0].start, s[1].start, h, w, sub))
    return a, out
for t in ('abs-pos-border-offset-001', 'abs-pos-border-offset-002'):
    ref, rb = boxes(REF + t + '.png'); rb.sort(key=lambda b: (b[0] // 20, b[1]))
    for plat, d in (('ios', 'ios-screenshots'), ('android', 'android-screenshots')):
        _, cb = boxes(f'{S}{d}/wpt__css-writing-modes__{t}.png'); cb.sort(key=lambda b: (b[1] // 200, b[0]))
        comp = np.full_like(ref, 255); same = 0
        for i, (ry, rx, h, w, A) in enumerate(rb):
            B = cb[i][4] if i < len(cb) else None
            if B is not None and B.shape[:2] == (h, w):
                comp[ry:ry + h, rx:rx + w] = B
                same += int((np.abs(A - B).sum(axis=2) > 30).sum() == 0)
        Image.fromarray(comp.astype(np.uint8)).save(f'{OUT}/{t}-{plat}-rows.png')
        print(f'{t} {plat}: ref boxes {len(rb)}, native boxes {len(cb)}, box-identical to ref {same}/{len(rb)}')
