#!/usr/bin/env python3
# tools/titan/results/wave55-plan/residual-read.py — the RESIDUAL read every gating and pictureCorrectTally key carries
# (plan-skeptic round 5 verify M1-M6; fix-r6.done.md; PLAN §9 D40). Imported by geometry-gate.py (never by a probe: the
# wave-53 / wave-54 tally probes are outside this plan's edits, and one read in the gate covers every key at once).
#
# Why: rounds 1-4 taught each probe to read its OWN colour / level inside its OWN rows. Round 5's verifier painted where no
# read of a key looks — a 50 % copy of the class ink (not strict green / not dark), black / #66ccff / #cccccc / yellow / iOS
# grey ink in a colour the masks never test, a line in rows no XOR covers — and 704 fakes on 33 of 43 gating keys and 17
# tally entries passed, every one on BACKGROUND-ONLY paint. The class is closed here generically, per key cell:
#   ZONE      the key's DECLARED change zone: Z_C on the capture, Z_B on the --base capture, each the union of
#               ('mask', [classes], grow)   the pixels of those colour classes, dilated by `grow` px (the probe's own
#                                           predicates: a colour its probe reads pixel for pixel — strict green, magenta, …);
#               ('bands', pred, box, pad, gap) the probe's own row bands of `pred` inside box (blank runs > gap split),
#                                           every band's rows ±pad across the box's columns — the rows the probe XORs;
#               ('rect', box)               a recorded rectangle (in Z_C only), and
#             SPEC['windows']: recorded AA / change windows — the bboxes (+1 px) of the clusters of the fixed pictures' own
#             diff against wave54-final that the class zones leave out (corner arcs, the '0' glyphs, clip-3's grown #middle;
#             derived and re-measured by fix-r6.zones.py → fix-r6.zones.out.txt, never typed by hand).
#   READS     with d = (capture ≠ base), pixel for pixel, all at tolerance 0 (every fixed picture reads 0 / 0 / 0):
#     outside   d outside Z_C ∪ Z_B ∪ windows — paint ANYWHERE else (a faint copy, an off-colour speck, a line in an unread
#               row) fails every key of the cell at once;
#     vacated   d inside Z_B \ (Z_C ∪ windows) whose capture colour is not background / a declared `vacate` class, or that
#               touches (8-neighbour) a 'mask' class pixel present at the same place on BOTH pictures — where the base's class
#               ink LEFT only the background (or the parent fill it uncovers) may show, and a shape that did not move cannot
#               lose pixels to it (a hole);
#     non-class d inside Z_C ∪ windows whose capture colour is neither background nor a declared `ink` class, nor (where the
#               spec declares it) an anti-aliasing colour: 'neutral' (grey ramp: black text), 'ramp' (white → an ink
#               colour: coloured text), and inside the windows only a flat palette colour, 'box' (a pixel bordered by ≥ 2
#               distinct palette classes, inside the per-channel box of the palette present within 2 px on either picture:
#               an arc's AA) or 'segment' (within L1 SEG_TOL of the segment between two palette colours: a glyph's AA over
#               yellow / white); and a window holds ONE connected change (every fixed picture's does) — a second component
#               in it is paint the unit does not draw.
#   Inside the zone the probe's own class reads stay; the residual only ever turns a PASS into a FAIL.
# A gating / tally key whose cell has no SPEC is a refusal in geometry-gate.py (exit 2), so a new key cannot skip the read.
# The 2-px floor (D39) is unchanged: a fake adding ≤ 2 px of class-consistent ink INSIDE a zone is still the probe's ±2.
import os
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
DIRS = {'web': 'screenshots', 'ios': 'ios-screenshots', 'android': 'android-screenshots'}
NEUTRAL_TOL, RAMP_TOL, SEG_TOL, BOX_TOL, FLAT_TOL = 2, 3, 3, 14, 10   # measured minima 0 / 0 / 0 / 14 (fix-r6.zones.out.txt §4) + one rounding step; FLAT = the cbb probe's ±10


