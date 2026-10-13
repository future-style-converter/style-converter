# control arm of the L6 may-move census: the brief's explicit-px rule must reproduce the published set exactly
import sys,collections,json
sys.path.insert(0,sys.argv[1]); from irlib import *
E=json.load(open(sys.argv[2]))
D=docs(); mm=set()
for stem,(sec,d) in D.items():
    C=comps(d); kids=collections.defaultdict(list)
    for c in C:
        p=(c.get('slot') or {}).get('parent')
        if p and (c.get('meta') or {}).get('role')!='line-break': kids[p].append(c)
    for c in C:
        P=props(c)
        if any(isinstance(P.get(f'Border{s}Width'),dict) and (P[f'Border{s}Width'].get('px') or 0)>0 and P.get(f'Border{s}Style') not in (None,'NONE','HIDDEN') for s in ('Top','Right','Bottom','Left')) and (kids.get(c['id']) or (c.get('text') or '').strip()):
            mm.add(stem); break
L6=E['lanes']['L6-android-paint-order']
pub=set(E['unionMayMoveCaptures']['android'])|set(L6['captureCarriers']['android'])|set(L6['sharedStems'])
pub.discard('wpt__filter-effects__backdrop-filter-basic-blur')   # (2b), added by rule outside the scan
print('explicit-px rule',len(mm),'published raw set',len(pub),'equal',mm==pub, sorted(mm^pub)[:5])
