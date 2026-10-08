#!/usr/bin/env python3
# tools/titan/results/wave54-plan/web-out-of-flow-hyphen-box.geometry.py — the wave-54 post-gate GEOMETRY probe for
# the web-out-of-flow-hyphen-box family (BACKLOG obligation 0(c); brief web-out-of-flow-hyphen-box.md §6).
#
# Why: soft-hyphen.geometry.py (wave-53 plan) found the defect but stops at the FIRST wrong box ("box 4 height 26 vs
# ref 46"); the picture has TWO collapsed boxes (4 and 5, the two `high<abspos>way` boxes) and the two boxes under
# them ride 40 px high. This probe reports EVERY wrong box, so a half-fix (one box) cannot read as OK, and it carries
# the family's must-not-move rows. Every line ends in "→ GEOMETRY OK" or "→ GEOMETRY WRONG (<why>)".
#
# Usage: python3 tools/titan/results/wave54-plan/web-out-of-flow-hyphen-box.geometry.py [run-id] [--png <replay.png>]
#        (default run wave53-final; --png adds a `web*` row reading that picture as the -002 web capture)
# Expected on wave53-final (measured 2026-10-08):
#   hyphens/hyphens-out-of-flow-002 web      … → GEOMETRY WRONG (boxes 4,5 height 26 vs ref 46)
#   every other row                          … → GEOMETRY OK
# Expected after the fix (closing gate): every row → GEOMETRY OK, the -002 web row included. The replay
#   web-out-of-flow-hyphen-box-replay/web-W1-exact.png must print OK (it does: the picture the fix predicts).
# Self-check: the ref rows and the span-002 ANCHOR rows (an already-correct picture of the same 6ch `highway` box on
# all three platforms) must print OK, or the probe exits 1 (the rule, not the lane, is then wrong).
import sys
sys.path.insert(0, __import__('os').path.join(__import__('os').path.dirname(__file__), '..', 'wave53-plan'))
from geometry_common import paths, load, orange, bands, near

args = sys.argv[1:]
png = None
if '--png' in args:
    i = args.index('--png'); png = args[i + 1]; del args[i:i + 2]
run = args[0] if args else 'wave53-final'
ANCHOR = 'hyphens/hyphens-span-002'
TARGET = 'hyphens/hyphens-out-of-flow-002'
self_check_failed = []


def ink(p):
    # Text ink = every channel < 200 (wave-53 L2 skeptic D1): keeps Android's light-grey U+2010, refuses orange.
    return max(p[0], p[1], p[2]) < 200


def boxes(px, w, h):
    # Each orange-bordered box below the y95 instruction paragraph as (top, bottom, left, right), with its ink lines
    # (searched from 3 px inside the border out to 12 px PAST the right border, so an overflowing `highway` is seen).
    out = []
    for (y0, y1, x0, x1) in bands(px, 0, w, 95, h, orange, gap=0):
        out.append(((y0, y1, x0, x1), bands(px, x0 + 3, min(w, x1 + 12), y0 + 3, y1 - 2, ink, gap=0)))
    return out


def check(found, ref):
    # css-text-3 §5.4 (`hyphens: auto` hyphenates `highway` at high|way under lang=en) + §5.1 (an out-of-flow element
    # introduces no soft wrap opportunity and has no effect on hyphenation — the test's assert) + CSS 2.1 §10.8: every
    # box is two 20-px line boxes + 2×3 px border = 46 px and reads `high‐` (ink right edge x61) over `way` (x54).
    # Tolerances: box height ±1 px, ink edges ±2 px. Collects EVERY wrong box.
    if len(found) != len(ref):
        return f'boxes {len(found)}/{len(ref)}'
    tall, inky = {}, []
    for i, ((b, lines), (rb, rlines)) in enumerate(zip(found, ref)):
        hb, hr = b[1] - b[0] + 1, rb[1] - rb[0] + 1
        if not near(hb, hr, 1):
            tall.setdefault((hb, hr), []).append(str(i + 1))
            continue
        if len(lines) != len(rlines):
            inky.append(f'box {i + 1} {len(lines)} ink lines vs ref {len(rlines)}')
            continue
        for j, (l, rl) in enumerate(zip(lines, rlines)):
            if not near(l[2], rl[2], 2) or not near(l[3], rl[3], 2):
                inky.append(f'box {i + 1} line {j + 1} ink x{l[2]}-{l[3]} vs ref x{rl[2]}-{rl[3]}')
                break
    why = [f'box{"es" if len(ids) > 1 else ""} {",".join(ids)} height {hb} vs ref {hr}' for (hb, hr), ids in tall.items()]
    why += inky
    return '; '.join(why) or None


rows = []
for test in (ANCHOR, 'hyphens/hyphens-out-of-flow-001', TARGET):
    rows += [(test, label, p) for label, p in paths(run, 'css-text', test)]
    if test == TARGET and png:
        rows.append((test, 'web*', png))
refs = {}
for test, label, p in rows:
    im, px = load(p)
    if im is None:
        print(f'{test} {label:8} MISSING {p}')
        if label == 'ref' or test == ANCHOR:
            self_check_failed.append(f'{test} {label} missing')
        continue
    found = boxes(px, *im.size)
    if label == 'ref':
        refs[test] = found
    why = check(found, refs[test]) if test in refs else 'no ref'
    heights = [b[1] - b[0] + 1 for b, _ in found]
    edges = [tuple(l[3] for l in ls) for _, ls in found]
    verdict = 'GEOMETRY OK' if why is None else f'GEOMETRY WRONG ({why})'
    print(f'{test} {label:8} heights {heights} ink-right {edges} → {verdict}')
    if why is not None and (label == 'ref' or test == ANCHOR):
        self_check_failed.append(f'{test} {label}')
if self_check_failed:
    print('SELF-CHECK FAILED: ' + ', '.join(self_check_failed))
sys.exit(1 if self_check_failed else 0)
