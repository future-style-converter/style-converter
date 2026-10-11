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
#   [S] bidi-tab-001 <plat> rtl-rows — on the four RTL rows (bands y86/106/186/226) the blue ink extent equals the
#       ref's ±3 px and no blue ink lies outside it (the '0' run at its box Left + run Left).
#   [W] anchor-position-multicol-nested-001 <plat> stack — the four 10×10 out-of-flow squares of the ABSOLUTE parent
#       __1__2 sit at their declared offsets from its origin (tops 0/0/90/90 → y78/78/168/168, lefts 0/170 →
#       x190/360): each cell ≥ 95 % green (CSS 2.1 §10.6.4: out-of-flow siblings take NO flow space).
#   [S] dynamic-offset-rtl-001|dynamic-offset-vrl-rtl-001 <plat> — the magenta 10×10 bbox equals the ref's ±1.
#   [W] dynamic-offset-rtl-002|dynamic-offset-vrl-rtl-002 <plat> — the magenta box's left edge equals 16 + the wire's
#       physical `left` (10 / 70) ±1: these refs show the post-script inset the wire does not carry, so the [W] rule
#       is the WIRE's picture and its self-check row is web (CSS itself on the same wire), not the ref.
#   [C] <doc> <plat> identical to <base> — a must-not-move picture (decoded pixels equal to the base run's), or the
#       named band / colour subset equal.
# Self-check: every [S] ref row and every [W] web row must print OK, else exit 1 (the rule, not the lane, is wrong).
#
# Usage: python3 positioned-parent-slot.geometry.py <run> [<base>] [--replay ARM]
#   ARM ∈ U1, U1U2, PLAIN, U2: substitute the android capture of every document that has
#   positioned-parent-slot-replay/<doc>-android-<ARM>.png (planning replays; see positioned-parent-slot.replay.py).
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import paths, load  # noqa: E402  (shared wave-53 probe helpers)

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


def verdict(key, label, facts, why, selfcheck=False):
    global selfcheck_failed
    print(f'{key} {facts} → ' + ('GEOMETRY OK' if not why else f'GEOMETRY WRONG ({"; ".join(why)})'))
    if selfcheck and why:
        selfcheck_failed = True


# ── [S] dir-style-02a rows 6 / 7 / 9 ──
ROW_X1 = {6: 153, 7: 243, 9: 153}           # the ref's right ink edge per row (measured: §1 of the brief)
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
        verdict(key, label, f'ink {b[:4] if b else None} dot {dot[4] if dot else 0}px', why, selfcheck=(label == 'ref'))

# ── [S] bidi-tab-001 RTL rows ──
RTL_BANDS = (86, 106, 186, 226)
ref_ext = {}
for label in ('ref', 'web', 'ios', 'android'):
    key = f'[S] bidi-tab-001 {label} rtl-rows'
    im, px = capture('css-text', 'bidi/bidi-tab-001', label)
    if im is None:
        print(f'{key} MISSING'); continue
    why, facts = [], []
    for y0 in RTL_BANDS:
        b = box(px, (0, y0, im.width, y0 + 20), blue)
        ext = (b[0], b[2]) if b else None
        facts.append(f'y{y0}:{ext}')
        if label == 'ref':
            ref_ext[y0] = ext
        elif ext is None or abs(ext[0] - ref_ext[y0][0]) > 3 or abs(ext[1] - ref_ext[y0][1]) > 3:
            why.append(f'band y{y0} blue x{ext} vs ref x{ref_ext[y0]}')
    verdict(key, label, ' '.join(facts), why, selfcheck=(label == 'ref'))

# ── [W] anchor-position-multicol-nested-001: the stacked parent's four squares ──
CELLS = ((190, 78), (360, 78), (190, 168), (360, 168))
for label in ('ref', 'web', 'ios', 'android'):
    key = f'[W] anchor-position-multicol-nested-001 {label} stack'
    im, px = capture('css-anchor-position', 'anchor-position-multicol-nested-001', label)
    if im is None:
        print(f'{key} MISSING'); continue
    fill = []
    for (x, y) in CELLS:
        b = box(px, (x, y, x + 10, y + 10), green)
        fill.append(b[4] if b else 0)
    why = [f'cell ({x},{y}) {n}% green' for (x, y), n in zip(CELLS, fill) if n < 95]
    verdict(key, label, f'cells {fill}', why, selfcheck=(label in ('ref', 'web')))

# ── [S] / [W] dynamic-offset RTL squares ──
for test, kind, wire_left in (('dynamic-offset-rtl-001', 'S', None), ('dynamic-offset-vrl-rtl-001', 'S', None),
                              ('dynamic-offset-rtl-002', 'W', 10), ('dynamic-offset-vrl-rtl-002', 'W', 70)):
    refb = None
    for label in ('ref', 'web', 'ios', 'android'):
        key = f'[{kind}] {test} {label}'
        im, px = capture('css-writing-modes', test, label)
        if im is None:
            print(f'{key} MISSING'); continue
        b = box(px, (0, 0, im.width, im.height), magenta)
        why = []
        if kind == 'S':
            if label == 'ref':
                refb = b
            if b is None or any(abs(b[i] - refb[i]) > 1 for i in range(4)):
                why.append(f'magenta {b[:4] if b else None} vs ref {refb[:4]}')
            verdict(key, label, f'magenta {b[:4] if b else None}', why, selfcheck=(label == 'ref'))
        else:
            if label == 'ref':
                print(f'{key} magenta {b[:4] if b else None} (post-script ref: not this rule\'s picture)'); continue
            if b is None or abs(b[0] - (16 + wire_left)) > 1:
                why.append(f'magenta x{b[0] if b else None} vs wire x{16 + wire_left}')
            verdict(key, label, f'magenta {b[:4] if b else None}', why, selfcheck=(label == 'web'))


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
