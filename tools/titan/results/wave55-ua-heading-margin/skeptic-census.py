#!/usr/bin/env python3
# Wave 55 · L4 SKEPTIC census — an INDEPENDENT re-derivation of U1 / U2's blast radius (never the lane's
# census-jvm / census-swift harness): a static Python replay over the VERBATIM per-test IR of a run
# (tools/titan/runs/<run>/sections/*/per-test-ir/*.json), mirroring the rule as the patches state it.
#
# U1 (UaPositionedHeadingMargin / UAPositionedHeadingMargin) fires iff: tag has a UA block margin
# (p ul ol blockquote pre figure h1-h6) AND own Position ABSOLUTE|FIXED AND >= 1 undeclared block edge
# (MarginTop/MarginBlockStart, MarginBottom/MarginBlockEnd) AND tag h1-h6 AND not vertical (inherited
# WritingMode) AND a resolvable face. It reports every out-of-flow UA-margin-tag component, plus the
# hazards the lane's census could hide: Position read FIRST (Compose isOutOfFlowChild) vs LAST
# (a last-writer read), Position / Margin / All inside media or selector buckets (the step reads the
# POST-bucket own list, the parent's overlay split reads the RAW list), root-level (T6) placement.
#
# U2 (UAHeadingFoldGate): every h1-h6 that HAS children — the old gate stood all of them down; the
# fold gate stands down only those whose runs do not fold. This census lists them; the fold verdict
# itself is the lane's JVM replay (not re-implemented here) — the hosts are cross-checked by name.
#
#   python3 -B skeptic-census.py <run-dir>   → stdout (committed as skeptic-census.<run>.out.txt)
import json, os, sys, glob

RUN = sys.argv[1]
UA_EM = {'p': 1, 'ul': 1, 'ol': 1, 'blockquote': 1, 'pre': 1, 'figure': 1,
         'h1': .67, 'h2': .83, 'h3': 1, 'h4': 1.33, 'h5': 1.67, 'h6': 2.33}
MULT = {'h1': 2, 'h2': 1.5, 'h3': 1.17, 'h4': 1, 'h5': .83, 'h6': .67}
HEAD = set(MULT)
TOP, BOT = {'MarginTop', 'MarginBlockStart'}, {'MarginBottom', 'MarginBlockEnd'}
VERT = {'VERTICAL_RL', 'VERTICAL_LR', 'SIDEWAYS_RL', 'SIDEWAYS_LR'}
KW = {'xx-small': 9, 'x-small': 10, 'small': 13, 'medium': 16, 'large': 18, 'x-large': 24, 'xx-large': 32}


def kw(data):
    # A bare keyword string or {"keyword": …}.
    if isinstance(data, str):
        return data
    if isinstance(data, dict) and isinstance(data.get('keyword'), str):
        return data['keyword']
    return None


def positions(props):
    # Every Position keyword in declaration order.
    return [str(kw(p['data']) or '').upper() for p in props if p['type'] == 'Position']


def bucket_props(c):
    # Every property carried inside a media / selector bucket (shape-agnostic walk).
    out = []
    for key in ('media', 'selectors'):
        b = c.get(key)
        stack = [b] if b else []
        while stack:
            x = stack.pop()
            if isinstance(x, dict):
                if 'type' in x and 'data' in x and isinstance(x['type'], str):
                    out.append(x)
                else:
                    stack.extend(x.values())
            elif isinstance(x, list):
                stack.extend(x)
    return out


def font_px(props, parent_px):
    # A coarse own-FontSize resolver: px, em, %, keywords; None = no own size / unresolvable.
    for p in props:
        if p['type'] != 'FontSize':
            continue
        d = p['data']
        if isinstance(d, dict) and isinstance(d.get('px'), (int, float)):
            return float(d['px'])
        k = kw(d)
        if k and k.lower() in KW:
            return float(KW[k.lower()])
        s = json.dumps(d)
        if '"EM"' in s or '"em"' in s:
            try:
                v = d['original']['original']['v']
                return v * parent_px
            except Exception:
                return 'unresolved'
        return 'unresolved'
    return None


def first_family(props):
    for p in props:
        if p['type'] == 'FontFamily':
            s = json.dumps(p['data']).lower()
            return 'monospace' if 'monospace' in s.split(',')[0] else s[:30]
    return None


