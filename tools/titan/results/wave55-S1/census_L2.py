# S1 own census, L2 U1 (Compose). Under WPT capture a box whose effective box-sizing is not border-box (unset = content-box
# under capture) publishes its DECLARED size as its children's containing block instead of size minus the padding+border
# band (DynamicValueResolver.childContainingBlock + ContainingBlockBands.subtracts). The block changes on an axis iff a px
# (or %) size is declared there AND the band (FIRST-declared px, as pxOf reads) is non-zero. Readers (upper bound): a
# direct child or ANY descendant that is out-of-flow, or carries a percentage / calc / var / viewport value, or a vertical
# writing mode. U2 rides U1's carriers (PercentContentBoxBand: % size + band on a content-box box).
import sys, json; sys.path.insert(0, sys.argv[1]); import irlib
e=json.load(open(irlib.ROOT+'/tools/titan/results/wave55-plan/expectations.json'))
L=e['lanes']['L2-cbb-android']; car=set(L['captureCarriers']['android'])
allcar={}
for ln,l in e['lanes'].items():
    for s in l['captureCarriers'].get('android',[]): allcar.setdefault(s,[]).append(ln[:2])
maymove=set(e['lanes']['L6-android-paint-order']['mayMove']['android'])
def firstpx(c,*types):
    for p in c.get('properties') or []:
        if p['type'] in types and isinstance(p['data'],dict) and 'px' in p['data']: return p['data']['px']
    return None
def declared(c,*types):
    for p in c.get('properties') or []:
        if p['type'] in types and isinstance(p['data'],dict) and (('px' in p['data']) or p['data'].get('type')=='percentage'): return True
    return False
def reads(c):
    pc=irlib.props(c)
    if pc.get('Position') in ('ABSOLUTE','FIXED'): return 'oof'
    if 'WritingMode' in pc and pc['WritingMode']!='HORIZONTAL_TB': return 'vtext'
    s=json.dumps(c.get('properties') or [])
    if 'percentage' in s or '"PERCENT"' in s: return 'spc'
    if 'calc' in s or 'var(' in s or '"VW"' in s or '"VH"' in s: return 'dyn'
    return None
rad={}
for stem,sec,comps,byid,kids in irlib.docs():
    for c in comps:
        pc=irlib.props(c)
        if pc.get('BoxSizing')=='BORDER_BOX': continue
        bx=sum(firstpx(c,t) or 0 for t in (('PaddingLeft','PaddingInlineStart'),('PaddingRight','PaddingInlineEnd'),('BorderLeftWidth',),('BorderRightWidth',)) for t in [t] ) if False else (firstpx(c,'PaddingLeft','PaddingInlineStart') or 0)+(firstpx(c,'PaddingRight','PaddingInlineEnd') or 0)+(firstpx(c,'BorderLeftWidth') or 0)+(firstpx(c,'BorderRightWidth') or 0)
        by=(firstpx(c,'PaddingTop','PaddingBlockStart') or 0)+(firstpx(c,'PaddingBottom','PaddingBlockEnd') or 0)+(firstpx(c,'BorderTopWidth') or 0)+(firstpx(c,'BorderBottomWidth') or 0)
        ax=(declared(c,'Width','InlineSize') and bx!=0) or (declared(c,'Height','BlockSize') and by!=0)
        if not ax: continue
        stack=list(kids.get(c['id'],[])); hit=None
        while stack:
            k=stack.pop(); r=reads(k)
            if r: hit=r; break
            stack.extend(kids.get(k['id'],[]))
        if hit: rad.setdefault(stem,set()).add(hit)
print('U1 upper-bound radius:', len(rad), 'documents')
out=sorted(rad-car) if False else None
for s in sorted(rad):
    tag='CARRIER' if s in car else ('other-lane-carrier '+','.join(allcar[s]) if s in allcar else ('L6-mayMove' if s in maymove else 'UNLISTED'))
    print(f'  {tag:24s} {s} {sorted(rad[s])}')
print('carriers outside my radius:', sorted(car-set(rad)))
print('UNLISTED count:', sum(1 for s in rad if s not in car and s not in allcar and s not in maymove))
