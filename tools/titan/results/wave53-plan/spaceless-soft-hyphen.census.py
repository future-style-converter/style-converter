#!/usr/bin/env python3
# tools/titan/results/wave53-plan/spaceless-soft-hyphen.census.py
#
# The committed METHOD behind spaceless-soft-hyphen.md §6 and PLAN.md §2 L2 (plan-skeptic should-fix: the brief's
# counts had no script beside them). Pure JSON over one run's per-test IR; no image is decoded, nothing is built.
#
# Usage: python3 tools/titan/results/wave53-plan/spaceless-soft-hyphen.census.py [run-id]     (default wave52-ship)
#
# What is counted, and what is NOT (the definitions the 17 / 24 / 21 / 12 / 9 of the plan rest on):
#   docs       — every `sections/*/per-test-ir/*.json` file (1435 on wave52-ship; `ls …/per-test-ir/ | wc -l` prints
#                1494 because it also counts the 30 directory headers and 29 blank separator lines).
#   shy docs   — documents whose raw JSON holds U+00AD anywhere.
#   strings    — space-less strings holding U+00AD that a text label can receive, from TWO sources only:
#                  leaf  = a component's own `text` when it carries NO `meta.runs`;
#                  run   = each `meta.runs[i].text` piece of a runs host.
#                A runs host's own `text` field is a MIRROR of its merged run pieces (the extractor writes both); it is
#                counted separately as `hostMirror` and EXCLUDED from the 24 — counting it would add 6 (all in
#                hyphens-out-of-flow-001) and give 30. "Space-less" = no U+0020 in the string.
#   abspos     — runs hosts with a `{child: …}` piece whose component carries Position ABSOLUTE|FIXED (21).
#   inert      — of those members, the ones PLAN.md L2 F2's predicate admits: tag in InlineRunFold.kt TEXT_MEMBER_TAGS
#                (span, time, data), no children / runs / decoration properties, and every property in
#                {Position ABSOLUTE|FIXED, Color with alpha 0, Hyphens, Border*Color} (12, all hyphens-out-of-flow-00x).
#                The rest still bail (9).
import glob, json, os, sys
from collections import Counter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
run = sys.argv[1] if len(sys.argv) > 1 else 'wave52-ship'
docs = sorted(glob.glob(f'{ROOT}/tools/titan/runs/{run}/sections/*/per-test-ir/*.json'))
SHY = '­'
TEXT_MEMBER_TAGS = {'span', 'time', 'data'}                  # InlineRunFold.kt:172
DECORATION = ('TextDecoration', 'TextShadow', 'Outline', 'BoxShadow', 'Background')

def inert(member, has_children):
    # The F2 predicate, written from PLAN.md §2 L2 "Change" F2 (a census twin, not the Kotlin code).
    tag = (member.get('meta') or {}).get('sourceTag')
    if tag not in TEXT_MEMBER_TAGS or has_children or (member.get('meta') or {}).get('runs'):
        return False
    for p in member.get('properties', []):
        t, v = p['type'], p['data']
        if t.startswith(DECORATION):
            return False
        if t == 'Position' and v in ('ABSOLUTE', 'FIXED'):
            continue
        if t == 'Color' and isinstance(v, dict) and (v.get('srgb') or {}).get('a', 1) == 0:
            continue
        if t == 'Hyphens' or (t.startswith('Border') and t.endswith('Color')):
            continue
        return False
    return True

shy_docs, strings, mirrors, abspos, inert_n, bail_n = [], Counter(), Counter(), [], [], []
for path in docs:
    raw = open(path, encoding='utf-8').read()
    stem = os.path.basename(path)[:-5]
    comps = json.loads(raw)['components']
    if SHY in raw:
        shy_docs.append(stem)
    by_name = {c.get('name'): c for c in comps}
    parents = {(c.get('slot') or {}).get('parent') for c in comps}
    for c in comps:
        runs = (c.get('meta') or {}).get('runs')
        if runs:
            for piece in runs:
                t = piece.get('text')
                if t and SHY in t and ' ' not in t:
                    strings[(stem, 'run')] += 1
            t = c.get('text')
            if t and SHY in t and ' ' not in t:
                mirrors[stem] += 1                           # the host's mirror of its merged pieces — NOT counted
            for piece in runs:
                m = by_name.get(piece.get('child'))
                pos = {p['type']: p['data'] for p in (m or {}).get('properties', [])}.get('Position')
                if m and pos in ('ABSOLUTE', 'FIXED'):
                    abspos.append((stem, c['id'], m['id']))
                    (inert_n if inert(m, m['id'] in parents) else bail_n).append((stem, m['id']))
        else:
            t = c.get('text')
            if t and SHY in t and ' ' not in t:
                strings[(stem, 'leaf')] += 1

print(f'run {run}: per-test IR docs {len(docs)}')
print(f'shy docs {len(shy_docs)}')
print(f'space-less U+00AD strings (leaf + run pieces) {sum(strings.values())}  '
      f'[leaf {sum(v for (s, k), v in strings.items() if k == "leaf")}, run {sum(v for (s, k), v in strings.items() if k == "run")}]')
print(f'host text mirrors excluded {sum(mirrors.values())} {dict(mirrors)}  (counted in: {sum(strings.values()) + sum(mirrors.values())})')
print(f'abspos/fixed members in runs hosts {len(abspos)}: inert {len(inert_n)}, still bail {len(bail_n)}')
print('inert docs', sorted(Counter(s for s, _ in inert_n).items()))
print('bail docs', sorted(Counter(s for s, _ in bail_n).items()))
for (s, k), v in sorted(strings.items()):
    print(f'  {k:4} {v:2} {s}')
# Expected on wave52-ship: docs 1435 · shy docs 17 · strings 24 [leaf 15, run 9] · mirrors 6 (→ 30 if counted) ·
# abspos 21: inert 12 (hyphens-out-of-flow-001 ×6, -002 ×6), still bail 9.
