import sys, json
sys.path.insert(0, sys.argv[1]); from irlib import *
fire=[]; ident=[]; ptags=[]; tagsall={}
for sec,st,d in docs():
    D=Doc(d)
    for c in D.comps:
        tag=((c.get('meta') or {}).get('sourceTag') or '').lower()
        p=last(c,'Position')
        if p not in ('ABSOLUTE','FIXED'): continue
        wm,_=D.eff(c,'WritingMode')
        hor = wm in (None,'HORIZONTAL_TB')
        und = (not has(c,'MarginTop')) or (not has(c,'MarginBottom')) or (not has(c,'MarginBlockStart') and not has(c,'MarginTop'))
        undeclared_edges=[e for e,ts in (('top',('MarginTop','MarginBlockStart')),('bottom',('MarginBottom','MarginBlockEnd'))) if not any(has(c,t) for t in ts)]
        tagsall.setdefault(tag,set()).add(st)
        if tag in ('h1','h2','h3','h4','h5','h6'):
            if undeclared_edges and hor: fire.append((st,c['id'],tag,undeclared_edges,bool(D.kids.get(c['id']))))
            else: ident.append((st,c['id'],tag,undeclared_edges,hor))
        if tag=='p' and undeclared_edges and hor: ptags.append((st,c['id']))
print('abspos heading components firing (undeclared block edge, horizontal):',len(fire),'docs',len({f[0] for f in fire}))
for f in fire: print('   ',f)
print('abspos heading components identity (both edges declared or vertical):',len(ident),'docs',len({f[0] for f in ident}))
for f in sorted({f[0] for f in ident}): print('   ',f)
print('abspos <p> with an undeclared block edge (out of scope):',len(ptags),'docs',len({x[0] for x in ptags}))
for x in sorted({x[0] for x in ptags}): print('   ',x)
