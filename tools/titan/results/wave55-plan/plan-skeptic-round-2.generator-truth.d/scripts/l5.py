# gt2 L5 census (as run via stdin): where U+3001 sits on the wire, FontVariantNumeric components, RTL-script marker strings.
import sys,json,collections,re
sys.path.insert(0,'.'); from irlib import *
where=collections.Counter(); docsU=collections.defaultdict(list); fvn=[]; rtlmark=[]
def walkjson(x,path):
    if isinstance(x,dict):
        for k,v in x.items(): yield from walkjson(v,path+[k])
    elif isinstance(x,list):
        for i,v in enumerate(x): yield from walkjson(v,path+['[]'])
    elif isinstance(x,str): yield path,x
for sec,st,d in docs():
    D=Doc(d)
    for c in D.comps:
        for path,s in walkjson(c,[]):
            if '、' in s:
                key='/'.join(p for p in path if p!='[]'); where[key]+=1; docsU[st].append((c['id'][-8:],key,s))
        if has(c,'FontVariantNumeric'): fvn.append((st,c['id'],last(c,'FontVariantNumeric')))
        m=(c.get('meta') or {})
        for path,s in walkjson(m,['meta']):
            if re.search('[֐-׿؀-ۿ]',s) and 'marker' in '/'.join(path).lower(): rtlmark.append((st,c['id'][-8:],'/'.join(path),s))
print('U+3001 occurrences by wire path:',dict(where))
for st,v in docsU.items(): print(' ',st,len(v),collections.Counter(k for _,k,_ in v))
print('FontVariantNumeric components:',len(fvn),collections.Counter(x[0] for x in fvn))
print('RTL-script marker strings in meta:',len(rtlmark))
for x in rtlmark: print('  ',x)
