#!/usr/bin/env python3
# wave-55 L2 SKEPTIC — the EMPIRICAL U1 radius. wave54-probe (tree 206d71d9) differs from 0d0d3a3d on every
# Android-relevant path ONLY by 5b333c8a (brief cbb-android.md "Evidence"; re-checked by the skeptic with
# `git diff --stat 206d71d9 0d0d3a3d -- runtimes/compose apps/android-harness tools/titan/extract-fixture.mjs converter`),
# so every android capture that differs in decoded pixels between wave54-probe and BOTH wave54-final and
# wave55-open (two base runs: a capture that also differs between the two bases is device noise) is U1's
# device-measured radius on the 26 sections wave54-probe ran. Usage: python3.12 -I skeptic-probe-diff.py <runs dir>
import os, sys
from PIL import Image
RUNS = sys.argv[1]
probe = os.path.join(RUNS, 'wave54-probe', 'sections')
moved, noisy, compared, missing = [], [], 0, 0
for sec in sorted(os.listdir(probe)):
    pdir = os.path.join(probe, sec, 'android-screenshots')
    if not os.path.isdir(pdir):
        continue
    for png in sorted(os.listdir(pdir)):
        if not png.endswith('.png'):
            continue
        bases = [os.path.join(RUNS, r, 'sections', sec, 'android-screenshots', png) for r in ('wave54-final', 'wave55-open')]
        if not all(os.path.exists(b) for b in bases):
            missing += 1
            continue
        compared += 1
        p = Image.open(os.path.join(pdir, png)).convert('RGBA')
        b1, b2 = (Image.open(b).convert('RGBA') for b in bases)
        def same(x, y):
            return x.size == y.size and x.tobytes() == y.tobytes()
        if not same(b1, b2):
            noisy.append(f'{sec}/{png} (wave54-final != wave55-open)')
            if same(p, b1) or same(p, b2):
                continue
        if not same(p, b1) and not same(p, b2):
            n = sum(1 for a, c in zip(p.getdata(), b1.getdata()) if a != c) if p.size == b1.size else -1
            moved.append(f'{sec}/{png} differing-pixels-vs-wave54-final {n}')
print(f'compared {compared} android captures (missing in a base run: {missing})')
print(f'base-run noise (wave54-final vs wave55-open differ): {len(noisy)}')
for n in noisy:
    print('  noise', n)
print(f'moved at wave54-probe (HEAD + 5b333c8a) vs both bases: {len(moved)}')
for m in moved:
    print('  moved', m)
