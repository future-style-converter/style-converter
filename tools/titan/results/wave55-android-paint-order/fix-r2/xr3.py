#!/usr/bin/env python3
# fix-r2/xr3.py <fix.tsv> <xr3.tsv> <md1.tsv> <per-test-ir sections dir> <expectations.json>
# Adjudicates mutation XR3 (`config.overflow.shouldClip` -> `config.overflow.clipsX` on StyleApplier's two legacy-branch
# lines) over every reachable payload: (1) the IR scan — every Overflow* declaration of the 1435 wave54-final per-test IR
# documents, wherever it sits; (2) Compose's own predicates per component (FixR2L6CensusTest columns clipsX / clipsY /
# clip = shouldClip) and where they differ, with the band flag; (3) the census of the XR3-mutated tree against the fix tree
# (all columns, every row); (4) the positive control MD1 on the same pipeline (the census must see a predicate edit);
# (5) the lane's pin payloads that reach the legacy branch; (6) the plan sets the divergent documents sit in.
import csv, glob, json, os, sys
fix_p, xr3_p, md1_p, secs, exp_p = sys.argv[1:6]
def load(p):
    rows = list(csv.reader(open(p), delimiter='\t')); h = rows[0]
    return h, [dict(zip(h, r)) for r in rows[1:]]
h, fix = load(fix_p); _, xr3 = load(xr3_p); _, md1 = load(md1_p)
print(f'# fix-r2/xr3.py — rows fix {len(fix)} · xr3 {len(xr3)} · md1 {len(md1)}')
# (1) IR scan: every dict whose "type" starts with Overflow, at any depth, with its JSON path class.
kinds = {}
for f in sorted(glob.glob(os.path.join(secs, '*', 'per-test-ir', '*.json'))):
    def walk(o, path):
        if isinstance(o, dict):
            t = o.get('type')
            if isinstance(t, str) and t.startswith('Overflow'):
                k = (t, path); kinds[k] = kinds.get(k, 0) + 1
            for kk, v in o.items(): walk(v, path + '/' + ('*' if kk == 'components' else kk))
        elif isinstance(o, list):
            for v in o: walk(v, path)
    walk(json.load(open(f)), '')
print('(1) IR scan, Overflow* declarations by (type, path):')
for (t, p), n in sorted(kinds.items(), key=lambda x: -x[1]): print(f'    {n:5d}  {t:20s} {p}')
# (2) Compose's own predicates.
pairs = {}
for r in fix:
    k = (r['ovX'], r['ovY'], r['clipsX'], r['clipsY'], r['clip']); pairs[k] = pairs.get(k, 0) + 1
print('(2) specified (overflowX, overflowY) → Compose clipsX / clipsY / shouldClip, components:')
for k, n in sorted(pairs.items(), key=lambda x: -x[1]): print(f'    {n:5d}  x={k[0]:8s} y={k[1]:8s} clipsX={k[2]:5s} clipsY={k[3]:5s} shouldClip={k[4]}')
div = [r for r in fix if r['clipsX'] != r['clip']]
print(f'    clipsX != shouldClip (the only rows XR3 can touch): {len(div)}; ERR rows (no config): {sum(r["clipsX"] == "-" for r in fix)}')
for r in div: print(f'      {r["stem"]} {r["id"]} tag={r["tag"]} band={r["band"]} legacyBand={r["legacyBand"]} self={r["self"]} sig={r["sig"]}')
print(f'    of them with a band: {sum(r["band"] == "true" for r in div)} · on the legacy band route: {sum(r["legacyBand"] == "true" for r in div)}')
# (3) XR3 tree vs fix tree.
d3 = [(a['id'], k) for a, b in zip(fix, xr3) for k in h if a[k] != b[k]]
print(f'(3) census XR3-mutated tree vs fix tree: rows {len(xr3)}, differing cells {len(d3)}, differing rows {len({i for i, _ in d3})}')
# (4) the positive control.
d4 = sorted({a['id'] for a, b in zip(fix, md1) if a != b})
cl = {a['id'] for a in fix if a['clip'] == 'true' and a['legacyBand'] == 'true'}
print(f'(4) control MD1 (unconditional behind-content lines) vs fix tree: differing rows {len(d4)}; all of them clipping legacy-band rows: {set(d4) <= cl} (clipping legacy-band rows {len(cl)})')
# (5) the lane's pins that reach the legacy predicate (BorderSidesBehindContentTest P3a-d / P3h / P3i, EffectsClipFilterOrderTest P4b).
pins = {'P3a flex safe-001': 'flex-abspos-staticpos-align-self-safe-001__0-023', 'P3b-d background-clip-002': 'background-clip-002__2-095',
        'P3h revert-layer-015': 'revert-layer-015__0-077', 'P3i anchor-scope-scroll': 'anchor-scope-scroll__0-781'}
for name, suf in pins.items():
    m = [r for r in fix if r['id'].endswith(suf)]
    for r in m: print(f'(5) pin {name}: {r["id"]} x={r["ovX"]} y={r["ovY"]} clipsX={r["clipsX"]} shouldClip={r["clip"]} band={r["band"]} legacyBand={r["legacyBand"]}')
    if not m: print(f'(5) pin {name}: NOT FOUND ({suf})')
p4 = [r for r in fix if 'clip-path-filter-order' in r['stem']]
print(f'(5) pin P4b clip-path-filter-order: {len(p4)} components, clipsX != shouldClip on {sum(r["clipsX"] != r["clip"] for r in p4)}, banded {sum(r["band"] == "true" for r in p4)}')
# (6) plan sets.
L6 = json.load(open(exp_p))['lanes']['L6-android-paint-order']
txt = json.dumps(L6)
for stem in sorted({r['stem'] for r in div}): print(f'(6) {stem}: named in expectations.json L6 = {stem in txt or stem.replace("wpt__", "", 1) in txt}')
