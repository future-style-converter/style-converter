#!/usr/bin/env python3
# tools/titan/results/wave55-plan/android-paint-order.replay.py — planning replays for lane L6 (android-paint-order).
#
# Why: the two units of the brief change WHICH paint wins where a parent's border band and a descendant's paint
# overlap (CSS 2.1 Appendix E: the band paints first, the descendant over it). They move no box. So the replay is a
# pixel-exact model of exactly that, on the platform's OWN capture (wave54-final android), nothing else touched:
#   U1  backdrop-filter-clip-rect — the 416 overlap pixels (rows 166-167 / 218-219, x163-266, where the red .menu /
#       .menu2 borders cross the blue .navbar border) take the frozen ref's pixel (the children's red, painted last).
#   U2  the legacy-route family — every "crossing" run (1..4 dark parent-border pixels with the child's fill on both
#       sides, horizontally or vertically; geometry probe `crossings`) takes the fill colour of its left/top neighbour:
#       the child's paint over the band. The child's OWN border (flex: dotted purple) is left as captured — a lower
#       bound on the gain.
# Writes android-paint-order-replay/<stem>-android-<U1|U2>.png; scored by android-paint-order.replay-score.mjs.
# A replay never runs native layout: it predicts a SCORE, never a picture label (BACKLOG "Wave 53 lessons").
# Usage: python3 android-paint-order.replay.py [run-id=wave54-final]   (decodes 2 + 26 PNGs)
import os
import sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
REFS = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/'
        'white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin')
run = sys.argv[1] if len(sys.argv) > 1 else 'wave54-final'
OUT = os.path.join(HERE, 'android-paint-order-replay')
os.makedirs(OUT, exist_ok=True)


def cap(section, test):
    """The android composed capture of <section>/<test>.html in `run`."""
    return f'{ROOT}/tools/titan/runs/{run}/sections/{section}/android-screenshots/wpt__{section}__{test.replace("/", "__")}.png'


def black(p):
    """A dark parent border pixel (black 1-3 px bands in every U2 carrier)."""
    return p[0] + p[1] + p[2] < 150


def teal(p):
    """CSS teal (0,128,128), the grid abspos children's background."""
    return p[0] <= 40 and 100 <= p[1] <= 160 and 100 <= p[2] <= 160


def yellow(p):
    """CSS yellow (255,255,0), the flex abspos children's background."""
    return p[0] >= 200 and p[1] >= 200 and p[2] <= 80


def paint_crossings(im, fill, maxrun=4):
    """Repaint every crossing run with the fill colour of the pixel before it; returns the repainted pixel count."""
    px = im.load()
    w, h = im.size
    n = 0
    # horizontal runs first, then vertical, each on the picture as it stands
    for y in range(h):
        x = 1
        while x < w - 1:
            if black(px[x, y]) and fill(px[x - 1, y]):
                k = x
                while k < w and black(px[k, y]) and k - x < maxrun:
                    k += 1
                if k < w and fill(px[k, y]):
                    for xx in range(x, k):
                        px[xx, y] = px[x - 1, y]
                        n += 1
                x = k
            else:
                x += 1
    for x in range(w):
        y = 1
        while y < h - 1:
            if black(px[x, y]) and fill(px[x, y - 1]):
                k = y
                while k < h and black(px[x, k]) and k - y < maxrun:
                    k += 1
                if k < h and fill(px[x, k]):
                    for yy in range(y, k):
                        px[x, yy] = px[x, y - 1]
                        n += 1
                y = k
            else:
                y += 1
    return n


# U1: the 416 overlap pixels take the ref's pixel.
ref = Image.open(f'{REFS}/filter-effects/backdrop-filter-clip-rect.png').convert('RGB')
im = Image.open(cap('filter-effects', 'backdrop-filter-clip-rect')).convert('RGB')
rp, ip = ref.load(), im.load()
changed = 0
for y in (166, 167, 218, 219):
    for x in range(163, 267):
        if ip[x, y] != rp[x, y]:
            ip[x, y] = rp[x, y]
            changed += 1
im.save(f'{OUT}/backdrop-filter-clip-rect-android-U1.png')
print(f'U1 backdrop-filter-clip-rect android: {changed} px repainted')

