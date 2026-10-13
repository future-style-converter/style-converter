#!/usr/bin/env python3
# tools/titan/results/wave55-plan/fix-r6.teeth-r5-equiv.py — proves probes-teeth-r5.py pins the VERIFIER's own fakes (round 6).
#
# For every pinned case (one fake run of plan-skeptic-round-5.verify.d's sweep holding a > 2-px acceptance): every faked capture is
# rebuilt twice — by probes-teeth-r5.py (plan-skeptic-round-5.fakes/added_ink5.py) and by the verifier's ORIGINAL generator
# (plan-skeptic-round-5.verify.d/fakes5.py, its builder section executed in-process: its sweep loop is never run) — and the two
# pictures must be byte-identical; and the changed-px count and bbox against the fixed picture must equal the verifier's record
# ('added', 'bbox'). Exit 1 on any difference. Writes nothing (the record is this script's stdout).
# Usage: python3.12 -B fix-r6.teeth-r5-equiv.py > fix-r6.teeth-r5-equiv.out.txt
import hashlib, importlib.util, os, sys
sys.dont_write_bytecode = True
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
def load(n, p):
    s = importlib.util.spec_from_file_location(n, os.path.join(HERE, p)); m = importlib.util.module_from_spec(s); s.loader.exec_module(m); return m
r5 = load('probes_teeth_r5', 'probes-teeth-r5.py')
pt = load('probes_teeth', 'probes-teeth.py')
V = os.path.join(HERE, 'plan-skeptic-round-5.verify.d', 'fakes5.py')
src = open(V).read()
src = src[:src.index('\nlines = []')]                    # the verifier's definitions only (its sweep starts at `lines = []`)
g = {'__name__': 'fakes5_original', '__file__': V}
_argv = sys.argv; sys.argv = ['fakes5.py', '--scratch', '/nonexistent-not-written']
exec(compile(src, V, 'exec'), g)
sys.argv = _argv
sha = lambda p: hashlib.sha256(open(p, 'rb').read()).hexdigest()[:16]
print(f'# fix-r6.teeth-r5-equiv — probes-teeth-r5.py {sha(os.path.join(HERE, "probes-teeth-r5.py"))}, added_ink5.py '
      f'{sha(os.path.join(HERE, "plan-skeptic-round-5.fakes", "added_ink5.py"))} vs the verifier\'s fakes5.py {sha(V)} (its builders, executed '
      'in-process with its own hard-coded plan path)')
ggroups = {(ln, gid): repl for ln, secs, gid, repl in g['groups']()}
n_cases = n_caps = bad = 0
for lane in ('L1', 'L2', 'L3', 'L4', 'L5', 'L6'):
    cs = r5.cases(pt, lane)
    runs = {run: (rs, acc) for run, rs, acc in r5.pinned(lane)}
    lane_caps = 0
    for ln, secs, fid, repl, want in cs:
        n_cases += 1
        rs, acc = runs[fid]
        import re
        m = re.search(r'-z(\d+)c(\d+)-(.+)$', fid); j, ci, v = int(m[1]), int(m[2]), m[3]
        for r in rs:
            x = (r['sec'], r['plat'], r['test'])
            F = ggroups[(lane, r['group'])][x]; B = pt.cap('wave54-final', x[0], x[2], x[1])
            zs = [(zn, g['Zone'](F, B, boxes)) for zn, boxes, keys in g['zones_of'](*x)]
            z = zs[j][1]; k = g['K'](z, z.classes[ci])
            o = g['build'](k, v)
            mine = repl[x]
            same = o is not None and o.shape == mine.shape and (o == mine).all()
            d = (mine != F).any(axis=2); ys, xs = np.nonzero(d)
            bb = [int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())] if len(ys) else None
            rec_ok = int(d.sum()) == r['added'] and bb == r['bbox']
            n_caps += 1; lane_caps += 1
            if not (same and rec_ok):
                bad += 1
                print(f'  DIFFER {lane} {fid} {x[2]} {x[1]}: picture identical {same}; added {int(d.sum())} vs record {r["added"]}, bbox {bb} vs {r["bbox"]}')
    print(f'  {lane}: {len(cs)} pinned cases, {lane_caps} faked captures; keys pre-registered FAIL {sum(len(c[4]) for c in cs)}')
print(f'\n{n_cases} pinned cases, {n_caps} faked captures: {n_caps - bad} byte-identical to the verifier\'s generator with its record\'s added px and bbox, '
      f'{bad} differ')
sys.exit(1 if bad else 0)
