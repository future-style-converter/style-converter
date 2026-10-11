# Round-2 instrument-teeth fakes: NEW wrong pictures (beyond probes-teeth.py's standing set) per gating key, built from each
# key's own fixed picture, read through the REAL geometry-gate.py on the generated expectations.json. Writes only under the
# scratch dir beside this file. A fake whose target key prints PASS is an ACCEPTED fake (a defect of the key).
import importlib.util, json, os, sys
sys.dont_write_bytecode = True
import numpy as np
from PIL import Image
R = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
P = f'{R}/tools/titan/results/wave55-plan'
SCR = os.path.dirname(os.path.abspath(__file__))
assert not SCR.startswith(R + '/')
spec = importlib.util.spec_from_file_location('pt', f'{P}/probes-teeth.py'); pt = importlib.util.module_from_spec(spec); spec.loader.exec_module(pt)
EXP = f'{P}/expectations.json'; PY = ['/opt/homebrew/bin/python3.12', '-B']
A, I, shift, bbox_of, png, cap = pt.A, pt.I, pt.shift, pt.bbox_of, pt.png, pt.cap
WHITE = np.array([255, 255, 255])

def comps(mask):
    """4-connected components of a boolean mask -> list of (ys, xs) arrays (small pictures: flood fill)."""
    seen = np.zeros_like(mask); out = []
    H, Wd = mask.shape
    for y0, x0 in zip(*np.nonzero(mask)):
        if seen[y0, x0]: continue
        st = [(y0, x0)]; seen[y0, x0] = True; ys, xs = [], []
        while st:
            y, x = st.pop(); ys.append(y); xs.append(x)
            for yy, xx in ((y+1, x), (y-1, x), (y, x+1), (y, x-1)):
                if 0 <= yy < H and 0 <= xx < Wd and mask[yy, xx] and not seen[yy, xx]:
                    seen[yy, xx] = True; st.append((yy, xx))
        out.append((np.array(ys), np.array(xs)))
    return out

CASES = []   # (lane, sections, fid, repl, {(script, key): 'FAIL'}, why)
def case(lane, secs, fid, repl, keys, why):
    CASES.append((lane, secs, fid, repl, {k: 'FAIL' for k in keys}, why))

# ── L1 ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
K1 = lambda t: ('hyphen-ios-linebox.geometry.py', f'hyphen-ios-linebox {t} ios')
L1F = {t: png(f'hyphen-ios-linebox-replay/{t}-ios-Rm.png') for t in ('001', '003', '004')}
def l1_one_hyph(a, t):              # target ink removed in ONE line only (group 1 line 1's hyphenate-character)
    cells = 1 if t == '003' else 3; o = a.copy(); top, bot, x0, x1 = pt._l1_groups(a, t)[0][0]
    reg = np.zeros(a.shape[:2], bool); reg[top - 3:bot + 4, x1 - cells * 10 + 2:x1 + 2] = True; o[pt._nonwhite(a) & reg] = 255; return o
def l1_last_glyph(a, t):            # segment shortened: group 1 line 2 loses its last ~8 px (one Menlo cell)
    o = a.copy(); top, bot, x0, x1 = pt._l1_groups(a, t)[0][1]
    reg = np.zeros(a.shape[:2], bool); reg[top - 3:bot + 4, x1 - 7:x1 + 2] = True; o[pt._nonwhite(a) & reg] = 255; return o
def l1_line_x1(a, t):               # one run (group 3 line 2) moved 1 px right
    top, bot, _, _ = pt._l1_groups(a, t)[2][1]; return shift(a, 1, 0, (0, top - 3, 140, bot + 4))
for name, f in (('one-hyph-removed', l1_one_hyph), ('g1l2-last-cell-cut', l1_last_glyph), ('g3l2+1x', l1_line_x1),
                ('all+1x', lambda a, t: shift(a, 1, 0, (0, pt.L1_DIV0[t], 140, a.shape[0])))):
    ts = ('003', '004') if name == 'one-hyph-removed' else ('001', '003', '004')
    case('L1', 'css-text', f'L1-{name}', {('css-text', 'ios', f'hyphens/hyphenate-character-{t}'): f(L1F[t], t) for t in L1F},
         [K1(t) for t in ts], 'run moved 1 px / one hyphen / one cell')

