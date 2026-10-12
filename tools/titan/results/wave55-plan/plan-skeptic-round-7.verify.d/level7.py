# level7.py — for every MF1 acceptance: how many changed px reach the probes' ink level (min channel < 200) in the fake, and the
# darkest added value (min channel) — i.e. is the accepted paint below every probe level?
import sys, os, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common7 import *
import fakes7 as F7
CX = {(f'{s[0]}-{s[1]}', c.cell): c for s in F7.contexts() for c in s[4].values()}
FAM = dict(F7.FAMILIES)
REGION = ('hyphenate-character', 'text-decoration-inset', 'counter-suffix', 'css3-counter-styles-005', 'dir-style-02a')
seen = set(); hist = collections.Counter(); worst = []
mode = None
for l in open(sys.argv[1]):
    if l.startswith('MUST-FIX acceptances (fake'): mode = 1
    elif l.startswith('floor acc'): mode = None
    elif mode and l.startswith('   ') and ' | ' in l:
        a = l.strip().split(' | '); f, st, c = a[0], a[1], a[2]
        if (f, st, c) in seen or f.startswith(('S-moved', 'T-window')) or not any(r in c for r in REGION): continue
        seen.add((f, st, c)); cx = CX[(st, c)]
        o = np.clip(FAM[f](cx), 0, 255).astype(np.uint8).astype(np.int16)
        d = (o != cx.F).any(axis=2)
        vis = int((d & (o.min(axis=2) < 200) & (cx.F.min(axis=2) >= 200)).sum())
        dk = int(o.min(axis=2)[d].min())
        hist['new probe-level px 0' if vis == 0 else 'new probe-level px > 0'] += 1
        if vis: worst.append((vis, f, c.split('/')[-1], dk))
print(dict(hist), 'of', len(seen), 'distinct MF1 fakes')
for w in sorted(worst, reverse=True)[:15]: print('  ', w)
