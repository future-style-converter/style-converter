#!/usr/bin/env python3
# tools/titan/results/wave55-plan/counter-suffix-rows.census.py — the wave-55 L5 (counter-suffix rows) corpus census.
#
# READ-ONLY over a run's per-test IR and manifests (no capture, no build). Answers, per unit of the lane brief
# counter-suffix-rows.md, "which components / documents / scored cells can this change reach?":
#   U1-ios  (marker base direction): every list-item marker STRING a native paints — the baked `meta.markerText` or, when
#           absent, the type the native table would expand — classified by the Unicode bidi class of its first strong
#           character (UAX #9 P2) and by the item's inherited `direction`. iOS's marker Text takes a first-strong base
#           direction; css-lists-3 §3.1.1 (`unicode-bidi: isolate`) gives the marker the ITEM's direction. The two
#           disagree exactly when (first strong is R/AL and the item is LTR) or (the item is RTL and the string holds a
#           neutral / weak character whose resolved level the base direction decides).
#   U2      (suffix gap, both natives): every marker string whose counter-style suffix carries NO trailing space —
#           U+3001 IDEOGRAPHIC COMMA (css-counter-styles-3 §6.1 cjk-decimal, §6.2 hiragana / katakana (-iroha), §6.4
#           cjk-earthly-branch / cjk-heavenly-stem, §7.1.1 japanese-*, §7.1.3 chinese) — split by list-style-position
#           (outside hangs through ListMarkerOutsideHang, inside takes the Row / HStack spacing).
#   U3-ios  (tabular-nums on a run): every component DECLARING `FontVariantNumeric` (not inherited on either native:
#           InheritedText.inheritedTypes / INHERITED_PROPERTY_TYPES do not list it) whose own text holds a digit.
#   D       (docs): every `<li>` under an inherited `direction: rtl` (the re-true's "corpus's only RTL list rows" claim).
#   Q       (queued, NOT a wave-55 unit): baked markers holding an ASCII digit — the reach of the UA `::marker
#           { font-variant-numeric: tabular-nums }` rule (css-lists-3 §3.1.1) neither native applies to its marker Text.
# For each reached document it prints the scored cells (scorer idiom: typeof ssim === number && !scoreExcluded;
# pass = wptPass === true), so the brief's carrier lists are pointers, not prose.
#
# Usage: python3 tools/titan/results/wave55-plan/counter-suffix-rows.census.py [run-id]   (default wave54-final)
import collections, glob, json, os, sys, unicodedata

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
RUN = sys.argv[1] if len(sys.argv) > 1 else 'wave54-final'
SECS = f'{ROOT}/tools/titan/runs/{RUN}/sections'
LIST_TAGS = {'ol', 'ul', 'menu', 'dir'}
# css-counter-styles-3 predefined styles whose `suffix` is "、" (U+3001) with no trailing space (verified against the
# CR's §6.1 / §6.2 / §6.4 / §7.1 text). Every other predefined suffix ends in U+0020 (". ", ", ", "/ ", " ").
IDEOGRAPHIC_COMMA_STYLES = {'cjk-decimal', 'hiragana', 'hiragana-iroha', 'katakana', 'katakana-iroha',
                            'cjk-earthly-branch', 'cjk-heavenly-stem', 'japanese-informal', 'japanese-formal',
                            'simp-chinese-informal', 'simp-chinese-formal', 'trad-chinese-informal',
                            'trad-chinese-formal', 'cjk-ideographic'}


def cells_of(sec):
    """{test-stem: {platform: 'P 0.9873'}} from the section manifest, scorer idiom (score-gate.mjs isScored/isPass)."""
    out = collections.defaultdict(dict)
    mp = f'{SECS}/{sec}/manifest.json'
    if not os.path.exists(mp):
        return out
    m = json.load(open(mp))
    for test, r in ((m.get('wpt') or {}).get('results') or {}).items():
        diffs = (r.get('browserRef') or {}).get('diffs') or {}
        for key, plat in (('web-ref', 'web'), ('ios-ref', 'ios'), ('android-ref', 'android')):
            x = diffs.get(key)
            if x and isinstance(x.get('ssim'), (int, float)) and not isinstance(x.get('ssim'), bool) and not x.get('scoreExcluded'):
                stem = 'wpt__' + test.split('/', 1)[1].rsplit('.', 1)[0].replace('/', '__')
                out[stem][plat] = f"{'P' if x.get('wptPass') is True else 'f'} {x['ssim']}"
    return out