# ── L2 ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
S2 = 'compose-wpt-content-box-cb.geometry.py'
k2 = lambda sec, t: (S2, f'{t.split("/")[-1] if sec == "filter-effects" else t} android')
U1 = pt.L2_U1
def l2_redsliver(a, sec):           # the failure ink showing beside the right box: 2 x 100 px (red / backdrop pink)
    x0, y0, x1, y1 = bbox_of(pt._green(a)); o = a.copy()
    if sec == 'css-flexbox': o[y0:y0 + 100, x1 + 1:x1 + 3] = (255, 0, 0)
    else: o[y0:y0 + 100, x1 + 1:x1 + 3] = (255, 127, 255)
    return o
def l2_notch(a, sec):               # a 7 x 7 white hole in the middle of the "filled" box (49 px)
    x0, y0, x1, y1 = bbox_of(pt._green(a)); cx, cy = (x0 + x1) // 2, (y0 + y1) // 2; o = a.copy(); o[cy:cy + 7, cx:cx + 7] = 255; return o
def l2_corner_moved(a, sec):        # count-preserving: TL arc filled square, BR radius grown so the mass stays (radius on the wrong corners)
    g = pt._green(a); x0, y0, x1, y1 = bbox_of(g); o = a.copy()
    tl = np.zeros_like(g); tl[y0:y0 + 26, x0:x0 + 26] = True; add = int((tl & ~g).sum()); o[tl & ~g] = (0, 128, 0)
    yy, xx = np.mgrid[0:a.shape[0], 0:a.shape[1]]
    for r in range(26, 60):           # smallest BR radius whose extra cut equals the TL fill
        cxr, cyr = x1 - r + 0.5, y1 - r + 0.5
        cut = g & (xx > cxr) & (yy > cyr) & ((xx - cxr) ** 2 + (yy - cyr) ** 2 > r * r)
        if int(cut.sum()) >= add: break
    o[cut] = a[y1 + 3, x1 + 3] if y1 + 3 < a.shape[0] else 255     # the backdrop behind (blue band) / canvas
    return o
for name, f, which in (('fail-ink-sliver', l2_redsliver, U1), ('notch7', l2_notch, U1), ('radius-moved', l2_corner_moved, [u for u in U1 if u[0] == 'filter-effects'])):
    case('L2', 'css-flexbox,filter-effects', f'L2-{name}', {(sec, 'android', t): f(cap('wave54-probe', sec, t, 'android'), sec) for sec, t in which},
         [k2(sec, t) for sec, t in which], 'U1 keys')

# ── L3 ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
S3 = 'positioned-parent-slot.geometry.py'
D = pt.l3_fixed('dir-style-02a')
def l3_vflip(a, k):
    y0 = 16 + 20 * (k - 1); o = a.copy(); o[y0:y0 + 20, 21:] = a[y0:y0 + 20, 21:][::-1]; return o
for k in (6, 7, 9):
    y0 = 16 + 20 * (k - 1)
    for name, f in (('text+2y', lambda a: shift(a, 0, 2, (21, y0, a.shape[1], y0 + 18))), ('text-2y', lambda a: shift(a, 0, -2, (21, y0 + 2, a.shape[1], y0 + 20))),
                    ('text-vflip', lambda a: l3_vflip(a, k))):
        case('L3', 'selectors', f'L3-dir-row{k}-{name}', {('selectors', 'android', 'dir-style-02a'): f(D)}, [(S3, f'[S] dir-style-02a android row{k}')], 'dir row')
B = pt.l3_fixed('bidi-tab-001')
def l3_bidi_order(a):               # count-preserving: each RTL band's blue run mirrored inside its own extent ("0■" <-> "■0")
    o = a.copy(); b = pt._blue(a)
    for y0 in (86, 106, 186, 226):
        xs = np.nonzero(b[y0:y0 + 20])[1]
        if len(xs): o[y0:y0 + 20, xs.min():xs.max() + 1] = a[y0:y0 + 20, xs.min():xs.max() + 1][:, ::-1]
    return o
