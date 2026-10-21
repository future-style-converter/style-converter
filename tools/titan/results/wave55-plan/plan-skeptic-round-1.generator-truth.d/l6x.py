# own crossing counter + carrier rule over MY may-move docs (276) — independent implementation
import sys,collections,json,os
import numpy as np
from PIL import Image
sys.path.insert(0,sys.argv[1]); from irlib import *
ROOT='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/'
REFS=ROOT+'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin'
RUN=ROOT+'tools/titan/runs/wave54-final/sections'
DIRS={'web':'screenshots','ios':'ios-screenshots','android':'android-screenshots'}
def count(path):
    if not os.path.exists(path): return None
    a=np.asarray(Image.open(path).convert('RGB')).astype(np.int16)
    tot=0
    for A in (a, a.transpose(1,0,2)):
        dark=A.sum(2)<150; sat=((A.max(2)-A.min(2))>60)&~dark
        H,W=dark.shape
        # label runs of dark along axis 1
        for y in np.nonzero(dark.any(1))[0]:
            row=dark[y]; x=0
            idx=np.flatnonzero(np.diff(np.concatenate(([0],row.astype(np.int8),[0]))))
            for s,e in zip(idx[::2],idx[1::2]):
                L=e-s
                if 1<=L<=4 and s>=1 and e<W and sat[y,s-1] and sat[y,e] and np.abs(A[y,s-1]-A[y,e]).max()<=8: tot+=L
    return tot
VIS=('SOLID','DASHED','DOTTED','DOUBLE','GROOVE','RIDGE','INSET','OUTSET')
def bordered(P):
    for s in ('Top','Right','Bottom','Left'):
        st=P.get(f'Border{s}Style'); w=P.get(f'Border{s}Width')
        if st in VIS and (w is None or not isinstance(w,dict) or w.get('px') is None or w['px']>0): return True
    return False
D=docs(); rows=[]
for stem,(sec,d) in D.items():
    C=comps(d); kids=collections.defaultdict(list)
    for c in C:
        p=(c.get('slot') or {}).get('parent')
        if p and (c.get('meta') or {}).get('role')!='line-break': kids[p].append(c)
    if not any(bordered(props(c)) and (kids.get(c['id']) or (c.get('text') or '').strip()) for c in C): continue
    flat=stem[len(f'wpt__{sec}__'):]
    ref=count(f'{REFS}/{sec}/{flat}.png'); caps={p:count(f'{RUN}/{sec}/{dd}/{stem}.png') for p,dd in DIRS.items()}
    rows.append((stem,ref,caps))
car=[s for s,ref,c in rows if ref is not None and c['android'] is not None and c['android']-ref>=20 and all(c[p] is not None and abs(c[p]-ref)<=5 for p in ('web','ios'))]
print('docs scanned',len(rows),'carriers',len(car))
for s in sorted(car): print('  ',s)
json.dump({'rows':rows,'car':car},open(sys.argv[2],'w'))
