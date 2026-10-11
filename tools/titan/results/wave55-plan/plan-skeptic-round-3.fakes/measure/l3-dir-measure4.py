# plan-skeptic-round-3.fakes/measure — fix-r3 threshold measurement (L3 dir-style-02a rows): passing pictures vs added-ink fakes; read-only.
# same-engine render variance: one string at two positions (twin rows), XOR of ink masks at the best ±1 alignment
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

Y0 = lambda k: 16 + 20 * (k - 1)
def crop_ink(a, k):
    m = ink(a)[Y0(k):Y0(k)+20]; xs = np.nonzero(m.any(axis=0))[0]; return m, (xs.min(), xs.max()) if len(xs) else None
def xor_best(m, r, span=1):
    best = None
    for dy in range(-span, span+1):
        for dx in range(-span, span+1):
            rs = np.roll(np.roll(r, dy, axis=0), dx, axis=1)
            v = int((m ^ rs).sum()); best = v if best is None or v < best else best
    return best
for lab in ('ref','web','ios','android','U1U2','U1'):
    a = pics[lab]
    out=[]
    for k, t in ((6,3),(9,3),(5,2),(8,2),(9,6)):
        m, bm = crop_ink(a, k); r, br = crop_ink(a, t)
        if bm is None or br is None: out.append(f'r{k}~r{t}: n/a'); continue
        # align by bbox left (measurement only: the mirrored iOS rows sit 219 px right)
        sh = bm[0] - br[0]; rr = np.roll(r, sh, axis=1)
        out.append(f'r{k}~r{t}: xor@bbox {int((m ^ rr).sum())} (shift {sh:+d}) best±1 {xor_best(m, rr)}')
    print(f'{lab:8}', ' | '.join(out))
# row 7 vs base row 7 text (x >= 21)
for lab in ('ref','web','U1U2','U1'):
    a = pics[lab]; b = pics['android' if lab in ('U1U2','U1') else lab]
    m = ink(a)[Y0(7):Y0(7)+20, 21:]; r = ink(b)[Y0(7):Y0(7)+20, 21:]
    print(f'{lab:8} r7 text vs base: xor {int((m^r).sum())} best±1 {xor_best(m, r)}')
