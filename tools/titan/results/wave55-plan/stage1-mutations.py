#!/usr/bin/env python3
# tools/titan/results/wave55-plan/stage1-mutations.py — the stage-1 probe edits (PLAN §10 items 4-6) proven able to fail on the
# DEVICE pictures they re-pin to: each fake is wave55-pre's own capture with one defect painted in, read through the edited probes
# (positioned-parent-slot.geometry.py --ink-run wave55-pre; counter-suffix-rows.geometry.py TWIN_RUN_DEFAULT wave55-pre) and, for
# the residual windows, through residual-read.py. The unfaked capture must read OK; every fake WRONG / FAIL. Writes
# stage1-mutations.out.txt beside this file; exit 1 on any verdict other than the pre-registered one.
# Fake runs are symlink trees of wave55-pre under tools/titan/runs/s1mut-<name>/ (gitignored; only the faked PNG is a real file).
# Usage: /opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/stage1-mutations.py
import os, subprocess, sys
sys.dont_write_bytecode = True
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
RUNS = os.path.join(ROOT, 'tools', 'titan', 'runs')
PY = ['/opt/homebrew/bin/python3.12', '-B']
BASE = 'wave55-pre'
DIRS = {'web': 'screenshots', 'ios': 'ios-screenshots', 'android': 'android-screenshots'}
out = []
say = lambda *l: out.extend(l)


def fake_run(name, sec, plat, flat, picture):
    """tools/titan/runs/s1mut-<name>: every file of wave55-pre's sections symlinked, except the one faked capture (a real PNG)."""
    rid = f's1mut-{name}'; root = os.path.join(RUNS, rid)
    if os.path.lexists(root):
        subprocess.run(['rm', '-rf', root], check=True)
    src = os.path.join(RUNS, BASE, 'sections')
    for s in os.listdir(src):
        for d in os.listdir(os.path.join(src, s)):
            sd = os.path.join(src, s, d); td = os.path.join(root, 'sections', s, d)
            if os.path.isdir(sd):
                os.makedirs(td, exist_ok=True)
                for f in os.listdir(sd):
                    os.symlink(os.path.join(sd, f), os.path.join(td, f))
            else:
                os.makedirs(os.path.dirname(td), exist_ok=True); os.symlink(sd, td)
    tgt = os.path.join(root, 'sections', sec, DIRS[plat], f'wpt__{sec}__{flat}.png')
    os.unlink(tgt); Image.fromarray(picture.astype(np.uint8)).save(tgt)
    return rid


def capture(sec, plat, flat):
    return np.array(Image.open(os.path.join(RUNS, BASE, 'sections', sec, DIRS[plat], f'wpt__{sec}__{flat}.png')).convert('RGB')).astype(np.int16)


def probe(script, rid, extra=()):
    p = subprocess.run(PY + [os.path.join(HERE, script), rid, 'wave55-open', *extra], capture_output=True, text=True, cwd=ROOT)
    return p.stdout.splitlines()


def line(lines, key):
    for l in lines:
        if l.startswith(key):
            return l
    return f'{key} — NO LINE'


fails = 0
def check(name, key, got, want):
    """want: 'OK' or 'WRONG' (the line's verdict)."""
    global fails
    v = 'OK' if got.rstrip().endswith('→ GEOMETRY OK') or got.rstrip().endswith('→ LINEBOX OK') else ('WRONG' if 'WRONG' in got else '??')
    ok = v == want; fails += 0 if ok else 1
    say(f'  {"as required" if ok else "VIOLATION  "}  {name:28} {key:40} want {want:5} got {v:5} | {got[:170]}')


