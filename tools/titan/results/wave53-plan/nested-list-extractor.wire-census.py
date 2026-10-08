import json,glob,os
base='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/runs/wave52-ship/sections'
nested_li=set(); list_in_li=set(); runs_before_text=set(); n=0
for f in sorted(glob.glob(base+'/*/per-test-ir/*.json')):
    n+=1
    d=json.load(open(f)); comps=d.get('components',[])
    byid={c['id']:c for c in comps}
    def anc_tags(c):
        out=[]; p=(c.get('slot') or {}).get('parent')
        while p and p in byid:
            out.append((byid[p].get('meta') or {}).get('sourceTag')); p=(byid[p].get('slot') or {}).get('parent')
        return out
    stem=os.path.basename(f)[:-5]
    for c in comps:
        tag=(c.get('meta') or {}).get('sourceTag')
        a=anc_tags(c)
        if tag=='li' and 'li' in a: nested_li.add(stem)
        if tag in ('ol','ul','menu','dir') and 'li' in a: list_in_li.add(stem)
        ps=c.get('pseudos') or {}
        if (c.get('meta') or {}).get('runs') and (ps.get('before') or {}).get('_text'): runs_before_text.add(stem)
print('docs',n); print('nested li',sorted(nested_li)); print('list inside li',sorted(list_in_li)); print('runs+before _text',sorted(runs_before_text))
