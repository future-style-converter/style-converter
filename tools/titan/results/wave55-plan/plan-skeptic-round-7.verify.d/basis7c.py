# basis7c.py — fix basis for MF3 (window palette paint): on each window cell's fixed picture, which colour classes do the
# changed px INSIDE the windows take on the capture (flat palette class within ±10, else AA), and which do they leave on the base?
import sys, os, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common7 import *
import fakes7 as F7
for lane, setn, secs, repl, cxs in F7.contexts():
    for k3, cx in cxs.items():
        ws = cx.spec.get('windows', [])
        if not ws: continue
        pal = cx.spec.get('palette', [])
        d = (cx.F != cx.B).any(axis=2)
        cnt = collections.Counter(); bcnt = collections.Counter()
        for x0, y0, x1, y1 in ws:
            for y in range(y0, y1):
                for x in range(x0, x1):
                    if not d[y, x]: continue
                    c = next((n for n in pal if (abs(cx.F[y, x] - np.array(RR.PALETTE[n])) <= 10).all()), 'AA')
                    b = next((n for n in pal if (abs(cx.B[y, x] - np.array(RR.PALETTE[n])) <= 10).all()), 'AA')
                    cnt[c] += 1; bcnt[(b, c)] += 1
        print(f'{cx.cell[:72]:72} windows {len(ws)}: changed px by capture class {dict(cnt)}')
        print(f'{"":72} base→capture {dict(bcnt)}')
