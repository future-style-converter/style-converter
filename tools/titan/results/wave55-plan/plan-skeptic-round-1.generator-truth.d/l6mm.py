import sys,collections,json
sys.path.insert(0,sys.argv[1]); from irlib import *
D=docs(); E=json.load(open(sys.argv[2]))
VIS=('SOLID','DASHED','DOTTED','DOUBLE','GROOVE','RIDGE','INSET','OUTSET')
def bordered(P):
    for s in ('Top','Right','Bottom','Left'):
        st=P.get(f'Border{s}Style'); w=P.get(f'Border{s}Width')
        if st in VIS:
            if w is None: return True        # medium
            if isinstance(w,dict) and (w.get('px') or 0)>0: return True
            if isinstance(w,dict) and w.get('px') is None: return True   # keyword thin/medium/thick
    return False
mm=set(); nb=0; opac=[]
for stem,(sec,d) in D.items():
    C=comps(d); kids=collections.defaultdict(list)
    for c in C:
        p=(c.get('slot') or {}).get('parent')
        if p and (c.get('meta') or {}).get('role')!='line-break': kids[p].append(c)
    for c in C:
        P=props(c)
        if not bordered(P): continue
        nb+=1
        if kids.get(c['id']) or (c.get('text') or '').strip(): mm.add(stem)
        op=P.get('Opacity')
        if op is not None and (op if isinstance(op,(int,float)) else (op.get('value',1) if isinstance(op,dict) else 1))<1: opac.append((stem,c['id'][-10:],op))
print('bordered components',nb,'documents with a bordered component that has a non-br child or own text:',len(mm))
L6=E['lanes']['L6-android-paint-order']
pub=set(E['unionMayMoveCaptures']['android'])|set(L6['captureCarriers']['android'])|set(s.split(' ')[0] for s in [])
shared=set()
for x in L6.get('sharedStems',[]):
    shared.add(x if isinstance(x,str) else x.get('stem', json.dumps(x)))
print('sharedStems sample', list(L6['sharedStems'])[:2] if isinstance(L6['sharedStems'],list) else L6['sharedStems'])
allpub=pub|{s for s in shared if s.startswith('wpt__')}
print('published may-move+L6 carriers+shared:',len(allpub))
print('in my set, not published:',sorted(mm-allpub))
print('published, not in my set:',sorted(allpub-mm))
print('bordered components with opacity<1:',opac)
