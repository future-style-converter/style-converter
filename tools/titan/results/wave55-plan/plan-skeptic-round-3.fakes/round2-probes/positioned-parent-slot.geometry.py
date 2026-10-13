#!/usr/bin/env python3
# tools/titan/results/wave55-plan/positioned-parent-slot.geometry.py — the GEOMETRY probe of wave-55 lane
# L3-positioned-parent-slot (brief: positioned-parent-slot.md, BACKLOG obligation 0(i)).
#
# Why a probe and not a score: the lane's replays show the WRONG fix passing. Mounting PositionedParentFlowSlot for an
# RTL parent with no RTL origin (arm PLAIN) blanks dir-style-02a's rows 6 and 9 and still scores android 0.9663 > 0.95;
# only WHERE the ink lands separates "fixed" from "degenerate". Every line starts with its key and ends in
# "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)".
#
# Keys (the brief's §7 table pins class + unit for each):
#   [S] dir-style-02a <plat> row6|row7|row9 — the row's ink starts at the canvas edge and ends at the ref's right x ±3
#       (not mirrored), nothing past it, and the 2-px "." sits at x15-19 in the row's dot band (rows +12..+16).
#       CSS 2.1 §10.3.7 / css-position-3 §3.1: `left` is physical whatever the containing block's direction.
#       Plan-skeptic round 1 (instrument-teeth M3) added the GLYPHS: the row's ink-column profile (ink px per column)
#       cross-correlated with the ref's over shifts −6..+6 must peak at shift 0 ±1 with a normalised peak ≥ 0.75 — a row
#       gutted to its dot (0.19), its words mirrored (0.77 at +5) or its text 2-3 px off (best shift ±2/±3) is WRONG.
#   [S] bidi-tab-001 <plat> rtl-rows — on the four RTL rows (bands y86/106/186/226) the blue ink extent equals the
#       ref's ±3 px and no blue ink lies outside it (the '0' run at its box Left + run Left); round 1 added: each band's
#       blue px within ±20 % of the ref band's (a run gutted to its two outer columns is WRONG) and its LEFT edge ±1.
#   [W] anchor-position-multicol-nested-001 <plat> stack — the four 10×10 out-of-flow squares of the ABSOLUTE parent
#       __1__2 sit at their declared offsets from its origin (tops 0/0/90/90 → y78/78/168/168, lefts 0/170 →
#       x190/360): each cell ≥ 95 % green (CSS 2.1 §10.6.4: out-of-flow siblings take NO flow space); round 1 added:
#       the 1-px ring OUTSIDE each cell holds the WEB row's green count ±4 (the square ends where web's does — a grown
#       square, one slab over the four cells or flow-stacked duplicates are WRONG). The ref row reads the cells only (the
#       ref paints green beside cells 2-3: its ring is not this rule's picture; web is the [W] self-check row).
#   [S] dynamic-offset-rtl-001|dynamic-offset-vrl-rtl-001 <plat> — the magenta 10×10 bbox equals the ref's ±1 and (round
#       1) its magenta px count equals the ref's ±5 (a 1-px hollow frame is WRONG).
#   [W] dynamic-offset-rtl-002|dynamic-offset-vrl-rtl-002 <plat> — the magenta box's left edge equals 16 + the wire's
#       physical `left` (10 / 70) ±1: these refs show the post-script inset the wire does not carry, so the [W] rule
#       is the WIRE's picture and its self-check row is web (CSS itself on the same wire), not the ref. Round 1 added:
#       the FULL magenta bbox equals the web row's ±1 (a box 3 px low or 5 px wider was OK on the left edge alone) and
#       its count is 100 ±5 (a 10×10 box, not a frame).
#   [C] <doc> <plat> identical to <base> — a must-not-move picture (decoded pixels equal to the base run's), or the
#       named band / colour subset equal.
# Plan-skeptic round 2 (instrument-teeth M1) found 18 / 18 plausible wrong pictures accepted (the rules read x only, the
# run's extent only, the four cells only, the magenta child only) and added, each measured on the passing pictures and on
# the skeptic's fakes (plan-skeptic-round-2.instrument-teeth.d/fixcheck.L2L3.out.txt):
#   dir rows  `rows:` the row profile (ink px per ROW over y0−4 … y0+23) cross-correlated with the ref's over ±4 must peak
#             at shift 0 ±1 — text 2 px low / high (+2 / −2) or upside down (−3) is WRONG (web / the U1U2 replay peak at 0);
#   bidi      `order:` per RTL band, the blue COLUMN profile's correlation with the ref's at shift 0 ≥ 0.80 (web 0.999, ios
#             ≥ 0.85, the replay ≥ 0.904; the run mirrored inside its extent ≤ 0.701 — UAX #9 L2 orders the run, its extent
#             cannot) and `boxes:` each yellow inline-block (y60-155, y160-255) bbox = the ref's ±1 (erased: none);
#   stack     `red:` strict red px = 0 (the test's own failure ink: "no red") and `others:` green px OUTSIDE the four cells and
#             their ±2 ring within ±10 % of the web row's (web / ios / the replay 300; the other squares erased: 0);
#   dyn       `parent:` the yellow positioned parent's bbox = the picture's ±1 (the ref for [S], web for [W]) and yellow +
#             magenta inside it = the picture's ±1 % (10000) — the child read alone passed the parent erased or moved 3 px.
# Self-check: every [S] ref row and every [W] web row must print OK, else exit 1 (the rule, not the lane, is wrong).
#
# Usage: python3 positioned-parent-slot.geometry.py <run> [<base>] [--replay ARM]
#   ARM ∈ U1, U1U2, PLAIN, U2: substitute the android capture of every document that has
#   positioned-parent-slot-replay/<doc>-android-<ARM>.png (planning replays; see positioned-parent-slot.replay.py).
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import paths, load  # noqa: E402  (shared wave-53 probe helpers)
import numpy as np  # noqa: E402  (round 2: whole-picture colour counts)

