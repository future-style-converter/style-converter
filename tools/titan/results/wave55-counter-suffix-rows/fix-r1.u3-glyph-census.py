#!/usr/bin/env python3
# fix-r1.u3-glyph-census.py — wave-55 L5 FIX lane (fix-r1): U3-ios's GLYPH radius, corpus-wide (skeptic defect 1 is a pixel radius
# wider than the plan's digit window, inside U3's one capture; this census says whether it is wider ANYWHERE else).
# U3-ios acts on a component that DECLARES FontVariantNumeric with TABULAR_NUMS (declared-only on both natives: census-l5.py), and
# then — Font.monospacedDigit() → the face's whole `tnum` feature — on every glyph of its text that Inter's GSUB `tnum` maps.
# Walks every per-test IR of a run (default wave54-final; = wave55-open's, wire 1435 / 1435), lists each declaring component's
# text, and splits its characters into: tnum-mapped by Inter (repainted), in Inter but not mapped (unchanged), not in Inter
# (fallback face: unchanged). Usage: python3 fix-r1.u3-glyph-census.py [run]   (any python with fontTools; exit 0)
import glob, json, os, sys
from fontTools.ttLib import TTFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
RUN = sys.argv[1] if len(sys.argv) > 1 else 'wave54-final'
f = TTFont(f'{ROOT}/apps/ios-harness/StyleConverterTest/Resources/Inter-Regular.otf')
cmap = f.getBestCmap()
gsub = f['GSUB'].table
tnum = {}
for fr in gsub.FeatureList.FeatureRecord:
    if fr.FeatureTag == 'tnum':
        for li in fr.Feature.LookupListIndex:
            lk = gsub.LookupList.Lookup[li]
            for st in lk.SubTable:
                st = st.ExtSubTable if lk.LookupType == 7 else st
                tnum.update(getattr(st, 'mapping', {}))
docs = sorted(glob.glob(f'{ROOT}/tools/titan/runs/{RUN}/sections/*/per-test-ir/*.json'))
print(f'# fix-r1.u3-glyph-census.out.txt — run {RUN}: {len(docs)} per-test IR documents; Inter-Regular tnum maps {len(tnum)} glyphs')
n_comp = n_docs = n_mapped = 0
for p in docs:
    d = json.load(open(p))
    hits = []
    for c in d.get('components', []):
        fvn = [x['data'] for x in c.get('properties', []) if x.get('type') == 'FontVariantNumeric']
        if not fvn or not any('TABULAR_NUMS' in (v if isinstance(v, list) else [v]) for v in fvn):
            continue
        t = c.get('text') or ''
        mapped = [ch for ch in t if ord(ch) in cmap and cmap[ord(ch)] in tnum]
        kept = [ch for ch in t if ord(ch) in cmap and cmap[ord(ch)] not in tnum]
        fallback = [ch for ch in t if ord(ch) not in cmap]
        hits.append(f'  {c.get("id")} text {t!r}: tnum-mapped {mapped} · in Inter, unmapped {kept} · fallback face {fallback}')
        n_comp += 1; n_mapped += len(mapped)
    if hits:
        n_docs += 1
        print(os.path.relpath(p, ROOT))
        print('\n'.join(hits))
print(f'TOTAL: {n_comp} declaring component(s) in {n_docs} document(s); {n_mapped} glyph(s) repainted by tnum '
      f'(the plan read 1: row 9\'s digit window)')
