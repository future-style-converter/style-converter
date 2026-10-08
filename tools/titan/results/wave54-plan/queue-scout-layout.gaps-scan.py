#!/usr/bin/env python3
# tools/titan/results/wave54-plan/queue-scout-layout.gaps-scan.py — READ-ONLY: for every css-gaps ref, the count of
# "ref-only saturated ink" px per native capture (ref pixel saturated (max-min channel > 120), capture pixel not, and
# the two differ by > 90 summed channels) — i.e. decoration ink the ref paints and the native does not. Prints every
# test with > 50 such px on either native. Usage: python3 queue-scout-layout.gaps-scan.py [run=wave53-final]
import os, sys
import numpy as np
from PIL import Image
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../../..'))
REF = ROOT + '/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-gaps/'
run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
S = f'{ROOT}/tools/titan/runs/{run}/sections/css-gaps/'
sat = lambda a: (a.max(axis=2) - a.min(axis=2)) > 120
n = 0
for f in sorted(os.listdir(REF)):
    r = np.array(Image.open(REF + f).convert('RGB')).astype(int); n += 1
    row = []
    for d in ('ios-screenshots', 'android-screenshots'):
        p = f'{S}{d}/wpt__css-gaps__{f}'
        c = np.array(Image.open(p).convert('RGB')).astype(int) if os.path.exists(p) else None
        row.append('—' if c is None or c.shape != r.shape else int((sat(r) & ~sat(c) & (np.abs(r - c).sum(axis=2) > 90)).sum()))
    if any(isinstance(x, int) and x > 50 for x in row):
        print(f'{f[:-4]:40s} ios {row[0]!s:>6}  android {row[1]!s:>6}')
print(f'{n} css-gaps refs scanned ({run})')
