#!/usr/bin/env python3
# wave-55 L2 (cbb-android) SKEPTIC census — written independently of the lane's JUnit censuses
# (DynamicValueResolverTest "CBB census", PercentContentBoxBandTest "P-U2-6"). Wire-level, over the
# verbatim per-test IR of tools/titan/runs/<run>/sections/*/per-test-ir/ (wave54-final = wave55-open).
#
# A — U1's radius (ContainingBlockBands): thread the containing block each box publishes
#     (DynamicValueResolver.childContainingBlock, mirrored) twice — frozen (always subtract the
#     padding + border px bands) and WPT (subtract only under a declared BORDER_BOX) — starting from the
#     harness root block (358 wide, height unknown). Unlike the lane's census this one
#       * applies the runtime's tag-keyed UA list padding (ListStyleUaRule: ul/ol/menu/dir get a 40px
#         inline-start padding when the author declared none) BEFORE the block is computed, because the
#         renderer computes the block from the post-UA list;
#       * reads a SUPERSET of readers: any percentage / %-expression anywhere in the box's own properties,
#         any out-of-flow box (position absolute/fixed — AbsposInsetStretch / AbsposAutoMargin read the block
#         even without a %), and vertical-writing-mode text — the latter checked against BOTH the incoming
#         block and the box's OWN published block (VerticalTextFlowLayout reads LocalContainingBlock inside
#         the box's own provider, one level below the incoming block).
#     `A` is the superset; `A-refined` (lines `R`) keeps only the readers the renderer actually has (read from the code:
#     resolveOutOfFlowPercentSizes, AbsposInsetStretch, AbsposAutoMargin, Margin/PaddingApplier + PercentInsetPositioned
#     via ElementContainingBlock, the resolver's %-expressions, VerticalTextFlowLayout). Cross-check: skeptic-probe-diff.py.
# B — U2's radius (PercentContentBoxBand.owns): every Width/Height (logical sizes folded per writing mode,
#     as SizingExtractor does) carrying a percentage, under the WPT content-box default, with a band
#     computed as a SUPERSET (padding px or font-relative, plus border width whether or not a style is
#     declared; the UA list padding included) and the band computed as the runtime does (border only with
#     a painted style). An axis is a carrier when its runtime band is non-zero; superset-only axes are
#     listed separately so a reader can see what the style gate removes.
# Usage: python3.12 -I skeptic-census.py <sections dir>
import glob, json, os, re, sys
from collections import defaultdict

ROOT = sys.argv[1]
PCT = re.compile(r'"type":\s*"percentage"|"PERCENT"|%')            # any percentage leaf / expression
FONT_REL = {'EM': 16.0, 'REM': 16.0, 'CH': 8.0, 'EX': 8.0, 'LH': 19.2, 'CAP': 11.0, 'IC': 16.0}

def px(data):
    """Top-level px (post-resolution shape) — font-relative originals approximated (non-zero is what matters)."""
    if isinstance(data, (int, float)) and not isinstance(data, bool):
        return float(data)                                            # bare number (the logical-size shapes)
    if not isinstance(data, dict):
        return None
    if isinstance(data.get('px'), (int, float)):
        return float(data['px'])
    o = data.get('original')
    if isinstance(o, dict) and isinstance(o.get('v'), (int, float)):
        return float(o['v']) * FONT_REL.get(str(o.get('u', '')).upper(), 1.0)
    e = data.get('expr')
    if isinstance(e, str) and '%' not in e and 'var(' not in e:
        nums = re.findall(r'(-?\d+(?:\.\d+)?)px', e)
        if nums:
            return sum(float(n) for n in nums)                        # calc(70px + 70px) → 140
    return None

def first(props, *types):
    """DynamicValueResolver.pxOf: the FIRST property of each type in order that has a px."""
    for t in types:
        for p in props:
            if p['type'] == t:
                v = px(p['data'])
                if v is not None:
                    return v
                break
    return None

def size_of(props, base, *types):
    """childContainingBlock.sizeOf: px, else % of a known base, else unknown."""
    v = first(props, *types)
    if v is not None:
        return v
    for t in types:
        for p in props:
            if p['type'] == t and isinstance(p['data'], dict) and p['data'].get('type') == 'percentage':
                return None if base is None else base * float(p['data']['value']) / 100.0
    return None

