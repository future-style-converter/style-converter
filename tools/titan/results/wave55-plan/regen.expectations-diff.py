#!/usr/bin/env python3
# tools/titan/results/wave55-plan/regen.expectations-diff.py — the STRUCTURAL diff of expectations.json across the pre-landing
# regeneration (regen.done.md; PLAN §8 step 4 ¶2): every JSON path whose value differs, was added or removed, checked against the
# INTENDED set below — exit 1 if a path changed that is not intended, or an intended path did not change. A list of strings
# (mayMove, unionMayMoveCaptures) prints its set difference; a string prints its changed span (common prefix and suffix cut).
# Usage: python3 regen.expectations-diff.py <before.json> <after.json> <before label> <plan-build.py path>
import hashlib, json, sys
a_p, b_p, label, gen = sys.argv[1:5]
A, B = json.load(open(a_p)), json.load(open(b_p))
sha = lambda p: hashlib.sha256(open(p, 'rb').read()).hexdigest()
L1, L4, L5, L6 = ('/lanes/L1-hyphen-ios-linebox', '/lanes/L4-ua-heading-margin', '/lanes/L5-counter-suffix-rows', '/lanes/L6-android-paint-order')
GROW = 'wpt__css-display__display-contents-dynamic-pseudo-insertion-001'
INTENDED = {   # path → why (PLAN §10 item; the lane's request)
    '/generatedWith/inputs/tools/titan/results/wave55-hyphen-ios-linebox/census.out.txt': 'L1: the radius 58 / 18 / 4 SAME is asserted from the lane census (§10 item 3)',
    '/generatedWith/inputs/tools/titan/results/wave55-android-paint-order/fix-r1/census.out.txt': 'L6: the growth stem is asserted from the lane census (§10 item 2)',
    '/generatedWith/inputs/tools/titan/results/wave55-android-paint-order/reverify-r1/census.out.txt': 'L6: … and from the re-verifier\'s census',
    f'{L1}/carrierRule/NB-ios': 'L1 SHOULD: 60 / 19 / 5 SAME → 58 / 18 / 4 SAME, all-prop-001 outside',
    f'{L1}/revertUnits/NB-ios/commit': 'L1: "the 5 SAME controls" → the in-radius SAME controls + all-prop-001 (0 runs)',
    f'{L1}/contingencyUnits/NB-ios-b/trigger': 'L1 SHOULD (skeptic should-fix 1): the R1 signature',
    f'{L1}/stage1Decision': 'L1 SHOULD: the R1 signature routes to --contingency L1:NB-ios-b, re-probe requires the SAME controls byte-identical',
    f'{L4}/ownership/owned[3]': 'L4 NIT: headingStandsDown KEPT (the lane\'s decision 1), not removed',
    f'{L4}/revertUnits/U2-android/commit': 'L4 NIT: the same, in the unit commit text',
    f'{L5}/carrierRule/U3-ios': 'L5 MUST (fix-r1): U3-ios repaints 6 glyphs in 4 rows; the capture set is unchanged',
    f'{L5}/predictions[0]/to': 'L5 MUST (fix-r1 a): the replay grafted only the 1',
    f'{L5}/predictions[0]/kind': 'L5: rows 3-6 and 9 → 9-12',
    f'{L5}/predictions[0]/confidence': 'L5 MUST (fix-r1 a): MED-HIGH → MED',
    f'{L5}/predictions[0]/geometry': 'L5 R5: [H] / [T] ios twin-only WRONG at wave55-pre → the look; OK from wave55-probe',
    f'{L5}/stage1Decision': 'L5 R4 / R5: the precedence and the wave55-pre key predictions',
    f'{L5}/geometryProbes[0]/teeth': 'L5 R1-R3: the probe edit, the residual SPEC and the re-grafted replays (proofs named)',
    f'{L6}/carrierRule/U2': 'L6 MUST: the growth stem by rule + U2\'s clip condition (29 bands / 26 documents keep the base chain)',
    f'{L6}/mayMove/android': f'L6 MUST: + {GROW} (unscored)',
    f'{L6}/mayMoveRule': 'L6 MUST (fix-r1 item 4): the opacity-group and UA-widget-replica pictures',
    f'{L6}/stage1Decision': 'L6 MUST: the same three pictures in the decision',
    f'{L6}/revertUnits/U1/commit': 'L6: + P5a-f',
    f'{L6}/revertUnits/U2/commit': 'L6: the clip condition + P3h / P3i',
    '/unionMayMoveCaptures/android': f'L6: + {GROW} (the union may-move set is L6\'s)',
    '/stayDegenerateEvenIfPass[6]': 'L5: the honesty-list note reads rows 3-6 and 9-12 (U3-ios is run-wide)',
    '/planDecisions[id=C5]/resolution': 'L1: the tripwire keeps 3 in-radius controls (all-prop-001 outside), not 4',
    '/planDecisions[id=D41]': 'the regeneration\'s own decision row',
}
out = []
def walk(x, y, path):
    if isinstance(x, dict) and isinstance(y, dict):
        for k in sorted(set(x) | set(y), key=str):
            p = f'{path}/{k}'
            if k not in y:
                out.append(('-', p, x[k], None))
            elif k not in x:
                out.append(('+', p, None, y[k]))
            else:
                walk(x[k], y[k], p)
    elif isinstance(x, list) and isinstance(y, list) and x + y and all(isinstance(e, dict) and 'id' in e for e in x + y):
        xi, yi = {e['id']: e for e in x}, {e['id']: e for e in y}
        for k in [e['id'] for e in x] + [e['id'] for e in y if e['id'] not in xi]:
            p = f'{path}[id={k}]'
            if k not in yi:
                out.append(('-', p, xi[k], None))
            elif k not in xi:
                out.append(('+', p, None, yi[k]))
            else:
                walk(xi[k], yi[k], p)
    elif isinstance(x, list) and isinstance(y, list) and len(x) == len(y):
        for i, (u, v) in enumerate(zip(x, y)):
            walk(u, v, f'{path}[{i}]')
    elif x != y:
        out.append(('~', path, x, y))