HERE = os.path.dirname(os.path.abspath(__file__))
argv = sys.argv[1:]
replay = None
if '--replay' in argv:
    i = argv.index('--replay'); replay = argv[i + 1]; del argv[i:i + 2]
run = argv[0] if argv else 'wave54-final'
base = argv[1] if len(argv) > 1 else 'wave54-final'
selfcheck_failed = False

ink = lambda p: min(p) < 200                                   # any glyph ink (green / blue / black text)
dark = lambda p: min(p) < 120                                  # glyph cores (the dot is 2×2 at < 120)
blue = lambda p: p[2] > 150 and p[0] < 120 and p[1] < 120
green = lambda p: p[0] < 60 and p[1] > 100 and p[2] < 60
magenta = lambda p: p[0] > 200 and p[1] < 80 and p[2] > 200
red = lambda p: p[0] > 200 and p[1] < 60 and p[2] < 60


def capture(section, test, label, run_id=None):
    """(im, px) for one platform capture (or the ref), honouring --replay for android."""
    flat = test.replace('/', '__')
    if replay and label == 'android' and run_id is None:
        # Arms are per document: a combined arm falls back to the single-unit replay of a document only one unit
        # reaches (U1U2 → U1 for LTR-only documents, → U2 for host-active RTL ones; PLAIN = U1 off the RTL rows).
        for arm in {'U1U2': ['U1U2', 'U1', 'U2'], 'PLAIN': ['PLAIN', 'U1']}.get(replay, [replay]):
            rp = os.path.join(HERE, 'positioned-parent-slot-replay', f'{flat.split("__")[-1]}-android-{arm}.png')
            if os.path.exists(rp):
                return load(rp)
    for lab, p in paths(run_id or run, section, test):
        if lab == label:
            return load(p)
    return None, None


def box(px, region, pred):
    """(x0, y0, x1, y1, n) of pixels matching pred in region, or None."""
    x0, y0, x1, y1 = region
    xs, ys, n = [], [], 0
    for y in range(y0, y1):
        for x in range(x0, x1):
            if pred(px[x, y]):
                xs.append(x); ys.append(y); n += 1
    return (min(xs), min(ys), max(xs), max(ys), n) if n else None


def profile(px, w, y0, y1):
    """Ink px per column inside rows [y0, y1) — the row's glyph signature along x."""
    return [sum(1 for y in range(y0, y1) if ink(px[x, y])) for x in range(w)]


def xcorr(p, q, span=6):
    """(peak normalised correlation, its shift) of profile p against q shifted by −span..+span columns."""
    best = (-1.0, 0)
    for s in range(-span, span + 1):
        qs = [q[x - s] if 0 <= x - s < len(q) else 0 for x in range(len(p))]
        den = (sum(a * a for a in p) * sum(b * b for b in qs)) ** 0.5
        c = sum(a * b for a, b in zip(p, qs)) / den if den else 0.0
        best = max(best, (c, s))
    return best


