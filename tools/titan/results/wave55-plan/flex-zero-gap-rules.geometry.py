#!/usr/bin/env python3
# tools/titan/results/wave55-plan/flex-zero-gap-rules.geometry.py — the WAVE-55 COPY of the wave-54 flex-gap GEOMETRY probe
# (tools/titan/results/wave54-plan/flex-zero-gap-rules.geometry.py, which stays byte-identical as the wave-54 record).
# Lane L7-instruments, BACKLOG obligation 0(m)(1); brief tools/titan/results/wave55-plan/instruments.md §7.
#
# Why a copy: plan-skeptic round-3 verifier R3V-S1 (tools/titan/results/wave54-plan/plan-skeptic-round-3.verify.md:158)
# showed the wave-54 probe reads the BLUE row-rule runs on COLUMNS only (x40 / x90 / x140, its line 89), so a row-rule
# segment with the wrong HORIZONTAL extent printed GEOMETRY OK x3 at SSIM 0.999 / 0.9993 (fakes
# wave54-plan/verify-r3/gap/fake-blue-r1-short5 and fake-blue-r2-lshort4). The GAP-ios / GAP-android GEOMETRY OK of the
# wave-54 closing gate therefore never covered that axis. This copy adds the transpose of fix r3's red column read:
# the blue runs on a ROW through each row-rule gap (y65 in y61-70, y115 in y111-120), +-1 px against the ref, which
# gives [(16, 165)] for each (css-gap-decorations-1: a row rule spans the container's inline extent between its
# content edges, x16-165 here). Everything else is the wave-54 probe's rule, unchanged.
#
# Rule (css-gap-decorations-1: a gap decoration is centred in its gap and painted even when the gap is 0): the
# strict-red (column rules) and strict-blue (row rules) pixel counts equal the ref's +-2 % and their bboxes +-1 px; the
# red runs on a row through EVERY flex line (y20 / y95 / y145), the blue runs on a column through every item column
# (x40 / x90 / x140), the red runs on a column through each column-rule gap (x65 / x115: every column-rule segment's
# VERTICAL extent, flex line by line) and — new here — the blue runs on a row through each row-rule gap (y65 / y115:
# every row rule's HORIZONTAL extent) all equal the ref's +-1 px. Each line ends in "→ GEOMETRY OK" or
# "→ GEOMETRY WRONG (<why>)"; the ref row must print OK and show the expected structure (exit 1 otherwise).
#
# Usage: python3 tools/titan/results/wave55-plan/flex-zero-gap-rules.geometry.py [run-id]   (default wave54-final)
#   A run id is resolved under tools/titan/runs/, so a fake directory is passed as ../results/<path-to-fake>.
import os, sys
# The shared PIL helpers of the wave-53 probes (paths / load / bbox / near), unchanged since wave 53.
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import paths, load, bbox, near  # noqa: E402

# The run read; wave54-final is the evidence run of record for wave 55.
run = sys.argv[1] if len(sys.argv) > 1 else 'wave54-final'
# Strict rule colours: the test paints column rules red and row rules blue (iOS captures them as pure 255,0,0 / 0,0,255).
red = lambda p: p[0] > 200 and p[1] < 80 and p[2] < 80
blue = lambda p: p[0] < 80 and p[1] < 80 and p[2] > 200
ROWS = (20, 95, 145)    # one row through each flex line (lines 1/2/3 span y16-60 / y71-110 / y121-165 in the ref)
COLS = (40, 90, 140)    # one column through each item column (x16-60 / x71-110 / x121-165)
GAPS = (65, 115)        # one column through each column-rule gap (x61-70 / x111-120): every segment's vertical extent
RGAPS = (65, 115)       # wave 55 (R3V-S1): one row through each row-rule gap (y61-70 / y111-120): its horizontal extent
LINES = ((16, 60), (71, 110), (121, 165))   # the ref's flex lines (rows), to name the line of a segment that differs
# The structure the ref must show for the rows/columns to mean what the comments say (self-check, not a tolerance):
REF_RUNS = {'red': {20: 2, 95: 1, 145: 2}, 'blue': {40: 2, 90: 2, 140: 2}, 'redCol': {65: 2, 115: 3},
            'blueRow': {65: 1, 115: 1}}


def runs_on(px, n, at, pred, horizontal):
    """Maximal runs of `pred` pixels along row `at` (horizontal) or column `at` (vertical): [(start, end)]."""
    out, cur = [], None
    for i in range(n):
        if pred(px[i, at] if horizontal else px[at, i]):
            cur = [i, i] if cur is None else [cur[0], i]
        elif cur is not None:
            out.append(tuple(cur)); cur = None
    if cur is not None:
        out.append(tuple(cur))
    return out


