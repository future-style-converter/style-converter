# L1-hyphen-ios-linebox: the three gating keys `hyphen-ios-linebox 00{1,3,4} ios` (LINEBOX OK). Fixed picture =
# hyphen-ios-linebox-replay/<t>-ios-Rm.png (the unit's model, built from the wave54-final iOS capture); fakes move or cut
# the ink of that picture.
from lib import *

S = 'hyphen-ios-linebox.geometry.py'
DIV0 = {'001': 100, '003': 80, '004': 80}
core = lambda a: a.max(axis=2) < 110
nonwhite = lambda a: a.min(axis=2) < 250


def line_rows(a, y0):
    """[(top, bot, x0, x1)] of the div's ink lines (x < 140), gaps > 2 rows split (the probe's segmentation)."""
    m = core(a[:, :140]); out, cur, blank = [], None, 0
    for y in range(y0, a.shape[0]):
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


def groups(ls):
    gs, cur = [], []
    for ln in ls:
        if cur and ln[0] - cur[-1][1] >= 18:
            gs.append(cur); cur = []
        cur.append(ln)
    if cur:
        gs.append(cur)
    return gs


def hyph_removed(a, t):
    """Target ink removed: the hyphenate-character glyph(s) at the end of every non-final line of a group erased
    (003: the bullet, 1 cell; 004: '/-/', 3 cells; 001 has no visible hyphen: a no-op control)."""
    if t == '001':
        return a
    cells = 1 if t == '003' else 3; cw = 10; o = a.copy()
    for g in groups(line_rows(a, DIV0[t])):
        for top, bot, x0, x1 in g[:-1]:
            reg = np.zeros(a.shape[:2], bool); reg[top - 3:bot + 4, x1 - cells * cw + 2:x1 + 2] = True
            o[nonwhite(a) & reg] = (255, 255, 255)
    return o


def lines_swapped(a, t):
    """Count-preserving rearrangement: lines 2 and 3 of group 1 trade places (each line's row band moved whole)."""
    g = groups(line_rows(a, DIV0[t]))[0]; o = a.copy()
    (t2, b2, _, _), (t3, b3, _, _) = g[1], g[2]
    h = max(b2 - t2, b3 - t3) + 1; pad = 3
    band2 = a[t2 - pad:t2 + h + pad, :140].copy(); band3 = a[t3 - pad:t3 + h + pad, :140].copy()
    o[t2 - pad:t2 + h + pad, :140] = band3; o[t3 - pad:t3 + h + pad, :140] = band2
    return o


def right_half_cut(a, t):
    """Segment shortened: every line keeps only its left half (the right half of its ink erased)."""
    o = a.copy()
    for top, bot, x0, x1 in line_rows(a, DIV0[t]):
        reg = np.zeros(a.shape[:2], bool); reg[top - 3:bot + 4, (x0 + x1) // 2:140] = True; o[nonwhite(a) & reg] = (255, 255, 255)
    return o


def text_x(a, t, dx):
    return shift(a, dx, 0, (0, DIV0[t], 140, a.shape[0]))


def one_line_x(a, t, dx):
    g = groups(line_rows(a, DIV0[t]))[1]; top, bot, _, _ = g[0]
    return shift(a, dx, 0, (0, top - 3, 140, bot + 4))


FAKES = {'fixed': lambda a, t: a, 'hyph-removed': hyph_removed, 'lines-2-3-swapped': lines_swapped,
         'right-half-cut': right_half_cut, 'text+3x': lambda a, t: text_x(a, t, 3), 'text-2x': lambda a, t: text_x(a, t, -2),
         'g2l1+3x': lambda a, t: one_line_x(a, t, 3)}
results = []
for name, f in FAKES.items():
    repl = {('css-text', 'ios', f'hyphens/hyphenate-character-{t}'): I(f(A(Image.open(f'{P55}/hyphen-ios-linebox-replay/{t}-ios-Rm.png')), t))
            for t in DIV0}
    rid = make_run(f'L1-{name}', repl)
    rc, rows = gate(rid, 'L1', 'css-text', f'L1-{name}')
    for t in DIV0:
        k = f'hyphen-ios-linebox {t} ios'; r = rows.get((S, k))
        results.append(('L1', S, k, name, r['verdict'] if r else 'NO ROW', r['line'][-150:] if r else ''))
for r in results:
    print(' | '.join(r))