walk(A, B, '')
cut = lambda v: (json.dumps(v, ensure_ascii=False) if not isinstance(v, str) else v)
print(f'# regen.expectations-diff.txt — the structural diff of expectations.json, {label} → the pre-landing regeneration (plan-build.py, never hand-edited)')
print(f'# before sha256 {sha(a_p)} · after sha256 {sha(b_p)} (plan-build.py {sha(gen)[:16]}…)')
print(f'# {len(out)} difference(s); every other value identical (the JSON is the indent-1 rendering both times)\n')
for op, p, x, y in out:
    print(f'{op} {p}    [{"INTENDED: " + INTENDED[p] if p in INTENDED else "NOT INTENDED"}]')
    if op == '~' and isinstance(x, list) and isinstance(y, list) and all(isinstance(e, str) for e in x + y):
        print(f'  list of {len(x)} → {len(y)}: added {sorted(set(y) - set(x))} · removed {sorted(set(x) - set(y))} · order: sorted both times {x == sorted(x) and y == sorted(y)}')
    elif op == '~' and isinstance(x, str) and isinstance(y, str):
        n = 0
        while n < min(len(x), len(y)) and x[n] == y[n]:
            n += 1
        m = 0
        while m < min(len(x), len(y)) - n and x[-1 - m] == y[-1 - m]:
            m += 1
        print(f'  common prefix {n} chars, common suffix {m} chars')
        print(f'  before (span): {x[n:len(x) - m]!r}')
        print(f'  after  (span): {y[n:len(y) - m]}')
    elif op == '+':
        print(f'  after:  {cut(y)}')
    else:
        print(f'  before: {cut(x)}')
        print(f'  after:  {cut(y)}')
    print()
changed = {p for _, p, _, _ in out}
stray = sorted(changed - set(INTENDED))
missing = sorted(set(INTENDED) - changed)
print(f'# top-level keys: before {len(A)} · after {len(B)}; planDecisions ids: before {len(A.get("planDecisions", []))} · after {len(B.get("planDecisions", []))}')
print(f'# intended paths {len(INTENDED)} · changed paths {len(changed)} · NOT intended {len(stray)} {stray} · intended but unchanged {len(missing)} {missing}')
print(f'# the structural diff is exactly the intended set: {"YES" if not stray and not missing else "NO"}')
sys.exit(1 if stray or missing else 0)
