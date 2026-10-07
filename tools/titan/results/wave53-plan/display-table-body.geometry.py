#!/usr/bin/env python3
# wave-53 plan, family display-table-body — the GEOMETRY probe the closing
# gate reads instead of the SSIM (display-table-body.md §8). SSIM passes a
# 20x20 square displaced by 5-23 px in this directory (0.990-0.996), so the
# verdict on these cells is the square's rows/columns and the red-ink count.
#
# Usage: python3 display-table-body.geometry.py [run-id] [test-number ...]
#   default run wave52-ship, default tests 006 (pass 001..009 for the
#   whole css21-errata s-11-1-1b neighbourhood).
# Decodes 4 PNGs per test (ref + 3 captures) — keep it to a handful.
#
# Self-check (plan-skeptic round 2, nit 6; the same contract as the four sibling probes in geometry_common.py): for a
# test whose expected picture is pre-registered in EXPECT, the REF row must print exactly that string, otherwise the
# rule (not the lane) is wrong and the probe exits 1. A missing ref also exits 1. Capture rows are printed, never
# judged here: PLAN.md §6 reads each capture line against the same string. Exit 0 otherwise.
import os, sys
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
REF = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/'
       'white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/CSS2')
args = sys.argv[1:]
run = args.pop(0) if args and not args[0].isdigit() else 'wave52-ship'
tests = args or ['006']
SEC = f'{ROOT}/tools/titan/runs/{run}/sections/CSS2'
DIRS = [('ref', None), ('web', 'screenshots'), ('ios', 'ios-screenshots'), ('android', 'android-screenshots')]
# The pre-registered picture per test (PLAN.md §2 L3 item B, expectations.json lanes.L3-canvas-root.geometryProbe.expect).
EXPECT = {'006': 'square rows 56-75 (20) x 24-43 | red px 0'}
ref_failed = False   # set when a ref row is missing or does not print its EXPECT string


def probe(path):
    im = Image.open(path).convert('RGB'); w, h = im.size; px = im.load()
    rows = []   # rows holding an 18+-px run of pure black (the square, never glyphs)
    for y in range(min(h, 300)):
        run_len, best = 0, None
        for x in range(w):
            if sum(px[x, y]) < 30:
                run_len += 1
            else:
                if run_len >= 18: best = (x - run_len, x - 1)
                run_len = 0
        if run_len >= 18: best = (w - run_len, w - 1)
        if best: rows.append((y, best))
    red = sum(1 for y in range(min(h, 300)) for x in range(w)
              if px[x, y][0] > 150 and px[x, y][1] < 90 and px[x, y][2] < 90)
    if not rows: return 'no square', red
    return f'square rows {rows[0][0]}-{rows[-1][0]} ({len(rows)}) x {rows[0][1][0]}-{rows[0][1][1]}', red


for t in tests:
    stem = f'css21-errata__s-11-1-1b-{t}'
    for label, d in DIRS:
        p = f'{REF}/{stem}.png' if d is None else f'{SEC}/{d}/wpt__CSS2__{stem}.png'
        if not os.path.exists(p):
            print(t, label.ljust(8), 'MISSING', p)
            ref_failed = ref_failed or label == 'ref'   # no ref → the rule cannot be checked → exit 1
            continue
        sq, red = probe(p)
        line = f'{sq} | red px {red}'
        print(t, label.ljust(8), line)
        if label == 'ref' and t in EXPECT and line != EXPECT[t]:
            print(t, 'SELF-CHECK FAILED: the ref row is not', repr(EXPECT[t]), file=sys.stderr)
            ref_failed = True
# Expected for 006 after the fix, every platform: "square rows 56-75 (20) x 24-43 | red px 0"
sys.exit(1 if ref_failed else 0)
