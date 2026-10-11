# L5-counter-suffix-rows: the six gating keys of counter-suffix-rows.geometry.py ([H] ios, [C] ios/android, [T] ios,
# [I] ios/android). Fixed pictures = counter-suffix-rows-replay/ok-all/ (every unit modelled), placed as the run's captures.
from lib import *

S = 'counter-suffix-rows.geometry.py'
OK = f'{P55}/counter-suffix-rows-replay/ok-all'
dark = lambda a: a.sum(axis=2) < 300
ROW = {k: (16 + 24 * (k - 1), 16 + 24 * k) for k in range(1, 13)}


def fx(test, plat):
    return A(Image.open(f'{OK}/{test.replace("/", "__")}.{plat}.png'))


def col_blobs(a, x0, x1, y0, y1):
    m = dark(a[y0:y1, x0:x1]); cols = np.nonzero(m.any(axis=0))[0] + x0; out = []
    for x in cols:
        if out and x == out[-1][1] + 1:
            out[-1][1] = x
        else:
            out.append([x, x])
    return out


def erase(a, x0, x1, y0, y1):
    o = a.copy(); m = a.min(axis=2) < 250; reg = np.zeros_like(m); reg[y0:y1, x0:x1 + 1] = True; o[m & reg] = (255, 255, 255); return o


# [I] css3-counter-styles-005: remove the "、" suffix ink (the blob(s) between the marker's ideograph and the item text).
def i_comma_removed(a):
    o = a.copy()
    for y0, y1 in ((95, 121), (121, 152)):
        bl = col_blobs(o, 200, 330, y0, y1)
        item = next((b for b in bl if b[0] >= 255), None)
        marker = [b for b in bl if b[0] < 255]
        if len(marker) >= 2:
            o = erase(o, marker[-1][0] - 1, marker[-1][1] + 1, y0, y1)     # the last marker blob before the item = the 、
    return o


def i_marker_glyph_block(a):
    """Rearrangement: the marker ideograph replaced by a solid block of its own bbox (left edge kept)."""
    o = a.copy()
    for y0, y1 in ((95, 121), (121, 152)):
        bl = col_blobs(o, 200, 330, y0, y1)
        if bl:
            xa, xb = bl[0]; ys = np.nonzero(dark(o[y0:y1, xa:xb + 1]).any(axis=1))[0] + y0
            o[ys.min():ys.max() + 1, xa:xb + 1] = (0, 0, 0)
    return o


# [T] row 9 digit: shift the '1' 3 px down inside its row; replace it with a 7-px wide bar of its height.
def t_shift_down(a):
    y0, y1 = ROW[9]; return shift(a, 0, 3, (136, y0, 151, y1 - 3))


def t_bar(a):
    y0, y1 = ROW[9]; bl = col_blobs(a, 136, 151, y0, y1)
    o = erase(a, 136, 150, y0, y1)
    ys = np.nonzero(dark(a[y0:y1, 136:151]).any(axis=1))[0] + y0
    o[ys.min():ys.max() + 1, bl[0][0]:bl[0][0] + 7] = (0, 0, 0)
    return o


# [C] rows 5-6: the ideograph removed except a 1-px speck at its left edge (comma + item text untouched).
def c_ideograph_gutted(a):
    o = a.copy()
    for k in (5, 6):
        y0, y1 = ROW[k]; bl = col_blobs(o, 20, 60, y0, y1)
        if len(bl) >= 2:
            first, comma = bl[0], bl[-1]
            o = erase(o, first[0] - 1, comma[0] - 2, y0, y1)
            o[(y0 + y1) // 2, first[0]] = (0, 0, 0)
    return o


# [H] rows 3-4: the Hebrew letter replaced by a solid block of its bbox (period kept).
def h_letter_block(a):
    o = a.copy()
    for k in (3, 4):
        y0, y1 = ROW[k]; bl = col_blobs(o, 30, 64, y0, y1); letters = [b for b in bl if b[1] - b[0] >= 4]
        if letters:
            xa, xb = letters[0]; ys = np.nonzero(dark(o[y0:y1, xa:xb + 1]).any(axis=1))[0] + y0
            o[ys.min():ys.max() + 1, xa:xb + 1] = (0, 0, 0)
    return o


def h_item_text_gone(a):
    """The item text 'foo' / 'bar' of rows 3-4 erased (x64+); the marker untouched."""
    o = a.copy()
    for k in (3, 4):
        y0, y1 = ROW[k]; o = erase(o, 64, 200, y0, y1)
    return o


cases = {
    'fixed': {}, 'I-comma-removed': {'005': i_comma_removed}, 'I-marker-block': {'005': i_marker_glyph_block},
    'T-1-down3': {'cs': t_shift_down}, 'T-1-bar7': {'cs': t_bar}, 'C-ideograph-gutted': {'cs': c_ideograph_gutted},
    'H-letter-block': {'cs': h_letter_block}, 'H-item-text-gone': {'cs': h_item_text_gone},
}
results = []
for name, fs in cases.items():
    repl = {}
    for plat in ('web', 'ios', 'android'):
        cs, c5 = fx('counter-suffix', plat), fx('cjk-decimal/css3-counter-styles-005', plat)
        if plat != 'web':
            cs = fs.get('cs', lambda a: a)(cs); c5 = fs.get('005', lambda a: a)(c5)
        repl[('css-counter-styles', plat, 'counter-suffix')] = I(cs)
        repl[('css-counter-styles', plat, 'cjk-decimal/css3-counter-styles-005')] = I(c5)
    rid = make_run(f'L5-{name}', repl)
    rc, rows = gate(rid, 'L5', 'css-counter-styles', f'L5-{name}')
    for (s, k), r in sorted(rows.items()):
        if s == S and r['class'] == 'gating':
            results.append(('L5', s, k, name, r['verdict'], r['line'][-140:]))
for r in results:
    print(' | '.join(r))
