#!/usr/bin/env python3
# tools/titan/results/wave54-plan/flex-zero-gap-rules.geometry.py — the post-gate GEOMETRY probe for the wave-54
# "flex-zero-gap-rules" lane (brief: flex-zero-gap-rules.md; scout: queue-scout-layout.md).
#
# Why: css-gaps/flex/flex-gap-decorations-033 ("flex column and row gaps are painted with 0px gaps") fails on both
# natives at f 0.94 with EVERY other pixel identical to the ref — the 5 200 rule pixels are simply absent. The probe
# pins the rules themselves. Each line ends in "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)"; the ref row must print
# OK and show the expected structure (exit 1 otherwise).
#
# Rule (css-gap-decorations-1: a gap decoration is centred in its gap and painted even when the gap is 0): the
# strict-red (column rules) and strict-blue (row rules) pixel counts equal the ref's ±2 % and their bboxes ±1 px, and
# the red runs on a row through EVERY flex line, and the blue runs on a column through every item column, equal the
# ref's ±1 px.
#
# fix r2 (plan-skeptic round 2, R2-M1): the probe read the red runs on ONE row (y20, flex line 1), so a picture with
# line 2's single column-rule segment painted at the OTHER gap (x111-120 -> x61-70) kept every count, every bbox and
# row 20, printed OK and scored 0.9919 (>= the 0.99 floor): skeptic-r2/fake-gap. A gap-index error on a wrapped line
# is exactly what "keep zero-extent intervals as positions" can produce, so every line is now read. The ref's rows:
# line 1 = y16-60 (two rules), line 2 = y71-110 (ONE rule, at the right gap), line 3 = y121-165 (two rules); the row
# rules are blue at y61-70 and y111-120 across x16-165, read on one column inside each item column.
#
# Usage: python3 tools/titan/results/wave54-plan/flex-zero-gap-rules.geometry.py [run-id]   (default wave53-final)
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import paths, load, bbox, near  # noqa: E402

run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
red = lambda p: p[0] > 200 and p[1] < 80 and p[2] < 80
blue = lambda p: p[0] < 80 and p[1] < 80 and p[2] > 200
ROWS = (20, 95, 145)    # one row through each flex line (lines 1/2/3 span y16-60 / y71-110 / y121-165 in the ref)
COLS = (40, 90, 140)    # one column through each item column (x16-60 / x71-110 / x121-165)
# The structure the ref must show for the rows/columns to mean what the comments say (self-check, not a tolerance):
REF_RUNS = {'red': {20: 2, 95: 1, 145: 2}, 'blue': {40: 2, 90: 2, 140: 2}}


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
    return len(a) == len(b) and all(near(x, y, 1) for x, y in zip(a, b))


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
             'blueRuns': {x: runs_on(px, h, x, blue, False) for x in COLS}}
        why = None
        if label == 'ref':
            ref = m
            bad = [f'{c} {k}: {len(m[c + "Runs"][k])} runs vs {n}' for c in ('red', 'blue') for k, n in REF_RUNS[c].items()
                   if len(m[c + 'Runs'][k]) != n]
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
        print(f'{test} {label:8} {m} → ' + ('GEOMETRY OK' if why is None else f'GEOMETRY WRONG ({why})'))
        if label == 'ref' and why is not None:
            ref_failed = True
sys.exit(1 if ref_failed else 0)
