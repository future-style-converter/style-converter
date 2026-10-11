#!/usr/bin/env python3
# tools/titan/results/wave55-plan/cbb-android.geometry.py — the supplementary GEOMETRY probe for wave-55 lane
# L2-cbb-android, unit U2 (brief: cbb-android.md §7). It runs NEXT TO the re-used
# tools/titan/results/wave54-plan/compose-wpt-content-box-cb.geometry.py, never instead of it.
#
# Why: the re-used probe checks the strict-green bbox and the pink fail ink. On backdrop-filter-nested-border-radius-clip-3
# that is blind to three wrong pictures U2 could produce, because #outer's padding-box clip (x16..235 / y16..135) hides
# where #inner ends: an UNCLIPPED #middle overflow (grey at x236..245), a DOUBLED band (#inner 220×120 cut square by the
# clip), and a band that grows #middle but leaves #outer's blue right band showing. This probe adds two named reasons to
# the bbox rule (CSS 2.1 §10.2 + css-sizing-3 §3: #middle's border box = 100% × 200 + 10 + 10 = 220 wide, 120 tall, and
# css-overflow-3 §3: #outer clips it at its padding edge):
#   bands:  the colour runs along row y=80 and column x=120 — blue #66ccff, grey #cccccc, green (0,128,0) — equal the
#           ref's run list, each edge ±1 px (ref: blue[16..25] grey[26..35] green[36..235] / …green[36..135]);
#   corner: the green count in EACH of the four 20×20 squares at #inner's corners (x36..55 / x216..235 × y36..55 /
#           y116..135) equals the ref's within ±8 px (ref [251, 250, 252, 252]: the radius-25 arcs leave grey there; a
#           square or missing corner moves it by ≥ 80). Plan-skeptic round 1 (instrument-teeth M2): the wave-55 draft
#           counted the bottom-right square only, so three square corners passed;
#   mass:   the strict-green count equals the ref's within max(40, 0.5 %) — a gutted #inner that keeps only the two
#           scanlines this probe reads, its outline and one corner (957 px of 19365) passed the draft. REPLACED in round 2:
#   mask:   (plan-skeptic round 2, instrument-teeth M2) |strict-green(capture) XOR strict-green(ref)| ≤ 40 px pixelwise —
#           a 7×7 hole clear of row 80 / column 120 / the corner squares passed the mass (72 px XOR; passing ≤ 25);
#   fail:   the backdrop-source pink (255,127,255)±12 and strict red px = the ref's ±2 — a 2×40 pink sliver right of
#           #inner above row 80 (80 px) passed every read (every passing picture reads 0).
# Each line ends in "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<reasons>)"; the ref row must print OK (exit 1 otherwise).
#
# Usage: /opt/homebrew/bin/python3.12 tools/titan/results/wave55-plan/cbb-android.geometry.py [run-id]  (default wave54-final)
#        … --png <label>=<file> [...]   read extra PNGs (the planning fakes) against the same ref.
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import paths, load, bbox, near  # noqa: E402
import numpy as np  # noqa: E402  (round 2: the pixelwise green mask, the fail ink)

TEST = 'backdrop-filter-nested-border-radius-clip-3'
# The re-used probe's own predicates, verbatim (compose-wpt-content-box-cb.geometry.py).
green = lambda p: p[0] < 8 and abs(p[1] - 128) < 8 and p[2] < 8
CORNERS = ((36, 36), (216, 36), (36, 116), (216, 116))     # top-left of each 20×20 corner square of #inner (x36..235, y36..135)
CLASSES = [('blue', (102, 204, 255)), ('grey', (204, 204, 204)), ('green', (0, 128, 0)), ('pink', (255, 127, 255))]


def cls(p):
    """Flat-colour class ±10 per channel; anything else (white canvas, antialiased arcs) is not a run."""
    for name, c in CLASSES:
        if all(abs(p[i] - c[i]) <= 10 for i in range(3)):
            return name
    return None


def runs(seq):
    """[(name, start, end)] of consecutive equal non-None classes along a line."""
    out = []
    for i, n in seq:
        if n is None:
            continue
        if out and out[-1][0] == n and out[-1][2] == i - 1:
            out[-1][2] = i
        else:
            out.append([n, i, i])
    return [tuple(r) for r in out]


