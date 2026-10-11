import importlib.util, json, os, sys
import numpy as np
P = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave55-plan'
SC = sys.argv[1]
spec = importlib.util.spec_from_file_location('pt', os.path.join(P, 'probes-teeth.py')); pt = importlib.util.module_from_spec(spec); spec.loader.exec_module(pt)
EXP = os.path.join(P, 'expectations.json'); PY = ['/opt/homebrew/bin/python3.12', '-B']
b = pt.png('positioned-parent-slot-replay/bidi-tab-001-android-U1U2.png')
blue = (b[..., 2] > 150) & (b[..., 0] < 120) & (b[..., 1] < 120)
for dy in (3, 5):
    o = b.copy()
    for y0 in (86, 106, 186, 226):                     # the four RTL bands: their blue run painted twice, dy lower, inside the band
        reg = np.zeros(blue.shape, bool); reg[y0 - 2:y0 + 16] = True
        m = blue & reg; ys, xs = np.nonzero(m)
        ok = (ys + dy) < b.shape[0]
        o[ys[ok] + dy, xs[ok]] = b[ys[ok], xs[ok]]
    rid = pt.make_run(SC, f'L3-bidi-bandghost{dy}', {('css-text', 'android', 'bidi/bidi-tab-001'): o})
    rc, rows = pt.gate(rid, 'L3', 'css-text', EXP, PY, os.path.join(SC, f'gate-bidi-bandghost{dy}.json'))
    for (s, k), r in sorted(rows.items()):
        if 'bidi-tab-001 android' in k:
            print(f'bidi band ghost +{dy}y  {k:40} {r["verdict"]:5} {r["class"]:8} {r["line"][-150:]}')
    print('gate exit', rc)
