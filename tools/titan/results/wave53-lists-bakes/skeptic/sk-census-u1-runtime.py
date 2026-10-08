#!/usr/bin/env python3
# tools/titan/results/wave53-lists-bakes/skeptic/sk-census-u1-runtime.py — the L1 SKEPTIC's own census of the
# component shapes the C/D folds (PseudoTextFold.{swift,kt} runs branch) can reach, over EVERY per-test IR document
# of a run (default wave52-ship). Counts: runs carriers; runs+before; runs+before with a baked `_text`; runs+after;
# runs whose first entry is a child; and the same for the wave53-open run, plus a byte-identity check of the two runs.
import json, glob, os, sys, hashlib
R = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..', '..'))
def census(run):
    files = sorted(glob.glob(f'{R}/tools/titan/runs/{run}/sections/*/per-test-ir/*.json'))
    rows = {'docs': len(files), 'runs': [], 'runs+before': [], 'runs+before._text': [], 'runs+after': [], 'runs child-first+before': []}
    for p in files:
        d = json.load(open(p, encoding='utf-8'))
        for c in d.get('components', []):
            m = c.get('meta') or {}; ps = c.get('pseudos') or {}
            if not m.get('runs'): continue
            key = f"{os.path.basename(p)}:{c['name']}"
            rows['runs'].append(key)
            if 'before' in ps:
                rows['runs+before'].append(key)
                if isinstance(ps['before'], dict) and ('_text' in ps['before'] or 'text' in ps['before']): rows['runs+before._text'].append(key)
                if 'child' in m['runs'][0]: rows['runs child-first+before'].append(key)
            if 'after' in ps: rows['runs+after'].append(key)
    return rows, files
for run in sys.argv[1:] or ['wave52-ship', 'wave53-open']:
    rows, files = census(run)
    print(run, {k: (v if isinstance(v, int) else len(v)) for k, v in rows.items()})
    for k in ('runs+before', 'runs+before._text', 'runs+after', 'runs child-first+before'):
        for x in rows[k]: print('   ', k, x)
a = {os.path.relpath(p, f'{R}/tools/titan/runs/wave52-ship'): hashlib.sha1(open(p, 'rb').read()).hexdigest() for p in glob.glob(f'{R}/tools/titan/runs/wave52-ship/sections/*/per-test-ir/*.json')}
b = {os.path.relpath(p, f'{R}/tools/titan/runs/wave53-open'): hashlib.sha1(open(p, 'rb').read()).hexdigest() for p in glob.glob(f'{R}/tools/titan/runs/wave53-open/sections/*/per-test-ir/*.json')}
print('per-test IR wave52-ship', len(a), 'wave53-open', len(b), 'common', len(a.keys() & b.keys()), 'byte-different', sum(a[k] != b[k] for k in a.keys() & b.keys()))
