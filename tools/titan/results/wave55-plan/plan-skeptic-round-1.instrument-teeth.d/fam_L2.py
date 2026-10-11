# L2-cbb-android: the six U1 keys of compose-wpt-content-box-cb.geometry.py (fixed picture = wave54-probe android, the
# old U1 on device) and the U2 key backdrop-filter-nested-border-radius-clip-3 android of BOTH probes (fixed picture =
# cbb-android-replay/android-clip-3.png). Every fake is derived from the fixed picture; the expected verdict of a WRONG
# picture is FAIL on its gating key.
import sys
from lib import *

green = lambda a: (a[..., 0] < 8) & (np.abs(a[..., 1] - 128) < 8) & (a[..., 2] < 8)
U1 = [('css-flexbox', 'abspos/abspos-autopos-htb-ltr'), ('css-flexbox', 'abspos/abspos-autopos-vlr-ltr'),
      ('css-flexbox', 'abspos/abspos-autopos-vrl-ltr'), ('filter-effects', 'backdrop-filter-nested-border-radius-clip'),
      ('filter-effects', 'backdrop-filter-nested-border-radius-clip-2'), ('filter-effects', 'backdrop-filter-nested-border-radius-clip-4')]
C3 = ('filter-effects', 'backdrop-filter-nested-border-radius-clip-3')
S1, S2 = 'compose-wpt-content-box-cb.geometry.py', 'cbb-android.geometry.py'


def gbbox(a):
    ys, xs = np.nonzero(green(a))
    return xs.min(), ys.min(), xs.max(), ys.max()


def hollow(a, fill=(255, 255, 255)):
    """Target ink removed: the green box keeps only its 1-px outline (same bbox, 0 fail ink)."""
    x0, y0, x1, y1 = gbbox(a); g = green(a); o = a.copy()
    inner = np.zeros_like(g); inner[y0 + 1:y1, x0 + 1:x1] = True
    o[g & inner] = fill
    return o


def corners2(a):
    """Target ink removed: only the two green pixels at the bbox's top-left and bottom-right corners remain."""
    x0, y0, x1, y1 = gbbox(a); g = green(a); o = a.copy()
    o[g] = (255, 255, 255); o[y0, x0] = (0, 128, 0); o[y1, x1] = (0, 128, 0)
    return o


def stripe(a, wpx=30):
    """Segment removed: a white vertical stripe through the box centre (same bbox)."""
    x0, y0, x1, y1 = gbbox(a); g = green(a); o = a.copy(); cx = (x0 + x1) // 2
    m = np.zeros_like(g); m[:, cx - wpx // 2:cx + wpx // 2] = True
    o[g & m] = (255, 255, 255)
    return o


def gshift(a, dx, dy):
    x0, y0, x1, y1 = gbbox(a)
    return shift(a, dx, dy, (x0, y0, x1 + 1, y1 + 1))


def shorten(a, n=3):
    x0, y0, x1, y1 = gbbox(a); g = green(a); o = a.copy()
    m = np.zeros_like(g); m[:, x1 - n + 1:] = True
    o[g & m] = (255, 255, 255)
    return o


results = []
FAKES_U1 = {'fixed': lambda a: a, 'hollow': hollow, 'corners2': corners2, 'stripe30': stripe,
            'shift+2x': lambda a: gshift(a, 2, 0), 'shift-3y': lambda a: gshift(a, 0, -3), 'short3': shorten}
for name, f in FAKES_U1.items():
    repl = {(sec, 'android', t): I(f(A(cap('wave54-probe', sec, t, 'android')))) for sec, t in U1}
    rid = make_run(f'L2-U1-{name}', repl)
    rc, rows = gate(rid, 'L2', 'css-flexbox,filter-effects', f'L2-U1-{name}')
    for sec, t in U1:
        k = f'{t.split("/")[-1] if sec == "filter-effects" else t} android'
        r = rows.get((S1, k))
        results.append(('L2', S1, k, name, r['verdict'] if r else 'NO ROW', r['line'][-150:] if r else ''))

# clip-3 (U2): the fixed picture is the replay; two probes read the same key.
base3 = A(Image.open(f'{P55}/cbb-android-replay/android-clip-3.png'))


def c3_gut(a):
    """Target ink removed: the green interior goes white except row y=80, column x=120 (the two scanlines the
    supplementary probe reads), the bbox outline and the bottom-right corner square x216-235 / y116-135."""
    g = green(a); o = a.copy(); x0, y0, x1, y1 = gbbox(a)
    keep = np.zeros_like(g)
    keep[80, :] = True; keep[:, 120] = True; keep[y0, :] = keep[y1, :] = True; keep[:, x0] = keep[:, x1] = True
    keep[116:136, 216:236] = True
    o[g & ~keep] = (255, 255, 255)
    return o


def c3_square_corners(a):
    """Rearrangement: #inner's top-left, top-right and bottom-left radius-25 arcs filled square (green where the arc left
    grey) — the radius lost on three corners; the bottom-right corner square the probe counts is untouched."""
    o = a.copy(); x0, y0, x1, y1 = gbbox(a)
    for (cx0, cy0) in ((x0, y0), (x1 - 24, y0), (x0, y1 - 24)):
        o[cy0:cy0 + 25, cx0:cx0 + 25] = (0, 128, 0)
    return o


FAKES_C3 = {'fixed': lambda a: a, 'gut-cross': c3_gut, 'square-3-corners': c3_square_corners,
            'shift+2x': lambda a: gshift(a, 2, 0), 'stripe30': stripe}
for name, f in FAKES_C3.items():
    rid = make_run(f'L2-U2-{name}', {(C3[0], 'android', C3[1]): I(f(base3))})
    rc, rows = gate(rid, 'L2', 'filter-effects', f'L2-U2-{name}')
    for s in (S1, S2):
        k = 'backdrop-filter-nested-border-radius-clip-3 android'
        r = rows.get((s, k))
        results.append(('L2', s, k, name, r['verdict'] if r else 'NO ROW', r['line'][-150:] if r else ''))

for r in results:
    print(' | '.join(r))
