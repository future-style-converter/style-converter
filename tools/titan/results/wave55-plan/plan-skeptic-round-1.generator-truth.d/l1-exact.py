import sys,collections
sys.path.insert(0,sys.argv[1]); from irlib import *
D=docs()
for stem,(sec,d) in D.items():
    byid,par=parents(d)
    for c in comps(d):
        P=props(c)
        def eff(t):
            if t in P: return P[t]
            for p in chain(c['id'],par):
                pp=props(byid[p]) if p in byid else {}
                if t in pp: return pp[t]
        lh=eff('LineHeight')
        if not (isinstance(lh,dict) and lh.get('original')=='normal'): continue
        if (c.get('meta') or {}).get('role')=='line-break' or not c.get('text'): continue
        t=c['text']; ws=any(ch.isspace() for ch in t)
        kids=[x for x in comps(d) if (x.get('slot') or {}).get('parent')==c['id']]
        print(f"{stem[5:60]:56} {c['id'][-14:]:>14} ws={ws!s:5} wm={eff('WritingMode')} W={json.dumps(P.get('Width'))[:50]} kids={[ (k.get('meta') or {}).get('role') or (k.get('meta') or {}).get('sourceTag') for k in kids][:4]} text={t[:40]!r}")
