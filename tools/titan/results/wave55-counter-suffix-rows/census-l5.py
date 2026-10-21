#!/usr/bin/env python3
# census-l5.py — wave 55 lane L5 (counter-suffix-rows): the BUILDER's own
# behavioural blast-radius census, written independently of the brief's
# counter-suffix-rows.census.py. It re-derives, from the verbatim per-test IR
# of a run, which components each L5 unit's code reaches on each native, by
# modelling the renderers' own decisions (not a static grep):
#
#   iOS  — ComponentRenderer.swift: in-flow children only (isOutOfFlow drops
#          absolute / fixed); marker = baked meta.markerText, else (an <li>
#          whose own Display keeps list-item, under ol/ul/menu/dir) the
#          ListMarkerText table for ListMarkerResolver's fold; placement =
#          inside-overlay / outside-hang / HStack (ListMarkerEmptyItem,
#          ListMarkerRow.hangsOutside).
#   Compose — ComponentRenderer.kt: in-flow <li> children of a list parent
#          (uaMarkerDefault != null) that keep their marker (liKeepsItsMarker);
#          same three placements (ListMarkerEmptyItem / ListMarkerRow).
#
# Unit rules (the code under test, re-implemented here as the census key):
#   U1-ios      ListMarkerBidi.painted(marker, itemIsRTL) != marker, i.e. the
#               UAX #9 P2/P3 natural direction (the SAME letter-category /
#               default-R-block approximation the Swift file uses) differs
#               from the item's resolved `direction` (own, else inherited).
#   U2o-*       outside-hang marker whose RAW string ends in U+3001.
#   U2i-*       HStack / Row marker whose RAW string ends in U+3001.
#   U3-ios      a component that DECLARES FontVariantNumeric tabular-nums
#               (own properties; not inherited on either native).
#
# Output: per-unit reach (documents, components, captures) and a cross-check
# against expectations.json lanes["L5-counter-suffix-rows"].
#
# Usage: python3.12 -B census-l5.py [run]   (default wave54-final)
import json, os, sys, unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))                 # the lane dir
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))  # repo root
RUN = sys.argv[1] if len(sys.argv) > 1 else 'wave54-final'         # run of record
SECTIONS = os.path.join(ROOT, 'tools', 'titan', 'runs', RUN, 'sections')
EXPECT = os.path.join(ROOT, 'tools', 'titan', 'results', 'wave55-plan', 'expectations.json')
CELLS = os.path.join(ROOT, 'tools', 'titan', 'results', 'wave55-plan', 'cells-wave54-final.json')

LIST_TAGS = {'ol': 'decimal', 'ul': 'disc', 'menu': 'disc', 'dir': 'disc'}  # HTML §15.3.7 UA defaults
INHERITED = {'Direction', 'ListStyleType', 'ListStylePosition', 'ListStyle'}  # the subset of InheritedText this census needs
BLOCK_SIZE = {'Height', 'MinHeight', 'BlockSize', 'MinBlockSize'}    # ListMarkerEmptyItem.declaredBlockSize
TABULAR = {'tabular_nums', 'tabular-nums'}                           # FontVariantNumericApplier.tabular


def kw(data):
    """ValueExtractors.extractKeyword stand-in: a bare string or {keyword|value}."""
    if isinstance(data, str):
        return data
    if isinstance(data, dict):
        for k in ('keyword', 'value'):
            if isinstance(data.get(k), str):
                return data[k]
    return None


def last(props, t):
    """Last-wins fold of one property type (every extractor's convention)."""
    hit = [p for p in props if p.get('type') == t]
    return hit[-1] if hit else None


def merge(own, inherited):
    """InheritedText.merge: inherited entries for undeclared types, then own."""
    declared = {p['type'] for p in own}
    return [p for p in inherited if p['type'] not in declared] + list(own)


def inheritable(props):
    """InheritedText.inheritable restricted to the census's types."""
    return [p for p in props if p['type'] in INHERITED]


