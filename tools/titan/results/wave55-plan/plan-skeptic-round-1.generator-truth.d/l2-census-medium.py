import sys,collections,json
sys.path.insert(0,sys.argv[1]); from irlib import *
D=docs()
def px(v):
    if isinstance(v,dict):
        if v.get('type')=='length' and isinstance(v.get('px'),(int,float)): return v['px']
        if 'px' in v and isinstance(v['px'],(int,float)) and v.get('type') in (None,'length'): return v['px']
    return None
def pct(v): return isinstance(v,dict) and (v.get('type')=='percentage' or 'PERCENT' in json.dumps(v))
def band(P,axis):
    s=0
    sides=('Left','Right') if axis=='x' else ('Top','Bottom')
    VIS=('SOLID','DASHED','DOTTED','DOUBLE','GROOVE','RIDGE','INSET','OUTSET')
    for sd in sides:
        v=P.get('Padding'+sd)
        if isinstance(v,dict) and isinstance(v.get('px'),(int,float)): s+=v['px']
        w=P.get('Border'+sd+'Width'); st=P.get('Border'+sd+'Style')
        if st in VIS:
            if w is None: s+=3
            elif isinstance(w,dict) and isinstance(w.get('px'),(int,float)): s+=w['px']
    return s
res=collections.defaultdict(list)
for stem,(sec,d) in D.items():
    byid,par=parents(d); C=comps(d)
    PR={c['id']:props(c) for c in C}
    def wm(cid):
        for x in [cid]+chain(cid,par):
            v=PR.get(x,{}).get('WritingMode')
            if v: return v
        return 'HORIZONTAL_TB'
    memo={}
    def differs(cid):  # does the block cid publishes to children differ on each axis?
        if cid in memo: return memo[cid]
        P=PR.get(cid,{}); out=set()
        bb=P.get('BoxSizing')=='BORDER_BOX'
        for ax,t in (('x','Width'),('y','Height')):
            if px(P.get(t)) is not None and band(P,ax)>0 and not bb: out.add(ax)
            elif pct(P.get(t)):
                p=par.get(cid)
                if p and ax in differs(p): out.add(ax)
        memo[cid]=out; return out
    for c in C:
        X=PR[c['id']]; pos=X.get('Position')
        oof=pos in ('ABSOLUTE','FIXED')
        if oof:
            cb=None
            for a in chain(c['id'],par):
                if PR.get(a,{}).get('Position') in ('RELATIVE','ABSOLUTE','FIXED','STICKY'): cb=a; break
        else: cb=par.get(c['id'])
        if not cb: continue
        ax=differs(cb)
        if not ax: continue
        why=[]
        for a,(t,i1,i2) in (('x',('Width','Left','Right')),('y',('Height','Top','Bottom'))):
            if a not in ax: continue
            if oof and (pct(X.get(t)) or (X.get(i1) not in (None,'auto') and X.get(i2) not in (None,'auto'))): why.append('oof')
            if any(pct(X.get(k)) for k in X if k.startswith(('Margin','Padding','Top','Left','Right','Bottom','Inset'))): why.append('spc')
            if c.get('text') and wm(c['id']).startswith('VERTICAL'): why.append('vtext')
            if not oof and pct(X.get(t)): why.append('inflow%')
        if why: res[stem].append((c['id'][-6:],sorted(set(why)),sorted(ax)))
cells=json.load(open(sys.argv[2]))
n=0
for s,v in sorted(res.items()):
    t=stem_to_test(s); n+=len(v)
    print(f"{s:80} android {cells.get(t+' android','—'):9} {v}")
print('docs',len(res),'boxes',n)
json.dump(sorted(res),open(sys.argv[3],'w'))
