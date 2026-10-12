#!/usr/bin/env python3
# Round-5 verifier: per-key adjudication of fakes5.py's records (fakes5.L*.json). Every record is one faked zone of one capture
# read by the keys that own the zone (fakes5.py zones_of: the region the key reads with a non-pixel-identity read). An
# ACCEPTANCE is an owning gating key that still PASSes (or a pictureCorrectTally entry whose every key PASSes). It is
# FLOOR when the fake changed ≤ 2 px (the named 2-px floor, PLAN §0 / D39); otherwise MUST-FIX.
# Each accepted fake is also classed by WHERE its ink went relative to the key's own reads (see `family`):
#   faint   — a 50 % alpha copy / speck / line (ink the probe's colour-exact or dark mask does not see)
#   other   — full-ink paint of a colour class the key's probe does not read (e.g. black / blue on an L2 green-mask key)
#   own     — full-ink paint of the colour class the key reads (its own mask sees it)
# Usage: python3.12 -B adjudicate5.py <fakes5.L1.json> … > adjudicate5.out.txt
import collections, json, sys

OWN = {  # the colour class(es) each probe family's masks read, by key prefix (from the probes' own predicates)
    'hyphen-ios-linebox': 'ink (min < 200 / core max < 110)', 'hyphenate-character': 'core max < 110',
    'abspos/': 'strict green (0,128,0)±8 + fail ink (red / pink)', 'backdrop-filter-nested': 'strict green (0,128,0)±8 + fail ink (red / pink)',
    '[S] dir-style': 'ink (min < 200)', '[S] bidi': 'blue (b > 150, r, g < 120) + yellow box bbox', '[W] anchor': 'green (r < 60, g > 100, b < 60) + red',
    '[S] dynamic': 'magenta + yellow parent', '[W] dynamic': 'magenta + yellow parent',
    '[M]': 'dark (sum < 300) bands + level (min < 200, sum < 300) XOR in band rows ±2', '[F]': 'dark (sum < 300) bands + level XOR in band rows ±2',
    '[H]': 'level (min < 200, sum < 300)', '[C]': 'level', '[T]': 'level', '[I]': 'level',
    '[X]': 'child fill (yellow / teal) + dark (sum < 150) crossings / band floor', '[PO]': 'red / blue / pink counts (blue a lower bound only)'}
OWN_RGB = {'abspos/': [(0, 128, 0)], 'backdrop-filter-nested': [(0, 128, 0)], '[S] bidi': [(0, 0, 255)], '[W] anchor': [(0, 128, 0)],
           '[S] dynamic': [(255, 0, 255), (255, 255, 0)], '[W] dynamic': [(255, 0, 255), (255, 255, 0)],
           '[X] flex': [(255, 255, 0)], '[X] grid': [(0, 128, 128)], '[PO]': [(255, 0, 0), (0, 0, 255), (255, 127, 255)]}


def bare(key):
    """The key without its script (records written with script-qualified keys carry 'script.py key')."""
    return key.split(' ', 1)[1] if key.split(' ', 1)[0].endswith('.py') else key


def family(r, key):
    key = bare(key)
    if r['variant'].startswith('f50'):
        return 'faint'
    for p, cols in OWN_RGB.items():
        if key.startswith(p):
            return 'own' if any(sum(abs(a - b) for a, b in zip(r['class'], c)) <= 60 for c in cols) else 'other'
    return 'own'


recs = [r for f in sys.argv[1:] for r in json.load(open(f))]
for r in recs:                                   # prefer the script-qualified key lists (two L2 probes share a key name)
    if 'ownQ' in r:
        r['own'], r['gatingPass'] = r['ownQ'], r['gatingPassQ']
by_lane = collections.defaultdict(list)
for r in recs:
    by_lane[r['lane']].append(r)
tot_must, tot_floor = 0, 0
for lane in sorted(by_lane):
    rs = by_lane[lane]
    acc = [r for r in rs if r['gatingPass'] or r['tallyPass']]
    must = [r for r in acc if r['added'] > 2]; flo = [r for r in acc if r['added'] <= 2]
    tot_must += len(must); tot_floor += len(flo)
    keys_read = collections.Counter(k for r in rs for k in r['own'])
    print(f'\n## {lane}: {len({r["run"] for r in rs})} fake runs · {len(rs)} faked zones · {sum(keys_read.values())} key reads of a faked zone · '
          f'ACCEPTED {len(acc)} faked zones (MUST-FIX > 2 px: {len(must)}; floor ≤ 2 px: {len(flo)})')
    per = collections.defaultdict(list)
    for r in must:
        for k in r['gatingPass']:
            per[k].append(r)
        for cell in r['tallyPass']:
            if not r['gatingPass']:
                per[f'(tally only) {cell}'].append(r)
    for k in sorted(keys_read):
        rk = per.get(k, [])
        fams = collections.Counter(family(r, k) for r in rk)
        n_read = keys_read[k]
        line = f'  {k:58} reads {n_read:3} · accepted > 2 px {len(rk):3}'
        if rk:
            mx = max(rk, key=lambda r: r['added'])
            line += (f' · faint {fams["faint"]} / other-colour {fams["other"]} / own-colour {fams["own"]} · largest +{mx["added"]} px '
                     f'({mx["variant"]} class {tuple(mx["class"])} bbox {tuple(mx["bbox"])})')
        print(line)
        own = [r for r in rk if family(r, k) == 'own']
        for r in own[:12]:
            print(f'      own-colour: {r["variant"]:16} class {tuple(r["class"])} +{r["added"]} px bbox {tuple(r["bbox"])} — {r["capture"]}')
        if len(own) > 12:
            print(f'      … {len(own) - 12} more own-colour acceptances')
    for k in sorted(x for x in per if x.startswith('(tally only)')):
        print(f'  {k}: {len(per[k])}')
    vs = collections.Counter(r['variant'] for r in must)
    print('  variants accepted (> 2 px): ' + ', '.join(f'{v} ×{n}' for v, n in sorted(vs.items())))
    print(f'  where the accepted (> 2 px) paint went: onto the zone background only {sum(1 for r in must if r["overpaint"] == 0)}, '
          f'over other colours only {sum(1 for r in must if r["onBackground"] == 0)}, both {sum(1 for r in must if r["overpaint"] and r["onBackground"])}')
    print('  floor (≤ 2 px) acceptances: ' + ', '.join(f'{v} ×{n}' for v, n in sorted(collections.Counter(r['variant'] for r in flo).items())))
print(f'\nALL LANES: MUST-FIX acceptances (> 2 px) {tot_must}; floor (≤ 2 px) {tot_floor}')
