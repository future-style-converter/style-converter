#!/usr/bin/env python3
# tools/titan/results/wave54-plan/rtl-marker-bake.geometry.py — the wave-54 rtl-marker-bake GEOMETRY probe (units P and M').
#
# Why a new probe and not wave53-plan/lists-bakes.geometry.py alone: that probe caught the wave-53 Android defect only
# by luck of row 1's right edge. Its band scan stops at y303, so the lone "ב." the wave53-probe Android capture drew at
# y309-317 (one 20-px run height below row 4) was never seen. It also never asked whether a row's marker ink sits ON
# that row's text line, nor whether the marker is wider than a lone "." (the digits '1' / '2' were missing). This probe
# asks all three, per RTL row, and adds the unit-P lines (padding kept on a bidi-bake root shifts Android runs by the
# padding). Every line ends in "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)". The ref rows must print OK (exit 1
# otherwise): the rule must describe the picture the test asks for. Pure PIL, 16 PNGs (+3 with a base run).
#
# Usage: python3 tools/titan/results/wave54-plan/rtl-marker-bake.geometry.py <run-id> [base-run-id]
#   base-run-id adds the must-not-move crop: counter-suffix rows y0-207 (the eight LTR rows) pixel-identical to base.
# Expected after unit P alone:  [P] lines OK on every platform; [M] counter-suffix lines WRONG on ios/android/web (no
#                               RTL marker on the natives, Blink's marker on the left on web) — P does not claim M.
# Expected after units P + M':  every line OK; wave53-probe (the reverted M + P under the <li>) prints android
#                               "[M] … GEOMETRY WRONG (rtl row 1 marker …)" and ios/web OK — the regression this guards.
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import paths, load, dark, bands, near  # noqa: E402  (shared wave-53 helpers: refs path, PNG decode)

args = sys.argv[1:]
run = args[0] if args else 'wave53-final'
base = args[1] if len(args) > 1 else None
ref_failed = False


def ink(p):
    # Any glyph ink, coloured or black (bidi-lines paints blue / orange "!"): sum < 600 keeps antialiased cores.
    return p[0] + p[1] + p[2] < 600


def m_rows(px):
    # [M] The four dir=rtl rows of counter-suffix: text ink in the item column x95-128 (ref "foo"/"bar" x104-127).
    return bands(px, 95, 129, 205, 300, dark)


def m_check(px, rows, ref):
    # css-lists-3 §3 + §4: each RTL item's outside ::marker hangs on its INLINE-START side = the right of an RTL item,
    # on the item's FIRST LINE (the marker is aligned with the first line box). Ref: text x104-127, marker ink x133-145.
    if len(rows) != 4:
        # Say where the text went (pre-P Android painted it +48 px right, at x152-174).
        wide = bands(px, 95, 200, 205, 300, dark)
        return f'rtl text rows {len(rows)}/4 in x95-128' + (f' (text at x{min(r[2] for r in wide)}-{max(r[3] for r in wide)})' if wide else '')
    for i, (r, q) in enumerate(zip(rows, ref)):
        # Unit P: the text sits at the ref's x (Android shifted it +48 by the root's 3em padding before P).
        if not near(r[2], q[2], 3):
            return f'rtl row {i + 1} text left x{r[2]} vs ref x{q[2]}'
        # Unit M': marker ink in x129-150 whose rows overlap THIS text row's band (±2) — not the next row's.
        mk = bands(px, 129, 151, r[0] - 2, r[1] + 3, dark)
        if not mk:
            spill = bands(px, 129, 151, r[0] - 2, r[1] + 26, dark)
            where = f' (marker ink y{spill[0][0]}-{spill[0][1]} below the line)' if spill else ''
            return f'rtl row {i + 1} no marker ink on text row y{r[0]}-{r[1]}{where}'
        xs = [x for x in range(129, 151) for y in range(r[0] - 2, r[1] + 3) if dark(px[x, y])]
        # A lone "." is 1-2 px wide; ".1" / ".א" span ≥ 7 px (ref 133-145). Catches a dropped digit run.
        # (iOS paints a proportional '1' without the tabular foot: '.1' = x134-141, 7 px — still ≥ 5.)
        if max(xs) - min(xs) < 5:
            return f'rtl row {i + 1} marker ink x{min(xs)}-{max(xs)} narrower than 5 px (a glyph run missing)'
        if not near(max(xs), 144, 3):
            return f'rtl row {i + 1} marker right x{max(xs)} vs ref x144±3'
    # Nothing below the last RTL row: a run placed one run-height late would land at y300-330.
    below = bands(px, 95, 160, 300, 335, dark)
    if below:
        return f'ink y{below[0][0]}-{below[0][1]} x{below[0][2]}-{below[0][3]} below the last rtl row'
    # Nothing on the LTR hang side in the RTL band (Blink's marker before the fix sat at x46-58).
    if any(dark(px[x, y]) for y in range(208, 303) for x in range(40, 64)):
        return 'ink at x40-63 in the rtl rows (marker on the wrong side)'
    return None


