#!/usr/bin/env python3
"""S1 census replay (wave 54): re-derive every lane's carrier set from the wave54-open per-test IR with S1's OWN
static models (not the lanes' scripts), then compare with expectations.json lanes.<lane>.revertUnits[*].captures/wire.

Models (each a static reading of the landed code's trigger, applied to the PRE-landing wire):
  L1 P   : a bidi-bake root (bidiBaked doc; RELATIVE + BORDER_BOX root holding ABSOLUTE runs) with non-zero padding,
           no content-box clip/origin, overflow visible.  Captures android only (Android anchors at the content box).
  L1 M'  : a component with meta.markerText AND Position ABSOLUTE|FIXED (a bake-positioned list item).
  L2     : a body-root whose LAST Display is TABLE|INLINE_TABLE (the only TableBodyForest trigger).
  L3 U1  : a HyphenateCharacter decl whose value changes under css-syntax-3 decoding ("" kept, escapes decoded).
  L3 U3  : replays buildNode's br state machine per runs-host, base vs + the child-scope re-arm; a br whose height
           flips is a wire change.  Sanity: the base replay must reproduce every br height on the wire.
  L3 U3b : a br still at 20px after U3 inside a host whose OWN props carry LineHeight (px, or multiplier x own px FontSize).
  L4 OOF : static reach of (a) a non-ABSOLUTE/FIXED establisher (Contain layout/paint/strict/content, non-empty
           Filter/BackdropFilter, WillChange filter/backdrop-filter/contain) with an out-of-flow descendant whose
           nearest positioned/transformed ancestor lies above it, (b) a FIXED box with no declared inset.
           Swift: (a) for FIXED descendants only, plus (b).
  L4 CBB : a non-border-box parent with non-zero padding/border whose child reads the published block
           (out-of-flow child with % size, opposing insets, auto margins with insets, or a % margin/padding/inset).
  L4 GAP : a flex container with a rule family on an axis whose gap is zero/absent.
  L5     : a tagged h1..h6 (leaf, no own FontSize/Font/FontWeight) or sub/sup (no own FontSize/Font).
  L6 RS  : consecutive roots (no slot.parent) prev/next with prev meta.role ws-after, both inline-level (declared
           inline* display, else the INLINE_LEVEL_SOURCE_TAGS set), body-root white-space not preserving, the root
           container not a table plan (body-root display table|inline-table).
  L6 W1  : a runs-host with its OWN Hyphens AUTO whose {child} member is ABSOLUTE|FIXED, alpha-0 Color, no children,
           a span/time/data tag, between two {text} pieces that meet mid-word.
"""
import json, glob, os, re, sys, collections

RUN = sys.argv[1]
EXP = sys.argv[2]
SEC = f"{RUN}/sections"

def stem(path):
    return os.path.basename(path)[:-5]

docs = {}
for f in sorted(glob.glob(f"{SEC}/*/per-test-ir/*.json")):
    docs[stem(f)] = json.load(open(f))['components']
manifest_baked = set()
for m in glob.glob(f"{SEC}/*/manifest.json"):
    res = json.load(open(m)).get('wpt', {}).get('results', {})
    for test, r in res.items():
        if r.get('bidiBaked'):
            for c in r.get('components', [])[:1]:
                manifest_baked.add(c.rsplit('__', 1)[0])

def props(c):
    return {p['type']: p['data'] for p in c.get('properties', []) or []}

def all_props(c):
    return [(p['type'], p['data']) for p in c.get('properties', []) or []]

def kw(v):
    return v.upper() if isinstance(v, str) else None

def px(v):
    if isinstance(v, dict):
        if 'px' in v and isinstance(v['px'], (int, float)): return v['px']
    return None

def nonzero_len(v):
    if v is None: return False
    if isinstance(v, dict):
        if isinstance(v.get('px'), (int, float)): return v['px'] != 0
        o = v.get('original')
        if isinstance(o, dict):
            if isinstance(o.get('v'), (int, float)): return o['v'] != 0
            if isinstance(o.get('px'), (int, float)): return o['px'] != 0
        return True
    return False

def is_pct(v):
    s = json.dumps(v)
    return '"PERCENT"' in s or '"%"' in s or '"u": "PERCENT"' in s or 'percent' in s.lower()

def tree(cs):
    by = {c['id']: c for c in cs}
    kids = collections.defaultdict(list)
    for c in cs:
        p = (c.get('slot') or {}).get('parent')
        if p: kids[p].append(c)
    parent = {c['id']: (c.get('slot') or {}).get('parent') for c in cs}
    return by, kids, parent

