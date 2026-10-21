# L4-ua-heading-margin: the twelve gating keys of ua-heading-margin.geometry.py ([M] x8, [F] x4). Fixed pictures = the
# brief's replay arms ua-heading-margin-replay/<test>-<plat>-C.png (U1 + U2: the 2em face at the margined position) and
# -U1.png (U1 alone: today's face, margined). Fakes move / cut the heading bands of the fixed picture.
from lib import *

S = 'ua-heading-margin.geometry.py'
RP = f'{P55}/ua-heading-margin-replay'
dark = lambda a: a.sum(axis=2) < 300
TESTS = ['text-decoration-inset-005', 'text-decoration-inset-006', 'text-decoration-inset-014', 'text-decoration-inset-011']


def bands(a, y0=96, y1=400, gap=2):
    m = dark(a); out, cur, blank = [], None, 0
    for y in range(y0, min(y1, a.shape[0])):
        xs = np.nonzero(m[y])[0]
        if len(xs):
            cur = [y, y, xs.min(), xs.max()] if cur is None else [cur[0], y, min(cur[2], xs.min()), max(cur[3], xs.max())]; blank = 0
        elif cur is not None:
            blank += 1
            if blank > gap:
                out.append(cur); cur = None; blank = 0
    if cur is not None:
        out.append(cur)
    return out


def heading_region(a, test):
    """Rows of the heading: band 1 (and band 2 for 005/006/014) from y96 (y112 for 011)."""
    b = bands(a, 112 if test.endswith('011') else 96)
    n = 1 if test.endswith('011') else min(2, len(b))
    return b[0][0], b[n - 1][1] + 1, b


def h_shift(a, test, dx, dy):
    y0, y1, _ = heading_region(a, test); return shift(a, dx, dy, (0, y0, a.shape[1], y1))


def line2_removed(a, test):
    _, _, b = heading_region(a, test); o = a.copy()
    if len(b) < 2:
        return a
    t, bt = b[1][0], b[1][1]
    m = dark(a); band = np.zeros_like(m); band[t:bt + 1] = True; o[m & band] = (255, 255, 255); return o


def line1_truncated(a, test):
    """Segment shortened: heading line 1 keeps only its left 40 % (its height and top unchanged)."""
    _, _, b = heading_region(a, test); o = a.copy(); t, bt, x0, x1 = b[0]
    cut = x0 + int((x1 - x0) * 0.4); m = dark(a); band = np.zeros_like(m); band[t:bt + 1, cut:] = True
    o[m & band] = (255, 255, 255); return o


def b011_gut(a, test):
    """011: every band keeps only its outline extremes (a 1-px dark frame at the band's bbox), i.e. its glyphs removed."""
    b = bands(a, 112); o = a.copy(); m = dark(a)
    for t, bt, x0, x1 in b:
        reg = np.zeros_like(m); reg[t:bt + 1, x0:x1 + 1] = True; o[m & reg] = (255, 255, 255)
        o[t, x0] = o[bt, x1] = (0, 0, 0)
    return o


def fixed(test, plat, arm):
    return A(Image.open(f'{RP}/{test}-{plat}-{arm}.png'))


results = []
for arm in ('C', 'U1'):
    FAKES = {'fixed': lambda a, t: a, 'heading+3y': lambda a, t: h_shift(a, t, 0, 3), 'heading-2y': lambda a, t: h_shift(a, t, 0, -2),
             'heading+12x': lambda a, t: h_shift(a, t, 12, 0), 'line2-removed': line2_removed, 'line1-truncated': line1_truncated}
    for name, fk in FAKES.items():
        repl = {}
        for t in TESTS:
            for p in ('ios', 'android'):
                f = fk
                if t.endswith('011') and name in ('line2-removed', 'line1-truncated'):
                    f = b011_gut if name == 'line1-truncated' else (lambda a, tt: a)
                repl[('css-text-decor', p, t)] = I(f(fixed(t, p, arm), t))
        rid = make_run(f'L4-{arm}-{name}', repl)
        rc, rows = gate(rid, 'L4', 'css-text-decor', f'L4-{arm}-{name}')
        for (s, k), r in sorted(rows.items()):
            if s == S and r['class'] == 'gating':
                results.append(('L4', s, k, f'{arm}:{name}', r['verdict'], r['line'][-120:]))
for r in results:
    print(' | '.join(r))