bad = lambda p: (p[0] > 200 and p[1] < 80 and p[2] < 80) or (p[0] > 240 and abs(p[1] - 127) < 12 and p[2] > 240)   # the re-used probe's fail ink


def green_mask(px, w, h):
    """Strict-green mask as a bool array (round 2: the pixelwise read)."""
    return np.array([[green(px[x, y]) for x in range(w)] for y in range(h)], bool)


def measure(px, w, h):
    """bbox of strict green, the two run lists, the corner-square green count, the green mask and the fail ink."""
    gb, gn = bbox(px, w, h, green)
    _, bn = bbox(px, w, h, bad)
    row = runs([(x, cls(px[x, 80])) for x in range(0, min(w, 260))])
    col = runs([(y, cls(px[120, y])) for y in range(0, min(h, 160))])
    # the four corner squares of #inner, in the order TL, TR, BL, BR (the radius-25 arcs; CSS backgrounds-3 §5.3)
    corner = [sum(1 for y in range(cy, cy + 20) for x in range(cx, cx + 20) if green(px[x, y])) for cx, cy in CORNERS]
    return {'green': gb, 'greenPx': gn, 'row': row, 'col': col, 'corner': corner, 'mask': green_mask(px, w, h), 'failInkPx': bn}


def xor_px(m, r):
    """|m XOR r| on the union canvas (a size difference counts every green pixel outside the overlap)."""
    h, w = max(m.shape[0], r.shape[0]), max(m.shape[1], r.shape[1])
    pm, pr = np.zeros((h, w), bool), np.zeros((h, w), bool)
    pm[:m.shape[0], :m.shape[1]] = m; pr[:r.shape[0], :r.shape[1]] = r
    return int((pm ^ pr).sum())


def same_runs(a, b):
    return len(a) == len(b) and all(x[0] == y[0] and abs(x[1] - y[1]) <= 1 and abs(x[2] - y[2]) <= 1 for x, y in zip(a, b))


def fmt(r):
    return ' '.join(f'{n}[{s}..{e}]' for n, s, e in r)


def verdict(m, ref):
    why = []
    if (m['green'] is None) != (ref['green'] is None) or (m['green'] and not near(m['green'], ref['green'], 1)):
        why.append(f'green {m["green"]} vs ref {ref["green"]}')
    if not same_runs(m['row'], ref['row']) or not same_runs(m['col'], ref['col']):
        why.append(f'bands row {fmt(m["row"])} / col {fmt(m["col"])}')
    if any(abs(a - b) > 8 for a, b in zip(m['corner'], ref['corner'])):
        why.append(f'corner {m["corner"]} vs ref {ref["corner"]} (TL, TR, BL, BR ±8)')
    xr = xor_px(m['mask'], ref['mask'])
    if xr > 40:
        why.append(f'mask: green XOR ref {xr} px > 40 (a hole / gutted / partial #inner)')            # round 2 (M2)
    if abs(m['failInkPx'] - ref['failInkPx']) > 2:
        why.append(f'failure ink {m["failInkPx"]} px vs ref {ref["failInkPx"]} ±2 (pink / red showing)')   # round 2 (M2)
    return 'GEOMETRY OK' if not why else 'GEOMETRY WRONG (' + '; '.join(why) + ')'


args = sys.argv[1:]
extra = []
if '--png' in args:
    i = args.index('--png')
    extra = [a.split('=', 1) for a in args[i + 1:]]
    args = args[:i]
run = args[0] if args else 'wave54-final'
ref = None
ref_failed = False
rows = [(label, p) for label, p in paths(run, 'filter-effects', TEST)] + [(label, p) for label, p in extra]
for label, p in rows:
    im, px = load(p)
    if im is None:
        print(f'{TEST} {label:8} MISSING')
        continue
    m = measure(px, *im.size)
    if label == 'ref':
        ref = m
    v = verdict(m, ref)
    print(f'{TEST} {label:8} {{green {m["green"]} greenPx {m["greenPx"]} corner {m["corner"]} row {fmt(m["row"])} xor {xor_px(m["mask"], ref["mask"])} '
          f'failInk {m["failInkPx"]}}} → {v}')
    if label == 'ref' and v != 'GEOMETRY OK':
        ref_failed = True
sys.exit(1 if ref_failed else 0)
