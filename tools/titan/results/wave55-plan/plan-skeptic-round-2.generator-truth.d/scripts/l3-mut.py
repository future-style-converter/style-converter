import sys, json
sys.path.insert(0, sys.argv[1]); from irlib import *
POS={'RELATIVE','ABSOLUTE','FIXED','STICKY'}
INSETS=('Top','Left','Right','Bottom','InsetInlineStart','InsetInlineEnd','InsetBlockStart','InsetBlockEnd')
def pos(c): return last(c,'Position')
def resolved_inset(c): return any(isinstance(last(c,t),dict) and isinstance(last(c,t).get('px'),(int,float)) for t in INSETS)
def declared_inset(c): return any(has(c,t) and last(c,t)!='auto' for t in INSETS)
def transformed(c):
    for p in props(c):
        t,v=p['type'],p['data']
        if t=='Transform' and isinstance(v,dict) and v.get('list'): return True
        if t in ('Translate','Rotate','Scale','Perspective'): return True
        if t=='TransformStyle' and 'PRESERVE' in str(v): return True
        if t=='WillChange' and ('transform' in str(v).lower() or 'perspective' in str(v).lower() or 'filter' in str(v).lower()): return True
    if pos(c) not in ('ABSOLUTE','FIXED'):
        for p in props(c):
            t,v=p['type'],p['data']
            if t in ('Filter','BackdropFilter') and v not in (None,[],'none','NONE'): return True
            if t=='Contain' and any(k in str(v).upper() for k in ('LAYOUT','PAINT','STRICT','CONTENT')): return True
    return False
def clipped(c): return has(c,'ClipPath') and last(c,'ClipPath') not in (None,'none','NONE',{'type':'none'})
def host_active(D):
    def walk(c,pa,ta,ca):
        p=pos(c)
        if not ca:
            if p=='FIXED' and not ta and declared_inset(c): return True
            if p=='ABSOLUTE' and not pa and not ta and resolved_inset(c): return True
        if not pa and ((p=='ABSOLUTE' and not resolved_inset(c)) or (p=='FIXED' and not declared_inset(c))): return True
        npa= pa or p in POS; nta= ta or transformed(c); nca= ca or clipped(c)
        return any(walk(k,npa,nta,nca) for k in D.kids.get(c['id'],[]))
    return any(walk(r,False,False,False) for r in D.kids.get(None,[]))
def oof(c): return pos(c) in ('ABSOLUTE','FIXED')
def rtl(D,c):
    v,_=D.eff(c,'Direction'); return v=='RTL'
def pxw(c):
    v=last(c,'Width'); return isinstance(v,dict) and isinstance(v.get('px'),(int,float))
def nonzero_h(c,D):
    v=last(c,'Height')
    if isinstance(v,dict) and isinstance(v.get('px'),(int,float)): return v['px']>0
    return bool(c.get('text')) or bool(D.kids.get(c['id']))
U1=[]; U2=[]; inact_docs=set(); stack_docs={}
for sec,st,d in docs():
    D=Doc(d); act=host_active(D)
    for P in D.comps:
        kids=D.kids.get(P['id'],[])
        if pos(P) not in ('ABSOLUTE','FIXED','STICKY','RELATIVE'): continue
        tcb = transformed(P) and any(oof(k) for k in kids)
        if tcb: continue          # positioned-container Box branch
        ok=[k for k in kids if oof(k)]
        if not ok: continue
        if not act:
            U1.append((st,P['id'],len(ok),rtl(D,P))); inact_docs.add(st)
            if not rtl(D,P):
                prev=None
                for k in kids:
                    if oof(k) and prev is not None and nonzero_h(prev,D): stack_docs.setdefault(st,[]).append(k['id'])
                    if oof(k): prev=k
        if rtl(D,P) and pxw(P) and any(has(k,'Left') or has(k,'Right') or has(k,'InsetInlineStart') or has(k,'InsetInlineEnd') for k in ok):
            U2.append((st,P['id'],'active' if act else 'inactive'))
print('U1 host-inactive Column-loop parents with oof children:',len(U1),'children',sum(x[2] for x in U1),'docs',len(inact_docs))
print('   of which RTL parents:',sum(1 for x in U1 if x[3]))
print('U1 STACK docs (LTR parents, an oof child after an oof sibling of non-zero height):',len(stack_docs))
for s in sorted(stack_docs): print('   ',s, stack_docs[s])
print('U2 RTL parents with px Width and an inline-inset oof child:',len(U2),'docs',len({x[0] for x in U2}))
for x in U2: print('   ',x)