def capture_path(run, cell):
    """'<section>/<test>.html <plat>' → the run's composed capture (section-runner layout)."""
    page, plat = cell.rsplit(' ', 1)
    sec, test = page.split('/', 1)
    flat = test[:-5].replace('/', '__') if test.endswith('.html') else test.replace('/', '__')
    return os.path.join(ROOT, 'tools', 'titan', 'runs', run, 'sections', sec, DIRS[plat], f'wpt__{sec}__{flat}.png')


def rgb(path):
    """int16 [y, x, 3] of a capture, or None when it is missing."""
    return np.asarray(Image.open(path).convert('RGB')).astype(np.int16) if os.path.exists(path) else None


near = lambda c, t=FLAT_TOL: (lambda a: (np.abs(a - np.array(c, np.int16)) <= t).all(axis=2))
# The probes' own colour / level predicates (named after the probe that reads them pixel for pixel).
PRED = {
    'white': lambda a: (a == 255).all(axis=2),                                                        # the canvas
    'green': lambda a: (a[..., 0] < 8) & (np.abs(a[..., 1] - 128) < 8) & (a[..., 2] < 8),           # L2 strict green
    'red': lambda a: (a[..., 0] > 200) & (a[..., 1] < 80) & (a[..., 2] < 80),                       # L2 fail ink (red)
    'pink': lambda a: (a[..., 0] > 240) & (np.abs(a[..., 1] - 127) < 12) & (a[..., 2] > 240),       # L2 fail ink (backdrop pink)
    'blue66': near((102, 204, 255)), 'grey204': near((204, 204, 204)), 'yellowFF': near((255, 255, 0)),   # cbb run colours
    'green3': lambda a: (a[..., 0] < 60) & (a[..., 1] > 100) & (a[..., 2] < 60),                    # L3 stack squares
    'magenta': lambda a: (a[..., 0] > 200) & (a[..., 1] < 80) & (a[..., 2] > 200),                  # L3 dyn child
    'yellow3': lambda a: (a[..., 0] > 200) & (a[..., 1] > 200) & (a[..., 2] < 80),                  # L3 yellow parents / boxes
    'blue3': lambda a: (a[..., 2] > 150) & (a[..., 0] < 120) & (a[..., 1] < 120),                   # L3 bidi runs
    'teal': lambda a: (a[..., 0] <= 40) & (a[..., 1] >= 100) & (a[..., 1] <= 160) & (a[..., 2] >= 100) & (a[..., 2] <= 160),
    'yellow6': lambda a: (a[..., 0] >= 200) & (a[..., 1] >= 200) & (a[..., 2] <= 80),               # L6 [X] fills
    'red6': lambda a: (a[..., 0] >= 200) & (a[..., 1] <= 60) & (a[..., 2] <= 60),                   # L6 [PO] menus
    'core': lambda a: a.max(axis=2) < 110,                                                            # L1 line segmentation
    'dark': lambda a: a.sum(axis=2) < 300,                                                            # L4 band segmentation
    'neutral': lambda a: (a.max(axis=2) - a.min(axis=2)) <= NEUTRAL_TOL,                              # a grey (black text AA)
}
PALETTE = {'white': (255, 255, 255), 'green': (0, 128, 0), 'pink': (255, 127, 255), 'red': (255, 0, 0), 'blue66': (102, 204, 255),
           'grey204': (204, 204, 204), 'yellowFF': (255, 255, 0), 'black': (0, 0, 0), 'blueFF': (0, 0, 255), 'lime': (0, 255, 0)}

# ── the per-cell declarations ─────────────────────────────────────────────────────────────────────────────────────────────
def _l1(y0):            # L1: the probe's lines (core, x < 140, gap 2) with the crop rows −3 … +3 its two-level XOR reads
    return {'zone': [('bands', 'core', (0, y0, 140, None), 3, 2)], 'ink': [], 'aa': 'neutral'}


