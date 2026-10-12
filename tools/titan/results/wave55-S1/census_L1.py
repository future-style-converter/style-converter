# S1 own census, L1 NB-ios: upper-bound radius = documents with a text-bearing component whose EFFECTIVE LineHeight is the
# declared keyword `normal` (own, else inherited through slot.parent; an own `All` reset drops own + inherited, as the
# renderer's AllReset does) — the declaredNormal guard; WPT capture holds for every corpus doc; exactness / horizontal /
# route are left to the lane's predicate (so this is a SUPERSET of the radius by construction).
import sys, json; sys.path.insert(0, sys.argv[1]); import irlib
e=json.load(open(irlib.ROOT+'/tools/titan/results/wave55-plan/expectations.json'))
L=e['lanes']['L1-hyphen-ios-linebox']; car=set(L['captureCarriers']['ios'])
def lh(c,byid):
    while c is not None:
        p=irlib.props(c)
        if 'All' in p: return None
        if 'LineHeight' in p: return p['LineHeight']
        c=irlib.parent(c,byid)
    return None
def is_normal(d): return isinstance(d,dict) and d.get('original')=='normal'
def vertical(c,byid):
    while c is not None:
        p=irlib.props(c)
        if 'WritingMode' in p: return p['WritingMode'] not in ('HORIZONTAL_TB',)
        c=irlib.parent(c,byid)
    return False
radius={}; 
for stem,sec,comps,byid,kids in irlib.docs():
    runs=[]
    for c in comps:
        if not (c.get('text') or (c.get('meta') or {}).get('runs')): continue
        d=lh(c,byid)
        if is_normal(d): runs.append((c['id'], 'V' if vertical(c,byid) else 'H'))
    if runs: radius[stem]=runs
print('radius documents', len(radius), 'components', sum(len(v) for v in radius.values()))
for s in sorted(radius):
    tag='CARRIER' if s in car else 'not-carrier'
    print(f'  {tag:12s} {s}  comps={len(radius[s])} vertical={sum(1 for _,v in radius[s] if v=="V")}')
print('carriers not in radius:', sorted(car-set(radius)))
