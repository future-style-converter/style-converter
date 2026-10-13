# S1 own census, L6 U1+U2 (Compose). Superset of the documents whose capture CAN move: a component with a visible border
# band on any side (an explicit non-zero px width with a non-none style, OR a visible style with no width declared = the
# 3-dp `medium` default; physical, logical and shorthand forms), that does NOT clip (fix-r1's `!config.overflow.shouldClip`:
# OverflowX/OverflowY/Overflow not HIDDEN|CLIP|SCROLL|AUTO), and that has something to paint inside the band: a non-<br>
# child, own text / runs, or a ::before/::after pseudo; plus every bordered component under a non-1 Opacity (the band moves
# inside the opacity group). Compared with captureCarriers ∪ mayMove ∪ every lane's android carriers ∪ ring-fence.
import sys, json; sys.path.insert(0, sys.argv[1]); import irlib
e=json.load(open(irlib.ROOT+'/tools/titan/results/wave55-plan/expectations.json'))
L=e['lanes']['L6-android-paint-order']; car=set(L['captureCarriers']['android']); mm=set(L['mayMove']['android'])
other=set(s for ln,l in e['lanes'].items() for s in l['captureCarriers'].get('android',[]))
ring={'wpt__filter-effects__backdrop-filter-basic-blur'}
SIDES=['Top','Right','Bottom','Left','BlockStart','BlockEnd','InlineStart','InlineEnd']
def band(c):
    p=irlib.props(c); vis=False
    for s in SIDES:
        st=p.get(f'Border{s}Style'); w=p.get(f'Border{s}Width')
        if st is None or str(st).upper() in ('NONE','HIDDEN'): continue
        if w is None: vis=True; break
        if isinstance(w,dict) and (w.get('px') or 0)>0: vis=True; break
        if isinstance(w,(str,)) and w.upper() in ('THIN','MEDIUM','THICK'): vis=True; break
        if isinstance(w,dict) and 'px' not in w: vis=True; break
    if not vis:
        for k in ('Border','BorderWidth','BorderStyle','BorderBlock','BorderInline'):
            if k in p: vis=True
    return vis
def clips(c):
    p=irlib.props(c)
    return any(str(p.get(k,'')).upper() in ('HIDDEN','CLIP','SCROLL','AUTO') for k in ('OverflowX','OverflowY','Overflow'))
rad={}; why={}
for stem,sec,comps,byid,kids in irlib.docs():
    for c in comps:
        if not band(c): continue
        m=c.get('meta') or {}
        inside=[k for k in kids.get(c['id'],[]) if ((k.get('meta') or {}).get('sourceTag') or '').lower()!='br']
        content = bool(inside) or bool(c.get('text')) or bool(m.get('runs')) or any(x in m for x in ('before','after','pseudo','pseudos','markerText'))
        op=irlib.props(c).get('Opacity')
        if (content and not clips(c)) or (op not in (None,1,1.0)):
            rad.setdefault(stem,[]).append(c['id'])
cov=car|mm|other|ring
print('superset radius:', len(rad), 'documents;', 'carriers', len(car), 'mayMove', len(mm))
print('carriers outside the superset:', sorted(car-set(rad)))
un=sorted(set(rad)-cov)
print('UNDER-REPORT candidates (superset minus carriers/mayMove/other-lane carriers/ring):', len(un))
for s in un: print('  ', s, rad[s][:3])
print('mayMove outside the superset (over-coverage):', len(mm-set(rad)))
