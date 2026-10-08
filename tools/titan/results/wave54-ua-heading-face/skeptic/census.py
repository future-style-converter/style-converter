#!/usr/bin/env python3
# Skeptic (wave 54 L5) — OWN blast-radius census of the UA element-font rule, written from the
# rule's contract (UAElementFontRule.kt / .swift) and the IR v2 wire (slot.parent trees), NOT from
# the lane's census harness. Walks every per-test IR document of a run, and for every component
# whose meta.sourceTag is h1..h6 / sub / sup computes: own FontSize/Font/FontWeight, the inherited
# px size and first font family (replaying the rule down the ancestor chain), whether each half
# fires (pre-gate), whether it has children / runs (→ the fold gate decides), and whether it is a
# runs MEMBER of a parent runs host (→ consumed by the fold on Compose if that host folds).
import json, glob, os, sys, collections
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), *(['..'] * 5)))
run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
MULT = {'h1': 2.0, 'h2': 1.5, 'h3': 1.17, 'h4': 1.0, 'h5': .83, 'h6': .67, 'sub': 1 / 1.2, 'sup': 1 / 1.2}
HEAD = {'h1', 'h2', 'h3', 'h4', 'h5', 'h6'}
INHERIT = {'FontSize', 'FontFamily', 'FontWeight', 'Color'}  # the subset this census needs
def first(props, t):
    return next((p for p in props if p['type'] == t), None)
def px(p):
    d = p and p.get('data')
    return d.get('px') if isinstance(d, dict) else None
def fam(p):
    d = p and p.get('data')
    if isinstance(d, list): return (d[0] if d else None)
    if isinstance(d, dict):
        for k in ('families', 'value', 'names'):
            v = d.get(k)
            if isinstance(v, list) and v: return v[0] if isinstance(v[0], str) else json.dumps(v[0])
    return json.dumps(d)[:40] if d is not None else None
rows, docs_with_tags = [], collections.Counter()
for f in sorted(glob.glob(os.path.join(ROOT, 'tools/titan/runs', run, 'sections/*/per-test-ir/*.json'))):
    stem = os.path.basename(f)[:-5]
    comps = json.load(open(f))['components']
    byid = {c['id']: c for c in comps}
    byname = {c.get('name'): c for c in comps}
    kids = collections.defaultdict(list)
    for c in comps:
        par = (c.get('slot') or {}).get('parent')
        if par: kids[par].append(c)
    # effective inherited list per component, replaying the rule top-down (roots have no parent)
    memo = {}
    def eff(c):
        if c['id'] in memo: return memo[c['id']]
        par = (c.get('slot') or {}).get('parent')
        inh = [p for p in eff(byid[par]) if p['type'] in INHERIT] if par in byid else []
        own = c.get('properties') or []
        merged = own + [p for p in inh if not any(o['type'] == p['type'] for o in own)]
        tag = ((c.get('meta') or {}).get('sourceTag') or '').lower()
        out = list(merged)
        if tag in MULT and not any(p['type'] in ('FontSize', 'Font') for p in own):
            base = px(first(merged, 'FontSize'))
            if base is None:
                ff = fam(first(merged, 'FontFamily')) or ''
                base = 13.0 if str(ff).strip('"\'').lower() in ('monospace', 'ui-monospace') else 16.0
            out = [p for p in out if p['type'] != 'FontSize'] + [{'type': 'FontSize', 'data': {'px': MULT[tag] * base}}]
        if tag in HEAD and not any(p['type'] in ('FontWeight', 'Font') for p in own):
            out = [p for p in out if p['type'] != 'FontWeight'] + [{'type': 'FontWeight', 'data': {'weight': 700}}]
        memo[c['id']] = out
        return out
    for c in comps:
        meta = c.get('meta') or {}
        tag = (meta.get('sourceTag') or '').lower()
        if tag not in MULT: continue
        docs_with_tags[stem] += 1
        own = c.get('properties') or []
        par = (c.get('slot') or {}).get('parent')
        parent = byid.get(par)
        member_of = None
        if parent is not None and any(r.get('child') == c.get('name') for r in ((parent.get('meta') or {}).get('runs') or [])):
            member_of = parent
        # ancestor runs-host chain (nested members are folded by the outermost folding host)
        anc_hosts, a = [], parent
        while a is not None:
            if (a.get('meta') or {}).get('runs'): anc_hosts.append(((a.get('meta') or {}).get('sourceTag') or '-') + ':' + a['id'])
            a = byid.get((a.get('slot') or {}).get('parent'))
        e = eff(c)
        rows.append(dict(
            stem=stem, id=c['id'], tag=tag,
            ownSize=bool(first(own, 'FontSize') or first(own, 'Font')),
            ownWeight=bool(first(own, 'FontWeight') or first(own, 'Font')),
            sizeFires=not bool(first(own, 'FontSize') or first(own, 'Font')),
            weightFires=(tag in HEAD) and not bool(first(own, 'FontWeight') or first(own, 'Font')),
            postSize=px(first(e, 'FontSize')),
            children=len(kids[c['id']]), runs=bool(meta.get('runs')),
            memberOfRunsHost=(member_of['id'] if member_of else None),
            ancestorRunsHosts=anc_hosts,
            buckets=bool(c.get('selectors') or c.get('media')),
            hasAll=bool(first(own, 'All')),
        ))
print(f'# run={run} documents={len(glob.glob(os.path.join(ROOT, "tools/titan/runs", run, "sections/*/per-test-ir/*.json")))} tagged-components={len(rows)} documents-with-tags={len(docs_with_tags)}')
fires = [r for r in rows if (r['tag'] in HEAD and (r['sizeFires'] or r['weightFires'])) or (r['tag'] not in HEAD and r['sizeFires'])]
print(f'# firing (pre-gate) components={len(fires)} in documents={len(set(r["stem"] for r in fires))}')
for r in rows:
    f = 'FIRES' if r in fires else 'idle '
    print(f"{f} {r['stem']} {r['id']} {r['tag']} ownSize={int(r['ownSize'])} ownWeight={int(r['ownWeight'])} post={r['postSize'] if r['postSize'] is None else round(r['postSize'],3)} "
          f"children={r['children']} runs={int(r['runs'])} member={r['memberOfRunsHost']} ancHosts={r['ancestorRunsHosts']} buckets={int(r['buckets'])} all={int(r['hasAll'])}")
