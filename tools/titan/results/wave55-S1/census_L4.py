# S1 own census, L4: U1 = an out-of-flow (own Position ABSOLUTE|FIXED) h1-h6 with >=1 undeclared block margin edge in a
# horizontal writing mode (inherited); also lists the non-heading out-of-flow UA-margin tags (logged-skip). U2 = every
# h1-h6 that hosts element children (the fold gate's domain; the fold verdict itself is the runtime's).
import sys, json; sys.path.insert(0, sys.argv[1]); import irlib
e=json.load(open(irlib.ROOT+'/tools/titan/results/wave55-plan/expectations.json'))
L=e['lanes']['L4-ua-heading-margin']; car={p:set(v) for p,v in L['captureCarriers'].items()}
H={'h1','h2','h3','h4','h5','h6'}; UA={'p','ul','ol','dl','menu','dir','blockquote','figure','pre','hr','fieldset','form'}|H
TOP={'MarginTop','MarginBlockStart','MarginBlock','Margin'}; BOT={'MarginBottom','MarginBlockEnd','MarginBlock','Margin'}
def wm(c,byid):
    while c is not None:
        p=irlib.props(c)
        if 'WritingMode' in p: return p['WritingMode']
        c=irlib.parent(c,byid)
    return 'HORIZONTAL_TB'
u1={}; u1skip={}; u1decl=set(); u2={}
for stem,sec,comps,byid,kids in irlib.docs():
    for c in comps:
        tag=((c.get('meta') or {}).get('sourceTag') or '').lower(); p=irlib.props(c)
        if p.get('Position') in ('ABSOLUTE','FIXED') and tag in UA:
            t=not (TOP & set(p)); b=not (BOT & set(p))
            if not (t or b):
                if tag in H: u1decl.add(stem)
                continue
            if tag not in H: u1skip.setdefault(stem,[]).append(tag); continue
            if wm(c,byid) not in ('HORIZONTAL_TB',): u1skip.setdefault(stem,[]).append(tag+'/vertical'); continue
            u1.setdefault(stem,[]).append(c['id'])
        if tag in H and kids.get(c['id']): u2.setdefault(stem,[]).append(c['id'])
print('U1 heading radius:', len(u1), 'docs', sum(map(len,u1.values())), 'components'); [print('  ',s,v) for s,v in sorted(u1.items())]
print('U1 non-heading / vertical (logged skip):', sorted(u1skip.items()))
print('U1 declared-both-edges abspos headings (identity):', len(u1decl))
print('U2 heading hosts:', len(u2), sorted(u2))
rad=set(u1)|set(u2)
for p in ('ios','android'): print(p,'carriers',len(car[p]),'radius-minus-carriers',sorted(rad-car[p]),'carriers-minus-radius',sorted(car[p]-rad))