yellow = lambda a: (a[..., 0] > 200) & (a[..., 1] > 200) & (a[..., 2] < 80)
def l3_bidi_y(a, dy):               # each RTL line (its yellow box slice with the run on it) moved dy px inside the box
    o = a.copy()
    for y0 in (86, 106, 186, 226):
        o = shift(o, 0, dy, (16, y0, 136, y0 + 20 - dy), bg=(255, 255, 0))
    return o
for name, f in (('rtl-run-order-mirrored', l3_bidi_order), ('rtl-runs+2y', lambda a: l3_bidi_y(a, 2)), ('yellow-boxes-erased', lambda a: np.where(yellow(a)[..., None], 255, a))):
    case('L3', 'css-text', f'L3-bidi-{name}', {('css-text', 'android', 'bidi/bidi-tab-001'): f(B)}, [(S3, '[S] bidi-tab-001 android rtl-rows')], 'bidi')
ST = pt.l3_fixed('anchor-position-multicol-nested-001')
def l3_red(a):
    o = a.copy(); o[150:160, 270:280] = (255, 0, 0); return o
def l3_others_erased(a):
    o = a.copy(); keep = np.zeros(a.shape[:2], bool)
    for x, y in pt.L3_CELLS: keep[y - 2:y + 12, x - 2:x + 12] = True
    g = (a[..., 0] < 60) & (a[..., 1] > 100) & (a[..., 2] < 60); o[g & ~keep] = 255; return o
for name, f in (('red-shown', l3_red), ('other-squares-erased', l3_others_erased)):
    case('L3', 'css-anchor-position', f'L3-stack-{name}', {('css-anchor-position', 'android', 'anchor-position-multicol-nested-001'): f(ST)},
         [(S3, '[W] anchor-position-multicol-nested-001 android stack')], 'stack')
mag = lambda a: (a[..., 0] > 200) & (a[..., 1] < 80) & (a[..., 2] > 200)
def l3_parent(a, how):
    y = yellow(a); x0, y0, x1, y1 = bbox_of(y); m = mag(a); o = a.copy(); o[y] = 255
    if how == 'x+3': o[y0:y1 + 1, x0 + 3:x1 + 4] = (255, 255, 0)
    o[m] = a[m]; return o
for doc, kind in (('dynamic-offset-rtl-001', 'S'), ('dynamic-offset-rtl-002', 'W')):
    m = pt.l3_fixed(doc)
    for name in ('parent-erased', 'parent+3x'):
        case('L3', 'css-writing-modes', f'L3-{doc[-3:]}-{name}', {('css-writing-modes', 'android', doc): l3_parent(m, 'x+3' if name.endswith('3x') else 'erase')},
             [(S3, f'[{kind}] {doc} android')], 'dyn')

# ── L4 ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
S4 = 'ua-heading-margin.geometry.py'
L4T = ['text-decoration-inset-005', 'text-decoration-inset-006', 'text-decoration-inset-014']
def l4_clip(a, t, n=10):            # segment shortened: heading line 1 clipped by n px at its right end (a clip, not a wrap)
    _, _, b = pt.l4_region(a, t); top, bot, x0, x1 = b[0]; m = pt._dk(a); reg = np.zeros_like(m); reg[top:bot + 1, x1 - n + 1:] = True
    o = a.copy(); o[(a.min(axis=2) < 250) & reg] = 255; return o
def l4_mirror(a, t):                # count-preserving: line 1's glyphs mirrored inside its own extent
    _, _, b = pt.l4_region(a, t); top, bot, x0, x1 = b[0]; o = a.copy(); o[top - 2:bot + 3, x0:x1 + 1] = a[top - 2:bot + 3, x0:x1 + 1][:, ::-1]; return o