def position(c):
    return kw(props(c).get('Position'))

out = collections.defaultdict(lambda: collections.defaultdict(set))   # lane-unit -> platform -> stems
notes = collections.defaultdict(list)

# ---------------- L1 ----------------
for s, cs in docs.items():
    by, kids, parent = tree(cs)
    baked = any(s.startswith(b) or s == b for b in manifest_baked) or s in manifest_baked
    for c in cs:
        P = props(c)
        if c.get('meta', {}).get('markerText') is not None and kw(P.get('Position')) in ('ABSOLUTE', 'FIXED'):
            for pf in ('web', 'ios', 'android'): out['L1/Mprime'][pf].add(s)
            out['L1/Mprime']['wire'].add(s)
        if not baked: continue
        if kw(P.get('Position')) not in ('RELATIVE', 'FIXED', 'ABSOLUTE', 'STICKY') or kw(P.get('BoxSizing')) != 'BORDER_BOX': continue   # rootProperties keeps a non-static author position
        ch = kids.get(c['id'], [])
        if not ch or not any(position(k) == 'ABSOLUTE' for k in ch): continue
        # a ROOT is the OUTERMOST bake-positioned box: an ancestor with the same signature makes this a baked inline box
        def sig(a):
            A = props(a)
            return kw(A.get('Position')) in ('RELATIVE', 'FIXED', 'ABSOLUTE', 'STICKY') and kw(A.get('BoxSizing')) == 'BORDER_BOX' \
                and any(position(k) == 'ABSOLUTE' for k in kids.get(a['id'], []))
        pp = parent.get(c['id']); inner = False
        while pp:
            if sig(by[pp]): inner = True; break
            pp = parent.get(pp)
        if inner: continue
        pads = [P.get(k) for k in ('PaddingTop', 'PaddingRight', 'PaddingBottom', 'PaddingLeft')]
        if not any(nonzero_len(v) for v in pads): continue
        blob = json.dumps(P)
        if 'CONTENT_BOX' in json.dumps({k: P.get(k) for k in ('BackgroundClip', 'BackgroundOrigin')}):
            notes['L1'].append(f'{s} {c["id"]}: content-box guard'); continue
        ov = [kw(P.get(k)) for k in ('Overflow', 'OverflowX', 'OverflowY')]
        if any(o not in (None, 'VISIBLE') for o in ov):
            notes['L1'].append(f'{s} {c["id"]}: overflow guard'); continue
        out['L1/P']['android'].add(s); out['L1/P']['wire'].add(s)
        notes['L1'].append(f'P root {s} {c["id"]}')

# ---------------- L2 ----------------
for s, cs in docs.items():
    for c in cs:
        if (c.get('meta') or {}).get('role') != 'body-root': continue
        disp = [d for t, d in all_props(c) if t == 'Display']
        if disp and kw(disp[-1]) in ('TABLE', 'INLINE_TABLE'):
            out['L2/TB-android']['android'].add(s)

# ---------------- L3 U1 ----------------
def u1_changes(raw):
    s = raw.strip()
    if len(s) >= 2 and s[0] == s[-1] and s[0] in '"\'':
        inner = s[1:-1]
        return inner == '' or '\\' in inner
    return False
for s, cs in docs.items():
    for c in cs:
        for t, d in all_props(c):
            if t == 'Generic' and isinstance(d, dict) and d.get('propertyName') == 'hyphenate-character':
                if u1_changes(d.get('rawValue', '')): out['L3/U1']['wire'].add(s)
            if t == 'HyphenateCharacter':
                v = d.get('value') if isinstance(d, dict) else None
                if isinstance(v, str) and '\\' in v: out['L3/U1']['wire'].add(s)

# ---------------- L3 U3 / U3b: br state machine ----------------
INLINE_LEVEL_TAGS = {'span', 'a', 'b', 'i', 'em', 'strong', 'code', 'small', 'sub', 'sup', 'u', 's', 'q', 'abbr',
    'cite', 'time', 'label', 'mark', 'bdi', 'bdo', 'samp', 'kbd', 'var', 'img', 'input', 'select', 'button',
    'textarea', 'output', 'meter', 'progress', 'ruby', 'rt', 'rb'}
WS = re.compile(r'[^ \t\n\r\f]')
def br_height(c):
    h = props(c).get('Height')
    return px(h)
