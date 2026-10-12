# basis7.py — fix basis for the faint-ink class (MF1): on every fixed picture and legitimate arm of the neutral / ramp cells,
# how far does a changed, non-white px inside the declared zone lie from probe-level ink (min channel < 200) of the capture
# or the base? (0 = it is itself probe-level ink; 1 = in the 1-px AA ring.) A rule "changed non-white in-zone px within 1 px
# of probe-level ink" is tolerance-0-safe iff every max here is ≤ 1.
import sys, os, glob, re
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common7 import *
from PIL import Image
import fakes7 as F7
def far(C, B, spec):
    win = np.zeros(C.shape[:2], bool)
    zc = RR.zone(C, spec['zone'], True)
    d = (C != B).any(axis=2) & zc & ~(C == 255).all(axis=2)
    ink = (C.min(axis=2) < 200) | (B.min(axis=2) < 200)
    dist = F7.dist_map(ink, 8)
    return int(d.sum()), (int(dist[d].max()) if d.any() else 0), int((d & (dist > 1)).sum()), int((d & (dist > 2)).sum())
P = PLAN
items = []
for lane, setn, secs, repl, cxs in F7.contexts():
    for k3, cx in cxs.items():
        if cx.kind == 'region':
            items.append((f'{lane}-{setn}', cx.cell, cx.F))
for f in sorted(glob.glob(f'{P}/hyphen-ios-linebox-replay/0*-ios-*.png')):
    t, arm = re.match(r'.*/(\d+)-ios-(\w+)\.png', f).groups(); c = f'css-text/hyphens/hyphenate-character-{t}.html ios'
    if c in RR.SPECS: items.append((f'L1-{arm}', c, A(Image.open(f))))
for f in sorted(glob.glob(f'{P}/ua-heading-margin-replay/text-decoration-inset-0*-*-*.png')):
    m = re.match(r'.*/(text-decoration-inset-\d+)-(ios|android)-(C|D|U1)\.png', f)
    if m and f'css-text-decor/{m.group(1)}.html {m.group(2)}' in RR.SPECS: items.append((f'L4-{m.group(3)}', f'css-text-decor/{m.group(1)}.html {m.group(2)}', A(Image.open(f))))
for arm in ('U1', 'U1U2'):
    items.append((f'L3-{arm}', 'selectors/dir-style-02a.html android', A(Image.open(f'{P}/positioned-parent-slot-replay/dir-style-02a-android-{arm}.png'))))
tot = [0, 0, 0]
for lab, c, C in items:
    n, mx, g1, g2 = far(C, base_arr(c), RR.SPECS[c]); tot[0] += n; tot[1] += g1; tot[2] += g2
    print(f'{lab:12} {c[:70]:70} changed non-white in-zone {n:5}  max dist {mx}  > 1 px: {g1:4}  > 2 px: {g2:4}')
print(f'ALL: {tot[0]} changed non-white in-zone px; {tot[1]} lie > 1 px from probe-level ink; {tot[2]} > 2 px')
