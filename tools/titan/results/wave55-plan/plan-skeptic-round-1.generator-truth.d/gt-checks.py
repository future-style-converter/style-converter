#!/usr/bin/env python3
# plan-skeptic round 1 (generator-truth) — the structural checks, independent of plan-build.py:
#   F  every prediction "from" equals the run of record's cell (own snapshot via the scorer's loader)
#   P  carriers / named must-not-move / complement / ring-fence partition every scored cell (no hole, no overlap)
#   W  every watch line matches exactly one scored cell (score-gate.mjs watchCells' substring rule, re-implemented)
#   G  every gating prediction floors at 0.95 and predicts P
# Usage: gt-checks.py <expectations.json> <own-cells.json> <watchlist.txt>   → exit 1 on any finding
import json, re, sys
exp, cellsp, watchp = sys.argv[1:4]
E = json.load(open(exp)); C = json.load(open(cellsp)); bad = []
tests = {k.rsplit(' ', 1)[0] for k in C}
stem2test = {'wpt__' + re.sub(r'\.html$', '', t).replace('/', '__'): t for t in tests}
def cells_of(stems, p): return {stem2test[s] + ' ' + p for s in stems if s in stem2test and stem2test[s] + ' ' + p in C}
# F
nF = 0
for ln, L in E['lanes'].items():
    for p in L['predictions']:
        if not re.match(r'^\S+\.html (web|ios|android)$', p['cell']): continue
        nF += 1; m = re.match(r'^([Pf]) ([0-9.]+)', p['from'])
        if not m or C.get(p['cell']) != f'{m.group(1)} {m.group(2)}': bad.append(f'F {ln} {p["cell"]} from={p["from"]} run={C.get(p["cell"])}')
# P
car = set().union(*[cells_of(v, pf) for L in E['lanes'].values() for pf, v in L['captureCarriers'].items()])
named = set().union(*[set(L['mustNotMove']) for L in E['lanes'].values()])
comp = set(E['mustNotMoveComplement']['cells']); ring = set(E['ringFenced']['cells'])
for a, b, n in ((car, named, 'carrier&named'), (car, comp, 'carrier&complement'), (car, ring, 'carrier&ring'), (named, comp, 'named&complement'), (named, ring, 'named&ring'), (comp, ring, 'complement&ring')):
    if a & b: bad.append(f'P overlap {n}: {sorted(a & b)[:3]}')
hole = set(C) - car - named - comp - ring
if hole: bad.append(f'P hole {len(hole)}: {sorted(hole)[:3]}')
alien = (named | comp | ring) - set(C)
if alien: bad.append(f'P not a scored cell: {sorted(alien)[:3]}')
# W
W = [l.strip() for l in open(watchp) if l.strip() and not l.startswith('#')]
for w in W:
    pat, plat = w.split()
    m = [k for k in C if k.endswith(' ' + plat) and pat in (k.split('/')[0] + '/css/' + k.rsplit(' ', 1)[0])]
    if len(m) != 1: bad.append(f'W {w!r} matches {len(m)}')
if len(W) != len(set(W)): bad.append('W duplicate lines')
# G
nG = 0
for ln, L in E['lanes'].items():
    for p in L['predictions']:
        if p.get('gating'):
            nG += 1
            if p.get('floor') != 0.95 or not p['to'].startswith('P'): bad.append(f'G {ln} {p["cell"]} floor={p.get("floor")} to={p["to"][:20]}')
print(f'F {nF} rows · P {len(C)} cells = carriers {len(car)} + named {len(named)} + complement {len(comp)} + ring {len(ring)} · W {len(W)} lines · G {nG} gating rows')
for b in bad: print('FINDING', b)
print('HOLDS' if not bad else f'RED ({len(bad)})'); sys.exit(1 if bad else 0)
