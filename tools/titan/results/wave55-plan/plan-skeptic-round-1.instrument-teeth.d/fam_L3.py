# L3-positioned-parent-slot: the seven gating keys of positioned-parent-slot.geometry.py. Fixed pictures = the brief's
# replays positioned-parent-slot-replay/<doc>-android-U1U2.png (falling back to -U1 / -U2 as the probe itself does).
from lib import *

S = 'positioned-parent-slot.geometry.py'
RP = f'{P55}/positioned-parent-slot-replay'
ink = lambda a: a.min(axis=2) < 200
dark = lambda a: a.min(axis=2) < 120
blue = lambda a: (a[..., 2] > 150) & (a[..., 0] < 120) & (a[..., 1] < 120)
green = lambda a: (a[..., 0] < 60) & (a[..., 1] > 100) & (a[..., 2] < 60)
magenta = lambda a: (a[..., 0] > 200) & (a[..., 1] < 80) & (a[..., 2] > 200)
ROW_X1 = {6: 153, 7: 243, 9: 153}


def fx(doc):
    for arm in ('U1U2', 'U1', 'U2'):
        p = f'{RP}/{doc}-android-{arm}.png'
        if os.path.exists(p):
            return A(Image.open(p))


def row_band(k):
    y0 = 16 + 20 * (k - 1); return y0, y0 + 20


def dir_gut(a, k):
    """Target ink removed: row k keeps only its 2-px '.' at x15-19 and ONE 2x2 ink block at the row's right edge."""
    y0, y1 = row_band(k); o = a.copy(); m = ink(a); band = np.zeros_like(m); band[y0:y1] = True
    ys, xs = np.nonzero(m & band); xr = xs.max(); yr = ys[xs.argmax()]
    keep = np.zeros_like(m); keep[y0 + 12:y0 + 17, 15:20] = True
    o[m & band & ~keep] = (255, 255, 255)
    o[yr:yr + 2, xr - 1:xr + 1] = (0, 0, 0)
    return o


def dir_words_swapped(a, k):
    """Count-preserving rearrangement: row k's ink right of x24 mirrored left-right inside its own extent (glyph order
    reversed, every glyph flipped) — the '.' at x15-19 and both extents unchanged."""
    y0, y1 = row_band(k); o = a.copy(); m = ink(a); band = np.zeros_like(m); band[y0:y1] = True
    ys, xs = np.nonzero(m & band); xs = xs[xs > 24]
    xa, xb = xs.min(), xs.max()
    o[y0:y1, xa:xb + 1] = a[y0:y1, xa:xb + 1][:, ::-1]
    return o


def dir_shift(a, k, dx):
    y0, y1 = row_band(k); return shift(a, dx, 0, (21, y0, a.shape[1], y1))     # the row's text right of the dot


def bidi_shift(a, dx):
    o = a.copy()
    for y0 in (86, 106, 186, 226):
        o = shift(o, dx, 0, (0, y0, o.shape[1], y0 + 20))
    return o


def bidi_gut(a):
    """Target ink removed: on each RTL row only the leftmost and rightmost blue columns remain."""
    o = a.copy(); b = blue(a)
    for y0 in (86, 106, 186, 226):
        band = np.zeros_like(b); band[y0:y0 + 20] = True
        ys, xs = np.nonzero(b & band)
        if not len(xs):
            continue
        keep = band & (np.arange(b.shape[1])[None, :] == xs.min()) | band & (np.arange(b.shape[1])[None, :] == xs.max())
        o[b & band & ~keep] = (255, 255, 255)
    return o


CELLS = ((190, 78), (360, 78), (190, 168), (360, 168))


def stack_grown(a):
    """Each 10x10 square grown to 30x30 (down-right): every cell still 100 % green."""
    o = a.copy()
    for x, y in CELLS:
        o[y:y + 30, x:x + 30] = (0, 128, 0)
    return o


def stack_slab(a):
    """One green slab covering the four cells (x190-369, y78-177): the squares' shape and separation gone."""
    o = a.copy(); o[78:178, 190:370] = (0, 128, 0); return o