# ── L3 · [S] bidi-tab-001 android rtl-rows (the ink-run structure read) ──
say('## L3 positioned-parent-slot.geometry.py <fake> wave55-open (INK_RUN_DEFAULT wave55-pre): [S] bidi-tab-001 android rtl-rows')
A = capture('css-text', 'android', 'bidi__bidi-tab-001')
BLUE = (0, 0, 255)
def l3(name, f, want):
    a = A.copy(); f(a)
    rid = fake_run(name, 'css-text', 'android', 'bidi__bidi-tab-001', a)
    check(name, '[S] bidi-tab-001 android rtl-rows', line(probe('positioned-parent-slot.geometry.py', rid), '[S] bidi-tab-001 android rtl-rows'), want)
def blob(a, x, y, n):
    a[y:y + n, x:x + n] = BLUE
l3('unfaked', lambda a: None, 'OK')
l3('blob2-y106-yellow', lambda a: blob(a, 60, 112, 2), 'WRONG')            # a 2×2 blob on the yellow box, touching nothing
l3('blob3-y86-yellow', lambda a: blob(a, 70, 92, 3), 'WRONG')              # a 3×3 blob in another RTL band
l3('blob3-touch-ring-y106', lambda a: blob(a, 112, 115, 3), 'WRONG')       # touching the '0' ring (x115-123) from the left
l3('blob2-touch-box-y86', lambda a: blob(a, 28, 95, 2), 'WRONG')           # touching the square (x16-27) from the right
def dbl(a):                                                                 # the '0' doubled: its crop copied 14 px left on band y106
    a[110:125, 101:110] = A[110:125, 115:124]
l3('zero-doubled-y106', dbl, 'WRONG')
def nodot(a):                                                               # the '0' ring's inner dot erased on band y106 (a lost glyph part)
    a[117:119, 118:120] = (255, 255, 0)
l3('zero-dot-erased-y106', nodot, 'WRONG')
def shiftrun(a):                                                            # the whole RTL run of band y106 2 px right (the extent rule)
    a[106:126, 2:] = A[106:126, :-2]; a[106:126, :2] = 255
l3('run+2x-y106', shiftrun, 'WRONG')

# ── L5 · counter-suffix ios: [H] (the twin letter reference + the duplicate read), [K] (the x137 zone), [T] ──
say('\n## L5 counter-suffix-rows.geometry.py <fake> wave55-open (TWIN_RUN_DEFAULT wave55-pre): [H] / [K] / [T] counter-suffix ios')
C = capture('css-counter-styles', 'ios', 'counter-suffix')
ROW = {k: (16 + 24 * (k - 1), 16 + 24 * k) for k in range(1, 13)}
L3B, L4B = (47, 53), (46, 53)                                               # the row-3 / row-4 letter columns (the probe's own [H] line)
def l5(name, f, key, want):
    a = C.copy(); f(a)
    rid = fake_run(name, 'css-counter-styles', 'ios', 'counter-suffix', a)
    check(name, key, line(probe('counter-suffix-rows.geometry.py', rid), key), want)
def swap(a):
    r3 = C[ROW[3][0]:ROW[3][1], L3B[0] - 1:L3B[1] + 2].copy(); r4 = C[ROW[4][0]:ROW[4][1], L4B[0] - 1:L4B[1] + 2].copy()
    a[ROW[3][0]:ROW[3][1], L3B[0] - 1:L3B[1] + 2] = 255; a[ROW[4][0]:ROW[4][1], L4B[0] - 1:L4B[1] + 2] = 255
    a[ROW[3][0]:ROW[3][1], L3B[0] - 1:L3B[0] - 1 + r4.shape[1]] = r4; a[ROW[4][0]:ROW[4][1], L4B[0] - 1:L4B[0] - 1 + r3.shape[1]] = r3
def dup3(a):                                                                # row 4 shows row 3's letter (one counter value twice)
    r3 = C[ROW[3][0]:ROW[3][1], L3B[0] - 1:L3B[1] + 2].copy()
    a[ROW[4][0]:ROW[4][1], L4B[0] - 1:L4B[1] + 2] = 255; a[ROW[4][0]:ROW[4][1], L4B[0] - 1:L4B[0] - 1 + r3.shape[1]] = r3
