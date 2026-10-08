#!/usr/bin/env python3
# Skeptic (wave 54 L6, sk6) static IR census, independent of the lane's scripts and of the runtime code:
# RS = adjacent ROOT pairs (no resolvable slot.parent) whose earlier root carries meta.role ws-after, both
# inline-level (declared Display inline* else the UA inline tag set READ from ComponentRenderer.tsx), body not
# declaring a preserving white-space and not a table box. W1 = runs hosts with OWN Hyphens AUTO whose {child}
# member splits a word (non-white-space on both sides) and is span/time/data + Position ABSOLUTE|FIXED + Color a<=0
# + nothing else but Hyphens, no children/runs/decorations.
import glob, json, os, re, sys
RUN = sys.argv[1]
src = open('apps/web-harness/src/sdui/ComponentRenderer.tsx').read()
blk = src[src.index('const INLINE_LEVEL_SOURCE_TAGS'):]; blk = blk[:blk.index(']);')]
INLINE_TAGS = set(re.findall(r"'([a-z0-9-]+)'", blk))
def disp(c):
    for p in c['properties']:
        if p['type'] == 'Display':
            d = p['data']; k = d if isinstance(d, str) else (d.get('keyword') or d.get('type'))
            return k.lower().replace('_', '-') if isinstance(k, str) else None
    return None
def inline(c):
    d = disp(c)
    return d.startswith('inline') if d is not None else ((c.get('meta') or {}).get('sourceTag') or '').lower() in INLINE_TAGS
pairs = {}; w1 = []
for f in sorted(glob.glob(f'tools/titan/runs/{RUN}/sections/*/per-test-ir/*.json')):
    comps = json.load(open(f))['components']; ids = {c['id'] for c in comps}; stem = os.path.basename(f)[:-5]
    roots = [c for c in comps if (c.get('slot') or {}).get('parent') not in ids]
    body = next((c for c in comps if (c.get('meta') or {}).get('role') == 'body-root'), None)
    bws = next((p['data'] for p in (body or {'properties': []})['properties'] if p['type'] == 'WhiteSpace'), None)
    btable = body is not None and (disp(body) or '') in ('table', 'inline-table')
    n = 0
    for a, b in zip(roots, roots[1:]):
        if (a.get('meta') or {}).get('role') != 'ws-after' or not inline(a) or not inline(b): continue
        if isinstance(bws, str) and bws.upper().replace('-', '_') in ('PRE', 'PRE_WRAP', 'PRE_LINE', 'BREAK_SPACES'): continue
        if btable: continue
        n += 1
    if n: pairs[stem] = n
    kids = {}
    for c in comps: kids.setdefault((c.get('slot') or {}).get('parent'), []).append(c)
    for h in comps:
        runs = (h.get('meta') or {}).get('runs') or []
        if not any(p['type'] == 'Hyphens' and str(p['data']).upper() == 'AUTO' for p in h['properties']): continue
        named = {k['name']: k for k in kids.get(h['id'], [])}
        for i in range(1, len(runs) - 1):
            r, pv, nx = runs[i], runs[i - 1], runs[i + 1]
            if 'child' not in r or 'text' not in pv or 'text' not in nx or not pv['text'] or not nx['text']: continue
            if pv['text'][-1] in ' \t\n\r\f' or nx['text'][0] in ' \t\n\r\f': continue
            m = named.get(r['child'])
            if not m or ((m.get('meta') or {}).get('sourceTag') or '').lower() not in ('span', 'time', 'data'): continue
            if kids.get(m['id']) or (m.get('meta') or {}).get('runs') or (m.get('meta') or {}).get('decorations'): continue
            t = [p['type'] for p in m['properties']]
            pos = [p['data'] for p in m['properties'] if p['type'] == 'Position']
            col = [p['data'] for p in m['properties'] if p['type'] == 'Color']
            if set(t) - {'Position', 'Color', 'Hyphens'} or not pos or not col: continue
            if not all(str(x).upper() in ('ABSOLUTE', 'FIXED') for x in pos): continue
            if not all(isinstance(x, dict) and isinstance((x.get('srgb') or {}).get('a'), (int, float)) and x['srgb']['a'] <= 0 for x in col): continue
            w1.append((stem, h['id']))
print(f'RS: {len(pairs)} documents, {sum(pairs.values())} separators')
for k, v in sorted(pairs.items()): print(f'  {v:3d} {k}')
print(f'W1: {len(w1)} joins in {len({s for s, _ in w1})} documents')
for x in w1: print('  ', *x)
