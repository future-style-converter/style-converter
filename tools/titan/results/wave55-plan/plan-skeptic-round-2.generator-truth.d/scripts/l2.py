import sys, json, re
sys.path.insert(0, sys.argv[1]); from irlib import *
def px(c,*ts):
    for t in ts:
        v=last(c,t)
        if isinstance(v,dict) and isinstance(v.get('px'),(int,float)): return float(v['px'])
    return None
def pct(c,*ts):
    for t in ts:
        v=last(c,t)
        if isinstance(v,dict):
            if v.get('type')=='percentage': return float(v.get('value', v.get('percentage')))
            o=v.get('original')
            if isinstance(o,dict) and o.get('u')=='PERCENT': return float(o['v'])
    return None
def size(c,base,*ts):
    p=px(c,*ts)
    if p is not None: return p
    q=pct(c,*ts)
    if q is not None and base is not None: return q*base/100
    return None
def childcb(c,cb,mode):
    w=size(c,cb[0],'Width','InlineSize'); h=size(c,cb[1],'Height','BlockSize')
    bs=last(c,'BoxSizing')
    sub = True if mode=='frozen' else (bs=='BORDER_BOX')
    if sub:
        if w is not None: w-= (px(c,'PaddingLeft','PaddingInlineStart') or 0)+(px(c,'PaddingRight','PaddingInlineEnd') or 0)+(px(c,'BorderLeftWidth') or 0)+(px(c,'BorderRightWidth') or 0)
        if h is not None: h-= (px(c,'PaddingTop','PaddingBlockStart') or 0)+(px(c,'PaddingBottom','PaddingBlockEnd') or 0)+(px(c,'BorderTopWidth') or 0)+(px(c,'BorderBottomWidth') or 0)
    return (w,h)
cells=json.load(open(ROOT+'/tools/titan/results/wave55-plan/cells-wave54-final.json'))['cells']
hits={}
def dynpct_nested(c):
    for p in props(c):
        if p['type'] in ('BackgroundImage','Generic','Color'): continue
        s=json.dumps(p['data'])
        if '"PERCENT"' in s or ('"expr"' in s and '%' in s): return p['type']
    return None
def dynpct(c):
    # DynamicValueResolver.needsResolution's TOP-LEVEL shapes only (a nested PERCENT, e.g. Transform translate, is not resolved against the block)
    for p in props(c):
        v=p['data']
        if not isinstance(v,dict) or 'srgb' in v: continue
        if isinstance(v.get('expr'),str) and '%' in v['expr']: return p['type']
        o=v.get('original')
        if isinstance(o,dict) and o.get('u')=='PERCENT' and not isinstance(v.get('px'),(int,float)): return p['type']
    return None
for sec,st,d in docs():
    D=Doc(d)
    cbin={}
    def walk(c,cbF,cbW):
        cbin[c['id']]=(cbF,cbW)
        for k in D.kids.get(c['id'],[]): walk(k,childcb(c,cbF,'frozen'),childcb(c,cbW,'wpt'))
    for r in D.kids.get(None,[]): walk(r,(390.0,600.0),(390.0,600.0))
    for c in D.comps:
        F,W=cbin[c['id']]
        mx = F[0]!=W[0]; my = F[1]!=W[1]
        if not (mx or my): continue
        why=[]
        pos=last(c,'Position')
        if pos in ('ABSOLUTE','FIXED'):
            if mx and (pct(c,'Width','InlineSize') is not None or (has(c,'Left') and has(c,'Right'))): why.append('oof-x')
            if my and (pct(c,'Height','BlockSize') is not None or (has(c,'Top') and has(c,'Bottom'))): why.append('oof-y')
        wm,_=D.eff(c,'WritingMode')
        if c.get('text') and wm not in (None,'HORIZONTAL_TB'): why.append('vtext')
        dp=dynpct(c)
        if dp: why.append('dyn:'+dp)
        for t in ('MarginTop','MarginLeft','MarginRight','MarginBottom','PaddingTop','PaddingLeft','PaddingRight','PaddingBottom','Top','Left','Right','Bottom'):
            if pct(c,t) is not None: why.append('spc:'+t)
        if why: hits.setdefault(st,[]).append((c['id'],why,F,W))
n=sum(len(v) for v in hits.values())
print('L2 U1 my census: boxes',n,'docs',len(hits))
for st in sorted(hits):
    print(st)
    for cid,why,F,W in hits[st]: print('   ',cid[-6:],why,F,'->',W)
