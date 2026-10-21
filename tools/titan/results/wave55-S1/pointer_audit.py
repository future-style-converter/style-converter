# S1 pointer audit over the seven lane notes (+ skeptic.md): (1) every cited repo path / lane-dir file resolves as a
# TRACKED file (or a tracked dir prefix) of the landed tree 5bbc2a41; (2) every `wave5x-<run> <sec>/<test> <platform>
# <P|f> <ssim>` citation equals the scorer's cell (cells.mjs over wave54-final + wave55-open); (3) every `path:LINE`
# pointer whose path is a tracked SOURCE file is reported with the cited line's text on the landed tree.
import re, subprocess, os, sys, json, collections
R='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
LANES=['hyphen-ios-linebox','cbb-android','positioned-parent-slot','ua-heading-margin','counter-suffix-rows','android-paint-order','instruments']
tracked=set(subprocess.run(['git','-C',R,'ls-tree','-r','--name-only','5bbc2a41'],capture_output=True,text=True).stdout.split('\n'))
dirs=set()
for t in tracked:
    p=t
    while '/' in p: p=p.rsplit('/',1)[0]; dirs.add(p)
PATH=re.compile(r'(?<![\w./-])((?:tools|runtimes|apps|schema|converter|docs|fixtures)/[\w./@{},*…+-]+|(?:wave5\d-[\w-]+|\.\./wave5\d-[\w-]+)/[\w./{},*…+-]+|[\w-]+\.(?:txt|py|mjs|sh|json|md|png|patch|kt|swift|ts|tsx|tsv\.gz|log\.txt))')
CELL=re.compile(r'(wave5[45]-(?:final|open|probe)) ([\w./-]+?)(?:\.html)? (ios|android|web) ([Pf]) (0\.\d+|1)\b')
res=collections.Counter(); bad=[]; cells=[]
def candidates(tok, lane):
    tok=tok.rstrip('.,;:)`\'"').replace('…','')
    out=[tok, f'tools/titan/results/wave55-{lane}/{tok}', f'tools/titan/results/{tok}', f'tools/titan/results/wave55-plan/{tok}']
    if tok.startswith('../'): out.append('tools/titan/results/'+tok[3:])
    return out
for lane in LANES:
    for nm in ('_note.md','skeptic.md'):
        f=f'{R}/tools/titan/results/wave55-{lane}/{nm}'
        if not os.path.exists(f): continue
        for i,line in enumerate(open(f),1):
            if line.startswith('TREES:'): continue
            for m in CELL.finditer(line): cells.append((lane,nm,i,m.group(1),m.group(2),m.group(3),m.group(4),m.group(5)))
            for m in PATH.finditer(line):
                tok=m.group(1)
                if any(c in tok for c in '{}*<>'): res['pattern-skipped']+=1; continue
                if '/' not in tok and not tok.endswith(('.txt','.py','.mjs','.sh','.json','.png','.patch','.tsv.gz')): continue
                ok=False
                for c in candidates(tok,lane):
                    c=c.rstrip('/')
                    if c in tracked or c in dirs: ok=True; break
                if ok: res['resolved']+=1
                else:
                    # bare file names: accept if a tracked file with that basename exists anywhere under the lane / plan / gate dirs
                    base=os.path.basename(tok.rstrip('.,;:)`'))
                    hits=[t for t in tracked if t.endswith('/'+base) and ('wave55-' in t or '/' not in tok)]
                    if '/' not in tok and hits: res['resolved-by-basename']+=1
                    else: res['UNRESOLVED']+=1; bad.append((lane,nm,i,tok))
print('paths:',dict(res))
for b in bad: print('  UNRESOLVED',b)
json.dump(cells,open(sys.argv[1],'w'))
print('cell citations:',len(cells))
