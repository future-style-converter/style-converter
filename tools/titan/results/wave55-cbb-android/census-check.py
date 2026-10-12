#!/opt/homebrew/bin/python3.12
# wave-55 L2 census cross-check (called by census.sh): the lane's behavioural census vs the generated contract.
#   - U1 documents (census-U1.junit.txt) and U2 axes (census-U2.junit.txt) → capture stems;
#   - per-platform / wire radius from the PATCHES' touched paths (a Compose-only change cannot move web, iOS or the wire);
#   - compared with expectations.json lanes["L2-cbb-android"]: captureCarriers, wireCarriers, revertUnits[U].captures;
#   - and with the planning census (wave55-plan/cbb-android.census.out.txt A / B).
# Prints IDENTICAL / GREW / SHRANK per set; a set that GROWS is a plan-regeneration need (PLAN §0 "Skeptic census").
import json, os, re, subprocess, sys
HERE, WT = sys.argv[1], sys.argv[2]
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
exp = json.load(open(os.path.join(ROOT, 'tools/titan/results/wave55-plan/expectations.json')))['lanes']['L2-cbb-android']
# U1: one line per box, "<doc>.json <id> [why] from->to"; the carrier is the document's capture stem.
u1 = [l.split()[0] for l in open(os.path.join(HERE, 'census-U1.junit.txt')) if l.strip()]
u1_docs = sorted(set(f[:-len('.json')] for f in u1))
# U2: first line "axes N owned M", then one owned axis per line.
lines = [l.rstrip('\n') for l in open(os.path.join(HERE, 'census-U2.junit.txt')) if l.strip()]
m = re.match(r'axes (\d+) owned (\d+)', lines[0]); axes, owned = int(m.group(1)), int(m.group(2))
u2_docs = sorted(set(l.split()[0][:-len('.json')] for l in lines[1:]))
print(f'U1 census (JUnit, wave54-final): {len(u1)} boxes in {len(u1_docs)} documents')
print(f'U2 census (JUnit, wave54-final): {axes} percentage axes, {owned} owned, in {len(u2_docs)} document(s): {u2_docs}')
for l in lines[1:]: print('    ' + l)
# Platform / wire radius from the patches' touched paths (git apply --numstat on each delivered patch).
touched = []
for p in sorted(f for f in os.listdir(HERE) if f.endswith('.patch')):
    out = subprocess.run(['git', 'apply', '--numstat', os.path.join(HERE, p)], cwd=ROOT, capture_output=True, text=True).stdout
    touched += [(p, l.split('\t')[2]) for l in out.splitlines()]
def under(prefixes): return [t for t in touched if any(t[1].startswith(x) for x in prefixes)]
web = under(['runtimes/web/', 'apps/web-harness/']); ios = under(['runtimes/swiftui/', 'apps/ios-harness/', 'Package.swift'])
wire = under(['converter/', 'tools/titan/extract-fixture.mjs', 'tools/titan/bidi', 'schema/'])
android = under(['runtimes/compose/', 'apps/android-harness/'])
print(f'touched paths: {len(touched)} (android/compose {len(android)} · web {len(web)} · ios {len(ios)} · wire {len(wire)})')
for p, t in touched: print(f'    {p}: {t}')
# Compare with the contract.
def cmp(name, mine, theirs, ref='expectations'):
    mine, theirs = sorted(set(mine)), sorted(set(theirs))
    if mine == theirs: v = 'IDENTICAL'
    elif set(mine) > set(theirs): v = f'GREW by {sorted(set(mine) - set(theirs))}  ← PLAN REGENERATION NEED'
    elif set(mine) < set(theirs): v = f'SHRANK by {sorted(set(theirs) - set(mine))}'
    else: v = f'DIFFER: + {sorted(set(mine) - set(theirs))} − {sorted(set(theirs) - set(mine))}'
    print(f'{name}: lane {len(mine)} vs {ref} {len(theirs)} — {v}')
union = sorted(set(u1_docs) | set(u2_docs))
cmp('captureCarriers.android', union if android else [], exp['captureCarriers']['android'])
cmp('captureCarriers.ios', [] if not ios else ['<ios touched>'], exp['captureCarriers']['ios'])
cmp('captureCarriers.web', [] if not web else ['<web touched>'], exp['captureCarriers']['web'])
cmp('wireCarriers', [] if not wire else ['<wire touched>'], exp['wireCarriers'])
cmp('revertUnits.U1.captures.android', u1_docs, exp['revertUnits']['U1']['captures']['android'])
cmp('revertUnits.U2.captures.android', u2_docs, exp['revertUnits']['U2']['captures']['android'])
# The planning census (wire mirror) — section A documents and section B carrier axes.
plan = open(os.path.join(ROOT, 'tools/titan/results/wave55-plan/cbb-android.census.out.txt')).read()
a = plan.split('## A.')[1].split('## B.')[0]
plan_a = sorted(set(re.findall(r'^(wpt__\S+)\s+\[', a, re.M)))
cmp('planning census A (documents)', u1_docs, plan_a, ref='cbb-android.census.out.txt')
plan_b = int(re.search(r'— (\d+) percentage axes in the corpus', plan).group(1))
plan_bc = int(re.search(r'B carriers: (\d+) axes', plan).group(1))
print(f'planning census B: {plan_b} percentage axes, {plan_bc} carriers — lane {axes} / {owned}: '
      + ('IDENTICAL' if (plan_b, plan_bc) == (axes, owned) else 'DIFFER'))