def host_line_box(host):
    P = props(host)
    lh = P.get('LineHeight')
    if lh is None: return None
    if isinstance(lh, dict):
        if isinstance(lh.get('px'), (int, float)) and 'multiplier' not in lh: return lh['px']
        m = lh.get('multiplier')
        fs = px(P.get('FontSize'))
        if isinstance(m, (int, float)) and fs is not None: return round(m * fs, 4)
        o = lh.get('original')
        if isinstance(o, dict) and isinstance(o.get('px'), (int, float)): return o['px']
    return None
sanity_bad = []; u3_brs = collections.Counter(); u3b_brs = collections.Counter()
for s, cs in docs.items():
    by, kids, parent = tree(cs)
    for host in cs:
        runs = (host.get('meta') or {}).get('runs')
        if not runs: continue
        order = [e['child'] for e in runs if 'child' in e]
        hostText = bool(host.get('text'))
        for variant in ('base', 'u3'):
            has = hostText
            prev_child_pos = -1
            for idx, e in enumerate(runs):
                if 'child' not in e: continue
                if variant == 'u3':
                    seg = runs[prev_child_pos + 1: idx]
                    if any('text' in r and WS.search(r['text']) for r in seg): has = True
                prev_child_pos = idx
                ch = by.get(e['child']) or next((c for c in cs if c.get('name') == e['child']), None)
                if ch is None: continue
                m = ch.get('meta') or {}
                tag = (m.get('sourceTag') or '').lower()
                P = props(ch)
                if tag == 'br':
                    isclear = 'Clear' in P
                    h = 0 if (has or isclear) else 20
                    real = br_height(ch)
                    if variant == 'base':
                        if real is not None and abs(real - h) > 1e-6:
                            sanity_bad.append((s, ch['id'], real, h))
                    else:
                        if real is not None and abs(real - h) > 1e-6:
                            u3_brs[s] += 1
                        if h == 20 and host_line_box(host) is not None and abs(host_line_box(host) - 20) > 1e-6:
                            u3b_brs[s] += 1
                    has = False
                else:
                    d = kw(P.get('Display')) or ''
                    fl = kw(P.get('Float')) or ''
                    po = kw(P.get('Position')) or ''
                    oof = fl in ('LEFT', 'RIGHT', 'INLINE_START', 'INLINE_END') or po in ('ABSOLUTE', 'FIXED')
                    if not oof and d != 'NONE':
                        has = d.startswith('INLINE') or d == 'CONTENTS' if d else tag in INLINE_LEVEL_TAGS
for s in u3_brs: out['L3/U3']['wire'].add(s)
for s in u3b_brs: out['L3/U3b']['wire'].add(s)
notes['L3'].append(f'U3 brs {sum(u3_brs.values())} in {len(u3_brs)} docs; U3b brs {sum(u3b_brs.values())} in {len(u3b_brs)} docs; base-replay mismatches {len(sanity_bad)}')
for x in sanity_bad[:12]: notes['L3'].append(f'  base replay != wire: {x}')

# ---------------- L4 OOF ----------------
def contain_fires(v):
    s = json.dumps(v).upper()
    return any(k in s for k in ('LAYOUT', 'PAINT', 'STRICT', 'CONTENT')) and 'INLINE_SIZE' not in s.replace('LAYOUT', '') or \
        any(k in s for k in ('LAYOUT', 'PAINT', 'STRICT', '"CONTENT"'))
def establishes(c):
    P = props(c)
    fires = False
    for t, d in all_props(c):
        if t == 'Contain' and contain_fires(d): fires = True
        if t in ('Filter', 'BackdropFilter') and ((isinstance(d, list) and d) or isinstance(d, dict)): fires = True
        if t == 'WillChange' and isinstance(d, list) and any(isinstance(h, dict) and h.get('name') in ('filter', 'backdrop-filter', 'contain') for h in d): fires = True
    return fires and kw(P.get('Position')) not in ('ABSOLUTE', 'FIXED')
def transformed(c):
    P = props(c)
    return any(t in P for t in ('Transform', 'Perspective', 'Translate', 'Rotate', 'Scale')) and json.dumps(P.get('Transform', '')) not in ('"NONE"', '[]')
INSETS = ('Top', 'Right', 'Bottom', 'Left', 'InsetBlockStart', 'InsetBlockEnd', 'InsetInlineStart', 'InsetInlineEnd')
def declares_inset(c):
    P = props(c)
    return any(k in P and P[k] != 'auto' for k in INSETS)
