#!/usr/bin/env python3
# tools/titan/results/wave55-plan/fix-r6.residual-on-r5.py — the round-6 residual read on EVERY acceptance the round-5 verifier
# recorded (plan-skeptic-round-5.verify.d/fakes5.L*.json: 704 > 2 px, 85 on the floor), in-process (fix-r6.done.md).
#
# Why: §T5 (probes-selfcheck.py) re-runs the verifier's whole sweep through the real gate, which is the proof; this is the
# per-acceptance attribution beside it. Each accepted fake is rebuilt with the verifier's ORIGINAL generator (its builder
# section executed in-process) and read by residual-read.py against wave54-final: OUTSIDE / VACATED / NON-CLASS says which read
# now rejects it; OK means the residual read alone does not (then a probe read must, or it is on the named 2-px floor).
# Usage: python3.12 -B fix-r6.residual-on-r5.py > fix-r6.residual-on-r5.out.txt
import collections, hashlib, importlib.util, json, os, sys
sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
V5 = os.path.join(HERE, 'plan-skeptic-round-5.verify.d')
src = open(os.path.join(V5, 'fakes5.py')).read()
src = src[:src.index('\nlines = []')]                    # the verifier's definitions only
_argv = sys.argv; sys.argv = ['fakes5.py', '--scratch', '/nonexistent-not-written']
g = {'__name__': 'fakes5_original', '__file__': os.path.join(V5, 'fakes5.py')}
exec(compile(src, os.path.join(V5, 'fakes5.py'), 'exec'), g)
sys.argv = _argv
s_ = importlib.util.spec_from_file_location('residual_read', os.path.join(HERE, 'residual-read.py')); RR = importlib.util.module_from_spec(s_); s_.loader.exec_module(RR)
pt = g['pt']
sha = lambda p: hashlib.sha256(open(p, 'rb').read()).hexdigest()[:16]
print(f'# fix-r6.residual-on-r5 — residual-read.py {sha(os.path.join(HERE, "residual-read.py"))} on the round-5 records (fakes5.py '
      f'{sha(os.path.join(V5, "fakes5.py"))}, its builders); base wave54-final')
recs = [r for L in ('L1', 'L2', 'L3', 'L4', 'L5', 'L6') for r in json.load(open(os.path.join(V5, f'fakes5.{L}.json')))]
acc = [r for r in recs if r['gatingPassQ'] or r['tallyPass']]
groups = {(ln, gid): repl for ln, secs, gid, repl in g['groups']()}
cache, tally, left = {}, collections.Counter(), []
for r in acc:
    x = (r['sec'], r['plat'], r['test'])
    if (r['lane'], r['group'], x) not in cache:
        F = groups[(r['lane'], r['group'])][x]; B = pt.cap('wave54-final', x[0], x[2], x[1])
        cache[(r['lane'], r['group'], x)] = (B, [[g['K'](z, c) for c in z.classes] for z in (g['Zone'](F, B, boxes) for zn, boxes, keys in g['zones_of'](*x))])
    B, zs = cache[(r['lane'], r['group'], x)]
    ks = zs[r['zone']]
    ci = next(i for i, kk in enumerate(ks) if tuple(int(t) for t in kk.c) == tuple(r['class']))
    o = g['build'](ks[ci], r['variant'])
    ro, rv, rn, _ = RR.read_arrays(o, B, RR.SPECS[f'{x[0]}/{x[2]}.html {x[1]}'])
    how = 'OUTSIDE' if ro else 'VACATED' if rv else 'NON-CLASS' if rn else 'OK'
    tally[(r['lane'], 'floor' if r['added'] <= 2 else '> 2 px', how)] += 1
    if how == 'OK':
        left.append(r)
print(f'\n{len(acc)} accepted faked zones (> 2 px {sum(r["added"] > 2 for r in acc)}, floor {sum(r["added"] <= 2 for r in acc)}); the read that now rejects each:')
for k in sorted(tally):
    print(f'  {k[0]} {k[1]:6} {k[2]:9} {tally[k]}')
print(f'\nnot rejected by the residual read: {len(left)} (> 2 px: {sum(r["added"] > 2 for r in left)})')
for r in left:
    print(f'  {"FLOOR" if r["added"] <= 2 else "> 2px"} {r["lane"]} {r["group"]:28} z{r["zone"]} {r["variant"]:16} class {tuple(r["class"])} {r["capture"]:55} '
          f'+{r["added"]} bbox {r["bbox"]}')
print('\n(the > 2-px ones are the three L3 dyn 2×2 yellow-over-magenta holes: equal to the base there, so no residual read can see them; '
      'the L3 probe\'s magenta count ±2 rejects them — probes-teeth-r5.py pins all three FAIL through the real gate)')
