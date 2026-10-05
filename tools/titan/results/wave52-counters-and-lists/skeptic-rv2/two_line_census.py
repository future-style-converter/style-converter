#!/usr/bin/env python3
# L6 re-verify skeptic — independent census of the TWO-LINE far-edge paths (DOUBLE w>=3, GROOVE/RIDGE w>=2)
# whose doubleGeom inner line is mirrored by max(inset, extent-inset) whenever extent < 2*inset.
# inset_max: DOUBLE w - w/6 (=5w/6) ; GROOVE/RIDGE 3w/4.  Mirror (wrong) iff extent < 2*inset_max.
import json, glob, os, sys, collections
ROOT = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
RUN = f'{ROOT}/tools/titan/runs/wave51-fix/sections'
cells = {}
for man in glob.glob(f'{RUN}/*/manifest.json'):
    m = json.load(open(man))
    for key, r in m['wpt']['results'].items():
        for plat, d in (r.get('browserRef') or {}).get('diffs', {}).items():
            if isinstance(d, dict) and isinstance(d.get('ssim'), (int, float)) and not d.get('scoreExcluded'):
                cells[(key, plat.replace('-ref', ''))] = ('P' if d.get('wptPass') is True else 'f', d['ssim'])
def prop(c, t):
    for p in c.get('properties') or []:
        if p.get('type') == t: return p.get('data')
    return None
def px(v):
    if isinstance(v, dict):
        if isinstance(v.get('px'), (int, float)): return v['px']
    if isinstance(v, (int, float)): return v
    return None
def style(c, side):
    s = prop(c, f'Border{side}Style')
    return s.upper() if isinstance(s, str) else ''
def width(c, side):
    st = style(c, side)
    if st in ('', 'NONE', 'HIDDEN'): return 0
    w = px(prop(c, f'Border{side}Width'))
    return 3 if w is None else w
rows = []
for f in sorted(glob.glob(f'{RUN}/*/per-test-ir/*.json')):
    stem = os.path.basename(f)[:-5]
    key = 'css/' + '/'.join(stem[len('wpt__'):].split('__')) + '.html'
    cs = json.load(open(f)).get('components', [])
    ids = {c['id']: c for c in cs}
    parents = {(c.get('slot') or {}).get('parent') for c in cs}
    for c in cs:
        for far, near, P, pa, pb in (('Bottom', 'Top', 'Height', 'PaddingTop', 'PaddingBottom'),
                                     ('Right', 'Left', 'Width', 'PaddingLeft', 'PaddingRight')):
            st = style(c, far); w = width(c, far)
            if st not in ('DOUBLE', 'GROOVE', 'RIDGE') or w <= 0: continue
            if st == 'DOUBLE' and w < 3: continue          # drawDouble's one-stroke fallback
            if st != 'DOUBLE' and w < 2: continue          # groove/ridge one-stroke fallback
            inset_max = (w - w / 6) if st == 'DOUBLE' else 0.75 * w
            thresh = 2 * inset_max
            band = width(c, near) + (px(prop(c, pa)) or 0) + (px(prop(c, pb)) or 0) + w
            bs = str(prop(c, 'BoxSizing') or '').upper()
            h = px(prop(c, P))
            if h is not None:
                ext = h if 'BORDER' in bs else h + band; how = f'{P}={h}' + (' border-box' if 'BORDER' in bs else '')
            elif c.get('text'):
                ext = None; how = 'text'
            elif c['id'] in parents:
                ext = None; how = 'children'
            else:
                ext = band; how = 'empty'
            # parent definite size smaller than the child's own extent (the T2 compression)
            par = ids.get((c.get('slot') or {}).get('parent'))
            ph = px(prop(par, P)) if par is not None else None
            mirrored = ext is not None and ext < thresh
            rows.append(dict(test=key, comp=c['name'], side=far, style=st, w=w, extent=ext, how=how,
                             thresh=round(thresh, 3), mirrored=mirrored, parentDefinite=ph,
                             android=cells.get((key, 'android'))))
mir = [r for r in rows if r['mirrored']]
amb = [r for r in rows if r['extent'] is None]
print('two-line far-edge carriers:', len(rows), 'tests', len({r['test'] for r in rows}))
print('MIRRORED (extent known < 2*inset_max):', len(mir), 'tests', len({r['test'] for r in mir}))
for r in mir: print('  ', r)
print('extent unknown (text/children):', len(amb))
for r in amb: print('  ', r['test'], r['comp'], r['side'], r['style'], r['w'], r['how'], 'parentDef', r['parentDefinite'], r['android'])
print('parent definite < thresh (compression candidates):')
for r in rows:
    if r['parentDefinite'] is not None and r['parentDefinite'] < r['thresh']: print('  ', r)
json.dump(rows, open(sys.argv[1], 'w'), indent=1)
