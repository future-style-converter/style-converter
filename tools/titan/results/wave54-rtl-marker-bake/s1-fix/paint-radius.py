#!/usr/bin/env python3
# S1 S6 fix-pass radius census (wave-54 L1, rtl-marker-bake): for every list
# item the BIDI BAKE turned into a box (meta.sourceTag li + Position/Left/Top,
# under a bake ROOT = the nearest ancestor carrying rootProperties' four
# Direction/UnicodeBidi/TextAlign/BoxSizing keys), the paint effects the marker
# decline now reads (bidi-marker-paint.mjs) on the item→root chain (item and
# every box BELOW the root; the root and above still paint over root-owned
# runs). Reads the [W-L3] trees (pre = wave54-open's wire, post = the landed
# wire) — static, no device. A hit would mean the decline moves a corpus wire.
import json, glob, sys, os
# IR property types of the paint effects (one IR type per CSS longhand).
PAINT = {'Opacity', 'Transform', 'Filter', 'ClipPath', 'Overflow', 'OverflowX', 'OverflowY',
         'Visibility', 'TextShadow', 'MixBlendMode', 'MaskImage', 'Mask'}
# rootProperties' signature (bidi-bake.mjs): every bake root carries all four.
ROOT = {'Direction', 'UnicodeBidi', 'TextAlign', 'BoxSizing'}
root_dir = sys.argv[1]
for side in ('pre', 'post'):
    docs = sorted(glob.glob(os.path.join(root_dir, side, '*', 'per-test-ir', '*.json')))
    items, hits, per_doc = 0, 0, {}
    for p in docs:
        comps = json.load(open(p))['components']
        by_id = {c['id']: c for c in comps}
        types = lambda c: {q['type'] for q in c.get('properties', [])}
        for c in comps:
            if (c.get('meta') or {}).get('sourceTag') != 'li' or not {'Position', 'Left', 'Top'} <= types(c):
                continue
            # Climb to the nearest bake-root-shaped ancestor, collecting the chain below it.
            chain, cur = [c], by_id.get((c.get('slot') or {}).get('parent'))
            while cur is not None and not ROOT <= types(cur):
                chain.append(cur)
                cur = by_id.get((cur.get('slot') or {}).get('parent'))
            if cur is None:
                continue  # not inside a bidi-bake root: the marker bake never sees it
            items += 1
            per_doc[os.path.basename(p)] = per_doc.get(os.path.basename(p), 0) + 1
            found = [(b['name'], [(q['type'], q['data']) for q in b['properties'] if q['type'] in PAINT]) for b in chain]
            found = [f for f in found if f[1]]
            hits += bool(found)
            print(f'{side} {"HIT  " if found else "clean"} {c["name"]} (root {cur["name"]}, chain {len(chain)}): {found or "-"}')
    print(f'{side}: {len(docs)} docs · {items} bidi-baked li boxes in {len(per_doc)} docs {per_doc} · {hits} with a paint effect on the item→root chain')
