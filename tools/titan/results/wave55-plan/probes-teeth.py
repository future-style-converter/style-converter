#!/usr/bin/env python3
# tools/titan/results/wave55-plan/probes-teeth.py — the STANDING rejection set of every gating geometry key (plan-skeptic
# round 1, instrument-teeth M9; PLAN §0 "Every gating geometry key ships with its fakes"; wave-54 lesson "Every gating
# geometry probe the plan wrote passed some skeptic's fake"). Imported by probes-selfcheck.py (§T); runnable alone.
#
# Why: probes-selfcheck.py's §0-§L7 prove each key FAILs on the PRE-fix picture and PASSes the replay — not that it rejects a
# PLAUSIBLE WRONG picture. Round 1 built such pictures from each key's OWN fixed picture (the brief's replay / the old unit's
# device capture) and 41 of 43 gating keys accepted one. This module keeps those fakes as code: per family, the fixed picture
# with ONE defect of the four classes — target ink removed, a run moved ±2 / ±3 px, a segment shortened, a count-preserving
# rearrangement — written into a FAKE RUN (a symlink tree of tools/titan/runs/wave54-final under the scratch dir, only the faked
# captures replaced; its run id is its path relative to tools/titan/runs/) and read through the REAL geometry-gate.py on the
# GENERATED expectations.json under the pinned interpreter. Each case pre-registers, per gating key, PASS (its fixed picture)
# or FAIL (a fake); a case whose key prints otherwise is a VIOLATION (probes-selfcheck.py exits 1). Exceptions are named in the
# case with their reason (safe-002's own replay keeps 50 crossings; a 1-px whole shift sits inside [PO]'s ±1 tolerance by design;
# a no-op fake is not listed).
# Builders ported from plan-skeptic-round-1.instrument-teeth.d/fam_L{1..6}.py. Writes ONLY under the scratch dir it is given.
# Plan-skeptic round 2 (instrument-teeth M6): 37 of 43 gating keys accepted a NEW fake of the same four classes, and the
# tally's non-gating keys had no fakes at all. probes-teeth-r2.py holds those round-2 cases (every one pre-registered FAIL,
# the tally keys' fixed pictures PASS, one named in-tolerance exception) and run_all() appends them lane by lane.
# Plan-skeptic round 3 (verify M2): a fifth class, ADDED INK (the target painted twice, a glyph doubled, stray ink in the band),
# passed the three L3 dir-style-02a row keys. probes-teeth-r3.py holds those cases (builders in
# plan-skeptic-round-3.fakes/added_ink.py) and run_all() appends them after round 2's.
# Plan-skeptic round 4 (verify M1-M4): STRICTLY-additive ink (nothing moved or erased) still passed 17 gating keys of L4 / L5 / L6
# inside their own read zones. probes-teeth-r4.py pins every such variant (builders: plan-skeptic-round-4.fakes/added_ink4.py,
# the verifier's own) and run_all() appends them after round 3's.
import json, os, shutil, subprocess
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
RUNS = f'{ROOT}/tools/titan/runs'
W54 = f'{RUNS}/wave54-final'
REFS = f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin'
DIRS = {'web': 'screenshots', 'ios': 'ios-screenshots', 'android': 'android-screenshots'}

cap_name = lambda sec, test: f'wpt__{sec}__{test.replace("/", "__")}.png'
A = lambda im: np.asarray(im.convert('RGB')).astype(np.int16).copy()
I = lambda a: Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGB')
cap = lambda run, sec, test, plat: A(Image.open(f'{RUNS}/{run}/sections/{sec}/{DIRS[plat]}/{cap_name(sec, test)}'))
png = lambda rel: A(Image.open(os.path.join(HERE, rel)))


def shift(a, dx, dy, region=None, bg=(255, 255, 255)):
    """Translate the pixels of `region` (x0, y0, x1, y1; default whole) by (dx, dy), refilling the vacated area with bg."""
    h, w, _ = a.shape
    x0, y0, x1, y1 = region or (0, 0, w, h)
    src = a[y0:y1, x0:x1].copy(); out = a.copy(); out[y0:y1, x0:x1] = bg
    ys0, xs0, ys1, xs1 = max(0, y0 + dy), max(0, x0 + dx), min(h, y1 + dy), min(w, x1 + dx)
    out[ys0:ys1, xs0:xs1] = src[ys0 - (y0 + dy):ys1 - (y0 + dy), xs0 - (x0 + dx):xs1 - (x0 + dx)]
    return out


def bbox_of(m):
    ys, xs = np.nonzero(m); return int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())


def bands(m, y0, y1=None, x1=None, gap=2):
    """[top, bot, x0, x1] row bands of mask m from y0 (columns < x1), blank runs > gap split."""
    out, cur, blank = [], None, 0
    for y in range(y0, min(y1 or m.shape[0], m.shape[0])):
        xs = np.nonzero(m[y, :x1])[0]
        if len(xs):
            cur = [y, y, xs.min(), xs.max()] if cur is None else [cur[0], y, min(cur[2], xs.min()), max(cur[3], xs.max())]; blank = 0
        elif cur is not None:
            blank += 1
            if blank > gap:
                out.append(cur); cur = None; blank = 0
    if cur is not None:
        out.append(cur)
    return out

