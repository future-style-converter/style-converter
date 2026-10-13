import sys,collections,json,unicodedata
sys.path.insert(0,sys.argv[1]); from irlib import *
D=docs(); cells=json.load(open(sys.argv[2]))
u2=collections.Counter(); fvn=collections.Counter(); rtl=collections.defaultdict(list); u3001_elsewhere=collections.Counter()
for stem,(sec,d) in D.items():
    byid,par=parents(d); C=comps(d); PR={c['id']:props(c) for c in C}
    def eff(cid,t):
        for x in [cid]+chain(cid,par):
            v=PR.get(x,{}).get(t)
            if v: return v
    for c in C:
        m=(c.get('meta') or {}); mt=m.get('markerText')
        P=PR[c['id']]
        if mt and mt.endswith('、'):
            pos=eff(c['id'],'ListStylePosition') or 'OUTSIDE'
            u2[(stem,str(pos))]+=1
        if 'FontVariantNumeric' in P: fvn[stem]+=1
        if mt:
            # UAX9 natural direction of the marker string: first strong char
            nat=None
            for ch in mt:
                b=unicodedata.bidirectional(ch)
                if b in ('L',): nat='LTR'; break
                if b in ('R','AL'): nat='RTL'; break
            itemdir=eff(c['id'],'Direction') or 'LTR'
            if nat and nat!=itemdir: rtl[stem].append((mt,itemdir))
        elif 'ListStyleType' in P and '、' in json.dumps(P['ListStyleType'],ensure_ascii=False): u3001_elsewhere[stem]+=1
print('U2: markerText ending U+3001 by (doc, ListStylePosition):')
for k,v in sorted(u2.items()): print('  ',k,v)
print('  total',sum(u2.values()),'docs',len(set(k[0] for k in u2)))
print('U3: FontVariantNumeric declarers:',dict(fvn))
print('U1: markers whose natural direction != item direction:',dict(rtl))
print('li with a U+3001 list-style but no baked markerText (unreached):',sum(u3001_elsewhere.values()),dict(u3001_elsewhere))
