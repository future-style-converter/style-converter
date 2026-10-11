import importlib.util, sys, numpy as np
from PIL import Image
P='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave55-plan'
sys.argv=['x','wave54-final']
spec=importlib.util.spec_from_file_location('hl', P+'/hyphen-ios-linebox.geometry.py')
src=open(P+'/hyphen-ios-linebox.geometry.py').read()
ns={'__file__': P+'/hyphen-ios-linebox.geometry.py'}; exec(src.split("\nfor t in ('001', '002', '003', '004'):")[0].split('args = sys.argv')[0] if 'args = sys.argv' in src else src.split('\nmode =')[0], ns)
lines=ns['lines']; R='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools'
def cropm(im, ln, pred):
    a=np.asarray(im.convert('RGB')).astype(int)[ln[0]-3:ln[1]+4, :140]; return pred(a)
core=lambda a: a.max(axis=2)<110
ink=lambda a: a.min(axis=2)<200
for t in ('001','003','004'):
    for lab,path in (('ios',f'{R}/titan/runs/wave54-final/sections/css-text/ios-screenshots/wpt__css-text__hyphens__hyphenate-character-{t}.png'),
                     ('web',f'{R}/titan/runs/wave54-final/sections/css-text/screenshots/wpt__css-text__hyphens__hyphenate-character-{t}.png'),
                     ('ref',f'{R}/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text/hyphens__hyphenate-character-{t}.png'),
                     ('Rm',f'{P}/hyphen-ios-linebox-replay/{t}-ios-Rm.png')):
        im=Image.open(path).convert('RGB'); ls=lines(im, ns['DIV0'][t])
        # group lines by identical width (same string) and compare every pair
        sig={}
        for ln in ls: sig.setdefault((ln[3]-ln[2], ln[1]-ln[0]), []).append(ln)
        res=[]
        for k,v in sig.items():
            for i in range(1,len(v)):
                for pred,nm in ((core,'core'),(ink,'ink')):
                    a,b=cropm(im,v[0],pred),cropm(im,v[i],pred); h=min(len(a),len(b))
                    res.append(f'{nm} y{v[0][0]}~y{v[i][0]} n{a.sum()}/{b.sum()} xor{int((a[:h]^b[:h]).sum())}')
        print(t, lab, '; '.join(res))
