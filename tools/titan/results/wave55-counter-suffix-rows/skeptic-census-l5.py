#!/usr/bin/env python3
# skeptic-census-l5.py — wave-55 L5 SKEPTIC's own blast-radius census (independent of census-l5.py and of the brief's
# counter-suffix-rows.census.py; written from the renderer sources, not from either script).
#
# Model (read from the code at 611c9f93 + the L5 patches):
#  * A native marker row exists for child c of container p when
#      iOS     ComponentRenderer.swift :3837-3875 — meta.markerText (baked, ANY child) else, for an <li> whose own display
#              keeps the marker, ListMarkerText(type) under a list container (ListMarkerResolver.uaDefault: ol / ul / menu / dir);
#      Compose ComponentRenderer.kt :3549-3610 — only an <li> under a list parent (liKeepsItsMarker), baked else table.
#    Both runtimes render absolutely / fixed positioned children elsewhere (Compose RenderAbsoluteChild) — reported as a flag,
#    and counted in BOTH directions (in-flow only AND all), so an out-of-flow carrier can never be hidden.
#  * Type = the child's own ListStyleType, else the parent's own, else the parent tag's UA default (ListStyleUaRule replaces
#    an ancestor-inherited type on a list container); the ListStyle shorthand is honoured the same way.
#  * Position = the nearest ancestor-or-self ListStylePosition (inherited, css-lists-3 §3.1), else outside.
#  * Item direction = the nearest ancestor-or-self Direction (InheritedText carries "Direction"; ListStyleUaRule.isRtl).
#  * U1-ios reach: ListMarkerBidi.painted(s, itemIsRTL) != s (re-implemented here: first strong LETTER in the default R/AL
#    blocks, isolate runs skipped).  U2 reach: marker string's last scalar is U+3001; split by placement (outside hang /
#    inside row / inside overlay = no gap).  U3-ios reach: a component declaring FontVariantNumeric with TABULAR_NUMS, plus
#    every component whose text is painted with such a component's TextConfig (its anonymous runs: none declare one).
#  * Native tables: neither runtime's table emits U+3001 (grep '3001\|、' over runtimes/*/src|Sources at 611c9f93 = 0 hits
#    outside the L5 files); the iOS table emits Hebrew letters for `hebrew` (ListMarkerText.swift:44) — counted.
#
# Usage: python3 skeptic-census-l5.py <run-id>   (reads tools/titan/runs/<run>/sections/*/per-test-ir/*.json)
import collections
import glob
import json
import os
import sys
import unicodedata

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', '..'))
RUN = sys.argv[1] if len(sys.argv) > 1 else 'wave54-final'
LIST_UA = {'ol': 'decimal', 'ul': 'disc', 'menu': 'disc', 'dir': 'disc'}
TABLE_TYPES = {'disc', 'circle', 'square', 'none', 'decimal', 'decimal-leading-zero', 'lower-alpha', 'upper-alpha',
               'lower-latin', 'upper-latin', 'lower-roman', 'upper-roman', 'lower-greek', 'upper-greek', 'armenian',
               'upper-armenian', 'lower-armenian', 'georgian', 'hebrew', 'cjk-decimal', 'hiragana', 'katakana',
               'hiragana-iroha', 'katakana-iroha', 'korean-hangul-formal'}


def kw(data):
    """The wire keyword of a property value (string or {"keyword": ...} / {"original": ...} shapes)."""
    if isinstance(data, str):
        return data
    if isinstance(data, dict):
        for k in ('keyword', 'value', 'original'):
            if isinstance(data.get(k), str):
                return data[k]
    return None


def prop(c, t):
    """Last declaration of type t on component c (last wins, as the runtimes' folds)."""
    v = None
    for p in c.get('properties', []) or []:
        if p.get('type') == t:
            v = p.get('data')
    return v


def is_rtl_letter(ch):
    """First-strong approximation: a LETTER whose code point lies in a default R/AL block (UAX #9 DerivedBidiClass)."""
    v = ord(ch)
    return (0x0590 <= v <= 0x08FF or 0xFB1D <= v <= 0xFDFF or 0xFE70 <= v <= 0xFEFF or
            0x10800 <= v <= 0x10FFF or 0x1E800 <= v <= 0x1EFFF)


