#!/usr/bin/env python3
"""Wave 55 L3 SKEPTIC census, Python half (independent of the lane's census/).

skeptic-census.py <run-dir> <host-dump.tsv>

Inputs: the verbatim per-test IR of <run-dir>/sections/*/per-test-ir/ and the
JVM half's host-dump.tsv (per document: CanvasRootHoist.hostActivates on the
raw and on the harness-rewritten roots; per (parent, out-of-flow child) pair of
the REWRITTEN tree: the runtime's Box-branch inputs and
PositionedParentFlowSlot.applies, plus the composition chain of ids).

Decisions are computed HERE from the spec'd table (PLAN §2 L3), not by calling
the lane's gate:
  before  = MOUNT iff hostActive && applies
  U1      = MOUNT iff applies && (hostActive || (composed && !parentRtl))
  U1+U2   = MOUNT_RTL_ORIGIN iff applies && parentRtl && (hostActive || composed)
            && parent declares a px Width && the child resolves exactly one of
            Left / Right to px; else the U1 row.
composed = True for every corpus document (the titan capture is composed).
parentRtl: Compose's USED direction (ComponentRenderer provides Rtl for a box
whose effective Direction is RTL and never re-provides Ltr): model A = any box
of the composition chain (root..parent) declares Direction RTL; model B = the
same chain cut at the last canvas-hoisted box (FIXED, or ABSOLUTE with no
positioned ancestor) when the host is active (the overlay is composed outside
its DOM ancestors). Both are printed; disagreements are listed.
Classes for every pair whose decision changes: STACK (an earlier sibling with
non-zero or unknown height), CLAMP (child px size beyond the parent's px
content box / remaining height), AUTO-PW / AUTO-PH (parent width / height not
px — the mount reports 0x0, so an auto parent shrinks) with PAINTS (the parent
paints a background / border / shadow / outline), END (a right/bottom-only
child: the mount anchors at the slot end), PCT (a % size or inset), MARGIN
(a non-zero child margin), AUTO-CW (width-auto child: shrink-to-fit band).
"""
import glob, json, os, sys
from collections import defaultdict, OrderedDict

run, dump = sys.argv[1], sys.argv[2]

# ---- the IR, by document stem -> {id: component}
ir = {}
for f in sorted(glob.glob(os.path.join(run, 'sections', '*', 'per-test-ir', '*.json'))):
    stem = os.path.basename(f)[:-5]
    d = json.load(open(f))
    ir[stem] = {c['id']: c for c in d['components']}

def props(c):
    return c.get('properties', []) if c else []

def last(c, t):
    v = None
    for p in props(c):
        if p['type'] == t:
            v = p['data']
    return v

def first(c, t):
    for p in props(c):
        if p['type'] == t:
            return p['data']
    return None

def px(data):
    """A px length the runtime's extractDp would read, else None."""
    if isinstance(data, dict):
        if isinstance(data.get('px'), (int, float)) and data.get('type') in (None, 'length'):
            return float(data['px'])
    if isinstance(data, (int, float)):
        return float(data)
    return None

def is_pct(data):
    return isinstance(data, dict) and (data.get('type') == 'percentage' or 'percent' in json.dumps(data).lower())

def pos(c):
    v = last(c, 'Position')
    return (v or 'STATIC').upper() if isinstance(v, str) else 'STATIC'

def declares_rtl(c):
    v = first(c, 'Direction')
    return isinstance(v, str) and v.upper() == 'RTL'

def paints(c):
    why = []
    bg = last(c, 'BackgroundColor')
    if isinstance(bg, dict) and bg.get('srgb') and (bg['srgb'].get('a', 1) or 0) > 0:
        why.append('bg')
    if last(c, 'BackgroundImage') not in (None, 'NONE', 'none', []):
        why.append('bgimg')
    for s in ('Top', 'Right', 'Bottom', 'Left'):
        w = px(last(c, f'Border{s}Width')); st = last(c, f'Border{s}Style')
        if w and w > 0 and isinstance(st, str) and st.upper() not in ('NONE', 'HIDDEN'):
            why.append('border'); break
    if last(c, 'BoxShadow') not in (None, 'NONE', 'none', []):
        why.append('shadow')
    ow = px(last(c, 'OutlineWidth')); ost = last(c, 'OutlineStyle')
    if ow and ow > 0 and isinstance(ost, str) and ost.upper() != 'NONE':
        why.append('outline')
    if c and c.get('_text'):
        why.append('text')
    return why

