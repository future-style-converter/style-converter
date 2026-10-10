# skeptic-r3: every gating row — floor vs the wave54-open value (a floor at or below today's value cannot fail on a
# tree where the lane did nothing: R4 is vacuous there and only R6 (its geometry key) can catch a no-op).
import json, sys, re
E = json.load(open(sys.argv[1])); C = json.load(open(sys.argv[2]))['cells']

gkeys = {}
for ln, l in E['lanes'].items():
    for pr in l['geometryProbes']:
        for k, u in pr.get('gating', {}).items(): gkeys.setdefault(ln, []).append((k, u))
vac = 0
for ln, l in E['lanes'].items():
    for p in l['predictions']:
        if not p['gating']: continue
        v, s = C[p['cell']].split(); s = float(s)
        vacuous = v == 'P' and s >= p['floor']
        vac += vacuous
        t, plat = p['cell'].rsplit(' ', 1); tail = t[:-5].split('/')[-1]
        gk = [k for k, u in gkeys.get(ln, []) if tail in k and k.endswith(plat)]
        print(f"{'VACUOUS ' if vacuous else '        '}{ln[:3]} {p['cell']:62} today {v} {s:<7} floor {p['floor']:<6} to {p['to'][:28]:28} units {','.join(p['units'])} geo-key {gk or 'NONE'}")
print('vacuous floors:', vac)