def rowprofile(px, w, y0):
    """Ink px per ROW over y0−4 … y0+23 (round 2): the row's glyph signature along y (vertical drift, a flip)."""
    return [sum(1 for x in range(w) if ink(px[x, y])) for y in range(y0 - 4, y0 + 24)]


def corr0(p, q):
    """Normalised correlation of two profiles at shift 0 (round 2: the run's internal order, not its extent)."""
    den = (sum(a * a for a in p) * sum(b * b for b in q)) ** 0.5
    return sum(a * b for a, b in zip(p, q)) / den if den else 0.0


def arr(im):
    """(h, w, 3) int array of a decoded capture (round 2: whole-picture colour masks)."""
    return np.asarray(im.convert('RGB')).astype(np.int16)


A_YEL = lambda a: (a[..., 0] > 200) & (a[..., 1] > 200) & (a[..., 2] < 80)          # CSS yellow (the positioned parents)
A_MAG = lambda a: (a[..., 0] > 200) & (a[..., 1] < 80) & (a[..., 2] > 200)          # magenta (the out-of-flow child)
A_GRN = lambda a: (a[..., 0] < 60) & (a[..., 1] > 100) & (a[..., 2] < 60)           # the `green` predicate above
A_RED = lambda a: (a[..., 0] > 200) & (a[..., 1] < 60) & (a[..., 2] < 60)           # the `red` predicate above


def abox(m, region=None):
    """(x0, y0, x1, y1) of mask m inside region (x0, y0, x1, y1), or None."""
    x0, y0, x1, y1 = region or (0, 0, m.shape[1], m.shape[0])
    ys, xs = np.nonzero(m[y0:y1, x0:x1])
    return (int(xs.min()) + x0, int(ys.min()) + y0, int(xs.max()) + x0, int(ys.max()) + y0) if len(xs) else None


def verdict(key, label, facts, why, selfcheck=False):
    global selfcheck_failed
    print(f'{key} {facts} → ' + ('GEOMETRY OK' if not why else f'GEOMETRY WRONG ({"; ".join(why)})'))
    if selfcheck and why:
        selfcheck_failed = True


# ── [S] dir-style-02a rows 6 / 7 / 9 ──
ROW_X1 = {6: 153, 7: 243, 9: 153}           # the ref's right ink edge per row (measured: §1 of the brief)
ref_prof = {}                               # the ref's ink-column profile per row (read first: the ref row comes first)
ref_rprof = {}                              # round 2: the ref's ink-ROW profile per row
for label in ('ref', 'web', 'ios', 'android'):
    im, px = capture('selectors', 'dir-style-02a', label)
    for k in (6, 7, 9):
        key = f'[S] dir-style-02a {label} row{k}'
        if im is None:
            print(f'{key} MISSING'); continue
        y0 = 16 + 20 * (k - 1)
        b = box(px, (0, y0, im.width, y0 + 20), ink)
        dot = box(px, (15, y0 + 12, 20, y0 + 17), dark)
        why = []
        if b is None:
            why.append('no ink in the row')
        else:
            if b[0] > 18 or abs(b[2] - ROW_X1[k]) > 3:
                why.append(f'extent x{b[0]}-{b[2]} vs ref x17-{ROW_X1[k]}')
            if b[2] > ROW_X1[k] + 10:
                why.append('ink past the ref right edge (mirrored)')
        if dot is None or dot[4] < 2:
            why.append(f'dot: none at x15-19 y{y0 + 12}-{y0 + 16}')
        prof = profile(px, im.width, y0, y0 + 20)
        rprof = rowprofile(px, im.width, y0)
        if label == 'ref':
            ref_prof[k] = prof
            ref_rprof[k] = rprof
        peak, shift = xcorr(prof, ref_prof[k])
        if peak < 0.75 or abs(shift) > 1:
            why.append(f'glyphs: column profile peak {peak:.2f} at shift {shift:+d} vs the ref (want ≥ 0.75 at 0 ±1)')
        rpeak, rshift = xcorr(rprof, ref_rprof[k], span=4)
        if abs(rshift) > 1:
            # round 2: text 2 px low / high or upside down keeps every column read above (CSS 2.1 §10.8: the row's line box)
            why.append(f'rows: row profile peak {rpeak:.2f} at shift {rshift:+d} vs the ref (want 0 ±1)')
        verdict(key, label, f'ink {b[:4] if b else None} dot {dot[4] if dot else 0}px profile {peak:.2f}@{shift:+d} rows {rpeak:.2f}@{rshift:+d}', why,
                selfcheck=(label == 'ref'))

