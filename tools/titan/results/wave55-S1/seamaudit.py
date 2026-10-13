# S1 seam audit: for every landed unit, compare the commit's per-file +/- line multiset with its patches'.
import json, subprocess, re, sys, collections
R='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
m=json.load(open(R+'/tools/titan/results/wave55-plan/landing-manifest.json'))
log=subprocess.run(['git','-C',R,'log','--format=%h\t%s','b4f64963..5bbc2a41'],capture_output=True,text=True).stdout.strip().split('\n')
subj2sha={}
for l in log:
    sha,s=l.split('\t',1); subj2sha[s]=sha
SEAMS=set(m['seamFiles']) if isinstance(m['seamFiles'],list) else set(x if isinstance(x,str) else x['path'] for x in m['seamFiles'])
def parse(diff):
    files=collections.OrderedDict(); cur=None; hunks=collections.defaultdict(list)
    for line in diff.split('\n'):
        if line.startswith('diff --git'):
            cur=line.split(' b/',1)[1]; files[cur]=[]
        elif line.startswith('@@') and cur:
            hunks[cur].append(line)
        elif cur and (line.startswith('+') and not line.startswith('+++')) :
            files[cur].append(line)
        elif cur and (line.startswith('-') and not line.startswith('---')):
            files[cur].append(line)
    return files,hunks
out=[]
for u in m['units']:
    if u['kind']=='contingency': continue
    sha=subj2sha.get(u['subject'])
    pf=collections.OrderedDict(); kinds={}
    for p in u['applyOrder']:
        try: txt=open(R+'/'+p).read()
        except FileNotFoundError: out.append(f"{u['lane']} {u['unit']}: patch missing {p}"); continue
        f,h=parse(txt)
        for k,v in f.items():
            pf.setdefault(k,[]).extend(v); kinds.setdefault(k,[]).append(p.split('/')[-1])
    if not sha:
        out.append(f"{u['lane']} {u['unit']}: NO COMMIT (kind {u['kind']}); patches {u['applyOrder']}"); continue
    cd=subprocess.run(['git','-C',R,'show','--format=','--no-renames',sha],capture_output=True,text=True).stdout
    cf,ch=parse(cd)
    same_paths = set(cf)==set(pf)
    bad=[]
    for k in set(cf)|set(pf):
        if collections.Counter(cf.get(k,[]))!=collections.Counter(pf.get(k,[])): bad.append(k)
    seamtouch=[k for k in cf if k in SEAMS or k.endswith('SeamReachabilityTest.kt')]
    # unit patch touching a seam?
    up=u.get('unitPatch'); upseam=[]
    if up:
        f,_=parse(open(R+'/'+up).read()); upseam=[k for k in f if k in SEAMS]
    out.append(f"{u['lane']} {u['unit']} {sha}: paths commit={len(cf)} patches={len(pf)} equalPaths={same_paths} lineMismatch={bad} seamFilesInCommit={[(k.split('/')[-1],len(ch[k])) for k in seamtouch]} unitPatchTouchesSeam={upseam}")
print('\n'.join(out))
