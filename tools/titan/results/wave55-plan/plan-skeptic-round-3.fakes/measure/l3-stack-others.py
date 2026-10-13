import importlib.util, os, numpy as np
from PIL import Image
P='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave55-plan'
def load(n,p):
    s=importlib.util.spec_from_file_location(n,p); m=importlib.util.module_from_spec(s); s.loader.exec_module(m); return m
pt=load('pt',P+'/probes-teeth.py')
G=lambda a:(a[...,0]<60)&(a[...,1]>100)&(a[...,2]<60)
pics={'ref':pt.A(Image.open(f'{pt.REFS}/css-anchor-position/anchor-position-multicol-nested-001.png'))}
for p in ('web','ios','android'): pics[p]=pt.cap('wave54-final','css-anchor-position','anchor-position-multicol-nested-001',p)
pics['U1']=pt.l3_fixed('anchor-position-multicol-nested-001')
CELLS=((190,78),(360,78),(190,168),(360,168))
for lab,a in pics.items():
    r=[]
    for d in (0,1,2):
        keep=np.zeros(a.shape[:2],bool)
        for x,y in CELLS: keep[y-d:y+10+d, x-d:x+10+d]=True
        r.append(int((G(a)&~keep).sum()))
    print(lab, 'green outside cells (+0/+1/+2 ring):', r, 'total green', int(G(a).sum()))
