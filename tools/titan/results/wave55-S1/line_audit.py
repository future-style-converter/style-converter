# S1 :LINE audit — every `<file>:<N>[-M]` pointer in the lane notes whose file resolves uniquely on the landed tree; when
# the pointer is followed (same line) by a backtick-quoted snippet, check the snippet occurs within the cited range +-2 on
# the LANDED tree, else on the base 611c9f93 (a base-anchored pointer), else MISS.
import re, subprocess, os, collections
R='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
LANES=['hyphen-ios-linebox','cbb-android','positioned-parent-slot','ua-heading-margin','counter-suffix-rows','android-paint-order','instruments']
tracked=subprocess.run(['git','-C',R,'ls-tree','-r','--name-only','5bbc2a41'],capture_output=True,text=True).stdout.split('\n')
bybase=collections.defaultdict(list)
for t in tracked: bybase[os.path.basename(t)].append(t)
P=re.compile(r'([\w./-]+\.(?:kt|swift|ts|tsx|mjs|py|sh|md|json)):(\d+)(?:-(\d+))?(?:\s*`([^`]{6,80})`)?')
def show(rev,path):
    r=subprocess.run(['git','-C',R,'show',f'{rev}:{path}'],capture_output=True,text=True)
    return r.stdout.split('\n') if r.returncode==0 else None
cache={}
st=collections.Counter(); miss=[]
for lane in LANES:
    for nm in ('_note.md','skeptic.md'):
        f=f'{R}/tools/titan/results/wave55-{lane}/{nm}'
        for i,line in enumerate(open(f),1):
            for m in P.finditer(line):
                fn,a,b,snip=m.group(1),int(m.group(2)),m.group(3),m.group(4)
                cands=[fn] if fn in tracked else bybase.get(os.path.basename(fn),[])
                if len(cands)!=1: st['ambiguous-or-untracked']+=1; continue
                path=cands[0]; b=int(b) if b else a
                if not snip: 
                    L=cache.setdefault(('5bbc2a41',path),show('5bbc2a41',path))
                    st['line-exists' if L and len(L)>=b else 'LINE-BEYOND-EOF']+=1
                    if not(L and len(L)>=b): miss.append((lane,nm,i,fn,a,'beyond EOF on landed'))
                    continue
                s=snip.strip().strip('…').strip()[:40]
                hit=None
                for rev in ('5bbc2a41','611c9f93','b4f64963'):
                    L=cache.setdefault((rev,path),show(rev,path))
                    if L and any(s in x for x in L[max(0,a-3):b+2]): hit=rev; break
                st['snippet@'+(hit or 'MISS')]+=1
                if not hit: miss.append((lane,nm,i,fn,a,s))
print(dict(st))
for x in miss: print('  ',x)
