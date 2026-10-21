# gt2 own key checker over my own probe read-outs (probes/NN.out.txt) — independent of geometry-gate.py's code.
import json, re, sys, collections
ROOT='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
S=sys.argv[1]
E=json.load(open(sys.argv[2] if len(sys.argv)>2 else ROOT+'/tools/titan/results/wave55-plan/expectations.json'))
norm=lambda s: re.sub(r'\s+',' ',s.strip())
i=0; bad=[]; multi=[]; tot=collections.Counter(); exactstart=0
selfcheck=open(ROOT+'/tools/titan/results/wave55-plan/probes-selfcheck.txt').read()
unprintable=[]
for lid,L in E['lanes'].items():
    for g in L['geometryProbes']:
        i+=1
        lines=[norm(l) for l in open(f'{S}/probes/{i:02d}.out.txt') if l.strip()]
        for key,want in g['expect'].items():
            kt=key.split()
            hits=[(l,j) for l in lines for j in range(0,3) if l.split()[j:j+len(kt)]==kt]
            # dedupe per line
            seen=[]; 
            for l,j in hits:
                if not seen or seen[-1][0]!=l: seen.append((l,j))
            cls='gating' if key in g.get('gating',{}) else 'withdrawn' if key in g.get('withdrawn',{}) else 'report' if key in g.get('report',[]) else 'control'
            tot[cls]+=1
            if not seen: bad.append((lid,key,cls,'NO LINE')); continue
            if len(seen)>1: multi.append((lid,key,[s[0][:90] for s in seen]))
            line,j=seen[-1]
            if j==0: exactstart+=1
            contains=g.get('contains',{}).get(key,'').replace('<base>','wave54-final')
            ok=line.endswith(norm(want)) and (not contains or contains in line)
            exp_ok = (cls=='control')
            if ok!=exp_ok: bad.append((lid,key,cls,f'{"PASS" if ok else "FAIL"} :: {line[-140:]}'))
            if cls in ('gating','report') and norm(want) not in selfcheck and not any(l.endswith(norm(want)) for l in lines):
                unprintable.append((lid,key,want))
print('keys by class',dict(tot),'total',sum(tot.values()))
print('lines matched at token 0:',exactstart)
print('wrong-class outcomes on wave54-final (gating/report should FAIL, control PASS):',len(bad))
for b in bad: print('   ',b)
print('keys matching >1 line:',len(multi))
for m in multi: print('   ',m)
print('gating/report expect strings printed neither on wave54-final nor anywhere in probes-selfcheck.txt:',len(unprintable))
for u in unprintable: print('   ',u)
