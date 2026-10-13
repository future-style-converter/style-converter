import sys,collections,json
sys.path.insert(0,sys.argv[1]); from irlib import *
D=docs(); cells=json.load(open(sys.argv[2]))
fire=collections.defaultdict(list); ident=collections.defaultdict(list); other=collections.defaultdict(list)
for stem,(sec,d) in D.items():
    byid,par=parents(d); C=comps(d); PR={c['id']:props(c) for c in C}
    def eff(cid,t):
        for x in [cid]+chain(cid,par):
            v=PR.get(x,{}).get(t)
            if v: return v
    for c in C:
        P=PR[c['id']]; tag=(c.get('meta') or {}).get('sourceTag')
        if P.get('Position') not in ('ABSOLUTE','FIXED'): continue
        wm=eff(c['id'],'WritingMode') or 'HORIZONTAL_TB'
        und=[e for e in ('MarginTop','MarginBottom') if e not in P]
        if tag in ('h1','h2','h3','h4','h5','h6'):
            if und and wm=='HORIZONTAL_TB': fire[stem].append((c['id'][-8:],tag,und,len([k for k in C if (k.get('slot') or {}).get('parent')==c['id']])))
            else: ident[stem].append((c['id'][-8:],tag,und,wm))
        elif tag in ('p',) and und: other[stem].append((c['id'][-8:],tag,und))
print('U1 fires (heading, abspos/fixed, >=1 undeclared block margin, horizontal):')
for s,v in sorted(fire.items()): print(f"  {s:70} ios {cells.get(stem_to_test(s)+' ios','—'):9} android {cells.get(stem_to_test(s)+' android','—'):9} {v}")
print('components',sum(len(v) for v in fire.values()),'docs',len(fire))
print('identity headings (both margins declared or vertical):',len(ident),sorted(ident)[:20])
print('abspos <p> with an undeclared margin:',sorted(other))
