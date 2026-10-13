#!/usr/bin/env python3
# tools/titan/results/wave55-plan/compose-wpt-content-box-cb.geometry.py — the WAVE-55 copy of the wave-54 L2 probe
# tools/titan/results/wave54-plan/compose-wpt-content-box-cb.geometry.py (which stays as it is: it is a wave-54 record).
#
# Why a copy (plan-skeptic round 1, instrument-teeth M1): the wave-54 rule reads the strict-green BBOX (±1, the plan's
# `contains` pins the exact tuple) and the fail-ink count, never the green MASS — a hollow 1-px outline (396 px of 10000),
# two corner pixels (2 px) or a box with a 30-px white gap print GEOMETRY OK on all seven L2 gating keys. This copy adds
# one reason (REPLACED in round 2 by the mask reason below), after the bbox reason and before the fail-ink reason:
#   mass: |strict-green px − the ref's| ≤ max(40, 0.5 % of the ref's)  (a box is a FILLED rectangle: CSS 2.1 §14.2 the
#         background paints the whole padding box; the measured cross-platform spread on the predicted pictures is ≤ 26 px:
#         clip-3 web 19374 / ios 19376 / the U1+U2 replay 19384 vs ref 19358 — the fakes sit at 2 / 396 / 7000 / 16384).
# Everything else is the wave-54 rule verbatim (CSS 2.1 §10.1 / §10.2: a percentage width resolves against the containing
# block's width, which for a content-box parent is its DECLARED width; css-position-3 §3.1 abspos → padding box): the
# strict-green (0,128,0)±8 bbox equals the ref's ±1 px; the strict-red (abspos-autopos failure ink) and the
# backdrop-source pink (255,127,255)±12 (the nested-clip failure ink) counts equal the ref's (0) within 2 % of the mass.
# The printed dict keeps 'green' FIRST, so the plan's `contains` strings ("'green': (16, 88, 115, 187)") still match.
# Plan-skeptic round 2 (instrument-teeth M2): the mass reason (±max(40, 0.5 %)) accepted a 7×7 hole and a mass-preserving
# corner-radius move, and the fail-ink bound (2 % of the mass: 200-390 px) accepted a 2×100 red / pink sliver — the exact
# failure picture of the RTL siblings. Both are REPLACED, each with a measured basis
# (plan-skeptic-round-2.instrument-teeth.d/fixcheck.L2L3.out.txt, fixcheck9.clip3.out.txt):
#   mask: |strict-green(capture) XOR strict-green(ref)| ≤ 40 px, pixelwise on the same canvas (passing pictures: web / ios
#         ≤ 25, the U1 device picture and the clip-3 replay ≤ 28; a 7×7 hole 49-77, a moved radius 283-339) — it subsumes
#         the mass and pins WHERE the green is, corner arcs included;
#   fail ink: |strict-red + backdrop-pink px − the ref's| ≤ 2 (every passing picture reads 0; the slivers 80-200).
# The bbox reason stays FIRST (the report / control `RTL_LINE` strings and the `contains` strings are unchanged).
# Plan-skeptic round 3 (verify M2 sweep): a 2×2 / 3×3 green blob beside the box, or a 3×3 hole in it, stayed under the 40-px
# XOR bound. `core:` (after the mask reason, before the fail ink) reads the pixels beyond the ref mask's 1-px anti-aliasing
# band: green outside it ≤ 1 px and ref-green missing inside it ≤ 1 px (every passing picture reads 0 / 0).
#
# Usage: /opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/compose-wpt-content-box-cb.geometry.py [run-id]
#        … --png <test>=<label>=<file> [...]   read extra PNGs (the planning fakes) against the same ref.
# Decodes 4 PNGs per test × 10 tests. The ref row must print OK (exit 1 otherwise).
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wave53-plan'))
from geometry_common import paths, load, bbox, near  # noqa: E402  (shared wave-53 probe helpers: refs path, decode, bbox)
import numpy as np  # noqa: E402  (round 2: the pixelwise green mask)

args = sys.argv[1:]
extra = []                                            # [(test, label, png)] — fakes read against the test's ref
if '--png' in args:
    i = args.index('--png')
    extra = [tuple(a.split('=', 2)) for a in args[i + 1:]]
    args = args[:i]
run = args[0] if args else 'wave54-final'
green = lambda p: p[0] < 8 and abs(p[1] - 128) < 8 and p[2] < 8          # strict CSS `green` (0,128,0)
bad = lambda p: (p[0] > 200 and p[1] < 80 and p[2] < 80) or (p[0] > 240 and abs(p[1] - 127) < 12 and p[2] > 240)
TESTS = [('css-flexbox', f'abspos/abspos-autopos-{m}') for m in ('htb-ltr', 'htb-rtl', 'vlr-ltr', 'vlr-rtl', 'vrl-ltr', 'vrl-rtl')]
TESTS += [('filter-effects', f'backdrop-filter-nested-border-radius-clip{s}') for s in ('', '-2', '-3', '-4')]
ref_failed = False


