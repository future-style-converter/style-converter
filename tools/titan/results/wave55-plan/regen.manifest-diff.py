#!/usr/bin/env python3
# tools/titan/results/wave55-plan/regen.manifest-diff.py — proves the pre-landing regeneration changed landing-manifest.json
# (land-units.sh --emit-manifest) ONLY in deliveryContract.cutOn's text (L3 NIT; PLAN §10 item 8): a structural walk of both
# JSONs, exit 1 on any other difference. Usage: python3 regen.manifest-diff.py <before.json> <after.json> <land-units.sh>
import hashlib, json, sys
a_p, b_p, lu = sys.argv[1:4]
A, B = json.load(open(a_p)), json.load(open(b_p))
sha = lambda p: hashlib.sha256(open(p, 'rb').read()).hexdigest()
out = []
def walk(x, y, path):
    if isinstance(x, dict) and isinstance(y, dict):
        for k in sorted(set(x) | set(y)):
            if k not in x or k not in y:
                out.append((path + '/' + k, x.get(k), y.get(k)))
            else:
                walk(x[k], y[k], path + '/' + k)
    elif isinstance(x, list) and isinstance(y, list) and len(x) == len(y):
        for i, (u, v) in enumerate(zip(x, y)):
            walk(u, v, f'{path}[{i}]')
    elif x != y:
        out.append((path, x, y))
walk(A, B, '')
print(f'# regen.manifest-diff.txt — landing-manifest.json before sha256 {sha(a_p)} · after {sha(b_p)} (land-units.sh {sha(lu)[:16]}…)')
for p, x, y in out:
    n = 0
    while isinstance(x, str) and isinstance(y, str) and n < min(len(x), len(y)) and x[n] == y[n]:
        n += 1
    print(f'~ {p}\n  common prefix {n} chars\n  before (rest): {x[n:] if isinstance(x, str) else x!r}\n  after  (rest): {y[n:] if isinstance(y, str) else y}')
ok = [p for p, _, _ in out] == ['/deliveryContract/cutOn']
print(f'# {len(out)} difference(s); exactly deliveryContract.cutOn: {"YES" if ok else "NO"}')
sys.exit(0 if ok else 1)
