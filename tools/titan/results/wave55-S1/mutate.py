# S1 mutation driver: MUTATING <file> sha256 <pre> BEFORE the edit; focused test (expect RED); byte-exact restore from the
# saved bytes; RESTORED <file> sha256 <post> (post must equal pre); focused test again (expect GREEN). Throwaway worktree only.
import sys, json, hashlib, subprocess, time
WT='<scratchpad>/wave55-S1-skeptic'
LOG='<scratchpad>/s1/mutation-log.txt'
def sh(f): return hashlib.sha256(open(f,'rb').read()).hexdigest()
def run(cmd):
    t=time.time(); p=subprocess.run(cmd,shell=True,cwd=WT,capture_output=True,text=True)
    out=p.stdout+p.stderr
    tail=[l for l in out.split('\n') if any(k in l for k in ('tests completed','FAILED','BUILD','Executed','# pass','# fail','Tests ','error:','** TEST'))][-6:]
    return p.returncode, round(time.time()-t), tail
for m in json.load(open(sys.argv[1])):
    f=WT+'/'+m['file']; pre=sh(f); orig=open(f,'rb').read()
    src=orig.decode()
    n=src.count(m['old'])
    with open(LOG,'a') as L: L.write(f"MUTATING {m['file']} sha256 {pre}  ({m['label']}: {m['what']}; occurrences {n})\n")
    if n!=1:
        with open(LOG,'a') as L: L.write(f"  SKIPPED: anchor occurs {n} times\n"); continue
    open(f,'w').write(src.replace(m['old'],m['new']))
    rc1,t1,tail1=run(m['test'])
    open(f,'wb').write(orig); post=sh(f)
    with open(LOG,'a') as L: L.write(f"RESTORED {m['file']} sha256 {post}  ({m['label']}) {'BYTE-EXACT' if post==pre else 'MISMATCH'}\n")
    rc2,t2,tail2=run(m['test'])
    v='OK' if rc1!=0 and rc2==0 and post==pre else 'FAIL'
    with open(LOG,'a') as L:
        L.write(f"  mutated rc={rc1} ({t1}s) {' | '.join(tail1)}\n  restored rc={rc2} ({t2}s) {' | '.join(tail2)}\n  VERDICT {m['label']}: {v} (red -> restore byte-exact -> green)\n")
    print(m['label'], v, rc1, rc2, flush=True)
