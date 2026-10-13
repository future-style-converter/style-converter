# fixed7.py — every fixed picture of the standing set through the REAL gate (--base wave55-open), per key: verdict + residual
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fakes7 as F7
from common7 import *
scr = sys.argv[1]; out = {}
CX = F7.contexts()
groups = [[s for s in CX if not (s[0] == 'L4' and s[1] == 'U1')], [s for s in CX if s[0] == 'L4' and s[1] == 'U1']]
for gi, sets in enumerate(groups):
    repl = {}
    for s in sets: repl.update(s[3])
    rid = make_run(scr, f'FIXED-g{gi}', repl)
    lanes = ','.join(sorted({s[0] for s in sets})); secs = ','.join(sorted({k[0] for s in sets for k in s[3]}))
    rc, rows, so = gate(rid, lanes, secs, os.path.join(scr, f'gate-FIXED-g{gi}.json'))
    open(f'fixed7.g{gi}.gate.out.txt', 'w').write(so)
    for lane, setn, secs_, rp, cxs in sets:
        for k3, cx in cxs.items():
            o, v, n, _ = RR.read_arrays(cx.F, cx.B, cx.spec)
            for _, s, k, cls in KEYS.get(cx.cell, []):
                r = rows.get((s, k), {})
                out[f'{lane}-{setn}|{cx.cell}|{s} {k}'] = {'verdict': r.get('verdict', 'NO ROW'), 'class': cls, 'residual': (r.get('residual') or {}).get('state'),
                                                          'inproc': [o, v, n], 'line': r.get('line', '')[-200:]}
                print(f'{lane}-{setn:6} {s[:28]:28} {k[:62]:62} {r.get("verdict", "NO ROW"):10} residual {(r.get("residual") or {}).get("line", "-")[:60]}  in-proc {o}/{v}/{n}')
json.dump(out, open('fixed7.json', 'w'), indent=1)
