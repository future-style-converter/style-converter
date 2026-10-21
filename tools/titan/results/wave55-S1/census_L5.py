# S1 own census, L5. U1-ios: a marker row (baked meta.markerText, else a list item with a non-none ListStyleType / the li
# default) whose UAX#9 natural direction (first strong LETTER, isolates skipped, as ListMarkerBidi.naturalIsRTL) differs
# from the item's merged Direction (own, else inherited) -> the painted string changes. U2: a baked marker ending U+3001
# (the local tables never emit U+3001: grep). U3: own FontVariantNumeric.
import sys, json, unicodedata; sys.path.insert(0, sys.argv[1]); import irlib
e=json.load(open(irlib.ROOT+'/tools/titan/results/wave55-plan/expectations.json'))
L=e['lanes']['L5-counter-suffix-rows']; car={p:set(v) for p,v in L['captureCarriers'].items()}
def rtlblock(v): return 0x590<=v<=0x8FF or 0xFB1D<=v<=0xFDFF or 0xFE70<=v<=0xFEFF or 0x10800<=v<=0x10FFF or 0x1E800<=v<=0x1EFFF
def natural_rtl(s):
    depth=0
    for ch in s:
        v=ord(ch)
        if v in (0x2066,0x2067,0x2068): depth+=1; continue
        if v==0x2069:
            depth=max(0,depth-1); continue
        if depth: continue
        if v==0x200E: return False
        if v in (0x200F,0x61C): return True
        if unicodedata.category(ch) in ('Lu','Ll','Lt','Lm','Lo'): return rtlblock(v)
    return False
def inh(c,byid,key,default=None):
    while c is not None:
        p=irlib.props(c)
        if key in p: return p[key]
        c=irlib.parent(c,byid)
    return default
u1={}; u2={}; u3={}; rtl_items={}
for stem,sec,comps,byid,kids in irlib.docs():
    for c in comps:
        m=c.get('meta') or {}; tag=(m.get('sourceTag') or '').lower(); p=irlib.props(c)
        baked=m.get('markerText')
        lst=inh(c,byid,'ListStyleType','disc' if tag=='li' else None)
        is_item = tag=='li' or p.get('Display')=='LIST_ITEM'
        has_marker = baked is not None and baked!='' or (is_item and lst not in (None,'none','NONE'))
        if not has_marker: continue
        d=inh(c,byid,'Direction','LTR'); item_rtl = str(d).upper()=='RTL'
        if item_rtl: rtl_items.setdefault(stem,0); rtl_items[stem]+=1
        if baked is not None and baked!='':
            nat=natural_rtl(baked); key='baked:'+baked
        else:
            nat = str(lst).lower() in ('hebrew','arabic-abjad','persian-abjad'); key='table:'+str(lst)
        if nat!=item_rtl: u1.setdefault(stem,[]).append((c['id'],key,'item-rtl' if item_rtl else 'item-ltr'))
        if baked and baked.endswith('、'): u2.setdefault(stem,[]).append(c['id'])
    for c in comps:
        if 'FontVariantNumeric' in irlib.props(c): u3.setdefault(stem,[]).append(c['id'])
print('U1-ios painted-string changes:', len(u1), 'docs')
for s,v in sorted(u1.items()): print('  ', 'CARRIER' if s in car['ios'] else 'NOT-CARRIER', s, len(v), sorted(set(k for _,k,_ in v))[:6], sorted(set(r for *_,r in v)))
print('docs with RTL list items:', rtl_items)
print('U2 baked U+3001:', {s:len(v) for s,v in u2.items()})
print('U3 FontVariantNumeric:', {s:len(v) for s,v in u3.items()})
rad_ios=set(u1)|set(u2)|set(u3); rad_and=set(u2)
print('ios radius-minus-carriers', sorted(rad_ios-car['ios']), 'carriers-minus-radius', sorted(car['ios']-rad_ios))
print('android radius-minus-carriers', sorted(rad_and-car['android']), 'carriers-minus-radius', sorted(car['android']-rad_and))