def first_strong(s):
    """UAX #9 P2: the bidi class of the first L / R / AL character, or None (P3 then gives level 0, LTR)."""
    for ch in s:
        b = unicodedata.bidirectional(ch)
        if b in ('L', 'R', 'AL'):
            return b
    return None


def prop(c, t):
    for p in c.get('properties', []):
        if p['type'] == t:
            return p['data']
    return None


def inherited(c, byid, t):
    """Nearest declaration of an inherited property along the slot.parent chain (own first)."""
    while c is not None:
        v = prop(c, t)
        if v is not None:
            return v
        c = byid.get((c.get('slot') or {}).get('parent'))
    return None


def position_of(li, byid):
    """css-lists-3 §3.5 list-style-position, inherited; the `ListStyle` shorthand counts when it says inside."""
    c = li
    while c is not None:
        v = prop(c, 'ListStylePosition')
        if v is not None:
            return 'inside' if 'INSIDE' in json.dumps(v).upper() else 'outside'
        s = prop(c, 'ListStyle')
        if s is not None and 'inside' in json.dumps(s).lower():
            return 'inside'
        c = byid.get((c.get('slot') or {}).get('parent'))
    return 'outside'


markers, comma, rtl_items, tnum, unbaked_comma = [], [], [], [], []
docs_cells = {}
for sec in sorted(os.listdir(SECS)):
    cells = cells_of(sec)
    for f in sorted(glob.glob(f'{SECS}/{sec}/per-test-ir/*.json')):
        stem = os.path.basename(f)[:-5]
        d = json.load(open(f))
        comps = d['components']
        byid = {c['id']: c for c in comps}
        for c in comps:
            meta = c.get('meta') or {}
            parent = byid.get((c.get('slot') or {}).get('parent'))
            ptag = ((parent or {}).get('meta') or {}).get('sourceTag', '').lower()
            direction = inherited(c, byid, 'Direction')
            is_rtl = isinstance(direction, str) and direction.upper() == 'RTL'
            # (D) every <li> under an inherited RTL direction.
            if meta.get('sourceTag', '').lower() == 'li' and is_rtl:
                rtl_items.append((sec, stem, c['id'], ptag, prop(c, 'Position'), meta.get('markerText')))
            # (U3) declared FontVariantNumeric on a component whose own text holds a digit.
            fvn = prop(c, 'FontVariantNumeric')
            if fvn is not None:
                tnum.append((sec, stem, c['id'], json.dumps(fvn), repr(c.get('text')),
                             any(ch.isdigit() for ch in (c.get('text') or ''))))
            # Markers the natives paint: an in-flow <li> child of a list container (the renderer's marker branch).
            if meta.get('sourceTag', '').lower() != 'li' or ptag not in LIST_TAGS:
                continue
            lst = inherited(c, byid, 'ListStyleType')
            lst = lst if isinstance(lst, str) else None
            if (lst or '').lower() == 'none':
                continue
            pos = position_of(c, byid)
            mt = meta.get('markerText')
            if mt is None:
                if (lst or '').lower() in IDEOGRAPHIC_COMMA_STYLES:
                    unbaked_comma.append((sec, stem, c['id'], lst, pos))
                continue
            fs = first_strong(mt)
            markers.append((sec, stem, c['id'], mt, fs, 'rtl' if is_rtl else 'ltr', pos, lst))
            if mt.endswith('、'):
                comma.append((sec, stem, c['id'], mt, pos, lst))
        docs_cells[stem] = cells.get(stem, {})