def first_left(px, y0, y1):
    # [P] Left edge of the first ink band inside a bordered box interior (x20-356, skipping the 3-px border columns).
    rows = bands(px, 20, 356, y0, y1, ink)
    return (rows[0][2], rows[0][0]) if rows else (None, None)


# [P] Padded bidi-bake roots whose runs Android anchored at the CONTENT box (+0.5ch = 4 px) before unit P. Each window
# holds one baked line; its first ink's left edge must equal the ref's ±1. bidi-lines-001: the first LTR "français"
# (ref x29) and the first RTL "فارسی" (ref x273 — its START is what the bake placed; the native Arabic face is wider,
# so its right edge is a font residual, not geometry). bidi-lines-002: the "! Hello" line (ref x31) and the "سلام !"
# line (ref x276). NOT its first line: the top/bottom orange "!" sit on the LEFT on all three platforms (the wire
# carries Left 10.09 for them) while the ref has them on the right — an out-of-family bake defect this probe must
# not attribute to unit P.
P_CASES = (('bidi/bidi-lines-001', 'css-text', ((112, 150), (155, 192))),
           ('bidi/bidi-lines-002', 'css-text', ((150, 190), (232, 277))))

for test, section, windows in P_CASES:
    refx = None
    for label, p in paths(run, section, test):
        im, px = load(p)
        if im is None:
            print(f'[P] {test} {label:8} MISSING {p}'); continue
        got = [first_left(px, y0, y1) for y0, y1 in windows]
        if label == 'ref':
            refx = [x for x, _ in got]
        bad = [f'line y{y} left x{x} vs ref x{rx}±1' for (x, y), rx in zip(got, refx or [None] * len(got))
               if x is None or rx is None or not near(x, rx, 1)]
        summary = ' '.join(f'y{y}:x{x}' for x, y in got)
        print(f'[P] {test} {label:8} line starts {summary} → ' + ('GEOMETRY OK' if not bad else f'GEOMETRY WRONG ({bad[0]})'))
        if label == 'ref' and bad:
            ref_failed = True

ref_rows = None
base_paths = dict(paths(base, 'css-counter-styles', 'counter-suffix')) if base else {}
for label, p in paths(run, 'css-counter-styles', 'counter-suffix'):
    im, px = load(p)
    if im is None:
        print(f'[M] counter-suffix {label:8} MISSING {p}'); continue
    rows = m_rows(px)
    if label == 'ref':
        ref_rows = rows
    why = m_check(px, rows, ref_rows)
    summary = ' '.join(f'y{r[0]}-{r[1]}:x{r[2]}-{r[3]}' for r in rows)
    extra = ''
    if base and label != 'ref':
        bim, _ = load(base_paths[label])
        same = bim is not None and list(bim.crop((0, 0, bim.size[0], 208)).getdata()) == list(im.crop((0, 0, im.size[0], 208)).getdata())
        extra = f' | rows 0-207 {"identical to" if same else "DIFFER from"} {base}'
    print(f'[M] counter-suffix {label:8} text {summary}{extra} → ' + ('GEOMETRY OK' if why is None else f'GEOMETRY WRONG ({why})'))
    if label == 'ref' and why is not None:
        ref_failed = True
sys.exit(1 if ref_failed else 0)
