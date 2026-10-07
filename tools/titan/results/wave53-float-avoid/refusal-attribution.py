#!/usr/bin/env python3
# tools/titan/results/wave53-float-avoid/refusal-attribution.py — WHY the brief's float-then-BFC look-alikes are refused.
# A Python READING of the wire for the lane note (the census of record is the real Kotlin/Swift planner run over all
# 1435 documents in FloatAvoidPlanTest / FloatAvoidPlanTests). For every component whose children contain a Float and
# whose last child (or any later child) is a BFC root, it lists the gate clauses the wire trips. Run from the repo root.
import collections, glob, json, os
RUN = 'tools/titan/runs/wave52-ship/sections'


def kw(c, t):
    for p in c.get('properties', []):
        if p['type'] == t:
            d = p['data']
            if isinstance(d, str): return d.upper().replace('-', '_')
            if isinstance(d, list): return [str(x).upper() for x in d]
            if isinstance(d, dict): return (d.get('keyword') or d.get('value') or 'OBJ')
    return None


def bfc(c):
    ov = [kw(c, t) for t in ('Overflow', 'OverflowX', 'OverflowY', 'OverflowBlock', 'OverflowInline')]
    ct = kw(c, 'Contain') or []
    return kw(c, 'Display') == 'FLOW_ROOT' or any(v not in (None, 'VISIBLE') for v in ov) or \
        any(x in ('LAYOUT', 'PAINT', 'STRICT', 'CONTENT') for x in ct)


def px(c, t):
    for p in c.get('properties', []):
        if p['type'] == t and isinstance(p['data'], dict) and p['data'].get('type') != 'percentage':
            return p['data'].get('px')
    return None


def why(c, kids, allkids):
    out = []
    if c.get('slot') and not bfc(c): out.append('G2 nested non-BFC container')
    if c.get('text') or (c.get('meta') or {}).get('runs'): out.append('G3 container text/runs')
    if any(p['type'] in ('WritingMode', 'Direction') for p in c.get('properties', [])): out.append('G3 writing-mode/direction')
    if any(any(p['type'] == 'Clear' for p in x.get('properties', [])) for x in allkids): out.append('G5 Clear in subtree')
    fl = [k for k in kids[:-1]]
    if any(kw(k, 'Float') is None for k in fl): out.append('G4 a non-float before the BFC')
    sides = [kw(k, 'Float') for k in fl]
    if any(a == b and a for a, b in zip(sides, sides[1:])): out.append('G5 same-side run')
    for k in fl:
        if any(p['type'].startswith(('Margin', 'Padding', 'Border')) for p in k.get('properties', [])): out.append('G6 float box-model wire'); break
    for k in fl:
        if px(k, 'Width') is None or px(k, 'Height') is None: out.append('G6 non-px float size'); break
    b = kids[-1]
    if not bfc(b): out.append('G4 last child not a BFC root')
    if (b.get('meta') or {}).get('sourceTag') not in (None, 'div'): out.append(f"G3 BFC tag {(b.get('meta') or {}).get('sourceTag')}")
    if b.get('text'): out.append('G3 BFC own text')
    if any(p['type'].startswith(('Margin', 'Padding', 'Border')) for p in b.get('properties', [])): out.append('G7 BFC box-model wire')
    if px(b, 'Width') is None and kw(b, 'Width') not in ('FIT_CONTENT', 'MIN_CONTENT', 'MAX_CONTENT'): out.append('G7 auto/non-px BFC width')
    return out


for f in sorted(glob.glob(f'{RUN}/*/per-test-ir/*.json')):
    comps = json.load(open(f))['components']
    kids = collections.defaultdict(list)
    for c in comps:
        if c.get('slot'): kids[c['slot']['parent']].append(c)
    def sub(c): return [c] + [y for k in kids.get(c['id'], []) for y in sub(k)]
    for c in comps:
        ks = kids.get(c['id'], [])
        if len(ks) >= 2 and any(kw(k, 'Float') for k in ks[:-1]) and bfc(ks[-1]):
            print(os.path.basename(f)[:-5], c['id'], '→', '; '.join(why(c, ks, sub(c)[1:])) or 'ADMITTED')
    # Root-level look-alikes (floats and BFC as sibling ROOTS: no container exists to plan).
    roots = [c for c in comps if not c.get('slot')]
    if any(kw(r, 'Float') for r in roots) and any(bfc(r) for r in roots):
        print(os.path.basename(f)[:-5], '(roots)', '→ no container: float and BFC are sibling composed roots')
