import sys,collections,json
sys.path.insert(0,sys.argv[1]); from irlib import *
D=docs(); VIS=('SOLID','DASHED','DOTTED','DOUBLE','GROOVE','RIDGE','INSET','OUTSET')
out=[]
for stem,(sec,d) in D.items():
    C=comps(d); kids=collections.defaultdict(list)
    for c in C:
        p=(c.get('slot') or {}).get('parent')
        if p: kids[p].append(c)
    for c in C:
        P=props(c)
        sides=[]
        for s in ('Top','Right','Bottom','Left'):
            st=P.get(f'Border{s}Style'); w=P.get(f'Border{s}Width')
            wpx=None if st not in VIS else (3 if w is None else (w.get('px') if isinstance(w,dict) else None))
            sides.append((st,wpx))
        has=any(st in VIS and (wpx or 0)>0 for st,wpx in sides)
        if not has: continue
        rad=any(isinstance(P.get(k),dict) and (P[k].get('px') or 0)>0 for k in P if k.startswith('Border') and k.endswith('Radius'))
        clips=P.get('OverflowX') in ('HIDDEN','CLIP','SCROLL','AUTO') or P.get('OverflowY') in ('HIDDEN','CLIP','SCROLL','AUTO')
        layers='BackgroundImage' in P
        op=P.get('Opacity'); partial=isinstance(op,dict) and op.get('alpha',1)<1
        uni=len(set(sides))==1 and sides[0][0]=='SOLID'
        clipins='ClipPath' in P or 'Clip' in P
        if rad and not clips and not layers and not partial and uni:
            implicit=any(P.get(f'Border{s}Width') is None for s in ('Top','Right','Bottom','Left'))
            out.append((stem[5:],c['id'][-8:],'medium-default' if implicit else '','clip' if clipins else '',len(kids.get(c['id'],[]))))
for o in out: print(o)
print(len(out))
