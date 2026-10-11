#!/usr/bin/env python3
# Round-4 verifier: what android-paint-order.geometry.py's `extra` reads WITH its 1-px dilation (the gate's rule) and WITHOUT
# it, on the five testable [X] fixed replays and on four of fakes4.py's accepted fakes (child fill copied 1 px down / left,
# its first component smeared 1 px, a 1x4 bar). Usage: python3.12 -B l6-extra-undilated.py <fakes4 --scratch dir of L6>
import numpy as np, os, sys
from PIL import Image
SC = sys.argv[1]
A = lambda p: np.asarray(Image.open(p).convert('RGB')).astype(int)
teal = lambda a: (a[..., 0] <= 40) & (a[..., 1] >= 100) & (a[..., 1] <= 160) & (a[..., 2] >= 100) & (a[..., 2] <= 160)
yel = lambda a: (a[..., 0] >= 200) & (a[..., 1] >= 200) & (a[..., 2] <= 80)
dark = lambda a: a.sum(axis=2) < 150                       # android-paint-order.geometry.py dark_px
def dil(m):                                                # its dilate1
    o = m.copy(); o[1:] |= m[:-1]; o[:-1] |= m[1:]; o[:, 1:] |= m[:, :-1]; o[:, :-1] |= m[:, 1:]; return o
W54 = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/runs/wave54-final/sections'
caps = [('css-flexbox', 'abspos__flex-abspos-staticpos-align-self-safe-001', yel), ('css-grid', 'abspos__grid-abspos-staticpos-align-self-001', teal),
        ('css-grid', 'abspos__grid-abspos-staticpos-justify-self-001', teal), ('css-grid', 'abspos__grid-abspos-staticpos-align-self-vertWM-001', teal),
        ('css-grid', 'abspos__grid-abspos-staticpos-align-self-rtl-001', teal)]
for sec, t, fl in caps:
    b = A(f'{W54}/{sec}/android-screenshots/wpt__{sec}__{t}.png'); fb = fl(b)
    row = []
    for run in ('FIXED', 'c0_vd-tgt+1', 'c0_hd-tgt-1', 'c0_dg-first-smear', 'c0_st-vbar'):
        a = A(os.path.realpath(f'{SC}/fakeruns/r4-L6-X-fixed-{run}/sections/{sec}/android-screenshots/wpt__{sec}__{t}.png')); fa = fl(a)
        row.append(f'{run}: dilated {int((fa & ~dil(fb | dark(b))).sum())} / undilated {int((fa & ~(fb | dark(b))).sum())}')
    print(t.split('__')[1], ' | '.join(row))
