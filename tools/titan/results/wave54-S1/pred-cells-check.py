#!/usr/bin/env python3
"""Every expectations.json prediction row's "from" value vs cells.mjs on wave54-open AND wave53-final (pointer audit, cells)."""
import json, re, subprocess
e = json.load(open('tools/titan/results/wave54-plan/expectations.json'))
rows = []
for ln, L in e['lanes'].items():
    for p in L.get('predictions', []):
        m = re.match(r'(\S+\.html) (web|ios|android)$', p.get('cell') or '')
        if m and p.get('from'): rows.append((ln, m.group(1), m.group(2), p['from']))
out = subprocess.run(['node', 'tools/titan/results/wave52-gate/cells.mjs', '|'.join(sorted({r[1] for r in rows})), 'wave54-open', 'wave53-final'], capture_output=True, text=True).stdout
got = {l[:78].strip(): [x.strip() for x in l[78:].split('→')] for l in out.splitlines()}
ok = 0
for ln, test, pf, frm in rows:
    g = got.get(f"{test.replace('css/', '')}|{pf}")
    f = re.match(r'([Pf])\s*([0-9.]+)', frm)
    if g and f and g[0].split() == [f.group(1), f.group(2)] and g[0] == g[1]: ok += 1
    else: print('ISSUE', ln, test, pf, frm, g)
print(f'prediction rows {len(rows)}: from == cells.mjs(wave54-open) == cells.mjs(wave53-final) on {ok}')
