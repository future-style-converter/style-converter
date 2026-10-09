#!/usr/bin/env python3
# wave54-S1/fix/mutate-planbuild.py (usage: python3 mutate-planbuild.py <scratch-export-dir>) — mutation proof for
# the S1-fix plan-build.py pin (stayDegenerateEvenIfPass <-> prediction kinds), in a mini export:
# tools/titan/results/wave54-plan copied, tools/titan/runs symlinked (plan-build reads RUNDIR only). Each mutation:
# anchor occurs once -> mutate -> run (expect rc != 0 with the pin's own message) -> restore from saved bytes ->
# sha256 before == after -> re-run (rc 0, outputs byte-identical to the shared tree's regenerated files).
import hashlib, os, shutil, subprocess, sys, filecmp
R = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..', '..'))   # repo root
EXP = sys.argv[1]
shutil.rmtree(EXP, ignore_errors=True)
shutil.copytree(f'{R}/tools/titan/results/wave54-plan', f'{EXP}/tools/titan/results/wave54-plan', symlinks=True)
os.makedirs(f'{EXP}/tools/titan', exist_ok=True); os.symlink(f'{R}/tools/titan/runs', f'{EXP}/tools/titan/runs')
P = f'{EXP}/tools/titan/results/wave54-plan/plan-build.py'
sha = lambda p: hashlib.sha256(open(p, 'rb').read()).hexdigest()
def run():
    out = f'{EXP}/out'; shutil.rmtree(out, ignore_errors=True); os.makedirs(out)
    p = subprocess.run(['python3', P, '--out', out], capture_output=True, text=True)
    same = p.returncode == 0 and all(filecmp.cmp(f'{out}/{f}', f'{R}/tools/titan/results/wave54-plan/{f}', shallow=False)
                                     for f in ('expectations.json', 'watchlist.txt'))
    last = (p.stderr.strip().splitlines() or [''])[-1]
    return p.returncode, same, last
MUTS = [
  ('MUT-a drop the semi-replaced-stretch-other entry from the list (the D4 state: a kind label with no list entry)',
   "                               f'{SRO} web (the label paints \"abel\" where the ref paints \"label\": its \"l\" lies under the green border; RS moves only the static position)',\n", ''),
  ('MUT-b an entry naming a cell with no prediction row (scope-pseudo-element web -> ios)',
   "f'{T(\"css-cascade\", \"scope-pseudo-element\")} web (the B/Foo wrap", "f'{T(\"css-cascade\", \"scope-pseudo-element\")} ios (the B/Foo wrap"),
]
rc, same, _ = run(); print(f'baseline: rc {rc}, outputs == shared tree: {same}')
for name, old, new in MUTS:
    saved = open(P, 'rb').read(); before = sha(P); s = saved.decode()
    assert s.count(old) == 1, name
    open(P, 'w', encoding='utf-8').write(s.replace(old, new))
    rc, _, last = run(); print(f'{name}: rc {rc} -> {"RED" if rc else "GREEN (pin did not fire)"}\n  {last[:230]}')
    open(P, 'wb').write(saved); after = sha(P)
    print(f'  restore: sha256 {before[:16]} -> {after[:16]} {"BYTE-EXACT" if before == after else "DIFFERS"}; == shared tree: {after == sha(f"{R}/tools/titan/results/wave54-plan/plan-build.py")}')
    rc, same, _ = run(); print(f'  re-run: rc {rc}, outputs == shared tree: {same} -> {"GREEN" if rc == 0 and same else "RED"}')
