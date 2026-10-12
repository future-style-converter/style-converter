# S1 own census, L3 (Compose). Column-loop pairs: an ABSOLUTE/FIXED child whose direct parent is ABSOLUTE/FIXED/STICKY
# (PositionedParentFlowSlot.applies). U1 changes only host-INACTIVE documents with an LTR parent; host model here:
# ACTIVE iff some ABSOLUTE/FIXED box has no positioned (non-static) / transformed ancestor (the canvas hoist), a coarse
# stand-in for CanvasRootHoist.anyOutOfFlowBox -> reported both ways. U2: RTL parent (merged Direction) with a px
# Width/InlineSize and exactly one physical inline inset (Left xor Right) on the child, any host state.
import sys, json; sys.path.insert(0, sys.argv[1]); import irlib
e=json.load(open(irlib.ROOT+'/tools/titan/results/wave55-plan/expectations.json'))
L=e['lanes']['L3-positioned-parent-slot']; car=set(L['captureCarriers']['android'])
named={x.split('.html')[0] for x in L['mustNotMove'] if x.endswith(' android')}
pred={p['cell'].split('.html')[0] for p in L['predictions']}
def stem2t(s): return s[len('wpt__'):].replace('__','/')
def inh(c,byid,key,default=None):
    while c is not None:
        p=irlib.props(c)
        if key in p: return p[key]
        c=irlib.parent(c,byid)
    return default
OOF=('ABSOLUTE','FIXED'); TCB={'Transform','Perspective','Filter','BackdropFilter','WillChange','Contain','Translate','Rotate','Scale','TransformStyle'}; CB=('ABSOLUTE','FIXED','STICKY')
pairs={}; u2={}; host={}
for stem,sec,comps,byid,kids in irlib.docs():
    active=False
    for c in comps:
        if irlib.props(c).get('Position') in OOF:
            a=irlib.parent(c,byid); anc=False
            while a is not None:
                pa=irlib.props(a)
                if pa.get('Position') not in (None,'STATIC') or 'Transform' in pa: anc=True; break
                a=irlib.parent(a,byid)
            if not anc: active=True
    host[stem]=active
    for c in comps:
        pc=irlib.props(c); par=irlib.parent(c,byid)
        if par is None or pc.get('Position') not in OOF: continue
        pp=irlib.props(par)
        if pp.get('Position') not in CB: continue
        if TCB & set(pp): continue  # a transform-CB parent with an out-of-flow child takes the Box branch (ComponentRenderer isPositionedContainer)
        rtl=str(inh(par,byid,'Direction','LTR')).upper()=='RTL'
        pairs.setdefault(stem,[]).append((par['id'],c['id'],rtl))
        w=pp.get('Width') or pp.get('InlineSize'); pxw=isinstance(w,dict) and 'px' in w
        if rtl and pxw and (('Left' in pc) != ('Right' in pc)): u2.setdefault(stem,[]).append(c['id'])
print('Column-loop pairs:', sum(map(len,pairs.values())), 'children in', len(pairs), 'documents')
u1=[s for s,v in pairs.items() if not host[s] and any(not r for *_,r in v)]
print('U1 (host-inactive by the coarse model, LTR parent):', len(u1), 'documents;  U2:', len(u2), 'documents', sum(map(len,u2.values())), 'children')
rad=set(u1)|set(u2)
for s in sorted(set(pairs)):
    t=stem2t(s); cls='CARRIER' if s in car else ('named-mustNotMove' if t in named else ('prediction' if t in pred else 'complement'))
    print(f"  {'U1' if s in u1 else '  '} {'U2' if s in u2 else '  '} host={'A' if host[s] else 'i'} {cls:18s} {s}")
print('carriers outside my U1|U2:', sorted(car-rad))
print('my U1|U2 outside carriers and outside the named/prediction lists:', sorted(s for s in rad-car if stem2t(s) not in named|pred))

ctl=set(l.strip().strip("'") for l in open(irlib.ROOT+'/tools/titan/results/wave55-positioned-parent-slot/census/census.wave54-final.out.txt').read().split("U1 controls (reached, identity / CLAMP / AUTO only): 13 [")[1].split("]")[0].split(", "))
print('lane-census U1 controls (13):', len(ctl))
print('my U1|U2 outside carriers AND outside the lane census controls:', sorted(rad-car-ctl))
