#!/usr/bin/env python3
# tools/titan/results/wave54-plan/queue-scout-layout.ink.py
# READ-ONLY pixel probe for the queue-scout-layout brief: per test, the bbox and
# pixel count of the strict-green (0,128,0)±8 and strict-red (r>200,g<80,b<80)
# ink in the frozen ref and in the <run>'s web / iOS / Android captures.
# Usage: python3 queue-scout-layout.ink.py <run> <section>/<test-path> [...]
import sys, os
import numpy as np
from PIL import Image
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../../..'))
REF = ROOT + '/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin'
run = sys.argv[1]
def bbox(m):
    ys, xs = np.nonzero(m)
    return f'{int(m.sum()):6d}px x{xs.min()}-{xs.max()} y{ys.min()}-{ys.max()}' if len(xs) else '     0px'
for arg in sys.argv[2:]:
    sec, test = arg.split('/', 1)
    flat = test.replace('/', '__')
    stem = f'wpt__{sec}__{flat}.png'
    S = f'{ROOT}/tools/titan/runs/{run}/sections/{sec}'
    print(arg)
    for name, p in [('ref', f'{REF}/{sec}/{flat}.png'), ('web', f'{S}/screenshots/{stem}'), ('ios', f'{S}/ios-screenshots/{stem}'), ('android', f'{S}/android-screenshots/{stem}')]:
        if not os.path.exists(p):
            print(f'  {name:8s} MISSING'); continue
        a = np.array(Image.open(p).convert('RGB')).astype(int)
        g = (abs(a[..., 0]) < 8) & (abs(a[..., 1] - 128) < 8) & (a[..., 2] < 8)
        r = (a[..., 0] > 200) & (a[..., 1] < 80) & (a[..., 2] < 80)
        print(f'  {name:8s} {a.shape[1]}x{a.shape[0]}  green {bbox(g)}   red {bbox(r)}')
