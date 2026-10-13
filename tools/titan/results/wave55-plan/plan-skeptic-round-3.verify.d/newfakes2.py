# round-3 verifier: a NEW fake class (added ink: the fixed picture double-painted, offset) through the real gate.
import importlib.util, json, os, sys
import numpy as np
P = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave55-plan'
SC = sys.argv[1]
spec = importlib.util.spec_from_file_location('pt', os.path.join(P, 'probes-teeth.py')); pt = importlib.util.module_from_spec(spec); spec.loader.exec_module(pt)
EXP = os.path.join(P, 'expectations.json'); PY = ['/opt/homebrew/bin/python3.12', '-B']
e = json.load(open(EXP))
gating = {(os.path.basename(next(t for t in pr['cmd'].split() if t.endswith('.py'))), k) for l in e['lanes'].values() for pr in l['geometryProbes'] for k in pr.get('gating', {})}
tally = {tuple(k.split(' ', 1)) for part in ('full', 'part', 'paintOrderOnly') for x in e['pictureCorrectTally'].get(part, []) for k in x['keys']}
T = {'faint50': lambda a: 255 - ((255 - a) * 0.5).astype(np.int16), 'gray-ink': lambda a: np.where((a.max(axis=2) < 110)[..., None], 120, a)}
fixed = [c for b in (pt.l1_cases, pt.l2_cases, pt.l3_cases, pt.l4_cases, pt.l5_cases, pt.l6_cases) for c in b() if c[2].endswith('fixed')]
res = []
for lane, secs, fid, repl, want in fixed:
    keys = [k for k, w in want.items() if w == 'PASS' and (k in gating or k in tally)]
    if not keys: continue
    for tn, tf in T.items():
        r2 = {k: tf(a) for k, a in repl.items()}
        rid = pt.make_run(SC, f'{lane}-{fid}-{tn}', r2)
        rc, rows = pt.gate(rid, lane, secs, EXP, PY, os.path.join(SC, f'gate-{lane}-{fid}-{tn}.json'))
        for k in keys:
            v = rows.get(k, {}).get('verdict', 'NO ROW')
            res.append((lane, fid, tn, k[0], k[1], v, rows.get(k, {}).get('line', '')[-160:]))
            print(f'{lane} {fid:12} {tn:11} {k[1]:62} {v}' + (f'   <- ACCEPTED: {rows[k]["line"][-140:]}' if v == 'PASS' else ''), flush=True)
acc = [r for r in res if r[5] == 'PASS']
print(f'\n{len(res)} key reads on {len(fixed)} fixed pictures x {len(T)} added-ink transforms; ACCEPTED {len(acc)}')
