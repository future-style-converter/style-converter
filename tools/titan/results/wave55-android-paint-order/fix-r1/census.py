#!/usr/bin/env python3
# fix-r1/census.py <base.tsv> <builder.tsv> <fix.tsv> (the committed copies are gzipped: gunzip -k census-*.tsv.gz first) — the L6 fix lane's blast-radius census (skeptic D1).
# Rows = L6FixCensusTest over the 1435 verbatim per-test IR documents of wave54-final, on the base tree (611c9f93), the
# builder's patches and the fix patches. A component MOVES when its folded chain signature differs from base. A moved
# banded component is EXPOSED (its band can paint differently) when anything inner of the band node can reach it — the
# skeptic's predicate, nothing subtracted: children (any, <br> included), own text, runs, pseudos, marker, border-image,
# a replaced / form / UA-widget tag, partial opacity (the band changes group), an outline with a negative offset.
# Cross-check: exposed documents vs expectations.json L6 captureCarriers ∪ mayMove ∪ sharedStems ∪ the ring-fence.
import csv, json, os, sys
from collections import Counter, defaultdict
HERE = os.path.dirname(os.path.abspath(__file__))
E = json.load(open(os.path.join(HERE, '..', '..', 'wave55-plan', 'expectations.json')))
LN = E['lanes']['L6-android-paint-order']
def load(p): return {(r['stem'], r['id']): r for r in csv.DictReader(open(p), delimiter='\t')}
base, bld, fix = (load(p) for p in sys.argv[1:4])
assert base.keys() == bld.keys() == fix.keys()
REPLACED = {'img', 'input', 'button', 'select', 'textarea', 'video', 'canvas', 'svg', 'iframe', 'object', 'embed',
            'progress', 'meter', 'hr', 'fieldset', 'legend', 'details', 'summary', 'audio', 'picture'}
WIDGET = {'input', 'button', 'select', 'textarea', 'meter', 'progress'}   # UAWidgetsResolve.kindFor's tags
def exposure(r):
    o = []
    if int(r['kidsAll']) > 0: o.append('child' if int(r['kidsNonBr']) > 0 else 'br-only-child')
    for k in ('text', 'runs', 'pseudos', 'marker'):
        if r[k] == 'true': o.append(k)
    if r['borderImage'] == 'true': o.append('border-image')
    if r['tag'].lower() in REPLACED: o.append('tag:' + r['tag'].lower())
    try:
        if float(r['opacity']) < 1: o.append('opacity<1')
        if r['outline'] == 'true' and float(r['outlineOffset']) < 0: o.append('outline<0')
    except ValueError: pass
    return o
def route(r): return 'U1' if r['self'] == 'true' else 'U2'
P = print
errs = sorted({k[0] for k, r in fix.items() if r['band'] == 'ERR'})
P(f'components {len(fix)} · documents {len({k[0] for k in fix})} · extraction errors {sum(r["band"] == "ERR" for r in fix.values())} {errs}')
band = {k: r for k, r in fix.items() if r['band'] == 'true'}
leg = {k: r for k, r in band.items() if r['self'] != 'true'}
P(f'banded {len(band)}: U1 self-paint {len(band) - len(leg)} · U2 legacy {len(leg)} '
  f'(fast {sum(r["fast"] == "true" for r in leg.values())}, slow {sum(r["fast"] != "true" for r in leg.values())})')
clipLeg = {k: r for k, r in leg.items() if r['clips'] == 'true'}
P(f'legacy bands on a CLIPPING box (overflow.shouldClip): {len(clipLeg)} in {len({k[0] for k in clipLeg})} documents')
# 1. The fix's invariant: a clipping legacy box keeps the base chain byte-for-byte (class signature).
bad = [k for k in clipLeg if fix[k]['sig'] != base[k]['sig']]
P(f'  fix: clipping legacy chains equal to base (must be all): {len(clipLeg) - len(bad)}/{len(clipLeg)} {bad[:3]}')
mvB = [k for k in clipLeg if bld[k]['sig'] != base[k]['sig']]
P(f'  builder: clipping legacy chains that MOVED (now withdrawn): {len(mvB)}')
# 2. Movers per tree. BEHAVIOURAL, not by signature: a slow-path band whose DrawWithContentElement keeps its place in
#    the class signature still flips its draw order inside the lambda (the builder's census: 98 such rows). So the
#    builder moves EVERY band (U1 self-paint + every U2 legacy band); the fix moves U1 + the non-clipping U2 bands.
sigB = {k for k in fix if bld[k]['sig'] != base[k]['sig']}
sigF = {k for k in fix if fix[k]['sig'] != base[k]['sig']}
P(f'signature changes: builder {len(sigB)} · fix {len(sigF)} · unbanded among them (must be 0): '
  f'{sum(bld[k]["band"] != "true" for k in sigB)} / {sum(fix[k]["band"] != "true" for k in sigF)}')
