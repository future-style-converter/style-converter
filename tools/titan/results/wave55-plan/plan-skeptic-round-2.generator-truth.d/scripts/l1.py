import sys, json, re
sys.path.insert(0, sys.argv[1]); from irlib import *
cells=json.load(open(ROOT+'/tools/titan/results/wave55-plan/cells-wave54-final.json'))['cells']
rows=[]; docs_hit={}
for sec,st,d in docs():
    D=Doc(d)
    for c in D.comps:
        t=c.get('text')
        if not t: continue
        lh,depth=D.eff(c,'LineHeight')
        if not (isinstance(lh,dict) and lh.get('original')=='normal'): continue
        wm,_=D.eff(c,'WritingMode'); fs,_=D.eff(c,'FontSize'); ff,_=D.eff(c,'FontFamily'); hy,_=D.eff(c,'Hyphens'); ws,_=D.eff(c,'WhiteSpace')
        fspx=fs.get('px') if isinstance(fs,dict) else None
        runs=(c.get('meta') or {}).get('runs')
        rows.append((st,c['id'],'own' if depth==0 else f'inh{depth}',wm,fspx,ff,hy,ws,t,bool(runs)))
        docs_hit.setdefault(st,[]).append(c['id'])
print('text comps with effective LineHeight normal:',len(rows),'docs',len(docs_hit))
print('own',sum(r[2]=='own' for r in rows),'inherited',sum(r[2]!='own' for r in rows))
for st in sorted(docs_hit):
    test='/'.join(st.split('__')[1:])+'.html'
    print(f"  {test:72s} ios {cells.get(test+' ios','—'):10s} n={len(docs_hit[st])}")
print('--- per component (horizontal space-less runs flagged)')
for r in rows:
    st,cid,dep,wm,fspx,ff,hy,ws,t,hasruns=r
    spaceless = not re.search(r'\s',t)
    soft = bool(re.search('[­\\-/‐　-鿿]',t))
    flag=''
    if spaceless and wm in (None,'HORIZONTAL_TB'): flag='SPACELESS-H' + ('+SOFTOPP' if soft else '')
    if wm not in (None,'HORIZONTAL_TB'): flag='VERTICAL '+str(wm)
    print(f"  {st[4:60]:56s} {cid[-12:]:>12s} {dep:5s} fs={fspx} ff={ff} hy={hy} ws={ws} runs={hasruns} {flag} text={t[:40]!r}")