# ── [S] bidi-tab-001 RTL rows ──
RTL_BANDS = (86, 106, 186, 226)
YBOXES = ((60, 155), (160, 255))            # the two yellow inline-block rows (round 2): their bboxes must not move or vanish
ref_ext, ref_col, ref_ybox = {}, {}, None
for label in ('ref', 'web', 'ios', 'android'):
    key = f'[S] bidi-tab-001 {label} rtl-rows'
    im, px = capture('css-text', 'bidi/bidi-tab-001', label)
    if im is None:
        print(f'{key} MISSING'); continue
    why, facts = [], []
    a = arr(im)
    ybox = [abox(A_YEL(a), (0, y0, a.shape[1], y1)) for y0, y1 in YBOXES]
    if label == 'ref':
        ref_ybox = ybox
    elif any((b_ is None) != (r_ is None) or (b_ and any(abs(b_[i] - r_[i]) > 1 for i in range(4))) for b_, r_ in zip(ybox, ref_ybox)):
        why.append(f'boxes: yellow {ybox} vs ref {ref_ybox} ±1')          # round 2: the inline-blocks erased / moved
    cols = []
    for y0 in RTL_BANDS:
        col = [sum(1 for y in range(y0, y0 + 20) if blue(px[x, y])) for x in range(im.width)]
        if label == 'ref':
            ref_col[y0] = col
        c0 = corr0(col, ref_col[y0])
        cols.append(round(c0, 3))
        if c0 < 0.80:
            # round 2: UAX #9 L2 reverses the RTL run's ORDER; a run mirrored inside its own extent keeps every extent read
            why.append(f'order: band y{y0} blue column profile {c0:.2f} at shift 0 vs the ref (want ≥ 0.80)')
    facts.append(f'order {cols} boxes {ybox}')
    for y0 in RTL_BANDS:
        b = box(px, (0, y0, im.width, y0 + 20), blue)
        ext = (b[0], b[2], b[4]) if b else None
        facts.append(f'y{y0}:{ext}')
        if label == 'ref':
            ref_ext[y0] = ext
        elif ext is None or abs(ext[0] - ref_ext[y0][0]) > 3 or abs(ext[1] - ref_ext[y0][1]) > 3:
            why.append(f'band y{y0} blue x{ext} vs ref x{ref_ext[y0]}')
        elif abs(ext[0] - ref_ext[y0][0]) > 1 or abs(ext[2] - ref_ext[y0][2]) > 0.2 * ref_ext[y0][2]:
            # round 1: the run itself, not only its extent — left edge ±1, blue px ±20 % of the ref band's
            why.append(f'band y{y0} blue left x{ext[0]} n {ext[2]} vs ref x{ref_ext[y0][0]} n {ref_ext[y0][2]} (±1 / ±20 %)')
    verdict(key, label, ' '.join(facts[1:] + facts[:1]), why, selfcheck=(label == 'ref'))

# ── [W] anchor-position-multicol-nested-001: the stacked parent's four squares ──
CELLS = ((190, 78), (360, 78), (190, 168), (360, 168))


def ring(px, x, y):
    """Green px on the 1-px ring just OUTSIDE the 10×10 cell at (x, y) (44 px: where a grown / slab / stacked square shows)."""
    return sum(1 for yy in range(y - 1, y + 11) for xx in range(x - 1, x + 11)
               if not (x <= xx < x + 10 and y <= yy < y + 10) and green(px[xx, yy]))


