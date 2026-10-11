# round-3 verifier: L3 dir-style-02a — every passing row double-painted INSIDE its own row band only (the duplicate of a
# mounted child at another y), all three gating rows at once, through the real gate; plus the [C] unaffected-rows control.
import importlib.util, json, os, sys
import numpy as np
P = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave55-plan'
SC = sys.argv[1]
spec = importlib.util.spec_from_file_location('pt', os.path.join(P, 'probes-teeth.py')); pt = importlib.util.module_from_spec(spec); spec.loader.exec_module(pt)
EXP = os.path.join(P, 'expectations.json'); PY = ['/opt/homebrew/bin/python3.12', '-B']
base = pt.png('positioned-parent-slot-replay/dir-style-02a-android-U1U2.png') if os.path.exists(os.path.join(P, 'positioned-parent-slot-replay/dir-style-02a-android-U1U2.png')) else None
if base is None:
    print(sorted(os.listdir(os.path.join(P, 'positioned-parent-slot-replay')))); sys.exit(2)
for dy in (3, 5, 7):
    o = base.copy()
    for k in (6, 7, 9):
        y0 = 16 + 20 * (k - 1)
        band = base[y0 - 2:y0 + 18].copy()
        sh = pt.shift(band, 0, dy)
        o[y0 - 2:y0 + 18] = np.minimum(band, sh)
    rid = pt.make_run(SC, f'L3-dir-bandghost{dy}', {('selectors', 'android', 'dir-style-02a'): o})
    rc, rows = pt.gate(rid, 'L3', 'selectors', EXP, PY, os.path.join(SC, f'gate-bandghost{dy}.json'))
    for (s, k), r in sorted(rows.items()):
        if 'dir-style-02a android' in k:
            print(f'band ghost +{dy}y  {k:45} {r["verdict"]:5} {r["class"]:8} {r["line"][-110:]}')
    print('gate exit', rc)
