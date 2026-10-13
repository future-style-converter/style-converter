#!/usr/bin/env python3
# Round-5 verifier: is a "nothing else changed" read feasible on the fixed pictures of the colour-mask keys? For every native
# capture of the L2 / L3 / L6 fixed pictures: the px that differ from the wave54-final capture OUTSIDE the key's own colour
# mask(s) of the fixed AND the base picture grown by g px (g = 1, 2). 0 means a residual read "the rest of the capture is the
# base's" accepts the fixed picture at tolerance 0 (replays / wave54-probe device captures; a device AA re-read is stage 1's).
import importlib.util, os, sys, hashlib
sys.dont_write_bytecode = True
import numpy as np
P = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave55-plan'
def load(n, p):
    s = importlib.util.spec_from_file_location(n, p); m = importlib.util.module_from_spec(s); s.loader.exec_module(m); return m
pt = load('pt', f'{P}/probes-teeth.py'); r2 = load('r2', f'{P}/probes-teeth-r2.py')
green = lambda a: (a[..., 0] < 8) & (np.abs(a[..., 1] - 128) < 8) & (a[..., 2] < 8)
blue3 = lambda a: (a[..., 2] > 150) & (a[..., 0] < 120) & (a[..., 1] < 120)
grn3 = lambda a: (a[..., 0] < 60) & (a[..., 1] > 100) & (a[..., 2] < 60)
mag = lambda a: (a[..., 0] > 200) & (a[..., 1] < 80) & (a[..., 2] > 200)
yel = lambda a: (a[..., 0] > 200) & (a[..., 1] > 200) & (a[..., 2] < 80)
ink = lambda a: a.min(axis=2) < 200
red6 = lambda a: (a[..., 0] >= 200) & (a[..., 1] <= 60) & (a[..., 2] <= 60)
blue6 = lambda a: (a[..., 2] >= 200) & (a[..., 0] <= 60) & (a[..., 1] <= 60)
dark6 = lambda a: a.sum(axis=2) < 150
teal = lambda a: (a[..., 0] <= 40) & (a[..., 1] >= 100) & (a[..., 1] <= 160) & (a[..., 2] >= 100) & (a[..., 2] <= 160)
yel6 = lambda a: (a[..., 0] >= 200) & (a[..., 1] >= 200) & (a[..., 2] <= 80)
def grow(m, g):
    o = m.copy()
    for _ in range(g):
        p = o.copy(); p[1:] |= o[:-1]; p[:-1] |= o[1:]; p[:, 1:] |= o[:, :-1]; p[:, :-1] |= o[:, 1:]
        p[1:, 1:] |= o[:-1, :-1]; p[1:, :-1] |= o[:-1, 1:]; p[:-1, 1:] |= o[1:, :-1]; p[:-1, :-1] |= o[1:, 1:]; o = p
    return o
def masks(test):
    n = test.split('/')[-1]
    if n.startswith('abspos-autopos') or n.startswith('backdrop-filter-nested'):
        return 'strict green', [green]
    if n == 'bidi-tab-001':
        return 'blue', [blue3]
    if n == 'dir-style-02a':
        return 'ink (min < 200)', [ink]
    if n.startswith('anchor-position'):
        return 'green', [grn3]
    if n.startswith('dynamic-offset'):
        return 'magenta + yellow', [mag, yel]
    if n == 'backdrop-filter-clip-rect':
        return 'red + blue', [red6, blue6]
    if 'flex-abspos' in n:
        return 'yellow fill + dark band', [yel6, dark6]
    if 'grid-abspos' in n:
        return 'teal fill + dark band', [teal, dark6]
    return None, None
seen = set()
for L, b in (('L2', pt.l2_cases), ('L3', pt.l3_cases), ('L6', pt.l6_cases)):
    for lane, secs, fid, repl, want in b() + r2.cases(pt, L):
        if not fid.endswith('fixed'):
            continue
        for x, F in repl.items():
            if x[1] == 'web':
                continue
            h = hashlib.sha1(F.tobytes()).hexdigest()
            if (x, h) in seen:
                continue
            seen.add((x, h))
            name, ms = masks(x[2])
            if not ms:
                continue
            B = pt.cap('wave54-final', x[0], x[2], x[1])
            d = (F != B).any(axis=2)
            m = np.zeros(d.shape, bool)
            for f in ms:
                m |= f(F) | f(B)
            out = [int((d & ~grow(m, g)).sum()) for g in (0, 1, 2)]
            print(f'{L} {fid:28} {x[2].split("/")[-1]:52} {x[1]:8} diff vs wave54-final {int(d.sum()):5} px; outside {name} (fixed ∪ base) grown 0/1/2 px: {out[0]} / {out[1]} / {out[2]}')