def same_runs(a, b):
    """Same number of runs, each endpoint within +-1 px of the ref's."""
    return len(a) == len(b) and all(near(x, y, 1) for x, y in zip(a, b))


def first_line_differing(a, b):
    """The flex line (1-based) of the first segment pair (capture `a[i]`, ref `b[i]`) that does not match +-1 px: the
    upper of the two segments (the only one when the other list is shorter), placed on the flex line it overlaps most.
    Names WHICH line's column rule has the wrong vertical extent (or an extra / a missing segment)."""
    for i in range(max(len(a), len(b))):
        if i < len(a) and i < len(b) and near(a[i], b[i], 1):
            continue
        seg = min(s for s in (a[i] if i < len(a) else None, b[i] if i < len(b) else None) if s is not None)
        overlap = [max(0, min(seg[1], y1) - max(seg[0], y0) + 1) for y0, y1 in LINES]
        return overlap.index(max(overlap)) + 1 if max(overlap) else '?'
    return '?'


ref_failed = False
for section, test in (('css-gaps', 'flex/flex-gap-decorations-033'),):
    ref = None
    for label, p in paths(run, section, test):
        im, px = load(p)
        if im is None:
            print(f'{test} {label:8} MISSING'); continue
        w, h = im.size
        rb, rn = bbox(px, w, h, red)
        bb, bn = bbox(px, w, h, blue)
        m = {'red': rb, 'redPx': rn, 'blue': bb, 'bluePx': bn,
             'redRuns': {y: runs_on(px, w, y, red, True) for y in ROWS},
             'blueRuns': {x: runs_on(px, h, x, blue, False) for x in COLS},
             'redColRuns': {x: runs_on(px, h, x, red, False) for x in GAPS},
             'blueRowRuns': {y: runs_on(px, w, y, blue, True) for y in RGAPS}}
        why = None
        if label == 'ref':
            ref = m
            bad = [f'{c} {k}: {len(m[c + "Runs"][k])} runs vs {n}' for c in ('red', 'blue', 'redCol', 'blueRow')
                   for k, n in REF_RUNS[c].items() if len(m[c + 'Runs'][k]) != n]
            if bad:
                why = 'ref structure changed: ' + '; '.join(bad)
        for k, bk in (('redPx', 'red'), ('bluePx', 'blue')):
            if why is not None:
                break
            if abs(m[k] - ref[k]) > max(2, 0.02 * ref[k]):
                why = f'{k} {m[k]} vs ref {ref[k]}'
            elif ref[bk] and m[bk] and not near(m[bk], ref[bk], 1):
                why = f'{bk} {m[bk]} vs ref {ref[bk]}'
        for y in ROWS:      # every flex line: the column rules sit in the ref's gaps, line by line
            if why is None and not same_runs(m['redRuns'][y], ref['redRuns'][y]):
                why = f'red runs row {y} {m["redRuns"][y]} vs ref {ref["redRuns"][y]}'
        for x in COLS:      # every item column: the row rules sit in the ref's gaps, column by column
            if why is None and not same_runs(m['blueRuns'][x], ref['blueRuns'][x]):
                why = f'blue runs col {x} {m["blueRuns"][x]} vs ref {ref["blueRuns"][x]}'
        for x in GAPS:      # every column-rule gap (wave-54 fix r3, R3-S3): each segment's vertical extent, line by line
            if why is None and not same_runs(m['redColRuns'][x], ref['redColRuns'][x]):
                why = (f'red runs col {x} {m["redColRuns"][x]} vs ref {ref["redColRuns"][x]}: '
                       f'flex line {first_line_differing(m["redColRuns"][x], ref["redColRuns"][x])} column-rule vertical extent')
        for y in RGAPS:     # every row-rule gap (wave 55, R3V-S1): each row rule's horizontal extent
            if why is None and not same_runs(m['blueRowRuns'][y], ref['blueRowRuns'][y]):
                why = f'blue runs row {y} {m["blueRowRuns"][y]} vs ref {ref["blueRowRuns"][y]}: row-rule horizontal extent'
        print(f'{test} {label:8} {m} → ' + ('GEOMETRY OK' if why is None else f'GEOMETRY WRONG ({why})'))
        if label == 'ref' and why is not None:
            ref_failed = True
sys.exit(1 if ref_failed else 0)