# ---- the JVM dump
host = {}
pairs = []
for line in open(dump):
    f = line.rstrip('\n').split('\t')
    if f[0] == 'DOC':
        host[f[2]] = (f[3] == 'true', f[4] == 'true', f[1])
    elif f[0] == 'PAIR':
        pairs.append(dict(doc=f[1], parent=f[2], idx=int(f[3]), child=f[4], ppos=f[5], cpos=f[6],
                          box=f[7] == 'true', applies=f[8] == 'true', chain=f[9].split('/')))

def chain_rtl(doc, chain, host_active, model):
    comps = ir[doc]
    ids = chain
    if model == 'B' and host_active:
        cut = 0
        positioned = False
        for i, cid in enumerate(ids):
            c = comps.get(cid)
            p = pos(c)
            if p == 'FIXED' or (p == 'ABSOLUTE' and not positioned):
                cut = i
            if p != 'STATIC':
                positioned = True
        ids = ids[cut:]
    return any(declares_rtl(comps.get(cid)) for cid in ids)

def decision(applies, host_active, rtl, origin_ok, state):
    if not applies:
        return 'IDENTITY'
    if state == 'before':
        return 'MOUNT' if host_active else 'IDENTITY'
    if state == 'U1U2' and rtl and origin_ok:
        return 'MOUNT_RTL_ORIGIN'
    return 'MOUNT' if (host_active or not rtl) else 'IDENTITY'

by_parent = defaultdict(list)
for p in pairs:
    by_parent[(p['doc'], p['parent'])].append(p)

rows = []
model_disagree = []
for (doc, pid), kids in by_parent.items():
    comps = ir[doc]
    parent = comps.get(pid)
    h_raw, h_rew, sec = host[doc]
    hA = h_rew
    chain = kids[0]['chain']
    rtlA = chain_rtl(doc, chain, hA, 'A')
    rtlB = chain_rtl(doc, chain, hA, 'B')
    if rtlA != rtlB and any(k['applies'] and not k['box'] for k in kids):
        model_disagree.append((doc, pid, hA, rtlA, rtlB))
    pW, pH = px(last(parent, 'Width')), px(last(parent, 'Height'))
    all_kids = (parent or {}).get('_kids')
    for k in kids:
        child = comps.get(k['child'])
        if not k['applies'] or k['box']:
            continue
        L, R = px(last(child, 'Left')), px(last(child, 'Right'))
        origin_ok = pW is not None and ((L is not None) != (R is not None))
        d0 = decision(True, hA, rtlA, origin_ok, 'before')
        d1 = decision(True, hA, rtlA, origin_ok, 'U1')
        d2 = decision(True, hA, rtlA, origin_ok, 'U1U2')
        # classes
        cls = []
        earlier = [x for x in kids if x['idx'] < k['idx']]
        hs = [px(last(comps.get(x['child']), 'Height')) for x in earlier]
        if any(h is None or h != 0 for h in hs):
            cls.append('STACK(%s)' % ','.join('?' if h is None else ('%g' % h) for h in hs))
        cW, cH = px(last(child, 'Width')), px(last(child, 'Height'))
        rem = None if pH is None else pH - sum(h or 0 for h in hs)
        if (pW is not None and cW is not None and cW > pW + 0.01) or (rem is not None and cH is not None and cH > rem + 0.01):
            cls.append('CLAMP')
        pp = paints(parent)
        if pW is None:
            cls.append('AUTO-PW' + ('[%s]' % '+'.join(pp) if pp else ''))
        if pH is None:
            cls.append('AUTO-PH' + ('[%s]' % '+'.join(pp) if pp else ''))
        if cW is None:
            cls.append('AUTO-CW')
        if (L is None and R is not None) or (px(last(child, 'Top')) is None and px(last(child, 'Bottom')) is not None):
            cls.append('END')
        if any(is_pct(last(child, t)) for t in ('Width', 'Height', 'Left', 'Right', 'Top', 'Bottom')):
            cls.append('PCT')
        if any((px(last(child, m)) or 0) != 0 for m in ('MarginTop', 'MarginLeft', 'MarginRight', 'MarginBottom')):
            cls.append('MARGIN')
        rows.append(dict(sec=sec, doc=doc, parent=pid, child=k['child'], idx=k['idx'], hraw=h_raw, hrew=h_rew,
                         rtl=rtlA, rtlB=rtlB, pW=pW, pH=pH, L=L, R=R, d0=d0, d1=d1, d2=d2, cls=cls,
                         ndisp=last(parent, 'Display')))

