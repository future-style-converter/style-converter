# arms7.py — the residual read (installed residual-read.py, base wave55-open) on every replay / arm picture of the key cells
import sys, os, glob, re
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common7 import *
from PIL import Image
rows = []
def rd(label, cell, path):
    C = A(Image.open(path)); B = base_arr(cell); o, v, n, _ = RR.read_arrays(C, B, RR.SPECS[cell])
    rows.append((label, cell, o, v, n)); print(f'{label:46} {cell[:72]:72} {o:5} / {v:4} / {n:4}')
P = PLAN
for f in sorted(glob.glob(f'{P}/hyphen-ios-linebox-replay/0*-ios-*.png')):
    t, arm = re.match(r'.*/(\d+)-ios-(\w+)\.png', f).groups()
    c = f'css-text/hyphens/hyphenate-character-{t}.html ios'
    if c in RR.SPECS: rd(f'L1 {t} {arm}', c, f)
for f in sorted(glob.glob(f'{P}/ua-heading-margin-replay/text-decoration-inset-0*-*-*.png')):
    m = re.match(r'.*/(text-decoration-inset-\d+)-(ios|android)-(\w+)\.png', f)
    if not m: continue
    t, p, arm = m.groups(); c = f'css-text-decor/{t}.html {p}'
    if c in RR.SPECS: rd(f'L4 {t[-3:]} {p} {arm}', c, f)
for f in sorted(glob.glob(f'{P}/positioned-parent-slot-replay/*.png')):
    doc, arm = re.match(r'.*/(.*)-android-(\w+)\.png', f).groups()
    c = {'dir-style-02a': 'selectors/dir-style-02a.html android', 'bidi-tab-001': 'css-text/bidi/bidi-tab-001.html android',
         'anchor-position-multicol-nested-001': 'css-anchor-position/anchor-position-multicol-nested-001.html android'}.get(doc, f'css-writing-modes/{doc}.html android')
    if c in RR.SPECS: rd(f'L3 {doc[:26]} {arm}', c, f)
for d in sorted(glob.glob(f'{P}/counter-suffix-rows-replay/*/')):
    for p in ('ios', 'android'):
        for stem, c in (('counter-suffix', f'css-counter-styles/counter-suffix.html {p}'), ('cjk-decimal__css3-counter-styles-005', f'css-counter-styles/cjk-decimal/css3-counter-styles-005.html {p}')):
            f = f'{d}{stem}.{p}.png'
            if os.path.exists(f) and c in RR.SPECS: rd(f'L5 {os.path.basename(d[:-1])} {stem[:14]} {p}', c, f)
for f in sorted(glob.glob(f'{P}/android-paint-order-replay/*.png')):
    stem = os.path.basename(f).replace('-android-U2.png', '').replace('-android-U1.png', '')
    for sec in ('css-flexbox', 'css-grid', 'filter-effects'):
        c = f'{sec}/{stem.replace("__", "/")}.html android'
        if c in RR.SPECS: rd(f'L6 {stem[-34:]}', c, f)
for f in sorted(glob.glob(f'{P}/cbb-android-replay/*.png')):
    rd(f'L2 clip-3 {os.path.basename(f)}', 'filter-effects/backdrop-filter-nested-border-radius-clip-3.html android', f)
