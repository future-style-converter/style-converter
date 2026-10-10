#!/usr/bin/env python3
# tools/titan/results/wave54-web-tail/dom-census-diff.py — wave 54 lane L6: diff two dom-census tables.
# Usage: dom-census-diff.py <before.json> <after.json>  → prints the changed documents and their count.
import json, sys
a = json.load(open(sys.argv[1])); b = json.load(open(sys.argv[2]))
# Same document set, or the comparison is meaningless.
assert set(a) == set(b), f"document sets differ: {len(set(a) ^ set(b))}"
changed = sorted(k for k in a if a[k] != b[k])
for k in changed: print('changed', k)
print(f"documents {len(a)} · changed {len(changed)} · identical {len(a) - len(changed)}")
