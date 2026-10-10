#!/usr/bin/env python3
# tools/titan/results/wave54-plan/web-root-separator.geometry.py — post-gate GEOMETRY probe for the
# queue-scout-text-web.md §B lane (web-root-separator).
#
# Why: box-sizing-009…025 web PASS today with the inline-level ROOT atoms packed flush (the ref has one collapsed
# source space, 5 px, between them), so "P ≈ 0.98" can only be told from "P unchanged" by where the second atom's
# left edge lands. Each line ends in "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)".
#
# Rule (CSS 2.1 §16.6.1 / css-text-3 §4.1.1: a collapsible source space between two inline-level boxes renders as
# ONE space advance in the line box), checked in two steps:
#   1. on the recorded probe row y the inked run that starts at or after `split` begins within ±1 px of the ref's
#      (the planning rule; its WRONG lines are cited verbatim in expectations.json, so it runs first);
#   2. (plan-skeptic r1 S2) on EVERY atom row band of the ref — the vertical bands of inked rows inside the case's
#      band range, found on the ref with geometry_common.bands — the same edge test on the band's middle row. A partial
#      fix that separates only the band holding row y (box-sizing-007: 1 of 10 bands; -008: 1 of 3) is WRONG here.
# The ref rows must print OK (self-check, exit 1 otherwise: no second atom on y, or an atom band without one);
# wave53-final web rows print WRONG; the ios and android rows are controls (the lane changes no native capture —
# natives already pack root atoms with the 4.5 px UAWidgetIntrinsics.atomGapPx advance).
#
# Usage: python3 tools/titan/results/wave54-plan/web-root-separator.geometry.py [run-id]   (default wave53-final)
# Decodes 72 PNGs.
import os
import sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'wave53-plan'))
from geometry_common import paths, load, bands  # noqa: E402  (shared wave-53 helpers: ref root + capture layout)

run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
ref_failed = False
ink = lambda p: not (p[0] > 240 and p[1] > 240 and p[2] > 240)   # anything that is not the white canvas

# (section, test, probe row y, split x, atom band range [y0, y1)) — rows, splits and ranges measured on the frozen ref
# (census run scan; fix-r1/rs-band-scan.out.txt lists every band and its run starts). The range starts below the
# instruction text; for the semi-replaced pair it stops above the rows that hold only one atom.
CASES = [
    ('css-ui', 'box-sizing-007', 150, 136, (80, 1344)),       # ref col-2 edge x151 (web x146) on all 10 bands
    ('css-ui', 'box-sizing-008', 150, 136, (80, 626)),        # ref x151 (web x146) on all 3 bands
    ('css-ui', 'box-sizing-010', 140, 86, (80, 600)),         # ref x91 (web: one merged run x16-155)
    ('css-ui', 'box-sizing-011', 140, 86, (80, 600)),         # S3: the 010 class, one key per tallied capture
    ('css-ui', 'box-sizing-013', 150, 86, (80, 600)),         # ref x91 (web merged)
    ('css-ui', 'box-sizing-014', 140, 86, (80, 600)),
    ('css-ui', 'box-sizing-015', 140, 86, (80, 600)),
    ('css-ui', 'box-sizing-016', 140, 86, (80, 600)),
    ('css-ui', 'box-sizing-017', 140, 86, (80, 600)),
    ('css-ui', 'box-sizing-018', 140, 86, (80, 600)),
    ('css-ui', 'box-sizing-019', 140, 86, (80, 600)),
    ('css-ui', 'box-sizing-020', 150, 146, (80, 600)),        # S3: ref x151 (web merged x16-275)
    ('css-ui', 'box-sizing-021', 150, 146, (80, 600)),
    ('css-ui', 'box-sizing-022', 200, 146, (80, 600)),        # ref x151 (web merged x16-275)
    ('css-ui', 'box-sizing-024', 150, 146, (80, 600)),
    ('css-ui', 'box-sizing-025', 150, 146, (80, 600)),
    ('css-position', 'position-absolute-semi-replaced-stretch-other', 60, 180, (30, 250)),   # ref x192 (web x187), 2 bands
    ('css-position', 'position-absolute-semi-replaced-stretch-input', 60, 180, (30, 250)),   # ref x192 (web x187), 2 bands
]


def edge(px, w, y, split):
    """Left edge of the first inked run starting at x >= split on row y (None if the row is blank there).
    A run that started LEFT of split and continues through it (a merged flush pair) does not count: the
    edge is the first ink pixel preceded by a non-ink pixel."""
    for x in range(max(split, 1), w):
        if ink(px[x, y]) and not ink(px[x - 1, y]):
            return x
    return None


for section, test, y, split, (by0, by1) in CASES:
    ref_edge, ref_bands = None, []
    for label, path in paths(run, section, test):
        im, px = load(path)
        if im is None:
            print(f'{test:<48} {label:<8} MISSING → GEOMETRY WRONG (no capture)')
            continue
        w, h = im.size
        e = edge(px, w, y, split)
        if label == 'ref':
            ref_edge = e
            # the atom bands and each band's ref edge on its middle row (the per-band rule's oracle)
            ref_bands = [(t, b, (t + b) // 2, edge(px, w, (t + b) // 2, split))
                         for t, b, _, _ in bands(px, 0, w, by0, min(by1, h), ink, gap=2)]
            bad = [r for r in ref_bands if r[3] is None]
            if e is None:
                verdict = 'GEOMETRY WRONG (ref has no second atom — rule wrong)'
            elif not ref_bands or bad:
                verdict = f'GEOMETRY WRONG (ref atom band rows {bad[0][0]}-{bad[0][1]} has no second atom — rule wrong)' if bad \
                    else 'GEOMETRY WRONG (ref has no atom band — rule wrong)'
            else:
                verdict = 'GEOMETRY OK'
            ref_failed |= verdict != 'GEOMETRY OK'
        elif not (e is not None and ref_edge is not None and abs(e - ref_edge) <= 1):
            verdict = f'GEOMETRY WRONG (second atom edge x{e} vs ref x{ref_edge} on row {y})'
        else:
            # step 2: every atom row band of the ref, probed on its middle row (rows past the capture's height count as blank)
            verdict = 'GEOMETRY OK'
            for t, b, mid, re_ in ref_bands:
                ce = edge(px, w, mid, split) if mid < h else None
                if ce is None or abs(ce - re_) > 1:
                    verdict = f'GEOMETRY WRONG (second atom edge x{ce} vs ref x{re_} on band rows {t}-{b} (row {mid}))'
                    break
        print(f'{test:<48} {label:<8} edge x{e} → {verdict}')
sys.exit(1 if ref_failed else 0)