def natural_rtl(s):
    """UAX #9 P2/P3 with isolate runs skipped, letters only (ListMarkerBidi's stated approximation)."""
    depth = 0
    for ch in s:
        v = ord(ch)
        if v in (0x2066, 0x2067, 0x2068):
            depth += 1
            continue
        if v == 0x2069:
            depth = max(0, depth - 1)
            continue
        if depth:
            continue
        if v == 0x200E:
            return False
        if v in (0x200F, 0x061C):
            return True
        if unicodedata.category(ch).startswith('L'):
            return is_rtl_letter(ch)
    return False


def bidi_class_rtl(s):
    """Cross-check with the REAL Bidi_Class (unicodedata.bidirectional): first L / R / AL, isolates skipped."""
    depth = 0
    for ch in s:
        v = ord(ch)
        if v in (0x2066, 0x2067, 0x2068):
            depth += 1
            continue
        if v == 0x2069:
            depth = max(0, depth - 1)
            continue
        if depth:
            continue
        b = unicodedata.bidirectional(ch)
        if b == 'L':
            return False
        if b in ('R', 'AL'):
            return True
    return False


def table_marker(t):
    """Does the native table give a natural-RTL marker / a U+3001-ended marker for type t (index-independent here)?"""
    return {'rtl': t == 'hebrew', 'ideo': False}


per_unit = collections.defaultdict(lambda: collections.defaultdict(set))   # unit -> platform -> {doc}
rows = collections.Counter()
detail = collections.defaultdict(list)
bidi_disagree = []
docs = sorted(glob.glob(f'{ROOT}/tools/titan/runs/{RUN}/sections/*/per-test-ir/*.json'))
for f in docs:
    stem = os.path.basename(f)[:-5]
    comps = json.load(open(f))['components']
    by_id = {c['id']: c for c in comps}
    parent = {c['id']: (c.get('slot') or {}).get('parent') for c in comps}

    def up(cid):
        """Ancestor-or-self chain, nearest first."""
        while cid is not None:
            yield by_id[cid]
            cid = parent.get(cid)

    def nearest(cid, t):
        for a in up(cid):
            v = prop(a, t)
            if v is not None:
                return v
        return None

    for c in comps:
        # U3: any declared FontVariantNumeric carrying a tabular keyword.
        fvn = prop(c, 'FontVariantNumeric')
        if fvn is not None and 'tabular' in json.dumps(fvn).lower():
            per_unit['U3-ios']['ios'].add(stem)
            detail['U3-ios'].append(f'{stem} {c["id"]} text={c.get("text")!r} fvn={fvn}')
        pid = parent[c['id']]
        if pid is None:
            continue
        p = by_id[pid]
        meta = c.get('meta') or {}
        tag = (meta.get('sourceTag') or '').lower()
        ptag = ((p.get('meta') or {}).get('sourceTag') or '').lower()
        baked = meta.get('markerText')
        disp = kw(prop(c, 'Display'))
        keeps = disp is None or 'list' in str(disp).lower() or 'LIST' in str(disp)
        pos_kind = kw(prop(c, 'Position'))
        oof = str(pos_kind).upper() in ('ABSOLUTE', 'FIXED')
        # Type fold (own > parent own > UA default of the parent tag).
        t = None
        for src in (c, p):
            v = kw(prop(src, 'ListStyleType'))
            if v is not None and v.lower().replace('_', '-') in TABLE_TYPES:
                t = v.lower().replace('_', '-')
                break
        if t is None:
            t = LIST_UA.get(ptag)
        position = (kw(nearest(c['id'], 'ListStylePosition')) or 'outside').lower()
        rtl_item = str(kw(nearest(c['id'], 'Direction')) or '').upper() == 'RTL'
        has_text = bool(c.get('text')) or any(parent.get(x['id']) == c['id'] for x in comps)
        for plat in ('ios', 'android'):
            if plat == 'ios':
                if baked is not None:
                    s, src = baked, 'baked'
                elif tag == 'li' and keeps and ptag in LIST_UA and t not in (None, 'none'):
                    s, src = None, 'table:' + t
                else:
                    continue
            else:
                if not (tag == 'li' and ptag in LIST_UA and (baked is not None or keeps)):
                    continue
                if baked is not None:
                    s, src = baked, 'baked'
                elif t not in (None, 'none'):
                    s, src = None, 'table:' + t
                else:
                    continue
            if s == '':
                continue
            flow = 'oof' if oof else 'flow'
            rows[(plat, src.split(':')[0], flow)] += 1
            ends3001 = s is not None and s[-1:] == '、'
            if s is not None and natural_rtl(s) != bidi_class_rtl(s):
                bidi_disagree.append(f'{stem} {c["id"]} {s!r}')
            nat = natural_rtl(s) if s is not None else table_marker(t)['rtl']
            if plat == 'ios' and nat != rtl_item:
                per_unit['U1-ios']['ios'].add(stem)
                detail['U1-ios'].append(f'{stem} {c["id"]} {src} {s!r} item_rtl={rtl_item} natural_rtl={nat} {flow} pos={position}')
            if ends3001:
                if position == 'outside':
                    unit = 'U2o'
                elif has_text:
                    unit = 'U2i'
                else:
                    unit = 'U2-overlay(no gap)'
                per_unit[f'{unit}-{plat}'][plat].add(stem)
                detail[f'{unit}-{plat}'].append(f'{stem} {c["id"]} {s!r} {flow} pos={position} text={c.get("text")!r}')