def stems(rs):
    return sorted({r['doc'] for r in rs})

print('# skeptic census (wave55 L3) over', run)
print('documents', len(host), '| host inactive (rewritten roots)', sum(1 for v in host.values() if not v[1]),
      '| raw != rewritten host flag:', [d for d, v in host.items() if v[0] != v[1]])
print('Column-loop applying pairs (not Box branch):', len(rows), 'children /',
      len({(r['doc'], r['parent']) for r in rows}), 'parents /', len(stems(rows)), 'docs')
print('Box-branch applying-shape pairs skipped:', sum(1 for p in pairs if p['box'] and p['ppos'] in ('ABSOLUTE', 'FIXED', 'STICKY') and p['cpos'] in ('ABSOLUTE', 'FIXED')))
print('direction model A vs B disagreements on reached parents:', model_disagree)
u1 = [r for r in rows if r['d1'] != r['d0']]
u2 = [r for r in rows if r['d2'] != r['d1']]
anych = [r for r in rows if r['d2'] != r['d0']]
print('\nU1 (before -> U1 changes):', len(u1), 'children /', len({(r['doc'], r['parent']) for r in u1}), 'parents /', len(stems(u1)), 'docs')
print('U2 (U1 -> U1+U2 changes):', len(u2), 'children /', len({(r['doc'], r['parent']) for r in u2}), 'parents /', len(stems(u2)), 'docs')
print('union (before -> U1+U2):', len(stems(anych)), 'docs')
print('RTL Column-loop parents (model A):', len({(r['doc'], r['parent']) for r in rows if r['rtl']}), 'in',
      len(stems([r for r in rows if r['rtl']])), 'docs; their children', sum(1 for r in rows if r['rtl']))
withheld = [r for r in rows if r['d1'] == 'IDENTITY' and r['rtl'] and not r['hrew']]
print('U1 RTL-guard withheld children (breadcrumb rtl-parent-unmounted):', len(withheld))
declined = [r for r in rows if r['rtl'] and r['d2'] != 'MOUNT_RTL_ORIGIN']
print('U2 declined RTL children (decline breadcrumb):', len(declined), [(r['doc'], r['child']) for r in declined])
print('\n## every changed child (doc | parent -> child | idx | host | rtl | pW x pH | L R | before -> U1 -> U1+U2 | classes)')
for r in sorted(anych, key=lambda r: (r['doc'], r['parent'], r['idx'])):
    print('%s | %s -> %s | %d | host=%s | rtl=%s | %s x %s | L=%s R=%s | %s -> %s -> %s | %s | disp=%s' % (
        r['doc'], r['parent'], r['child'], r['idx'], r['hrew'], r['rtl'], r['pW'], r['pH'], r['L'], r['R'],
        r['d0'], r['d1'], r['d2'], ' '.join(r['cls']) or 'identity-shape', r['ndisp']))

# ---- cross-check with expectations.json
exp_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'wave55-plan', 'expectations.json')
e = json.load(open(exp_path))['lanes']['L3-positioned-parent-slot']
cap = set(e['captureCarriers']['android'])
u1c = set(e['revertUnits']['U1']['captures']['android'])
u2c = set(e['revertUnits']['U2']['captures']['android'])
print('\n## cross-check vs expectations.json lanes["L3-positioned-parent-slot"]')
print('wireCarriers', e['wireCarriers'], '| web', e['captureCarriers']['web'], '| ios', e['captureCarriers']['ios'])
mine_u1, mine_u2 = set(stems(u1)), set(stems(u2))
print('U1 reached docs NOT in U1 captures (must be byte-identical controls):', sorted(mine_u1 - u1c))
print('U1 captures NOT reached by my census:', sorted(u1c - mine_u1))
print('U2 reached docs NOT in U2 captures:', sorted(mine_u2 - u2c))
print('U2 captures NOT reached by my census:', sorted(u2c - mine_u2))
print('union reached NOT in captureCarriers:', sorted(set(stems(anych)) - cap))
nonid = sorted({r['doc'] for r in anych if r['cls'] and r['doc'] not in cap})
print('reached docs outside captureCarriers whose changed children carry a NON-identity class (review each):', nonid)
