# S1 census helper: loads the wave54-final per-test IR (byte-identical to wave55-open) into a doc model.
import json, glob, os, collections
ROOT='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
def docs(run='wave54-final'):
    for p in sorted(glob.glob(f'{ROOT}/tools/titan/runs/{run}/sections/*/per-test-ir/*.json')):
        stem=os.path.basename(p)[:-5]; sec=p.split('/sections/')[1].split('/')[0]
        d=json.load(open(p)); comps=d['components']
        byid={c['id']:c for c in comps}
        kids=collections.defaultdict(list)
        for c in comps:
            par=(c.get('slot') or {}).get('parent')
            if par: kids[par].append(c)
        yield stem, sec, comps, byid, kids
def props(c):
    m={}
    for p in c.get("properties") or []: m[p["type"]]=p["data"]  # LAST declaration wins (the runtimes read lastOrNull)
    return m
def parent(c,byid):
    par=(c.get('slot') or {}).get('parent'); return byid.get(par) if par else None
