import json,os,sys
e=json.load(open(sys.argv[1])); OUT=sys.argv[2]
norm=lambda s:' '.join(s.split()); bad=0; n=0
for ln,L in e['lanes'].items():
    for g in L['geometryProbes']:
        name=os.path.basename([t for t in g['cmd'].split() if t.endswith('.py')][0])
        lines=[norm(l) for l in open(os.path.join(OUT,name+'.out')) if l.strip()]
        for k,want in g['expect'].items():
            n+=1; kt=k.split(); cls='gating' if k in g.get('gating',{}) else ('report' if k in g.get('report',[]) else 'control')
            hits=[l for l in lines if any(l.split()[i:i+len(kt)]==kt for i in range(0,3))]
            c=g.get('contains',{}).get(k,'').replace('<base>','wave54-final')
            ok=bool(hits) and hits[-1].endswith(norm(want)) and (not c or c in hits[-1])
            if (cls=='control')!=ok: bad+=1; print('KEY',ln,name,k,cls,'pass_on_wave54-final=',ok)
print('keys',n,'wrong-class outcomes',bad); sys.exit(1 if bad else 0)
