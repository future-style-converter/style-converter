#!/usr/bin/env python3
# tools/titan/results/wave53-plan/lists-bakes.geometry.py — the L1 (lists-bakes) post-gate GEOMETRY probe.
#
# Why: SSIM cannot tell a flat list with wrong numbers from the nested one (counter-reset-reversed-nested is P 0.9506
# today), nor a marker-less RTL list from a marked one (counter-suffix ios P 0.9802). PLAN.md §6 R6 reads this probe's
# verdict instead, per the plan-skeptic must-fix 2. Each line ends in "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)".
#
# Usage: python3 tools/titan/results/wave53-plan/lists-bakes.geometry.py [run-id] [base-run-id]
#   run-id      default wave52-ship (prints the pre-fix WRONG verdicts: the rule can fail).
#   base-run-id optional; adds the L1 must-not-move crop compare: counter-suffix rows y0-207 (the eight LTR/CJK rows the
#               lane must not touch) pixel-identical between base and run, per platform.
# Expected after L1 (both units) on wave53-probe / wave53-final, every platform:
#   counter-reset-reversed-nested <p> … → GEOMETRY OK
#   counter-suffix <p> … → GEOMETRY OK     and, with base wave53-open: "rows 0-207 identical to wave53-open"
# The ref row must print GEOMETRY OK (exit 1 otherwise): the rule describes the picture the test asks for.
# Decodes 8 PNGs (16 with a base run).
import sys
from geometry_common import paths, load, dark, bands, near

args = sys.argv[1:]
run = args[0] if args else 'wave52-ship'
base = args[1] if len(args) > 1 else None
ref_failed = False


def nested_rows(px, w):
    # Text rows of the list (dark ink, above y320 where the six 20-px rows live): (top, bottom, xmin, xmax) each.
    return bands(px, 0, w, 16, 320, dark)


def check_nested(rows, ref):
    # css-lists-3 §4.4.2 + HTML §13.2.6.4.7: six rows (3. Three / 2. Two / 11. Eleven / 9. Nine / 8. Eight / 1. One);
    # rows 3-5 are indented (x97 in the ref) because they sit in Two's nested <ol>. The marker width is in xmax, so a
    # wrong number ("12." vs "3.") moves xmax by ≥ 8 px. Tolerances: rows ±3 px, left edge ±3, right edge ±4 (native
    # glyph advances measured within 1-4 px of Chromium's on counter-suffix rows 1-8 of wave52-ship).
    if len(rows) != len(ref):
        return f'rows {len(rows)}/{len(ref)}'
    for i, (r, q) in enumerate(zip(rows, ref)):
        if not near(r[0], q[0], 3): return f'row {i + 1} top {r[0]} vs ref {q[0]}'
        if not near(r[2], q[2], 3): return f'row {i + 1} left x{r[2]} vs ref x{q[2]}'
        if not near(r[3], q[3], 4): return f'row {i + 1} right x{r[3]} vs ref x{q[3]}'
    return None


def suffix_rows(px, w):
    # The four dir=rtl rows of counter-suffix sit at y208-302 (ref ink y214-297).
    return bands(px, 0, w, 208, 303, dark)


def check_suffix(px, rows, ref):
    # css-lists-3: an outside ::marker sits on the INLINE-START side, i.e. the RIGHT of an RTL item: ref ink x133-145
    # (marker) after the text x104-127. Web paints Blink's marker on the left today (x46-58); natives paint none.
    if len(rows) != len(ref):
        return f'rtl rows {len(rows)}/{len(ref)}'
    for i, (r, q) in enumerate(zip(rows, ref)):
        if not near(r[2], q[2], 3): return f'rtl row {i + 1} left x{r[2]} vs ref x{q[2]}'
        if not near(r[3], q[3], 3): return f'rtl row {i + 1} right x{r[3]} vs ref x{q[3]}'
        if not any(dark(px[x, y]) for y in range(r[0], r[1] + 1) for x in range(129, 151)):
            return f'rtl row {i + 1} no marker ink in x129-150'
    if any(dark(px[x, y]) for y in range(208, 303) for x in range(40, 64)):
        return 'ink at x40-63 in the rtl rows (marker on the wrong side)'
    return None


def crop_equal(a, b):
    # Must-not-move crop: rows y0-207 of counter-suffix byte-for-byte in decoded pixels (base vs run).
    return list(a.crop((0, 0, a.size[0], 208)).getdata()) == list(b.crop((0, 0, b.size[0], 208)).getdata())


for test, section, rows_of, check in (
        ('counter-reset-reversed-nested', 'css-lists', nested_rows, lambda px, rows, ref: check_nested(rows, ref)),
        ('counter-suffix', 'css-counter-styles', suffix_rows, check_suffix)):
    ref_rows = None
    base_paths = dict(paths(base, section, test)) if base else {}
    for label, p in paths(run, section, test):
        im, px = load(p)
        if im is None:
            print(f'{test} {label:8} MISSING {p}'); continue
        rows = rows_of(px, im.size[0])
        if label == 'ref':
            ref_rows = rows
        why = check(px, rows, ref_rows)
        summary = ' '.join(f'y{r[0]}-{r[1]}:x{r[2]}-{r[3]}' for r in rows)
        extra = ''
        if base and label != 'ref' and test == 'counter-suffix':
            bim, _ = load(base_paths[label])
            extra = (f' | rows 0-207 identical to {base}' if bim is not None and crop_equal(bim, im)
                     else f' | rows 0-207 DIFFER from {base}')
        verdict = 'GEOMETRY OK' if why is None else f'GEOMETRY WRONG ({why})'
        print(f'{test} {label:8} {summary}{extra} → {verdict}')
        if label == 'ref' and why is not None:
            ref_failed = True
sys.exit(1 if ref_failed else 0)
