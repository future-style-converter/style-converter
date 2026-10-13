import sys,collections,json
sys.path.insert(0,sys.argv[1]); from irlib import *
D=docs(); n=0; nb=0; out=[]
for stem,(sec,d) in D.items():
    for c in comps(d):
        P=props(c)
        for ax,t,sides in (('x','Width',('Left','Right')),('y','Height',('Top','Bottom'))):
            v=P.get(t)
            if isinstance(v,dict) and v.get('type')=='percentage':
                n+=1
                b=sum((P.get(k) or {}).get('px',0) for sd in sides for k in ('Padding'+sd,'Border'+sd+'Width') if isinstance(P.get(k),dict))
                if b>0 and P.get('BoxSizing')!='BORDER_BOX' and P.get('Position') not in ('ABSOLUTE','FIXED'):
                    out.append((stem,c['id'][-6:],t,v['value'],b))
print('percentage axes',n); print('in-flow % axes with own non-zero band:',out)