def _l4(y0):            # L4: the probe's dark bands (sum < 300, gap 2) from y96 (011: y112), rows ±2 (face xor's own rows)
    return {'zone': [('bands', 'dark', (0, y0, None, 400), 2, 2)], 'ink': [], 'aa': 'neutral'}


_CLIP_PAL = ['white', 'green', 'pink', 'blue66', 'grey204', 'yellowFF']
SPECS = {}
for _t, _y in (('001', 100), ('003', 80), ('004', 80)):
    SPECS[f'css-text/hyphens/hyphenate-character-{_t}.html ios'] = _l1(_y)
for _m in ('htb', 'vlr', 'vrl'):
    SPECS[f'css-flexbox/abspos/abspos-autopos-{_m}-ltr.html android'] = {'zone': [('mask', ['green', 'red'], 0)], 'ink': ['green']}
for _s in ('', '-2', '-3', '-4'):
    SPECS[f'filter-effects/backdrop-filter-nested-border-radius-clip{_s}.html android'] = {
        'zone': [('mask', ['green', 'pink'], 0)], 'ink': ['green'], 'aa': 'box', 'palette': _CLIP_PAL, 'windows': []}
SPECS['selectors/dir-style-02a.html android'] = {           # the three moved rows' 20-px bands (the probe's row reads)
    'zone': [('rect', (0, 116, 390, 136)), ('rect', (0, 136, 390, 156)), ('rect', (0, 176, 390, 196))], 'ink': [], 'aa': 'ramp',
    'palette': ['lime', 'blueFF']}
SPECS['css-text/bidi/bidi-tab-001.html android'] = {
    'zone': [('mask', ['blue3'], 0)], 'ink': ['blue3', 'yellow3'], 'vacate': ['yellow3'], 'aa': 'segment',
    'palette': ['white', 'yellowFF', 'blueFF', 'black'], 'windows': []}
SPECS['css-anchor-position/anchor-position-multicol-nested-001.html android'] = {'zone': [('mask', ['green3'], 0)], 'ink': ['green3']}
for _d in ('rtl-001', 'rtl-002', 'vrl-rtl-001'):
    SPECS[f'css-writing-modes/dynamic-offset-{_d}.html android'] = {'zone': [('mask', ['magenta'], 0)], 'ink': ['magenta']}
for _t in ('005', '006', '014', '011'):
    for _p in ('ios', 'android'):
        SPECS[f'css-text-decor/text-decoration-inset-{_t}.html {_p}'] = _l4(112 if _t == '011' else 96)
SPECS['css-counter-styles/counter-suffix.html ios'] = {      # [H] rows 3-4 / [C] rows 5-6 marker zones, [T] row 9's digit window,
    # and (pre-landing regeneration, L5 fix-r1 R3) U3-ios's five other tabular run boxes — the periods of rows 9-12 (x130-135)
    # and row 10's '2' (x136-150): Inter's `tnum` is run-wide (wave55-counter-suffix-rows/fix-r1.u3-boxes.measure.out.txt)
    'zone': [('rect', (0, 64, 64, 112)), ('rect', (0, 112, 64, 160)), ('rect', (136, 208, 151, 232)),
             ('rect', (130, 208, 136, 304)), ('rect', (136, 232, 151, 256))], 'ink': [], 'aa': 'neutral'}
SPECS['css-counter-styles/counter-suffix.html android'] = {'zone': [('rect', (0, 112, 64, 160))], 'ink': [], 'aa': 'neutral'}
for _p in ('ios', 'android'):                                # [I] 005's line windows
    SPECS[f'css-counter-styles/cjk-decimal/css3-counter-styles-005.html {_p}'] = {'zone': [('rect', (200, 95, 390, 152))], 'ink': [], 'aa': 'neutral'}
