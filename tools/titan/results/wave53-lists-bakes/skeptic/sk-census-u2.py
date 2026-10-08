#!/usr/bin/env python3
# tools/titan/results/wave53-lists-bakes/skeptic/sk-census-u2.py — the L1 SKEPTIC's own Unit-2 reach census (not the
# lane's l1-census.py). Input: the 17 frozen `_wpt.bidiBaked` fixtures (tools/titan/results/wave53-plan/
# bidi-baked-fixtures/), cross-checked against the wave52-ship extract logs' `[bidi-bake: baked …]` list. Per doc:
#  M — every NON-root component inside a bake root that can generate a ::marker (tag li/summary, or an authored display
#      containing list-item), with its list-style-type, and every such component that is itself a root (never planned);
#  P — every root's tag, its authored padding* keys, and whether a UA stylesheet padding applies to that tag (HTML §15.3).
import json, glob, os, re, sys
R = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..', '..'))
FROZEN = os.path.join(R, 'tools/titan/results/wave53-plan/bidi-baked-fixtures')
UA_PAD = {'ol', 'ul', 'menu', 'dir', 'td', 'th', 'fieldset', 'legend', 'button', 'input', 'select', 'textarea', 'dialog',
          'details', 'summary', 'marquee', 'optgroup', 'option', 'pre'}  # pre: none in Chromium; listed to be visible
def comps(m, anc=()):
    for k, c in (m or {}).items():
        yield k, c, anc
        yield from comps(c.get('children'), anc + (k,))
def nonzero(v):
    return any(re.fullmatch(r'-?0(\.0+)?(px|em|rem|ch|%)?', t) is None for t in str(v).split())
logged = set()
for f in glob.glob(os.path.join(R, 'tools/titan/runs/wave52-ship/sections/*/extract.log')):
    for line in open(f, encoding='utf-8'):
        m = re.match(r'^extracted (\S+) .*\[bidi-bake: baked', line)
        if m: logged.add(m.group(1))
files = sorted(glob.glob(os.path.join(FROZEN, '*/*.json')))
print(f'frozen fixtures {len(files)}; wave52-ship extract logs baked {len(logged)}')
tot_m, tot_p_keys, tot_p_nz, tot_ua = 0, 0, [], []
for p in files:
    d = json.load(open(p, encoding='utf-8'))
    rel = os.path.relpath(p, FROZEN)
    allc = list(comps(d['components']))
    roots = {k for k, c, _ in allc if 'baked-bidi-visual-order' in (c.get('_lossyReasons') or [])}
    mk, rootli = [], []
    for k, c, anc in allc:
        props = c.get('properties') or {}
        li = c.get('_tag') in ('li', 'summary') or 'list-item' in str(props.get('display', ''))
        if not li: continue
        if k in roots: rootli.append(k); continue
        if any(a in roots for a in anc):
            mk.append((k, c.get('_tag'), props.get('list-style-type'), props.get('display'), props.get('list-style-position'), props.get('position')))
    tot_m += len(mk)
    for k, c, _ in allc:
        if k not in roots: continue
        props = c.get('properties') or {}
        pk = {kk: vv for kk, vv in props.items() if kk.startswith('padding')}
        if pk: tot_p_keys += 1
        nz = any(nonzero(v) for v in pk.values())
        ua = c.get('_tag') in UA_PAD
        if nz: tot_p_nz.append((rel, k, c.get('_tag'), pk))
        if ua: tot_ua.append((rel, k, c.get('_tag'), pk))
    print(f'{rel}: roots {len(roots)} (tags {sorted({c.get("_tag") for k, c, _ in allc if k in roots}, key=str)}); '
          f'marker-capable non-root in scope {len(mk)}; list-item roots {len(rootli)}')
    for x in mk: print('    M', x)
print(f'\nM: marker-capable non-root components in bake scope: {tot_m}')
print(f'P: roots with any padding* key: {tot_p_keys}; NON-ZERO authored: {len(tot_p_nz)} in {len({r for r, *_ in tot_p_nz})} docs')
for x in tot_p_nz: print('    P', x)
print(f'P: roots whose TAG has a UA padding: {len(tot_ua)}')
for x in tot_ua: print('    UA', x)
