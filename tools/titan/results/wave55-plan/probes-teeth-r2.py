#!/usr/bin/env python3
# tools/titan/results/wave55-plan/probes-teeth-r2.py — the ROUND-2 cases of the standing rejection set (plan-skeptic round 2,
# instrument-teeth M6). Loaded by probes-teeth.py run_all() (never run alone); every case is (lane, sections, fid, {(sec,
# plat, test): picture}, {(script, key): pre-registered verdict}) exactly as probes-teeth.py's own builders.
#
# Why a second module: round 1's fakes were the ones the round-1 rules were tuned to; round 2 built NEW wrong pictures of the
# same four classes (target ink removed, a run moved 2-3 px, a segment shortened, a count-preserving rearrangement) from each
# key's own fixed picture, and 37 of 43 gating keys accepted one (plan-skeptic-round-2.instrument-teeth.md §2). Those
# builders are ported here verbatim from plan-skeptic-round-2.instrument-teeth.d/r2fakes.py (+ l4_011.py, vrl.py), every case
# pre-registered FAIL on the key it attacks, plus a case set for every pictureCorrectTally key that is NOT gating:
#   [S] dynamic-offset-vrl-rtl-001 android (report), [K] counter-suffix ios / android (control), the wave-54
#   hyphenate-character-001 / -003 ios controls (L1's tally entries) — a tally key that accepts a wrong picture would let
#   the PR count it picture-correct.
# One fake is withdrawn as the skeptic withdrew it (bidi `rtl-runs+2y`: its band reads are confounded by the next line).
# One case is a NAMED in-tolerance exception (round 2 S1): line 1 of the 2em heading clipped 10 px at its right end passes
# the ten face-present L4 keys while ua-heading-margin.geometry.py X1_TOL ≥ 10 (±12 rests on web's face, Δx1 −8 / +2 / −8;
# stage 1 re-sets it from the device faces, PLAN §7 / §10). Its expectation is read from the probe's X1_TOL: PASS (the
# blind spot, PLAN §9 D33) while X1_TOL ≥ 10, FAIL once X1_TOL ≤ 9 — except the two Compose no-wrap [M] rows, which read
# band 1's position and glyphs over the ref's line-1 columns and never its right edge (PASS either way, named).
# Round 4 (fix-r4.done.md, D39): the face-present native rows read `face xor:` (every band's ink = the face reference's, XOR ≤
# FACE_XOR_TOL, the no-wrap rows' band 1 included), and a 10-px clip removes ≥ 10 px of line-1 ink — so while FACE_XOR_TOL < 10
# the clip is pre-registered FAIL on all ten keys: the D33 blind spot is closed (face_xor_tol() reads the switch).
# Writes nothing itself: probes-teeth.py writes the fake runs under its --scratch dir.
import os, re
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))


def comps(mask):
    """4-connected components of a boolean mask -> [(ys, xs)] (small pictures: an explicit flood fill)."""
    seen = np.zeros_like(mask); out = []
    H, Wd = mask.shape
    for y0, x0 in zip(*np.nonzero(mask)):
        if seen[y0, x0]:
            continue
        st = [(y0, x0)]; seen[y0, x0] = True; ys, xs = [], []
        while st:
            y, x = st.pop(); ys.append(y); xs.append(x)
            for yy, xx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
                if 0 <= yy < H and 0 <= xx < Wd and mask[yy, xx] and not seen[yy, xx]:
                    seen[yy, xx] = True; st.append((yy, xx))
        out.append((np.array(ys), np.array(xs)))
    return out


def x1_tol():
    """ua-heading-margin.geometry.py's line-1 right-edge tolerance, read from its source (the S1 exception's switch)."""
    m = re.search(r'^TOP_TOL, X0_TOL, X1_TOL = (\d+), (\d+), (\d+)', open(os.path.join(HERE, 'ua-heading-margin.geometry.py')).read(), re.M)
    assert m, 'ua-heading-margin.geometry.py: no X1_TOL line'
    return int(m.group(3))


def face_xor_tol():
    """ua-heading-margin.geometry.py's round-4 `face xor:` tolerance, or None before round 4 (the clip-10 case's second switch)."""
    m = re.search(r'^INK_TOL, FACE_INK_TOL, FACE_XOR_TOL = (\d+), (\d+), (\d+)', open(os.path.join(HERE, 'ua-heading-margin.geometry.py')).read(), re.M)
    return int(m.group(3)) if m else None