for _n, _f in (('css-flexbox/abspos/flex-abspos-staticpos-align-self-safe-001', 'yellow6'), ('css-flexbox/abspos/flex-abspos-staticpos-align-self-safe-002', 'yellow6'),
               ('css-grid/abspos/grid-abspos-staticpos-align-self-001', 'teal'), ('css-grid/abspos/grid-abspos-staticpos-justify-self-001', 'teal'),
               ('css-grid/abspos/grid-abspos-staticpos-align-self-vertWM-001', 'teal'), ('css-grid/abspos/grid-abspos-staticpos-align-self-rtl-001', 'teal')):
    SPECS[f'{_n}.html android'] = {'zone': [('mask', [_f], 0)], 'ink': [_f]}
SPECS['filter-effects/backdrop-filter-clip-rect.html android'] = {'zone': [('mask', ['red6'], 0)], 'ink': ['red6']}

# The recorded windows (fix-r6.zones.py → fix-r6.zones.out.txt: the clusters of each cell's fixed-picture diff the class zones
# leave out, bbox + 1 px; the record re-measures every fixed picture at 0 / 0 / 0 with them).
# WINDOWS_BEGIN
WINDOWS = {
    'css-text/bidi/bidi-tab-001.html android': [(26, 85, 39, 107), (26, 185, 39, 207), (26, 225, 39, 247), (112, 109, 124, 127), (125, 85, 138, 107), (125, 185, 138, 207), (125, 225, 138, 247), (209, 109, 221, 127)],
    'filter-effects/backdrop-filter-nested-border-radius-clip-2.html android': [(25, 25, 44, 44), (25, 108, 44, 127), (208, 108, 227, 127)],
    'filter-effects/backdrop-filter-nested-border-radius-clip-3.html android': [(15, 111, 61, 137), (211, 15, 237, 61), (211, 111, 237, 137)],
    'filter-effects/backdrop-filter-nested-border-radius-clip-4.html android': [(25, 26, 43, 51), (25, 96, 36, 107), (176, 96, 187, 107)],
    'filter-effects/backdrop-filter-nested-border-radius-clip.html android': [(25, 25, 47, 51), (25, 101, 51, 127), (201, 105, 227, 127)],
}
# WINDOWS_END
for _c, _w in WINDOWS.items():
    SPECS[_c]['windows'] = [tuple(b) for b in _w]


# ── the read ──────────────────────────────────────────────────────────────────────────────────────────────────────────────
def grow(m, g):
    """8-neighbour dilation by g px."""
    o = m.copy()
    for _ in range(g):
        p = o.copy(); p[1:] |= o[:-1]; p[:-1] |= o[1:]; p[:, 1:] |= o[:, :-1]; p[:, :-1] |= o[:, 1:]
        p[1:, 1:] |= o[:-1, :-1]; p[1:, :-1] |= o[:-1, 1:]; p[:-1, 1:] |= o[1:, :-1]; p[:-1, :-1] |= o[1:, 1:]; o = p
    return o


def row_bands(m, gap):
    """[(top, bottom)] of the rows of m holding a True pixel, blank runs > gap rows splitting (geometry_common.bands)."""
    rows, out, cur, blank = m.any(axis=1), [], None, 0
    for y in range(m.shape[0]):
        if rows[y]:
            cur = [y, y] if cur is None else [cur[0], y]; blank = 0
        elif cur is not None:
            blank += 1
            if blank > gap:
                out.append(cur); cur = None; blank = 0
    return out + ([cur] if cur is not None else [])


def zone(a, comps, with_rects):
    """The declared zone of one picture (rects only on the capture side: they are change zones, not ink the base had)."""
    H, W, _ = a.shape
    z = np.zeros((H, W), bool)
    for c in comps:
        if c[0] == 'mask':
            m = np.zeros((H, W), bool)
            for n in c[1]:
                m |= PRED[n](a)
            z |= grow(m, c[2])
        elif c[0] == 'bands':
            _, pred, (x0, y0, x1, y1), pad, gap = c
            x1, y1 = W if x1 is None else min(W, x1), H if y1 is None else min(H, y1)
            m = np.zeros((H, W), bool); m[y0:y1, x0:x1] = PRED[pred](a)[y0:y1, x0:x1]
            for t, b in row_bands(m, gap):
                z[max(0, t - pad):min(H, b + pad + 1), x0:x1] = True
        elif c[0] == 'rect' and with_rects:
            x0, y0, x1, y1 = c[1]; z[y0:y1, x0:x1] = True
    return z


