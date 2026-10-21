#!/usr/bin/env python3
# skeptic-model-u3.py — wave-55 L5 SKEPTIC: does a CORRECT U3-ios picture pass the plan's own [T] / [K] ios reads?
#
# Inputs (this dir): skeptic-catalyst-u3-with.png / -without.png — the VERBATIM wave54-final counter-suffix per-test IR
# rendered on Mac Catalyst through ComponentHost (the real PlaceholderLabel.labelFont path) with every L5 patch applied and
# the harness Inter faces registered, WITH and WITHOUT its 6 FontVariantNumeric declarations (= U3-ios on / the base's
# identity applier); source skeptic-probe-u3.swift.txt, readout skeptic-probe-u3.out.txt.
# Model: the wave54-final iOS capture with exactly the pixels U3 changes on Catalyst (with != without) replaced by the
# Catalyst 'with' pixels — 'skmodel' everywhere, 'skmodel2' inside row 9's digit window x136-150 only (what the plan
# assumes U3 touches). The REAL probe (counter-suffix-rows.geometry.py, copied byte-identical into a private mirror so
# no plan file is written) then reads the model against base wave54-final.
# Usage: /opt/homebrew/bin/python3.12 -B skeptic-model-u3.py   (exit 0; prints the [T] / [K] ios lines per model)
import os, shutil, subprocess, sys, tempfile
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
SEC = 'tools/titan/runs/wave54-final/sections/css-counter-styles'
TESTS = ('counter-suffix', 'cjk-decimal__css3-counter-styles-005')
dev = Image.open(f'{ROOT}/{SEC}/ios-screenshots/wpt__css-counter-styles__counter-suffix.png').convert('RGB')
cw = Image.open(f'{HERE}/skeptic-catalyst-u3-with.png').convert('RGB')
co = Image.open(f'{HERE}/skeptic-catalyst-u3-without.png').convert('RGB')
mirror = tempfile.mkdtemp(prefix='skL5-model-')
for d in ('wave55-plan', 'wave53-plan'):
    os.makedirs(f'{mirror}/tools/titan/results/{d}')
shutil.copy(f'{ROOT}/tools/titan/results/wave55-plan/counter-suffix-rows.geometry.py', f'{mirror}/tools/titan/results/wave55-plan/')
shutil.copy(f'{ROOT}/tools/titan/results/wave53-plan/geometry_common.py', f'{mirror}/tools/titan/results/wave53-plan/')
os.makedirs(f'{mirror}/tools/titan/runs')
os.symlink(f'{ROOT}/tools/wpt', f'{mirror}/tools/wpt')
os.symlink(f'{ROOT}/tools/titan/runs/wave54-final', f'{mirror}/tools/titan/runs/wave54-final')
for run, win in (('skmodel', None), ('skmodel2', (136, 208, 151, 232))):
    out = f'{mirror}/tools/titan/runs/{run}/sections/css-counter-styles'
    for d in ('screenshots', 'android-screenshots', 'ios-screenshots'):
        os.makedirs(f'{out}/{d}')
        for t in TESTS:
            shutil.copy(f'{ROOT}/{SEC}/{d}/wpt__css-counter-styles__{t}.png', f'{out}/{d}/')
    m, pw, po = dev.copy(), cw.load(), co.load()
    pm, n = m.load(), 0
    for y in range(cw.size[1]):
        for x in range(cw.size[0]):
            inside = win is None or (win[0] <= x < win[2] and win[1] <= y < win[3])
            if inside and pw[x, y] != po[x, y]:
                pm[x, y] = pw[x, y]
                n += 1
    m.save(f'{out}/ios-screenshots/wpt__css-counter-styles__counter-suffix.png')
    r = subprocess.run([sys.executable, '-B', f'{mirror}/tools/titan/results/wave55-plan/counter-suffix-rows.geometry.py', run, 'wave54-final'],
                       capture_output=True, text=True)
    print(f'== {run}: {n} px of the iOS capture replaced by U3\'s Catalyst delta ' + ('(everywhere)' if win is None else f'(row 9 digit window x{win[0]}-{win[2] - 1} only)'))
    for line in r.stdout.splitlines():
        if line.startswith(('[T] counter-suffix ios', '[K] counter-suffix ios')):
            print('  ' + line)
shutil.rmtree(mirror)
