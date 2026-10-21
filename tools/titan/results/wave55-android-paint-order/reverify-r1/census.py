#!/usr/bin/env python3
# reverify-r1/census.py <base.tsv> <fix.tsv> <skeptic-builder.tsv> — the L6 RE-VERIFIER's re-run of skeptic D1's check on
# the lane's CURRENT patches (fix-r1). Rows: ReverifyL6CensusTest (= the skeptic's SkepticL6CensusTest + a `clip`
# column, StyleApplier's config.overflow.shouldClip) over the 1435 verbatim wave54-final per-test IR documents, on the
# base tree 611c9f93 and on the full U1+U2+U3 state; the builder tree is the skeptic's own TSV (skeptic-census/).
# Mover (behavioural, from the patched StyleApplier): a banded component on the self-paint route (U1), or on the
# legacy route whose box does not clip (U2 after fix-r1). Exposure: the SKEPTIC's predicate, byte-for-byte, nothing
# subtracted. Plan set: expectations.json at HEAD 611c9f93 (git show), L6 carriers ∪ mayMove ∪ sharedStems ∪ ring.
import csv, json, os, subprocess, sys
from collections import defaultdict
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..', '..'))
E = json.loads(subprocess.check_output(['git', '-C', ROOT, 'show', '611c9f93:tools/titan/results/wave55-plan/expectations.json']))
L = E['lanes']['L6-android-paint-order']
def load(p): return {(r['stem'], r['id']): r for r in csv.DictReader(open(p), delimiter='\t')}
base, fix, bld = (load(p) for p in sys.argv[1:4])
assert base.keys() == fix.keys() == bld.keys(), 'row sets differ'
REPLACED = {'img', 'input', 'button', 'select', 'textarea', 'video', 'canvas', 'svg', 'iframe', 'object', 'embed',
            'progress', 'meter', 'hr', 'fieldset', 'legend', 'details', 'summary', 'audio', 'picture'}
def exposure(r):  # the skeptic's skeptic-census.py exposure(), unchanged
    out = []
    if int(r['kidsAll']) > 0: out.append('child' if int(r['kidsNonBr']) > 0 else 'br-only-child')
    if r['text'] == 'true': out.append('text')
    if r['runs'] == 'true': out.append('runs')
    if r['pseudos'] == 'true': out.append('pseudo')
    if r['marker'] == 'true': out.append('marker')
    if r['borderImage'] == 'true': out.append('border-image')
    if r['tag'].lower() in REPLACED: out.append('tag:' + r['tag'].lower())
    try:
        if float(r['opacity']) < 1: out.append('opacity<1')
    except ValueError: out.append('opacity?')
    if r['outline'] == 'true' and float(r['outlineOffset']) < 0: out.append('outline<0')
    extra = set(r['metaKeys'].split(',')) - {'', 'role', 'sourceTag', 'runs', 'markerText'}
    if extra: out.append('meta:' + '|'.join(sorted(extra)))
    return out
P = print
P(f'components {len(fix)} · documents {len({k[0] for k in fix})} · ERR rows base/fix '
  f'{sum(r["band"] == "ERR" for r in base.values())}/{sum(r["band"] == "ERR" for r in fix.values())}')
band = {k: r for k, r in fix.items() if r['band'] == 'true'}
u1 = {k for k, r in band.items() if r['self'] == 'true'}
leg = {k for k in band if k not in u1}
clipLeg = {k for k in leg if fix[k]['clip'] == 'true'}
P(f'banded {len(band)}: U1 self-paint {len(u1)} (clipping among them, must be 0: {sum(fix[k]["clip"] == "true" for k in u1)}) · '
  f'U2 legacy {len(leg)} of which CLIPPING {len(clipLeg)} in {len({k[0] for k in clipLeg})} documents')
# Invariants of the fix, read on the real chains:
P(f'[I1] clipping legacy chains == base chains (must be {len(clipLeg)}): {sum(fix[k]["sig"] == base[k]["sig"] for k in clipLeg)}')
P(f'[I2] clipping legacy chains with BehindContentBorderElement (must be 0): {sum("BehindContentBorderElement" in fix[k]["sig"] for k in clipLeg)}')
nonclipFast = [k for k in leg - clipLeg if fix[k]['fast'] == 'true']
P(f'[I3] non-clipping legacy FAST rows carrying BehindContentBorderElement (must be all {len(nonclipFast)}): '
  f'{sum("BehindContentBorderElement" in fix[k]["sig"] for k in nonclipFast)}')
