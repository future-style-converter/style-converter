#!/usr/bin/env python3
# skeptic-census.py — the L2 SKEPTIC's own blast-radius census (independent of census.py / census-kotlin.py):
# reads every per-test IR document of a run (listing <section>/per-test-ir/ directly, never walking node_modules)
# and reports (1) the TableBodyForest trigger set — body-root whose LAST `Display` primitive is TABLE/INLINE_TABLE —
# with each bail condition evaluated separately, (2) any document whose body-root expresses a table display some
# OTHER way (Generic/raw value: the trigger cannot see it), (3) meta.role values on the wire (the marker must be absent),
# (4) roots that are tables but are not the body (the forest never touches them). Usage: python3 skeptic-census.py <run>…
import json, os, sys, glob, hashlib, collections
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
def last_display(c):
    v = None
    for p in c.get('properties', []):
        if p.get('type') == 'Display': v = p.get('data')
    return v.upper() if isinstance(v, str) else (None if v is None else ('NONPRIM:' + json.dumps(v)))
def zero_px(d):
    if not isinstance(d, dict): return False
    px = d.get('px')
    if px is None and isinstance(d.get('original'), dict): px = d['original'].get('px')
    return px == 0 or px == 0.0
def paints_edges(c):
    return any((p['type'].startswith('Padding') or (p['type'].startswith('Border') and p['type'].endswith('Width'))) and not zero_px(p.get('data'))
               for p in c.get('properties', []))
for run in sys.argv[1:]:
    secs = sorted(glob.glob(os.path.join(ROOT, 'tools/titan/runs', run, 'sections', '*', 'per-test-ir')))
    n = 0; roles = collections.Counter(); trig = []; generic_table = []; other_table_roots = []; marker = []
    h = hashlib.sha256()
    for sd in secs:
        for f in sorted(os.listdir(sd)):
            if not f.endswith('.json'): continue
            raw = open(os.path.join(sd, f), 'rb').read(); h.update(f.encode()); h.update(raw)
            d = json.loads(raw); n += 1
            comps = d.get('components', [])
            for c in comps:
                r = (c.get('meta') or {}).get('role'); roles[r] += 1
                if r == 'anonymous-table': marker.append(f)
            roots = [c for c in comps if not (c.get('slot') or {}).get('parent')]
            bi = next((i for i, c in enumerate(roots) if (c.get('meta') or {}).get('role') == 'body-root'), -1)
            if bi < 0: continue
            body = roots[bi]; disp = last_display(body)
            for p in body.get('properties', []):
                if p.get('type') == 'Generic' and isinstance(p.get('data'), dict) and p['data'].get('propertyName') == 'display' \
                        and 'table' in str(p['data'].get('rawValue', '')): generic_table.append((f, p['data'].get('rawValue')))
            for c in roots[bi + 1:]:
                if last_display(c) in ('TABLE', 'INLINE_TABLE'): other_table_roots.append(f)
            if disp in ('TABLE', 'INLINE_TABLE') or (disp or '').startswith('NONPRIM'):
                after = roots[bi + 1:]
                trig.append((os.path.basename(os.path.dirname(sd)), f, disp, paints_edges(body), len(after),
                             [last_display(c) for c in after], [c['id'] for c in after]))
    print(f'== {run}: sections {len(secs)} documents {n} corpus-sha256 {h.hexdigest()[:16]}')
    print(f'   meta.role on the wire: {dict(roles)}  | "anonymous-table" on the wire: {len(marker)}')
    print(f'   body-root last Display in TABLE/INLINE_TABLE (or non-primitive): {len(trig)}')
    for t in trig: print('     ', t)
    print(f'   body-root with a Generic display containing "table" (trigger blind to it): {len(generic_table)} {generic_table}')
    print(f'   non-body ROOTS after the body that are tables (the forest never rewrites them): {len(set(other_table_roots))}')
