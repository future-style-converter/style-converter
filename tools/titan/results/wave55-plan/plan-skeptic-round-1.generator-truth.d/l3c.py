import sys,collections,json
sys.path.insert(0,sys.argv[1]); from irlib import *
D=docs(); cells=json.load(open(sys.argv[2]))
def ins(v): return v is not None and v!='auto' and not (isinstance(v,dict) and v.get('_unmapped'))
u2=collections.defaultdict(set); stack=collections.defaultdict(set)
for stem,(sec,d) in D.items():
    byid,par=parents(d); C=comps(d); PR={c['id']:props(c) for c in C}
    kids=collections.defaultdict(list)
    for c in C:
        p=par.get(c['id'])
        if p: kids[p].append(c)
    def direction(cid):
        for x in [cid]+chain(cid,par):
            v=PR.get(x,{}).get('Direction')
            if v: return v
        return 'LTR'
    for pid,ks in kids.items():
        Pp=PR.get(pid,{})
        if Pp.get('Position')=='RELATIVE' or str(Pp.get('Display','')).endswith(('FLEX','GRID')) or 'Transform' in Pp: continue  # Box / flex / grid branches
        oof=[k for k in ks if PR[k['id']].get('Position') in ('ABSOLUTE','FIXED')]
        if not oof: continue
        w=Pp.get('Width')
        if direction(pid)=='RTL' and isinstance(w,dict) and w.get('type')=='length':
            for k in oof:
                Pk=PR[k['id']]
                if any(ins(Pk.get(x)) for x in ('Left','Right')): u2[stem].add(pid[-6:])
print('U2 (RTL Column-loop parent, px Width, oof child with a physical inline inset):')
for s,v in sorted(u2.items()): print(f"  {s:78} android {cells.get(stem_to_test(s)+' android','—'):9} parents {sorted(v)}")
print('parents',sum(len(v) for v in u2.values()),'docs',len(u2))
