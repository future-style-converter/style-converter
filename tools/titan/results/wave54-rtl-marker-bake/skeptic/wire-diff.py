#!/usr/bin/env python3
# skeptic/wire-diff.py — the L1 SKEPTIC's own per-test IR differ (independent of the lane's w2-check.mjs).
# Compares every per-test IR document of <new-dir> with the same file under <base-dir>:
#   identical      — bytes equal;
#   renumbered     — equal once every component id's converter suffix "-NNN" (and slot.parent's) is stripped;
#   content-changed — anything else, printed component by component (added / removed / property types changed).
# Usage: wire-diff.py <base-per-test-ir-dir> <new-per-test-ir-dir> [--stack]   (--stack: Compose stack-shape count)
import json, os, re, sys, glob
base, new = sys.argv[1], sys.argv[2]
strip = lambda s: re.sub(r'-\d+$', '', s) if isinstance(s, str) else s
def norm(doc):
    # Ids without their converter-counter suffix, keyed by id, slot.parent stripped the same way.
    out = {}
    for c in doc['components']:
        c = json.loads(json.dumps(c)); c['id'] = strip(c['id'])
        if c.get('slot', {}).get('parent'): c['slot']['parent'] = strip(c['slot']['parent'])
        out[c['id']] = c
    return out
def stack(doc):
    # A non-RELATIVE out-of-flow parent with >= 2 out-of-flow children (the brief's host-gated Compose shape).
    P = {c['id']: {p['type']: p.get('data') for p in c.get('properties', [])} for c in doc['components']}
    kids = {}
    for c in doc['components']:
        par = (c.get('slot') or {}).get('parent')
        if par: kids.setdefault(par, []).append(c['id'])
    oof = lambda i: P[i].get('Position') in ('ABSOLUTE', 'FIXED', 'STICKY')
    return [i for i in P if oof(i) and sum(1 for k in kids.get(i, []) if oof(k)) >= 2]
cls = {'identical': [], 'renumbered': [], 'content-changed': [], 'missing-in-base': []}
for f in sorted(glob.glob(os.path.join(new, '*.json'))):
    name = os.path.basename(f); b = os.path.join(base, name)
    if not os.path.exists(b): cls['missing-in-base'].append(name); continue
    nb, bb = open(f, 'rb').read(), open(b, 'rb').read()
    if nb == bb: cls['identical'].append(name); continue
    nd, bd = json.loads(nb), json.loads(bb)
    if norm(nd) == norm(bd): cls['renumbered'].append(name); continue
    cls['content-changed'].append(name)
    N, B = norm(nd), norm(bd)
    print(f"CHANGED {name}: components {len(B)} -> {len(N)}")
    for i in sorted(set(N) | set(B)):
        if i not in B:
            c = N[i]; p = {q['type']: q.get('data') for q in c.get('properties', [])}
            print(f"  + {i} parent={c.get('slot', {}).get('parent')} meta={c.get('meta')} Left={p.get('Left')} Top={p.get('Top')} Width={p.get('Width')} Dir={p.get('Direction')} FVN={p.get('FontVariantNumeric')} text={c.get('text', c.get('content'))!r}")
        elif i not in N: print(f"  - {i}")
        elif N[i] != B[i]:
            pb = {q['type']: q.get('data') for q in B[i].get('properties', [])}; pn = {q['type']: q.get('data') for q in N[i].get('properties', [])}
            ch = sorted(k for k in set(pb) | set(pn) if pb.get(k) != pn.get(k))
            other = sorted(k for k in set(B[i]) | set(N[i]) if k != 'properties' and B[i].get(k) != N[i].get(k))
            print(f"  ~ {i} props {[(k, pb.get(k), pn.get(k)) for k in ch]} other {[(k, B[i].get(k), N[i].get(k)) for k in other]}")
    if '--stack' in sys.argv: print(f"  stack-shape parents: base {stack(bd)} -> new {stack(nd)}")
print(' · '.join(f"{k} {len(v)}" for k, v in cls.items()))
print('renumbered:', [n[:-5] for n in cls['renumbered']])
print('content-changed:', [n[:-5] for n in cls['content-changed']])
