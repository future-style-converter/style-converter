# Skeptic static IR census (independent of the lane's scripts): RS root pairs and W1 joins.
import json, glob, os, re, sys
RUN = sys.argv[1]
INLINE = set('span a b i em strong code small sub sup u s q abbr cite time label mark bdi bdo samp kbd var img input select button textarea output meter progress ruby rt rb'.split())
PRESERVE = {'PRE','PRE_WRAP','PRE_LINE','BREAK_SPACES','pre','pre-wrap','pre-line','break-spaces'}
def kw(d):
    if isinstance(d,(str,int,float)): return str(d)
    if isinstance(d,dict):
        for k in ('keyword','value','type'):
            if isinstance(d.get(k),(str,int,float)): return str(d[k])
    return None
def disp(c):
    for p in c.get('properties',[]):
        if p['type']=='Display':
            d=p['data']; k = d if isinstance(d,str) else (d.get('keyword') or d.get('type')) if isinstance(d,dict) else None
            if isinstance(k,str): return k.lower().replace('_','-')
    return None
def inline_level(c):
    d=disp(c)
    if d is not None: return d.startswith('inline')
    t=(c.get('meta') or {}).get('sourceTag'); return bool(t) and t.lower() in INLINE
rs_docs={}; rs_pairs=0; w1_hosts=[]; w1_inherit_candidates=[]; tables=[]
ws_decl=[]; font_decl={}
for f in sorted(glob.glob(f'tools/titan/runs/{RUN}/sections/*/per-test-ir/*.json')):
    doc=json.load(open(f)); comps=doc['components']; ids={c['id'] for c in comps}
    byname={c.get('name'):c for c in comps}
    stem=os.path.basename(f)[:-5]
    roots=[c for c in comps if not ((c.get('slot') or {}).get('parent') in ids)]
    body=next((c for c in comps if (c.get('meta') or {}).get('role')=='body-root'),None)
    bws=None; bdisp=None
    if body:
        for p in body['properties']:
            if p['type']=='WhiteSpace': bws=kw(p['data'])
        bdisp=disp(body)
    n=0
    for a,b in zip(roots,roots[1:]):
        if (a.get('meta') or {}).get('role')!='ws-after': continue
        if bws in PRESERVE: continue
        if not (inline_level(a) and inline_level(b)): continue
        n+=1
    if n:
        rs_docs[stem]=n; rs_pairs+=n
        if bdisp and 'table' in bdisp: tables.append(stem)
        if bws: ws_decl.append(stem)
        if body: font_decl[stem]=sorted({p['type'] for p in body['properties'] if p['type'] in ('FontSize','FontFamily','LetterSpacing','WordSpacing','FontWeight','FontStyle','LineHeight','TextAlign','Direction','WritingMode','FontVariant','FontStretch','TextIndent','FontFeatureSettings')})
    # W1
    childrenOf={}
    for c in comps:
        par=(c.get('slot') or {}).get('parent')
        if par in ids: childrenOf.setdefault(par,[]).append(c)
    for h in comps:
        runs=(h.get('meta') or {}).get('runs')
        if not runs: continue
        hy=[kw(p['data']) for p in h['properties'] if p['type']=='Hyphens']
        own_auto = any((x or '').upper()=='AUTO' for x in hy)
        kids=childrenOf.get(h['id'],[])
        def find(key):
            for k in kids:
                if k.get('name')==key or k.get('id')==key: return k
        for i,r in enumerate(runs):
            if not isinstance(r,dict) or 'child' not in r: continue
            if i==0 or i==len(runs)-1: continue
            pr,nx=runs[i-1],runs[i+1]
            if not ('text' in pr and 'text' in nx): continue
            if not pr['text'] or not nx['text']: continue
            if re.match(r'[ \t\n\r\f]',pr['text'][-1]) or re.match(r'[ \t\n\r\f]',nx['text'][0]): continue
            m=find(r['child'])
            if m is None: continue
            # Compose admits + tag ring
            tag=((m.get('meta') or {}).get('sourceTag') or '').lower()
            ok = tag in ('span','time','data') and not childrenOf.get(m['id']) and not (m.get('meta') or {}).get('runs') and not (m.get('meta') or {}).get('decorations')
            oof=tr=False
            if ok:
                for p in m['properties']:
                    t=p['type']
                    if t=='Position':
                        if (kw(p['data']) or '').upper() in ('ABSOLUTE','FIXED'): oof=True
                        else: ok=False;break
                    elif t=='Color':
                        a=((p['data'] or {}).get('srgb') or {}).get('a') if isinstance(p['data'],dict) else None
                        if isinstance(a,(int,float)) and a<=0: tr=True
                        else: ok=False;break
                    elif t!='Hyphens': ok=False;break
            inert= ok and oof and tr
            if inert and own_auto: w1_hosts.append((stem,h['id']))
            elif inert: w1_inherit_candidates.append((stem,h['id'],hy))
print('RS: docs',len(rs_docs),'pairs',rs_pairs,'table-body docs',tables,'body white-space decl',ws_decl)
for k,v in sorted(rs_docs.items()): print(f'  {v:3d} {k}  bodyFont={font_decl.get(k)}')
print('W1 joined hosts', len(w1_hosts)); [print('  ',x) for x in w1_hosts]
print('W1 inert mid-word members NOT own-auto (manual/none/inherited):', len(w1_inherit_candidates)); [print('  ',x) for x in w1_inherit_candidates]