def is_out_of_flow(c):
    """ComponentRenderer.isOutOfFlow (both natives): absolute / fixed."""
    p = last(c.get('properties', []), 'Position')
    return (kw(p['data']) or '').upper() in ('ABSOLUTE', 'FIXED') if p else False


def is_rtl(merged):
    """ListStyleUaRule.isRtl: the last Direction is the `rtl` keyword."""
    d = last(merged, 'Direction')
    return bool(d) and (kw(d['data']) or '').upper() == 'RTL'


R_BLOCKS = [(0x0590, 0x08FF), (0xFB1D, 0xFDFF), (0xFE70, 0xFEFF), (0x10800, 0x10FFF), (0x1E800, 0x1EFFF)]


def natural_is_rtl(s):
    """ListMarkerBidi.naturalIsRTL, re-implemented (P2 skips isolates)."""
    depth = 0
    for ch in s:
        v = ord(ch)
        if v in (0x2066, 0x2067, 0x2068):
            depth += 1; continue
        if v == 0x2069:
            depth = max(0, depth - 1); continue
        if depth:
            continue
        if v == 0x200E:
            return False
        if v in (0x200F, 0x061C):
            return True
        if unicodedata.category(ch).startswith('L'):
            return any(a <= v <= b for a, b in R_BLOCKS)
    return False


def table_marker(t):
    """A representative native-table string per type (ListMarkerText /
    ListStyleApplier.getMarker): only its DIRECTION and tail matter here."""
    t = (t or '').lower().replace('_', '-')
    if t == 'none':
        return ''
    if t == 'hebrew':
        return 'א.'      # strong R, "." suffix
    if t in ('disc', 'circle', 'square'):
        return '•'       # neutral
    if t in ('decimal', 'decimal-leading-zero'):
        return '1.'           # neutral
    return 'a.'               # every other modelled table answer is strong L or neutral; "." / "," suffix


MODELLED = {'disc', 'circle', 'square', 'none', 'decimal', 'decimal-leading-zero', 'lower-alpha', 'lower-latin',
            'upper-alpha', 'upper-latin', 'lower-roman', 'upper-roman', 'lower-greek', 'upper-greek', 'armenian',
            'georgian', 'hebrew', 'cjk-decimal', 'hiragana', 'katakana', 'hiragana-iroha', 'katakana-iroha',
            'korean-hangul-formal'}                                       # ListMarkerResolver.markerType(fromKeyword:)


def resolve(parent_tag, parent_inh, own):
    """ListMarkerResolver.resolve / ListStyleExtractor.resolveMarkerConfig fold
    (an unmodelled keyword keeps the running value, as markerType's nil does)."""
    ua = LIST_TAGS.get((parent_tag or '').lower())
    if not ua:
        return None
    typ, pos = ua, 'outside'
    for p in parent_inh + own:
        if p['type'] == 'ListStyleType' and (kw(p['data']) or '').lower().replace('_', '-') in MODELLED:
            typ = kw(p['data'])
        elif p['type'] == 'ListStylePosition' and (kw(p['data']) or '').lower() in ('inside', 'outside'):
            pos = kw(p['data']).lower()
    return typ, pos


def exposes_baseline(c, kids):
    """ListMarkerRow.itemExposesTextBaseline: own text, else an in-flow child's."""
    if c.get('text'):
        return True
    return any(not is_out_of_flow(k) and exposes_baseline(k, kids) for k in kids.get(c['id'], []))


def is_empty(c, kids):
    """ListMarkerEmptyItem.isEmpty."""
    if c.get('text') or kids.get(c['id']):
        return False
    ps = c.get('pseudos')
    if ps not in (None, {}):
        return False
    return not any(p['type'] in BLOCK_SIZE for p in c.get('properties', []))


def placement(pos, item, kids):
    """overlay / hang / row (iOS HStack, Compose Row)."""
    if pos == 'inside' and not exposes_baseline(item, kids) and not is_empty(item, kids):
        return 'overlay'
    if pos == 'outside':
        return 'hang'
    return 'row'


