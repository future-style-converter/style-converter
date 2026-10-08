#!/usr/bin/env python3
# tools/titan/results/wave53-lists-bakes/l1-census.py — L1's OWN blast-radius census (PLAN §0 "skeptic census";
# §2 L1 carrier set). Pure JSON over the per-test IR of two runs (default wave52-ship and wave53-open) plus the
# frozen bidi-baked fixtures; runs nothing, starts nothing.
#   1. wire identity: per-test IR sha1 of every document, run A vs run B (the plan's "byte-identical" premise);
#   2. Unit 1 runtime reach (C/D, PseudoTextFold twins): every component carrying meta.runs AND pseudos.before,
#      split into "folds" (before._text non-empty and runs[0] is text) vs "identity";
#      Unit 1 extraction reach (A/B) is u1-differential.out.json (in-process, 1435 tests);
#   3. Unit 2 hunk M reach: list-item components inside a bake root that are BOXES (Position ABSOLUTE|FIXED on an
#      `li`), and the meta.markerText carriers among them;
#   4. Unit 2 hunk P reach: re-stated from the frozen fixtures (bidi-baked-fixtures/): bake roots by the
#      `baked-bidi-visual-order` stamp, padding keys, zero/non-zero, guard keys;
#   5. hunk M's browser half runs only where a bidi-baked walk holds a list item: the bidiBaked fixtures whose
#      component tree has any `li` (roots or boxes) — those are the only documents where collectMarkerFacts opens
#      a CDP session at all.
import glob, hashlib, json, os, re, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
A, B = (sys.argv[1:3] + ['wave52-ship', 'wave53-open'][len(sys.argv[1:3]):])[:2]
def docs(run):
    out = {}
    for f in glob.glob(f'{ROOT}/tools/titan/runs/{run}/sections/*/per-test-ir/*.json'):
        out[f.split('/sections/')[1].replace('/per-test-ir/', '/')] = f
    return out
da, db = docs(A), docs(B)
sha = lambda p: hashlib.sha1(open(p, 'rb').read()).hexdigest()
diff = sorted(k for k in da if k in db and sha(da[k]) != sha(db[k]))
print(f'1. wire identity {A} vs {B}: {len(da)} / {len(db)} docs, common {len(set(da) & set(db))}, byte-different {len(diff)} {diff[:5]}')
fold, ident, m_boxes, m_text = [], [], [], []
for k, f in sorted(db.items()):
    comps = json.load(open(f)).get('components', [])
    for c in comps:
        meta, ps = c.get('meta') or {}, c.get('pseudos') or {}
        runs = meta.get('runs')
        if runs and 'before' in ps:
            (fold if (ps['before'].get('_text') and 'text' in runs[0]) else ident).append(f"{k}:{c['name']}")
        pos = next((p['data'] for p in c.get('properties', []) if p['type'] == 'Position'), None)
        if meta.get('sourceTag') == 'li' and pos in ('ABSOLUTE', 'FIXED'):
            m_boxes.append(f"{k}:{c['name']}")
            if meta.get('markerText'): m_text.append(f"{k}:{c['name']}={meta['markerText']}")
print(f'2. U1 runtime fold: folds {len(fold)} {fold} · identity {len(ident)} {ident}')
print(f'3. U2 hunk M: abspos li components {len(m_boxes)} in {len({x.split(":")[0] for x in m_boxes})} doc(s); with markerText {len(m_text)} {m_text}')
FX = os.path.join(ROOT, 'tools/titan/results/wave53-plan/bidi-baked-fixtures')
roots, li_docs = [], []
for f in sorted(glob.glob(f'{FX}/**/*.json', recursive=True)):
    fx = json.load(open(f)); rel = os.path.relpath(f, FX)
    has_li = False
    def walk(c):
        global has_li
        if c.get('_tag') == 'li': has_li = True
        if 'baked-bidi-visual-order' in (c.get('_lossyReasons') or []):
            props = c.get('properties') or {}
            pad = {k: v for k, v in props.items() if k.startswith('padding')}
            nz = any(re.sub(r'(px|em|rem|ch|%)$', '', t) not in ('0', '0.0') for v in pad.values() for t in str(v).split())
            guard = {k: props[k] for k in ('overflow', 'overflow-x', 'overflow-y', 'background-clip', 'background-origin') if k in props}
            roots.append((rel, nz, pad, guard))
        for k in (c.get('children') or {}).values(): walk(k)
    for c in (fx.get('components') or {}).values(): walk(c)
    if has_li: li_docs.append(rel)
nzr = [r for r in roots if r[1]]
print(f'4. U2 hunk P: bake roots {len(roots)}; with padding keys {sum(1 for r in roots if r[2])}; NON-ZERO {len(nzr)} in {len({r[0] for r in nzr})} docs '
      f'{sorted({r[0] for r in nzr})}; guard-tripping {[r for r in nzr if any(str(v) not in ("visible",) for v in r[3].values())]}')
print(f'5. hunk M browser half (CDP session) opens on {len(li_docs)} bidi-baked docs: {li_docs}')