def declared_box_sizing(props):
    v = None
    for p in props:
        if p['type'] == 'BoxSizing':
            v = p['data']                                             # last one wins (the extractor's fold)
    return v

def child_cb(props, parent, wpt):
    w = size_of(props, parent[0], 'Width', 'InlineSize')
    h = size_of(props, parent[1], 'Height', 'BlockSize')
    if wpt and declared_box_sizing(props) != 'BORDER_BOX':
        return (w, h)                                                 # ContainingBlockBands: content-box keeps the size
    bx = (first(props, 'PaddingLeft', 'PaddingInlineStart') or 0) + (first(props, 'PaddingRight', 'PaddingInlineEnd') or 0) \
        + (first(props, 'BorderLeftWidth') or 0) + (first(props, 'BorderRightWidth') or 0)
    by = (first(props, 'PaddingTop', 'PaddingBlockStart') or 0) + (first(props, 'PaddingBottom', 'PaddingBlockEnd') or 0) \
        + (first(props, 'BorderTopWidth') or 0) + (first(props, 'BorderBottomWidth') or 0)
    return (None if w is None else w - bx, None if h is None else h - by)

def last_kw(props, t):
    v = None
    for p in props:
        if p['type'] == t:
            v = p['data']
    return v if isinstance(v, str) else None

def ua_list(props, tag, rtl):
    """ListStyleUaRule's padding half: ul/ol/menu/dir with no author inline-start padding get 40px."""
    if tag not in ('ul', 'ol', 'menu', 'dir'):
        return props
    start = 'PaddingRight' if rtl else 'PaddingLeft'
    if any(p['type'] in (start, 'PaddingInlineStart', 'Padding') for p in props):
        return props
    return props + [{'type': start, 'data': {'px': 40}}]

