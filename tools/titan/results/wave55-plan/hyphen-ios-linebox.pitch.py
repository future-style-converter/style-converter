#!/usr/bin/env python3
# tools/titan/results/wave55-plan/hyphen-ios-linebox.pitch.py — wave-55 L1 (BACKLOG 0(f)) planning read-out, sub-pixel.
#
# Why: hyphen-ios-linebox.linetops.py reads whole-pixel ink tops. To tell "the iOS run's line PITCH is short" from "the
# iOS <br> box is laid differently", read each line's ink CENTROID (darkness-weighted row mean over x < 140, weight =
# 255 - max channel, rows of that line's core band ± 3) and compare it with the same line of the frozen ref. Per group:
#   first   = the group's first-line centroid offset vs the ref  (what the <br> boxes above it accumulate);
#   slope   = least-squares d(offset)/d(line index) inside the group (what the run's own line pitch gives away:
#             0 when the run advances the ref's line box, negative when it advances less).
# Read-only; decodes the same 28 PNGs as .linetops.py. Usage: python3 hyphen-ios-linebox.pitch.py [runs...]
import os, sys
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
REFS = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/'
        'white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text')
RUNS = sys.argv[1:] or ['wave54-ab-drop-U3b', 'wave54-final']
DIRS = [('web', 'screenshots'), ('ios', 'ios-screenshots'), ('android', 'android-screenshots')]
DIV0 = {'001': 100, '002': 100, '003': 80, '004': 80}


def core(p):
    return max(p[0], p[1], p[2]) < 110


def load(path):
    im = Image.open(path).convert('RGB')
    return im.load(), im.size


def bands(px, size, y0):
    """Core-ink line bands below y0 in x < 140 (i-dots merged), as in the geometry probe."""
    w, h = size
    out, cur, blank = [], None, 0
    for y in range(y0, h):
        if any(core(px[x, y]) for x in range(0, min(w, 140))):
            cur = [y, y] if cur is None else [cur[0], y]; blank = 0
        elif cur is not None:
            blank += 1
            if blank > 2:
                out.append(tuple(cur)); cur = None; blank = 0
    if cur is not None:
        out.append(tuple(cur))
    return out


def centroid(px, size, top, bot):
    """Darkness-weighted mean row of the band (± 3 rows of anti-aliasing), x < 140."""
    w, _ = size
    num = den = 0.0
    for y in range(top - 3, bot + 4):
        for x in range(0, min(w, 140)):
            p = px[x, y]
            wgt = 255 - max(p[0], p[1], p[2])
            if wgt > 8:
                num += wgt * y; den += wgt
    return num / den if den else float('nan')


def grouped(path, y0):
    px, size = load(path)
    bs = bands(px, size, y0)
    gs, cur = [], []
    for b in bs:
        if cur and b[0] - cur[-1][1] >= 18:
            gs.append(cur); cur = []
        cur.append(b)
    if cur:
        gs.append(cur)
    return [[centroid(px, size, t, b) for t, b in g] for g in gs]


def slope(ys):
    n = len(ys)
    if n < 2:
        return 0.0
    mx = (n - 1) / 2; my = sum(ys) / n
    return sum((i - mx) * (y - my) for i, y in enumerate(ys)) / sum((i - mx) ** 2 for i in range(n))


for t in ('001', '002', '003', '004'):
    ref = grouped(f'{REFS}/hyphens__hyphenate-character-{t}.png', DIV0[t])
    gaps = [round(ref[i + 1][0] - ref[i][0], 2) for i in range(len(ref) - 1)]
    print(f'== hyphenate-character-{t}  ref group-first centroid gaps {gaps}  (lines/group {[len(g) for g in ref]})')
    for run in RUNS:
        for label, d in DIRS:
            p = f'{ROOT}/tools/titan/runs/{run}/sections/css-text/{d}/wpt__css-text__hyphens__hyphenate-character-{t}.png'
            if not os.path.exists(p):
                print(f'   {run:20s} {label:8s} MISSING'); continue
            got = grouped(p, DIV0[t])
            if [len(g) for g in got] != [len(g) for g in ref]:
                print(f'   {run:20s} {label:8s} lines/group {[len(g) for g in got]} ≠ ref — not compared'); continue
            offs = [[c - r for r, c in zip(rg, cg)] for rg, cg in zip(ref, got)]
            firsts = ' '.join(f'{o[0]:+.2f}' for o in offs)
            slopes = ' '.join(f'{slope(o):+.3f}' for o in offs)
            print(f'   {run:20s} {label:8s} group-first offset [{firsts}]   in-group slope px/line [{slopes}]')
