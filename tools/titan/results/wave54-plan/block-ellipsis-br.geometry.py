#!/usr/bin/env python3
# tools/titan/results/wave54-plan/block-ellipsis-br.geometry.py — post-gate GEOMETRY probe for L3 unit U3 on the four
# passing line-clamp carriers outside the hyphenate family (plan-skeptic r1 S3: the §1 picture-correct tally counted
# css-overflow/line-clamp/block-ellipsis-002/-004/-005/-006 web with no geometry key).
#
# Why: those canvases hold nothing but the clamped text. Today web paints a stray 20-px blank line before the last
# visible line (extract-fixture.mjs :11706 seeds childLineCtx.hasInline once, so a <br> that ENDS a text line gets the
# blank-line height — hyphenate-character-compose.md §4 D), so the line that carries the ellipsis sits 20 px low. The
# score barely sees it (P 0.9868 / 0.9737 / 0.9735 / 0.9735); its line TOPS do. Each line ends in "→ GEOMETRY OK" or
# "→ GEOMETRY WRONG (<why>)".
#
# Rule (CSS 2.1 §9.5: a br preceded by inline content on its line ends that line, it opens no blank one; css-overflow-4
# line-clamp: the clamp keeps N lines and the ellipsis ends the last): ink bands (any non-white pixel; rows merged across
# ≤ 2 blank rows; geometry_common.bands) in the top 200 rows — the band COUNT equals the ref's, and each band's top and
# bottom are within ±3 px and its right edge within ±4 px of the ref's (the right edge holds the "…"). A stray blank
# line moves a top by 20 px; a missing clamp adds a band. The ref must have 3 bands (002) / 2 bands (004-006: the
# bordered "Line 3…" box touches line 2) — the self-check (exit 1 otherwise).
#
# Native rows: the natives show no stray gap on 004-006 and paint their own (different) pictures, so their expected
# lines in expectations.json are today's WRONG lines verbatim (controls: U3 is predicted byte-identical there).
# block-ellipsis-002 ios keeps Line 4 and no "…" (its clamp is not applied): U3 removes its stray blank lines but the
# band count stays 4 vs 3, so the row stays WRONG — the honest label for a cell that stays DEGENERATE.
#
# Usage: python3 tools/titan/results/wave54-plan/block-ellipsis-br.geometry.py [run-id]   (default wave53-final)
# Decodes 16 PNGs.
import os
import sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'wave53-plan'))
from geometry_common import paths, load, bands  # noqa: E402  (shared wave-53 helpers: ref root + capture layout)

run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
ref_failed = False
ink = lambda p: not (p[0] > 240 and p[1] > 240 and p[2] > 240)   # teal / purple text, blue border: not the canvas
REF_BANDS = {'002': 3, '004': 2, '005': 2, '006': 2}             # the self-check: the clamped picture the test asks for


def verdict(b, rb):
    """None when the capture's bands match the ref's; else the first mismatch."""
    if len(b) != len(rb):
        return f'{len(b)} bands vs ref {len(rb)}'
    for (t, bt, _, x1), (rt, rbt, _, rx1) in zip(b, rb):
        if abs(t - rt) > 3:
            return f'band top y{t} vs ref y{rt}'
        if abs(bt - rbt) > 3:
            return f'band bottom y{bt} vs ref y{rbt} (top y{t})'
        if abs(x1 - rx1) > 4:
            return f'band right edge x{x1} vs ref x{rx1} (y{t})'
    return None


for n, want in REF_BANDS.items():
    test = f'block-ellipsis-{n}'
    rb = None
    for label, path in paths(run, 'css-overflow', f'line-clamp/{test}'):
        im, px = load(path)
        if im is None:
            print(f'{test:<20} {label:<8} MISSING → GEOMETRY WRONG (no capture)')
            continue
        w, h = im.size
        b = bands(px, 0, w, 0, min(h, 200), ink, gap=2)
        if label == 'ref':
            rb = b
            why = None if len(b) == want else f'ref {len(b)} bands, the test asks for {want} — rule wrong'
            ref_failed |= why is not None
        else:
            why = verdict(b, rb)
        print(f'{test:<20} {label:<8} bands {b} → ' + ('GEOMETRY OK' if not why else f'GEOMETRY WRONG ({why})'))
sys.exit(1 if ref_failed else 0)