XOR_TOL, FAIL_INK_TOL = 40, 2           # round 2 (M2): measured — passing ≤ 28 / 0, the fakes ≥ 49 / ≥ 80
CORE_TOL = 1                            # round 3: green outside / ref-green missing beyond the 1-px AA band (passing 0 / 0)


def green_mask(im):
    """Strict-green mask (the `green` predicate) of a decoded capture as a bool array."""
    a = np.asarray(im.convert('RGB')).astype(np.int16)
    return (a[..., 0] < 8) & (np.abs(a[..., 1] - 128) < 8) & (a[..., 2] < 8)


def grow(m, r=1, outside=False):
    """Binary dilation of mask m by an r-px square (round 3); `outside` is what lies beyond the canvas."""
    h, w = m.shape; p = np.pad(m, r, constant_values=outside); o = np.zeros_like(m)
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            o |= p[r + dy:r + dy + h, r + dx:r + dx + w]
    return o


def core(m, r):
    """Round 3 (verify M2 sweep): (green px OUTSIDE the ref mask grown by its 1-px anti-aliasing band, ref-green px of the ref
    mask shrunk by that band that the capture lacks) — every passing picture reads (0, 0) on all ten tests (web, iOS, the U1
    device picture, the clip-3 replay: plan-skeptic-round-3.fakes, fix-r3.done.md), so added green beside the box (a stray
    blob, a doubled corner) or a hole inside it is read pixel for pixel, where the 40-px XOR bound let up to ~12 px through."""
    h, w = max(m.shape[0], r.shape[0]), max(m.shape[1], r.shape[1])
    pm, pr = np.zeros((h, w), bool), np.zeros((h, w), bool)
    pm[:m.shape[0], :m.shape[1]] = m; pr[:r.shape[0], :r.shape[1]] = r
    return int((pm & ~grow(pr)).sum()), int((~grow(~pr, outside=True) & ~pm).sum())


def xor_px(m, r):
    """|m XOR r| on the union canvas (a size difference counts every green pixel outside the overlap)."""
    h, w = max(m.shape[0], r.shape[0]), max(m.shape[1], r.shape[1])
    pm, pr = np.zeros((h, w), bool), np.zeros((h, w), bool)
    pm[:m.shape[0], :m.shape[1]] = m; pr[:r.shape[0], :r.shape[1]] = r
    return int((pm ^ pr).sum())


for section, test in TESTS:
    ref = ref_mask = None
    rows = list(paths(run, section, test)) + [(lab, p) for t, lab, p in extra if t == test]
    for label, p in rows:
        im, px = load(p)
        if im is None:
            print(f'{test} {label:8} MISSING'); continue
        w, h = im.size
        gb, gn = bbox(px, w, h, green)
        _, bn = bbox(px, w, h, bad)
        gm = green_mask(im)
        if label == 'ref':
            ref_mask = gm
        xr = xor_px(gm, ref_mask)
        m = {'green': gb, 'greenPx': gn, 'failInkPx': bn, 'xorPx': xr, 'core': core(gm, ref_mask)}
        if label == 'ref':
            ref = m
        why = None
        if (gb is None) != (ref['green'] is None) or (gb and not near(gb, ref['green'], 1)):
            why = f'green {gb} vs ref {ref["green"]}'
        elif xr > XOR_TOL:
            # round 2 (M2): WHERE the green is, pixel for pixel — a hole, a moved corner arc, a gutted / striped box
            why = f'mask: green XOR ref {xr} px > {XOR_TOL} (hole / moved radius / partial box)'
        elif max(core(gm, ref_mask)) > CORE_TOL:
            # round 3 (verify M2): ADDED green beside the box, or a hole in it, smaller than the XOR bound
            why = f'core: green {core(gm, ref_mask)[0]} px outside / {core(gm, ref_mask)[1]} px missing beyond the ref mask\'s 1-px band (±{CORE_TOL})'
        elif abs(bn - ref['failInkPx']) > FAIL_INK_TOL:
            # round 2 (M2): the failure ink is 0 on every passing picture; a sliver beside the box is the RTL failure picture
            why = f'failure ink {bn} px vs ref {ref["failInkPx"]} ±{FAIL_INK_TOL}'
        print(f'{test} {label:8} {m} → ' + ('GEOMETRY OK' if why is None else f'GEOMETRY WRONG ({why})'))
        if label == 'ref' and why is not None:
            ref_failed = True
sys.exit(1 if ref_failed else 0)
