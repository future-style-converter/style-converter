#!/usr/bin/env python3
# tools/titan/results/wave54-plan/queue-scout-layout.grid-safe.py — READ-ONLY: for
# css-grid/abspos/grid-abspos-staticpos-align-self-safe-001, the yellow+black container origins (y, x) and the teal
# (0,128,128) abspos boxes (y, x, h, w) in the ref and the three wave53-final captures (scipy connected components).
import os, sys
import numpy as np
from PIL import Image
from scipy import ndimage
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../../..'))
run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
R = ROOT + '/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-grid/abspos__grid-abspos-staticpos-align-self-safe-001.png'
S = f'{ROOT}/tools/titan/runs/{run}/sections/css-grid/'
stem = 'wpt__css-grid__abspos__grid-abspos-staticpos-align-self-safe-001.png'
for name, p in (('ref', R), ('web', S + 'screenshots/' + stem), ('ios', S + 'ios-screenshots/' + stem), ('android', S + 'android-screenshots/' + stem)):
    a = np.array(Image.open(p).convert('RGB')).astype(int)
    cont = ((a[..., 0] > 240) & (a[..., 1] > 240) & (a[..., 2] < 20)) | (a.sum(axis=2) < 60)
    lab, _ = ndimage.label(cont)
    boxes = sorted((s[0].start, s[1].start) for s in ndimage.find_objects(lab) if s[0].stop - s[0].start > 20)
    teal = (a[..., 0] < 10) & (abs(a[..., 1] - 128) < 8) & (abs(a[..., 2] - 128) < 8)
    lab, _ = ndimage.label(teal)
    tb = sorted((s[0].start, s[1].start, s[0].stop - s[0].start, s[1].stop - s[1].start) for s in ndimage.find_objects(lab))
    print(f'{name:8s} containers(y,x) {boxes}  teal(y,x,h,w) {tb}')
