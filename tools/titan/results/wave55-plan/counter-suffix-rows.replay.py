#!/usr/bin/env python3
# tools/titan/results/wave55-plan/counter-suffix-rows.replay.py — accept / reject FAKES for counter-suffix-rows.geometry.py.
#
# Builds pictures from the wave54-final captures (PIL, lossless, same PNG mode) under counter-suffix-rows-replay/<tag>/
# and runs the probe on each with base wave54-final, so every key is proven able to PASS (the fixed picture, composed
# from pixels a platform already paints) and to FAIL (each named defect, and each near-miss a wrong fix would leave).
# READ-ONLY outside counter-suffix-rows-replay/. Output: counter-suffix-rows.geometry.replays.txt (written by the caller).
#
#   ok-all        iOS rows 3-4 marker zone (x30-63, y64-111) from Android (letter-then-period, the bundled Noto Hebrew
#                 both natives share); iOS + Android rows 5-6 marker zone x20-59 moved +4 px (the U2 prediction: gap 4 →
#                 0); iOS row-9 digit zone (x136-150, y208-231) from Android (the tabular one.tf Android already paints);
#                 css3-counter-styles-005 iOS + Android item text (x255-333, y95-151) moved -4 px (U2 inline half).
#                 Expect: [H] [C] [T] [I] [K] OK on every platform; [L] natives WRONG (report — no unit targets it).
#   H-broken      Android rows 3-4 marker zone from iOS (period before the letter)           → [H] android WRONG.
#   H-nodot       ok-all with the rows 3-4 periods whitened on iOS                           → [H] ios WRONG.
#   H-flush       ok-all with the iOS rows 3-4 marker moved +4 px (order right, gap dropped) → [H] ios WRONG (x).
#   H-low         ok-all with the iOS rows 3-4 marker moved 2 px DOWN (U1's metric risk: a fallback line box for the
#                 bidi controls lowering the baseline-aligned marker)                     → [H] ios WRONG (y).
#   C-shift8      Android rows 5-6 marker moved +8 (overshoot into the content edge)       → [C] android WRONG.
#   C-nocomma     ok-all with the Android "、" whitened (a suffix-less marker at the right x) → [C] android WRONG.
#   C-space-only  Android rows 5-6 marker moved +4 but item text moved +4 too (a gap kept, the whole row shifted)
#                                                                                           → [C] android WRONG.
#   I-wholeline   css3-counter-styles-005 iOS marker AND item moved -4 (a shifted line, not a closed gap) → [I] ios WRONG.
#   K-leak        ok-all with iOS row 1 nudged 1 px (a U1 wrap that moved an untargeted marker)     → [K] ios WRONG.
import os
import shutil
import subprocess
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
SEC = f'{ROOT}/tools/titan/runs/wave54-final/sections/css-counter-styles'
DIRS = {'web': 'screenshots', 'ios': 'ios-screenshots', 'android': 'android-screenshots'}
TESTS = ('counter-suffix', 'cjk-decimal__css3-counter-styles-005')
OUT = f'{HERE}/counter-suffix-rows-replay'


def src(test, plat):
    return Image.open(f'{SEC}/{DIRS[plat]}/wpt__css-counter-styles__{test}.png')


def blank(im, box):
    """White (opaque) over box — the capture background."""
    im.paste((255, 255, 255, 255) if im.mode == 'RGBA' else (255, 255, 255), box)


def move(im, box, dx, dy=0):
    """Move the pixels of box by (dx, dy), whitening what they leave."""
    x0, y0, x1, y1 = box
    piece = im.crop(box)
    blank(im, box)
    im.paste(piece, (x0 + dx, y0 + dy))


def graft(im, other, box):
    im.paste(other.crop(box), box[:2])


def base_set():
    return {(t, p): src(t, p).copy() for t in TESTS for p in DIRS}


