#!/usr/bin/env python3
# wave-53 plan, family display-table-body — the wire census behind the brief
# (display-table-body.md §6). Reads ONLY per-test IR JSON (no images), so it
# is safe on a host running a gate.
#
# Usage: python3 display-table-body.census.py [run-id]   (default wave52-ship)
# Writes display-table-body.census.json beside this file.
import collections, glob, json, os, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
RUN = sys.argv[1] if len(sys.argv) > 1 else 'wave52-ship'
FILES = sorted(glob.glob(f'{ROOT}/tools/titan/runs/{RUN}/sections/*/per-test-ir/*.json'))

# css-display-3 table-internal display types (CSS 2.1 §8.3: margins do not apply).
TABLE_INTERNAL = {'TABLE_CELL', 'TABLE_ROW', 'TABLE_ROW_GROUP', 'TABLE_HEADER_GROUP',
                  'TABLE_FOOTER_GROUP', 'TABLE_COLUMN', 'TABLE_COLUMN_GROUP'}
# HTML UA table-internal tags (the natives' uaRoleOf channel) for boxes with no Display.
TABLE_TAGS = {'td', 'th', 'tr', 'tbody', 'thead', 'tfoot', 'col', 'colgroup'}
MARGINS = {'MarginTop', 'MarginBottom', 'MarginBlockStart', 'MarginBlockEnd',
           'MarginLeft', 'MarginRight', 'MarginInlineStart', 'MarginInlineEnd'}


def display_of(c):
    # Last Display wins (wire order is cascade order).
    ds = [p['data'] for p in c.get('properties', []) if p['type'] == 'Display']
    return ds[-1].upper().replace('-', '_') if ds and isinstance(ds[-1], str) else None


def nonzero(p):
    d = p['data']
    return not (isinstance(d, dict) and d.get('px') == 0)


body_display = collections.Counter()
body_display_docs = collections.defaultdict(list)
table_internal_roots = []
nonzero_margin_table_internal = []
carriers = []
for f in FILES:
    stem = os.path.basename(f)[:-5]
    comps = json.load(open(f)).get('components', [])
    for c in comps:
        role = (c.get('meta') or {}).get('role')
        disp = display_of(c)
        tag = (c.get('meta') or {}).get('sourceTag')
        if role == 'body-root':
            body_display[str(disp)] += 1
            body_display_docs[str(disp)].append(stem)
            # THE TRIGGER of the proposed fix: a table body.
            if disp in ('TABLE', 'INLINE_TABLE'):
                carriers.append(stem)
        ti = disp in TABLE_INTERNAL or (disp is None and tag in TABLE_TAGS)
        if not ti:
            continue
        is_root = not c.get('slot')
        if is_root:
            table_internal_roots.append({'doc': stem, 'id': c['id'], 'display': disp, 'role': role})
        nz = [{p['type']: p['data']} for p in c.get('properties', []) if p['type'] in MARGINS and nonzero(p)]
        if nz:
            nonzero_margin_table_internal.append({'doc': stem, 'id': c['id'], 'display': disp,
                                                  'root': is_root, 'role': role, 'margins': nz})

out = {
    '_what': 'wave-53 display-table-body wire census over ' + RUN + ' per-test IR',
    'documents': len(FILES),
    'bodyRootDisplay': {k: {'n': v, 'docs': body_display_docs[k] if k != 'None' else '(omitted)'}
                        for k, v in body_display.most_common()},
    'tableInternalRoots': table_internal_roots,
    'nonzeroMarginOnTableInternalBox': nonzero_margin_table_internal,
    'carrierPredicate': "a per-test IR doc whose meta.role=='body-root' component's last Display is TABLE or INLINE_TABLE",
    'carriers': carriers,
    'carrierCells': [f'{c}|{p}' for c in carriers for p in ('web', 'ios', 'android')],
}
json.dump(out, open(os.path.join(os.path.dirname(__file__), 'display-table-body.census.json'), 'w'), indent=1)
print(json.dumps({k: out[k] for k in ('documents', 'carriers')}),
      len(table_internal_roots), 'table-internal roots;',
      len(nonzero_margin_table_internal), 'table-internal boxes with a nonzero margin')
