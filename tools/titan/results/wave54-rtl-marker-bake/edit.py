#!/usr/bin/env python3
# tools/titan/results/wave54-rtl-marker-bake/edit.py — exact-string edit helper used to author this lane's hunks:
# every (old, new) pair must match EXACTLY ONCE, or nothing is written. Usage: edit.py <file> <pairs.json>
import json, sys
path, pairs = sys.argv[1], json.load(open(sys.argv[2], encoding='utf-8'))
s = open(path, encoding='utf-8').read()
for i, (old, new) in enumerate(pairs):
    n = s.count(old)
    if n != 1:
        sys.exit(f'pair {i}: old string found {n} times — nothing written')
    s = s.replace(old, new)
open(path, 'w', encoding='utf-8').write(s)
print(f'{path}: {len(pairs)} edit(s) applied')
