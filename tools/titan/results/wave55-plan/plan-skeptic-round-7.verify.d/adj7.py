# adj7.py — per-key adjudication of the round-7 sweep records (sweep7.*.jsonl) against the fixed pictures' own verdicts
# (fixed7.json). ACCEPTED: an OWNING gating key that PASSed its fixed picture still PASSes the fake, or every key of the
# cell's pictureCorrectTally entry still PASSes. Owners: on a cell with several gating keys reading disjoint zones (dir-style
# rows 6 / 7 / 9; counter-suffix ios [H] / [C] / [T]) the keys whose zone the changed px touch (all of them when the change
# touches none: the residual read is shared); otherwise every gating key of the cell. > 2 changed px: MUST-FIX; ≤ 2: the
# named 2-px floor (D39). The fake is rebuilt (fakes7.py is deterministic) to find its changed px.
import json, sys, glob, collections, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from common7 import KEYS, TALLY_BY_CELL, EXP
UNIT = {f'{os.path.basename(next(t for t in pr["cmd"].split() if t.endswith(".py")))} {k}': u for l in EXP['lanes'].values() for pr in l['geometryProbes'] for k, u in pr.get('gating', {}).items()}
OWN = {'selectors/dir-style-02a.html android': {'row6': (0, 116, 9999, 136), 'row7': (0, 136, 9999, 156), 'row9': (0, 176, 9999, 196)},
       'css-counter-styles/counter-suffix.html ios': {'[H]': (0, 64, 64, 112), '[C]': (0, 112, 64, 160), '[T]': (136, 208, 151, 232)}}
fixed = json.load(open('fixed7.json'))
files = sys.argv[1:] or (sorted(glob.glob('sweep7.s*.jsonl')) or ['sweep7.records.tsv'])
def load(f):
    """the sweep's raw jsonl, or the compact sweep7.records.tsv (verdict letters per key, in KEYS order)"""
    if not f.endswith('.tsv'):
        return [json.loads(l) for l in open(f)]
    cells, out = sorted(KEYS), []
    for l in open(f):
        if l.startswith('#'):
            continue
        fk, st, ci, px, res, v = l.rstrip('\n').split('\t'); c = cells[int(ci)]
        ks = {f'{s} {k}': ({'P': 'PASS', 'F': 'FAIL', 'U': 'UNMEASURED', 'N': 'NO ROW'}[v[2 * i]], {'o': 'OK', 'f': 'FAIL', '-': None}[v[2 * i + 1]], '')
              for i, (_, s, k, _) in enumerate(KEYS[c])}
        out.append({'fake': fk, 'set': st, 'cell': c, 'px': int(px), 'res': [int(x) for x in res.split('/')], 'keys': ks})
    return out
recs = [r for f in files for r in load(f)]
_F7 = _CX = None
def changed(r):
    global _F7, _CX
    if _F7 is None:
        import fakes7 as F7; _F7 = F7; _CX = {(f'{s[0]}-{s[1]}', c.cell): c for s in F7.contexts() for c in s[4].values()}
    cx = _CX[(r['set'], r['cell'])]; fn = dict(_F7.FAMILIES)[r['fake']]
    o = np.clip(fn(cx), 0, 255).astype(np.uint8).astype(np.int16)
    return (o != cx.F).any(axis=2), o, cx
