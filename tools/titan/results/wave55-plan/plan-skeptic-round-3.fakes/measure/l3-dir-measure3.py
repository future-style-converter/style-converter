# plan-skeptic-round-3.fakes/measure — fix-r3 threshold measurement (L3 dir-style-02a rows): passing pictures vs added-ink fakes; read-only.
# candidate metrics for the L3 dir rows on passing pictures and on added-ink fakes
import os, numpy as np
from PIL import Image
import os, numpy as np
from PIL import Image
P='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave55-plan'
ROOT='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
import importlib.util
spec = importlib.util.spec_from_file_location('pt', os.path.join(P, 'probes-teeth.py')); pt = importlib.util.module_from_spec(spec); spec.loader.exec_module(pt)
REF=f'{pt.REFS}/selectors/dir-style-02a.png'
pics={}
print(os.path.exists(REF), REF)
for lab in ('web','ios','android'):
    pics[lab]=pt.cap('wave54-final','selectors','dir-style-02a',lab)
import glob
refs=[REF]
pics['ref']=pt.A(Image.open(refs[0]))
for arm in ('U1U2','U1','PLAIN'): pics[arm]=pt.png(f'positioned-parent-slot-replay/dir-style-02a-android-{arm}.png')
ink=lambda a: a.min(axis=2)<200
dark=lambda a: a.min(axis=2)<120
def rowm(a,k):
    y0=16+20*(k-1); b=ink(a)[y0:y0+20]; d=dark(a)[y0:y0+20]; return int(b.sum()), int(d.sum())

base = {lab: pics[lab] for lab in ('ref','web','ios','android')}
Y0 = lambda k: 16 + 20 * (k - 1)
def rowprof(a, y0): return ink(a)[y0-4:y0+24].sum(axis=1).astype(float)
def xc(p, q, span=4):
    best=(-1,0)
    for s in range(-span, span+1):
        qs=np.array([q[i-s] if 0<=i-s<len(q) else 0 for i in range(len(p))])
        den=np.sqrt((p*p).sum()*(qs*qs).sum()); c=(p*qs).sum()/den if den else 0
        best=max(best,(c,s))
    return best
def metrics(a, k, lab):
    y0=Y0(k); m=ink(a)
    band=m[y0:y0+20]; mass=int(band.sum()); lead=int(band[:, :21].sum()); text=int(band[:, 21:].sum())
    r3=m[Y0(3):Y0(3)+20]; r3mass=int(r3.sum()); r3lead=int(r3[:, :21].sum())
    bm = ink(base['android' if lab not in base else lab])[y0:y0+20]
    btext=int(bm[:, 21:].sum())
    ys=np.nonzero(band.any(axis=1))[0]; ry=np.nonzero(r3.any(axis=1))[0]; bys=np.nonzero(bm[:,21:].any(axis=1))[0]
    rp=xc(rowprof(a,y0), rowprof(pics['ref'],y0))
    rp3=xc(rowprof(a,y0), rowprof(a,Y0(3)))
    xs=np.nonzero(band.any(axis=0))[0]; xs3=np.nonzero(r3.any(axis=0))[0]
    return dict(mass=mass, twin=round(mass/r3mass,3), text_vs_base=round(text/btext,3) if btext else None, lead=lead, r3lead=r3lead,
                yext=(int(ys.min()),int(ys.max())) if len(ys) else None, r3y=(int(ry.min()),int(ry.max())), basey=(int(bys.min()),int(bys.max())) if len(bys) else None,
                x=(int(xs.min()),int(xs.max())) if len(xs) else None, r3x=(int(xs3.min()),int(xs3.max())), rpeak=round(rp[0],3), rshift=rp[1], rp3=round(rp3[0],3))
def show(name, a, lab='U1U2'):
    for k in (6,7,9):
        print(f'{name:22} r{k}', metrics(a,k,lab))
for lab in ('ref','web','ios','android','U1U2','U1','PLAIN'):
    show(lab, pics[lab], lab)
F = pics['U1U2']
sh = pt.shift
def band_ghost(a, dy):
    o=a.copy()
    for k in (6,7,9):
        y0=Y0(k); b=a[y0-2:y0+18].copy(); o[y0-2:y0+18]=np.minimum(b, sh(b,0,dy))
    return o
def band_hdup(a, dx):
    o=a.copy()
    for k in (6,7,9):
        y0=Y0(k); b=a[y0:y0+20].copy(); o[y0:y0+20]=np.minimum(b, sh(b,dx,0))
    return o
def dot2(a, where):
    o=a.copy()
    for k in (6,7,9):
        y0=Y0(k); d=a[y0+12:y0+18, 16:21].copy()   # the dot cell
        if where=='left': o[y0+12:y0+18, 12:17]=np.minimum(o[y0+12:y0+18, 12:17], d)
        if where=='end':
            xs=np.nonzero(ink(a)[y0:y0+20].any(axis=0))[0]; x1=xs.max()+2
            o[y0+12:y0+18, x1:x1+5]=np.minimum(o[y0+12:y0+18, x1:x1+5], d)
    return o
def stray(a, size, where):
    o=a.copy()
    for k in (6,7,9):
        y0=Y0(k); m=ink(a)[y0:y0+20]; col=a[y0:y0+20][m.min(axis=None) if False else m][0] if m.any() else np.array([0,0,0])
        # the row's darkest ink colour
        sub=a[y0:y0+20]; idx=np.unravel_index(np.argmin(sub.sum(axis=2)), sub.shape[:2]); col=sub[idx]
        if where=='gap':      # in the first word gap after x 60, at mid-height
            cols=m.any(axis=0); xg=next(x for x in range(60, 150) if not cols[x:x+size].any())
            o[y0+8:y0+8+size, xg:xg+size]=col
        if where=='above':    # above the x-height, inside the band, over a glyph-free spot
            o[y0+1:y0+1+size, 100:100+size]=col
        if where=='inside':   # over glyphs (mid text) at baseline-2
            o[y0+10:y0+10+size, 80:80+size]=col
    return o
for dy in (3,5,7): show(f'band ghost +{dy}y', band_ghost(F,dy))
show('whole ghost +7y', np.minimum(F, sh(F,0,7))); show('whole ghost -12y', np.minimum(F, sh(F,0,-12)))
for dx in (1,2,3,5): show(f'band hdup +{dx}x', band_hdup(F,dx))
show('dot doubled left', dot2(F,'left')); show('dot doubled end', dot2(F,'end'))
for s in (2,3): 
    for w in ('gap','above','inside'): show(f'stray {s}x{s} {w}', stray(F,s,w))