def _classed(a, names):
    m = np.zeros(a.shape[:2], bool)
    for n in names:
        m |= PRED[n](a)
    return m


def _flat(a, names):
    m = np.zeros(a.shape[:2], bool)
    for n in names:
        m |= near(PALETTE[n])(a)
    return m


def _box_aa(a, b, names):
    """Round 6: an anti-aliased ARC pixel — bordered by >= 2 distinct flat palette classes among its 8 neighbours on the capture,
    its colour inside the per-channel box (+-BOX_TOL) of every palette class present within 2 px of it on the capture OR the
    base. The base side matters: the L2 arcs blend green with the backdrop pink they cover (Android's clip AA mixes the hidden
    layer: (135,138,155) beside blue + green only), so the box is the local palette of BOTH pictures; black / dark / off-hue
    paint stays outside it."""
    H, W, _ = a.shape
    lab = np.full((H, W), -1, np.int16)
    for i, n in enumerate(names):
        lab[near(PALETTE[n])(a)] = i
    p = np.pad(lab, 1, constant_values=-1)
    pres = np.zeros((len(names), H, W), bool)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            if dy or dx:
                nb = p[1 + dy:1 + dy + H, 1 + dx:1 + dx + W]
                for i in range(len(names)):
                    pres[i] |= nb == i
    cols = np.array([PALETTE[n] for n in names], np.int16)
    lo = np.full((H, W, 3), 999, np.int16); hi = np.full((H, W, 3), -999, np.int16)
    for i, n in enumerate(names):
        sel = grow(near(PALETTE[n])(a) | near(PALETTE[n])(b), 2)
        lo[sel] = np.minimum(lo[sel], cols[i]); hi[sel] = np.maximum(hi[sel], cols[i])
    inbox = ((a >= lo - BOX_TOL) & (a <= hi + BOX_TOL)).all(axis=2)
    return (pres.sum(axis=0) >= 2) & inbox


def _segment(a, names, tol):
    """A colour within L1 ≤ tol of the segment between two palette colours (incl. a colour and white): an AA blend."""
    ok = np.zeros(a.shape[:2], bool)
    cols = [np.array(PALETTE[n], float) for n in names]
    af = a.astype(float)
    for i in range(len(cols)):
        for j in range(i + 1, len(cols)):
            p, d = cols[i], cols[j] - cols[i]
            t = np.clip(((af - p) * d).sum(axis=2) / (d * d).sum(), 0.0, 1.0)
            ok |= np.abs(af - (p + t[..., None] * d)).sum(axis=2) <= tol
    return ok


def _persistent(C, B, comps):
    """The 'mask' classes' ink present on BOTH pictures at the same pixel: the part of a class shape that did not move."""
    m = np.zeros(C.shape[:2], bool)
    for c in comps:
        if c[0] == 'mask':
            for n in c[1]:
                m |= PRED[n](C) & PRED[n](B)
    return m


def _components(m):
    """8-connected component labels of a bool mask (0 = background) and the component count."""
    lab = np.zeros(m.shape, np.int32); n = 0; H, W = m.shape
    for y0, x0 in zip(*np.nonzero(m)):
        if lab[y0, x0]:
            continue
        n += 1; st = [(y0, x0)]; lab[y0, x0] = n
        while st:
            y, x = st.pop()
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    yy, xx = y + dy, x + dx
                    if 0 <= yy < H and 0 <= xx < W and m[yy, xx] and not lab[yy, xx]:
                        lab[yy, xx] = n; st.append((yy, xx))
    return lab, n


