# gt2 structural checks of expectations.json / watchlist.txt against MY cells.mjs read-out (cells-all.txt) and the run dir.
import json, re, os, sys, collections
ROOT='/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
S=sys.argv[1]; EXPF=sys.argv[2] if len(sys.argv)>2 else ROOT+'/tools/titan/results/wave55-plan/expectations.json'
WLF=sys.argv[3] if len(sys.argv)>3 else ROOT+'/tools/titan/results/wave55-plan/watchlist.txt'
E=json.load(open(EXPF))
cells={}
for line in open(S+'/cells-all.txt'):
    m=re.match(r'^(\S+)\|(\w+)\s+(.*)$',line.rstrip()); cells[m.group(1)+' '+m.group(2)]=m.group(3).strip()
RUN=ROOT+'/tools/titan/runs/wave54-final/sections'
CAPD={'web':'screenshots','ios':'ios-screenshots','android':'android-screenshots'}
stem=lambda t: 'wpt__'+t[:-5].replace('/','__')
fails=[]; NONCELL=[]
def F(msg): fails.append(msg)
# (1) predictions' from
npred=0; nfrom=0
for lid,L in E['lanes'].items():
    for p in L['predictions']:
        npred+=1
        c=p['cell']; fr=p.get('from','')
        m=re.match(r'^([Pf]) ([0-9.]+)',fr)
        if c in cells:
            nfrom+=1
            if not m or f'{m.group(1)} {m.group(2)}'!=cells[c]: F(f'FROM {lid} {c}: {fr!r} vs cells.mjs {cells[c]}')
        else:
            if m: F(f'FROM {lid} {c}: a P/f from {fr!r} on an UNSCORED cell')
# (2) carriers: capture stems exist, union = lanes
union={p:set() for p in CAPD}
for lid,L in E['lanes'].items():
    for p,v in L['captureCarriers'].items():
        for s in v:
            sec=s.split('__')[1]
            if not os.path.exists(f'{RUN}/{sec}/{CAPD[p]}/{s}.png'): F(f'CARRIER {lid} {p} {s}: no capture')
            union[p].add(s)
    u=set()
    for un,U in L['revertUnits'].items():
        for p,v in U['captures'].items(): u|={(p,s) for s in v}
    cc={(p,s) for p,v in L['captureCarriers'].items() for s in v}
    if u!=cc: F(f'UNITS {lid}: union of unit captures != captureCarriers ({len(u)} vs {len(cc)})')
for p in CAPD:
    if set(E['unionCaptureCarriers'][p])!=union[p]: F(f'UNION {p} differs')
carrier_cells={c for c in cells if stem(c.rsplit(' ',1)[0]) in union[c.rsplit(' ',1)[1]]}
# (3) partition
ring=set(E['ringFenced']['cells'])
named=collections.defaultdict(set)
for lid,L in E['lanes'].items():
    for c in L['mustNotMove']: named[c].add(lid)
comp=set(E['mustNotMoveComplement']['cells'])
named_set=set(named)
for c in named_set:
    if c not in cells: F(f'NAMED unscored {c} ({named[c]})')
    if c in carrier_cells: F(f'NAMED is a carrier: {c} ({named[c]})')
    if c in ring: F(f'NAMED is ring: {c}')
for c in comp:
    if c not in cells: F(f'COMP unscored {c}')
classes=[('carrier',carrier_cells),('named',named_set),('complement',comp),('ring',ring)]
for i in range(4):
    for j in range(i+1,4):
        x=classes[i][1]&classes[j][1]
        if x: F(f'OVERLAP {classes[i][0]}∩{classes[j][0]} {len(x)} e.g. {sorted(x)[:3]}')
allc=set(cells); cover=carrier_cells|named_set|comp|ring
if cover!=allc: F(f'HOLE {len(allc-cover)} {sorted(allc-cover)[:5]} EXTRA {len(cover-allc)}')
if E['mustNotMoveComplement']['count']!=len(comp): F('COMP count field')
# predictions are carrier cells (or unscored captures of the lane)
for lid,L in E['lanes'].items():
    lc={(p,s) for p,v in L['captureCarriers'].items() for s in v}
    for p in L['predictions']:
        if not re.match(r'^\S+\.html (web|ios|android)$', p['cell']):
            NONCELL.append(f'{lid}: {p["cell"][:70]}'); continue
        t,pl=p['cell'].rsplit(' ',1)
        if (pl,stem(t)) not in lc: F(f'PRED {lid} {p["cell"]} is not a capture carrier of its lane')
        for un in p.get('units',[]):
            U=L['revertUnits'].get(un)
            if U is None: F(f'PRED {lid} {p["cell"]} names unit {un} that does not exist'); continue
            if stem(t) not in U['captures'].get(pl,[]): F(f'PRED {lid} {p["cell"]} unit {un} does not carry it')
        for un in p.get('requires',[]) or []:
            if un not in L['revertUnits']: F(f'PRED {lid} {p["cell"]} requires unknown unit {un}')
