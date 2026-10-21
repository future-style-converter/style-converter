# gt2 own band-over-ink counter: a pixel where the CAPTURE shows one of the document's Compose border colours while the
# REF shows other, non-background ink at the same pixel ("band painted over a child"). Independent of the brief's
# crossing definition (which counts band-colour runs crossing child fills); thresholds are mine.
import sys, json, os
sys.path.insert(0, sys.argv[1]); from irlib import *
from PIL import Image
import numpy as np
sys.path.insert(0, sys.argv[1]); import l6mm_lib as M
REF=ROOT+'/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin'
RUN=ROOT+'/tools/titan/runs/wave54-final/sections'
CAP={'android':'android-screenshots','web':'screenshots','ios':'ios-screenshots'}
B=json.load(open(sys.argv[2]))['B']
want=set(B)|{'wpt__filter-effects__backdrop-filter-basic-blur'}
def rgb(v):
    s=v.get('srgb') if isinstance(v,dict) else None
    return None if not s else tuple(int(round(255*s[k])) for k in 'rgb')
rows=[]
for sec,st,d in docs():
    if st not in want: continue
    D=Doc(d); cols=set()
    for c in D.comps:
        if not M.bordered(c): continue
        for p in props(c):
            if p['type'].startswith('Border') and p['type'].endswith('Color'):
                x=rgb(p['data'])
                if x: cols.add(x)
        if not any(p['type'].startswith('Border') and p['type'].endswith('Color') for p in props(c)):
            x=rgb(last(c,'Color') or {}) or (0,0,0); cols.add(x)
    cols={c for c in cols if min(c)<235}
    if not cols: continue
    rest='__'.join(st.split('__')[2:])
    rp=f'{REF}/{sec}/{rest}.png'
    if not os.path.exists(rp): continue
    ref=np.asarray(Image.open(rp).convert('RGB'),dtype=np.int16)
    def isband(a):
        m=np.zeros(a.shape[:2],bool)
        for c in cols: m|= (np.abs(a-np.array(c)).max(axis=2)<=24)
        return m
    rband=isband(ref); rink=(~rband)&(ref.min(axis=2)<200)
    out={}
    for p,dd in CAP.items():
        f=f'{RUN}/{sec}/{dd}/{st}.png'
        if not os.path.exists(f): out[p]=None; continue
        a=np.asarray(Image.open(f).convert('RGB'),dtype=np.int16)
        if a.shape!=ref.shape: out[p]=None; continue
        out[p]=int((isband(a)&rink).sum())
    rows.append((st,out))
car=[st for st,o in rows if o.get('android') is not None and o['android']>=20 and all(o.get(q) is not None and abs(o[q])<=5 for q in ('web','ios'))]
print('docs scanned',len(rows))
print('my carriers (android band-over-ink >= 20, web and ios <= 5):',len(car))
for st in car: print('   ',st,[o for s,o in rows if s==st][0])
print('top android counts not carriers:')
for st,o in sorted(rows,key=lambda r:-(r[1].get('android') or 0))[:45]:
    if st not in car: print('   ',st,o)
json.dump(rows,open(sys.argv[3],'w'))