# ── L1 · hyphen-ios-linebox (fixed = hyphen-ios-linebox-replay/<t>-ios-Rm.png) ────────────────────────────────────────────
L1_DIV0 = {'001': 100, '003': 80, '004': 80}
_core = lambda a: a.max(axis=2) < 110
_nonwhite = lambda a: a.min(axis=2) < 250
def _l1_groups(a, t):
    gs, cur = [], []
    for ln in bands(_core(a), L1_DIV0[t], x1=140):
        if cur and ln[0] - cur[-1][1] >= 18:
            gs.append(cur); cur = []
        cur.append(ln)
    return gs + ([cur] if cur else [])
def l1_hyph_removed(a, t):          # target ink removed: the hyphenate-character(s) ending every non-final line (001 shows none)
    cells = 1 if t == '003' else 3; o = a.copy()
    for g in _l1_groups(a, t):
        for top, bot, x0, x1 in g[:-1]:
            reg = np.zeros(a.shape[:2], bool); reg[top - 3:bot + 4, x1 - cells * 10 + 2:x1 + 2] = True; o[_nonwhite(a) & reg] = 255
    return o
def l1_swap(a, t):                  # count-preserving rearrangement: lines 2 and 3 of group 1 trade places
    g = _l1_groups(a, t)[0]; o = a.copy(); (t2, b2, _, _), (t3, b3, _, _) = g[1], g[2]; h = max(b2 - t2, b3 - t3) + 1
    b2_, b3_ = a[t2 - 3:t2 + h + 3, :140].copy(), a[t3 - 3:t3 + h + 3, :140].copy(); o[t2 - 3:t2 + h + 3, :140] = b3_; o[t3 - 3:t3 + h + 3, :140] = b2_
    return o
