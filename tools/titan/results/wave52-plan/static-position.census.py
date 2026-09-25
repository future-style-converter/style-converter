#!/usr/bin/env python3
"""wave52-plan / static-position -- read-only carrier census over the per-test IR.

Walks every tools/titan/runs/<run>/sections/<section>/per-test-ir/*.json document
and buckets each ABSOLUTE-positioned component by the shape of its slot.parent, so
a builder can see which cells a layout/position change can reach. Prints JSON to
stdout; writes nothing.

    python3 tools/titan/results/wave52-plan/static-position.census.py [run]   # run defaults to wave51-fix

Buckets (a doc appears once per bucket it matches):
  grid:abspos-child                              abspos whose slot.parent is Display GRID
  grid:abspos-child-noinset                      ... and no Top/Right/Bottom/Left/Inset* (static position applies)
  grid:noinset-UNPOSITIONED-parent_T1            ... parent has no Position -> Compose zero-flow-anchor path (target T1)
  grid:noinset-POSITIONED-parent                 ... parent is relative/absolute/fixed/sticky (grid container = CB)
  grid:noinset-POSITIONED-parent-with-placement  ... and the child carries GridRow*/GridColumn*/GridArea (css-grid-1 s9.2, 2nd sentence)
  flex:abspos-child / flex:abspos-child-noinset  same for Display FLEX parents
  flex:noinset-padded-or-bordered-parent_T2      ... parent declares Padding* or Border*Width (iOS padding-box anchor, target T2)
  flex:noinset-relies-on-align-items_T2b         ... parent AlignItems is positional and the child has no AlignSelf
  generic-align-self=<raw>                       abspos child carrying an unmapped Generic align-self (target T3)
"""
import collections
import glob
import json
import os
import sys

RUN = sys.argv[1] if len(sys.argv) > 1 else 'wave51-fix'
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'runs', RUN, 'sections'))
INSETS = {'Top', 'Right', 'Bottom', 'Left', 'InsetBlockStart', 'InsetBlockEnd', 'InsetInlineStart', 'InsetInlineEnd', 'Inset'}
POSITIONED = {'RELATIVE', 'ABSOLUTE', 'FIXED', 'STICKY'}


def props(component):
    return {p['type']: p['data'] for p in component.get('properties', [])}


def main():
    files = sorted(glob.glob(os.path.join(ROOT, '*', 'per-test-ir', '*.json')))
    carriers = collections.defaultdict(set)
    for path in files:
        with open(path) as fh:
            doc = json.load(fh)
        comps = doc.get('components', [])
        by_id = {c['id']: c for c in comps}
        section = os.path.basename(os.path.dirname(os.path.dirname(path)))
        key = section + '/' + os.path.basename(path)[:-5]
        for c in comps:
            P = props(c)
            if P.get('Position') != 'ABSOLUTE':
                continue
            parent = by_id.get((c.get('slot') or {}).get('parent'))
            if parent is None:
                continue
            PP = props(parent)
            no_inset = not (INSETS & set(P))
            placed = any(k.startswith('GridRow') or k.startswith('GridColumn') or k == 'GridArea' for k in P)
            if PP.get('Display') == 'GRID':
                carriers['grid:abspos-child'].add(key)
                if no_inset:
                    carriers['grid:abspos-child-noinset'].add(key)
                    if PP.get('Position') in POSITIONED:
                        carriers['grid:noinset-POSITIONED-parent'].add(key)
                        if placed:
                            carriers['grid:noinset-POSITIONED-parent-with-placement'].add(key)
                    else:
                        carriers['grid:noinset-UNPOSITIONED-parent_T1'].add(key)
            if PP.get('Display') == 'FLEX':
                carriers['flex:abspos-child'].add(key)
                if no_inset:
                    carriers['flex:abspos-child-noinset'].add(key)
                    padded = any(k.startswith('Padding') for k in PP) or any(k.startswith('Border') and k.endswith('Width') for k in PP)
                    if padded:
                        carriers['flex:noinset-padded-or-bordered-parent_T2'].add(key)
                    if PP.get('AlignItems') not in (None, 'STRETCH', 'NORMAL') and 'AlignSelf' not in P:
                        carriers['flex:noinset-relies-on-align-items_T2b'].add(key)
            generic = P.get('Generic')
            generics = [generic] if isinstance(generic, dict) else (generic if isinstance(generic, list) else [])
            for g in generics:
                if isinstance(g, dict) and g.get('propertyName') == 'align-self':
                    carriers['generic-align-self=' + str(g.get('rawValue'))].add(key)
    out = {
        'run': RUN,
        'docs': len(files),
        'counts': {k: len(v) for k, v in sorted(carriers.items())},
        'carriers': {k: sorted(v) for k, v in sorted(carriers.items())},
    }
    print(json.dumps(out, indent=1))


if __name__ == '__main__':
    main()
