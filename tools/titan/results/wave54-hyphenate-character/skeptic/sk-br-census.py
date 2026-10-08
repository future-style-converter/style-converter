#!/usr/bin/env python3
# Skeptic (wave 54, L3) — INDEPENDENT br census over the recorded per-test IR (gate flags already applied), not the
# lane's in-process extractor differential. For every line-break component (meta.role == 'line-break' / sourceTag br):
# its height on the wire, its host (slot.parent; None = body scope) and whether the host's meta.runs carries
# non-collapsible text (css-text-3 §4.1.1: anything but space/tab/LF/CR/FF) strictly between the previous CHILD entry
# and this br's entry. U3's rule (seam-3) turns exactly the [host != None, height 20, text-before] brs into 0.
# Usage: sk-br-census.py <run-id>
import json, glob, os, re, sys, collections
run = sys.argv[1] if len(sys.argv) > 1 else 'wave53-final'
root = os.path.join(os.path.dirname(__file__), '../../../runs', run, 'sections')
NONWS = re.compile(r'[^ \t\n\r\f]')
def hpx(c):
    for p in c.get('properties', []):
        if p['type'] == 'Height':
            d = p.get('data') or {}
            return d.get('px')
    return None
flip = collections.defaultdict(list); start20 = collections.defaultdict(list); body20txt = collections.defaultdict(list)
nbr = 0; ndocs = 0; withbr = set()
for p in sorted(glob.glob(os.path.join(root, '*/per-test-ir/*.json'))):
    ndocs += 1; stem = os.path.basename(p)[:-5]
    d = json.load(open(p)); comps = d['components']; byid = {c['id']: c for c in comps}
    for c in comps:
        m = c.get('meta') or {}
        if not (m.get('role') == 'line-break' or m.get('sourceTag') == 'br'): continue
        nbr += 1; withbr.add(stem)
        h = hpx(c); par = (c.get('slot') or {}).get('parent')
        name = c.get('name', c['id'])
        k = re.search(r'__(\d+)$', name)
        if par is None:
            # body scope: report only (the body re-arm already exists)
            continue
        host = byid.get(par); runs = ((host or {}).get('meta') or {}).get('runs')
        if not runs or not k: continue
        idx = next((i for i, e in enumerate(runs) if 'child' in e and re.search(r'__%s$' % k.group(1), e['child']) and name.endswith(e['child'])), None)
        if idx is None: continue
        j = idx - 1; txt = ''
        while j >= 0 and 'child' not in runs[j]:
            txt = runs[j].get('text', '') + txt; j -= 1
        has = bool(NONWS.search(txt))
        if h == 20 and has: flip[stem].append(name)
        if h == 20 and not has: start20[stem].append(name)
print(f'run {run}: {ndocs} documents, {nbr} line-break components in {len(withbr)} documents')
print(f'U3 radius (host-scope br, 20px, non-collapsible text since the previous child): {sum(map(len, flip.values()))} brs in {len(flip)} documents')
for s in sorted(flip): print(f'  {s}  {len(flip[s])}')
print(f'line-start 20px host-scope brs (U3 keeps 20; U3b candidates if the host declares line-height): {sum(map(len, start20.values()))} in {len(start20)} documents')
for s in sorted(start20): print(f'  {s}  {len(start20[s])}')
