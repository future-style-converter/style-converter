import sys,collections,json
sys.path.insert(0,sys.argv[1]); from irlib import *
D=docs(); cells=json.load(open(sys.argv[2]))
POS=('RELATIVE','ABSOLUTE','FIXED','STICKY')
def ins(v): return v is not None and v!='auto' and not (isinstance(v,dict) and v.get('_unmapped'))
def hpx(c,P):
    h=P.get('Height')
    if isinstance(h,dict) and h.get('type')=='length': return (h.get('px') or 0)>0
    if (c.get('text') or '').strip(): return True
    return None
res={'narrow':collections.defaultdict(list),'broad':collections.defaultdict(list)}
for stem,(sec,d) in D.items():
    byid,par=parents(d); C=comps(d); PR={c['id']:props(c) for c in C}
    kids=collections.defaultdict(list)
    for c in C:
        p=par.get(c['id'])
        if p: kids[p].append(c)
    def posanc(cid): return any(PR.get(a,{}).get('Position') in POS for a in chain(cid,par))
    hostN=hostB=False
    for c in C:
        P=PR[c['id']]; ps=P.get('Position')
        if ps=='FIXED': hostN=hostB=True
        if ps=='ABSOLUTE' and not posanc(c['id']): hostN=hostB=True
        if ps=='ABSOLUTE' and any(ins(P.get(k)) for k in ('Top','Left','Right','Bottom')): hostB=True
    def direction(cid):
        for x in [cid]+chain(cid,par):
            v=PR.get(x,{}).get('Direction')
            if v: return v
        return 'LTR'
    for pid,ks in kids.items():
        Pp=PR.get(pid,{})
        if Pp.get('Position') not in ('ABSOLUTE','FIXED','STICKY') or 'Transform' in Pp: continue   # Column-loop CB parents only (a transform-CB parent with an oof child takes the Box branch, ComponentRenderer.kt isPositionedContainer)
        if direction(pid)=='RTL': continue
        for i,k in enumerate(ks):
            if PR[k['id']].get('Position') in ('ABSOLUTE','FIXED') and i>0:
                prev=[hpx(x,PR[x['id']]) for x in ks[:i]]
                if any(v for v in prev):
                    for mode,h in (('narrow',hostN),('broad',hostB)):
                        if not h: res[mode][stem].append((pid[-6:],k['id'][-6:]))
for mode in res:
    print('==',mode,'host predicate: STACK docs',len(res[mode]))
    for s,v in sorted(res[mode].items()): print(f"   {s:78} android {cells.get(stem_to_test(s)+' android','—'):9} {sorted(set(v))[:3]}")