def l4_words_swapped(a, t):         # count-preserving: line 1's first and last thirds swapped (a word-order change, same extent)
    _, _, b = pt.l4_region(a, t); top, bot, x0, x1 = b[0]; w = (x1 - x0 + 1) // 3; o = a.copy()
    L, Rr = a[top - 2:bot + 3, x0:x0 + w].copy(), a[top - 2:bot + 3, x1 + 1 - w:x1 + 1].copy(); o[top - 2:bot + 3, x0:x0 + w] = Rr; o[top - 2:bot + 3, x1 + 1 - w:x1 + 1] = L; return o
MK = lambda t, p: (S4, f'[M] {t} {p}'); FK = lambda t, p: (S4, f'[F] {t} {p}')
for name, f in (('line1-clipped-10px', l4_clip), ('line1-mirrored', l4_mirror), ('line1-thirds-swapped', l4_words_swapped)):
    repl = {('css-text-decor', p, t): f(png(f'ua-heading-margin-replay/{t}-{p}-C.png'), t) for t in L4T for p in ('ios', 'android')}
    keys = [MK(t, p) for t in L4T for p in ('ios', 'android')] + [FK(t, 'ios') for t in L4T] + [FK('text-decoration-inset-014', 'android')]
    case('L4', 'css-text-decor', f'L4-C-{name}', repl, keys, 'face-present arm C')
    if name != 'line1-clipped-10px':
        repl = {('css-text-decor', p, t): f(png(f'ua-heading-margin-replay/{t}-{p}-U1.png'), t) for t in L4T for p in ('ios', 'android')}
        case('L4', 'css-text-decor', f'L4-U1-{name}', repl, [MK(t, p) for t in L4T for p in ('ios', 'android')], 'U1 alone arm')

# ── L5 ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
S5 = 'counter-suffix-rows.geometry.py'; OK = 'counter-suffix-rows-replay/ok-all'
ROW = pt.L5_ROW
def swap_regions(a, x0, x1, ra, rb):
    o = a.copy(); A_, B_ = a[ra[0]:ra[1], x0:x1].copy(), a[rb[0]:rb[1], x0:x1].copy(); o[ra[0]:ra[1], x0:x1] = B_; o[rb[0]:rb[1], x0:x1] = A_; return o
def l5_ideo_swap(a):                # rows 5 / 6 trade their counter ideographs (一 <-> 二); suffix and items kept
    b5 = pt.l5_blobs(a, 20, 60, *ROW[5]); b6 = pt.l5_blobs(a, 20, 60, *ROW[6])
    x0 = min(b5[0][0], b6[0][0]); x1 = max(b5[0][1], b6[0][1]) + 1; return swap_regions(a, x0, x1, ROW[5], ROW[6])
def l5_ideo_y(a, dy):               # rows 5-6's ideograph 3 px low inside its line
    o = a
    for k in (5, 6):
        b = pt.l5_blobs(o, 20, 60, *ROW[k]); o = shift(o, 0, dy, (b[0][0], ROW[k][0] + 3, b[0][1] + 1, ROW[k][1] - 3))
    return o
def l5_letter_swap(a):              # rows 3 / 4 trade their hebrew letters (א <-> ב); periods and items kept
    L3_ = [b for b in pt.l5_blobs(a, 30, 64, *ROW[3]) if b[1] - b[0] >= 4][0]; L4_ = [b for b in pt.l5_blobs(a, 30, 64, *ROW[4]) if b[1] - b[0] >= 4][0]
    return swap_regions(a, min(L3_[0], L4_[0]), max(L3_[1], L4_[1]) + 1, ROW[3], ROW[4])
def l5_period_y(a, dy):
    o = a
    for k in (3, 4):
        d = [b for b in pt.l5_blobs(o, 30, 64, *ROW[k]) if b[1] - b[0] <= 2][0]; o = shift(o, 0, dy, (d[0], ROW[k][0] + 2, d[1] + 1, ROW[k][1] - 4))
    return o
def l5_row9_text(a, how):           # row 9's item text (everything but the digit window x136-150) erased / moved 3 px
    y0, y1 = ROW[9]; o = a.copy()
    if how == 'erase':
        m = a.min(axis=2) < 250; reg = np.zeros_like(m); reg[y0:y1, :136] = True; o[m & reg] = 255; return o
    return shift(o, -3, 0, (60, y0, 136, y1))
