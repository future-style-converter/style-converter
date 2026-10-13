import sys,struct,math,collections,json
sys.path.insert(0,sys.argv[1]); from irlib import *
ROOT='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/'
def hhea(path):
    b=open(path,'rb').read()
    n=struct.unpack('>H',b[4:6])[0]; T={}
    for i in range(n):
        tag,cs,off,ln=struct.unpack('>4sIII',b[12+16*i:28+16*i]); T[tag.decode()]=off
    upem=struct.unpack('>H',b[T['head']+18:T['head']+20])[0]
    asc,desc,gap=struct.unpack('>hhh',b[T['hhea']+4:T['hhea']+10])
    return upem,asc,desc,gap
FACES={'dejavu':ROOT+'tools/titan/fonts/mono-pin/DejaVuSansMono.ttf','inter':ROOT+'apps/ios-harness/StyleConverterTest/Resources/Inter-Regular.otf','libertine':ROOT+'tools/wpt/css/css-text/boundary-shaping/resources/LinLibertine_Re-4.7.5.woff.otf'}
M={k:hhea(v) for k,v in FACES.items()}
def lround(x): return math.floor(x+0.5)
def box(face,px):
    upem,a,d,g=M[face]; a,d,g=a*px/upem,-d*px/upem,g*px/upem
    return a+d+g, lround(a)+lround(d)+lround(g)
D=docs()
cells=json.load(open(sys.argv[2]))
rad=collections.defaultdict(list); own=inh=0; nbr=0
for stem,(sec,d) in D.items():
    byid,par=parents(d)
    for c in comps(d):
        P=props(c)
        def eff(t):
            if t in P: return P[t],'own'
            for p in chain(c['id'],par):
                pp=props(byid[p]) if p in byid else {}
                if t in pp: return pp[t],'inh'
            return None,None
        lh,src=eff('LineHeight')
        if not (isinstance(lh,dict) and lh.get('original')=='normal'): continue
        if (c.get('meta') or {}).get('role')=='line-break': nbr+=1; continue
        if not c.get('text'): continue
        if src=='own': own+=1
        else: inh+=1
        fs,_=eff('FontSize'); fam,_=eff('FontFamily')
        px=(fs or {}).get('px',16) if isinstance(fs,dict) else 16
        fam=[f.lower() for f in (fam or [])]
        face='dejavu' if fam and fam[0]=='monospace' else ('libertine' if fam and fam[0]=='test' else 'inter')
        nl=c['text'].count('\n')
        hasbr=any((byid.get(x,{}).get('meta') or {}).get('role')=='line-break' for x in [cc['id'] for cc in comps(d) if (cc.get('slot') or {}).get('parent')==c['id']])
        rad[stem].append((face,px,fam[:1],hasbr,P.get('Hyphens'),P.get('Width')))
print('components',sum(len(v) for v in rad.values()),'docs',len(rad),'own',own,'inherited',inh,'line-break boxes with normal LH',nbr)
car=[];same=[]
for stem,v in sorted(rad.items()):
    t=stem_to_test(stem)
    fp=sorted(set((f,p) for f,p,*_ in v))
    multi=any(h for *_,h,hy,w in v)
    moves=[]
    for f,p in fp:
        raw,bl=box(f,p); moves.append((f,p,round(raw,4),math.ceil(raw),bl))
    one_line_moves=any(ce!=bl for *_,ce,bl in moves)
    cls='TARGET' if multi else ('MOVES' if one_line_moves else 'SAME')
    (car if cls!='SAME' else same).append(stem)
    print(f'  {cls:6} {t:75} ios {cells.get(t+" ios","—"):9} {moves} fam={[x[2] for x in v][:1]} br={multi}')
print('carriers ios',len(car)); print('same',len(same))
json.dump({'ios':car,'same':same},open(sys.argv[3],'w'))
