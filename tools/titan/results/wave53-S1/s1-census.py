#!/usr/bin/env python3
# S1 combined-tree skeptic census (wave 53): re-derive each lane's carrier set from the
# wave53-open per-test IR by RULE (my own reading of the PLAN rules), then compare with
# expectations.json lanes.<lane>.captureCarriers / wireCarriers.
import json, glob, os, re, sys
ROOT = sys.argv[1]
RUN = f'{ROOT}/tools/titan/runs/wave53-open/sections'
E = json.load(open(f'{ROOT}/tools/titan/results/wave53-plan/expectations.json'))
docs = {}
for f in sorted(glob.glob(f'{RUN}/*/per-test-ir/*.json')):
    docs[os.path.basename(f)[:-5]] = json.load(open(f))['components']
def props(c):
    d = {}
    for p in c.get('properties', []): d[p['type']] = p['data']   # last wins, like the extractors
    return d
def kids(comps):
    by = {}
    for c in comps:
        par = (c.get('slot') or {}).get('parent'); by.setdefault(par, []).append(c)
    return by
def ancestors(c, byid):
    out = []; p = (c.get('slot') or {}).get('parent')
    while p and p in byid: out.append(byid[p]); p = (byid[p].get('slot') or {}).get('parent')
    return out
res = {}
# ---- L1 U2 hunk M: an absolutely positioned component carrying meta.markerText (the bidi-baked li)
m = set()
for s, comps in docs.items():
    for c in comps:
        pr = props(c)
        if (c.get('meta') or {}).get('markerText') and pr.get('Position') in ('ABSOLUTE', 'FIXED'): m.add(s)
res['L1-U2-M'] = m
# ---- L1 U2 hunk P: bidi-bake roots (frozen fixtures, `baked-bidi-visual-order`) with a NON-ZERO padding
FX = f'{ROOT}/tools/titan/results/wave53-plan/bidi-baked-fixtures'
pz = set(); zero_only = set(); roots = 0
def nonzero(v):
    return any(t not in ('0', '0px', '0em', '0%') for t in str(v).split())
for f in glob.glob(f'{FX}/*/*.json'):
    d = json.load(open(f)); sec = f.split('/')[-2]; stem = 'wpt__' + sec + '__' + os.path.basename(f)[:-5]
    def walk(mp):
        global roots
        for k, c in (mp or {}).items():
            if any('baked-bidi' in x for x in (c.get('_lossyReasons') or [])):
                roots += 1
                pp = {p: v for p, v in (c.get('properties') or {}).items() if p.startswith('padding')}
                clip = any('content-box' in str(v) for p, v in (c.get('properties') or {}).items() if p in ('background-clip', 'background-origin'))
                ov = any(str(v) not in ('visible',) for p, v in (c.get('properties') or {}).items() if p.startswith('overflow'))
                if pp and any(nonzero(v) for v in pp.values()) and not clip and not ov: pz.add(stem)
                elif pp: zero_only.add(stem)
            walk(c.get('children'))
    walk(d['components'])
res['L1-U2-P'] = pz
# ---- L2: a space-less string with U+00AD reaching a label (horizontal, not pre, not dictionary AUTO), and F2 inert members
SHY = '­'
l2 = set(); f2 = set()
def inh(c, byid, key):
    for a in [c] + ancestors(c, byid):
        v = props(a).get(key)
        if v is not None: return v
    return None
for s, comps in docs.items():
    byid = {c['id']: c for c in comps}; byname = {c['name']: c for c in comps}
    for c in comps:
        meta = c.get('meta') or {}
        texts = [] if meta.get('runs') else [c.get('text') or '']
        texts += [r.get('text') or '' for r in meta.get('runs') or []]
        hit = any(SHY in t and ' ' not in t for t in texts)
        wm = str(inh(c, byid, 'WritingMode') or 'HORIZONTAL_TB')
        ws = str(inh(c, byid, 'WhiteSpace') or 'NORMAL')
        hy = str(inh(c, byid, 'Hyphens') or 'MANUAL')
        if hit and wm.startswith('HORIZONTAL') and ws not in ('PRE', 'NOWRAP') and hy != 'AUTO': l2.add(s)
        # F2: a runs host whose child member is abspos/fixed, transparent, childless, props ⊆ {Position, Color, Hyphens}
        for r in meta.get('runs') or []:
            ch = byname.get(r.get('child'))
            if not ch: continue
            pr = props(ch)
            col = pr.get('Color') or {}
            transparent = isinstance(col, dict) and (col.get('srgb') or {}).get('a') == 0
            nokids = not any((x.get('slot') or {}).get('parent') == ch['id'] for x in comps) and not (ch.get('meta') or {}).get('runs')
            if pr.get('Position') in ('ABSOLUTE', 'FIXED') and transparent and nokids and set(pr) <= {'Position', 'Color', 'Hyphens'}:
                f2.add(s)
