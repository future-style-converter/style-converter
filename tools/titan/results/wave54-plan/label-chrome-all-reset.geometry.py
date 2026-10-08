#!/usr/bin/env python3
# tools/titan/results/wave54-plan/label-chrome-all-reset.geometry.py — the GEOMETRY probe of the wave-54 family
# "label-chrome-all-reset" (BACKLOG 0(e)): the six all-then-color canvases × three platforms.
#
# Why: the tripwire (tools/visual/label-chrome-tripwire.test.mjs) asserts a label on EVERY baseline stem, while the
# shared chrome contract (docs/DYNAMIC_CAPTURE.md §5 "When") draws one iff the composed capture root has NO composed
# children and NO non-empty text. This probe reads the label-due bit from the FIXTURE STRUCTURE (children / `_text` —
# independent of every pixel and every runtime), decodes each PNG, and checks the picture the contract asks for:
#   label-due  → every glyph pixel of the name's label (block-font.json at frame (8,6), truncated to the frame) is
#                non-ground, and the #2ecc71 fill box matches the row's `_expect` box at the content origin (16,16);
#   exempt     → zero glyph pixels lit, band rows 0..15 pure ground, and the same fill-box check.
# Each line ends in "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)". `--naive` applies the CURRENT tripwire assumption
# (every stem label-due) and must print WRONG on 001/003/005 — the rule can fail. Two summary lines then test the two
# competing explanations: label drawn ⇔ label-due (the contract), and label drawn ⇔ captured root declares no `all`
# (the BACKLOG 0(e) premise "the `all` reset reaches the chrome").
#
# Usage: python3 tools/titan/results/wave54-plan/label-chrome-all-reset.geometry.py [DIR|baseline] [--naive]
#   DIR defaults to label-chrome-all-reset.seeded-77fe41e8/ (the 18 PNGs of 77fe41e8, `git show` read-only);
#   `baseline` reads tools/visual/baseline/ (after the seeding step). Exit 1 on any WRONG line.
import json, os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
args = [a for a in sys.argv[1:] if not a.startswith('--')]
NAIVE = '--naive' in sys.argv
SRC = args[0] if args else os.path.join(HERE, 'label-chrome-all-reset.seeded-77fe41e8')
if SRC == 'baseline':
    SRC = os.path.join(ROOT, 'tools', 'visual', 'baseline')
FONT = json.load(open(os.path.join(ROOT, 'tools', 'visual', 'block-font.json')))
FIXTURE = 'fixtures/combinations/all-then-color.json'
GROUND = (0x1A, 0x1A, 0x2E)
PLATFORMS = ('Android', 'iOS', 'web')
# Capture order = the device flatten (pre-order, children standalone: neither parent creates a paint context) —
# printed from the tracked IR by label-chrome-all-reset.census.mjs (C).
STEMS = ['000_ATC_AllThenProps', '001_ATC_PropsThenAll_InGreenParent', '002_reset',
         '003_ATC_InitialUnderRedParent', '004_span', '005_ATC_DirectionSurvives']


def nodes(fixture):
    """name → fixture node (all depths)."""
    out = {}
    def walk(m):
        for k, v in (m or {}).items():
            out[k] = v
            walk(v.get('children'))
    walk(json.load(open(os.path.join(ROOT, fixture)))['components'])
    return out


NODES = nodes(FIXTURE)


def label_due(v):
    return not (v.get('children') or {}) and not (isinstance(v.get('_text'), str) and v['_text'])


def expected_box(name):
    """The `_expect` box of the capture root; a standalone child captures its own box (or none: `reset` paints nothing)."""
    if name == 'reset':
        return None                                   # all: revert LAST → UA block, auto size, no background
    if name == 'span':
        return (200, 60)                              # all: initial; display: block; 200x60 green (FIRST → kept after)
    return tuple(NODES[name]['_expect']['box'])


