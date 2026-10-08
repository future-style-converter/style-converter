#!/usr/bin/env python3
# tools/titan/results/wave54-table-body-cell/census.py — the lane's OWN wire-level blast-radius census (independent of the
# brief's compose-table-body-cell.census.mjs). Over every per-test IR document of a run it reads:
#   (a) the TableBodyForest trigger — a `meta.role: body-root` whose LAST `Display` leaf is TABLE / INLINE_TABLE
#       (TableBodyForest.TABLE_BODY), before the edge / run bails the Kotlin applies;
#   (b) every distinct `meta.role` value on the wire, to prove the new in-memory value `anonymous-table` never arrives
#       from the wire (so TableBodyForest.isAnonymous is true ONLY for the forest's synthetic boxes);
#   (c) which documents carry a box the renderer can route to Compose DisplayType.TABLE (declared Display TABLE /
#       INLINE_TABLE-folded-elsewhere excluded, UA `<table>` with no declared Display) — the radius of the seam's `chrome`
#       call, all of which get (today's stroke, hug=false) unless (a) fires.
# Usage: python3 census.py <run>   (default wave53-final)
import glob, json, os, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
files = sorted(glob.glob(os.path.join(ROOT, 'tools/titan/runs', run, 'sections', '*', 'per-test-ir', '*.json')))
roles, trig, table_docs = {}, [], []
def last_display(c):
    ds = [p['data'] for p in c.get('properties', []) if p.get('type') == 'Display' and isinstance(p.get('data'), str)]
    return ds[-1].upper() if ds else None
for f in files:
    d = json.load(open(f)); stem = os.path.basename(f)[:-5]
    comps = d.get('components', [])
    for c in comps:
        r = (c.get('meta') or {}).get('role')
        roles[r] = roles.get(r, 0) + 1
    if any((c.get('meta') or {}).get('role') == 'body-root' and last_display(c) in ('TABLE', 'INLINE_TABLE') for c in comps):
        trig.append(stem)
    if any(last_display(c) == 'TABLE' or (last_display(c) is None and ((c.get('meta') or {}).get('sourceTag') or '').lower() == 'table') for c in comps):
        table_docs.append(stem)
print(f'run {run}: {len(files)} per-test IR documents')
print('meta.role values on the wire:', json.dumps(dict(sorted(roles.items(), key=lambda kv: str(kv[0])))))
print('anonymous-table on the wire:', roles.get('anonymous-table', 0))
print(f'(a) forest trigger (body-root last Display TABLE/INLINE_TABLE): {len(trig)}', trig)
print(f'(c) documents with a declared-TABLE box or a UA <table> (the chrome call radius, upper bound): {len(table_docs)}')