web_ring = web_others = None
for label in ('ref', 'web', 'ios', 'android'):
    key = f'[W] anchor-position-multicol-nested-001 {label} stack'
    im, px = capture('css-anchor-position', 'anchor-position-multicol-nested-001', label)
    if im is None:
        print(f'{key} MISSING'); continue
    a = arr(im)
    keep = np.zeros(a.shape[:2], bool)
    for (x, y) in CELLS:
        keep[y - 2:y + 12, x - 2:x + 12] = True
    reds, others = int(A_RED(a).sum()), int((A_GRN(a) & ~keep).sum())
    fill = []
    for (x, y) in CELLS:
        b = box(px, (x, y, x + 10, y + 10), green)
        fill.append(b[4] if b else 0)
    why = [f'cell ({x},{y}) {n}% green' for (x, y), n in zip(CELLS, fill) if n < 95]
    rings = [ring(px, x, y) for (x, y) in CELLS]
    if label == 'web':
        web_ring, web_others = rings, others
    if reds:
        why.append(f'red: {reds} red px (the test\'s failure ink: "only green, no red")')        # round 2
    if label not in ('ref', 'web'):
        if web_ring is None:
            why.append('ring: no web row to compare (the [W] picture is web\'s)')
        else:
            if any(abs(a_ - b_) > 4 for a_, b_ in zip(rings, web_ring)):
                why.append(f'ring {rings} green px outside the cells vs web {web_ring} ±4 (grown / merged / stacked squares)')
            if abs(others - web_others) > 0.1 * web_others:
                # round 2: the rest of the picture (the other squares) — erased squares kept every cell / ring read
                why.append(f'others: {others} green px outside the cells vs web {web_others} ±10 %')
    verdict(key, label, f'cells {fill} ring {rings} red {reds} others {others}', why, selfcheck=(label in ('ref', 'web')))

# ── [S] / [W] dynamic-offset RTL squares ──
for test, kind, wire_left in (('dynamic-offset-rtl-001', 'S', None), ('dynamic-offset-vrl-rtl-001', 'S', None),
                              ('dynamic-offset-rtl-002', 'W', 10), ('dynamic-offset-vrl-rtl-002', 'W', 70)):
    refb = webb = refpar = webpar = None
    for label in ('ref', 'web', 'ios', 'android'):
        key = f'[{kind}] {test} {label}'
        im, px = capture('css-writing-modes', test, label)
        if im is None:
            print(f'{key} MISSING'); continue
        b = box(px, (0, 0, im.width, im.height), magenta)
        why = []
        # round 2: the yellow positioned PARENT (the box whose RTL / vertical-rl origin the child resolves against) and
        # yellow + magenta inside it — erased or moved 3 px, the child alone passed. [S] reads the ref's, [W] web's.
        a = arr(im)
        pb = abox(A_YEL(a))
        pn = int((A_YEL(a) | A_MAG(a))[pb[1]:pb[3] + 1, pb[0]:pb[2] + 1].sum()) if pb else 0
        par = (pb, pn)
        if label == 'ref':
            refpar = par
        if label == 'web':
            webpar = par
        want_par = refpar if kind == 'S' else webpar
        if label not in ('ref',) and not (kind == 'W' and label == 'web'):
            if want_par is None or (pb is None) != (want_par[0] is None) or (pb and any(abs(pb[i] - want_par[0][i]) > 1 for i in range(4))):
                why.append(f'parent: yellow {pb} vs {"ref" if kind == "S" else "web"} {want_par[0] if want_par else None} ±1')
            elif abs(pn - want_par[1]) > 0.01 * want_par[1]:
                why.append(f'parent: yellow + magenta {pn} px vs {want_par[1]} ±1 %')
        if kind == 'S':
            if label == 'ref':
                refb = b
            if b is None or any(abs(b[i] - refb[i]) > 1 for i in range(4)):
                why.append(f'magenta {b[:4] if b else None} vs ref {refb[:4]}')
            elif abs(b[4] - refb[4]) > 5:
                why.append(f'magenta {b[4]} px vs ref {refb[4]} ±5 (a frame / partial box)')      # round 1
            verdict(key, label, f'magenta {b[:4] if b else None} n {b[4] if b else 0} parent {pb} n {pn}', why, selfcheck=(label == 'ref'))
        else:
            if label == 'ref':
                print(f'{key} magenta {b[:4] if b else None} (post-script ref: not this rule\'s picture)'); continue
            if label == 'web':
                webb = b
            if b is None or abs(b[0] - (16 + wire_left)) > 1:
                why.append(f'magenta x{b[0] if b else None} vs wire x{16 + wire_left}')
            elif webb is None or any(abs(b[i] - webb[i]) > 1 for i in range(4)):
                # round 1: the whole box, not its left edge only — web is CSS on the same wire
                why.append(f'magenta {b[:4]} vs web {webb[:4] if webb else None} ±1')
            elif abs(b[4] - 100) > 5:
                why.append(f'magenta {b[4]} px vs 100 ±5 (the 10×10 box, not a frame)')
            verdict(key, label, f'magenta {b[:4] if b else None} n {b[4] if b else 0} parent {pb} n {pn}', why, selfcheck=(label == 'web'))


