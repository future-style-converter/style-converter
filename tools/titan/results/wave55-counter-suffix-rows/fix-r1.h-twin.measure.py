#!/usr/bin/env python3
# fix-r1.h-twin.measure.py — wave-55 L5 FIX lane (fix-r1), skeptic defect 2 (should-fix): does the round-4 [H] ios twin `ink:`
# read accept a CORRECT U1 reorder when both pictures come from ONE rasterizer (the stage-1 situation: wave55-pre's device
# capture read against wave55-open's device capture)? The skeptic's graft (Catalyst glyphs pasted into the device capture) mixed
# two rasterizers and was inconclusive (47 px vs Android).
# Inputs: fix-r1.catalyst-full.png / fix-r1.catalyst-noU1.png — the VERBATIM wave54-final counter-suffix per-test IR rendered on
# Mac Catalyst through ComponentHost (Inter registered, 390x600 at scale 1) with every L5 patch applied (FULL) and with U1-ios
# reverted (the [all − U1-ios] revert state; seam-1 + unit-U1-ios reverse-applied) — written by a throwaway test class
# (ZZFixL5RenderTests, never delivered) run alone in the fix worktree.
# Runs the REAL probe (the shared tree's counter-suffix-rows.geometry.py, copied byte-identical into a private temp mirror — no
# plan file is written) with run = catfull and base = catnou1 (and base wave54-final for the cross-rasterizer contrast).
# Usage: /opt/homebrew/bin/python3.12 -B fix-r1.h-twin.measure.py   (exit 0; prints the probe lines that read iOS)
import hashlib, os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
PROBE = f'{ROOT}/tools/titan/results/wave55-plan/counter-suffix-rows.geometry.py'
SEC = 'tools/titan/runs/wave54-final/sections/css-counter-styles'
M = tempfile.mkdtemp(prefix='fixL5-htwin-')
for d in ('wave55-plan', 'wave53-plan'):
    os.makedirs(f'{M}/tools/titan/results/{d}')
shutil.copy(PROBE, f'{M}/tools/titan/results/wave55-plan/')
shutil.copy(f'{ROOT}/tools/titan/results/wave53-plan/geometry_common.py', f'{M}/tools/titan/results/wave53-plan/')
os.makedirs(f'{M}/tools/titan/runs')
os.symlink(f'{ROOT}/tools/wpt', f'{M}/tools/wpt')
os.symlink(f'{ROOT}/{SEC.rsplit("/sections", 1)[0]}', f'{M}/tools/titan/runs/wave54-final')
for rid, png in (('catfull', 'fix-r1.catalyst-full.png'), ('catnou1', 'fix-r1.catalyst-noU1.png')):
    out = f'{M}/tools/titan/runs/{rid}/sections/css-counter-styles'
    for d in ('screenshots', 'android-screenshots', 'ios-screenshots'):
        os.makedirs(f'{out}/{d}')
        for t in ('counter-suffix', 'cjk-decimal__css3-counter-styles-005'):
            shutil.copy(f'{ROOT}/{SEC}/{d}/wpt__css-counter-styles__{t}.png', f'{out}/{d}/')
    shutil.copy(f'{HERE}/{png}', f'{out}/ios-screenshots/wpt__css-counter-styles__counter-suffix.png')
print(f'# fix-r1.h-twin.measure.out.txt — the real L5 probe (sha256 {hashlib.sha256(open(PROBE, "rb").read()).hexdigest()}) on Catalyst pictures')
for run, base in (('catfull', 'catnou1'), ('catfull', 'wave54-final'), ('catnou1', 'wave54-final')):
    r = subprocess.run([sys.executable, '-B', f'{M}/tools/titan/results/wave55-plan/counter-suffix-rows.geometry.py', run, base],
                       capture_output=True, text=True)
    print(f'== run {run} base {base} (rc {r.returncode})')
    for l in r.stdout.splitlines():
        if ' ios ' in l or l.split()[2:3] == ['ios']:
            print('  ' + l)
shutil.rmtree(M)