def ok_all():
    s = base_set()
    graft(s[('counter-suffix', 'ios')], src('counter-suffix', 'android'), (30, 64, 64, 112))
    for p in ('ios', 'android'):
        move(s[('counter-suffix', p)], (20, 112, 60, 160), 4)
        move(s[('cjk-decimal__css3-counter-styles-005', p)], (255, 95, 334, 152), -4)
    graft(s[('counter-suffix', 'ios')], src('counter-suffix', 'android'), (136, 208, 151, 232))
    return s


def tags():
    yield 'ok-all', ok_all()
    s = base_set(); graft(s[('counter-suffix', 'android')], src('counter-suffix', 'ios'), (30, 64, 64, 112)); yield 'H-broken', s
    s = ok_all(); [blank(s[('counter-suffix', 'ios')], (55, y0, 60, y0 + 24)) for y0 in (64, 88)]; yield 'H-nodot', s
    s = ok_all(); move(s[('counter-suffix', 'ios')], (30, 64, 60, 112), 4); yield 'H-flush', s
    s = ok_all(); move(s[('counter-suffix', 'ios')], (30, 64, 64, 110), 0, 2); yield 'H-low', s
    s = base_set(); move(s[('counter-suffix', 'android')], (20, 112, 56, 160), 8); yield 'C-shift8', s
    s = ok_all(); blank(s[('counter-suffix', 'android')], (48, 112, 56, 160)); yield 'C-nocomma', s
    s = base_set(); move(s[('counter-suffix', 'android')], (20, 112, 120, 160), 4); yield 'C-space-only', s
    s = ok_all(); move(s[('cjk-decimal__css3-counter-styles-005', 'ios')], (210, 95, 334, 152), -4); yield 'I-wholeline', s
    s = ok_all(); move(s[('counter-suffix', 'ios')], (40, 16, 63, 40), 1); yield 'K-leak', s


def arms():
    """Per-unit replay ARMS for the score replay (counter-suffix-rows.replay-score.mjs): one unit's predicted pixels each."""
    a = {}
    cs = lambda p: src('counter-suffix', p).copy()
    im = cs('ios'); graft(im, src('counter-suffix', 'android'), (30, 64, 64, 112)); a['ios-U1'] = im
    im = cs('ios'); move(im, (20, 112, 60, 160), 4); a['ios-U2o'] = im
    im = cs('ios'); graft(im, src('counter-suffix', 'android'), (136, 208, 151, 232)); a['ios-U3'] = im
    im = cs('ios'); graft(im, src('counter-suffix', 'android'), (30, 64, 64, 112)); move(im, (20, 112, 60, 160), 4); a['ios-U1U2o'] = im
    a['ios-all'] = ok_all()[('counter-suffix', 'ios')]
    im = cs('android'); move(im, (20, 112, 60, 160), 4); a['android-U2o'] = im
    for p in ('ios', 'android'):
        im = src('cjk-decimal__css3-counter-styles-005', p).copy(); move(im, (255, 95, 334, 152), -4); a[f'005-{p}-U2i'] = im
    return a


if os.path.isdir(OUT):
    for d in os.listdir(OUT):
        if os.path.isdir(f'{OUT}/{d}'):
            shutil.rmtree(f'{OUT}/{d}')
os.makedirs(f'{OUT}/arms', exist_ok=True)
for name, im in arms().items():
    im.save(f'{OUT}/arms/{name}.png')
lines = []
for tag, s in tags():
    os.makedirs(f'{OUT}/{tag}', exist_ok=True)
    for (t, p), im in s.items():
        im.save(f'{OUT}/{tag}/{t}.{p}.png')
    r = subprocess.run([sys.executable, f'{HERE}/counter-suffix-rows.geometry.py', f'replay:{tag}', 'wave54-final'],
                       capture_output=True, text=True)
    lines.append(f'### replay:{tag} (base wave54-final) — probe exit {r.returncode}')
    # Drop the ref rows (label column == 'ref'; the self-check is the exit code), keep every capture row.
    lines += [l for l in r.stdout.splitlines() if l.split()[2:3] != ['ref'] and l.split()[1:2] != ['ref']]
print('\n'.join(lines))
