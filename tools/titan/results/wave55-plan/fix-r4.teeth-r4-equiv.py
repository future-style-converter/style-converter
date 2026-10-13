#!/usr/bin/env python3
# tools/titan/results/wave55-plan/fix-r4.teeth-r4-equiv.py — proves probes-teeth-r4.py's pinned pictures ARE the verifier's fakes:
# for every IN_ZONE case it builds (plan-skeptic-round-4.fakes/added_ink4.py, the functions copied from the verifier's fakes4.py),
# every faked capture is compared, decoded pixel for pixel, with the capture fakes4.py itself wrote into its fake run of the same
# group and variant (a sweep scratch dir: `fakes4.py --scratch <dir>/<lane> --groups <lane>`). Read-only; prints one line per case.
# Usage: python3 fix-r4.teeth-r4-equiv.py <sweep scratch dir holding L4/ L5/ L6/>
import importlib.util, os, sys
import numpy as np
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__)); SC = sys.argv[1]
def load(n, p):
    s = importlib.util.spec_from_file_location(n, p); m = importlib.util.module_from_spec(s); s.loader.exec_module(m); return m
pt = load('pt', os.path.join(HERE, 'probes-teeth.py')); r4 = load('r4', os.path.join(HERE, 'probes-teeth-r4.py'))
cases = {c[2]: c for L in ('L4', 'L5', 'L6') for c in r4.cases(pt, L)}
bad = n = pics = 0
for lane, gid, var, keys in r4.IN_ZONE:
    fid = f'r4-{gid}-{var.replace(":", "_")}'
    _, secs, _, repl, want = cases[fid]
    run = os.path.join(SC, lane, 'fakeruns', f'r4-{lane}-{gid}-{var.replace(":", "_")}')
    diffs, faked = [], 0
    for (sec, plat, test), a in repl.items():
        if plat == 'web':
            continue
        p = os.path.join(run, 'sections', sec, pt.DIRS[plat], pt.cap_name(sec, test))
        b = np.asarray(Image.open(os.path.realpath(p)).convert('RGB')).astype(np.int16)
        fixed = [c for c in {'L4': pt.l4_cases, 'L5': pt.l5_cases, 'L6': pt.l6_cases}[lane]() if c[2] == gid][0][3][(sec, plat, test)]
        faked += not (a == fixed).all()
        pics += 1
        if a.shape != b.shape or not (a == b).all():
            diffs.append(f'{test.split("/")[-1]} {plat}')
    n += 1; bad += bool(diffs)
    print(f'  {"IDENTICAL" if not diffs else "DIFFERS  "} {lane} {gid} {var:22} {faked} faked capture(s), {len(want)} key(s) pre-registered FAIL' + (f' — {diffs}' if diffs else ''))
print(f'\n{n} pinned round-4 cases, {pics} native captures compared: {n - bad} identical to fakes4.py\'s own fake runs, {bad} differ')
sys.exit(1 if bad else 0)