# (4) floors
g=0
for lid,L in E['lanes'].items():
    for p in L['predictions']:
        to=str(p.get('to','')); fr=str(p.get('from',''))
        if p.get('gating'):
            g+=1
            if p.get('floor')!=0.95: F(f'FLOOR {lid} {p["cell"]} gating floor {p.get("floor")}')
        else:
            if p.get('floor') is not None: F(f'FLOOR {lid} {p["cell"]} non-gating floor {p.get("floor")}')
        ff = fr.startswith('f') and re.match(r'^f\b',to.strip() or 'x') is not None
        if ff and p.get('floor') is not None: F(f'FLOOR f->f with floor {p["cell"]}')
# (5) watchlist: one scored cell per line (watchCells substring rule against css/<sec>/... test path)
wl=[l.strip() for l in open(WLF) if l.strip() and not l.startswith('#')]
keys=[(c.rsplit(' ',1)[0], c.rsplit(' ',1)[1]) for c in cells]
wmatch=collections.Counter(); dup=[l for l,n in collections.Counter(wl).items() if n>1]
watched=set()
for l in wl:
    pat,pl=l.split()[:2]
    hits=[f'{t} {p}' for t,p in keys if p==pl and pat in f'{t.split("/")[0]}/css/{t}']
    if len(hits)!=1: F(f'WATCH {l!r} matches {len(hits)}')
    watched|=set(hits)
if dup: F(f'WATCH duplicate lines {dup[:3]}')
notw=(carrier_cells|named_set|ring)-watched
if notw: F(f'WATCH missing carrier/named/ring cells {len(notw)} {sorted(notw)[:4]}')
# (6) may-move
mm=set(E['unionMayMoveCaptures']['android'])
allcar={s for p in union for s in union[p]}
x=mm&union['android']
if x: F(f'MAYMOVE contains android carriers {sorted(x)[:4]}')
if set(E['unionMayMoveCaptures'].get('web',[])) or set(E['unionMayMoveCaptures'].get('ios',[])): F('MAYMOVE web/ios non-empty')
if 'wpt__filter-effects__backdrop-filter-basic-blur' not in mm: F('MAYMOVE lacks basic-blur (D2)')
mmcells={f'{"/".join(s.split("__")[1:])}.html android' for s in mm}
mm_scored=mmcells&allc
mm_in_comp=mm_scored&comp; mm_in_named=mm_scored&named_set; mm_in_ring=mm_scored&ring
# (7) ring: in no list/key
blob=json.dumps({k:v for k,v in E.items() if k not in ('ringFenced',)})
rt=E['ringFenced']['test']
where=[]
for lid,L in E['lanes'].items():
    for k,v in L.items():
        if rt in json.dumps(v): where.append(f'{lid}.{k}')
for k,v in E.items():
    if k not in ('lanes','ringFenced') and rt in json.dumps(v): where.append(k)
print(f'predictions {npred} (scored-cell rows {nfrom}); gating rows {g}')
print(f'scored {len(allc)} = carrier {len(carrier_cells)} + named {len(named_set)} + complement {len(comp)} + ring {len(ring)} = {len(carrier_cells)+len(named_set)+len(comp)+len(ring)}')
print(f'watch lines {len(wl)}; watched cells {len(watched)}; extra watched (complement) {sorted(watched-(carrier_cells|named_set|ring))}')
print(f'may-move android stems {len(mm)}; scored {len(mm_scored)} (complement {len(mm_in_comp)}, named {len(mm_in_named)}, ring {len(mm_in_ring)}); unscored {len(mm)-len(mm_scored)}')
print('named lanes of may-move cells:',collections.Counter(l for c in mm_in_named for l in named[c]))
print('ring test referenced in:',where)
# (8) gating keys: unit exists, cell is a capture of a required unit
for lid,L in E['lanes'].items():
    for g in L['geometryProbes']:
        for key,spec in g.get('gating',{}).items():
            need=g.get('requires',{}).get(key) or spec.split('|')
            for u in need:
                if u not in L['revertUnits']: F(f'KEY {lid} {key} needs unknown unit {u}')
            cell=g['keyCells'].get(key)
            if not cell or cell not in cells: F(f'KEY {lid} {key} cell {cell} not scored'); continue
            t,pl=cell.rsplit(' ',1)
            if not any(stem(t) in L['revertUnits'][u]['captures'].get(pl,[]) for u in need if u in L['revertUnits']):
                F(f'KEY {lid} {key} cell {cell} is not a capture of {need}')
            preds=[p for p in L['predictions'] if p['cell']==cell]
            if not preds: F(f'KEY {lid} {key}: no prediction row for {cell}')
            elif not any(p.get('gating') or p.get('floorNA') for p in preds): F(f'KEY {lid} {key}: its row {cell} is neither gating nor floorNA')
print('non-cell prediction rows',len(NONCELL)); [print('   ',x) for x in NONCELL]
print('FAILS',len(fails))
for f in fails: print('  ',f)
sys.exit(1 if fails else 0)