def display_takes_marker_away(own):
    """ListItemMarkerGate.displayTakesMarkerAway: an authored non-list-item Display."""
    d = last(own, 'Display')
    k = (kw(d['data']) or '') if d else ''
    return bool(d) and k.lower().replace('_', '-') != 'list-item'


def census_doc(path):
    doc = json.load(open(path, encoding='utf-8'))
    comps = doc.get('components', [])
    by_id = {c['id']: c for c in comps}
    kids = {}
    for c in comps:                                   # v2 flat list: slot.parent names the parent
        par = (c.get('slot') or {}).get('parent')
        if par in by_id:
            kids.setdefault(par, []).append(c)
    merged = {}

    def walk(c, inh):                                 # resolve the inherited channel top-down
        own = c.get('properties', [])
        m = merge(own, inh)
        tag = ((c.get('meta') or {}).get('sourceTag') or '').lower()
        if tag in LIST_TAGS and not last(own, 'ListStyleType'):
            m = [p for p in m if p['type'] != 'ListStyleType'] + [{'type': 'ListStyleType', 'data': LIST_TAGS[tag]}]
        merged[c['id']] = m
        for k in kids.get(c['id'], []):
            walk(k, inheritable(m))

    for c in comps:
        if (c.get('slot') or {}).get('parent') not in by_id:
            walk(c, [])
    rows = []                                          # (unit, platform, component name, detail)
    for c in comps:
        ptag = ((c.get('meta') or {}).get('sourceTag') or '').lower()
        inh = inheritable(merged.get(c['id'], []))
        for x in kids.get(c['id'], []):
            if is_out_of_flow(x):
                continue                               # both natives: absolute / fixed render elsewhere
            own = x.get('properties', [])
            meta = x.get('meta') or {}
            cfg = resolve(ptag, inh, own)
            baked = meta.get('markerText')
            is_li = (meta.get('sourceTag') or '').lower() == 'li'
            # iOS marker string (a baked marker wins even on a non-<li>)
            ios = baked if baked is not None else (table_marker(cfg[0]) if (is_li and cfg and not display_takes_marker_away(own)) else '')
            # Compose: only <li> of a list parent that keeps its marker
            andr = None
            if cfg and is_li and (baked is not None or not display_takes_marker_away(own)):
                andr = baked if baked is not None else table_marker(cfg[0])
            pos = cfg[1] if cfg else None
            place = placement(pos, x, kids)
            rtl = is_rtl(merge(own, inh))
            if ios:
                rows.append(('ALL-ios', 'ios', x['name'], f"{'baked' if baked is not None else 'table'} {'R' if natural_is_rtl(ios) else 'L/none'}/{'rtl' if rtl else 'ltr'} {place}"))
                if natural_is_rtl(ios) != rtl:
                    rows.append(('U1-ios', 'ios', x['name'], f"{ios!r} natural={'R' if natural_is_rtl(ios) else 'L/none'} item={'rtl' if rtl else 'ltr'} {place} {'baked' if baked is not None else 'table'}"))
                if ios.endswith('、'):
                    rows.append(('U2o-ios' if place == 'hang' else 'U2i-ios' if place == 'row' else 'U2-overlay-ios', 'ios', x['name'], f"{ios!r} {place}"))
            if andr:
                if andr.endswith('、'):
                    rows.append(('U2o-android' if place == 'hang' else 'U2i-android' if place == 'row' else 'U2-overlay-android', 'android', x['name'], f"{andr!r} {place}"))
    for c in comps:                                    # U3: declared tabular-nums
        p = last(c.get('properties', []), 'FontVariantNumeric')
        if p:
            vals = p['data'] if isinstance(p['data'], list) else [p['data']]
            words = {str(kw(v) or v).lower() for v in vals}
            if words & TABULAR:
                rows.append(('U3-ios', 'ios', c['name'], f"text={c.get('text')!r}"))
    return rows


