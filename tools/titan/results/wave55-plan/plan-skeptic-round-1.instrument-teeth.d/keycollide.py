# For every registered probe (expectations.json), run it once on wave54-final (base wave54-final) and count the lines
# geometry-gate.py's find_line() matches per expect key (its code, verbatim): a key matched by >1 line is read from the
# LAST one (hits[-1]) — a key whose tokens prefix another key's line, or a duplicate line, would read the wrong verdict.
import json, re, subprocess
from lib import *
E = json.load(open(f'{P55}/expectations.json'))
norm = lambda s: re.sub(r'\s+', ' ', s.strip())
def find_line(lines, key):
    kt = key.split(); hits = []
    for l in lines:
        t = l.split()
        for i in range(0, min(3, max(0, len(t) - len(kt) + 1))):
            if t[i:i + len(kt)] == kt:
                hits.append(l); break
    return hits
multi, zero, total = [], [], 0
for lid, lane in E['lanes'].items():
    for pr in lane['geometryProbes']:
        cmd = pr['cmd'].replace('<run>', 'wave54-final').replace('<base>', 'wave54-final')
        p = subprocess.run(['nice', '-n', '19'] + cmd.split(), cwd=ROOT, capture_output=True, text=True)
        lines = [norm(l) for l in p.stdout.splitlines() if l.strip()]
        for k in pr['expect']:
            total += 1; h = find_line(lines, k)
            if len(h) > 1: multi.append((lid, cmd.split()[2].split('/')[-1], k, len(h), [x[:110] for x in h]))
            if not h: zero.append((lid, k))
print(f'keys {total}; matched by >1 line: {len(multi)}; by none: {len(zero)}')
for m in multi: print('  MULTI', m[0], m[1], repr(m[2]), m[3]); [print('     ', x) for x in m[4]]
for z in zero: print('  NONE', z)
# the same rule against every OTHER key of the same probe: a key whose tokens are a prefix of another key's
for lid, lane in E['lanes'].items():
    for pr in lane['geometryProbes']:
        ks = list(pr['expect'])
        for a in ks:
            for b in ks:
                if a != b and b.split()[:len(a.split())] == a.split():
                    print('  PREFIX', lid, repr(a), 'prefixes', repr(b))
