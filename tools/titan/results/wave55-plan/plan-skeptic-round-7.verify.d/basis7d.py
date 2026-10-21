# basis7d.py — fix bases for MF5 (L6 [X] child fill over the band), MF6 ([PO] overlap tolerance), MF7 (stack: other squares)
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common7 import *
import fakes7 as F7
CX = {(f'{s[0]}-{s[1]}', c.cell): c for s in F7.contexts() for c in s[4].values()}
FAM = dict(F7.FAMILIES)
def lab4(m):
    lab = np.zeros(m.shape, np.int32); n = 0; H, W = m.shape
    for y0, x0 in zip(*np.nonzero(m)):
        if lab[y0, x0]: continue
        n += 1; st = [(y0, x0)]; lab[y0, x0] = n
        while st:
            y, x = st.pop()
            for yy, xx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
                if 0 <= yy < H and 0 <= xx < W and m[yy, xx] and not lab[yy, xx]:
                    lab[yy, xx] = n; st.append((yy, xx))
    return lab
def orphan_reveal(C, B, fill):
    """revealed fill px (capture fill on the base band, sum < 150) whose 4-connected fill component holds no base-fill px"""
    fc, fb, band = fill(C), fill(B), B.sum(axis=2) < 150
    lab = lab4(fc); keep = set(np.unique(lab[fc & fb])) - {0}
    rev = fc & band
    return int(rev.sum()), int((rev & ~np.isin(lab, list(keep))).sum())
print('MF5 [X]: revealed fill px on the base band / of them in a fill component with NO base-fill px (an orphan reveal)')
for (st, cell), cx in sorted(CX.items()):
    if st != 'L6-X-U2': continue
    fill = RR.PRED[cx.spec['ink'][0]]
    print(f'  fixed  {cell.split("/")[-1][:52]:52} {orphan_reveal(cx.F, cx.B, fill)}')
    for f in ('T-L6-X-fill-over-band-3pct', 'T-L6-X-fill-over-band-6pct', 'T-L6-X-fill-over-band-9pct', 'A-class-speck2-edge'):
        o = FAM[f](cx)
        if o is not None:
            print(f'    fake {f:34} {orphan_reveal(np.clip(o, 0, 255).astype(np.int16), cx.B, fill)}')
print('MF6 [PO]: overlap rows (166/167/218/219 × x163-266) red / blue on the fixed U1 replay and the base')
po = CX[('L6-PO-U1', 'filter-effects/backdrop-filter-clip-rect.html android')]
for lab, a in (('fixed U1', po.F), ('base', po.B)):
    O = a[[166, 167, 218, 219], 163:267]
    print(f'  {lab:9} red {int(RR.PRED["red6"](O).sum())} blue {int(((O[..., 2] >= 200) & (O[..., 0] <= 60) & (O[..., 1] <= 60)).sum())}; red px in the picture {int(RR.PRED["red6"](a).sum())}')
print('MF7 stack: green3 outside the four cells\' ±2 keep boxes, fixed vs base (XOR px), and on the fakes')
sk = CX[('L3-fixed', 'css-anchor-position/anchor-position-multicol-nested-001.html android')]
keep = np.zeros(sk.F.shape[:2], bool)
for x, y in PT.L3_CELLS: keep[y - 2:y + 12, x - 2:x + 12] = True
g = RR.PRED['green3']
print(f'  fixed: green outside the keep boxes {int((g(sk.F) & ~keep).sum())} px, XOR vs base {int(((g(sk.F) ^ g(sk.B)) & ~keep).sum())}')
for f in ('T-L3-stack-other-square-moved+24+0', 'T-L3-stack-other-square-moved+0+30', 'T-L3-stack-other-square-moved-40+10'):
    o = np.clip(FAM[f](sk), 0, 255).astype(np.int16)
    print(f'  {f}: green outside {int((g(o) & ~keep).sum())} px, XOR vs base {int(((g(o) ^ g(sk.B)) & ~keep).sum())}')
web = RR.rgb(RR.capture_path('wave55-open', 'css-anchor-position/anchor-position-multicol-nested-001.html web'))
ios = RR.rgb(RR.capture_path('wave55-open', 'css-anchor-position/anchor-position-multicol-nested-001.html ios'))
for lab, a in (('fixed U1 replay', sk.F), ('ios wave55-open', ios)):
    h, w = min(a.shape[0], web.shape[0]), min(a.shape[1], web.shape[1])
    print(f'  {lab}: green-outside-the-cells mask XOR web (wave55-open) {int(((g(a[:h, :w]) ^ g(web[:h, :w])) & ~keep[:h, :w]).sum())} px')
for f in ('T-L3-stack-other-square-moved+24+0', 'T-L3-stack-other-square-moved+0+30', 'T-L3-stack-other-square-moved-40+10'):
    o = np.clip(FAM[f](sk), 0, 255).astype(np.int16); h, w = min(o.shape[0], web.shape[0]), min(o.shape[1], web.shape[1])
    print(f'  {f}: XOR web {int(((g(o[:h, :w]) ^ g(web[:h, :w])) & ~keep[:h, :w]).sum())} px')
print('  one-sided: green outside the cells\' keep boxes that the BASE does not have green at (U1 moves the four cells, never the others):')
for lab, a in [('fixed U1 replay', sk.F)] + [(f, np.clip(FAM[f](sk), 0, 255).astype(np.int16)) for f in ('T-L3-stack-other-square-moved+24+0', 'T-L3-stack-other-square-moved+0+30', 'T-L3-stack-other-square-moved-40+10')]:
    print(f'    {lab}: {int((g(a) & ~g(sk.B) & ~keep).sum())} px')