def l5_i_copy(a):                   # 005: line 1's first-column ideograph replaced by line 2's (二 where 一 belongs); suffixes kept
    o = a.copy(); b1 = pt.l5_blobs(a, 200, 330, 95, 121); b2 = pt.l5_blobs(a, 200, 330, 121, 152)
    x0 = min(b1[0][0], b2[0][0]); x1 = max(b1[0][1], b2[0][1]) + 1; o[95:121, x0:x1] = 255
    src = a[121:152, x0:x1]; ys = np.nonzero((src.min(axis=2) < 250).any(axis=1))[0]
    ys1 = np.nonzero((a[95:121, x0:x1].min(axis=2) < 250).any(axis=1))[0]
    c1 = 95 + (ys1.min() + ys1.max()) // 2; h = ys.max() - ys.min() + 1; top = c1 - h // 2
    o[top:top + h, x0:x1] = src[ys.min():ys.max() + 1]; return o
def l5_i_y(a, dy):                  # 005: line 1's marker (ideograph + 、) 3 px low inside its line
    bl = [b for b in pt.l5_blobs(a, 200, 330, 95, 121) if b[0] < 255]; return shift(a, 0, dy, (bl[0][0], 97, bl[-1][1] + 1, 118))
G5 = {'H': [(S5, '[H] counter-suffix ios')], 'C': [(S5, '[C] counter-suffix ios'), (S5, '[C] counter-suffix android')], 'T': [(S5, '[T] counter-suffix ios')],
      'I': [(S5, '[I] cjk-decimal/css3-counter-styles-005 ios'), (S5, '[I] cjk-decimal/css3-counter-styles-005 android')],
      'Kios': [(S5, '[K] counter-suffix ios')], 'Kand': [(S5, '[K] counter-suffix android')]}
L5F = {'C-ideographs-swapped-5-6': ({'cs': l5_ideo_swap}, G5['C']), 'C-ideograph+3y': ({'cs': lambda a: l5_ideo_y(a, 3)}, G5['C']),
       'H-letters-swapped-3-4': ({'cs': l5_letter_swap}, G5['H']), 'H-period-3y': ({'cs': lambda a: l5_period_y(a, -3)}, G5['H']),
       'T-row9-text-erased': ({'cs': lambda a: l5_row9_text(a, 'erase')}, G5['T'] + G5['Kios'] + G5['Kand']),
       'T-row9-text-3x': ({'cs': lambda a: l5_row9_text(a, 'move')}, G5['T'] + G5['Kios'] + G5['Kand']),
       'I-line1-ideograph-is-line2s': ({'005': l5_i_copy}, G5['I']), 'I-line1-marker+3y': ({'005': lambda a: l5_i_y(a, 3)}, G5['I'])}
for name, (fs, keys) in L5F.items():
    repl = {}
    for p in ('web', 'ios', 'android'):
        cs, c5 = png(f'{OK}/counter-suffix.{p}.png'), png(f'{OK}/cjk-decimal__css3-counter-styles-005.{p}.png')
        if p != 'web': cs, c5 = fs.get('cs', lambda a: a)(cs), fs.get('005', lambda a: a)(c5)
        repl[('css-counter-styles', p, 'counter-suffix')] = cs; repl[('css-counter-styles', p, 'cjk-decimal/css3-counter-styles-005')] = c5
    case('L5', 'css-counter-styles', f'L5-{name}', repl, keys, 'L5')

