#!/usr/bin/env python3
# tools/titan/results/wave53-plan/canvas-root.geometry.py — the L3 item-A (root background image → canvas) post-gate
# GEOMETRY probe. Item B's probe is display-table-body.geometry.py (same directory).
#
# Why: these are f→P flips, but a 0.99 could come from the wrong surface (ICB-only with a white frame replays 0.879)
# or a 50-px tile-phase error (replays 0.51-0.63), and contents-root-background.geometry.mjs is a REPLAY pinned to
# wave52-ship, not a probe of a run. PLAN.md §6 R6 reads this script (plan-skeptic must-fix 2). Each line ends in
# "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)".
#
# Usage: python3 tools/titan/results/wave53-plan/canvas-root.geometry.py [run-id]     (default wave52-ship)
# Expected after L3-A on wave53-probe / wave53-final, every platform:
#   display-contents-root-background <p>        … → GEOMETRY OK   (framed surface green, text where the ref has it)
#   background-attachment-margin-root-001 <p>   … → GEOMETRY OK   (ICB painted, 16-px frame white, band phase y66)
#   background-attachment-margin-root-002 <p>   … → GEOMETRY OK   (same, band phase y16 — `fixed` → ICB origin)
# The ref rows must print GEOMETRY OK (exit 1 otherwise). Decodes 12 PNGs.
import sys
from geometry_common import paths, load, bbox, near

run = sys.argv[1] if len(sys.argv) > 1 else 'wave52-ship'
ref_failed = False
F = 16                                                     # capture-frame chrome width (CANVAS_PAD_PX)
green = lambda p: p[0] <= 8 and abs(p[1] - 128) <= 8 and p[2] <= 8          # rgb(0,128,0), the 1×1 data-URI tile
ink = lambda p: max(p) < 64                                                 # black text cores, on white or on green
white = lambda p: min(p) >= 250
nonwhite = lambda p: not white(p)


def frame_pixels(w, h):
    # The 16-px capture frame around the 358×568 ICB.
    return [(x, y) for y in range(h) for x in range(w) if x < F or x >= w - F or y < F or y >= h - F]


def contents_root(px, w, h, ref):
    # css-backgrounds-3 §2.11.2: the root's background (here a green image) becomes the CANVAS background — the whole
    # frame, chrome included (F2 framed surface: a 1×1 uniform tile). Rules: ≥ 98 % of the frame green, ≥ 99 % of the
    # 16-px chrome green, the text bbox within ±3 px of the ref's.
    _, n = bbox(px, w, h, green)
    fr = frame_pixels(w, h)
    chrome = sum(1 for x, y in fr if green(px[x, y])) / len(fr)
    tb, _ = bbox(px, w, h, ink)
    m = {'green': round(n / (w * h), 4), 'chrome': round(chrome, 4), 'text': tb}
    if ref is None: ref = m
    if m['green'] < 0.98: return m, f'green {m["green"]} < 0.98'
    if chrome < 0.99: return m, f'frame chrome green {m["chrome"]} < 0.99'
    if tb is None or not near(tb, ref['text'], 3): return m, f'text bbox {tb} vs ref {ref["text"]}'
    return m, None


def margin_root(px, w, h, ref):
    # §3.4 + F1: tiles cover the ICB only (the frame stays white: a non-uniform stack is never stretched into the
    # chrome), and the 100-px gradient band restarts where the layer's origin puts it — the root's padding box (y66) for
    # 001's `scroll` layer, the viewport/ICB (y16) for 002's `fixed` one. Band starts = rows where G jumps by > 60
    # (navy → green) on the x195 column, inside the ICB. Rules: painted bbox ±1, frame white, band starts ±1.
    bb, _ = bbox(px, w, h, nonwhite)
    col = [px[w // 2, y] for y in range(h)]
    starts = [y for y in range(F + 1, h - F) if col[y][1] - col[y - 1][1] > 60]
    if green(col[F]) or (col[F][1] > 100 and col[F][2] < 40): starts = [F] + starts   # a band starting at the ICB top
    frame_dirty = sum(1 for x, y in frame_pixels(w, h) if nonwhite(px[x, y]))
    m = {'painted': bb, 'starts': starts, 'frameDirty': frame_dirty}
    if ref is None: ref = m
    if bb is None or not near(bb, ref['painted'], 1): return m, f'painted {bb} vs ref {ref["painted"]}'
    if frame_dirty: return m, f'{frame_dirty} non-white px in the 16-px frame'
    if not near(starts, ref['starts'], 1): return m, f'band starts {starts} vs ref {ref["starts"]}'
    return m, None


for section, test, rule in (('css-display', 'display-contents-root-background', contents_root),
                            ('css-backgrounds', 'background-attachment-margin-root-001', margin_root),
                            ('css-backgrounds', 'background-attachment-margin-root-002', margin_root)):
    ref = None
    for label, p in paths(run, section, test):
        im, px = load(p)
        if im is None:
            print(f'{test} {label:8} MISSING {p}'); continue
        m, why = rule(px, *im.size, ref)
        if label == 'ref':
            ref = m
        verdict = 'GEOMETRY OK' if why is None else f'GEOMETRY WRONG ({why})'
        print(f'{test} {label:8} {m} → {verdict}')
        if label == 'ref' and why is not None:
            ref_failed = True
sys.exit(1 if ref_failed else 0)
