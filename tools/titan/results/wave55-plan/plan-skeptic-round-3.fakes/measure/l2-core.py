# L2: green outside the ref mask's 1-px AA band, and ref-green missing inside it, on passing pictures
import importlib.util, os, numpy as np
from PIL import Image
P='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave55-plan'
def load(n,p):
    s=importlib.util.spec_from_file_location(n,p); m=importlib.util.module_from_spec(s); s.loader.exec_module(m); return m
pt=load('pt',P+'/probes-teeth.py')
G=lambda a:(a[...,0]<8)&(np.abs(a[...,1]-128)<8)&(a[...,2]<8)
def dil(m,r):
    o=m.copy()
    for dy in range(-r,r+1):
        for dx in range(-r,r+1):
            o|=np.roll(np.roll(m,dy,0),dx,1)
    return o
def ero(m,r): return ~dil(~m,r)
TESTS=[('css-flexbox',f'abspos/abspos-autopos-{m}') for m in ('htb-ltr','htb-rtl','vlr-ltr','vlr-rtl','vrl-ltr','vrl-rtl')]+[('filter-effects',f'backdrop-filter-nested-border-radius-clip{s}') for s in ('','-2','-3','-4')]
for sec,t in TESTS:
    ref=pt.A(Image.open(f'{pt.REFS}/{sec}/{t.replace("/","__")}.png')); rm=G(ref)
    pics={p:pt.cap('wave54-final',sec,t,p) for p in ('web','ios','android')}
    pics['probe-android']=pt.cap('wave54-probe',sec,t,'android')
    if t.endswith('clip-3'): pics['replay']=pt.png('cbb-android-replay/android-clip-3.png')
    out=[]
    for lab,a in pics.items():
        m=G(a)
        if m.shape!=rm.shape: out.append(f'{lab}:size'); continue
        for r in (1,2):
            extra=int((m & ~dil(rm,r)).sum()); miss=int((rm & ~ero(rm,r) & False).sum()) if False else int((ero(rm,r) & ~m).sum())
            out.append(f'{lab} r{r} extra {extra} miss {miss}')
        out.append(f'xor {int((m^rm).sum())}')
    print(t, '|', ' ; '.join(out))