def read_arrays(C, B, spec):
    """(outside, vacated, non-class) px of capture C against base B under one cell's declaration (masks returned too)."""
    if C.shape != B.shape:
        n = int(C.shape[0] * C.shape[1])
        return n, 0, 0, None
    win = np.zeros(C.shape[:2], bool)
    for x0, y0, x1, y1 in spec.get('windows', []):
        win[y0:y1, x0:x1] = True
    zc, zb = zone(C, spec['zone'], True) | win, zone(B, spec['zone'], False)
    d = (C != B).any(axis=2)
    outside = d & ~(zc | zb)
    # vacated: where the base's class ink LEFT only the background (or the parent fill it uncovers) may show — and only where
    # the shape moved away: a vacated px beside class ink that stayed (same class, same px, both pictures) is a hole eroded
    # into a shape that did not move (round 6: a 2×2 yellow over a bidi run box that is blue in both pictures).
    vacated = d & zb & ~zc & (~_classed(C, ['white'] + spec.get('vacate', [])) | grow(_persistent(C, B, spec['zone']), 1))
    ok = _classed(C, ['white'] + spec['ink'])
    aa = spec.get('aa')
    if aa == 'neutral':
        ok |= PRED['neutral'](C)
    elif aa == 'ramp':
        ok |= _segment(C, ['white'] + spec['palette'], RAMP_TOL)
    elif aa in ('box', 'segment'):
        inwin = _flat(C, spec['palette']) | (_box_aa(C, B, spec['palette']) if aa == 'box' else _segment(C, spec['palette'], SEG_TOL))
        ok |= win & inwin
    nonclass = d & zc & ~ok
    # a recorded window holds ONE connected change (every fixed picture: one component per window, fix-r6.zones.out.txt §2):
    # a second component inside it is paint the unit does not draw (a speck / line not touching the recorded change)
    for x0, y0, x1, y1 in spec.get('windows', []):
        lab, n = _components(d[y0:y1, x0:x1])
        if n > 1:
            sizes = np.bincount(lab.ravel())[1:]
            stray = (lab > 0) & (lab != 1 + int(np.argmax(sizes)))
            nonclass[y0:y1, x0:x1] |= stray
    return int(outside.sum()), int(vacated.sum()), int(nonclass.sum()), (outside, vacated, nonclass)


def read(run, base, cell):
    """The residual read of one cell: dict(state OK / FAIL / MISSING, the three counts, a printable line)."""
    spec = SPECS.get(cell)
    if spec is None:
        return {'state': 'NO SPEC', 'line': f'residual: no declared zone for {cell}'}
    C, B = rgb(capture_path(run, cell)), rgb(capture_path(base, cell))
    if C is None or B is None:
        return {'state': 'MISSING', 'line': f'residual: capture MISSING ({"run" if C is None else "base"} {cell})'}
    o, v, n, _ = read_arrays(C, B, spec)
    ok = o == 0 and v == 0 and n == 0
    why = (f'residual: {o} px differ from {base} OUTSIDE the declared change zone, {v} vacated px not background, {n} non-class px '
           f'inside it (each must be 0: paint the key\'s reads never see)')
    return {'state': 'OK' if ok else 'FAIL', 'outside': o, 'vacated': v, 'nonClass': n,
            'line': f'residual 0 / 0 / 0 vs {base}' if ok else why}


if __name__ == '__main__':
    import sys
    a = sys.argv[1:]
    if len(a) < 1 or '--base' not in a:
        print('usage: residual-read.py <run> --base <base> [cell …]  (every declared cell by default)', file=sys.stderr); sys.exit(2)
    b = a[a.index('--base') + 1]; rest = [x for i, x in enumerate(a) if x != '--base' and (i == 0 or a[i - 1] != '--base')]
    run, cells = rest[0], rest[1:] or sorted(SPECS)
    bad = 0
    for c in cells:
        r = read(run, b, c)
        bad += r['state'] != 'OK'
        print(f'{c:78} {r["state"]:8} {r["line"]}')
    sys.exit(1 if bad else 0)
