#!/usr/bin/env python3
# wave-54 plan, family compose-table-body-cell — the GEOMETRY probe the probe/closing gate reads instead of SSIM
# for CSS2/css21-errata/s-11-1-1b-006 (brief: compose-table-body-cell.md §6). SSIM passed BOTH wrong Android
# pictures (wave53-open P 0.9944: square 5 px high; wave53-probe P 0.9906: an empty outlined cell where the square
# belongs and the square displaced 20 px right), so the verdict is the picture:
#   (1) the 20x20 block x 24-43 x rows 56-75 is SOLID black (every pixel sum < 30 — the reference's td at (8,40)),
#   (2) no other dark ink in the band x 0-389 x rows 52-140 (no outline stroke, no displaced square, no second box),
#   (3) no red ink (the s-11-1-1b family's failure colour).
# One line per (run, platform), ending in "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)".
#
# Usage: python3 compose-table-body-cell.geometry.py [run-id ...]     (default: wave53-open wave53-probe wave53-final)
# Self-check (the wave-53 geometry_common contract): the REF row must print OK, else the rule is wrong → exit 1.
# Capture rows are printed, never judged into the exit code (PLAN §6 reads each line against its pre-registered verdict).
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import paths, load  # noqa: E402  (shared ref/capture path layout + loader)

SECTION, TEST = 'CSS2', 'css21-errata/s-11-1-1b-006'
SQ = (24, 56, 43, 75)          # the reference square, inclusive: x0, y0, x1, y1 (document (8,40) + the 16-px frame)
BAND = (0, 52, 390, 141)       # rows below the text (text ink ends at row 51 on every platform) to row 140


def black(p):
    return p[0] + p[1] + p[2] < 30          # the td's pure black fill (the probe's own threshold, display-table-body)


def darkish(p):
    return p[0] + p[1] + p[2] < 600         # any ink: strokes, antialiased edges, a displaced square


def red(p):
    return p[0] > 150 and p[1] < 90 and p[2] < 90


def verdict(path):
    im, px = load(path)
    if im is None:
        return 'MISSING', False
    w, h = im.size
    x0, y0, x1, y1 = SQ
    solid = sum(1 for y in range(y0, y1 + 1) for x in range(x0, x1 + 1) if black(px[x, y]))
    stray = [(x, y) for y in range(BAND[1], min(h, BAND[3])) for x in range(BAND[0], min(w, BAND[2]))
             if darkish(px[x, y]) and not (x0 <= x <= x1 and y0 <= y <= y1)]
    reds = sum(1 for y in range(min(h, 300)) for x in range(w) if red(px[x, y]))
    head = f'square block solid {solid}/400 | stray ink {len(stray)}'
    if stray:
        xs = [p[0] for p in stray]; ys = [p[1] for p in stray]
        head += f' (x {min(xs)}-{max(xs)}, rows {min(ys)}-{max(ys)})'
    head += f' | red px {reds}'
    why = []
    if solid < 400:
        why.append(f'the block x24-43 rows 56-75 is not solid ({solid}/400 black)')
    if stray:
        why.append(f'{len(stray)} ink px outside the block')
    if reds:
        why.append(f'{reds} red px')
    ok = not why
    # fix r2 (plan-skeptic N5): the WRONG verdict carries the same "→" separator as the OK one
    return head + (' → GEOMETRY OK' if ok else f' → GEOMETRY WRONG ({"; ".join(why)})'), ok


runs = sys.argv[1:] or ['wave53-open', 'wave53-probe', 'wave53-final']
ref_failed = False
for i, run in enumerate(runs):
    for label, p in paths(run, SECTION, TEST):
        if label == 'ref' and i > 0:
            continue                         # the ref is run-independent: print and self-check it once
        line, ok = verdict(p)
        print(f'{(run if label != "ref" else "ref").ljust(13)} 006 {label.ljust(8)} {line}')
        if label == 'ref' and not ok:
            print('SELF-CHECK FAILED: the ref row must print GEOMETRY OK', file=sys.stderr)
            ref_failed = True
sys.exit(1 if ref_failed else 0)
