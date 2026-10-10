# skeptic-r3: geometry CONTROL keys (non-gating, expected PASS) whose cell is a capture carrier of some unit.
# geometry-gate.py does not exit non-zero on a control FAIL ("diagnosed through rules 5 / R4"); R4 (control-check)
# allows every carrier capture to change, and adjudicate R5 / R7 reads only non-carrier must-not-move cells. So a control
# key on a carrier is gated by NOTHING unless its cell is also a directed prediction row (rule 2) or P today (rule 1).
import json, sys, re
E = json.load(open(sys.argv[1])); G = json.load(open(sys.argv[2]))
carrier = {}
for ln, l in E['lanes'].items():
    for u, U in l['revertUnits'].items():
        for plat, stems in U['captures'].items():
            for s in stems: carrier.setdefault((s.split('__', 2)[2] if s.count('__') >= 2 else s, plat), set()).add((ln, u))
preds = {}
for ln, l in E['lanes'].items():
    for p in l['predictions']:
        if p['kind'] != 'non-corpus':
            t, plat = p['cell'].rsplit(' ', 1); preds[(t, plat)] = p
def match(key):
    """key: '<label…> <path-tail> <platform>' -> carrier entries whose stem ends with the path tail."""
    toks = key.split(); plat = toks[-1]; tail = toks[-2].replace('/', '__')
    return plat, tail, [(st, v) for (st, pl), v in carrier.items() if pl == plat and (st == tail or st.endswith('__' + tail))]
n = 0
for r in G['rows']:
    if r['class'] != 'control': continue
    plat, tail, hits = match(r['key'])
    if not hits: continue
    n += 1
    units = sorted({u for _, v in hits for u in v})
    pr = [p for (t, pl), p in preds.items() if pl == plat and t[:-5].replace('/', '__').endswith(tail)]
    pd = '; '.join(f"{p['from']} -> {p['to'][:40]} dir={p['direction']} gating={p['gating']}" for p in pr) or 'NO prediction row'
    print(f'{r["lane"]:24} {r["script"]:40} {r["key"]:45} carried by {units} | {pd}')
print(f'control keys on carriers: {n}')
