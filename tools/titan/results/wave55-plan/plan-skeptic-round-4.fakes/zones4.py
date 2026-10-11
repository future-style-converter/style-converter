#!/usr/bin/env python3
# Round-4 verifier: zone adjudication of fakes4.py's acceptances. For every HOLE / CTRL-ONLY / GATE-PASS line of a fakes4
# output, the faked capture is re-read from the fake run (and its fixed picture from the group's FIXED run) and the NEW INK
# (pixels that become ink under the lane probe's OWN ink mask, see mask(); the any-mask count beside it) is counted INSIDE THE OWN ZONE of each gating key under
# test that PASSED it. Zones are the probes' own: one key per capture reads the whole capture; dir-style-02a rows 6 / 7 / 9 =
# their 20-px row bands (positioned-parent-slot.geometry.py rows 16+20(k-1)); bidi-tab-001 rtl-rows = RTL_BANDS (86, 106,
# 186, 226) x 20; counter-suffix [H] ios = x0-63 x y64-111, [C] = x0-63 x y112-159 (the complement of [K]'s ZONES),
# [T] ios = row 9 y208-231 (digit window + ROW9_OUT). An acceptance with > 2 new-ink px inside the passing key's own zone is
# IN-ZONE (the key accepted added ink it reads); otherwise the ink lay in a sibling's zone (benign).
# Usage: python3.12 -B zones4.py <scratch-dir-of-the-lane> <fakes4.Lx.out.txt>
import glob, os, re, sys
import numpy as np
from PIL import Image
SC, OUT = sys.argv[1], sys.argv[2]
import json
E = json.load(open(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'expectations.json')))   # round-4 fix: this copy's plan dir
GATING = {k for l in E['lanes'].values() for pr in l['geometryProbes'] for k in pr.get('gating', {})}
A = lambda p: np.asarray(Image.open(p).convert('RGB')).astype(np.int16)
newink_any = lambda o, f: ((o.min(axis=2) < 200) & ~(f.min(axis=2) < 200)) | ((o.sum(axis=2) < 300) & ~(f.sum(axis=2) < 300))
# The lane probe's OWN ink mask (the count that decides IN-ZONE): L1 / L3 dir + magenta / L5 `ink:` read min channel < 200;
# L4 reads dark = RGB sum < 300 (_dk); L2 the green mask (|c - (0,128,0)| < 8 per channel); L3 bidi the blue mask; L3 stack
# green; L6 the child-fill masks (yellow / teal) and red ([PO]).
GID = ''
def mask(lane, cap, a):
    if lane == 'L4':                                  # U1 (today's face / 011): the crop's ink XOR reads min < 200; the
        return (a.min(axis=2) < 200) if GID.startswith('U1') or '011' in cap else (a.sum(axis=2) < 300)   # face-present face ink reads dark
    if lane == 'L2' or cap.startswith('anchor-position-multicol'):
        return (a[..., 0] < 8) & (np.abs(a[..., 1] - 128) < 8) & (a[..., 2] < 8)
    if cap.startswith('bidi-tab'):
        return (a[..., 2] > 150) & (a[..., 0] < 120) & (a[..., 1] < 120)
    if lane == 'L6':
        yel = (a[..., 0] >= 200) & (a[..., 1] >= 200) & (a[..., 2] <= 80)
        teal = (a[..., 0] <= 40) & (a[..., 1] >= 100) & (a[..., 1] <= 160) & (a[..., 2] >= 100) & (a[..., 2] <= 160)
        red = (a[..., 0] >= 200) & (a[..., 1] <= 60) & (a[..., 2] <= 60)
        return yel | teal | red
    return a.min(axis=2) < 200
def zone(key, shape):
    H, W = shape; m = np.zeros(shape, bool)
    r = re.search(r'dir-style-02a android row(\d)', key)
    if r:
        k = int(r.group(1)); m[16 + 20 * (k - 1):36 + 20 * (k - 1)] = True; return m
    if 'rtl-rows' in key:
        for y in (86, 106, 186, 226):
            m[y:y + 20] = True
        return m
    if key == '[H] counter-suffix ios':
        m[64:112, 0:64] = True; return m
    if key in ('[C] counter-suffix ios', '[C] counter-suffix android'):
        m[112:160, 0:64] = True; return m
    if key == '[T] counter-suffix ios':
        m[208:232] = True; return m
    m[:] = True; return m
def find(run, cap, plat):
    d = {'ios': 'ios-screenshots', 'android': 'android-screenshots'}[plat]
    g = [p for p in glob.glob(f'{SC}/fakeruns/{run}/sections/*/{d}/*.png') if p.endswith('__' + cap + '.png') and not os.path.islink(p)]
    return g[0] if g else None
fixed_of = {}
res = {'IN-ZONE': [], 'benign': []}
for line in open(OUT):
    m = re.match(r'  (HOLE|CTRL-ONLY|GATE-PASS)(\(<=2\))? (\S+) (\S+) (\S+) — (\S+) (ios|android)(.*)', line.rstrip('\n'))
    if not m:
        continue
    kind, small, lane, gid, var, cap, plat, rest = m.groups(); GID = gid
    tag = var.replace(':', '_')
    fp = find(f'r4-{lane}-{gid}-{tag}', cap, plat)
    if fp is None:
        print('NO FAKE FILE', line.strip()[:120]); continue
    rel = fp.split('/sections/', 1)[1]
    base = f'{SC}/fakeruns/r4-{lane}-{gid}-FIXED/sections/{rel}'
    o, f = A(fp), A(os.path.realpath(base))
    ni = mask(lane, cap, o) & ~mask(lane, cap, f); na = newink_any(o, f)
    if kind == 'GATE-PASS':
        keys = [re.search(r': "([^"]+)" PASS', rest).group(1)]
    elif kind == 'CTRL-ONLY':
        keys = eval(re.search(r'gating keys under test PASS (\[.*\])', rest).group(1))
    else:
        keys = [k for k in eval(re.search(r"readers (\[.*?'\])", rest).group(1)) if k in GATING] or ['(capture: tally / control keys only)']
    for k in keys:
        z = zone(k, ni.shape); n = int((ni & z).sum())
        res['IN-ZONE' if n > 2 else 'benign'].append((kind, lane, gid, var, f'{cap} {plat}', k, n, int((na & z).sum())))
for cl in ('IN-ZONE', 'benign'):
    print(f'## {cl}: {len(res[cl])}')
    for r in res[cl]:
        print(f'  {r[0]:9} {r[1]} {r[2]} {r[3]:22} {r[4]:45} key "{r[5]}" new ink in its zone: probe mask {r[6]} (any mask {r[7]})')