for s, cs in docs.items():
    by, kids, parent = tree(cs)
    def anc(cid):
        p = parent.get(cid)
        while p:
            yield by[p]; p = parent.get(p)
    hitA = hitI = False
    for c in cs:
        po = position(c)
        if po not in ('ABSOLUTE', 'FIXED'): continue
        if po == 'FIXED' and not declares_inset(c) and not any(transformed(a) for a in anc(c['id'])):
            hitA = hitI = True; notes['L4'].append(f'OOF(b) {s} {c["id"]} FIXED no inset')
        for a in anc(c['id']):
            if establishes(a):
                hitA = True
                if po == 'FIXED': hitI = True
                notes['L4'].append(f'OOF(a) {s} {c["id"]} {po} under establisher {a["id"]}')
                break
            if transformed(a): break
            if po == 'ABSOLUTE' and position(a) in ('RELATIVE', 'ABSOLUTE', 'FIXED', 'STICKY'): break
    if hitA: out['L4/OOF-android']['android'].add(s)
    if hitI: out['L4/OOF-ios']['ios'].add(s)

# ---------------- L4 CBB ----------------
def bands(c):
    P = props(c)
    keys = ('PaddingTop', 'PaddingRight', 'PaddingBottom', 'PaddingLeft', 'BorderTopWidth', 'BorderRightWidth',
            'BorderBottomWidth', 'BorderLeftWidth')
    return any(nonzero_len(P.get(k)) for k in keys)
def oof_reads(P, po):
    if po not in ('ABSOLUTE', 'FIXED'): return False
    if is_pct(P.get('Width')) or is_pct(P.get('Height')): return True
    if ('Left' in P and 'Right' in P) or ('Top' in P and 'Bottom' in P): return True
    return any(is_pct(P.get(i)) for i in INSETS)
PCT_BOX = ('MarginTop', 'MarginRight', 'MarginBottom', 'MarginLeft', 'PaddingTop', 'PaddingRight', 'PaddingBottom', 'PaddingLeft')
for s, cs in docs.items():
    by, kids, parent = tree(cs)
    hit = None
    for c in cs:
        if kw(props(c).get('BoxSizing')) == 'BORDER_BOX' or not bands(c): continue
        # BFS: the changed block reaches a child; an in-flow child with a % size republishes a changed block (transitive)
        frontier = list(kids.get(c['id'], []))
        while frontier and not hit:
            k = frontier.pop(0); P = props(k); po = position(k)
            if oof_reads(P, po) or any(is_pct(P.get(m)) for m in PCT_BOX): hit = (c['id'], k['id']); break
            if is_pct(P.get('Width')) or is_pct(P.get('Height')): frontier.extend(kids.get(k['id'], []))
        if hit: break
    if hit:
        out['L4/CBB-android']['android'].add(s); notes['L4'].append(f'CBB {s} {hit[0]} -> {hit[1]}')

# ---------------- L4 GAP ----------------
for s, cs in docs.items():
    for c in cs:
        P = props(c)
        if kw(P.get('Display')) not in ('FLEX', 'INLINE_FLEX'): continue
        col_rule = kw(P.get('ColumnRuleStyle')) not in (None, 'NONE', 'HIDDEN')
        row_rule = kw(P.get('RowRuleStyle')) not in (None, 'NONE', 'HIDDEN')
        cg = px(P.get('ColumnGap')); rg = px(P.get('RowGap'))
        if (col_rule and not cg) or (row_rule and not rg):
            notes['L4'].append(f'GAP candidate {s} {c["id"]} jc={P.get("JustifyContent")} ac={P.get("AlignContent")}')
            if kw(P.get('JustifyContent')) in (None, 'FLEX_START', 'START', 'NORMAL') and kw(P.get('AlignContent')) in (None, 'NORMAL', 'STRETCH', 'FLEX_START', 'START'):
                out['L4/GAP-android']['android'].add(s); out['L4/GAP-ios']['ios'].add(s)

# ---------------- L5 ----------------
for s, cs in docs.items():
    by, kids, parent = tree(cs)
    for c in cs:
        tag = ((c.get('meta') or {}).get('sourceTag') or '').lower()
        P = props(c)
        if tag in ('h1', 'h2', 'h3', 'h4', 'h5', 'h6'):
            if kids.get(c['id']): continue                       # headingStandsDown
            if any(k in P for k in ('FontSize', 'Font')) and 'FontWeight' in P: continue
            out['L5/U1-android']['android'].add(s); notes['L5'].append(f'{s} {c["id"]} {tag} leaf')
        elif tag in ('sub', 'sup'):
            if 'FontSize' in P or 'Font' in P: continue
            out['L5/U1-android']['android'].add(s); out['L5/sub-sup-only']['android'].add(s); notes['L5'].append(f'{s} {c["id"]} {tag}')

