# count-expectations.py — L4 RE-VERIFIER: reads a generated expectations.json / watchlist.txt and prints the counts the
# lane skeptic's defects 1 and 3 turn on, from the JSON itself (never from plan-build.py's own summary print).
import json, sys
e = json.load(open(sys.argv[1])); wl = [l for l in open(sys.argv[2]) if l.strip() and not l.startswith('#')]
L4 = e['lanes']['L4-oof-layout']; L6 = e['lanes']['L6-web-tail']
cbb = L4['revertUnits']['CBB-android']['captures']['android']
semi = sorted(c for c in cbb if 'semi-replaced-stretch' in c)
# the union carrier set R4 reads: every lane's captureCarriers per platform
union = {p: set() for p in ('web', 'ios', 'android')}
for lane in e['lanes'].values():
    for p, v in lane.get('captureCarriers', {}).items(): union[p] |= set(v)
l6m = [m for m in L6.get('mustNotMove', []) if 'semi-replaced-stretch' in str(m) and 'android' in str(m)]
sdp = e.get('stayDegenerateEvenIfPass', [])
p011 = [p for p in L4['predictions'] if 'contain-content-011' in str(p.get('cell', p)) and 'android' in str(p.get('cell', p))]
print(f"   CBB-android android captures {len(cbb)} (semi-replaced-stretch: {[s.split('__')[-1] for s in semi]})")
print(f"   L4 captureCarriers {{{', '.join(f'{k}: {len(v)}' for k, v in L4['captureCarriers'].items())}}} · predictions {len(L4['predictions'])} · mustNotMove {len(L4['mustNotMove'])}")
print(f"   union carriers web {len(union['web'])} · ios {len(union['ios'])} · android {len(union['android'])}; semi-replaced android in union: {sum(1 for c in union['android'] if 'semi-replaced-stretch' in c)}")
print(f"   L6 mustNotMove {len(L6['mustNotMove'])} (semi-replaced android lines left: {len(l6m)})")
print(f"   stayDegenerateEvenIfPass {len(sdp)}; 011 android in it: {any('contain-content-011' in s and 'android' in s for s in sdp)}")
for p in p011: print(f"   011 android prediction: kind={p.get('kind')!r} floor={p.get('floor')} gating={p.get('gating')} confidence={p.get('confidence')}")
print(f"   watchlist lines {len(wl)}")
