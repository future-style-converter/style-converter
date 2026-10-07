#!/usr/bin/env python3
# tools/titan/results/wave53-plan/soft-hyphen.geometry.py — the L2 (soft-hyphen) post-gate GEOMETRY probe.
#
# Why: the three android targets PASS today with the wrong words (highwa/y, h/ighway, high/way — DEGENERATE at
# 0.953-0.982), and the iOS targets have the right glyphs in 26-px boxes where the ref's are 46 px. SSIM alone cannot
# say which picture a 0.99 is. PLAN.md §6 R6 reads this probe (plan-skeptic must-fix 2). Each line ends in
# "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)".
#
# Usage: python3 tools/titan/results/wave53-plan/soft-hyphen.geometry.py [run-id]     (default wave52-ship)
# Expected after L2 on wave53-probe / wave53-final:
#   hyphens/hyphens-span-001 android|ios        … → GEOMETRY OK
#   hyphens/hyphens-out-of-flow-001 android|ios … → GEOMETRY OK
#   hyphens/hyphens-out-of-flow-002 android     … → GEOMETRY OK
# (web rows are printed as controls: L2 changes no web capture; -002 web is WRONG today and stays so, out of family.)
# The ref rows must print GEOMETRY OK (exit 1 otherwise). Decodes 12 PNGs.
import sys
from geometry_common import paths, load, dark, orange, bands, near

run = sys.argv[1] if len(sys.argv) > 1 else 'wave52-ship'
ref_failed = False


def boxes(px, w, h):
    # Each orange-bordered test box (below the y95 instruction paragraph) as (top, bottom, left, right), and inside it
    # the ink lines (dark rows, 3 px in from the 3-px border) as (top, bottom, xmin, xmax).
    out = []
    for (y0, y1, x0, x1) in bands(px, 0, w, 95, h, orange, gap=0):
        out.append(((y0, y1, x0, x1), bands(px, x0 + 3, x1 - 2, y0 + 3, y1 - 2, dark, gap=0)))
    return out


def check(found, ref):
    # css-text-3 §5.3 (`hyphens: manual`: U+00AD is a break opportunity; the hyphen is painted when the break is
    # taken) + CSS 2.1 §10.8: every box is two line boxes tall (ref 46 px) and reads `high‐` (ink right edge x61 —
    # the hyphen) over `way` (x53). Tolerances: box height ±1 px, ink edges ±2 px.
    if len(found) != len(ref):
        return f'boxes {len(found)}/{len(ref)}'
    for i, ((b, lines), (rb, rlines)) in enumerate(zip(found, ref)):
        if not near(b[1] - b[0], rb[1] - rb[0], 1):
            return f'box {i + 1} height {b[1] - b[0] + 1} vs ref {rb[1] - rb[0] + 1}'
        if len(lines) != len(rlines):
            return f'box {i + 1} {len(lines)} ink lines vs ref {len(rlines)}'
        for j, (l, rl) in enumerate(zip(lines, rlines)):
            if not near(l[2], rl[2], 2) or not near(l[3], rl[3], 2):
                return f'box {i + 1} line {j + 1} ink x{l[2]}-{l[3]} vs ref x{rl[2]}-{rl[3]}'
    return None


for test in ('hyphens/hyphens-span-001', 'hyphens/hyphens-out-of-flow-001', 'hyphens/hyphens-out-of-flow-002'):
    ref = None
    for label, p in paths(run, 'css-text', test):
        im, px = load(p)
        if im is None:
            print(f'{test} {label:8} MISSING {p}'); continue
        found = boxes(px, *im.size)
        if label == 'ref':
            ref = found
        why = check(found, ref)
        heights = sorted({b[1] - b[0] + 1 for b, _ in found})
        lines = sorted({tuple((l[2], l[3]) for l in ls) for _, ls in found})
        verdict = 'GEOMETRY OK' if why is None else f'GEOMETRY WRONG ({why})'
        print(f'{test} {label:8} boxes {len(found)} heights {heights} line-ink {lines} → {verdict}')
        if label == 'ref' and why is not None:
            ref_failed = True
sys.exit(1 if ref_failed else 0)