rows, docs_with_oof, heading_hosts = [], set(), []
files = sorted(glob.glob(os.path.join(RUN, 'sections', '*', 'per-test-ir', '*.json')))
for fn in files:
    doc = json.load(open(fn))
    comps = doc['components']
    byid = {c['id']: c for c in comps}
    parent = {c['id']: (c.get('slot') or {}).get('parent') for c in comps}

    def chain(cid):
        # Ancestors nearest-first.
        out, p = [], parent.get(cid)
        while p:
            out.append(byid[p])
            p = parent.get(p)
        return out

    stem = os.path.basename(fn)[:-5]
    for c in comps:
        meta = c.get('meta') or {}
        tag = (meta.get('sourceTag') or '').lower()
        props = c.get('properties') or []
        kids = [x for x in comps if parent.get(x['id']) == c['id']]
        if tag in HEAD and kids:
            heading_hosts.append((stem, c['id'], tag, len(kids), positions(props)))
        if tag not in UA_EM:
            continue
        pos = positions(props)
        bprops = bucket_props(c)
        bpos = positions(bprops)
        oof_first = bool(pos) and pos[0] in ('ABSOLUTE', 'FIXED')
        oof_last = bool(pos) and pos[-1] in ('ABSOLUTE', 'FIXED')
        oof_bucket = any(x in ('ABSOLUTE', 'FIXED') for x in bpos)
        if not (oof_first or oof_last or oof_bucket):
            continue
        docs_with_oof.add(stem)
        types = [p['type'] for p in props]
        dtop, dbot = bool(TOP & set(types)), bool(BOT & set(types))
        btypes = {p['type'] for p in bprops}
        anc = chain(c['id'])
        # Inherited WritingMode: own first, then the nearest declaring ancestor.
        wm = next((kw(p['data']) for p in props if p['type'] == 'WritingMode'), None)
        for a in anc:
            if wm is not None:
                break
            wm = next((kw(p['data']) for p in a.get('properties') or [] if p['type'] == 'WritingMode'), None)
        vertical = (wm or '').upper().replace('-', '_') in VERT
        # Inherited font size px (root → parent), and the first family for the 13px monospace rung.
        ppx, fam = 16.0, None
        for a in reversed(anc):
            r = font_px(a.get('properties') or [], ppx)
            if isinstance(r, float):
                ppx = r
            fam = first_family(a.get('properties') or []) or fam
        fam = first_family(props) or fam
        own_size = font_px(props, ppx)
        sized_anywhere = any(font_px(a.get('properties') or [], 16.0) is not None for a in anc)
        base = ppx if sized_anywhere else (13.0 if fam == 'monospace' else 16.0)
        if tag in HEAD:
            face = (MULT[tag] * base) if own_size is None else (own_size if isinstance(own_size, float) else None)
        else:
            face = None
        if not (oof_first or oof_last):
            verdict = 'BUCKET-ONLY-OOF (raw in-flow; step reads post-bucket own)'
        elif not (not dtop or not dbot):
            verdict = 'identity: both edges declared'
        elif tag not in HEAD:
            verdict = f'identity + breadcrumb tag:{tag}'
        elif vertical:
            verdict = 'identity + breadcrumb vertical'
        elif face is None:
            verdict = f'identity + breadcrumb size:{tag}'
        else:
            m = round(UA_EM[tag] * face, 3)
            edges = ('T' if not dtop else '') + ('B' if not dbot else '')
            verdict = f'FIRES margin {m} on edges {edges}'
        hazards = []
        if oof_first != oof_last:
            hazards.append('POSITION-FIRST-VS-LAST-DIFFER')
        if len(pos) > 1:
            hazards.append(f'multiple Position {pos}')
        if bpos:
            hazards.append(f'bucket Position {bpos}')
        if btypes & (TOP | BOT):
            hazards.append('bucket margin edge')
        if 'All' in types or 'All' in btypes:
            hazards.append('All present')
        if parent.get(c['id']) is None:
            hazards.append('ROOT (T6 territory)')
        rows.append((stem, c['id'], tag, pos, verdict, hazards, wm, base, fam))

print(f'run: {RUN}')
print(f'documents: {len(files)}')
print(f'out-of-flow UA-margin-tag components: {len(rows)} in {len(docs_with_oof)} documents')
fires = [r for r in rows if r[4].startswith('FIRES')]
print(f'FIRES: {len(fires)} components in {len({r[0] for r in fires})} documents')
for r in rows:
    print(f'  {r[4]:<45} {r[0]}  id={r[1]} tag={r[2]} pos={r[3]} wm={r[6]} base={r[7]} fam={r[8]} hazards={r[5] or "-"}')
print()
print(f'heading hosts (h1-h6 with >= 1 child; the U2 gate population): {len(heading_hosts)}')
for h in heading_hosts:
    print(f'  {h[0]}  id={h[1]} tag={h[2]} children={h[3]} pos={h[4]}')
