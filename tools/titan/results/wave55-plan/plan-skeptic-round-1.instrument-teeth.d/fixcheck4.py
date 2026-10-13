# fixcheck4.py — L4 [M] with TODAY's face (U1 alone): the 011 translation rule generalised to 005/006/014 — every heading
# band = the wave54-final band shifted down by the UA margin (21 / 17 px) ±1, x0/x1 unchanged ±1, same band count. Read-only.
import os
from lib import *
dk = lambda a: a.sum(axis=2) < 300
def bands(a, y0=96, y1=400):
    m = dk(a); out, cur, blank = [], None, 0
    for y in range(y0, min(y1, a.shape[0])):
        xs = np.nonzero(m[y])[0]
        if len(xs):
            cur = [y, y, xs.min(), xs.max()] if cur is None else [cur[0], y, min(cur[2], xs.min()), max(cur[3], xs.max())]; blank = 0
        elif cur is not None:
            blank += 1
            if blank > 2:
                out.append(cur); cur = None; blank = 0
    if cur is not None:
        out.append(cur)
    return out
MARGIN = {'005': 21, '006': 21, '014': 17}
for t, mg in MARGIN.items():
    test = f'text-decoration-inset-{t}'
    for p in ('ios', 'android'):
        base = bands(A(cap('wave54-final', 'css-text-decor', test, p)))
        nh = len(bands(A(Image.open(f'{P55}/ua-heading-margin-replay/{test}-{p}-U1.png'))))
        for name, a in [('fixed U1', A(Image.open(f'{P55}/ua-heading-margin-replay/{test}-{p}-U1.png')))] + \
                       [(f, A(Image.open(f'{SCR}/fakes/L4-U1-{f}/{p}-screenshots__{cap_name("css-text-decor", test)}'))) for f in ('heading-2y', 'heading+12x', 'line2-removed', 'line1-truncated')]:
            b = bands(a); n = min(len(base), 2)
            ok = len(b) >= n and all(abs(x[0] - (y[0] + mg)) <= 1 and abs(x[1] - (y[1] + mg)) <= 1 and abs(x[2] - y[2]) <= 1 and abs(x[3] - y[3]) <= 1
                                     for x, y in zip(b[:n], base[:n]))
            print(f'  {t} {p:8} {name:16} bands {[tuple(int(v) for v in x) for x in b[:2]]} vs base+{mg} {[(int(y[0]) + mg, int(y[1]) + mg, int(y[2]), int(y[3])) for y in base[:2]]} → {"OK" if ok else "WRONG"}')
