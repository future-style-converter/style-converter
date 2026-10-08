#!/usr/bin/env python3
# skeptic/census-ir.py — the L1 SKEPTIC's own blast-radius census, read-only over a run's per-test IR
# (tools/titan/runs/<run>/sections/*/per-test-ir/*.json) and manifests. Independent of the lane's
# census.mjs: it never reads fixtures/wpt or the frozen bake fixtures; bake roots are found by the
# WIRE SIGNATURE rootProperties() leaves (Direction LTR + UnicodeBidi NORMAL + TextAlign LEFT +
# BoxSizing BORDER_BOX + Width + Height) with >= 1 baked-run descendant (Position ABSOLUTE +
# WhiteSpace + Color + FontFamily, no meta.sourceTag), then cross-checked against the manifests'
# `bidiBaked` flags. Usage: census-ir.py <run> [--json out.json]
import json, os, sys, glob
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..', '..'))
RUN = sys.argv[1]
SEC = os.path.join(ROOT, 'tools', 'titan', 'runs', RUN, 'sections')
# UA stylesheet padding (html.css) on these tags: a root with no authored Padding* would still be zeroed by P.
UA_PADDED = {'ol', 'ul', 'menu', 'dir', 'fieldset', 'td', 'th', 'dialog', 'legend', 'textarea', 'select', 'button', 'input'}
# Non-inherited paint effects (plus visibility) that an item's / intermediate's subtree carries and a ROOT-owned run escapes.
ESCAPED = {'Opacity', 'Visibility', 'Transform', 'Filter', 'ClipPath', 'MaskImage', 'Mask', 'MixBlendMode', 'ZIndex',
           'OverflowX', 'OverflowY', 'Overflow', 'ContentVisibility', 'Isolation', 'BackdropFilter', 'Rotate', 'Scale', 'Translate'}
def props(c): return {p['type']: p.get('data') for p in c.get('properties', [])}
def nonzero(v):
    # A Padding* datum: {px: n} or {original: {v, u}} (em/%, unresolved on the wire).
    if not isinstance(v, dict): return False
    if v.get('px') not in (None, 0, 0.0): return True
    o = v.get('original')
    return isinstance(o, dict) and o.get('v') not in (None, 0, 0.0)
def is_run(c, p):
    return p.get('Position') == 'ABSOLUTE' and 'WhiteSpace' in p and 'Color' in p and 'FontFamily' in p and not (c.get('meta') or {}).get('sourceTag')
def is_root_sig(p):
    return p.get('Direction') == 'LTR' and p.get('UnicodeBidi') == 'NORMAL' and p.get('TextAlign') == 'LEFT' \
        and p.get('BoxSizing') == 'BORDER_BOX' and 'Width' in p and 'Height' in p
baked = {}
for mf in glob.glob(os.path.join(SEC, '*', 'manifest.json')):
    for rel, r in json.load(open(mf))['wpt']['results'].items():
        if r.get('bidiBaked'): baked[rel] = r
out = {'run': RUN, 'docs': {}, 'bakedManifest': sorted(baked)}
ndocs = 0
for f in sorted(glob.glob(os.path.join(SEC, '*', 'per-test-ir', '*.json'))):
    ndocs += 1
    comps = json.load(open(f))['components']
    byid = {c['id']: c for c in comps}
    kids = {}
    for c in comps:
        par = (c.get('slot') or {}).get('parent')
        if par: kids.setdefault(par, []).append(c['id'])
    P = {i: props(c) for i, c in byid.items()}
    def anc(i):
        a = []; par = (byid[i].get('slot') or {}).get('parent')
        while par in byid: a.append(par); par = (byid[par].get('slot') or {}).get('parent')
        return a
    runs = [i for i, c in byid.items() if is_run(c, P[i])]
    roots = set()
    for r in runs:
        # The OUTERMOST ancestor carrying the root signature (roots never nest; the bake retires direction there).
        sig = [a for a in anc(r) if is_root_sig(P[a])]
        if sig: roots.add(sig[-1])
    if not roots: continue
    doc = os.path.basename(f)[:-5]
    d = {'section': f.split(os.sep)[-3], 'roots': []}
    for rt in sorted(roots):
        p = P[rt]; tag = (byid[rt].get('meta') or {}).get('sourceTag')
        pads = {k: p[k] for k in ('PaddingTop', 'PaddingRight', 'PaddingBottom', 'PaddingLeft') if k in p}
        sub = [i for i in byid if rt in anc(i)]
        items = []
        for i in sub:
            m = byid[i].get('meta') or {}
            if m.get('sourceTag') == 'li' or P[i].get('Display') in ('LIST_ITEM', 'list-item'):
                chain = [i] + [a for a in anc(i) if a != rt and rt in anc(a)]   # the item + intermediates below the root
                esc = sorted({k for a in chain for k in P[a] if k in ESCAPED})
                items.append({'id': i, 'markerText': m.get('markerText'), 'position': P[i].get('Position'),
                              'listStyleType': P[i].get('ListStyleType'), 'directChild': (byid[i].get('slot') or {}).get('parent') == rt,
                              'escapedEffects': esc, 'ownRuns': sum(1 for k in kids.get(i, []) if k in runs)})
        d['roots'].append({'id': rt, 'tag': tag, 'position': p.get('Position'), 'padding': pads,
                           'nonZeroPadding': any(nonzero(v) for v in pads.values()),
                           'uaPaddedNoAuthored': tag in UA_PADDED and not pads,
                           'guards': {k: p[k] for k in ('BackgroundClip', 'BackgroundOrigin', 'OverflowX', 'OverflowY', 'Overflow') if k in p},
                           'listItemRoot': tag == 'li', 'items': items,
                           'directRuns': sum(1 for k in kids.get(rt, []) if k in runs)})
    # Compose stack shape: a non-RELATIVE out-of-flow parent with >= 2 out-of-flow children.
    oof = lambda i: P[i].get('Position') in ('ABSOLUTE', 'FIXED', 'STICKY')
    d['stackShape'] = [i for i in byid if oof(i) and sum(1 for k in kids.get(i, []) if oof(k)) >= 2]
    out['docs'][doc] = d
