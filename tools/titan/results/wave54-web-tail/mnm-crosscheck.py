#!/usr/bin/env python3
# tools/titan/results/wave54-web-tail/mnm-crosscheck.py — wave 54 lane L6: every must-not-move cell of the lane
# (expectations.json lanes["L6-web-tail"].mustNotMove) against the executed DOM census of the landed lane.
# A web must-not-move cell must have an IDENTICAL composed DOM; a native one is untouched by construction
# (the lane edits no Swift / Kotlin file — checked from `git status` by the caller).
import json, sys
exp = json.load(open('tools/titan/results/wave54-plan/expectations.json'))['lanes']['L6-web-tail']
changed = {l.split()[1].split('/', 1)[1][:-5] for l in open(sys.argv[1]) if l.startswith('changed ')}
mnm = exp['mustNotMove']
web = [c for c in mnm if c.endswith(' web')]
# 'css-text/hyphens/hyphens-span-002.html web' → 'wpt__css-text__hyphens__hyphens-span-002'
stem = lambda cell: 'wpt__' + cell.rsplit(' ', 1)[0][:-5].replace('/', '__')
moved = [c for c in web if stem(c) in changed]
print(f"must-not-move cells {len(mnm)}: web {len(web)} · native {len(mnm) - len(web)}")
print("web must-not-move cells whose DOM changed:", moved or 'none')
