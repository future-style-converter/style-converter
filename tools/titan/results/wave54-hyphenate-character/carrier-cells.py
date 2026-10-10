#!/usr/bin/env python3
"""tools/titan/results/wave54-hyphenate-character/carrier-cells.py — wave 54 lane L3: the carrier set's cells today
(from the plan's snapshot cells-<run>.json, written by snapshot-cells.mjs = the scorer's own loadRun), per platform,
with P/f counts — the "how many pass today" half of the census — and the lane's must-not-move cells from
expectations.json, each with its score. Usage: python3 carrier-cells.py [run]"""
import json, os, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../../..'))
RUN = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
cells = json.load(open(f'{ROOT}/tools/titan/results/wave54-plan/cells-{RUN}.json'))['cells']
E = json.load(open(f'{ROOT}/tools/titan/results/wave54-plan/expectations.json'))['lanes']['L3-hyphenate-character']
def cell(stem, p):
    # A capture stem flattens <section>/<subdir>/<test> with "__" (safe-name.mjs fixtureStem).
    k = stem.replace('wpt__', '', 1).replace('__', '/') + '.html ' + p
    return k, cells.get(k, 'UNSCORED')
print(f'carrier cells on {RUN}')
tot = {}
for p in ['web', 'ios', 'android']:
    for s in sorted(E['captureCarriers'][p]):
        k, v = cell(s, p); tot.setdefault(p, []).append(v.split()[0])
        print(f'   {p:<8} {s:<66} {v}')
for p, v in tot.items(): print(f'   {p}: {len(v)} carriers — P {v.count("P")} · f {v.count("f")} · other {len(v) - v.count("P") - v.count("f")}')
mnm = E.get('mustNotMove', [])
print(f'\nmust-not-move ({len(mnm)} lines) on {RUN}:')
for m in mnm:
    # Each must-not-move line IS a snapshot key ("<test>.html <platform>").
    print(f'   {m:<70} {cells.get(m, "UNSCORED")}')
