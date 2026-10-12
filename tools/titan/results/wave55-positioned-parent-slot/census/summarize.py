#!/usr/bin/env python3
# census/summarize.py <census.tsv> — the lane census report: totals, per-document classes with the wave54-final cells,
# the carrier sets it derives, and the cross-check against expectations.json lanes["L3-positioned-parent-slot"].
# Read-only; run from anywhere (paths are resolved from this file). Exit 1 when the census GROWS past expectations.
import sys, json, collections, os
HERE = os.path.dirname(os.path.abspath(__file__))
PLAN = os.path.normpath(os.path.join(HERE, '..', '..', 'wave55-plan'))
rows = [l.rstrip('\n').split('\t') for l in open(sys.argv[1]) if not l.startswith('#')]
head = [l for l in open(sys.argv[1]) if l.startswith('#docs')][0].split('\t')
exp = json.load(open(os.path.join(PLAN, 'expectations.json')))['lanes']['L3-positioned-parent-slot']
cells = json.load(open(os.path.join(PLAN, 'cells-wave54-final.json')))['cells']
def stem(doc): return doc.split('/', 1)[1]
def cell(doc, plat):
    sec, st = doc.split('/', 1); t = st.replace('wpt__' + sec + '__', '').replace('__', '/') + '.html'
    return cells.get(f'{sec}/{t} {plat}', '—')
par = collections.defaultdict(list)
for r in rows: par[(r[0], r[2])].append(r)
docs = lambda ks: len({d for d, _ in ks})
print(f'census over {head[1]} per-test IR documents (runtime predicates, L3CensusRunTest.kt); host INACTIVE on {head[3].strip()}')
print(f'Box-branch parents with an applying child (unaffected): {head[5]}')
act = [k for k, v in par.items() if v[0][1] == 'ACTIVE']; ina = [k for k, v in par.items() if v[0][1] == 'inactive']
print(f'Column-loop parents (PositionedParentFlowSlot.applies to ≥1 child): {len(par)} in {docs(par)} docs')
print(f'  host ACTIVE (mounted today): {len(act)} parents / {docs(act)} docs (≥2 out-of-flow children: {sum(len(par[k]) >= 2 for k in act)})')
print(f'  host INACTIVE: {len(ina)} parents / {docs(ina)} docs / {sum(len(par[k]) for k in ina)} children (≥2: {sum(len(par[k]) >= 2 for k in ina)} parents in {docs([k for k in ina if len(par[k]) >= 2])} docs)')
rtl = [k for k, v in par.items() if v[0][4] == 'RTL']
print(f'  laid out RTL: {len(rtl)} parents / {docs(rtl)} docs / {sum(len(par[k]) for k in rtl)} children ({sum(par[k][0][1] == "inactive" for k in rtl)} host-inactive)')
u1 = [r for r in rows if r[6] != r[7]]; u2 = [r for r in rows if r[7] != r[8]]
print(f'U1 reach (today → U1 decision differs): {len(u1)} children / {len({(r[0], r[2]) for r in u1})} parents / {len({r[0] for r in u1})} docs')
print(f'U2 reach (U1 → U1+U2 differs): {len(u2)} children / {len({(r[0], r[2]) for r in u2})} parents / {len({r[0] for r in u2})} docs')
print()
print('== per document (changed pairs only): classes · android / ios / web cells at wave54-final ==')
bydoc = collections.defaultdict(list)
for r in u1 + [r for r in u2 if r not in u1]: bydoc[r[0]].append(r)
mover1, ctrl1, mover2 = set(), set(), set()
for d, rs in sorted(bydoc.items()):
    cls = collections.Counter(c for r in rs for c in r[9].split(','))
    if any(c.startswith('STACK') for c in cls): mover1.add(stem(d))
    elif any(r[6] != r[7] for r in rs): ctrl1.add(stem(d))
    if any(r[7] != r[8] for r in rs): mover2.add(stem(d))
    print(f'  {d} [{rs[0][1]}] {dict(cls)} | android {cell(d, "android")} · ios {cell(d, "ios")} · web {cell(d, "web")}')
print()
want1 = set(exp['revertUnits']['U1']['captures']['android']); want2 = set(exp['revertUnits']['U2']['captures']['android'])
union = set(exp['captureCarriers']['android'])
print('== carriers (android captures; web / ios / wire: none — no web, iOS, converter or extractor file changes) ==')
print(f'  U1 carriers (STACK class): {len(mover1)} {sorted(mover1)}')
print(f'  U1 controls (reached, identity / CLAMP / AUTO only): {len(ctrl1)} {sorted(ctrl1)}')
print(f'  U2 carriers (RTL origin): {len(mover2)} {sorted(mover2)}')
print(f'  union: {len(mover1 | mover2)}')
print()
print('== cross-check against expectations.json lanes["L3-positioned-parent-slot"] ==')
grew = False
for name, mine, theirs in (('U1', mover1, want1), ('U2', mover2, want2), ('union', mover1 | mover2, union)):
    extra, missing = sorted(mine - theirs), sorted(theirs - mine)
    grew |= bool(extra)
    print(f'  {name}: census {len(mine)} vs expectations {len(theirs)} — extra {extra or "none"} · missing {missing or "none"}')
print(f'  wireCarriers: expectations {exp["wireCarriers"]} — the lane changes no converter / extractor byte')
print('CENSUS ' + ('GREW — a plan regeneration need (report, never hide)' if grew else 'IDENTICAL to expectations (no growth)'))
sys.exit(1 if grew else 0)
