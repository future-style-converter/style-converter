#!/usr/bin/env python3
# Round-4 verifier: added ink restricted to ONE gating key's own zone of counter-suffix ios (so no sibling zone is touched):
# [H] rows 3-4 marker zone x0-63 x y64-111 and [T] row 9's digit window x136-150 x y208-231 — the zone's class-black ink
# copied 1 px down / 1 px right (written only where it is not already ink), and a 2x2 / 3x3 black blob on background beside
# the zone's glyph. Read through the real gate (probes-teeth make_run / gate). Usage: python3.12 -B l5-zone-fakes.py <scratch>
import importlib.util, json, os, sys
sys.dont_write_bytecode = True
import numpy as np
P = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave55-plan'
s = importlib.util.spec_from_file_location('pt', f'{P}/probes-teeth.py'); pt = importlib.util.module_from_spec(s); s.loader.exec_module(pt)
SC = sys.argv[1]; EXP = f'{P}/expectations.json'; PY = ['/opt/homebrew/bin/python3.12', '-B']
lane, secs, fid, repl, want = [c for c in pt.l5_cases() if c[2] == 'fixed'][0]
X = ('css-counter-styles', 'ios', 'counter-suffix'); F = repl[X]
ink = np.abs(F).sum(axis=2) <= 90                       # class black (as fakes4.py)
anyink = F.min(axis=2) < 200
def copy(zone, dx, dy):
    x0, y0, x1, y1 = zone; o = F.copy(); ys, xs = np.nonzero(ink[y0:y1, x0:x1]); ys += y0; xs += x0
    ty, tx = ys + dy, xs + dx; ok = ~ink[ty, tx]; o[ty[ok], tx[ok]] = F[ys[ok], xs[ok]]; return o
def blob(zone, n):
    x0, y0, x1, y1 = zone; ys, xs = np.nonzero(ink[y0:y1, x0:x1]); xr, ym = xs.max() + x0, int(np.median(ys)) + y0
    for dx in range(2, 8):                              # right of the zone's last glyph, on background, inside the zone
        if xr + dx + n <= x1 and not anyink[ym:ym + n, xr + dx - 1:xr + dx + n + 1].any():
            o = F.copy(); o[ym:ym + n, xr + dx:xr + dx + n] = 0; return o
H, T = (0, 64, 64, 112), (136, 208, 151, 232)
fakes = {'H-zone+1y': copy(H, 0, 1), 'H-zone+1x': copy(H, 1, 0), 'H-blob2': blob(H, 2), 'H-blob3': blob(H, 3),
         'T-window+1y': copy(T, 0, 1), 'T-window+1x': copy(T, 1, 0), 'T-blob2': blob(T, 2), 'T-blob3': blob(T, 3)}
rid = pt.make_run(SC, 'r4-L5-zone-FIXED', repl); _, base = pt.gate(rid, lane, secs, EXP, PY, f'{SC}/gate-r4-L5-zone-FIXED.json')
keys = sorted(k for k in base if 'counter-suffix ios' in k[1] and base[k]['verdict'] == 'PASS')
print('fixed:', ', '.join(f'{k[1]} {base[k]["verdict"]}' for k in keys))
for name, o in fakes.items():
    if o is None:
        print(f'{name:12} (no room)'); continue
    r = dict(repl); r[X] = o
    new = int(((o.min(axis=2) < 200) & ~anyink).sum())
    rid = pt.make_run(SC, f'r4-L5-zone-{name}', r); rc, rows = pt.gate(rid, lane, secs, EXP, PY, f'{SC}/gate-r4-L5-zone-{name}.json')
    print(f'{name:12} +{new:2} new ink px | ' + ', '.join(f'{k[1]}={rows[k]["verdict"]}({rows[k]["class"][:4]})' for k in keys))