def run():
    files = sorted(glob.glob(os.path.join(ROOT, '*', 'per-test-ir', '*.json')))
    a_boxes, a_docs, b_axes, b_owned, b_super = [], set(), 0, [], []
    r_boxes, r_docs = [], set()
    for f in files:
        name = os.path.basename(f)
        comps = json.load(open(f))['components']
        kids = defaultdict(list)
        roots = []
        for c in comps:
            par = (c.get('slot') or {}).get('parent')
            (kids[par] if par else roots).append(c)

        def walk(c, o, w, wm, rtl):
            props = c.get('properties', [])
            meta = c.get('meta') or {}
            tag = meta.get('sourceTag')
            own_wm = last_kw(props, 'WritingMode') or wm
            own_dir = last_kw(props, 'Direction')
            r = (own_dir == 'RTL') if own_dir else rtl
            props = ua_list(props, tag, r)
            vertical = own_wm.startswith('VERTICAL') or own_wm.startswith('SIDEWAYS')
            text = bool(c.get('text')) or bool(meta.get('runs'))
            co, cw = child_cb(props, o, False), child_cb(props, w, True)
            why = []
            if o != w:
                if any(PCT.search(json.dumps(p['data'])) for p in props):
                    why.append('pct')
                if last_kw(props, 'Position') in ('ABSOLUTE', 'FIXED'):
                    why.append('oof')
                if vertical and text:
                    why.append('vtext-in')
                # REFINED readers (the renderer's actual block readers, read from the code):
                # resolveOutOfFlowPercentSizes (% size), AbsposInsetStretch (no size + both opposing px
                # insets), AbsposAutoMargin (an auto margin between declared insets), Margin/PaddingApplier +
                # PercentInsetPositioned (% spacing / inset via ElementContainingBlock), the resolver (% in
                # an expression / original object).
                oof = last_kw(props, 'Position') in ('ABSOLUTE', 'FIXED')
                def has(t): return any(p['type'] == t for p in props)
                def sized(*ts): return any(p['type'] in ts and p['data'] not in ('auto', 'AUTO') for p in props)
                if oof and any(p['type'] in ('Width', 'Height', 'InlineSize', 'BlockSize') and isinstance(p['data'], dict)
                               and p['data'].get('type') == 'percentage' for p in props):
                    why.append('R:oof-pct')
                if oof and ((not sized('Width', 'InlineSize') and (has('Left') or has('InsetInlineStart')) and (has('Right') or has('InsetInlineEnd')))
                            or (not sized('Height', 'BlockSize') and (has('Top') or has('InsetBlockStart')) and (has('Bottom') or has('InsetBlockEnd')))):
                    why.append('R:oof-stretch')
                if oof and any(p['type'].startswith('Margin') and 'auto' in json.dumps(p['data']).lower() for p in props):
                    why.append('R:oof-automargin')
                if any(re.match(r'^(Margin|Padding|Top|Right|Bottom|Left|Inset)', p['type']) and PCT.search(json.dumps(p['data'])) for p in props):
                    why.append('R:spc')
                if any(isinstance(p['data'], dict) and ('expr' in p['data'] or isinstance(p['data'].get('original'), dict))
                       and PCT.search(json.dumps(p['data'])) for p in props):
                    why.append('R:dyn')
                if vertical and text:
                    why.append('R:vtext-in')
            if vertical and text and co != cw:
                why.extend(['vtext-own', 'R:vtext-own'])
            if why:
                a_boxes.append(f'{name} {c["id"].split("__")[-1]} {why} in {o}->{w} own {co}->{cw}')
                a_docs.add(name)
                if any(x.startswith('R:') for x in why):
                    r_boxes.append(f'{name} {c["id"].split("__")[-1]} {[x for x in why if x.startswith("R:")]} in {o}->{w} own {co}->{cw}')
                    r_docs.add(name)
            # B — U2 axes (WPT reading: content-box unless a declared border-box).
            nonlocal b_axes
            for axis, types in (('W', ('Width', 'BlockSize' if vertical else 'InlineSize')),
                                ('H', ('Height', 'InlineSize' if vertical else 'BlockSize'))):
                raw = None
                for p in props:                                       # the extractor's fold: last of the axis' types
                    if p['type'] in types:
                        raw = p['data']
                if not (isinstance(raw, dict) and raw.get('type') == 'percentage'):
                    continue
                b_axes += 1
                if declared_box_sizing(props) == 'BORDER_BOX':
                    continue
                sides = ('Left', 'Right') if axis == 'W' else ('Top', 'Bottom')
                logical = ('InlineStart', 'InlineEnd') if axis == 'W' else ('BlockStart', 'BlockEnd')
                pad = sum((first(props, 'Padding' + s, 'Padding' + l) or 0) for s, l in zip(sides, logical))
                bw_any = sum((first(props, f'Border{s}Width') or 0) for s in sides)
                bw_styled = sum((first(props, f'Border{s}Width') or 0) for s in sides
                                if (last_kw(props, f'Border{s}Style') or 'NONE') not in ('NONE', 'HIDDEN'))
                line = f'{name} {c["id"].split("__")[-1]} {axis} {raw["value"]}% pad {pad} border(styled) {bw_styled} border(any) {bw_any}'
                if pad + bw_styled != 0:
                    b_owned.append(line)
                elif bw_any != 0:
                    b_super.append(line)
            for k in kids.get(c['id'], []):
                walk(k, co, cw, own_wm, r)

        for c in roots:
            walk(c, (358.0, None), (358.0, None), 'HORIZONTAL_TB', False)
    print(f'documents {len(files)}')
    print(f'A U1 boxes {len(a_boxes)} in {len(a_docs)} documents')
    for d in sorted(a_docs):
        print('  A doc', d)
    for b in a_boxes:
        print('  A box', b)
    print(f'A-refined U1 boxes {len(r_boxes)} in {len(r_docs)} documents (the renderer readers only)')
    for d in sorted(r_docs):
        print('  R doc', d)
    for b in r_boxes:
        print('  R box', b)
    print(f'B U2 percentage axes {b_axes}; runtime-band carriers {len(b_owned)}; border-width-without-style (superset only) {len(b_super)}')
    for b in b_owned:
        print('  B carrier', b)
    for b in b_super:
        print('  B superset-only', b)

run()
