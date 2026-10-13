import importlib.util, os, numpy as np
from PIL import Image
P='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave55-plan'
def load(n,p):
    s=importlib.util.spec_from_file_location(n,p); m=importlib.util.module_from_spec(s); s.loader.exec_module(m); return m
pt=load('pt',P+'/probes-teeth.py'); ai=load('ai',P+'/plan-skeptic-round-3.fakes/added_ink.py')
blue=lambda a:(a[...,2]>150)&(a[...,0]<120)&(a[...,1]<120)
pics={'ref':pt.A(Image.open(f'{pt.REFS}/css-text/bidi__bidi-tab-001.png'))}
for p in ('web','ios','android'): pics[p]=pt.cap('wave54-final','css-text','bidi/bidi-tab-001',p)
pics['U1U2']=pt.l3_fixed('bidi-tab-001'); pics['PLAIN']=pt.png('positioned-parent-slot-replay/bidi-tab-001-android-PLAIN.png')
def comps8(m):
    from scipy import ndimage
    lab,n=ndimage.label(m,structure=np.ones((3,3))); return [(np.nonzero(lab==i)) for i in range(1,n+1)]
try:
    import scipy
except Exception as e: print('no scipy', e)
for lab,a in pics.items():
    out=[]
    for y0 in (86,106,186,226):
        m=np.zeros(a.shape[:2],bool); m[y0:y0+20]=blue(a)[y0:y0+20]
        cs=ai.comps(m)
        out.append(f'y{y0}: ' + ' '.join(f'[{xs.min()}-{xs.max()}x{ys.min()}-{ys.max()} n{len(xs)}]' for ys,xs in sorted(cs,key=lambda c:c[1].min())))
    print(f'{lab:6}', ' | '.join(out))
