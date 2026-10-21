# fixcheck3.py — the [X] fix measured against the BASE android capture (wave54-final = wave55-open byte-identical), not the
# ref: U2 changes WHICH paint wins, never where a box is, so (a) the child-fill bbox must equal the base's ±1 and the fill
# count must be >= the base's (the band no longer covers it), and (b) the parent band must still be there: dark px >= 0.85 x
# min(base, ref) dark px (round 2 of this check: on the flex pair the base band is 2x the ref's — the crossings). Read-only.
import os
from lib import *
teal = lambda a: (a[..., 0] <= 40) & (a[..., 1] >= 100) & (a[..., 1] <= 160) & (a[..., 2] >= 100) & (a[..., 2] <= 160)
yellow = lambda a: (a[..., 0] >= 200) & (a[..., 1] >= 200) & (a[..., 2] <= 80)
dark = lambda a: a.sum(axis=2) < 150
FK = f'{SCR}/fakes'
X = [('css-flexbox', 'abspos/flex-abspos-staticpos-align-self-safe-001', yellow), ('css-flexbox', 'abspos/flex-abspos-staticpos-align-self-safe-002', yellow),
     ('css-grid', 'abspos/grid-abspos-staticpos-align-self-001', teal), ('css-grid', 'abspos/grid-abspos-staticpos-justify-self-001', teal),
     ('css-grid', 'abspos/grid-abspos-staticpos-align-self-vertWM-001', teal), ('css-grid', 'abspos/grid-abspos-staticpos-align-self-rtl-001', teal)]
for sec, t, fill in X:
    B = A(cap('wave54-final', sec, t, 'android')); by, bx = np.nonzero(fill(B)); bb = (bx.min(), by.min(), bx.max(), by.max())
    bd = min(int(dark(B).sum()), int(dark(A(ref(sec, t))).sum()))   # the band floor: the smaller of base and ref dark px
    cases = [('fixed U2 replay', A(Image.open(f'{P55}/android-paint-order-replay/{t.replace("/", "__")}-android-U2.png')))]
    cases += [(f, A(Image.open(f'{FK}/L6-X-{f}/android-screenshots__{cap_name(sec, t)}'))) for f in ('border-erased', 'child+3x', 'child-3y', 'child-shrunk8%', 'child-scrambled')]
    for name, a in cases:
        y, x = np.nonzero(fill(a)); b = (x.min(), y.min(), x.max(), y.max()); d = int(dark(a).sum())
        ok = all(abs(u - v) <= 1 for u, v in zip(b, bb)) and len(x) >= 0.98 * len(bx) and d >= 0.9 * bd
        print(f'  {t.split("staticpos-")[-1]:22} {name:18} fill {len(x)}/{len(bx)} bbox Δ{max(abs(u - v) for u, v in zip(b, bb))} dark {d}/{bd} → {"OK" if ok else "WRONG"}')
