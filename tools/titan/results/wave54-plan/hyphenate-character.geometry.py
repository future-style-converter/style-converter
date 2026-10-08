#!/usr/bin/env python3
# tools/titan/results/wave54-plan/hyphenate-character.geometry.py — the wave-54 hyphenate-character post-gate GEOMETRY probe.
#
# Why: SSIM cannot tell a faithful 0.98 from a degenerate one on these tests. The wave-53 plan labelled any
# hyphenate-character-00x flip DEGENERATE by construction (U+2010 baked where the ref paints "" / U+2022 / "/-/"), and
# the wave-54 replay (hyphenate-character.replay.out.txt) shows a br-height fix ALONE would flip -003 ios to 0.9566 with
# the hyphen still wrong. This probe reads the PICTURE: for every line of the four word groups in the <div> it compares
# the line's ink top (±4 px), its ink right edge (±3 px) and the ink height of its LAST glyph cell (±2 px: a hyphen is
# 1-2 rows, the bullet ~5, "/-/" ends in a 13-row slash, "" leaves the syllable's own last letter) with the frozen ref.
# The <p> preamble is NOT read (its lost <b> is the corpus-wide 'inline-run-merged' approximation, out of family).
#
# Each line ends in "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)". The why names the FIRST mismatch of each kind:
#   lines   — a group's line count differs from the ref's (the hyphenate string's WIDTH decides the breaks);
#   offset  — a line's top is off by > 4 px (the extractor's 20 px br after interleaved text adds a blank line);
#   glyph   — a line's right edge or last-cell height differs (the hyphenate string itself).
# Usage: python3 hyphenate-character.geometry.py [run-id]      (default wave53-final)
#        python3 hyphenate-character.geometry.py --replay <G|B|GB|GW>   reads hyphenate-character-replay/<t>-<plat>-<tag>.png
#        (the planning replays: proves the probe separates the glyph defect from the br-height defect)
# Self-check: every ref row must print GEOMETRY OK (exit 1 otherwise). Decodes 12 PNGs.
# Expected after the family lands (G = converter + both natives, B = extractor br hunk), on the closing gate:
#   001/003/004 web|ios|android → GEOMETRY OK. With G alone: "offset" stays WRONG on every row (B). With B alone: the
#   -001/-003 rows stay WRONG on "glyph" on the natives (a pass there would be DEGENERATE).
import os, sys
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
REFS = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/'
        'white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text')
REPLAY = sys.argv[2] if len(sys.argv) > 2 and sys.argv[1] == '--replay' else None
run = 'wave53-final' if REPLAY or len(sys.argv) < 2 else sys.argv[1]
SEC = f'{ROOT}/tools/titan/runs/{run}/sections/css-text'
HERE = os.path.dirname(os.path.abspath(__file__))
DIRS = [('ref', None), ('web', 'screenshots'), ('ios', 'ios-screenshots'), ('android', 'android-screenshots')]
DIV0 = {'001': 100, '003': 80, '004': 80}   # first row below the <p> (hyphenate-character.bands.py)


def ink(p):
    # max-channel < 200: keeps Android's light anti-aliased hyphen (wave-53 L2 skeptic D1), white is never ink.
    # Used for the END-CELL measurement only.
    return max(p[0], p[1], p[2]) < 200


def core(p):
    # Glyph cores (max-channel < 110) for LINE SEGMENTATION: at < 200 the anti-aliased rows of a descender ("hy",
    # "ple") touch the next line's ascender and two lines merge into one band. Android's hyphen core is 114 grey,
    # so a line that is ONLY a hyphen would vanish here — the iOS lone-"-" line is still seen (TextKit ink is darker).
    return max(p[0], p[1], p[2]) < 110