def stack_dup(a):
    """The correct four squares PLUS four flow-stacked duplicates 10 px lower (the wrong 'flow space' picture on top)."""
    o = a.copy()
    for x, y in CELLS:
        o[y + 10:y + 20, x:x + 10] = (0, 128, 0)
    return o


def mag(a):
    ys, xs = np.nonzero(magenta(a)); return xs.min(), ys.min(), xs.max(), ys.max()


def mag_hollow(a):
    x0, y0, x1, y1 = mag(a); o = a.copy(); m = magenta(a); inner = np.zeros_like(m); inner[y0 + 1:y1, x0 + 1:x1] = True
    o[m & inner] = (255, 255, 255); return o


def mag_shift(a, dx, dy):
    x0, y0, x1, y1 = mag(a); return shift(a, dx, dy, (x0, y0, x1 + 1, y1 + 1))


def mag_wide(a, n):
    x0, y0, x1, y1 = mag(a); o = a.copy(); o[y0:y1 + 1, x1 + 1:x1 + 1 + n] = (255, 0, 255); return o


cases = []
d = fx('dir-style-02a')
for k in (6, 7, 9):
    for name, f in (('fixed', lambda a: a), ('gut', lambda a: dir_gut(a, k)), ('mirror-words', lambda a: dir_words_swapped(a, k)),
                    ('text+2x', lambda a: dir_shift(a, k, 2)), ('text-3x', lambda a: dir_shift(a, k, -3)), ('text+3x', lambda a: dir_shift(a, k, 3))):
        cases.append((f'dir-row{k}-{name}', 'selectors', 'dir-style-02a', I(f(d)), f'[S] dir-style-02a android row{k}'))
b = fx('bidi-tab-001')
for name, f in (('fixed', lambda a: a), ('gut', bidi_gut), ('rtl+2x', lambda a: bidi_shift(a, 2)), ('rtl-3x', lambda a: bidi_shift(a, -3))):
    cases.append((f'bidi-{name}', 'css-text', 'bidi/bidi-tab-001', I(f(b)), '[S] bidi-tab-001 android rtl-rows'))
s = fx('anchor-position-multicol-nested-001')
for name, f in (('fixed', lambda a: a), ('grown30', stack_grown), ('slab', stack_slab), ('dup-stacked', stack_dup),
                ('shift+1x', lambda a: shift(a, 1, 0))):
    cases.append((f'stack-{name}', 'css-anchor-position', 'anchor-position-multicol-nested-001', I(f(s)),
                  '[W] anchor-position-multicol-nested-001 android stack'))
m1 = fx('dynamic-offset-rtl-001')
for name, f in (('fixed', lambda a: a), ('hollow', mag_hollow), ('shift+2x', lambda a: mag_shift(a, 2, 0))):
    cases.append((f'dyn1-{name}', 'css-writing-modes', 'dynamic-offset-rtl-001', I(f(m1)), '[S] dynamic-offset-rtl-001 android'))
m2 = fx('dynamic-offset-rtl-002')
for name, f in (('fixed', lambda a: a), ('shift+3y', lambda a: mag_shift(a, 0, 3)), ('shift-3y', lambda a: mag_shift(a, 0, -3)),
                ('wider+5', lambda a: mag_wide(a, 5)), ('hollow', mag_hollow)):
    cases.append((f'dyn2-{name}', 'css-writing-modes', 'dynamic-offset-rtl-002', I(f(m2)), '[W] dynamic-offset-rtl-002 android'))

results = []
for fid, sec, test, im, key in cases:
    rid = make_run(f'L3-{fid}', {(sec, 'android', test): im})
    rc, rows = gate(rid, 'L3', sec, f'L3-{fid}')
    r = rows.get((S, key))
    results.append(('L3', S, key, fid, r['verdict'] if r else 'NO ROW', r['line'][-140:] if r else ''))
for r in results:
    print(' | '.join(r))