# ── [C] must-not-move pictures: decoded pixels identical to <base> ──
def same(section, test, label, region=None, pred=None):
    a, pa = capture(section, test, label)
    b, pb = capture(section, test, label, run_id=base)
    if a is None or b is None:
        return None
    if a.size != b.size:
        return f'size {a.size} vs {b.size}'
    x0, y0, x1, y1 = region or (0, 0, a.width, a.height)
    diff = 0
    for y in range(y0, y1):
        for x in range(x0, x1):
            u, v = pa[x, y], pb[x, y]
            if (pred(u) != pred(v)) if pred else (u != v):
                diff += 1
    return f'{diff} px differ' if diff else ''


CONTROLS = [
    # host-INACTIVE documents the mount reaches whose pictures must not move (identity / CLAMP / AUTO classes, §5)
    # (counter-suffix's four <li> boxes are identity-class too, but L5 carries counter-suffix this wave: not a control.)
    ('css-text', 'bidi/bidi-lines-002'),
    ('css-text', 'boundary-shaping/boundary-shaping-009'), ('css-view-transitions', 'css-tags-paint-order'),
    ('css-view-transitions', 'css-tags-paint-order-with-entry'), ('selectors', 'dir-selector-change-003'),
    ('selectors', 'dir-selector-change-004'), ('css-break', 'block-end-aligned-abspos-nested'),
    ('css-position', 'position-absolute-scrollable-overflow-zero'),
    ('css-text-decor', 'text-decoration-subelements-002'),
    ('css-backgrounds', 'background-attachment-local/attachment-local-positioning-3'),
    ('css-backgrounds', 'background-attachment-local/attachment-local-positioning-4'),
    # host-ACTIVE LTR documents mounted today (U1 keeps hostActive → mount; U2 touches RTL only)
    ('css-color', 'composited-filters-under-opacity'), ('css-transforms', 'css-transform-scale-002'),
    # (backdrop-filter-edge-clipping is the same shape but an L6 paint-order carrier: not a control.)
    ('css-images', 'conic-gradient-line-height-relative-units-003'),
    ('css-images', 'conic-gradient-line-height-relative-units-004'),
    # BACKLOG 0(i)'s named control (FIXED boxes activate its host); L4's UA-margin census also reaches its two <p>
    # boxes — dropped by the plan if L4 carries it.
    ('css-anchor-position', 'anchor-center-safe-rtl'), ('css-writing-modes', 'dynamic-offset-vrl-001'),
]
for section, test in CONTROLS:
    name = test.split('/')[-1]
    r = same(section, test, 'android')
    key = f'[C] {name} android'
    if r is None:
        print(f'{key} MISSING'); continue
    verdict(key, 'android', f'identical to {base}', [r] if r else [])
# Partial controls on the target documents themselves, and the two other platforms (the wire does not change).
for key, args in (
        ('[C] dir-style-02a android unaffected-rows', ('selectors', 'dir-style-02a', 'android')),
        ('[C] dir-style-02a web', ('selectors', 'dir-style-02a', 'web')),
        ('[C] dir-style-02a ios', ('selectors', 'dir-style-02a', 'ios')),
        ('[C] bidi-tab-001 android ltr-rows', ('css-text', 'bidi/bidi-tab-001', 'android')),
        ('[C] bidi-tab-001 web', ('css-text', 'bidi/bidi-tab-001', 'web')),
        ('[C] bidi-tab-001 ios', ('css-text', 'bidi/bidi-tab-001', 'ios'))):
    if key.endswith('unaffected-rows'):
        parts = [same(*args, region=(0, y, 390, y + 20)) for y in (16, 36, 56, 76, 96, 156, 196, 216)]
    elif key.endswith('ltr-rows'):
        parts = [same(*args, region=(0, y, 390, y + 20)) for y in (66, 126, 146, 166, 206, 246)]
    else:
        parts = [same(*args)]
    if any(p is None for p in parts):
        print(f'{key} MISSING'); continue
    bad = [p for p in parts if p]
    verdict(key, args[2], f'identical to {base}', bad)
r = same('css-anchor-position', 'anchor-position-multicol-004', 'android', pred=red)
if r is None:
    print('[C] anchor-position-multicol-004 android red MISSING')
else:
    verdict('[C] anchor-position-multicol-004 android red', 'android', f'identical to {base}', [r] if r else [])
sys.exit(1 if selfcheck_failed else 0)
