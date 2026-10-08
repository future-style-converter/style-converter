import json, glob, os, sys
ROOT='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
cells=json.load(open(sys.argv[1]))['wave52-ship']
def status(t):
    return {pl:(('P' if cells[t+'|'+pl]['pass'] else 'f')+' %.4f'%cells[t+'|'+pl]['ssim']) if (t+'|'+pl) in cells else '—' for pl in ('web','ios','android')}
pa=[]; frag=[]
for f in sorted(glob.glob(ROOT+'/tools/titan/runs/wave52-ship/sections/*/per-test-ir/*.json')):
    sec=f.split('/sections/')[1].split('/')[0]
    stem=os.path.basename(f)[len('wpt__'+sec+'__'):-5]
    test='css/'+sec+'/'+stem.replace('__','/')+'.html'
    comps=json.load(open(f))['components']
    byid={c['id']:c for c in comps}
    P=lambda c:{p['type']:p.get('data') for p in c['properties']}
    n_pa=sum(1 for c in comps if 'PositionArea' in P(c))
    if n_pa:
        # position-area boxes whose baked Top/Left are both 0 (inset within the area cell)
        z=sum(1 for c in comps if 'PositionArea' in P(c) and all((P(c).get(k) or {}).get('px')==0 for k in ('Top','Left')))
        pa.append({'test':test,'positionAreaBoxes':n_pa,'bakedTopLeftZero':z,'status':status(test)})
    # anchors inside a multicol ancestor
    def anc(c):
        while True:
            par=(c.get('slot') or {}).get('parent')
            if not par or par not in byid: return
            c=byid[par]; yield c
    for c in comps:
        if 'AnchorName' in P(c) and any(('ColumnCount' in P(a) or 'ColumnWidth' in P(a)) for a in anc(c)):
            vert=any(str(P(a).get('WritingMode','')).startswith('VERTICAL') for a in anc(c))
            frag.append({'test':test,'vertical':vert,'status':status(test)}); break
json.dump({'positionArea':pa,'anchorInMulticol':frag},open(sys.argv[2],'w'),indent=1)
print('positionArea tests',len(pa))
for r in pa: print('  ',r['test'],r['positionAreaBoxes'],r['bakedTopLeftZero'],r['status'])
print('anchor in multicol tests',len(frag))
for r in frag: print('  ',r['test'],'vertical' if r['vertical'] else '',r['status'])