def main():
    docs = []
    for sec in sorted(os.listdir(SECTIONS)):
        d = os.path.join(SECTIONS, sec, 'per-test-ir')
        if os.path.isdir(d):
            docs += [(sec, os.path.join(d, f)) for f in sorted(os.listdir(d)) if f.endswith('.json')]
    print(f"census-l5 over {RUN}: {len(docs)} per-test IR documents")
    reach = {}                                          # unit → {stem: [rows]}
    for sec, path in docs:
        stem = os.path.basename(path)[:-5]
        for unit, plat, name, det in census_doc(path):
            reach.setdefault(unit, {}).setdefault(stem, []).append((plat, name, det))
    cells = json.load(open(CELLS))['cells'] if os.path.exists(CELLS) else {}
    allrows = [r for v in reach.pop('ALL-ios', {}).values() for r in v]
    from collections import Counter
    print("\n(ALL-ios) every iOS marker row the renderer paints, by source, natural/item direction and placement:")
    for k, n in sorted(Counter(r[2] for r in allrows).items()):
        print(f"    {n:5d}  {k}")
    print(f"    total {len(allrows)} marker rows; baked {sum(1 for r in allrows if r[2].startswith('baked'))}"
          f" (of which painted unchanged: {sum(1 for r in allrows if r[2].startswith('baked') and r[2].split()[1] in ('L/none/ltr', 'R/rtl'))})")
    for unit in sorted(reach):
        stems = reach[unit]
        n = sum(len(v) for v in stems.values())
        print(f"\n({unit}) {n} components in {len(stems)} documents")
        for stem, rs in sorted(stems.items()):
            print(f"  {stem}: {len(rs)}")
            for plat, name, det in rs[:40]:
                print(f"    {plat} {name} {det}")
            if len(rs) > 40:
                print(f"    … {len(rs) - 40} more")
    # cross-check against the plan's generated carriers
    lane = json.load(open(EXPECT))['lanes']['L5-counter-suffix-rows']
    print("\nCROSS-CHECK against expectations.json lanes['L5-counter-suffix-rows']")
    grew = shrank = 0
    for unit, plan in lane['revertUnits'].items():
        for plat in ('web', 'ios', 'android'):
            want = set(plan['captures'][plat])
            # a unit reaches captures only on the platform its name ends in; D (comment-only) reaches none
            have = set(reach.get(unit, {}).keys()) if unit.endswith(plat) else set()
            extra, missing = sorted(have - want), sorted(want - have)
            tag = 'identical' if not extra and not missing else 'DIFFERS'
            grew += len(extra); shrank += len(missing)
            print(f"  {unit:12s} {plat:8s} plan {len(want)} census {len(have)} {tag}" + (f" +{extra}" if extra else '') + (f" -{missing}" if missing else ''))
    union = {p: set() for p in ('web', 'ios', 'android')}
    for unit, stems in reach.items():
        for plat in union:
            if unit.endswith(plat) and not unit.startswith('U2-overlay'):
                union[plat] |= set(stems)
    for plat in ('web', 'ios', 'android'):
        want = set(lane['captureCarriers'][plat])
        print(f"  captureCarriers {plat:8s} plan {len(want)} census {len(union[plat])} " + ('identical' if want == union[plat] else f"DIFFERS +{sorted(union[plat]-want)} -{sorted(want-union[plat])}"))
    print(f"  wireCarriers plan {len(lane['wireCarriers'])} census 0 (no converter / extractor / bake file is touched) " + ('identical' if not lane['wireCarriers'] else 'DIFFERS'))
    print(f"\nCENSUS DELTA vs plan: grew {grew} · shrank {shrank} (unit capture sets)")
    for unit in ('U2-overlay-ios', 'U2-overlay-android'):
        print(f"  {unit}: {sum(len(v) for v in reach.get(unit, {}).values())} (overlay markers ending U+3001 — no unit reaches the overlay, reported for completeness)")


if __name__ == '__main__':
    main()
