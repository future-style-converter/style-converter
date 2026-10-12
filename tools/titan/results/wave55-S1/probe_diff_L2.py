# S1: empirical check of the L2 U1 radius — wave54-probe ran HEAD + 5b333c8a (= L2 U1's code) on device; every document of
# MY static upper-bound radius whose section was probed is pixel-diffed (android) against wave54-final AND wave55-open.
import sys, os, re
import numpy as np
from PIL import Image
R='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/runs'
lines=open(sys.argv[1]).read().split('\n')
docs=[(l.split()[-2] if False else re.search(r'(wpt__\S+)',l).group(1), l.split()[0]) for l in lines if 'wpt__' in l and not l.startswith('carriers')]
def load(run,sec,stem):
    p=f'{R}/{run}/sections/{sec}/android-screenshots/{stem}.png'
    return np.asarray(Image.open(p).convert('RGB')).astype(int) if os.path.exists(p) else None
moved=[]; unprobed=[]
for stem,cls in docs:
    sec=stem.split('__')[1]
    a=load('wave54-final',sec,stem); b=load('wave54-probe',sec,stem); c=load('wave55-open',sec,stem)
    if b is None: unprobed.append((stem,cls)); continue
    if a is None: continue
    d=(a.shape!=b.shape) or int((np.abs(a-b).sum(axis=2)>0).sum())
    d2=(c is None) or (c.shape!=b.shape) or int((np.abs(c-b).sum(axis=2)>0).sum())
    if d and d2: moved.append((stem,cls,d))
print('probed radius docs moved at wave54-probe vs wave54-final and wave55-open:')
for m in moved: print('  ',m)
print('unprobed (section not in wave54-probe):', len(unprobed), sorted(set(s.split("__")[1] for s,_ in unprobed)))
