#!/usr/bin/env python3
# fix-r1.u3-boxes.measure.py — wave-55 L5 FIX lane (fix-r1), defect 1: WHERE does U3-ios move ink on counter-suffix, and how
# would each read the plan-regeneration request names behave there? Measured, not derived.
#
# Inputs (read-only): the frozen ref and the wave54-final ref-run captures of css-counter-styles/counter-suffix (web / ios /
# android), and the two Catalyst renders in this dir (skeptic-catalyst-u3-{with,without}.png — the VERBATIM wave54-final IR
# through the real PlaceholderLabel.labelFont path with every L5 patch applied and the harness Inter registered, WITH and
# WITHOUT its 6 FontVariantNumeric declarations; the fix lane re-rendered both and got pixel-identical PNGs,
# fix-r1.catalyst-rerun.out.txt). The model picture is the skeptic's 'skmodel': the device capture with exactly U3's
# Catalyst delta grafted (skeptic-model-u3.py).
# Prints, per row 9-12: (1) the dark-ink column blobs of x120-155 on every picture (the plan probe's own `dark`), (2) the
# Catalyst delta box (with != without), (3) for each U3 run box the two-nested-mask XOR (the probe's inkmask) of Catalyst
# 'with' against 'without' at the best whole-px offset (dx -2..2, dy -1..1) — the read a base-twin rule would make on a
# CORRECT U3 picture drawn by the same rasterizer.
# Usage: /opt/homebrew/bin/python3.12 -B fix-r1.u3-boxes.measure.py   (exit 0)
import os
import sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'tools/titan/results/wave53-plan'))
sys.dont_write_bytecode = True
from geometry_common import dark  # noqa: E402  (the plan probes' own ink predicate)

SEC = f'{ROOT}/tools/titan/runs/wave54-final/sections/css-counter-styles'
REF = f'{ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-counter-styles/counter-suffix.png'
P = {'ref': REF, 'web': f'{SEC}/screenshots/wpt__css-counter-styles__counter-suffix.png',
     'ios-base': f'{SEC}/ios-screenshots/wpt__css-counter-styles__counter-suffix.png',
     'android': f'{SEC}/android-screenshots/wpt__css-counter-styles__counter-suffix.png',
     'cat-with': f'{HERE}/skeptic-catalyst-u3-with.png', 'cat-without': f'{HERE}/skeptic-catalyst-u3-without.png'}
IM = {k: Image.open(v).convert('RGB') for k, v in P.items()}
A = {k: np.asarray(v, dtype=np.int16) for k, v in IM.items()}
# skmodel: the device capture with U3's Catalyst delta grafted (the skeptic's model, rebuilt here in memory)
delta = (A['cat-with'] != A['cat-without']).any(axis=2)
A['skmodel'] = np.where(delta[..., None], A['cat-with'], A['ios-base'])
IM['skmodel'] = Image.fromarray(A['skmodel'].astype(np.uint8))
ROW = {k: (16 + 24 * (k - 1), 16 + 24 * k) for k in range(1, 13)}


def blobs(im, x0, x1, y0, y1):
    """Column-connected dark-ink blobs (the plan probe's blobs()) in [x0,x1)×[y0,y1): 'xa-xb y ya-yb'."""
    px = im.load()
    cols = [x for x in range(x0, x1) if any(dark(px[x, y]) for y in range(y0, y1))]
    runs = []
    for x in cols:
        if runs and x == runs[-1][1] + 1:
            runs[-1][1] = x
        else:
            runs.append([x, x])
    out = []
    for xa, xb in runs:
        ys = [y for y in range(y0, y1) for x in range(xa, xb + 1) if dark(px[x, y])]
        out.append(f'x{xa}-{xb} y{min(ys)}-{max(ys)}')
    return ', '.join(out)


def level(a):
    """The probe's round-4 ink LEVEL: (min channel < 200) + (RGB sum < 300)."""
    return (a.min(axis=2) < 200).astype(np.int16) + (a.sum(axis=2) < 300).astype(np.int16)


def best_xor(a, b, box):
    """Min over dx -2..2, dy -1..1 of Σ|level(a) − level(b) moved| inside box (x0, y0, x1, y1), blank-filled."""
    x0, y0, x1, y1 = box
    la, lb = level(a), level(b)
    best = None
    for dx in range(-2, 3):
        for dy in range(-1, 2):
            m = np.zeros_like(lb)
            h, w = lb.shape
            m[max(0, dy):min(h, h + dy), max(0, dx):min(w, w + dx)] = lb[max(0, -dy):min(h, h - dy), max(0, -dx):min(w, w - dx)]
            n = int(np.abs(la[y0:y1, x0:x1] - m[y0:y1, x0:x1]).sum())
            if best is None or n < best[0]:
                best = (n, dx, dy)
    return best


print('# fix-r1.u3-boxes.measure.out.txt — U3-ios ink radius on counter-suffix rows 9-12 (x120-155), per picture')
for k in (9, 10, 11, 12):
    y0, y1 = ROW[k]
    print(f'row {k} y{y0}-{y1 - 1}:')
    for name in ('ref', 'web', 'android', 'ios-base', 'cat-without', 'cat-with', 'skmodel'):
        print(f'  {name:12} {blobs(IM[name], 120, 156, y0, y1)}')
    ys, xs = np.nonzero(delta[y0:y1, :])
    print(f'  Catalyst delta (with != without): {len(xs)} px' + (f', x{xs.min()}-{xs.max()} y{ys.min() + y0}-{ys.max() + y0}' if len(xs) else ''))
    for nm, box in ((f'period box x130-135', (130, y0, 136, y1)), (f'digit box x136-150', (136, y0, 151, y1)) if k in (9, 10) else (None, None)):
        if nm is None:
            continue
        n, dx, dy = best_xor(A['cat-with'], A['cat-without'], box)
        m, mdx, mdy = best_xor(A['skmodel'], A['ios-base'], box)
        print(f'  {nm}: nested-mask XOR Catalyst with vs without {n} (dx {dx:+d} dy {dy:+d}); skmodel vs ios-base {m} (dx {mdx:+d} dy {mdy:+d})')
outside = delta.copy()
for k in (9, 10, 11, 12):
    outside[ROW[k][0]:ROW[k][1], 130:151] = False
print(f'Catalyst delta OUTSIDE the six run boxes (rows 9-12 × x130-150): {int(outside.sum())} px')