# ---------------- L6 ----------------
PRESERVE = {'PRE', 'PRE_WRAP', 'BREAK_SPACES', 'PRE_LINE'}
def inline_level(c):
    d = kw(props(c).get('Display'))
    if d: return d.startswith('INLINE')
    return ((c.get('meta') or {}).get('sourceTag') or '').lower() in INLINE_LEVEL_TAGS
seps = collections.Counter()
for s, cs in docs.items():
    roots = [c for c in cs if not (c.get('slot') or {}).get('parent')]
    body = next((c for c in cs if (c.get('meta') or {}).get('role') == 'body-root'), None)
    if body is not None:
        bd = [d for t, d in all_props(body) if t == 'Display']
        if bd and kw(bd[-1]) in ('TABLE', 'INLINE_TABLE'): continue
        if kw(props(body).get('WhiteSpace')) in PRESERVE: continue
    for a, b in zip(roots, roots[1:]):
        if (a.get('meta') or {}).get('role') == 'ws-after' and inline_level(a) and inline_level(b):
            seps[s] += 1
for s in seps: out['L6/RS']['web'].add(s)
notes['L6'].append(f'RS separators {sum(seps.values())} in {len(seps)} docs')
W1 = collections.Counter()
for s, cs in docs.items():
    by, kids, parent = tree(cs)
    for host in cs:
        runs = (host.get('meta') or {}).get('runs')
        if not runs or kw(props(host).get('Hyphens')) != 'AUTO': continue
        for i in range(1, len(runs) - 1):
            e = runs[i]
            if 'child' not in e: continue
            m = by.get(e['child']) or next((c for c in cs if c.get('name') == e['child']), None)
            if m is None: continue
            P = props(m); tag = ((m.get('meta') or {}).get('sourceTag') or '').lower()
            col = P.get('Color')
            alpha0 = isinstance(col, dict) and (col.get('srgb') or {}).get('a') is not None and col['srgb']['a'] <= 0
            others = set(P) - {'Position', 'Color', 'Hyphens', 'Top', 'Left', 'Right', 'Bottom'}
            inert = position(m) in ('ABSOLUTE', 'FIXED') and alpha0 and not kids.get(m['id']) and tag in ('span', 'time', 'data') and not others
            before, after = runs[i - 1], runs[i + 1]
            if inert and 'text' in before and 'text' in after and re.search(r'\w$', before['text']) and re.match(r'\w', after['text']):
                W1[s] += 1
for s in W1: out['L6/W1']['web'].add(s)
notes['L6'].append(f'W1 joins {sum(W1.values())} in {len(W1)} docs')

# ---------------- L7 (must-not-move census only) ----------------
allp = [s for s, cs in docs.items() if any(t == 'All' for c in cs for t, _ in all_props(c))]
notes['L7'].append(f'docs carrying an All property: {len(allp)}')

# ---------------- compare ----------------
e = json.load(open(EXP))['lanes']
lane_of = {'L1': 'L1-rtl-marker-bake', 'L2': 'L2-table-body-cell', 'L3': 'L3-hyphenate-character', 'L4': 'L4-oof-layout',
           'L5': 'L5-ua-heading-face', 'L6': 'L6-web-tail'}
print(f'documents {len(docs)} · bidiBaked {len(manifest_baked)}')
for key in sorted(out):
    ln, unit = key.split('/', 1)
    U = e[lane_of[ln]]['revertUnits'].get(unit)
    for pf in ('web', 'ios', 'android', 'wire'):
        mine = out[key].get(pf, set())
        if U is None:
            if mine: print(f'{key:44s} {pf:7s} mine {len(mine):3d} (no unit of that name: model-only set)')
            continue
        plan = set(U.get('wire') or []) if pf == 'wire' else set((U.get('captures') or {}).get(pf) or [])
        if not mine and not plan: continue
        if not mine and pf != 'wire' and key.startswith('L3/'): print(f'{key:44s} {pf:7s} (wire-only model; captures follow the wire + per-platform pixel replay, not re-derived)'); continue
        tag = 'EQUAL' if mine == plan else 'DIFF'
        print(f'{key:44s} {pf:7s} mine {len(mine):3d} plan {len(plan):3d} {tag}')
        for x in sorted(mine - plan): print(f'      only-mine  {x}')
        for x in sorted(plan - mine): print(f'      only-plan  {x}')
print()
for ln in sorted(notes):
    for n in notes[ln][:60]: print(f'[{ln}] {n}')
