#!/usr/bin/env python3
# plan-dry-run.json-diff.py — L4 fix pass: the structural diff (paths that differ) of the h2 and h1h2 dry-run
# expectations.json against the installed one, so a reader sees exactly what each hunk changes. Read-only.
import json, os
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..', '..'))
D = os.path.join(ROOT, 'tools/titan/results/wave54-oof-layout/fix/dry/')
base = json.load(open(os.path.join(ROOT, 'tools/titan/results/wave54-plan/expectations.json')))
def walk(a, b, p, out):
    # Recurse into dicts and equal-length lists; record a length change or a leaf change at its path.
    if type(a) != type(b): out.append((p, a, b)); return
    if isinstance(a, dict):
        for k in sorted(set(a) | set(b)): walk(a.get(k, '<absent>'), b.get(k, '<absent>'), p + '/' + str(k), out)
    elif isinstance(a, list):
        if len(a) != len(b): out.append((p, f'len {len(a)}', f'len {len(b)}')); return
        for i, (x, y) in enumerate(zip(a, b)): walk(x, y, p + f'[{i}]', out)
    elif a != b: out.append((p, a, b))
for v in ('h2', 'h1h2'):
    e = json.load(open(D + v + '/out/expectations.json')); out = []; walk(base, e, '', out)
    print(f'== {v}: {len(out)} differing paths')
    for p, a, b in out: print('  ', p, '\n      installed:', str(a)[:200], '\n      variant  :', str(b)[:200])
h = json.load(open(D + 'h1h2/out/expectations.json'))
print('h1h2 stayDegenerateEvenIfPass:', json.dumps(h['stayDegenerateEvenIfPass'], ensure_ascii=False, indent=1))
print('h1h2 CBB-android android captures:', len(h['lanes']['L4-oof-layout']['revertUnits']['CBB-android']['captures']['android']))
print('h1h2 contain-content-011 android row:', json.dumps([p for p in h['lanes']['L4-oof-layout']['predictions'] if p['cell'] == 'css-contain/contain-content-011.html android'], ensure_ascii=False))
