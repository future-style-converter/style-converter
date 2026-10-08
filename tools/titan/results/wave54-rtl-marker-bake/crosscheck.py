#!/usr/bin/env python3
# tools/titan/results/wave54-rtl-marker-bake/crosscheck.py — compares this lane's own census (census.<run>.json, from
# census.mjs) with the plan's pre-registration (tools/titan/results/wave54-plan/expectations.json lanes
# ["L1-rtl-marker-bake"]): capture carriers, wire carriers, per-unit carriers, the id shadow, and the must-not-move set
# re-derived here (every scored cell of a bidi-baked document that is not a capture carrier, plus the shadowed documents
# ×3), with today's verdicts from cells-<run>.json. Prints OK / MISMATCH lines; exit 1 on any mismatch.
import json, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
RUN = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
cen = json.load(open(os.path.join(HERE, f'census.{RUN}.json')))
exp = json.load(open(os.path.join(ROOT, 'tools/titan/results/wave54-plan/expectations.json')))['lanes']['L1-rtl-marker-bake']
cells = json.load(open(os.path.join(ROOT, f'tools/titan/results/wave54-plan/cells-{RUN}.json')))['cells']
irname = lambda t: 'wpt__' + t.replace('css/', '', 1).rsplit('.', 1)[0].replace('/', '__')
bad = 0
def check(name, mine, theirs):
    global bad
    ok = sorted(mine) == sorted(theirs)
    bad += 0 if ok else 1
    print(f"{'OK      ' if ok else 'MISMATCH'} {name}: census {sorted(mine)}" + ('' if ok else f" vs plan {sorted(theirs)}"))
P_docs = sorted({irname(p['test']) for p in cen['P']})
M_docs = sorted({irname(m['test']) for m in cen['Mprime']})
check('unit P wire carriers (non-zero unguarded padding roots)', P_docs, exp['revertUnits']['P']['wire'])
check('unit P android captures', P_docs, exp['revertUnits']['P']['captures']['android'])
check('unit P web/ios captures (none: abspos insets resolve against the padding box)', [], exp['revertUnits']['P']['captures']['web'] + exp['revertUnits']['P']['captures']['ios'])
check('unit M′ wire carriers', M_docs, exp['revertUnits']['Mprime']['wire'])
for pf in ('web', 'ios', 'android'):
    check(f'unit M′ {pf} captures', M_docs, exp['revertUnits']['Mprime']['captures'][pf])
    check(f'lane {pf} capture carriers', sorted(set(M_docs) | (set(P_docs) if pf == 'android' else set())), exp['captureCarriers'][pf])
check('lane wire carriers', sorted(set(P_docs) | set(M_docs)), exp['wireCarriers'])
sh = exp['revertUnits']['Mprime']['wireShadow']['css-counter-styles']
check('M′ id shadow (later tests renumbered)', [s['later'] for s in cen['shadow']], [sh['documents']])
print(f"P roots {len(cen['P'])} in {len(P_docs)} docs; M′ items {len(cen['Mprime'])} in {len(M_docs)} docs; UA-padding roots {len(cen['Pua'])}")
# Today's carrier cells.
carrier_cells = []
for d in cen['baked']:
    rel = d['test'].replace('css/', '', 1)
    for pf in ('web', 'ios', 'android'):
        if irname(d['test']) in exp['captureCarriers'][pf]:
            carrier_cells.append((f'{rel} {pf}', cells.get(f'{rel} {pf}', 'unscored')))
print('carrier cells today:', '; '.join(f'{c} {v}' for c, v in carrier_cells))
scored = [v for _, v in carrier_cells if v != 'unscored']
print(f'  scored {len(scored)}, passing {sum(v.startswith("P") for v in scored)}')
# Must-not-move, re-derived: scored cells of baked docs not carried on that platform + the 15 shadowed docs ×3.
mnm = []
for d in cen['baked']:
    rel = d['test'].replace('css/', '', 1)
    for pf in ('web', 'ios', 'android'):
        if irname(d['test']) not in exp['captureCarriers'][pf] and f'{rel} {pf}' in cells: mnm.append(f'{rel} {pf}')
lst = open(os.path.join(ROOT, f'tools/titan/runs/{RUN}/sections/css-counter-styles/tests.list')).read().split()
later = lst[lst.index('css/css-counter-styles/counter-suffix.html') + 1:]
for t in later:
    rel = t.replace('css/', '', 1)
    for pf in ('web', 'ios', 'android'):
        if f'{rel} {pf}' in cells: mnm.append(f'{rel} {pf}')
plan_mnm = set(exp['mustNotMove'])
mine = set(mnm)
print(f'must-not-move re-derived: {len(mine)} scored cells ({sum(cells[c].startswith("P") for c in mine)} P today); plan lists {len(plan_mnm)}')
only_mine, only_plan = sorted(mine - plan_mnm), sorted(plan_mnm - mine)
print('  in census, not in plan:', only_mine or '—')
print('  in plan, not in census:', only_plan or '—')
if only_mine: bad += 1
sys.exit(1 if bad else 0)
