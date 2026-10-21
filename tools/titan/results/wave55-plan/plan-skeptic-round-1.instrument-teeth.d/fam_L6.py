# L6-android-paint-order: the six [X] U2 keys (fixed picture = android-paint-order-replay/<flat>-android-U2.png, the
# brief's modelled U2 picture built from the wave54-final android capture) and the [PO] U1 key (fixed picture =
# android-paint-order-replay/backdrop-filter-clip-rect-android-U1.png).
from lib import *

S = 'android-paint-order.geometry.py'
X = [('css-flexbox', 'abspos/flex-abspos-staticpos-align-self-safe-001', 'yellow'),
     ('css-flexbox', 'abspos/flex-abspos-staticpos-align-self-safe-002', 'yellow'),
     ('css-grid', 'abspos/grid-abspos-staticpos-align-self-001', 'teal'),
     ('css-grid', 'abspos/grid-abspos-staticpos-justify-self-001', 'teal'),
     ('css-grid', 'abspos/grid-abspos-staticpos-align-self-vertWM-001', 'teal'),
     ('css-grid', 'abspos/grid-abspos-staticpos-align-self-rtl-001', 'teal')]
teal = lambda a: (a[..., 0] <= 40) & (a[..., 1] >= 100) & (a[..., 1] <= 160) & (a[..., 2] >= 100) & (a[..., 2] <= 160)
yellow = lambda a: (a[..., 0] >= 200) & (a[..., 1] >= 200) & (a[..., 2] <= 80)
FILL = {'teal': teal, 'yellow': yellow}
dark = lambda a: a.sum(axis=2) < 150


def fixed(sec, t):
    return A(Image.open(f'{P55}/android-paint-order-replay/{t.replace("/", "__")}-android-U2.png'))


def border_erased(a, fill):
    """The parent's border dropped entirely (every dark px → white): the 'fix' that loses the band."""
    o = a.copy(); o[dark(a)] = (255, 255, 255); return o


def child_shift(a, fill, dx, dy):
    """The child (its fill bbox) translated by (dx, dy); what it uncovers is white."""
    ys, xs = np.nonzero(FILL[fill](a))
    return shift(a, dx, dy, (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))


def child_shrunk(a, fill, frac=0.08):
    """The child's fill shortened by 8 % of its rows from the bottom (a segment shortened), under the 0.90 bar."""
    m = FILL[fill](a); ys, xs = np.nonzero(m); y0, y1 = ys.min(), ys.max()
    cut = y1 - int((y1 - y0 + 1) * frac)
    o = a.copy(); mm = m.copy(); mm[:cut + 1] = False; o[mm] = (255, 255, 255); return o


def child_scrambled(a, fill):
    """Count-preserving rearrangement: the child's fill pixels moved to the top-left of the canvas (same count)."""
    m = FILL[fill](a); n = int(m.sum()); o = a.copy(); col = a[m][0]
    o[m] = (255, 255, 255)
    side = int(np.ceil(np.sqrt(n))); flat = o.reshape(-1, 3)
    idx = [(y * a.shape[1] + x) for y in range(side) for x in range(side)][:n]
    flat[idx] = col
    return flat.reshape(a.shape)


results = []
FAKES = {'fixed': lambda a, f: a, 'border-erased': border_erased, 'child+3x': lambda a, f: child_shift(a, f, 3, 0),
         'child-3y': lambda a, f: child_shift(a, f, 0, -3), 'child-shrunk8%': child_shrunk, 'child-scrambled': child_scrambled}
for name, fk in FAKES.items():
    repl = {(sec, 'android', t): I(fk(fixed(sec, t), f)) for sec, t, f in X}
    rid = make_run(f'L6-X-{name}', repl)
    rc, rows = gate(rid, 'L6', 'css-flexbox,css-grid', f'L6-X-{name}')
    for sec, t, f in X:
        k = f'[X] {t.split("/")[-1]} android'
        r = rows.get((S, k))
        results.append(('L6', S, k, name, r['verdict'] if r else 'NO ROW', r['line'][-130:] if r else ''))

po0 = A(Image.open(f'{P55}/android-paint-order-replay/backdrop-filter-clip-rect-android-U1.png'))
red = lambda a: (a[..., 0] >= 200) & (a[..., 1] <= 60) & (a[..., 2] <= 60)
blue = lambda a: (a[..., 2] >= 200) & (a[..., 0] <= 60) & (a[..., 1] <= 60)


def po_navbar_inner_dropped(a):
    """The navbar's blue band kept only where the probe reads it (overlap rows x120-159 / x270-309 and under the menus):
    the band elsewhere (its left / right / vertical sides) erased."""
    o = a.copy(); b = blue(a); keep = np.zeros_like(b)
    for y in (166, 167, 218, 219):
        keep[y, 120:160] = keep[y, 270:310] = keep[y, 163:267] = True
    o[b & ~keep] = (255, 255, 255); return o


def po_menus_shift(a, dy):
    ys, xs = np.nonzero(red(a)); return shift(a, 0, dy, (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))


PO = {'fixed': lambda a: a, 'navbar-sides-erased': po_navbar_inner_dropped, 'menus+1y': lambda a: po_menus_shift(a, 1),
      'menus+2y': lambda a: po_menus_shift(a, 2), 'whole+1x': lambda a: shift(a, 1, 0)}
for name, fk in PO.items():
    rid = make_run(f'L6-PO-{name}', {('filter-effects', 'android', 'backdrop-filter-clip-rect'): I(fk(po0))})
    rc, rows = gate(rid, 'L6', 'filter-effects', f'L6-PO-{name}')
    k = '[PO] backdrop-filter-clip-rect android'
    r = rows.get((S, k))
    results.append(('L6', S, k, name, r['verdict'] if r else 'NO ROW', r['line'][-160:] if r else ''))
for r in results:
    print(' | '.join(r))
