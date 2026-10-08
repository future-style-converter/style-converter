#!/usr/bin/env python3
# Wave 54 L5 RE-VERIFIER — derives the GO-SMALL Android capture-carrier set from the probe's raw facts
# (reverify/probe.<run>.jsonl): a tagged component MOVES the Android capture iff the shipped step fires on it
# (same == false) AND no ancestor runs host folds (a Folded ancestor paints the member's text itself, from the
# member's OWN properties, so the member's RenderComponent — the seam — never runs). iOS: the Swift diff is
# comment-only, so 0 by construction (checked separately). Also prints the four inset heading hosts' rows.
import json, sys, os
HERE = os.path.dirname(os.path.abspath(__file__))
for run in ('wave53-final', 'wave54-open'):
    rows = [json.loads(l) for l in open(os.path.join(HERE, f'probe.{run}.jsonl'))]
    summ = [r for r in rows if r.get('summary')][0]; rows = [r for r in rows if not r.get('summary')]
    fires = [r for r in rows if not r['same']]
    rendered = [r for r in fires if not any(h.endswith('=Folded') for h in r['ancestorFolds'])]
    carriers = sorted({r['doc'] for r in rendered})
    print(f'# {run}: documents={summ["documents"]} tagged={len(rows)} in {len({r["doc"] for r in rows})} docs; firing={len(fires)}; rendered-firing={len(rendered)}; '
          f'All-on-tagged={sum(r["all"] for r in rows)} var-on-tagged={sum(r["var"] for r in rows)}')
    print(f'# {run}: ANDROID CARRIERS ({len(carriers)}): ' + ' '.join(c.split('/')[-1].replace('wpt__', '') for c in carriers))
    for r in rows:
        if 'inset-0' in r['doc'] or 'subelements' in r['doc']:
            print(f"  {r['doc'].split('__')[-1]} {r['tag']} {r['id'].split('__')[-1]} kids={r['kids']} standsDown={r['standsDown']} same={r['same']} "
                  f"size={r['size']} ownFold={r['ownFold']} ancestors={r['ancestorFolds']}")
