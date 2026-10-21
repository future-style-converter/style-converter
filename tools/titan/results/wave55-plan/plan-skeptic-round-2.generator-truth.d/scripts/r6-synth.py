# gt2 synthetic --movers 0 record for adjudicate.mjs R6: every wave54-final cell unchanged except inset-005 ios/android f -> P 0.97,
# plus a geometry JSON where both [F] text-decoration-inset-005 rows PASS. Usage: python3 r6-synth.py <cells-wave54-final.json> <outdir>
import json,sys
snap=json.load(open(sys.argv[1])); out=sys.argv[2]
gained=[];movers=[]
tot={'prev':{p:{'passing':0,'measured':0} for p in ('web','ios','android')},'cur':{p:{'passing':0,'measured':0} for p in ('web','ios','android')}}
for k,v in snap['cells'].items():
    t,p=k.rsplit(' ',1); test='css/'+t; s=float(v.split()[1]); P=v.startswith('P')
    tot['prev'][p]['measured']+=1; tot['cur'][p]['measured']+=1; tot['prev'][p]['passing']+=P
    if k in ('css-text-decor/text-decoration-inset-005.html android','css-text-decor/text-decoration-inset-005.html ios'):
        gained.append({'test':test,'platform':p,'prev':v,'cur':'P 0.97','delta':0.97-s}); tot['cur'][p]['passing']+=1
    else:
        movers.append({'test':test,'platform':p,'prev':v,'cur':v,'delta':0}); tot['cur'][p]['passing']+=P
json.dump({'prev':'wave55-open','cur':'wave55-final','totals':tot,'gained':gained,'lost':[],'movers':movers,'newlyMeasured':[],'unmeasuredNow':[],'missingSections':[],'columnShorts':[]},open(out+'/score.json','w'))
rows=[{'script':'ua-heading-margin.geometry.py','key':f'[F] text-decoration-inset-005 {p}','verdict':'PASS','line':f'[F] text-decoration-inset-005 {p} bands ... → GEOMETRY OK'} for p in ('android','ios')]
json.dump({'run':'wave55-final','base':'wave55-open','rows':rows},open(out+'/geometry.json','w'))
# then: node tools/titan/results/wave55-gate/adjudicate.mjs <outdir>/score.json --geometry <outdir>/geometry.json