def lines(path, y0):
    """Text lines of the <div> (x < 140): [(top, bottom, xmin, xmax, last_cell_height)], i-dots merged (gap <= 2; a descender-to-ascender gap is >= 3)."""
    im = Image.open(path).convert('RGB'); px = im.load(); w, h = im.size
    rows = []
    for y in range(y0, h):
        xs = [x for x in range(0, min(w, 140)) if core(px[x, y])]
        rows.append((y, xs))
    out, cur, blank = [], None, 0
    for y, xs in rows:
        if xs:
            if cur is None:
                cur = [y, y, min(xs), max(xs)]
            else:
                cur[1] = y; cur[2] = min(cur[2], min(xs)); cur[3] = max(cur[3], max(xs))
            blank = 0
        elif cur is not None:
            blank += 1
            if blank > 2:
                out.append(cur); cur = None; blank = 0
    if cur is not None:
        out.append(cur)
    res = []
    for top, bot, x0, x1 in out:
        # widen the core band's right edge with the light ink rule (Android's grey hyphen), same rows
        x1 = max([x1] + [x for y in range(top, bot + 1) for x in range(x1, min(w, 140)) if ink(px[x, y])])
        # the last glyph cell: ink rows among the 5 columns ending at the line's right edge (half a 9.6 px mono
        # cell: the hyphen / bullet / slash alone, never the letter before it)
        ys = [y for y in range(top - 2, bot + 3) if any(ink(px[x, y]) for x in range(max(0, x1 - 4), x1 + 1))]
        res.append((top, bot, x0, x1, (max(ys) - min(ys) + 1) if ys else 0))
    return res


def groups(ls):
    """Split lines into word groups at a blank band >= 18 px (one blank line is ~25 px; a line gap <= 14, iOS lone-hyphen line included)."""
    gs, cur = [], []
    for ln in ls:
        if cur and ln[0] - cur[-1][1] >= 18:
            gs.append(cur); cur = []
        cur.append(ln)
    if cur:
        gs.append(cur)
    return gs


ref_failed = False
for t in ('001', '003', '004'):
    ref_path = f'{REFS}/hyphens__hyphenate-character-{t}.png'
    ref = groups(lines(ref_path, DIV0[t]))
    for label, d in DIRS:
        p = ref_path if d is None else (f'{HERE}/hyphenate-character-replay/{t}-{label}-{REPLAY}.png' if REPLAY
                                         else f'{SEC}/{d}/wpt__css-text__hyphens__hyphenate-character-{t}.png')
        if not os.path.exists(p):
            print(f'hyphenate-character-{t} {label:8s} MISSING → GEOMETRY WRONG (no capture)'); continue
        got = groups(lines(p, DIV0[t]))
        why = {}
        counts = [len(g) for g in got]
        if counts != [len(g) for g in ref]:
            why['lines'] = f'groups {counts} lines vs ref {[len(g) for g in ref]}'
        for gi, (rg, cg) in enumerate(zip(ref, got)):
            for li, (r, c) in enumerate(zip(rg, cg)):
                tag = f'group {gi + 1} line {li + 1}'
                if 'offset' not in why and abs(r[0] - c[0]) > 4:
                    why['offset'] = f'{tag} top y{c[0]} vs ref y{r[0]}'
                if 'glyph' not in why and (abs(r[3] - c[3]) > 3 or abs(r[4] - c[4]) > 2):
                    why['glyph'] = f'{tag} ink x{c[2]}-{c[3]} end-cell {c[4]}px vs ref x{r[2]}-{r[3]} end-cell {r[4]}px'
        summary = ' '.join(f'{len(g)}' for g in got)
        verdict = 'GEOMETRY OK' if not why else 'GEOMETRY WRONG (' + '; '.join(f'{k}: {v}' for k, v in why.items()) + ')'
        print(f'hyphenate-character-{t} {label:8s} groups {summary:9s} → {verdict}')
        if label == 'ref' and why:
            ref_failed = True
if ref_failed:
    print('SELF-CHECK FAILED (a ref row is not OK)')
    sys.exit(1)