print(f'# skeptic-census-l5.py {RUN}: {len(docs)} per-test IR documents')
print('# marker rows by (platform, source, flow):', dict(sorted(rows.items())))
print('# letters-only first-strong vs real Bidi_Class disagreements on baked markers:', len(bidi_disagree))
for x in bidi_disagree[:20]:
    print('#   ', x)
for unit in sorted(detail):
    docs_u = {d.split(' ')[0] for d in detail[unit]}
    print(f'== {unit}: {len(detail[unit])} rows / {len(docs_u)} documents')
    for line in detail[unit]:
        print('   ', line)
print('== capture sets per unit and platform')
for unit in sorted(per_unit):
    for plat, ss in sorted(per_unit[unit].items()):
        print(f'   {unit:22} {plat:8} {sorted(ss)}')
# Cross-check against the plan.
exp = json.load(open(f'{ROOT}/tools/titan/results/wave55-plan/expectations.json'))['lanes']['L5-counter-suffix-rows']
plan = {}
for u, d in exp['revertUnits'].items():
    for plat, ss in d['captures'].items():
        plan[(u, plat)] = set(ss)
mine = collections.defaultdict(set)
for unit, pl in per_unit.items():
    key = unit.replace('U2-overlay(no gap)', 'overlay')
    for plat, ss in pl.items():
        mine[(key if key != 'U1-ios' else 'U1-ios', plat)] |= {'wpt__' + s.split('wpt__', 1)[-1] if not s.startswith('wpt__') else s for s in ss}
print('== cross-check vs expectations.json revertUnits[*].captures (mine − plan = UNDER-REPORTED by the plan)')
for (u, plat), ss in sorted(plan.items()):
    m = mine.get((u, plat), set())
    print(f'   {u:12} {plat:8} plan {len(ss)} mine {len(m)} under-reported {sorted(m - ss)} over {sorted(ss - m)}')
for (u, plat), m in sorted(mine.items()):
    if (u, plat) not in plan and m:
        print(f'   {u:12} {plat:8} NOT A PLAN UNIT/PLATFORM: {sorted(m)}')
union = collections.defaultdict(set)
for (u, plat), m in mine.items():
    if not u.startswith('overlay'):
        union[plat] |= m
cc = exp['captureCarriers']
for plat in ('web', 'ios', 'android'):
    print(f'   captureCarriers {plat:8} plan {len(cc[plat])} mine {len(union[plat])} under-reported {sorted(union[plat] - set(cc[plat]))} over {sorted(set(cc[plat]) - union[plat])}')
print('   wireCarriers plan', exp['wireCarriers'], '(no L5 path is on the extract route: mine [])')
