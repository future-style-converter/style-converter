#!/usr/bin/env python3
# tools/titan/results/wave55-plan/hyphen-ios-linebox.linetops.py — wave-55 L1 (BACKLOG 0(f)) planning read-out.
#
# Why: the drop-U3b arm says U3b (d64d4c6e, a line-start <br> takes its host's own declared line box, 20 -> 19.2 px)
# costs every iOS hyphenate-character cell. Before naming a mechanism, measure WHERE each line of the <div> sits on every
# platform, with and without U3b, against the frozen ref: per line, its ink top (glyph cores, max-channel < 110 — the
# segmentation hyphenate-character.geometry.py uses), and per group the first-line top and the in-group pitch.
# Read-only: decodes 4 refs + 4 tests x 3 platforms x 2 runs = 28 PNGs. No device, no build.
# Usage: python3 hyphen-ios-linebox.linetops.py [runA=wave54-ab-drop-U3b] [runB=wave54-final]
import os, sys
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
REFS = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/'
        'white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text')
RUNS = sys.argv[1:3] if len(sys.argv) >= 3 else ['wave54-ab-drop-U3b', 'wave54-final']
DIRS = [('web', 'screenshots'), ('ios', 'ios-screenshots'), ('android', 'android-screenshots')]
# First row below the <p> preamble (hyphenate-character.bands.py; 002 shares 001's preamble).
DIV0 = {'001': 100, '002': 100, '003': 80, '004': 80}


def core(p):
    # Glyph cores only: anti-aliased rows of a descender would merge two lines (same rule as the geometry probe).
    return max(p[0], p[1], p[2]) < 110


def lines(path, y0):
    """[(top, bottom)] of the ink lines in x < 140 below y0; i-dots merged (a gap of <= 2 blank rows)."""
    im = Image.open(path).convert('RGB'); px = im.load(); w, h = im.size
    out, cur, blank = [], None, 0
    for y in range(y0, h):
        hit = any(core(px[x, y]) for x in range(0, min(w, 140)))
        if hit:
            cur = [y, y] if cur is None else [cur[0], y]; blank = 0
        elif cur is not None:
            blank += 1
            if blank > 2:
                out.append(tuple(cur)); cur = None; blank = 0
    if cur is not None:
        out.append(tuple(cur))
    return out


def groups(ls):
    """Word groups split at a blank band >= 18 px (a blank line is ~19-25 px; an in-group line gap <= 14)."""
    gs, cur = [], []
    for ln in ls:
        if cur and ln[0] - cur[-1][1] >= 18:
            gs.append(cur); cur = []
        cur.append(ln)
    if cur:
        gs.append(cur)
    return gs


def preamble_top(path):
    """Top row of the <p> preamble's first line (any core ink in rows 0..60): the page-level anchor."""
    im = Image.open(path).convert('RGB'); px = im.load(); w, _ = im.size
    for y in range(0, 60):
        if any(core(px[x, y]) for x in range(0, w)):
            return y
    return None


for t in ('001', '002', '003', '004'):
    refp = f'{REFS}/hyphens__hyphenate-character-{t}.png'
    rg = groups(lines(refp, DIV0[t]))
    rtops = [[l[0] for l in g] for g in rg]
    print(f'== hyphenate-character-{t}  ref preamble y{preamble_top(refp)}  group first-line tops {[g[0] for g in rtops]}'
          f'  lines/group {[len(g) for g in rtops]}')
    print(f'   ref   line tops {rtops}')
    for run in RUNS:
        for label, d in DIRS:
            p = f'{ROOT}/tools/titan/runs/{run}/sections/css-text/{d}/wpt__css-text__hyphens__hyphenate-character-{t}.png'
            if not os.path.exists(p):
                print(f'   {run:20s} {label:8s} MISSING'); continue
            cg = groups(lines(p, DIV0[t]))
            ctops = [[l[0] for l in g] for g in cg]
            # Per-line delta vs ref where the group/line exists in both (positive = capture is LOWER than the ref).
            deltas = [[c - r for r, c in zip(rgi, cgi)] for rgi, cgi in zip(rtops, ctops)]
            firsts = [g[0] - rg0[0] for g, rg0 in zip(ctops, rtops)]
            print(f'   {run:20s} {label:8s} pre y{preamble_top(p)}  lines/group {[len(g) for g in ctops]}'
                  f'  group-first Δ {firsts}  per-line Δ {deltas}')