docs = out['docs']
print(f"run {RUN}: {ndocs} per-test IR docs; signature-found baked docs {len(docs)}; manifest bidiBaked {len(baked)}")
# Cross-check the signature against the manifest flags (stems: wpt__<section>__<path with / → __>).
stem = lambda rel: 'wpt__' + rel[len('css/'):].replace('/', '__')[:-5] if rel.startswith('css/') else rel
man = {stem(r) for r in baked}
print('  in manifest not by signature:', sorted(man - set(docs)) or 'none')
print('  by signature not in manifest:', sorted(set(docs) - man) or 'none')
P_car = [(doc, r['id'], r['padding']) for doc, d in docs.items() for r in d['roots'] if r['nonZeroPadding']]
print(f"P carriers (non-zero Padding* on a bake root): {len(P_car)} roots in {len({x[0] for x in P_car})} docs")
for x in P_car: print('   ', x[0], x[1], json.dumps(x[2]))
ua = [(doc, r['id'], r['tag']) for doc, d in docs.items() for r in d['roots'] if r['uaPaddedNoAuthored']]
print(f"UA-padded roots with no authored Padding* (P would ADD a padding key): {len(ua)}", ua or '')
lir = [(doc, r['id'], r['padding']) for doc, d in docs.items() for r in d['roots'] if r['listItemRoot'] and r['nonZeroPadding']]
print(f"list-item roots with non-zero padding (P would move their native marker): {len(lir)}", lir or '')
zero = [(doc, r['id']) for doc, d in docs.items() for r in d['roots'] if r['padding'] and not r['nonZeroPadding']]
print(f"zero-padding roots (declared Padding*, all zero): {len(zero)} in {sorted({z[0] for z in zero})}")
guard = [(doc, r['id'], r['guards']) for doc, d in docs.items() for r in d['roots'] if r['guards']]
print(f"roots carrying a guard property on the wire: {len(guard)}", guard or '')
nonrel = [(doc, r['id'], r['position']) for doc, d in docs.items() for r in d['roots'] if r['position'] != 'RELATIVE']
print(f"roots NOT RELATIVE: {len(nonrel)}", nonrel or '')
M = [(doc, r['id'], it) for doc, d in docs.items() for r in d['roots'] for it in r['items']]
print(f"M' candidates (list items strictly inside a bake root): {len(M)}")
for doc, rt, it in M: print('   ', doc, 'root', rt, json.dumps(it, ensure_ascii=False))
print(f"  of which NOT a direct child of its root: {sum(1 for _, _, it in M if not it['directChild'])}; carrying an escaped paint effect: {sum(1 for _, _, it in M if it['escapedEffects'])}")
mt = [(doc, i) for doc, d in docs.items() for r in d['roots'] for i in [it['id'] for it in r['items'] if it['markerText'] and it['position'] in ('ABSOLUTE', 'FIXED')]]
print(f"plan carrierRule M' (meta.markerText + Position ABSOLUTE|FIXED): {len(mt)}", mt)
ss = {doc: d['stackShape'] for doc, d in docs.items() if d['stackShape']}
print(f"stack-shape parents inside baked docs: {sum(len(v) for v in ss.values())}", json.dumps(ss))
if '--json' in sys.argv: json.dump(out, open(sys.argv[sys.argv.index('--json') + 1], 'w'), ensure_ascii=False, indent=1)
