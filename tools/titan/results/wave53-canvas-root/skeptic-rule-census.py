#!/usr/bin/env python3
# tools/titan/results/wave53-canvas-root/skeptic-rule-census.py <run> — the L3
# SKEPTIC's code-free census: reads every per-test IR document of a run and
# counts, from the raw wire alone, the documents L3's two triggers can reach
# (A: a body-root with a BackgroundImage; B: a body-root whose LAST Display is
# TABLE/INLINE_TABLE), plus the near-misses an over-broad trigger would catch.
import glob, json, os, sys
RUN = sys.argv[1] if len(sys.argv) > 1 else 'wave52-ship'
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
files = sorted(glob.glob(os.path.join(ROOT, 'tools/titan/runs', RUN, 'sections/*/per-test-ir/*.json')))
c = dict(docs=0, body=0, multiBody=0, bodyImg=0, bodyImgNoneOnly=0, bodyColourOnly=0, bodyBgLess=0, bodyImgContained=0,
         tableLast=0, tableAny=0, tableishLast=0, bodyImgTypesNoImage=0, imgOnNonRootBody=0)
A, B, near = [], [], []
IMG = {'BackgroundImage', 'BackgroundSize', 'BackgroundRepeat', 'BackgroundAttachment', 'BackgroundPosition',
       'BackgroundPositionX', 'BackgroundPositionY', 'BackgroundOrigin', 'BackgroundClip', 'BackgroundBlendMode'}
for f in files:
    d = json.load(open(f)); c['docs'] += 1; name = os.path.basename(f)[:-5]
    bodies = [x for x in d['components'] if (x.get('meta') or {}).get('role') == 'body-root']
    if not bodies: continue
    c['body'] += 1; c['multiBody'] += len(bodies) > 1; b = bodies[0]; P = b['properties']; T = [p['type'] for p in P]
    if b.get('slot'): c['imgOnNonRootBody'] += 1                          # a slotted body-root (never a root)
    imgs = [p for p in P if p['type'] == 'BackgroundImage']
    if imgs:
        c['bodyImg'] += 1; L = imgs[-1]['data']; L = L if isinstance(L, list) else [L]
        if all(l == 'none' or (isinstance(l, dict) and l.get('type') == 'none') for l in L): c['bodyImgNoneOnly'] += 1
        else: A.append(name)
        if 'Contain' in T: c['bodyImgContained'] += 1
    elif 'BackgroundColor' in T: c['bodyColourOnly'] += 1
    else: c['bodyBgLess'] += 1
    if not imgs and IMG & set(T): c['bodyImgTypesNoImage'] += 1
    disp = [p['data'] for p in P if p['type'] == 'Display']
    if disp and str(disp[-1]).upper() in ('TABLE', 'INLINE_TABLE'): c['tableLast'] += 1; B.append(name)
    if any(str(x).upper() in ('TABLE', 'INLINE_TABLE') for x in disp): c['tableAny'] += 1
    if disp and str(disp[-1]).upper().startswith('TABLE') or disp and str(disp[-1]).upper() == 'INLINE_TABLE':
        c['tableishLast'] += 1; near.append(f"{name} {disp[-1]}")
print(f"run {RUN}: " + ', '.join(f"{k} {v}" for k, v in c.items()))
print('A (body-root with a painting BackgroundImage):', len(A)); [print('  ', x) for x in A]
print('B (body-root LAST Display TABLE/INLINE_TABLE):', len(B)); [print('  ', x) for x in B]
print('near-miss table-ish body displays (last):', len(near)); [print('  ', x) for x in near]
