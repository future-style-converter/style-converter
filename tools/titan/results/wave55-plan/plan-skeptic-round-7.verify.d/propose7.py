# propose7.py — would the measured fix bases close the round-7 acceptances? (in-process, on the rebuilt fakes; a basis, not a fix)
#   MF1 rule: inside a neutral / ramp zone, a changed non-white px > 1 px from probe-level ink (min channel < 200) of the capture
#             or the base is non-class (basis7.out.txt: 0 of 172 598 legit changed px violate it)
#   MF2 rule: bidi's mask zone restricted to the four RTL bands (basis7b.out.txt: fixed 0, PLAIN 506)
import sys, os, json, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common7 import *
import fakes7 as F7
CX = {(f'{s[0]}-{s[1]}', c.cell): c for s in F7.contexts() for c in s[4].values()}
FAM = dict(F7.FAMILIES)
acc = [l.strip().split(' | ') for l in open(sys.argv[1]) if l.startswith('   ') and ' | ' in l]
seen = set(); res = collections.Counter(); left = collections.defaultdict(list)
for a in acc:
    fake, st, cell = a[0], a[1], a[2]
    if (fake, st, cell) in seen: continue
    seen.add((fake, st, cell))
    cx = CX[(st, cell)]; o = np.clip(FAM[fake](cx), 0, 255).astype(np.uint8).astype(np.int16)
    d = (o != cx.F).any(axis=2) & (o != cx.B).any(axis=2)
    if cx.kind == 'region':
        zc = RR.zone(o, cx.spec['zone'], True)
        ink = (o.min(axis=2) < 200) | (cx.B.min(axis=2) < 200); dist = F7.dist_map(ink, 4)
        n = int((d & zc & ~(o == 255).all(axis=2) & (dist > 1)).sum()); rule = 'MF1'
    elif 'bidi' in cell:
        band = np.zeros(d.shape, bool)
        for y0 in (86, 106, 186, 226): band[y0:y0 + 20] = True
        n = int((d & ~band & RR.PRED['blue3'](o)).sum()); rule = 'MF2'
    else:
        n = None; rule = '-'
    k = (rule, 'closed' if n else 'open' if n == 0 else 'n/a')
    res[k] += 1
    if not n: left[(rule, cell.split('/')[-1][:40])].append(fake)
print(dict(res))
for k, v in sorted(left.items()):
    print(k, len(v), v[:12])