def dup4(a):                                                                # row 3 shows row 4's letter
    r4 = C[ROW[4][0]:ROW[4][1], L4B[0] - 1:L4B[1] + 2].copy()
    a[ROW[3][0]:ROW[3][1], L3B[0] - 1:L3B[1] + 2] = 255; a[ROW[3][0]:ROW[3][1], L3B[0] - 1:L3B[0] - 1 + r4.shape[1]] = r4
def block3(a):                                                              # the row-3 letter a solid block (the fill rule)
    a[ROW[3][0] + 6:ROW[3][1] - 6, L3B[0]:L3B[1] + 1] = 0
l5('unfaked', lambda a: None, '[H] counter-suffix ios', 'OK')
l5('letters-swapped', swap, '[H] counter-suffix ios', 'WRONG')
l5('row4-shows-row3-letter', dup3, '[H] counter-suffix ios', 'WRONG')
l5('row3-shows-row4-letter', dup4, '[H] counter-suffix ios', 'WRONG')
l5('row3-letter-block', block3, '[H] counter-suffix ios', 'WRONG')
l5('unfaked', lambda a: None, '[K] counter-suffix ios', 'OK')
def px(a, x, y):
    a[y, x] = (0, 0, 0)
l5('px-x140-row11', lambda a: px(a, 140, 270), '[K] counter-suffix ios', 'WRONG')     # inside the x137-end zone of rows 11-12
l5('px-x137-row12', lambda a: px(a, 137, 296), '[K] counter-suffix ios', 'WRONG')     # the zone's first column
l5('px-x60-row11', lambda a: px(a, 60, 270), '[K] counter-suffix ios', 'WRONG')       # the x0-129 zone
l5('unfaked', lambda a: None, '[T] counter-suffix ios', 'OK')
def one_down3(a):                                                           # row 9's '1' 3 px low (the harness's T-1-down3)
    a[ROW[9][0] + 3:ROW[9][1], 136:151] = C[ROW[9][0]:ROW[9][1] - 3, 136:151]; a[ROW[9][0]:ROW[9][0] + 3, 136:151] = 255
l5('one-down3', one_down3, '[T] counter-suffix ios', 'WRONG')

# ── the residual windows recorded from the device pictures (fix-r6.zones.py --also … --write): the x136 column of rows 11-12 ──
say('\n## residual-read.py <fake> --base wave55-open: the recorded device windows read what the [K] zone no longer covers')
RR = os.path.join(HERE, 'residual-read.py')
def resid(name, f, cell, want):
    global fails
    a = C.copy(); f(a)
    rid = fake_run(name, 'css-counter-styles', 'ios', 'counter-suffix', a)
    p = subprocess.run(PY + [RR, rid, '--base', 'wave55-open', cell], capture_output=True, text=True, cwd=ROOT)
    got = p.stdout.strip().splitlines()[-1] if p.stdout.strip() else 'NO LINE'
    v = 'OK' if ' OK ' in got else ('FAIL' if ' FAIL ' in got else '??')
    ok = v == want; fails += 0 if ok else 1
    say(f'  {"as required" if ok else "VIOLATION  "}  {name:28} {cell:46} want {want:4} got {v:4} | {got[:150]}')
CELL = 'css-counter-styles/counter-suffix.html ios'
resid('unfaked', lambda a: None, CELL, 'OK')
resid('px-x136-y300-outside-win', lambda a: px(a, 136, 300), CELL, 'FAIL')           # x136 but outside both recorded windows
resid('px-x136-y272-red-in-win', lambda a: a.__setitem__((272, 136), (255, 0, 0)), CELL, 'FAIL')   # inside a window, not an AA colour
resid('px-x140-row11', lambda a: px(a, 140, 270), CELL, 'FAIL')                      # outside every zone and window

say(f'\nRESULT: {fails} violation(s)')
open(os.path.join(HERE, 'stage1-mutations.out.txt'), 'w').write('\n'.join(out) + '\n')
print('\n'.join(out))
sys.exit(1 if fails else 0)
