# own IR loader for the generator-truth skeptic (independent of every brief script)
import json, glob, os, re
RUN='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/runs/'
def docs(run='wave54-final'):
    out={}
    for f in sorted(glob.glob(RUN+run+'/sections/*/per-test-ir/*.json')):
        sec=f.split('/sections/')[1].split('/')[0]
        stem=os.path.basename(f)[:-5]
        out[stem]=(sec,json.load(open(f)))
    return out
def comps(d):
    c=d.get('components',[])
    return c if isinstance(c,list) else list(c.values())
def props(c):
    return {p['type']:p.get('data') for p in c.get('properties',[])}
def parents(d):
    byid={c['id']:c for c in comps(d)}
    par={c['id']:(c.get('slot') or {}).get('parent') for c in comps(d)}
    return byid,par
def chain(cid,par):
    out=[]
    p=par.get(cid)
    while p:
        out.append(p); p=par.get(p)
    return out
def stem_to_test(stem):
    return stem[5:].replace('__','/')+'.html'