def cases(pt, lane):
    """The round-2 cases of one lane ('L1' … 'L6'); pt is the loaded probes-teeth module (its helpers and fixed pictures)."""
    out = []
    shift, bbox_of, png, cap = pt.shift, pt.bbox_of, pt.png, pt.cap
    def case(secs, fid, repl, keys, verdict='FAIL'):
        out.append((lane, secs, f'r2-{fid}', repl, {k: verdict for k in keys}))

    if lane == 'L1':
        K1 = lambda t: ('hyphen-ios-linebox.geometry.py', f'hyphen-ios-linebox {t} ios')
        HC = lambda t: ('hyphenate-character.geometry.py', f'hyphenate-character-{t} ios')     # tally controls (round-1 M8)
        F = {t: png(f'hyphen-ios-linebox-replay/{t}-ios-Rm.png') for t in ('001', '003', '004')}
        def one_hyph(a, t):         # target ink removed in ONE line only (group 1 line 1's hyphenate-character)
            cells = 1 if t == '003' else 3; o = a.copy(); top, bot, x0, x1 = pt._l1_groups(a, t)[0][0]
            reg = np.zeros(a.shape[:2], bool); reg[top - 3:bot + 4, x1 - cells * 10 + 2:x1 + 2] = True; o[pt._nonwhite(a) & reg] = 255; return o
        def last_cell(a, t):        # segment shortened: group 1 line 2 loses its last ~8 px (one Menlo cell)
            o = a.copy(); top, bot, x0, x1 = pt._l1_groups(a, t)[0][1]
            reg = np.zeros(a.shape[:2], bool); reg[top - 3:bot + 4, x1 - 7:x1 + 2] = True; o[pt._nonwhite(a) & reg] = 255; return o
        def line_x1(a, t):          # one run (group 3 line 2) moved 1 px right
            top, bot, _, _ = pt._l1_groups(a, t)[2][1]; return shift(a, 1, 0, (0, top - 3, 140, bot + 4))
        repl = lambda f: {('css-text', 'ios', f'hyphens/hyphenate-character-{t}'): f(F[t], t) for t in F}
        # the tally's hyphenate-character controls on the fixed pictures (they must certify them) …
        case('css-text', 'L1-tally-fixed', repl(lambda a, t: a), [HC('001'), HC('003')], 'PASS')
        for name, f in (('one-hyph-removed', one_hyph), ('g1l2-last-cell-cut', last_cell), ('g3l2+1x', line_x1),
                        ('all+1x', lambda a, t: shift(a, 1, 0, (0, pt.L1_DIV0[t], 140, a.shape[0])))):
            ts = ('003', '004') if name == 'one-hyph-removed' else ('001', '003', '004')
            # … and reject the defects their own rule reads: a missing hyphenate-character (003) and a cut last cell (001 / 003)
            hc = [HC('003')] if name == 'one-hyph-removed' else [HC('001'), HC('003')] if name == 'g1l2-last-cell-cut' else []
            case('css-text', f'L1-{name}', repl(f), [K1(t) for t in ts] + hc)

    if lane == 'L2':
        S2 = 'compose-wpt-content-box-cb.geometry.py'
        k2 = lambda sec, t: (S2, f'{t.split("/")[-1] if sec == "filter-effects" else t} android')
        def redsliver(a, sec):      # the failure ink beside the right box: 2 × 100 px (red / backdrop pink)
            x0, y0, x1, y1 = bbox_of(pt._green(a)); o = a.copy()
            o[y0:y0 + 100, x1 + 1:x1 + 3] = (255, 0, 0) if sec == 'css-flexbox' else (255, 127, 255); return o
        def notch(a, sec):          # a 7 × 7 white hole in the middle of the "filled" box (49 px)
            x0, y0, x1, y1 = bbox_of(pt._green(a)); cx, cy = (x0 + x1) // 2, (y0 + y1) // 2; o = a.copy(); o[cy:cy + 7, cx:cx + 7] = 255; return o
        def corner_moved(a, sec):   # count-preserving: the TL arc filled square, the BR radius grown so the mass stays
            g = pt._green(a); x0, y0, x1, y1 = bbox_of(g); o = a.copy()
            tl = np.zeros_like(g); tl[y0:y0 + 26, x0:x0 + 26] = True; add = int((tl & ~g).sum()); o[tl & ~g] = (0, 128, 0)
            yy, xx = np.mgrid[0:a.shape[0], 0:a.shape[1]]
            for r in range(26, 60):
                cxr, cyr = x1 - r + 0.5, y1 - r + 0.5
                cut = g & (xx > cxr) & (yy > cyr) & ((xx - cxr) ** 2 + (yy - cyr) ** 2 > r * r)
                if int(cut.sum()) >= add:
                    break
            o[cut] = a[y1 + 3, x1 + 3] if y1 + 3 < a.shape[0] else 255; return o
        for name, f, which in (('fail-ink-sliver', redsliver, pt.L2_U1), ('notch7', notch, pt.L2_U1),
                               ('radius-moved', corner_moved, [u for u in pt.L2_U1 if u[0] == 'filter-effects'])):
            case('css-flexbox,filter-effects', f'L2-{name}', {(sec, 'android', t): f(cap('wave54-probe', sec, t, 'android'), sec) for sec, t in which},
                 [k2(sec, t) for sec, t in which])
        C3, B3 = 'backdrop-filter-nested-border-radius-clip-3', png('cbb-android-replay/android-clip-3.png')
        K3 = [(S2, f'{C3} android'), ('cbb-android.geometry.py', f'{C3} android')]
        def c3_pink(a):             # the backdrop-source pink in a 2 × 40 sliver right of #inner, above row 80
            o = a.copy(); o[36:76, 236:238] = (255, 127, 255); return o
        def c3_notch(a):            # a 7 × 7 hole inside #inner, clear of row 80 / column 120 / the corner squares
            o = a.copy(); o[95:102, 150:157] = (204, 204, 204); return o
        for name, f in (('c3-pink-sliver-off-scanlines', c3_pink), ('c3-notch-off-scanlines', c3_notch)):
            case('filter-effects', f'L2-{name}', {('filter-effects', 'android', C3): f(B3)}, K3)

    if lane == 'L3':
        S3 = 'positioned-parent-slot.geometry.py'
        D = pt.l3_fixed('dir-style-02a')
        def vflip(a, k):
            y0 = 16 + 20 * (k - 1); o = a.copy(); o[y0:y0 + 20, 21:] = a[y0:y0 + 20, 21:][::-1]; return o
        for k in (6, 7, 9):
            y0 = 16 + 20 * (k - 1)
            for name, f in (('text+2y', lambda a: shift(a, 0, 2, (21, y0, a.shape[1], y0 + 18))),
                            ('text-2y', lambda a: shift(a, 0, -2, (21, y0 + 2, a.shape[1], y0 + 20))), ('text-vflip', lambda a: vflip(a, k))):
                case('selectors', f'L3-dir-row{k}-{name}', {('selectors', 'android', 'dir-style-02a'): f(D)}, [(S3, f'[S] dir-style-02a android row{k}')])
        B = pt.l3_fixed('bidi-tab-001')
        yel = lambda a: (a[..., 0] > 200) & (a[..., 1] > 200) & (a[..., 2] < 80)
        def bidi_order(a):          # count-preserving: each RTL band's blue run mirrored inside its own extent ("0■" ↔ "■0")
            o = a.copy(); b = pt._blue(a)
            for y0 in (86, 106, 186, 226):
                xs = np.nonzero(b[y0:y0 + 20])[1]
                if len(xs):
                    o[y0:y0 + 20, xs.min():xs.max() + 1] = a[y0:y0 + 20, xs.min():xs.max() + 1][:, ::-1]
            return o
        for name, f in (('rtl-run-order-mirrored', bidi_order), ('yellow-boxes-erased', lambda a: np.where(yel(a)[..., None], 255, a))):
            case('css-text', f'L3-bidi-{name}', {('css-text', 'android', 'bidi/bidi-tab-001'): f(B)}, [(S3, '[S] bidi-tab-001 android rtl-rows')])
        ST = pt.l3_fixed('anchor-position-multicol-nested-001')
        def red(a):
            o = a.copy(); o[150:160, 270:280] = (255, 0, 0); return o
        def others_erased(a):
            o = a.copy(); keep = np.zeros(a.shape[:2], bool)
            for x, y in pt.L3_CELLS:
                keep[y - 2:y + 12, x - 2:x + 12] = True
            g = (a[..., 0] < 60) & (a[..., 1] > 100) & (a[..., 2] < 60); o[g & ~keep] = 255; return o
        for name, f in (('red-shown', red), ('other-squares-erased', others_erased)):
            case('css-anchor-position', f'L3-stack-{name}', {('css-anchor-position', 'android', 'anchor-position-multicol-nested-001'): f(ST)},
                 [(S3, '[W] anchor-position-multicol-nested-001 android stack')])
        mag = lambda a: (a[..., 0] > 200) & (a[..., 1] < 80) & (a[..., 2] > 200)
        def parent(a, how):         # the yellow positioned parent erased, or moved 3 px right (the magenta child kept)
            y = yel(a); x0, y0, x1, y1 = bbox_of(y); m = mag(a); o = a.copy(); o[y] = 255
            if how == 'x+3':
                o[y0:y1 + 1, x0 + 3:x1 + 4] = (255, 255, 0)
            o[m] = a[m]; return o
        # the gating dynamic-offset keys, and (tally, report) dynamic-offset-vrl-rtl-001 — its fixed picture must pass too
        case('css-writing-modes', 'L3-vrl-rtl-001-fixed', {('css-writing-modes', 'android', 'dynamic-offset-vrl-rtl-001'): pt.l3_fixed('dynamic-offset-vrl-rtl-001')},
             [(S3, '[S] dynamic-offset-vrl-rtl-001 android')], 'PASS')
        for doc, kind in (('dynamic-offset-rtl-001', 'S'), ('dynamic-offset-rtl-002', 'W'), ('dynamic-offset-vrl-rtl-001', 'S')):
            m = pt.l3_fixed(doc)
            for name in ('parent-erased', 'parent+3x'):
                case('css-writing-modes', f'L3-{doc[15:]}-{name}', {('css-writing-modes', 'android', doc): parent(m, 'x+3' if name.endswith('3x') else 'erase')},
                     [(S3, f'[{kind}] {doc} android')])

    if lane == 'L4':
        S4 = 'ua-heading-margin.geometry.py'
        T3 = ['text-decoration-inset-005', 'text-decoration-inset-006', 'text-decoration-inset-014']
        def clip(a, t, n=10):       # segment shortened: heading line 1 clipped by n px at its right end
            _, _, b = pt.l4_region(a, t); top, bot, x0, x1 = b[0]; reg = np.zeros(a.shape[:2], bool); reg[top:bot + 1, x1 - n + 1:] = True
            o = a.copy(); o[(a.min(axis=2) < 250) & reg] = 255; return o
        def mirror(a, t):           # count-preserving: line 1's glyphs mirrored inside its own extent
            _, _, b = pt.l4_region(a, t); top, bot, x0, x1 = b[0]; o = a.copy(); o[top - 2:bot + 3, x0:x1 + 1] = a[top - 2:bot + 3, x0:x1 + 1][:, ::-1]; return o
        def thirds(a, t):           # count-preserving: line 1's first and last thirds swapped
            _, _, b = pt.l4_region(a, t); top, bot, x0, x1 = b[0]; w = (x1 - x0 + 1) // 3; o = a.copy()
            L, R = a[top - 2:bot + 3, x0:x0 + w].copy(), a[top - 2:bot + 3, x1 + 1 - w:x1 + 1].copy()
            o[top - 2:bot + 3, x0:x0 + w] = R; o[top - 2:bot + 3, x1 + 1 - w:x1 + 1] = L; return o
        MK = lambda t, p: (S4, f'[M] {t} {p}'); FK = lambda t, p: (S4, f'[F] {t} {p}')
        face_keys = [MK(t, p) for t in T3 for p in ('ios', 'android')] + [FK(t, 'ios') for t in T3] + [FK('text-decoration-inset-014', 'android')]
        nowrap = {MK('text-decoration-inset-005', 'android'), MK('text-decoration-inset-006', 'android')}
        for name, f in (('line1-mirrored', mirror), ('line1-thirds-swapped', thirds)):
            case('css-text-decor', f'L4-C-{name}', {('css-text-decor', p, t): f(png(f'ua-heading-margin-replay/{t}-{p}-C.png'), t) for t in T3 for p in ('ios', 'android')}, face_keys)
            case('css-text-decor', f'L4-U1-{name}', {('css-text-decor', p, t): f(png(f'ua-heading-margin-replay/{t}-{p}-U1.png'), t) for t in T3 for p in ('ios', 'android')},
                 [MK(t, p) for t in T3 for p in ('ios', 'android')])
        # the S1 named exception: PASS (blind spot) while X1_TOL ≥ 10; FAIL once stage 1 tightens it — the no-wrap rows always PASS;
        # round 4: FAIL on every face key while `face xor:` reads (FACE_XOR_TOL < 10: the clip removes ≥ 10 px of ink)
        tol, xtol = x1_tol(), face_xor_tol()
        repl = {('css-text-decor', p, t): clip(png(f'ua-heading-margin-replay/{t}-{p}-C.png'), t) for t in T3 for p in ('ios', 'android')}
        out.append((lane, 'css-text-decor', 'r2-L4-C-line1-clipped-10px', repl,
                    {k: ('FAIL' if xtol is not None and xtol < 10 else 'PASS' if tol >= 10 or k in nowrap else 'FAIL') for k in face_keys}))
        t11 = 'text-decoration-inset-011'
        def mirror_band(a, k):      # count-preserving: 011's band k mirrored inside its own x extent
            top, bot, x0, x1 = pt.bands(pt._dk(a), 112, 400)[k]; o = a.copy(); o[top - 1:bot + 2, x0:x1 + 1] = a[top - 1:bot + 2, x0:x1 + 1][:, ::-1]; return o
        for arm in ('C', 'U1'):
            case('css-text-decor', f'L4-011-{arm}-band1-mirrored', {('css-text-decor', p, t11): mirror_band(png(f'ua-heading-margin-replay/{t11}-{p}-{arm}.png'), 0) for p in ('ios', 'android')},
                 [MK(t11, p) for p in ('ios', 'android')])

    if lane == 'L5':
        S5, OK, ROW = 'counter-suffix-rows.geometry.py', 'counter-suffix-rows-replay/ok-all', pt.L5_ROW
        def swap(a, x0, x1, ra, rb):
            o = a.copy(); A_, B_ = a[ra[0]:ra[1], x0:x1].copy(), a[rb[0]:rb[1], x0:x1].copy(); o[ra[0]:ra[1], x0:x1] = B_; o[rb[0]:rb[1], x0:x1] = A_; return o
        def ideo_swap(a):           # rows 5 / 6 trade their counter ideographs (一 ↔ 二); suffix and items kept
            b5 = pt.l5_blobs(a, 20, 60, *ROW[5]); b6 = pt.l5_blobs(a, 20, 60, *ROW[6])
            return swap(a, min(b5[0][0], b6[0][0]), max(b5[0][1], b6[0][1]) + 1, ROW[5], ROW[6])
        def ideo_y(a, dy):          # rows 5-6's ideograph 3 px low inside its line
            o = a
            for k in (5, 6):
                b = pt.l5_blobs(o, 20, 60, *ROW[k]); o = shift(o, 0, dy, (b[0][0], ROW[k][0] + 3, b[0][1] + 1, ROW[k][1] - 3))
            return o
        def letter_swap(a):         # rows 3 / 4 trade their hebrew letters (א ↔ ב); periods and items kept
            L3_ = [b for b in pt.l5_blobs(a, 30, 64, *ROW[3]) if b[1] - b[0] >= 4][0]; L4_ = [b for b in pt.l5_blobs(a, 30, 64, *ROW[4]) if b[1] - b[0] >= 4][0]
            return swap(a, min(L3_[0], L4_[0]), max(L3_[1], L4_[1]) + 1, ROW[3], ROW[4])
        def period_y(a, dy):
            o = a
            for k in (3, 4):
                d = [b for b in pt.l5_blobs(o, 30, 64, *ROW[k]) if b[1] - b[0] <= 2][0]; o = shift(o, 0, dy, (d[0], ROW[k][0] + 2, d[1] + 1, ROW[k][1] - 4))
            return o
        def row9_text(a, how):      # row 9's item text (everything left of the digit window x136-150) erased / moved 3 px
            y0, y1 = ROW[9]; o = a.copy()
            if how == 'erase':
                m = a.min(axis=2) < 250; reg = np.zeros_like(m); reg[y0:y1, :136] = True; o[m & reg] = 255; return o
            return shift(o, -3, 0, (60, y0, 136, y1))
        def i_copy(a):              # 005: line 1's ideograph replaced by line 2's (二 where 一 belongs); suffixes kept
            o = a.copy(); b1 = pt.l5_blobs(a, 200, 330, 95, 121); b2 = pt.l5_blobs(a, 200, 330, 121, 152)
            x0 = min(b1[0][0], b2[0][0]); x1 = max(b1[0][1], b2[0][1]) + 1; o[95:121, x0:x1] = 255
            src = a[121:152, x0:x1]; ys = np.nonzero((src.min(axis=2) < 250).any(axis=1))[0]
            ys1 = np.nonzero((a[95:121, x0:x1].min(axis=2) < 250).any(axis=1))[0]
            c1 = 95 + (ys1.min() + ys1.max()) // 2; h = ys.max() - ys.min() + 1; top = c1 - h // 2
            o[top:top + h, x0:x1] = src[ys.min():ys.max() + 1]; return o
        def i_y(a, dy):             # 005: line 1's marker (ideograph + 、) 3 px low inside its line
            bl = [b for b in pt.l5_blobs(a, 200, 330, 95, 121) if b[0] < 255]; return shift(a, 0, dy, (bl[0][0], 97, bl[-1][1] + 1, 118))
        G = {'H': [(S5, '[H] counter-suffix ios')], 'C': [(S5, '[C] counter-suffix ios'), (S5, '[C] counter-suffix android')],
             'T': [(S5, '[T] counter-suffix ios')], 'I': [(S5, '[I] cjk-decimal/css3-counter-styles-005 ios'), (S5, '[I] cjk-decimal/css3-counter-styles-005 android')],
             'K': [(S5, '[K] counter-suffix ios'), (S5, '[K] counter-suffix android')]}
        F = {'C-ideographs-swapped-5-6': ({'cs': ideo_swap}, G['C']), 'C-ideograph+3y': ({'cs': lambda a: ideo_y(a, 3)}, G['C']),
             'H-letters-swapped-3-4': ({'cs': letter_swap}, G['H']), 'H-period-3y': ({'cs': lambda a: period_y(a, -3)}, G['H']),
             'T-row9-text-erased': ({'cs': lambda a: row9_text(a, 'erase')}, G['T'] + G['K']), 'T-row9-text-3x': ({'cs': lambda a: row9_text(a, 'move')}, G['T'] + G['K']),
             'I-line1-ideograph-is-line2s': ({'005': i_copy}, G['I']), 'I-line1-marker+3y': ({'005': lambda a: i_y(a, 3)}, G['I'])}
        for name, (fs, keys) in F.items():
            repl = {}
            for p in ('web', 'ios', 'android'):
                cs, c5 = png(f'{OK}/counter-suffix.{p}.png'), png(f'{OK}/cjk-decimal__css3-counter-styles-005.{p}.png')
                if p != 'web':
                    cs, c5 = fs.get('cs', lambda a: a)(cs), fs.get('005', lambda a: a)(c5)
                repl[('css-counter-styles', p, 'counter-suffix')] = cs; repl[('css-counter-styles', p, 'cjk-decimal/css3-counter-styles-005')] = c5
            case('css-counter-styles', f'L5-{name}', repl, keys)

    if lane == 'L6':
        S6 = 'android-paint-order.geometry.py'
        def one_child(a, fill):     # ONE child moved 3 px inside the children's union bbox (the bbox is unchanged)
            m = fill(a); cs = [c for c in comps(m) if len(c[0]) > 30]; ux0, uy0, ux1, uy1 = bbox_of(m)
            for ys, xs in sorted(cs, key=lambda c: -len(c[0])):
                for dx, dy in ((3, 0), (-3, 0), (0, 3), (0, -3)):
                    if xs.min() + dx >= ux0 and xs.max() + dx <= ux1 and ys.min() + dy >= uy0 and ys.max() + dy <= uy1:
                        o = a.copy(); col = a[ys, xs].copy(); o[ys, xs] = 255; o[ys + dy, xs + dx] = col
                        if bbox_of(fill(o))[:4] == (ux0, uy0, ux1, uy1):
                            return o
            raise AssertionError('no child can move 3 px inside the union bbox')
        repl = {(sec, 'android', t): one_child(png(f'android-paint-order-replay/{t.replace("/", "__")}-android-U2.png'), fl) for sec, t, fl in pt.L6_X}
        case('css-flexbox,css-grid', 'L6-X-one-child-moved-3px', repl,
             [(S6, f'[X] {t.split("/")[-1]} android') for sec, t, fl in pt.L6_X if not t.endswith('safe-002')])   # safe-002's own replay FAILs (known)
    return out