def cellline(stem):
    c = docs_cells.get(stem, {})
    return ' '.join(f'{p} {c[p]}' for p in ('web', 'ios', 'android') if p in c) or '(unscored)'


def by_doc(rows, idx=1):
    out = collections.OrderedDict()
    for r in rows:
        out.setdefault((r[0], r[idx]), []).append(r)
    return out


print(f'counter-suffix-rows census over {RUN}: {sum(1 for _ in glob.glob(f"{SECS}/*/per-test-ir/*.json"))} per-test IR documents')
print(f'\n(A) baked list-item markers (meta.markerText on an <li> under ol/ul/menu/dir, list-style-type not none): {len(markers)}'
      f' in {len(by_doc(markers))} documents')
cls = collections.Counter((m[4], m[5]) for m in markers)
print('    by (first strong bidi class, item direction): ' + ', '.join(f'{k[0] or "none"}/{k[1]} {v}' for k, v in sorted(cls.items(), key=str)))
print('\n(U1-ios) markers whose iOS first-strong base direction disagrees with the item direction (css-lists-3 §3.1.1):')
u1 = [m for m in markers if (m[4] in ('R', 'AL') and m[5] == 'ltr') or (m[5] == 'rtl')]
for (sec, stem), rows in by_doc(u1).items():
    print(f'    {sec}/{stem}: ' + ', '.join(f'{r[2].rsplit("-", 1)[0]} {r[3]!r} ({r[4]}, {r[5]}, {r[6]})' for r in rows))
    print(f'        cells: {cellline(stem)}')
print(f'    total: {len(u1)} markers in {len(by_doc(u1))} documents')
print('\n(U2) markers ending in U+3001 IDEOGRAPHIC COMMA (suffix without trailing space), by list-style-position:')
print('    ' + ', '.join(f'{k} {v}' for k, v in sorted(collections.Counter(c[4] for c in comma).items())))
for (sec, stem), rows in by_doc(comma).items():
    pos = collections.Counter(r[4] for r in rows)
    print(f'    {sec}/{stem}: {len(rows)} markers ({", ".join(f"{k} {v}" for k, v in sorted(pos.items()))}; '
          f'type {sorted({r[5] for r in rows})}; e.g. {rows[0][3]!r})')
    print(f'        cells: {cellline(stem)}')
print(f'    total: {len(comma)} markers in {len(by_doc(comma))} documents')
print(f'\n(U2-table) <li> under a U+3001-suffix list-style-type WITHOUT a baked marker (the native tables would paint "." — '
      f'ListMarkerText.swift:45-49 / StyleListApplier.getMarker): {len(unbaked_comma)}')
for r in unbaked_comma:
    print(f'    {r[0]}/{r[1]} {r[2]} {r[3]} {r[4]}')
print(f'\n(U3-ios) components DECLARING FontVariantNumeric: {len(tnum)} in {len(by_doc(tnum))} documents')
for r in tnum:
    print(f'    {r[0]}/{r[1]} {r[2]} {r[3]} text={r[4]} digit={r[5]}')
for (sec, stem) in by_doc(tnum):
    print(f'    cells {sec}/{stem}: {cellline(stem)}')
qd = [m for m in markers if any(ch in '0123456789' for ch in m[3])]
print(f'\n(Q, queued) baked markers holding an ASCII digit (UA ::marker tabular-nums reach on both natives): {len(qd)} in '
      f'{len(by_doc(qd))} documents: ' + ', '.join(f'{sec}/{stem.split("__", 2)[-1]}' for (sec, stem) in by_doc(qd)))
print(f'\n(D) <li> under an inherited direction: rtl: {len(rtl_items)} in {len(by_doc(rtl_items))} documents')
for (sec, stem), rows in by_doc(rtl_items).items():
    print(f'    {sec}/{stem}: {len(rows)} <li> (parent tags {sorted({r[3] for r in rows})}; Position {sorted({str(r[4]) for r in rows})}; '
          f'markerText {sorted({str(r[5]) for r in rows})})')