res['L2-F1'] = l2; res['L2-F2'] = f2
# ---- L3: body-root with a BackgroundImage (A); body-root whose last Display is TABLE / INLINE_TABLE (B)
a3 = set(); b3 = set(); bodies = 0
for s, comps in docs.items():
    for c in comps:
        if (c.get('meta') or {}).get('role') == 'body-root':
            bodies += 1
            pr = props(c)
            if 'BackgroundImage' in pr: a3.add(s)
            if pr.get('Display') in ('TABLE', 'INLINE_TABLE'): b3.add(s)
res['L3-A'] = a3; res['L3-B'] = b3
# ---- L4: k>=1 px floats (no margin/padding/border wire), then exactly ONE in-flow BFC root, last, provable inline size
PX = lambda v: isinstance(v, dict) and 'px' in v
BOX = re.compile(r'^(Margin|Padding|Border\w*Width)')
t0 = set(); t2 = set()
for s, comps in docs.items():
    by = kids(comps)
    for par, ch in by.items():
        if par is None or len(ch) < 2: continue
        fl = [c for c in ch if props(c).get('Float') in ('LEFT', 'RIGHT')]
        k = len(fl)
        if k < 1 or ch[:k] != fl or len(ch) != k + 1: continue
        t0.add(s)
        bfc = ch[-1]; bp = props(bfc)
        isbfc = bp.get('Display') == 'FLOW_ROOT' or any(str(bp.get(o, 'VISIBLE')) not in ('VISIBLE',) for o in ('OverflowX', 'OverflowY', 'Overflow')) or bool(bp.get('Contain'))
        sides = [props(c)['Float'] for c in fl]
        runs = any(sides[i] == sides[i + 1] for i in range(len(sides) - 1))
        floats_ok = all(PX(props(c).get('Width')) and PX(props(c).get('Height')) and not any(BOX.match(t) and nonzero(json.dumps(v.get('px', 1)) if isinstance(v, dict) else v) for t, v in props(c).items()) for c in fl)
        w = bp.get('Width'); cont = bp.get('Contain') or []
        wok = PX(w) or (isinstance(w, dict) and str(w.get('type', '')).upper().replace('-', '_') in ('FIT_CONTENT', 'MIN_CONTENT', 'MAX_CONTENT') or str(w).upper().find('CONTENT') >= 0) and any(x in cont for x in ('INLINE_SIZE', 'SIZE', 'STRICT'))
        noclear = not any('Clear' in props(c) for c in ch)
        if isbfc and not runs and floats_ok and wok and noclear and 'Float' not in bp: t2.add(s)
res['L4-T0'] = t0; res['L4'] = t2
# ---- compare with expectations.json
def plat(l): return {p: set(v) for p, v in E['lanes'][l]['captureCarriers'].items()}
print('docs', len(docs), '| bidi-bake roots', roots, '| body-roots', bodies)
for k, v in res.items(): print(f'{k:8} {len(v):3}  {sorted(x.replace("wpt__", "") for x in v)}')
L1 = plat('L1-lists-bakes'); print('L1 expect', {p: sorted(x.replace('wpt__', '') for x in v) for p, v in L1.items()}, 'wire', len(E['lanes']['L1-lists-bakes']['wireCarriers']))
print('L1 zero-padding bake-root docs (must stay identical):', sorted(x.replace('wpt__', '') for x in zero_only - pz))
for l in ('L2-soft-hyphen', 'L3-canvas-root', 'L4-float-avoid', 'L5-harness-hygiene'):
    print(l, {p: sorted(x.replace('wpt__', '') for x in v) for p, v in plat(l).items()}, 'wire', E['lanes'][l]['wireCarriers'])