# U2: the crossing runs take the child's fill. The carrier list is the crossings scan's Android-only rows
# (android-paint-order.crossings-scan.out.txt: android excess >= 20 over the ref, web and iOS excess <= 5); the fill
# predicate is the scan's generic one (a saturated, non-dark colour equal on both ends of the run).
def generic_fill(p):
    """A saturated non-dark pixel: the scan's `fill` (max-min > 60, channel sum >= 150)."""
    return max(p) - min(p) > 60 and sum(p) >= 150


def paint_crossings_generic(im, maxrun=4):
    """paint_crossings with the generic fill AND the scan's equal-ends rule (|Δ| <= 8 per channel)."""
    px = im.load()
    w, h = im.size
    same = lambda a, b: max(abs(a[i] - b[i]) for i in range(3)) <= 8
    n = 0
    for y in range(h):
        x = 1
        while x < w - 1:
            if black(px[x, y]) and generic_fill(px[x - 1, y]):
                k = x
                while k < w and black(px[k, y]) and k - x < maxrun:
                    k += 1
                if k < w and generic_fill(px[k, y]) and same(px[x - 1, y], px[k, y]):
                    for xx in range(x, k):
                        px[xx, y] = px[x - 1, y]
                        n += 1
                x = k
            else:
                x += 1
    for x in range(w):
        y = 1
        while y < h - 1:
            if black(px[x, y]) and generic_fill(px[x, y - 1]):
                k = y
                while k < h and black(px[x, k]) and k - y < maxrun:
                    k += 1
                if k < h and generic_fill(px[x, k]) and same(px[x, y - 1], px[x, k]):
                    for yy in range(y, k):
                        px[x, yy] = px[x, y - 1]
                        n += 1
                y = k
            else:
                y += 1
    return n


U2 = [
    ('css-flexbox', 'abspos/flex-abspos-staticpos-align-self-safe-001'),
    ('css-flexbox', 'abspos/flex-abspos-staticpos-align-self-safe-002'),
    ('css-flexbox', 'abspos/flex-abspos-staticpos-justify-self-001'),
    ('css-flexbox', 'abspos/flex-abspos-staticpos-margin-001'),
    ('css-flexbox', 'abspos/flex-abspos-staticpos-margin-002'),
    ('css-flexbox', 'abspos/flex-abspos-staticpos-margin-003'),
    ('css-flexbox', 'abspos/flex-abspos-staticpos-fallback-justify-content-001'),
    ('css-grid', 'abspos/grid-abspos-staticpos-align-self-001'),
    ('css-grid', 'abspos/grid-abspos-staticpos-align-self-002'),
    ('css-grid', 'abspos/grid-abspos-staticpos-align-self-img-001'),
    ('css-grid', 'abspos/grid-abspos-staticpos-align-self-img-002'),
    ('css-grid', 'abspos/grid-abspos-staticpos-align-self-rtl-001'),
    ('css-grid', 'abspos/grid-abspos-staticpos-align-self-rtl-002'),
    ('css-grid', 'abspos/grid-abspos-staticpos-align-self-rtl-003'),
    ('css-grid', 'abspos/grid-abspos-staticpos-align-self-rtl-004'),
    ('css-grid', 'abspos/grid-abspos-staticpos-align-self-safe-001'),
    ('css-grid', 'abspos/grid-abspos-staticpos-align-self-vertWM-001'),
    ('css-grid', 'abspos/grid-abspos-staticpos-align-self-vertWM-002'),
    ('css-grid', 'abspos/grid-abspos-staticpos-align-self-vertWM-003'),
    ('css-grid', 'abspos/grid-abspos-staticpos-align-self-vertWM-004'),
    ('css-grid', 'abspos/grid-abspos-staticpos-justify-self-001'),
    ('css-grid', 'abspos/grid-abspos-staticpos-justify-self-002'),
    ('css-images', 'gradient/gradient-powerless-hue-hsl'),
    ('css-images', 'gradient/gradient-powerless-hue-hwb'),
    ('css-images', 'gradient/gradient-powerless-hue-lch'),
    ('css-images', 'gradient/gradient-powerless-hue-oklch'),
]
for section, test in U2:
    im = Image.open(cap(section, test)).convert('RGB')
    n = paint_crossings_generic(im)
    im.save(f'{OUT}/{test.replace("/", "__")}-android-U2.png')
    print(f'U2 {section}/{test} android: {n} px repainted')