def l1_half(a, t):                  # segment shortened: every line keeps its left half
    o = a.copy()
    for top, bot, x0, x1 in bands(_core(a), L1_DIV0[t], x1=140):
        reg = np.zeros(a.shape[:2], bool); reg[top - 3:bot + 4, (x0 + x1) // 2:140] = True; o[_nonwhite(a) & reg] = 255
    return o
L1_FAKES = {'hyph-removed': l1_hyph_removed, 'lines-2-3-swapped': l1_swap, 'right-half-cut': l1_half,
            'text+3x': lambda a, t: shift(a, 3, 0, (0, L1_DIV0[t], 140, a.shape[0])), 'text-2x': lambda a, t: shift(a, -2, 0, (0, L1_DIV0[t], 140, a.shape[0])),
            'g2l1+3x': lambda a, t: (lambda ln: shift(a, 3, 0, (0, ln[0] - 3, 140, ln[1] + 4)))(_l1_groups(a, t)[1][0])}
def l1_cases():
    K = lambda t: ('hyphen-ios-linebox.geometry.py', f'hyphen-ios-linebox {t} ios')
    out = [('L1', 'css-text', 'fixed', {('css-text', 'ios', f'hyphens/hyphenate-character-{t}'): png(f'hyphen-ios-linebox-replay/{t}-ios-Rm.png') for t in L1_DIV0},
            {K(t): 'PASS' for t in L1_DIV0})]
    for name, f in L1_FAKES.items():
        out.append(('L1', 'css-text', name, {('css-text', 'ios', f'hyphens/hyphenate-character-{t}'): f(png(f'hyphen-ios-linebox-replay/{t}-ios-Rm.png'), t) for t in L1_DIV0},
                    {K(t): 'FAIL' for t in L1_DIV0 if not (name == 'hyph-removed' and t == '001')}))      # 001 draws no hyphen: a no-op there
    return out

# ── L2 · cbb-android (U1 fixed = the wave54-probe android capture; U2 fixed = cbb-android-replay/android-clip-3.png) ─────────
_green = lambda a: (a[..., 0] < 8) & (np.abs(a[..., 1] - 128) < 8) & (a[..., 2] < 8)
def l2_hollow(a):
    x0, y0, x1, y1 = bbox_of(_green(a)); g = _green(a); inner = np.zeros_like(g); inner[y0 + 1:y1, x0 + 1:x1] = True; o = a.copy(); o[g & inner] = 255; return o
def l2_corners2(a):
    x0, y0, x1, y1 = bbox_of(_green(a)); o = a.copy(); o[_green(a)] = 255; o[y0, x0] = o[y1, x1] = (0, 128, 0); return o
def l2_stripe(a, w=30):
    x0, y0, x1, y1 = bbox_of(_green(a)); cx = (x0 + x1) // 2; m = np.zeros(a.shape[:2], bool); m[:, cx - w // 2:cx + w // 2] = True; o = a.copy(); o[_green(a) & m] = 255; return o
def l2_short(a, n=3):
    x0, y0, x1, y1 = bbox_of(_green(a)); m = np.zeros(a.shape[:2], bool); m[:, x1 - n + 1:] = True; o = a.copy(); o[_green(a) & m] = 255; return o
def l2_gshift(a, dx, dy):
    x0, y0, x1, y1 = bbox_of(_green(a)); return shift(a, dx, dy, (x0, y0, x1 + 1, y1 + 1))
def l2_gut_cross(a):                # #inner gutted but for the two scanlines the supplementary probe once read, its outline and one corner
    g = _green(a); x0, y0, x1, y1 = bbox_of(g); keep = np.zeros_like(g)
    keep[80, :] = keep[:, 120] = keep[y0, :] = keep[y1, :] = keep[:, x0] = keep[:, x1] = True; keep[116:136, 216:236] = True
    o = a.copy(); o[g & ~keep] = 255; return o
def l2_square3(a):                  # three radius-25 corners filled square (the radius lost; the corner once counted untouched)
    x0, y0, x1, y1 = bbox_of(_green(a)); o = a.copy()
    for cx, cy in ((x0, y0), (x1 - 24, y0), (x0, y1 - 24)):
        o[cy:cy + 25, cx:cx + 25] = (0, 128, 0)
    return o
L2_U1 = [('css-flexbox', 'abspos/abspos-autopos-htb-ltr'), ('css-flexbox', 'abspos/abspos-autopos-vlr-ltr'), ('css-flexbox', 'abspos/abspos-autopos-vrl-ltr'),
         ('filter-effects', 'backdrop-filter-nested-border-radius-clip'), ('filter-effects', 'backdrop-filter-nested-border-radius-clip-2'),
         ('filter-effects', 'backdrop-filter-nested-border-radius-clip-4')]
def l2_cases():
    k1 = lambda sec, t: ('compose-wpt-content-box-cb.geometry.py', f'{t.split("/")[-1] if sec == "filter-effects" else t} android')
    c3 = ('filter-effects', 'backdrop-filter-nested-border-radius-clip-3')
    k3 = [('compose-wpt-content-box-cb.geometry.py', 'backdrop-filter-nested-border-radius-clip-3 android'), ('cbb-android.geometry.py', 'backdrop-filter-nested-border-radius-clip-3 android')]
    out = []
    for name, f in {'fixed': lambda a: a, 'hollow': l2_hollow, 'corners2': l2_corners2, 'stripe30': l2_stripe, 'shift+2x': lambda a: l2_gshift(a, 2, 0),
                    'shift-3y': lambda a: l2_gshift(a, 0, -3), 'short3': l2_short}.items():
        out.append(('L2', 'css-flexbox,filter-effects', f'U1-{name}', {(sec, 'android', t): f(cap('wave54-probe', sec, t, 'android')) for sec, t in L2_U1},
                    {k1(sec, t): 'PASS' if name == 'fixed' else 'FAIL' for sec, t in L2_U1}))
    base3 = png('cbb-android-replay/android-clip-3.png')
    for name, f in {'fixed': lambda a: a, 'gut-cross': l2_gut_cross, 'square-3-corners': l2_square3, 'shift+2x': lambda a: l2_gshift(a, 2, 0), 'stripe30': l2_stripe}.items():
        out.append(('L2', 'filter-effects', f'U2-{name}', {(c3[0], 'android', c3[1]): f(base3)}, {k: 'PASS' if name == 'fixed' else 'FAIL' for k in k3}))
    return out

# ── L3 · positioned-parent-slot (fixed = positioned-parent-slot-replay/<doc>-android-U1U2|U1|U2.png) ─────────────────────────
_ink = lambda a: a.min(axis=2) < 200
_blue = lambda a: (a[..., 2] > 150) & (a[..., 0] < 120) & (a[..., 1] < 120)
_mag = lambda a: (a[..., 0] > 200) & (a[..., 1] < 80) & (a[..., 2] > 200)
def l3_fixed(doc):
    for arm in ('U1U2', 'U1', 'U2'):
        p = os.path.join(HERE, 'positioned-parent-slot-replay', f'{doc}-android-{arm}.png')
        if os.path.exists(p):
            return A(Image.open(p))
def l3_dir_gut(a, k):               # the row keeps only its 2-px dot and one 2×2 block at its right edge
    y0 = 16 + 20 * (k - 1); m = _ink(a); band = np.zeros_like(m); band[y0:y0 + 20] = True
    ys, xs = np.nonzero(m & band); xr, yr = xs.max(), ys[xs.argmax()]; keep = np.zeros_like(m); keep[y0 + 12:y0 + 17, 15:20] = True
    o = a.copy(); o[m & band & ~keep] = 255; o[yr:yr + 2, xr - 1:xr + 1] = 0; return o
def l3_dir_mirror(a, k):            # the row's words mirrored inside their own extent (glyph order reversed; extents and dot kept)
    y0 = 16 + 20 * (k - 1); m = _ink(a); band = np.zeros_like(m); band[y0:y0 + 20] = True
    xs = np.nonzero(m & band)[1]; xs = xs[xs > 24]; o = a.copy(); o[y0:y0 + 20, xs.min():xs.max() + 1] = a[y0:y0 + 20, xs.min():xs.max() + 1][:, ::-1]; return o
def l3_bidi_gut(a):                 # each RTL run keeps only its outermost blue columns
    o = a.copy(); b = _blue(a); cols = np.arange(b.shape[1])[None, :]
    for y0 in (86, 106, 186, 226):
        band = np.zeros_like(b); band[y0:y0 + 20] = True; xs = np.nonzero(b & band)[1]
        if len(xs):
            o[b & band & ~((cols == xs.min()) | (cols == xs.max()))] = 255
    return o
def l3_bidi_shift(a, dx):
    o = a.copy()
    for y0 in (86, 106, 186, 226):
        o = shift(o, dx, 0, (0, y0, o.shape[1], y0 + 20))
    return o
L3_CELLS = ((190, 78), (360, 78), (190, 168), (360, 168))
def l3_stack(a, how):
    o = a.copy()
    if how == 'slab':
        o[78:178, 190:370] = (0, 128, 0); return o
    for x, y in L3_CELLS:
        if how == 'grown30':
            o[y:y + 30, x:x + 30] = (0, 128, 0)
        else:                                               # dup-stacked: the right squares PLUS flow-stacked duplicates 10 px lower
            o[y + 10:y + 20, x:x + 10] = (0, 128, 0)
    return o
def l3_mag(a, how, n=0):
    x0, y0, x1, y1 = bbox_of(_mag(a)); o = a.copy()
    if how == 'hollow':
        inner = np.zeros(a.shape[:2], bool); inner[y0 + 1:y1, x0 + 1:x1] = True; o[_mag(a) & inner] = 255; return o
    if how == 'wider':
        o[y0:y1 + 1, x1 + 1:x1 + 1 + n] = (255, 0, 255); return o
    return shift(a, n if how == 'x' else 0, n if how == 'y' else 0, (x0, y0, x1 + 1, y1 + 1))
def l3_cases():
    S = 'positioned-parent-slot.geometry.py'; out = []
    d = l3_fixed('dir-style-02a')
    for k in (6, 7, 9):
        for name, f in (('fixed', lambda a: a), ('gut', lambda a: l3_dir_gut(a, k)), ('mirror-words', lambda a: l3_dir_mirror(a, k)),
                        ('text+2x', lambda a: shift(a, 2, 0, (21, 16 + 20 * (k - 1), a.shape[1], 36 + 20 * (k - 1)))),
                        ('text-3x', lambda a: shift(a, -3, 0, (21, 16 + 20 * (k - 1), a.shape[1], 36 + 20 * (k - 1)))),
                        ('text+3x', lambda a: shift(a, 3, 0, (21, 16 + 20 * (k - 1), a.shape[1], 36 + 20 * (k - 1))))):
            out.append(('L3', 'selectors', f'dir-row{k}-{name}', {('selectors', 'android', 'dir-style-02a'): f(d)},
                        {(S, f'[S] dir-style-02a android row{k}'): 'PASS' if name == 'fixed' else 'FAIL'}))
    b = l3_fixed('bidi-tab-001')
    for name, f in (('fixed', lambda a: a), ('gut', l3_bidi_gut), ('rtl+2x', lambda a: l3_bidi_shift(a, 2)), ('rtl-3x', lambda a: l3_bidi_shift(a, -3))):
        out.append(('L3', 'css-text', f'bidi-{name}', {('css-text', 'android', 'bidi/bidi-tab-001'): f(b)}, {(S, '[S] bidi-tab-001 android rtl-rows'): 'PASS' if name == 'fixed' else 'FAIL'}))
    s = l3_fixed('anchor-position-multicol-nested-001')
    for name, f in (('fixed', lambda a: a), ('grown30', lambda a: l3_stack(a, 'grown30')), ('slab', lambda a: l3_stack(a, 'slab')),
                    ('dup-stacked', lambda a: l3_stack(a, 'dup')), ('shift+1x', lambda a: shift(a, 1, 0))):
        out.append(('L3', 'css-anchor-position', f'stack-{name}', {('css-anchor-position', 'android', 'anchor-position-multicol-nested-001'): f(s)},
                    {(S, '[W] anchor-position-multicol-nested-001 android stack'): 'PASS' if name == 'fixed' else 'FAIL'}))
    for doc, kind, fakes in (('dynamic-offset-rtl-001', 'S', (('hollow', lambda a: l3_mag(a, 'hollow')), ('shift+2x', lambda a: l3_mag(a, 'x', 2)))),
                             ('dynamic-offset-rtl-002', 'W', (('shift+3y', lambda a: l3_mag(a, 'y', 3)), ('shift-3y', lambda a: l3_mag(a, 'y', -3)),
                                                              ('wider+5', lambda a: l3_mag(a, 'wider', 5)), ('hollow', lambda a: l3_mag(a, 'hollow'))))):
        m = l3_fixed(doc)
        for name, f in (('fixed', lambda a: a),) + fakes:
            out.append(('L3', 'css-writing-modes', f'{doc[-3:]}-{name}', {('css-writing-modes', 'android', doc): f(m)},
                        {(S, f'[{kind}] {doc} android'): 'PASS' if name == 'fixed' else 'FAIL'}))
    return out

# ── L4 · ua-heading-margin (fixed = ua-heading-margin-replay/<test>-<plat>-C.png (U1+U2) and -U1.png (U1 alone)) ───────────
_dk = lambda a: a.sum(axis=2) < 300
L4_T = ['text-decoration-inset-005', 'text-decoration-inset-006', 'text-decoration-inset-014', 'text-decoration-inset-011']
def l4_region(a, t):
    b = bands(_dk(a), 112 if t.endswith('011') else 96, 400); n = 1 if t.endswith('011') else min(2, len(b)); return b[0][0], b[n - 1][1] + 1, b
def l4_hshift(a, t, dx, dy):
    y0, y1, _ = l4_region(a, t); return shift(a, dx, dy, (0, y0, a.shape[1], y1))
def l4_line2_removed(a, t):
    _, _, b = l4_region(a, t)
    if t.endswith('011') or len(b) < 2:
        return a
    m = _dk(a); band = np.zeros_like(m); band[b[1][0]:b[1][1] + 1] = True; o = a.copy(); o[m & band] = 255; return o
def l4_line1_cut(a, t):             # segment shortened: heading line 1 keeps its left 40 % (011: every band reduced to a 2-px frame)
    o = a.copy(); m = _dk(a)
    if t.endswith('011'):
        for top, bot, x0, x1 in bands(m, 112, 400):
            reg = np.zeros_like(m); reg[top:bot + 1, x0:x1 + 1] = True; o[m & reg] = 255; o[top, x0] = o[bot, x1] = 0
        return o
    _, _, b = l4_region(a, t); top, bot, x0, x1 = b[0]; band = np.zeros_like(m); band[top:bot + 1, x0 + int((x1 - x0) * 0.4):] = True; o[m & band] = 255; return o
def l4_cases():
    S = 'ua-heading-margin.geometry.py'; out = []
    M = [(S, f'[M] {t} {p}') for t in L4_T for p in ('ios', 'android')]
    F = [(S, f'[F] {t} ios') for t in L4_T[:3]] + [(S, '[F] text-decoration-inset-014 android')]
    fakes = {'fixed': lambda a, t: a, 'heading+3y': lambda a, t: l4_hshift(a, t, 0, 3), 'heading-2y': lambda a, t: l4_hshift(a, t, 0, -2),
             'heading+12x': lambda a, t: l4_hshift(a, t, 12, 0), 'line2-removed': l4_line2_removed, 'line1-truncated': l4_line1_cut}
    for arm in ('C', 'U1'):
        for name, f in fakes.items():
            repl = {('css-text-decor', p, t): f(png(f'ua-heading-margin-replay/{t}-{p}-{arm}.png'), t) for t in L4_T for p in ('ios', 'android')}
            want = {}
            for k in M + F:
                t, p = k[1].split()[1], k[1].split()[2]
                if name == 'fixed':
                    want[k] = 'PASS' if (arm == 'C' or k[1].startswith('[M]')) else 'FAIL'      # under U1 alone the face keys stay WRONG
                elif name == 'line2-removed' and (t.endswith('011') or (k[1].startswith('[M]') and (arm == 'U1' and not t.endswith('014') or p == 'android' and t[-3:] in ('005', '006')))):
                    continue        # a no-op (011; one band under U1), or the margin key of a heading Compose does not wrap (band 1 only, by design)
                else:
                    want[k] = 'FAIL'
            out.append(('L4', 'css-text-decor', f'{arm}-{name}', repl, want))
    return out

# ── L5 · counter-suffix-rows (fixed = counter-suffix-rows-replay/ok-all/) ────────────────────────────────────────────────────
_d5 = lambda a: a.sum(axis=2) < 300
L5_ROW = {k: (16 + 24 * (k - 1), 16 + 24 * k) for k in range(1, 13)}
def l5_blobs(a, x0, x1, y0, y1):
    cols = np.nonzero(_d5(a[y0:y1, x0:x1]).any(axis=0))[0] + x0; out = []
    for x in cols:
        if out and x == out[-1][1] + 1:
            out[-1][1] = x
        else:
            out.append([x, x])
    return out
def l5_erase(a, x0, x1, y0, y1):
    o = a.copy(); m = a.min(axis=2) < 250; reg = np.zeros_like(m); reg[y0:y1, x0:x1 + 1] = True; o[m & reg] = 255; return o
def l5_block(a, x0, x1, y0, y1):    # a blob replaced by a solid block of its own bbox
    o = a.copy(); ys = np.nonzero(_d5(a[y0:y1, x0:x1 + 1]).any(axis=1))[0] + y0; o[ys.min():ys.max() + 1, x0:x1 + 1] = 0; return o
def l5_i_comma(a):
    for y0, y1 in ((95, 121), (121, 152)):
        mk = [b for b in l5_blobs(a, 200, 330, y0, y1) if b[0] < 255]
        if len(mk) >= 2:
            a = l5_erase(a, mk[-1][0] - 1, mk[-1][1] + 1, y0, y1)
    return a
def l5_i_block(a):
    for y0, y1 in ((95, 121), (121, 152)):
        bl = l5_blobs(a, 200, 330, y0, y1)
        if bl:
            a = l5_block(a, bl[0][0], bl[0][1], y0, y1)
    return a
def l5_t_bar(a):
    y0, y1 = L5_ROW[9]; bl = l5_blobs(a, 136, 151, y0, y1); ys = np.nonzero(_d5(a[y0:y1, 136:151]).any(axis=1))[0] + y0
    o = l5_erase(a, 136, 150, y0, y1); o[ys.min():ys.max() + 1, bl[0][0]:bl[0][0] + 7] = 0; return o
def l5_c_gut(a):
    for k in (5, 6):
        y0, y1 = L5_ROW[k]; bl = l5_blobs(a, 20, 60, y0, y1)
        if len(bl) >= 2:
            a = l5_erase(a, bl[0][0] - 1, bl[-1][0] - 2, y0, y1); a[(y0 + y1) // 2, bl[0][0]] = 0
    return a
def l5_h_block(a):
    for k in (3, 4):
        y0, y1 = L5_ROW[k]; L = [b for b in l5_blobs(a, 30, 64, y0, y1) if b[1] - b[0] >= 4]
        if L:
            a = l5_block(a, L[0][0], L[0][1], y0, y1)
    return a
def l5_h_text_gone(a):
    for k in (3, 4):
        a = l5_erase(a, 64, 200, *L5_ROW[k])
    return a
def l5_cases():
    S = 'counter-suffix-rows.geometry.py'; OK = 'counter-suffix-rows-replay/ok-all'
    G = {'H': [('[H] counter-suffix ios',)], 'C': [('[C] counter-suffix ios',), ('[C] counter-suffix android',)], 'T': [('[T] counter-suffix ios',)],
         'I': [('[I] cjk-decimal/css3-counter-styles-005 ios',), ('[I] cjk-decimal/css3-counter-styles-005 android',)]}
    gating = [(S, k[0]) for v in G.values() for k in v]
    tally_k = [(S, '[K] counter-suffix ios'), (S, '[K] counter-suffix android')]          # CONTROL keys the tally entries #12-#13 require
    fakes = {'fixed': ({}, {k: 'PASS' for k in gating + tally_k}), 'I-comma-removed': ({'005': l5_i_comma}, {(S, k[0]): 'FAIL' for k in G['I']}),
             'I-marker-block': ({'005': l5_i_block}, {(S, k[0]): 'FAIL' for k in G['I']}),
             'T-1-down3': ({'cs': lambda a: shift(a, 0, 3, (136, L5_ROW[9][0], 151, L5_ROW[9][1] - 3))}, {(S, '[T] counter-suffix ios'): 'FAIL'}),
             'T-1-bar7': ({'cs': l5_t_bar}, {(S, '[T] counter-suffix ios'): 'FAIL'}),
             'C-ideograph-gutted': ({'cs': l5_c_gut}, {(S, k[0]): 'FAIL' for k in G['C']}),
             'H-letter-block': ({'cs': l5_h_block}, {(S, '[H] counter-suffix ios'): 'FAIL'}),
             'H-item-text-gone': ({'cs': l5_h_text_gone}, {k: 'FAIL' for k in tally_k})}
    out = []
    for name, (fs, want) in fakes.items():
        repl = {}
        for p in ('web', 'ios', 'android'):
            cs, c5 = png(f'{OK}/counter-suffix.{p}.png'), png(f'{OK}/cjk-decimal__css3-counter-styles-005.{p}.png')
            if p != 'web':
                cs, c5 = fs.get('cs', lambda a: a)(cs), fs.get('005', lambda a: a)(c5)
            repl[('css-counter-styles', p, 'counter-suffix')] = cs; repl[('css-counter-styles', p, 'cjk-decimal/css3-counter-styles-005')] = c5
        out.append(('L5', 'css-counter-styles', name, repl, want))
    return out

# ── L6 · android-paint-order ([X] fixed = android-paint-order-replay/<flat>-android-U2.png; [PO] = …-clip-rect-android-U1.png) ──
_teal = lambda a: (a[..., 0] <= 40) & (a[..., 1] >= 100) & (a[..., 1] <= 160) & (a[..., 2] >= 100) & (a[..., 2] <= 160)
_yellow = lambda a: (a[..., 0] >= 200) & (a[..., 1] >= 200) & (a[..., 2] <= 80)
_dark6 = lambda a: a.sum(axis=2) < 150
_red = lambda a: (a[..., 0] >= 200) & (a[..., 1] <= 60) & (a[..., 2] <= 60)
_blue6 = lambda a: (a[..., 2] >= 200) & (a[..., 0] <= 60) & (a[..., 1] <= 60)
L6_X = [('css-flexbox', 'abspos/flex-abspos-staticpos-align-self-safe-001', _yellow), ('css-flexbox', 'abspos/flex-abspos-staticpos-align-self-safe-002', _yellow),
        ('css-grid', 'abspos/grid-abspos-staticpos-align-self-001', _teal), ('css-grid', 'abspos/grid-abspos-staticpos-justify-self-001', _teal),
        ('css-grid', 'abspos/grid-abspos-staticpos-align-self-vertWM-001', _teal), ('css-grid', 'abspos/grid-abspos-staticpos-align-self-rtl-001', _teal)]
def l6_scramble(a, fill):           # count-preserving: the child fill moved to the canvas corner
    m = fill(a); n = int(m.sum()); col = a[m][0]; o = a.copy(); o[m] = 255; side = int(np.ceil(np.sqrt(n))); flat = o.reshape(-1, 3)
    flat[[y * a.shape[1] + x for y in range(side) for x in range(side)][:n]] = col; return flat.reshape(a.shape)
def l6_shrunk(a, fill, frac=0.08):
    m = fill(a); ys = np.nonzero(m)[0]; cut = ys.max() - int((ys.max() - ys.min() + 1) * frac); mm = m.copy(); mm[:cut + 1] = False; o = a.copy(); o[mm] = 255; return o
def l6_navbar_sides(a):             # the navbar band erased except where the old rule sampled it
    b = _blue6(a); keep = np.zeros_like(b)
    for y in (166, 167, 218, 219):
        keep[y, 120:160] = keep[y, 270:310] = keep[y, 163:267] = True
    o = a.copy(); o[b & ~keep] = 255; return o
def l6_cases():
    S = 'android-paint-order.geometry.py'; out = []
    fx = lambda t: png(f'android-paint-order-replay/{t.replace("/", "__")}-android-U2.png')
    for name, f in (('fixed', lambda a, fl: a), ('border-erased', lambda a, fl: np.where(_dark6(a)[..., None], 255, a)),
                    ('child+3x', lambda a, fl: shift(a, 3, 0, (lambda b: (b[0], b[1], b[2] + 1, b[3] + 1))(bbox_of(fl(a))))),
                    ('child-3y', lambda a, fl: shift(a, 0, -3, (lambda b: (b[0], b[1], b[2] + 1, b[3] + 1))(bbox_of(fl(a))))),
                    ('child-shrunk8%', l6_shrunk), ('child-scrambled', l6_scramble)):
        want = {(S, f'[X] {t.split("/")[-1]} android'): 'PASS' if name == 'fixed' else 'FAIL' for sec, t, fl in L6_X}
        if name == 'fixed':          # safe-002's own replay keeps 50 crossing px (a replay artifact; its row's lower bound): known, not a pass
            want[(S, '[X] flex-abspos-staticpos-align-self-safe-002 android')] = 'FAIL'
        out.append(('L6', 'css-flexbox,css-grid', f'X-{name}', {(sec, 'android', t): f(fx(t), fl) for sec, t, fl in L6_X}, want))
    po = png('android-paint-order-replay/backdrop-filter-clip-rect-android-U1.png')
    for name, f in (('fixed', lambda a: a), ('navbar-sides-erased', l6_navbar_sides),
                    ('menus+1y', lambda a: shift(a, 0, 1, (lambda b: (b[0], b[1], b[2] + 1, b[3] + 1))(bbox_of(_red(a))))),
                    ('menus+2y', lambda a: shift(a, 0, 2, (lambda b: (b[0], b[1], b[2] + 1, b[3] + 1))(bbox_of(_red(a)))))):
        out.append(('L6', 'filter-effects', f'PO-{name}', {('filter-effects', 'android', 'backdrop-filter-clip-rect'): f(po)},
                    {(S, '[PO] backdrop-filter-clip-rect android'): 'PASS' if name == 'fixed' else 'FAIL'}))
    # (a 1-px WHOLE-picture shift passes [PO]: its bbox reads ±1 by design — not a case)
    return out

def _self():
    """This module as an object (it is loaded under importlib by probes-selfcheck.py, so sys.modules may not name it)."""
    import types
    m = types.SimpleNamespace(**{k: v for k, v in globals().items() if not k.startswith('__')})
    return m


# ── the fake runs and the real gate ───────────────────────────────────────────────────────────────────────────────────────
def make_run(scratch, fid, repl):
    """A symlink tree of wave54-final with the faked captures written in; returns its run id (relative to tools/titan/runs)."""
    rd = os.path.join(scratch, 'fakeruns', fid)
    assert not (os.path.realpath(rd) + '/').startswith(ROOT + '/'), 'fake runs live outside the repository'
    if os.path.lexists(rd):
        shutil.rmtree(rd)
    os.makedirs(f'{rd}/sections')
    touched = {}
    for (sec, plat, test), a in repl.items():
        touched.setdefault(sec, {}).setdefault(DIRS[plat], {})[cap_name(sec, test)] = a
    for sec in sorted(os.listdir(f'{W54}/sections')):
        src = f'{W54}/sections/{sec}'
        if sec not in touched:
            os.symlink(src, f'{rd}/sections/{sec}'); continue
        os.makedirs(f'{rd}/sections/{sec}')
        for sub in sorted(os.listdir(src)):
            if sub not in touched[sec]:
                os.symlink(f'{src}/{sub}', f'{rd}/sections/{sec}/{sub}'); continue
            os.makedirs(f'{rd}/sections/{sec}/{sub}')
            for f in sorted(os.listdir(f'{src}/{sub}')):
                if f not in touched[sec][sub]:
                    os.symlink(f'{src}/{sub}/{f}', f'{rd}/sections/{sec}/{sub}/{f}')
            for f, a in touched[sec][sub].items():
                I(a).save(f'{rd}/sections/{sec}/{sub}/{f}')
    # relative to the REAL runs dir: in a throwaway worktree tools/titan/runs is a symlink, and the kernel resolves `..` from its target
    return os.path.relpath(os.path.realpath(rd), os.path.realpath(RUNS))


def gate(run_rel, lanes, sections, exp_path, py, out_json):
    """The real geometry-gate.py on the fake run (base wave54-final = wave55-open byte-identical): (exit, {(script, key): row})."""
    p = subprocess.run(['nice', '-n', '19'] + py + [os.path.join(HERE, 'geometry-gate.py'), run_rel, '--base', 'wave54-final', '--lanes', lanes,
                        '--sections', sections, '--json', out_json, '--exp', exp_path], cwd=ROOT, capture_output=True, text=True)
    rows = json.load(open(out_json))['rows'] if os.path.exists(out_json) else []
    return p.returncode, {(r['script'], r['key']): r for r in rows}


def run_all(scratch, exp_path, py, say, lanes=None, fixed_ok=None):
    """Every case (of `lanes`, default all) through the real gate. Returns (violations, {key: fakes rejected}, the keys that must
    reject a fake — every gating key plus (round 2) every pictureCorrectTally key — and the number of cases). Round 3 (verify N3):
    `fixed_ok`, a dict, receives per key the number of pre-registered PASS cases (its fixed pictures) it printed PASS on."""
    exp = json.load(open(exp_path))
    gating = {(os.path.basename(next(t for t in pr['cmd'].split() if t.endswith('.py'))), k)
              for lid, l in exp['lanes'].items() if not lanes or lid.split('-')[0] in lanes
              for pr in l['geometryProbes'] for k in pr.get('gating', {})}
    viol, covered, n = [], {}, 0
    builders = {'L1': l1_cases, 'L2': l2_cases, 'L3': l3_cases, 'L4': l4_cases, 'L5': l5_cases, 'L6': l6_cases}
    import importlib.util                               # round 2 (M6): the round-2 cases, appended per lane
    _spec = importlib.util.spec_from_file_location('probes_teeth_r2', os.path.join(HERE, 'probes-teeth-r2.py'))
    r2 = importlib.util.module_from_spec(_spec); _spec.loader.exec_module(r2)
    _spec3 = importlib.util.spec_from_file_location('probes_teeth_r3', os.path.join(HERE, 'probes-teeth-r3.py'))   # round 3 (M2)
    r3 = importlib.util.module_from_spec(_spec3); _spec3.loader.exec_module(r3)
    _spec4 = importlib.util.spec_from_file_location('probes_teeth_r4', os.path.join(HERE, 'probes-teeth-r4.py'))   # round 4 (M1-M4)
    r4 = importlib.util.module_from_spec(_spec4); _spec4.loader.exec_module(r4)
    pt = _self()
    # Round 2 (M6): every pictureCorrectTally key (gating or not) must reject at least one plausible fake too.
    key_lane = {(os.path.basename(next(t for t in pr['cmd'].split() if t.endswith('.py'))), k): lid.split('-')[0]
                for lid, l in exp['lanes'].items() for pr in l['geometryProbes'] for k in pr['expect']}
    tally = {tuple(k.split(' ', 1)) for part in ('full', 'part', 'paintOrderOnly') for e in exp.get('pictureCorrectTally', {}).get(part, [])
             for k in e['keys']}
    gating = gating | {k for k in tally if not lanes or key_lane.get(k) in lanes}
    for lane, secs, fid, repl, want in [c for L, b in builders.items() if not lanes or L in lanes for c in b() + r2.cases(pt, L) + r3.cases(pt, L) + r4.cases(pt, L)]:
        n += 1
        rid = make_run(scratch, f'{lane}-{fid}', repl)
        rc, rows = gate(rid, lane, secs, exp_path, py, os.path.join(scratch, f'gate-{lane}-{fid}.json'))
        got = []
        for k, w in sorted(want.items()):
            r = rows.get(k); v = r['verdict'] if r else 'NO ROW'
            got.append(f'{k[1]}={v}')
            if k in gating and w == 'FAIL' and v == 'FAIL':
                covered[k] = covered.get(k, 0) + 1
            if fixed_ok is not None and w == 'PASS' and v == 'PASS':
                fixed_ok[k] = fixed_ok.get(k, 0) + 1
            if v != w:
                viol.append(f'{lane} {fid}: {k[0]} {k[1]} printed {v}, pre-registered {w}' + (f' — {r["line"][-150:]}' if r else ''))
        say(f'  {lane} {fid:28} gate exit {rc}: ' + ('as pre-registered' if all(rows.get(k, {}).get('verdict') == w for k, w in want.items()) else 'VIOLATION')
            + f' ({len(want)} key(s))')
    return viol, covered, gating, n


if __name__ == '__main__':
    import sys
    sc = sys.argv[sys.argv.index('--scratch') + 1]
    exp_p = sys.argv[sys.argv.index('--exp') + 1] if '--exp' in sys.argv else os.path.join(HERE, 'expectations.json')
    ln = sys.argv[sys.argv.index('--lanes') + 1].split(',') if '--lanes' in sys.argv else None
    v, c, g, n = run_all(sc, exp_p, ['/opt/homebrew/bin/python3.12', '-B'], print, ln)
    print(f'{n} cases; violations {len(v)}; gating + tally keys with ≥ 1 rejected fake {len(c)} / {len(g)}')
    for x in v:
        print('  VIOLATION', x)
    for k in sorted(g - set(c)):
        print('  NO FAKE REJECTED', k)
    sys.exit(1 if v or (g - set(c)) else 0)
