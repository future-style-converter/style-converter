import sys, json, os
sys.path.insert(0, __import__("os").path.dirname(__file__)); from irlib import *
SIDE={'BorderTopWidth':('top','w'),'BorderBlockStartWidth':('top','w'),'BorderTopStyle':('top','s'),'BorderBlockStartStyle':('top','s'),
 'BorderRightWidth':('end','w'),'BorderInlineEndWidth':('end','w'),'BorderRightStyle':('end','s'),'BorderInlineEndStyle':('end','s'),
 'BorderBottomWidth':('bottom','w'),'BorderBlockEndWidth':('bottom','w'),'BorderBottomStyle':('bottom','s'),'BorderBlockEndStyle':('bottom','s'),
 'BorderLeftWidth':('start','w'),'BorderInlineStartWidth':('start','w'),'BorderLeftStyle':('start','s'),'BorderInlineStartStyle':('start','s')}
KW={'thin':1,'medium':3,'thick':5}
def width(v):
    if v is None: return None
    if isinstance(v,(int,float)): return float(v)
    if isinstance(v,str): return KW.get(v.lower())
    if isinstance(v,dict):
        for k in ('px','pixels'):
            if isinstance(v.get(k),(int,float)): return float(v[k])
        if v.get('type')=='keyword': return KW.get(str(v.get('value','')).lower())
        o=v.get('original')
        if isinstance(o,dict) and isinstance(o.get('v'),(int,float)) and o.get('u') in ('EM','REM','PX','PT'): return float(o['v'])  # resolved upstream (needsResolution)
    return None
STY={'SOLID','DASHED','DOTTED','DOUBLE','GROOVE','RIDGE','INSET','OUTSET','NONE','HIDDEN'}
def style(v):
    if isinstance(v,str) and v.upper() in STY: return v.upper()
    if isinstance(v,dict):
        for k in ('value','keyword','original'):
            if isinstance(v.get(k),str) and v[k].upper() in STY: return v[k].upper()
    if isinstance(v,list) and v: return style(v[0])
    return None
def sides(c, medium=True, explicit_only=False):
    sd={k:{'w':None,'s':None} for k in ('top','end','bottom','start')}; sw=ss=None
    for p in props(c):
        t,v=p['type'],p['data']
        if t=='BorderWidth': sw=width(v)
        elif t=='BorderStyle': ss=style(v)
        elif t in SIDE:
            side,kind=SIDE[t]; sd[side][kind]= width(v) if kind=='w' else style(v)
    for k in sd:
        if sd[k]['w'] is None: sd[k]['w']=sw
        if sd[k]['s'] is None: sd[k]['s']=ss
        if medium and sd[k]['w'] is None and sd[k]['s'] not in (None,'NONE','HIDDEN'): sd[k]['w']=3.0
    return sd
def bordered(c,medium=True):
    return any((x['w'] or 0)>0 and x['s'] not in (None,'NONE','HIDDEN') for x in sides(c,medium).values())
def explicit_px(c):
    # the brief's rule as documented: a Border*Width with px > 0 (no style test, no shorthand, no keyword)
    for p in props(c):
        if p['type'].startswith('Border') and p['type'].endswith('Width') and isinstance(p['data'],dict) and isinstance(p['data'].get('px'),(int,float)) and p['data']['px']>0: return True
    return False
def qualifies(D,c):
    if c.get('text'): return True
    return any(((k.get('meta') or {}).get('sourceTag') or '').lower()!='br' for k in D.kids.get(c['id'],[]))
