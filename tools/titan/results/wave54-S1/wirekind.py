#!/usr/bin/env python3
"""Kind-of-change audit over the [W-L3] trees: for every CONTENT-CHANGED doc, list each component-level change
(by component NAME, ids stripped) and classify it against the registered wire classes."""
import json, sys, re, glob, os, collections
base = sys.argv[1]; lst = sys.argv[2]
docs = [l.split()[1] for l in open(lst) if l.startswith('CONTENT-CHANGED')]
unexpected = 0
for d in docs:
    sec, f = d.split('/')
    a = json.load(open(f'{base}/pre/{sec}/per-test-ir/{f}'))['components']
    b = json.load(open(f'{base}/post/{sec}/per-test-ir/{f}'))['components']
    A = {c['name']: c for c in a}; B = {c['name']: c for c in b}
    kinds = collections.Counter()
    for n in sorted(set(A) | set(B)):
        if n not in A: kinds['+component ' + ('markerRun' if 'Position' in json.dumps(B[n]) else '?')] += 1; continue
        if n not in B: kinds['-component'] += 1; continue
        pa = {p['type']: p['data'] for p in A[n].get('properties', [])}; pb = {p['type']: p['data'] for p in B[n].get('properties', [])}
        for k in sorted(set(pa) | set(pb)):
            if pa.get(k) != pb.get(k):
                if k.startswith('Padding') and pb.get(k) == {'px': 0}: kinds['Padding*->0'] += 1
                elif k == 'Height' and (A[n].get('meta') or {}).get('role') == 'line-break': kinds[f'br Height {pa.get(k,{}).get("px")}->{pb.get(k,{}).get("px")}'] += 1
                elif k in ('Generic', 'HyphenateCharacter'): kinds[f'{k}: {json.dumps(pa.get(k), ensure_ascii=False)[:40]} -> {json.dumps(pb.get(k), ensure_ascii=False)[:40]}'] += 1
                elif k == 'ListStyleType' and pb.get(k) == 'NONE': kinds['+ListStyleType NONE'] += 1
                else: kinds[f'OTHER {k}: {json.dumps(pa.get(k))[:50]} -> {json.dumps(pb.get(k))[:50]}'] += 1; unexpected += 1
        ma = dict(A[n].get('meta') or {}); mb = dict(B[n].get('meta') or {})
        if ma != mb:
            for k in sorted(set(ma) | set(mb)):
                if ma.get(k) != mb.get(k):
                    if k == 'markerText' and k not in mb: kinds['-meta.markerText'] += 1
                    elif k == 'runs': kinds['meta.runs changed'] += 1
                    else: kinds[f'OTHER meta.{k}'] += 1; unexpected += 1
        sa = (A[n].get('slot') or {}).get('parent'); sb = (B[n].get('slot') or {}).get('parent')
        if re.sub(r'-\d+$', '', sa or '') != re.sub(r'-\d+$', '', sb or ''): kinds['OTHER slot.parent'] += 1; unexpected += 1
        if A[n].get('text') != B[n].get('text'): kinds['OTHER text'] += 1; unexpected += 1
    print(f'{d}: {len(a)}->{len(b)} components · ' + '; '.join(f'{k} x{v}' for k, v in kinds.items()))
print(f'unclassified changes: {unexpected}')