P(f'[I4] unbanded rows whose chain changed vs base (must be 0): {sum(fix[k]["sig"] != base[k]["sig"] for k in fix if k not in band)}')
P(f'[I5] builder→fix chain changes outside the clipping legacy rows (must be 0): '
  f'{sum(fix[k]["sig"] != bld[k]["sig"] for k in fix if k not in clipLeg)} · inside them: {sum(fix[k]["sig"] != bld[k]["sig"] for k in clipLeg)}')
movers = u1 | (leg - clipLeg)
def exposed(mov):
    out = defaultdict(list)
    for k in mov:
        e = exposure(fix[k])
        if e and e != ['br-only-child']: out[k[0]].append((k[1], '+'.join(e), 'U1' if k in u1 else 'U2'))
    return out
exB, exF = exposed(set(band)), exposed(movers)
car = set(L['captureCarriers']['android']); may = set(L['mayMove']['android']); shared = set(L['sharedStems'])
ring = set(E['ringFenced']['captures']['android']); union = set(E['unionCaptureCarriers']['android'])
covered = car | may | shared | ring
def stem(cell):  # "sec/a/b.html android" -> wpt__sec__a__b
    p = cell.rsplit(' ', 1)[0]; return 'wpt__' + p[:-5].replace('/', '__') if p.endswith('.html') else None
scoredA = {stem(c) for c in E['mustNotMoveComplement']['cells'] if c.endswith(' android')}
for ln in E['lanes'].values():
    scoredA |= {stem(c) for c in ln.get('mustNotMove', []) if c.endswith(' android')}
scoredA |= car | ring
P(f'\nplan set (HEAD 611c9f93): carriers {len(car)} · mayMove {len(may)} · sharedStems {len(shared)} · ring {len(ring)}')
for name, ex in (('builder movers (every band; the skeptic run)', exB), ('fix-r1 movers (U1 + non-clipping U2)', exF)):
    g = sorted(set(ex) - covered)
    P(f'{name}: exposed documents {len(ex)} · inside the plan set {len(set(ex) & covered)} · GROWTH {len(g)}')
    for s in g:
        P(f'   {s} [{"another lane carrier" if s in union else "nobody"}; android cell {"SCORED" if s in scoredA else "unscored"}] '
          + '; '.join(f'{i.rsplit("__", 1)[-1]} {e} {u}' for i, e, u in ex[s][:4]))
for s in ('wpt__css-cascade__revert-layer-015', 'wpt__css-pseudo__first-line-and-placeholder'):
    rows = [(k[1], fix[k]) for k in band if k[0] == s]
    for i, r in rows:
        P(f'D1 row {i}: tag {r["tag"]} clip {r["clip"]} self {r["self"]} fast {r["fast"]} · mover {(s, i) in movers} · '
          f'chain == base {r["sig"] == base[(s, i)]["sig"]} · chain == builder {r["sig"] == bld[(s, i)]["sig"]}')
    P(f'D1 {s}: exposed under fix {s in exF} · in plan set {s in covered} · scored android {s in scoredA}')
gone = sorted(set(exB) - set(exF))
P(f'\ndocuments exposed under the builder, not under fix-r1: {len(gone)}')
for s in gone:
    tag = 'carrier' if s in car else 'mayMove' if s in may else 'shared' if s in shared else 'ring' if s in ring else 'OUTSIDE'
    P(f'   {s} [{tag}]')
withdrawnDocs = {k[0] for k in clipLeg}
geoStems = set()
for gp in L['geometryProbes']:
    for key in gp.get('expect', {}):
        geoStems.add(key.split('] ', 1)[1].rsplit(' ', 1)[0])
P(f'withdrawn (clipping) band documents ∩ L6 capture carriers: {sorted(withdrawnDocs & car)}')
P(f'withdrawn band documents whose basename is a geometry-key test: {sorted(s for s in withdrawnDocs if s.rsplit("__", 1)[-1] in geoStems)}')
P(f'withdrawn band documents ∩ prediction cells: {sorted(s for s in withdrawnDocs if any(stem(p["cell"]) == s for p in L["predictions"] if isinstance(p, dict) and p.get("cell", "").endswith(".html android")))}')
for u, unit in L['revertUnits'].items():
    caps = set(unit['captures']['android'])
    miss = sorted(c for c in caps if not any(x[2] == u for x in exF.get(c, [])))
    P(f'revertUnit {u}: {len(caps)} android captures; each keeps an exposed mover on its route under fix-r1: {not miss} {miss}')
