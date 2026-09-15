#!/usr/bin/env python3
"""S6 skeptic PNG measurement kit — independent of any builder lane script."""
import sys, os, json
import numpy as np
from PIL import Image

REFROOT = 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins'
RUNROOT = 'tools/titan/runs'
DIRS = {'web': 'screenshots', 'ios': 'ios-screenshots', 'android': 'android-screenshots'}

def cap(run, sec, test, plat):
    return os.path.join(RUNROOT, run, 'sections', sec, DIRS[plat], f'wpt__{sec}__{test}.png')

def ref(sec, test):
    return os.path.join(REFROOT, sec, f'{test}.png')

def load(p):
    return np.array(Image.open(p).convert('RGB'))

def palette(a, top=8):
    cols, counts = np.unique(a.reshape(-1,3), axis=0, return_counts=True)
    o = np.argsort(-counts)
    return [[ [int(v) for v in cols[i]], int(counts[i]) ] for i in o[:top]], int(len(cols))

def inkbox(a, thresh=250):
    m = a.min(axis=2) < thresh
    ys, xs = np.nonzero(m)
    if len(ys)==0: return None
    return dict(x0=int(xs.min()), x1=int(xs.max()), y0=int(ys.min()), y1=int(ys.max()), n=int(m.sum()))

def summary(p, thresh=250):
    if not os.path.exists(p): return {'path': p, 'MISSING': True}
    a = load(p)
    top, n = palette(a)
    return {'path': p, 'h': int(a.shape[0]), 'w': int(a.shape[1]),
            'ncolors': n, 'top': top, 'inkbox': inkbox(a, thresh)}

def colorcount(p, rgb, tol=0):
    a = load(p).astype(int)
    d = np.abs(a - np.array(rgb)).max(axis=2)
    return int((d <= tol).sum())

if __name__ == '__main__':
    for p in sys.argv[1:]:
        print(json.dumps(summary(p), indent=1))
