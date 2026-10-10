#!/usr/bin/env python3
# tools/titan/results/wave54-table-body-cell/gen-pins.py — writes runtimes/compose/src/test/…/table/TableCellHugTest.kt
# from TableCellHugTest.kt.tmpl, splicing in the per-test IR components BYTE-VERBATIM from the wave53-final run (each
# component is the exact substring of the per-test-ir file; asserted). Why a generator: the pins must use the verbatim
# payloads (PLAN §0), and a skeptic can re-run this and `cmp` the result. Usage (repo root): python3 gen-pins.py
import json, os
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
RUN = os.path.join(ROOT, 'tools/titan/runs/wave53-final/sections')
DOCS = {
    '@@S006@@': 'CSS2/per-test-ir/wpt__CSS2__css21-errata__s-11-1-1b-006.json',
    '@@PCT003@@': 'css-tables/per-test-ir/wpt__css-tables__height-distribution__percentage-sizing-of-table-cell-children-003.json',
    '@@BEC001@@': 'css-tables/per-test-ir/wpt__css-tables__baseline-empty-cell-001.json',
    '@@CC004@@': 'css-contain/per-test-ir/wpt__css-contain__contain-content-004.json',
}
src = open(os.path.join(os.path.dirname(__file__), 'TableCellHugTest.kt.tmpl'), encoding='utf-8').read()
for key, rel in DOCS.items():
    raw = open(os.path.join(RUN, rel), encoding='utf-8').read()
    comps = json.loads(raw)['components']
    parts = []
    for c in comps:
        s = json.dumps(c, separators=(',', ':'), ensure_ascii=False)
        assert s in raw, f'{rel}: component {c["id"]} is not a verbatim substring'   # byte-verbatim
        assert '$' not in s and '"""' not in s, f'{rel}: unsafe in a Kotlin raw string'
        parts.append('        ' + s)
    # joined back exactly as the file joins them (','), one component per line for review
    src = src.replace(key, ',\n'.join(parts))
out = os.path.join(ROOT, 'runtimes/compose/src/test/java/com/styleconverter/runtime/table/TableCellHugTest.kt')
open(out, 'w', encoding='utf-8').write(src)
print('wrote', os.path.relpath(out, ROOT), sum(1 for _ in open(out, encoding='utf-8')), 'lines')