# ── L6 ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
S6 = 'android-paint-order.geometry.py'
def l6_one_child(a, fill, how):     # ONE child moved 3 px (or erased) inside the children's union bbox (the bbox is unchanged)
    m = fill(a); cs = [c for c in comps(m) if len(c[0]) > 30]; ux0, uy0, ux1, uy1 = bbox_of(m)
    for ys, xs in sorted(cs, key=lambda c: -len(c[0])):
        for dx, dy in ((3, 0), (-3, 0), (0, 3), (0, -3)):
            if xs.min() + dx >= ux0 and xs.max() + dx <= ux1 and ys.min() + dy >= uy0 and ys.max() + dy <= uy1:
                o = a.copy(); col = a[ys, xs].copy(); o[ys, xs] = 255; o[ys + dy, xs + dx] = col
                if bbox_of(fill(o))[:4] == (ux0, uy0, ux1, uy1): return o, f'child@{xs.min()},{ys.min()} {dx:+d},{dy:+d}'
    return a, 'none'
picked = {}
for name in ('one-child-moved-3px',):
    repl = {}
    for sec, t, fl in pt.L6_X:
        o, how = l6_one_child(pt.png(f'android-paint-order-replay/{t.replace("/", "__")}-android-U2.png'), fl, name); repl[(sec, 'android', t)] = o; picked[t] = how
    keys = [(S6, f'[X] {t.split("/")[-1]} android') for sec, t, fl in pt.L6_X if not t.endswith('safe-002')]   # safe-002's own replay FAILs (known)
    case('L6', 'css-flexbox,css-grid', f'L6-X-{name}', repl, keys, 'one child')

# ── L2 U2 (clip-3, two gating probes): defects that avoid the cbb probe's scanlines (row 80, column 120) and corner squares ──
C3 = ('filter-effects', 'backdrop-filter-nested-border-radius-clip-3'); B3 = pt.png('cbb-android-replay/android-clip-3.png')
def c3_pink(a):                     # the backdrop-source pink showing in a 2 x 40 px sliver right of #inner, above row 80
    o = a.copy(); o[36:76, 236:238] = (255, 127, 255); return o
def c3_notch(a):                    # a 7 x 7 hole inside #inner, clear of row 80 / column 120 / the corner squares
    o = a.copy(); o[95:102, 150:157] = (204, 204, 204); return o
K3 = [('compose-wpt-content-box-cb.geometry.py', 'backdrop-filter-nested-border-radius-clip-3 android'), ('cbb-android.geometry.py', 'backdrop-filter-nested-border-radius-clip-3 android')]
for name, f in (('c3-pink-sliver-off-scanlines', c3_pink), ('c3-notch-off-scanlines', c3_notch)):
    case('L2U2', 'filter-effects', f'L2-{name}', {(C3[0], 'android', C3[1]): f(B3)}, K3, 'clip-3')

if __name__ == '__main__':
    only = sys.argv[1].split(',') if len(sys.argv) > 1 else None
    print('L6 picked:', picked)
    res = []
    for lane, secs, fid, repl, want, why in CASES:
        if only and lane not in only: continue
        rid = pt.make_run(SCR, fid, repl)
        for (sec, plat, test), arr in repl.items():
            os.makedirs(f'{SCR}/pngs/{fid}', exist_ok=True); I(arr).save(f'{SCR}/pngs/{fid}/{plat}__{test.replace("/", "__")}.png')
        rc, rows = pt.gate(rid, lane[:2], secs, EXP, PY, os.path.join(SCR, f'gate-{fid}.json'))
        for k in sorted(want):
            r = rows.get(k); v = r['verdict'] if r else 'NO ROW'
            tag = 'ACCEPTED (defect)' if v == 'PASS' else 'rejected' if v == 'FAIL' else v
            line = (r['line'][-170:] if r else '')
            print(f'{fid:42} gate-exit {rc}  {k[1]:58} {v:6} {tag}')
            print(f'      {line}')
            res.append({'fid': fid, 'key': k[1], 'script': k[0], 'verdict': v, 'gateExit': rc, 'line': r['line'] if r else None})
    json.dump(res, open(f'{SCR}/r2fakes.{"-".join(only) if only else "all"}.json', 'w'), indent=1)
    acc = [x for x in res if x['verdict'] == 'PASS']
    print(f'\n{len(res)} (fake, key) reads; ACCEPTED {len(acc)}; rejected {sum(1 for x in res if x["verdict"] == "FAIL")}; other {sum(1 for x in res if x["verdict"] not in ("PASS", "FAIL"))}')

