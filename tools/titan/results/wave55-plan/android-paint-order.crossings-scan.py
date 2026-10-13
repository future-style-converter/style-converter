#!/usr/bin/env python3
# tools/titan/results/wave55-plan/android-paint-order.crossings-scan.py — pixel corroboration of the U2 census.
#
# Why: the IR census (android-paint-order.census.mjs) can only see px-resolvable child boxes; a descendant that
# reaches a parent's border band through auto sizing, intrinsic text overflow or a non-px inset is invisible to it.
# This scan looks at the PICTURES instead, but only for the 252 documents the wire says CAN carry the defect (a
# component with a visible border band AND at least one non-<br> child or its own text): per document it counts "crossings" — runs
# of 1..4 dark pixels (sum < 150) with the SAME saturated fill colour on both ends (horizontally or vertically), the
# signature of a dark parent band painted OVER a descendant's fill — on the frozen ref and on each capture.
# Text strokes on coloured backgrounds also make crossings, on both sides alike, so the read-out is the EXCESS over
# the ref (capture − ref), and a row is only a candidate; every candidate named in the brief was looked at.
# Usage: python3 android-paint-order.crossings-scan.py [run-id=wave54-final] [--min 20]
import glob
import json
import os
import sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
REFS = (f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/'
        'white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin')
args = sys.argv[1:]
MIN = int(args[args.index('--min') + 1]) if '--min' in args else 20
run = next((a for a in args if not a.startswith('--') and not a.isdigit()), 'wave54-final')
RUN = f'{ROOT}/tools/titan/runs/{run}/sections'
DIRS = {'web': 'screenshots', 'ios': 'ios-screenshots', 'android': 'android-screenshots'}


def structural_docs():
    """(section, stem) of every per-test IR document with a visibly bordered component that has a non-<br> child or
    its own text (text ink can reach the band too) — U2's MAY-MOVE set: no other android capture may change."""
    out = []
    for f in sorted(glob.glob(f'{RUN}/*/per-test-ir/*.json')):
        comps = json.load(open(f)).get('components', [])
        kids = {}
        for c in comps:
            p = (c.get('slot') or {}).get('parent')
            if p and (c.get('meta') or {}).get('role') != 'line-break':
                kids.setdefault(p, []).append(c)
        for c in comps:
            P = {p['type']: p['data'] for p in c.get('properties', [])}
            band = any(isinstance(P.get(f'Border{s}Width'), dict) and (P[f'Border{s}Width'].get('px') or 0) > 0
                       and P.get(f'Border{s}Style') not in (None, 'NONE', 'HIDDEN') for s in ('Top', 'Right', 'Bottom', 'Left'))
            if band and (kids.get(c['id']) or (c.get('text') or '').strip()):
                out.append((f.split('/')[-3], os.path.basename(f)[:-5]))
                break
    return out


def crossings(path):
    """Crossing-pixel count of one PNG (None when missing)."""
    if not os.path.exists(path):
        return None
    a = np.asarray(Image.open(path).convert('RGB')).astype(np.int16)
    dark = a.sum(axis=2) < 150
    mx, mn = a.max(axis=2), a.min(axis=2)
    fill = (mx - mn > 60) & ~dark          # a saturated, non-dark colour (teal, yellow, red, lime, …)
    n = 0
    for axis in (1, 0):                     # horizontal runs, then vertical
        A = a if axis == 1 else a.transpose(1, 0, 2)
        D = dark if axis == 1 else dark.T
        F = fill if axis == 1 else fill.T
        W = A.shape[1]
        for k in range(1, 5):
            if W < k + 2:
                continue
            run_dark = np.ones((A.shape[0], W - k - 1), dtype=bool)
            for j in range(k):
                run_dark &= D[:, 1 + j:W - k + j]
            left = A[:, :W - k - 1]
            right = A[:, k + 1:]
            same = (np.abs(left - right).max(axis=2) <= 8) & F[:, :W - k - 1] & F[:, k + 1:]
            # the run must be maximal (no dark pixel just inside either fill end) — guaranteed by F (non-dark)
            n += int((run_dark & same).sum()) * k
    return n


rows = []
for sec, stem in structural_docs():
    flat = stem[len(f'wpt__{sec}__'):]
    ref = crossings(f'{REFS}/{sec}/{flat}.png')
    caps = {p: crossings(f'{RUN}/{sec}/{d}/{stem}.png') for p, d in DIRS.items()}
    rows.append((sec, flat, ref, caps))

print(f'run {run}: {len(rows)} structural documents (bordered component with a non-<br> child or own text)')
print(f'candidates: android excess over ref >= {MIN} crossing px (web / ios excess shown for contrast)')
for sec, flat, ref, caps in sorted(rows, key=lambda r: -((r[3]['android'] or 0) - (r[2] or 0))):
    if ref is None or caps['android'] is None:
        continue
    ex = {p: (None if caps[p] is None else caps[p] - ref) for p in DIRS}
    if ex['android'] >= MIN:
        print(f'  {sec}/{flat:<70} ref {ref:>5}  android +{ex["android"]:<5} web {ex["web"]:+} ios {ex["ios"]:+}')

# The U2 carrier set (Android-only excess: android >= MIN over the ref while web and iOS stay within 5) and the
# may-move set, as capture stems, for the plan generator.
car = [(sec, flat) for sec, flat, ref, caps in rows if ref is not None and caps['android'] is not None
       and caps['android'] - ref >= MIN and all(caps[p] is not None and abs(caps[p] - ref) <= 5 for p in ('web', 'ios'))]
print(f'\nU2 android capture carriers (android excess >= {MIN}, web/ios within 5): {len(car)}')
for sec, flat in sorted(car):
    print(f'  wpt__{sec}__{flat}')
print(f'\nU2 may-move set (android captures only; every other android capture must be decoded-pixel identical): {len(rows)} stems')
for sec, flat, _, _ in rows:
    print(f'  wpt__{sec}__{flat}')
