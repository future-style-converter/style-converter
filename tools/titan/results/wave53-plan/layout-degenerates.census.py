import json, glob, os, re, sys
ROOT='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
cells=json.load(open(sys.argv[1]))['wave52-ship']
def kw(d):
    if isinstance(d,str): return d.upper().replace('-','_')
    if isinstance(d,dict):
        for k in ('value','keyword','type'):
            if isinstance(d.get(k),str): return d[k].upper().replace('-','_')
    return None
def props(c): return {p['type']:p.get('data') for p in c['properties']}
FLOATS={'LEFT','RIGHT','INLINE_START','INLINE_END'}
def isfloat(c):
    p=props(c); return kw(p.get('Float')) in FLOATS
def oof(c):
    p=props(c); return kw(p.get('Position')) in ('ABSOLUTE','FIXED') or kw(p.get('Display')) in ('NONE','CONTENTS')
def bfc_reason(c):
    p=props(c); r=[]
    d=kw(p.get('Display'))
    if d in ('FLOW_ROOT','FLEX','GRID','TABLE'): r.append('display:'+d)
    for k in ('Overflow','OverflowX','OverflowY'):
        v=p.get(k)
        vv = kw(v) if not isinstance(v,dict) else kw(v.get('x') or v.get('value'))
        if v is not None and vv not in (None,'VISIBLE','CLIP'): r.append(k+':'+str(vv))
    ct=p.get('Contain')
    if isinstance(ct,list):
        s={str(x).upper() for x in ct}
        if s & {'LAYOUT','PAINT','STRICT','CONTENT'}: r.append('contain:'+','.join(sorted(s)))
    elif isinstance(ct,str) and ct.upper() in ('LAYOUT','PAINT','STRICT','CONTENT'): r.append('contain:'+ct)
    if 'ColumnCount' in p or 'ColumnWidth' in p: r.append('multicol')
    return r
def px(d):
    if isinstance(d,dict) and isinstance(d.get('px'),(int,float)): return d['px']
    return None
broad=[]; narrow=[]; cis=[]
for f in sorted(glob.glob(ROOT+'/tools/titan/runs/wave52-ship/sections/*/per-test-ir/*.json')):
    sec=f.split('/sections/')[1].split('/')[0]
    stem=os.path.basename(f)[len('wpt__'+sec+'__'):-5]
    test='css/'+sec+'/'+stem.replace('__','/')+'.html' if sec!='CSS2' else 'css/CSS2/'+stem.replace('__','/')+'.html'
    doc=json.load(open(f)); comps=doc['components']
    kids={}
    for c in comps:
        par=(c.get('slot') or {}).get('parent') or '__ROOT__'
        kids.setdefault(par,[]).append(c)
    byid={c['id']:c for c in comps}
    anyclear=any('Clear' in props(c) for c in comps)
    for par,ch in kids.items():
        fl=[i for i,c in enumerate(ch) if isfloat(c)]
        if not fl: continue
        later=[(i,c,bfc_reason(c)) for i,c in enumerate(ch) if i>fl[0] and not isfloat(c) and not oof(c) and bfc_reason(c)]
        if not later: continue
        rec={'test':test,'parent':par,'children':len(ch),'floats':len(fl),'bfc':[(c['id'],r) for i,c,r in later]}
        broad.append(rec)
        # narrow gate: all in-flow children before the last are floats; last is BFC; floats px sized; no clear; no same-side runs; parent no text
        inflow=[c for c in ch if not oof(c)]
        pc=byid.get(par)
        ok = (inflow and not isfloat(inflow[-1]) and bfc_reason(inflow[-1]) and all(isfloat(c) for c in inflow[:-1])
              and not anyclear and (pc is None or not pc.get('text')))
        if ok:
            sides=[kw(props(c).get('Float')) for c in inflow[:-1]]
            run=any(sides[i]==sides[i+1] for i in range(len(sides)-1))
            sized=all(px(props(c).get('Width')) is not None and px(props(c).get('Height')) is not None for c in inflow[:-1])
            rec2=dict(rec, sides=sides, sameSideRun=run, pxSized=sized, bfcProps={k:v for k,v in props(inflow[-1]).items() if k in ('Width','Contain','Display','OverflowX','OverflowY','Overflow','BackgroundColor')})
            narrow.append(rec2)
        bl=inflow[-1] if inflow else None
    for c in comps:
        ct=props(c).get('Contain')
        if isinstance(ct,list) and any(str(x).upper() in ('INLINE_SIZE','SIZE','STRICT') for x in ct):
            cis.append((test,c['id'],ct,kw(props(c).get('Display')), props(c).get('Width')))
def status(t):
    return {pl:(('P' if cells[t+'|'+pl]['pass'] else 'f')+' %.4f'%cells[t+'|'+pl]['ssim']) if (t+'|'+pl) in cells else '—' for pl in ('web','ios','android')}
out={'broad':[],'narrow':[],'containInlineSizeOrSize':[]}
for r in broad: r['status']=status(r['test']); out['broad'].append(r)
for r in narrow: r['status']=status(r['test']); out['narrow'].append(r)
for t,i,ct,d,w in cis: out['containInlineSizeOrSize'].append({'test':t,'id':i,'contain':ct,'display':d,'width':w,'status':status(t)})
json.dump(out,open(sys.argv[2],'w'),indent=1)
print('broad shapes',len(broad),'tests',len({r['test'] for r in broad}))
print('narrow shapes',len(narrow),'tests',len({r['test'] for r in narrow}))
for r in narrow: print('  N',r['test'],r['sides'],'run' if r['sameSideRun'] else '', 'px' if r['pxSized'] else 'NOTPX', r['status'])
print('contain inline-size/size comps',len(cis),'tests',len({t for t,*_ in cis}))
