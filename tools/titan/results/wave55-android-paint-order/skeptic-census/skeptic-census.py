#!/usr/bin/env python3
# skeptic-census.py <full.tsv> — the L6 skeptic's own classification of SkepticL6CensusTest's rows (wave54-final IR).
# Every banded component on a route whose band now paints behind the content (U1 self-paint, U2 legacy) is a mover
# when ANYTHING painted inner of the band node can reach the band, or the band changes group: children (any slot child),
# own text, runs, pseudos, marker, border-image (BorderImageBox draws the 9-slice expanded back over the border box,
# INNER of the band), replaced / form tags, partial opacity, an outline with a negative offset. Nothing is subtracted
# for display:contents (reported separately), so the radius can only be over-, never under-stated by that rule.
import csv, json, os, sys
from collections import Counter, defaultdict
HERE = os.path.dirname(os.path.abspath(__file__))
E = json.load(open(os.path.join(HERE, '..', '..', 'wave55-plan', 'expectations.json')))
L = E['lanes']['L6-android-paint-order']
rows = list(csv.DictReader(open(sys.argv[1]), delimiter='\t'))
REPLACED = {'img', 'input', 'button', 'select', 'textarea', 'video', 'canvas', 'svg', 'iframe', 'object', 'embed',
            'progress', 'meter', 'hr', 'fieldset', 'legend', 'details', 'summary', 'audio', 'picture'}
errs = [r for r in rows if r['band'] == 'ERR']
print(f'components {len(rows)} · documents {len({r["stem"] for r in rows})} · extraction errors {len(errs)} '
      f'{sorted({r["stem"] for r in errs})}')
band = [r for r in rows if r['band'] == 'true']
u1 = [r for r in band if r['self'] == 'true']
u2 = [r for r in band if r['self'] != 'true']
print(f'banded {len(band)}: U1 self-paint {len(u1)} (fast {sum(r["fast"] == "true" for r in u1)}) · '
      f'U2 legacy {len(u2)} (fast {sum(r["fast"] == "true" for r in u2)}, slow {sum(r["fast"] != "true" for r in u2)})')
# Sanity on the patched chain: every U1 row holds the behind-content element; every U2 fast row too.
bad = [r['id'] for r in band if r['fast'] == 'true' and 'BehindContentBorderElement' not in r['sig']]
print(f'fast-path rows whose full-tree chain lacks BehindContentBorderElement (must be 0): {len(bad)} {bad[:5]}')
leg = [r['id'] for r in rows if r['band'] != 'true' and 'BehindContentBorderElement' in r['sig']]
print(f'unbanded rows carrying BehindContentBorderElement (must be 0): {len(leg)} {leg[:5]}')

def exposure(r):
    out = []
    if int(r['kidsAll']) > 0: out.append('child' if int(r['kidsNonBr']) > 0 else 'br-only-child')
    if r['text'] == 'true': out.append('text')
    if r['runs'] == 'true': out.append('runs')
    if r['pseudos'] == 'true': out.append('pseudo')
    if r['marker'] == 'true': out.append('marker')
    if r['borderImage'] == 'true': out.append('border-image')
    if r['tag'].lower() in REPLACED: out.append('tag:' + r['tag'].lower())
    try:
        if float(r['opacity']) < 1: out.append('opacity<1')
    except ValueError: out.append('opacity?')
    if r['outline'] == 'true' and float(r['outlineOffset']) < 0: out.append('outline<0')
    extra = set(r['metaKeys'].split(',')) - {'', 'role', 'sourceTag', 'runs', 'markerText'}
    if extra: out.append('meta:' + '|'.join(sorted(extra)))
    return out

car = set(L['captureCarriers']['android']); may = set(L['mayMove']['android']); shared = set(L['sharedStems'])
ring = set(E['ringFenced']['captures']['android']); union = set(E['unionCaptureCarriers']['android'])
covered = car | may | shared | ring
for label, rs in (('U1', u1), ('U2', u2)):
    cls = Counter(); docs = defaultdict(set)
    for r in rs:
        e = exposure(r); k = '+'.join(e) if e else 'inert'
        cls[k] += 1; docs[k].add(r['stem'])
    print(f'\n== {label}: {len(rs)} banded components in {len({r["stem"] for r in rs})} documents')
    for k, n in cls.most_common(): print(f'  {k}: {n} components in {len(docs[k])} documents')
exp_docs = defaultdict(list)
for r in band:
    e = exposure(r)
    if e and e != ['br-only-child']: exp_docs[r['stem']].append((r['id'], '+'.join(e), 'U1' if r['self'] == 'true' else 'U2'))
print(f'\n== documents with an exposed banded component: {len(exp_docs)}; inside carriers ∪ mayMove ∪ sharedStems ∪ ring: '
      f'{len(set(exp_docs) & covered)}')
grow = sorted(set(exp_docs) - covered)
print(f'GROWTH (outside the plan set): {len(grow)}')
for s in grow:
    print(f'  {s} [{"another lane carrier" if s in union else "nobody"}] ' + '; '.join(f'{i} {e} {u}' for i, e, u in exp_docs[s][:4]))
contents = sorted({r['stem'] for r in band if 'contents' in r['stem']})
print(f'banded rows in display-contents stems (not subtracted here): {len(contents)}')
print(f'plan stems with no exposed banded component: {len(covered - set(exp_docs))}')
print(f'plan carriers covered: {len(car & set(exp_docs))}/{len(car)}; missing {sorted(car - set(exp_docs))}')
for u, unit in L['revertUnits'].items():
    caps = set(unit['captures']['android'])
    print(f'revertUnit {u}: {len(caps)} android captures; each has an exposed component on its route: '
          f'{all(any(x[2] == u for x in exp_docs.get(c, [])) for c in caps)} '
          f'{[c for c in caps if not any(x[2] == u for x in exp_docs.get(c, []))]}')
