#!/usr/bin/env python3
# tools/titan/results/wave53-plan/rtl-marker-bake.padding-census.py
#
# The census behind PLAN.md §2 L1 hunk P's NON-ZERO guard (plan-skeptic must-fix 1): which bidi-bake roots carry a
# `padding*` key, which of those are non-zero, and where the keys sit in the root's property order. A root whose
# padding is all zero must be left untouched, because applyBidiBakePlan merges with Object.assign
# (tools/titan/bidi-bake.mjs:957): deleting four longhands and appending `padding` reorders the IR property list and
# changes that per-test IR document's bytes.
#
# Reads, by default, the FROZEN copy of the 17 bidiBaked fixtures beside this file (bidi-baked-fixtures/, with
# PROVENANCE.txt naming the run that extracted each one; plan-skeptic round 2, nit 12). The live fixtures/wpt/ is
# gitignored and rewritten by every gate, so a census over it answers for whatever the last gate extracted; pass a
# directory (e.g. fixtures/wpt) as the only argument to census a live extraction instead, and name its run in the note.
# A bake root is a component whose `_lossyReasons` holds 'baked-bidi-visual-order'. Pure JSON; nothing is run.
#
# Usage: python3 tools/titan/results/wave53-plan/rtl-marker-bake.padding-census.py [fixtures-dir]
# Expected (wave52-ship / wave53-open extraction): bidiBaked fixtures 17 · any padding key 14 roots in 7 docs ·
# NON-ZERO 6 roots in 4 docs (counter-suffix 2, anchor-center-safe-rtl 2, bidi-lines-001 1, bidi-lines-002 1) ·
# zero-padding roots 8 (dir-style-02a 6, dir-selector-change-003 1, -004 1), none with a content-box/overflow guard.
import glob, json, os, re, sys
from collections import Counter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
# The fixture tree to census: the frozen snapshot by default, or the directory given (relative to the repo root or absolute).
SRC = os.path.join(ROOT, sys.argv[1]) if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), 'bidi-baked-fixtures')
STAMP = 'baked-bidi-visual-order'                                # BIDI_BAKE_LOSSY_REASON, bidi-bake.mjs:191
GUARD_KEYS = ('overflow', 'overflow-x', 'overflow-y', 'background-clip', 'background-origin')


def nonzero(value):
    # Any side of a padding declaration other than a literal zero (with or without a unit) counts as non-zero.
    return any(re.sub(r'(px|em|rem|ch|%)$', '', tok) not in ('0', '0.0') for tok in str(value).split())


def walk(cmp, visit):
    # Fixtures nest components through `children` maps (the pre-converter envelope).
    visit(cmp)
    for child in (cmp.get('children') or {}).values():
        walk(child, visit)


baked, any_key, non_zero, rows = 0, Counter(), Counter(), []
for path in sorted(glob.glob(f'{SRC}/**/*.json', recursive=True)):
    if path.endswith('__ref.json') or '/_section-' in path:
        continue
    try:
        fixture = json.load(open(path, encoding='utf-8'))
    except (OSError, ValueError):
        continue
    if not (fixture.get('_wpt') or {}).get('bidiBaked'):
        continue
    baked += 1
    stem = os.path.relpath(path, SRC)[:-5]

    def visit(cmp):
        if STAMP not in (cmp.get('_lossyReasons') or []):
            return
        props = cmp.get('properties') or {}
        keys = list(props)
        pad = [(k, v) for k, v in props.items() if k.startswith('padding')]
        if not pad:
            return
        nz = any(nonzero(v) for _, v in pad)
        any_key[stem] += 1
        non_zero[stem] += nz
        guard = {k: props[k] for k in GUARD_KEYS if k in props}
        rows.append((stem, nz, pad, [keys.index(k) for k, _ in pad], len(keys), guard))

    for top in (fixture.get('components') or {}).values():
        walk(top, visit)

print(f'fixtures dir {os.path.relpath(SRC, ROOT)}')
print(f'bidiBaked fixtures {baked}')
print(f'any padding key: {sum(any_key.values())} roots in {len(any_key)} docs {dict(any_key)}')
print(f'NON-ZERO padding: {sum(non_zero.values())} roots in {sum(1 for v in non_zero.values() if v)} docs '
      f'{ {k: v for k, v in non_zero.items() if v} }')
for stem, nz, pad, idx, n, guard in rows:
    print(f'  {"NONZERO" if nz else "zero   "} {stem}: {pad} at property index {idx} of {n}; guard keys {guard}')
