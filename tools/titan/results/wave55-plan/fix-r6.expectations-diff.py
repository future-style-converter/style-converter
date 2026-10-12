#!/usr/bin/env python3
# tools/titan/results/wave55-plan/fix-r6.expectations-diff.py — the STRUCTURAL diff of two expectations.json files (every JSON path
# whose value differs, was added or removed), for fix-r6.expectations-diff.txt: proves a regeneration changed only what it meant to (round 6: every difference is an APPEND — a string extended, a key or a planDecisions id added).
# Usage: python3 fix-r6.expectations-diff.py <before.json> <after.json> <before label> <plan-build.py path>
import hashlib, json, sys
a_p, b_p, label, gen = sys.argv[1:5]
A, B = json.load(open(a_p)), json.load(open(b_p))
sha = lambda p: hashlib.sha256(open(p, 'rb').read()).hexdigest()
out = []
def walk(x, y, path):
    if isinstance(x, dict) and isinstance(y, dict):
        for k in sorted(set(x) | set(y), key=str):
            p = f'{path}/{k}'
            if k not in y:
                out.append(('-', p, x[k], None))
            elif k not in x:
                out.append(('+', p, None, y[k]))
            else:
                walk(x[k], y[k], p)
    elif isinstance(x, list) and isinstance(y, list) and all(isinstance(e, dict) and 'id' in e for e in x + y):
        xi, yi = {e['id']: e for e in x}, {e['id']: e for e in y}       # planDecisions: by id
        for k in [e['id'] for e in x] + [e['id'] for e in y if e['id'] not in xi]:
            p = f'{path}[id={k}]'
            if k not in yi:
                out.append(('-', p, xi[k], None))
            elif k not in xi:
                out.append(('+', p, None, yi[k]))
            else:
                walk(xi[k], yi[k], p)
    elif isinstance(x, list) and isinstance(y, list) and len(x) == len(y):
        for i, (u, v) in enumerate(zip(x, y)):                         # same-length lists: element by element
            walk(u, v, f'{path}[{i}]')
    elif x != y:
        out.append(('~', path, x, y))
walk(A, B, '')
cut = lambda v: (json.dumps(v, ensure_ascii=False) if not isinstance(v, str) else v)
print(f'# fix-r6.expectations-diff.txt — the structural diff of expectations.json, {label} → the round-6 regeneration (plan-build.py, never hand-edited)')
print(f'# before sha256 {sha(a_p)} · after sha256 {sha(b_p)} (plan-build.py {sha(gen)[:16]}…)')
print(f'# {len(out)} difference(s); every other value identical (the JSON is the indent-1 rendering both times)\n')
for op, p, x, y in out:
    print(f'{op} {p}')
    if x is not None and op == '~':
        # the common prefix is shown once: these are appended sentences
        xs, ys = cut(x), cut(y); n = 0
        while n < min(len(xs), len(ys)) and xs[n] == ys[n]:
            n += 1
        print(f'  common prefix: {n} chars (…{xs[max(0, n - 60):n]})')
        print(f'  before (rest): {xs[n:]!r}')
        print(f'  after  (rest): {ys[n:]}')
    elif op == '+':
        print(f'  after:  {cut(y)}')
    else:
        print(f'  before: {cut(x)}')
    print()
print(f'# top-level keys: before {len(A)} · after {len(B)}; planDecisions ids: before {len(A.get("planDecisions", []))} · after {len(B.get("planDecisions", []))}')
# round 6: an APPEND is a '~' whose old string is a prefix of the new one, or a '+'; a '-' or any other '~' is not
nonappend = [p for op, p, x, y in out if op == '-' or (op == '~' and not (isinstance(x, str) and isinstance(y, str) and y.startswith(x)))]
print(f'# appends only: {"YES" if not nonappend else "NO — " + ", ".join(nonappend)}')
sys.exit(1 if nonappend else 0)