acc, floor, n_reads, sibling_rejected = [], [], 0, []
per_key = collections.defaultdict(lambda: [0, 0, 0])
for r in recs:
    cell, st = r['cell'], r['set']
    fx = lambda k: fixed.get(f'{st}|{cell}|{k}', {}).get('verdict')
    gk = [f'{s} {k}' for _, s, k, c in KEYS[cell] if c == 'gating']
    cand = [k for k in gk if fx(k) == 'PASS' and r['keys'][k][0] == 'PASS']
    te = TALLY_BY_CELL.get(cell); tal = None
    if te:
        names = [f'{s} {k}' for s, k in te]
        if all(fx(k) == 'PASS' for k in names) and all(r['keys'].get(k, ('NO ROW',))[0] == 'PASS' for k in names):
            tal = ' + '.join(n.split('.py ')[1] for n in names)
    owners = gk
    if cell in OWN and (cand or tal):
        d, _, _ = changed(r); hit = []
        for tag, (x0, y0, x1, y1) in OWN[cell].items():
            if d[y0:y1, x0:x1].any():
                hit += [k for k in gk if tag in k]
        owners = hit or gk
    for k in gk:
        if k in owners:
            n_reads += 1; per_key[k][0] += 1
    # a sibling owning key of the SAME unit that rejects the fake reverts that unit anyway (round 3's "sibling row" rule): not a hole
    sib = [k for k in cand if k in owners and any(UNIT[j] == UNIT[k] and r['keys'][j][0] == 'FAIL' for j in owners if j != k)]
    sibling_rejected.extend((r['fake'], cell, k) for k in sib)
    hits = [('gating', k) for k in cand if k in owners and k not in sib] + ([('tally', tal)] if tal else [])
    for cls, k in hits:
        (acc if r['px'] > 2 else floor).append((r['fake'], st, cell, cls, k, r['px'], tuple(r['res'])))
        if cls == 'gating':
            per_key[k][1 if r['px'] > 2 else 2] += 1
print(f'{len(recs)} cell fakes from {len(files)} record file(s), {n_reads} owning gating-key reads; MUST-FIX acceptances {len(acc)} '
      f'(gating {sum(a[3] == "gating" for a in acc)}, tally {sum(a[3] == "tally" for a in acc)}); floor (≤ 2 px) {len(floor)}')
for k, (n, m, f) in sorted(per_key.items()):
    print(f'  {k[:95]:95} reads {n:5} must-fix {m:4} floor {f:3}')
print(f'sibling-rejected key reads (a PASS whose same-unit sibling key FAILs the fake: the unit reverts — not counted): {len(sibling_rejected)}')
for x in sorted(set((a, c.split('/')[-1], k.split('.py ')[1]) for a, c, k in sibling_rejected)):
    print('   sibling-rejected', ' | '.join(x))
print('\nMUST-FIX acceptances (fake | set | cell | class | key | changed px | in-process residual outside/vacated/non-class):')
for a in sorted(acc, key=lambda a: (a[2], a[4], a[0])):
    print('  ', ' | '.join(str(x) for x in a))
print('\nfloor acceptances:')
for a in sorted(floor, key=lambda a: (a[2], a[4], a[0])):
    print('  ', ' | '.join(str(x) for x in a))

# ── per lane × family class, and per (key, family class) — the summary tables of plan-skeptic-round-7.verify.md §2 ──────────
lane_of = lambda st: st.split('-')[0]
fc = lambda f: f.split('-')[0]
T = collections.defaultdict(lambda: [0, 0, 0, set(), set()])   # fakes, must-fix, floor, gating keys hit, tally cells hit
for r in recs:
    T[lane_of(r['set'])][0] += 1
for a in acc:
    t = T[lane_of(a[1])]; t[1] += 1
    (t[3] if a[3] == 'gating' else t[4]).add(a[4] if a[3] == 'gating' else a[2])
for a in floor:
    T[lane_of(a[1])][2] += 1
print('\nper lane: cell fakes · MUST-FIX acceptances (gating + tally) · floor · gating keys with a MUST-FIX acceptance · tally cells')
for L in sorted(T):
    t = T[L]; print(f'  {L}: {t[0]:5} fakes · {t[1]:5} must-fix · {t[2]:3} floor · {len(t[3]):2} gating keys · {len(t[4]):2} tally cells')
C = collections.defaultdict(collections.Counter)
for a in acc:
    if a[3] == 'gating':
        C[a[4]][fc(a[0])] += 1
print('\nper gating key: MUST-FIX acceptances by family class (S standing classes · A in-zone specks/lines · F faint copies · G duplicated glyph · W whole ghost · O outside the zones · T targeted)')
for k in sorted(C):
    print(f'  {k[:95]:95} ' + ' '.join(f'{x}:{C[k][x]}' for x in 'SAFGWOT' if C[k][x]))
