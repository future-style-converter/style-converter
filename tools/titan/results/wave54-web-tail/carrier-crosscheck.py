#!/usr/bin/env python3
# tools/titan/results/wave54-web-tail/carrier-crosscheck.py — wave 54 lane L6: the executed DOM census vs the plan's
# pre-registered carrier sets (expectations.json lanes["L6-web-tail"]: captureCarriers + revertUnits.*.captures).
# Usage: carrier-crosscheck.py <census-diff.out.txt> <unit|all>
import json, sys
exp = json.load(open('tools/titan/results/wave54-plan/expectations.json'))['lanes']['L6-web-tail']
diff = [l.split()[1] for l in open(sys.argv[1]) if l.startswith('changed ')]
# 'css-ui/wpt__css-ui__box-sizing-007.json' → 'wpt__css-ui__box-sizing-007' (the capture stem).
got = sorted(d.split('/', 1)[1][:-5] for d in diff)
unit = sys.argv[2]
want = sorted(exp['captureCarriers']['web'] if unit == 'all' else exp['revertUnits'][unit]['captures']['web'])
print(f"unit {unit}: census changed {len(got)} · pre-registered web carriers {len(want)}")
print("  in census, not pre-registered:", sorted(set(got) - set(want)) or 'none')
print("  pre-registered, not in census:", sorted(set(want) - set(got)) or 'none')
print("  native carriers pre-registered:", {p: len(exp['captureCarriers'][p]) for p in ('ios', 'android')}, "· wire carriers:", len(exp['wireCarriers']))
print("  VERDICT:", "EQUAL" if got == want else "DIFFERENT")
