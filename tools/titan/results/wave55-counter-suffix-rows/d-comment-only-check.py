#!/usr/bin/env python3
# d-comment-only-check.py <unit-D.patch> — the unit-D pin (lane L5): every
# added / removed line of the patch is a comment line (// /// * /* or blank),
# so unit D changes no code and no build output; and the five stale phrases
# the brief names are gone from the files it touches (exit 1 otherwise).
import re, sys
patch = open(sys.argv[1], encoding='utf-8').read().splitlines()
bad, files, changed = [], [], 0
for ln in patch:
    if ln.startswith('+++ b/'):
        files.append(ln[6:])
    if ln.startswith(('+++', '---', 'diff --git', 'index ', '@@', '#')):
        continue
    if ln[:1] in '+-':
        changed += 1
        body = ln[1:].strip()
        if body and not re.match(r'^(//|\*|/\*)', body):
            bad.append(ln)
print(f"unit-D: {len(files)} files, {changed} changed lines, non-comment lines: {len(bad)}")
for b in bad:
    print('  NON-COMMENT', b)
stale = re.compile(r'still lack markers for an|NO Compose twin yet|no Compose arm yet|TWIN DIVERGENCE — stated|inline-END side|end side under RTL')
added = [ln[1:] for ln in patch if ln.startswith('+') and not ln.startswith('+++')]
hits = [a for a in added if stale.search(a)]
print(f"stale phrases in the added text: {len(hits)}")
sys.exit(1 if bad or hits else 0)