def glyph_pixels(name, width):
    """Tripwire P: the label's set-bit pixels at frame (8,6) — normalize, truncate 8 + 6n ≤ width − 8."""
    chars = ''.join(c if c in FONT['glyphs'] else '-' for c in name.replace('_', ' ').upper())
    budget = width - 16
    n = 0 if budget <= 0 else min(len(chars), budget // FONT['advance'])
    pts = []
    for i in range(n):
        rows = FONT['glyphs'][chars[i]]
        for r in range(FONT['cell']['height']):
            for c in range(FONT['cell']['width']):
                if rows[r][c] == '1':
                    pts.append((8 + i * FONT['advance'] + c, 6 + r))
    return pts


ground = lambda p: all(abs(p[c] - GROUND[c]) <= 1 for c in range(3))
green = lambda p: abs(p[0] - 46) <= 8 and abs(p[1] - 204) <= 8 and abs(p[2] - 113) <= 8


def green_box(px, w, h):
    xs0 = ys0 = 10 ** 9; xs1 = ys1 = -1
    for y in range(h):
        for x in range(w):
            if green(px[x, y]):
                xs0 = min(xs0, x); ys0 = min(ys0, y); xs1 = max(xs1, x + 1); ys1 = max(ys1, y + 1)
    return None if xs1 < 0 else (xs0, ys0, xs1, ys1)


wrong = 0
drawn_vs_due = drawn_vs_noall = total = 0
bands = {}
for stem in STEMS:
    name = stem[4:]
    v = NODES[name]
    due = True if NAIVE else label_due(v)
    has_all = 'all' in (v.get('properties') or {})
    for plat in PLATFORMS:
        p = os.path.join(SRC, f'{plat}__{stem}.png')
        if not os.path.exists(p):
            print(f'{stem:36} {plat:8} MISSING {os.path.relpath(p, ROOT)} → GEOMETRY WRONG (missing capture)'); wrong += 1; continue
        im = Image.open(p).convert('RGB'); px = im.load(); w, h = im.size
        P = glyph_pixels(name, w)
        lit = sum(1 for x, y in P if not ground(px[x, y]))
        band_clean = all(ground(px[x, y]) for y in range(min(16, h)) for x in range(w))
        bands.setdefault(stem, []).append(im.crop((0, 0, w, min(16, h))).tobytes())
        gb = green_box(px, w, h)
        eb = expected_box(name)
        box_ok = (gb is None) if eb is None else (gb is not None and gb[0] == 16 and gb[1] == 16 and
                                                  abs((gb[2] - gb[0]) - eb[0]) <= 1 and abs((gb[3] - gb[1]) - eb[1]) <= 1)
        drawn = len(P) > 0 and lit == len(P)
        total += 1
        drawn_vs_due += drawn == label_due(v)
        drawn_vs_noall += drawn == (not has_all)
        why = None
        if due and lit != len(P):
            why = f'label absent: {len(P) - lit}/{len(P)} glyph px ground'
        elif not due and lit:
            why = f'label on an exempt capture: {lit}/{len(P)} glyph px lit'
        elif not due and not band_clean:
            why = 'band rows 0..15 carry paint on an exempt capture'
        elif not box_ok:
            why = f'fill box {gb} vs expected {eb} at (16,16)'
        verdict = 'GEOMETRY OK' if why is None else f'GEOMETRY WRONG ({why})'
        wrong += why is not None
        print(f'{stem:36} {plat:8} {w}x{h} due={str(due):5} rootAll={str(has_all):5} label {lit}/{len(P)} '
              f'band-ground={str(band_clean):5} fill={gb} → {verdict}')
for stem, bs in bands.items():
    same = len(bs) == 3 and bs[0] == bs[1] == bs[2]
    print(f'{stem:36} band rows 0..15 byte-identical ×3: {same}')
print(f'label drawn ⇔ label-due (fixture: no children, no _text): {drawn_vs_due}/{total}')
print(f'label drawn ⇔ captured root declares no `all` (BACKLOG 0(e) premise): {drawn_vs_noall}/{total}')
sys.exit(1 if wrong else 0)
