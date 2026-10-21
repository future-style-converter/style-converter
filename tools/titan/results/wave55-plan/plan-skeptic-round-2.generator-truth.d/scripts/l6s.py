# gt2 own "sandwich" counter: a band-coloured pixel with a NON-band, non-background ink pixel within 1..3 px on BOTH
# opposite sides (left+right or up+down): a band line running THROUGH other ink, i.e. a band painted over content.
# Excess = capture count - ref count; carrier = android excess >= 20 with web / iOS |excess| <= 5. Thresholds mine.
import sys, json, os
sys.path.insert(0, sys.argv[1]); from irlib import *
import l6mm_lib as M
from PIL import Image
import numpy as np
REF=ROOT+'/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin'
RUN=ROOT+'/tools/titan/runs/wave54-final/sections'
CAP={'android':'android-screenshots','web':'screenshots','ios':'ios-screenshots'}
want=set(json.load(open(sys.argv[2]))['B'])|{'wpt__filter-effects__backdrop-filter-basic-blur'}
def rgb(v):
    s=v.get('srgb') if isinstance(v,dict) else None
    return None if not s else tuple(int(round(255*s[k])) for k in 'rgb')
def count(a,cols):
    band=np.zeros(a.shape[:2],bool)
    for c in cols: band|=(np.abs(a-np.array(c)).max(axis=2)<=20)
    ink=(~band)&(a.min(axis=2)<215)
    H,W=band.shape; hit=np.zeros_like(band)
    for axis in (0,1):
        lo=np.zeros_like(band); hi=np.zeros_like(band)
        for k in (1,2,3):
            lo|=np.roll(ink,k,axis=axis); hi|=np.roll(ink,-k,axis=axis)
        hit|= band&lo&hi
    hit[:3,:]=hit[-3:,:]=False; hit[:,:3]=hit[:,-3:]=False
    return int(hit.sum())
rows=[]
for sec,st,d in docs():
    if st not in want: continue
    D=Doc(d); cols=set()
    for c in D.comps:
        if not M.bordered(c) and st!='wpt__filter-effects__backdrop-filter-basic-blur': continue
        bc=[rgb(p['data']) for p in props(c) if p['type'].startswith('Border') and p['type'].endswith('Color')]
        bc=[x for x in bc if x]
        if not bc: bc=[rgb(last(c,'Color') or {}) or (0,0,0)]
        cols|=set(bc)
    cols={c for c in cols if min(c)<235}
    rest='__'.join(st.split('__')[2:]); rp=f'{REF}/{sec}/{rest}.png'
    if not cols or not os.path.exists(rp): continue
    ref=np.asarray(Image.open(rp).convert('RGB'),dtype=np.int16); r0=count(ref,cols)
    o={'ref':r0}
    for p,dd in CAP.items():
        f=f'{RUN}/{sec}/{dd}/{st}.png'
        if not os.path.exists(f): o[p]=None; continue
        a=np.asarray(Image.open(f).convert('RGB'),dtype=np.int16)
        o[p]= None if a.shape!=ref.shape else count(a,cols)-r0
    rows.append((st,o))
car=[(st,o) for st,o in rows if o['android'] is not None and o['android']>=20 and all(o[q] is not None and abs(o[q])<=5 for q in ('web','ios'))]
json.dump(rows,open(sys.argv[3],"w")) if len(sys.argv)>3 else None
print("docs scanned",len(rows),"carriers",len(car))
for st,o in car: print('   ',st,o)
print('android excess >= 20 but web/ios not within 5:')
for st,o in sorted(rows,key=lambda r:-(r[1]['android'] or 0)):
    if (o['android'] or 0)>=20 and (st,o) not in car: print('   ',st,o)