movB = set(band)
movF = {k for k in band if k not in clipLeg}
diffBF = {k for k in fix if bld[k]['sig'] != fix[k]['sig']}
same = [k for k in movF if fix[k]['sig'] != bld[k]['sig']]
P(f'behavioural movers: builder {len(movB)} · fix {len(movF)} (withdrawn: the {len(clipLeg)} clipping legacy bands)')
P(f'builder ≠ fix by signature: {len(diffBF)} components, all clipping legacy bands: {diffBF <= set(clipLeg)}; '
  f'fix movers whose chain differs from the builder\'s (must be 0): {len(same)}')
# 3. Exposed documents, per tree.
def exposed(mov, tree):
    out = defaultdict(list)
    for k in mov:
        e = exposure(tree[k])
        if e: out[k[0]].append((k[1], '+'.join(e), route(tree[k])))
    return out
exB, exF = exposed(movB, bld), exposed(movF, fix)
cap = set(LN['captureCarriers']['android']); may = set(LN['mayMove']['android']); shared = set(LN['sharedStems'])
ring = {'wpt__filter-effects__backdrop-filter-basic-blur'}
plan = cap | may | shared | ring
P(f'\n== exposed documents: builder {len(exB)} · fix {len(exF)} (plan set {len(plan)}: carriers {len(cap)} · mayMove {len(may)} · sharedStems {len(shared)} · ring 1)')
for name, ex in (('builder', exB), ('fix', exF)):
    g = sorted(s for s in ex if s not in plan)
    P(f'{name}: inside the plan set {len(ex) - len(g)} · GROWTH {len(g)}')
    for s in g: P(f'   {s}: ' + '; '.join(f'{i} {e} {u}' for i, e, u in ex[s][:4]))
gone = sorted(set(exB) - set(exF))
P(f'documents exposed under the builder, no longer under the fix: {len(gone)}')
for s in gone: P(f'   {s} [{"carrier" if s in cap else "mayMove" if s in may else "shared" if s in shared else "ring" if s in ring else "OUTSIDE"}]: ' + '; '.join(f'{i} {e}' for i, e, _ in exB[s][:3]))
# 4. Carriers: every revert unit's captures keep an exposed mover on that unit's route under the fix.
P('')
for u, d in LN['revertUnits'].items():
    caps = d['captures']['android']
    miss = [s for s in caps if not any(r == u for _, _, r in exF.get(s, []))]
    P(f'revertUnit {u}: {len(caps)} android captures; each keeps an exposed mover on its route: {not miss} {miss}')
# 5. UA-widget hosts with a legacy band: clip status and whether they move.
P('\n== UA-widget tags with a legacy band (UAWidgetsResolve.kindFor tags; Appearance none = devolved)')
c = Counter()
for k, r in sorted(leg.items()):
    if r['tag'].lower() not in WIDGET: continue
    moves = k in movF
    c[(r['tag'], r['attrType'], r['clips'], moves)] += 1
    if moves: P(f'   MOVES  {k[0]} {k[1]} tag={r["tag"]} type={r["attrType"]} appearance={r["appearance"]} clips={r["clips"]} expo={"+".join(exposure(r))} in-plan={k[0] in plan}')
for (t, a, cl, mv), n in sorted(c.items()): P(f'   {n:3d} × {t} type={a} clips={cl} moves={mv}')
# 6. Opacity groups among the fix's movers.
op = [k for k in movF if fix[k]['opacity'] not in ('1.0', '-') and float(fix[k]['opacity']) < 1]
P(f'\nopacity<1 movers (band into the group): {len(op)} components in {len({k[0] for k in op})} documents {sorted({k[0] for k in op})}')
