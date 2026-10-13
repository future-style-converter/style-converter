#!/usr/bin/env python3
# tools/titan/results/wave55-hyphen-ios-linebox/mutations.py — wave-55 L1 (NB-ios) pin mutations, EXECUTED.
#
# Why: PLAN §0 "Pins and instruments can fail" — every pin P1–P8 of NormalLineBoxTests must be proven able to fail by
# an executed mutation of the mechanism (M1–M8, PLAN §2 L1 table). Each mutation is BRACKETED in the lane note
# (round 2, process-holes S12): `MUTATING <file> sha256 <pre>` is appended BEFORE the edit, the pin's suite runs (must be
# red, on the named test), the file is restored byte-exact from the in-memory copy, `RESTORED <file> sha256 <post>` is
# appended (post must equal pre), and the suite runs again (must be green). A lane killed mid-mutation leaves the sha256
# to restore against (§8 step 3 resume rule).
#
# Usage: python3 -B mutations.py --wt <lane worktree> --note <lane _note.md> --ir <run>/sections [--only M1,M2]
#   --ir feeds TEST_RUNNER_NB_IOS_PER_TEST_IR so the env-gated SAME-controls log runs too: green = 9 / 9 passed.
# Output: one block per mutation on stdout (sha256 prefixes and test names only: no worktree path in the record).
import argparse, hashlib, os, re, subprocess, sys

UNIT = 'runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift'
# (id, what it breaks, exact old text, replacement, the test(s) that must go red)
MUTATIONS = [
    ('M1', 'round the SUM (not each term)',
     '        return a + d + g\n',
     '        return (ascender - descender + leading).rounded(.toNearestOrAwayFromZero)\n',
     ['testP2EachTermRoundsAloneNeverTheSum']),
    ('M2', 'ceil the sum',
     '        return a + d + g\n',
     '        return (ascender - descender + leading).rounded(.up)\n',
     ['testP2EachTermRoundsAloneNeverTheSum']),
    ('M3', '.toNearestOrEven (banker\'s) instead of lroundf',
     'ascender.rounded(.toNearestOrAwayFromZero)', 'ascender.rounded(.toNearestOrEven)',
     ['testP3HalvesRoundAwayFromZero']),
    ('M4', 'drop the exactness guard',
     'guard declaredNormal, wptCapture, lineCountIsExact, horizontalWritingMode',
     'guard declaredNormal, wptCapture, horizontalWritingMode',
     ['testP4BlinkBoxOnlyUnderAllFourGuards']),
    ('M5', 'drop the WPT guard',
     'guard declaredNormal, wptCapture, lineCountIsExact, horizontalWritingMode',
     'guard declaredNormal, lineCountIsExact, horizontalWritingMode',
     ['testP4BlinkBoxOnlyUnderAllFourGuards']),
    ('M6', 'let the Blink box override a non-nil effective',
     '        if let effective { return effective }\n',
     '        if let effective, !(declaredNormal && wptCapture && lineCountIsExact && horizontalWritingMode) { return effective }\n',
     ['testP5EffectiveValueIsReturnedVerbatim']),
    ('M7', 'resolve every design to Inter in faceMetrics',
     '        if design == .default {\n', '        if design == .default || true {\n',
     ['testP6FaceResolutionMatchesLineBoxMetrics']),
    ('M8', 'drop the horizontal-writing-mode guard (D22)',
     'guard declaredNormal, wptCapture, lineCountIsExact, horizontalWritingMode',
     'guard declaredNormal, wptCapture, lineCountIsExact',
     ['testP4BlinkBoxOnlyUnderAllFourGuards', 'testP8VerticalRunIsDeclinedByTheHorizontalGuard']),
    # M9 — CONTINGENCY NB-ios-b only (run with --only M9 --expect 10 on the NB-ios + NB-ios-b state): the split never fires
    ('M9', 'NB-ios-b: verticalPads ignores blinkPinned (always symmetric)',
     '        if blinkPinned {\n', '        if false && blinkPinned {\n',
     ['testP9PadSplitOnlyForABlinkPinnedRun']),
]
# M3 rewrites all three rounding calls of blinkLineSpacing (the descender and gap terms too).
M3_EXTRA = [('(-descender).rounded(.toNearestOrAwayFromZero)', '(-descender).rounded(.toNearestOrEven)'),
            ('leading.rounded(.toNearestOrAwayFromZero)', 'leading.rounded(.toNearestOrEven)')]

def sha(b): return hashlib.sha256(b).hexdigest()

def note(path, line):
    # append-only: the bracket lines are the resume record
    with open(path, 'a', encoding='utf-8') as f: f.write(line + '\n')

def run_suite(wt, ir):
    # the pin's own suite only (focused, PLAN §0 "Single-writer suites"); per-checkout DerivedData under the worktree
    cmd = ['nice', 'xcodebuild', 'test', '-scheme', 'StyleConverterRuntime',
           '-destination', 'platform=macOS,variant=Mac Catalyst,arch=arm64',
           '-derivedDataPath', os.path.join(wt, '.dd'),
           '-only-testing:StyleConverterRuntimeTests/NormalLineBoxTests']
    env = dict(os.environ, TEST_RUNNER_NB_IOS_PER_TEST_IR=ir)  # xcodebuild forwards TEST_RUNNER_* into the runner
    p = subprocess.run(cmd, cwd=wt, capture_output=True, text=True, env=env)
    out = p.stdout + p.stderr
    failed = sorted(set(re.findall(r"NormalLineBoxTests (test\w+)\]' failed", out)))
    passed = sorted(set(re.findall(r"NormalLineBoxTests (test\w+)\]' passed", out)))
    built = 'BUILD FAILED' not in out and 'error: compile' not in out.lower()
    return p.returncode, failed, passed, built

ap = argparse.ArgumentParser()
ap.add_argument('--wt', required=True); ap.add_argument('--note', required=True); ap.add_argument('--ir', required=True)
ap.add_argument('--only', default='')
ap.add_argument('--expect', type=int, default=9)  # NormalLineBoxTests cases on the state under test (10 with NB-ios-b)
a = ap.parse_args()
only = set(x for x in a.only.split(',') if x)
path = os.path.join(a.wt, UNIT)
bad = 0
for mid, what, old, new, want in MUTATIONS:
    if (only and mid not in only) or (not only and mid == 'M9'): continue  # M9 needs the contingency on the tree
    pre = open(path, 'rb').read()
    src = pre.decode('utf-8')
    assert src.count(old) >= 1, f'{mid}: anchor missing'
    mut = src.replace(old, new) if mid != 'M3' else src.replace(old, new)
    if mid == 'M3':
        for o, n in M3_EXTRA: assert o in mut, f'{mid}: extra anchor'; mut = mut.replace(o, n)
    note(a.note, f'MUTATING {UNIT} sha256 {sha(pre)}  ({mid}: {what})')
    open(path, 'wb').write(mut.encode('utf-8'))
    rc, failed, passed, built = run_suite(a.wt, a.ir)
    red = rc != 0 and all(w in failed for w in want)
    # restore byte-exact from the in-memory copy, then verify
    open(path, 'wb').write(pre)
    post = open(path, 'rb').read()
    note(a.note, f'RESTORED {UNIT} sha256 {sha(post)}  ({mid})')
    rc2, failed2, passed2, _ = run_suite(a.wt, a.ir)
    green = rc2 == 0 and not failed2 and len(passed2) == a.expect
    ok = red and green and sha(post) == sha(pre)
    bad += not ok
    print(f'{mid} [{what}] pre {sha(pre)[:16]} post {sha(post)[:16]}')
    print(f'   mutated: exit {rc} red tests {failed} (want {want}) built {built} -> {"RED as required" if red else "NOT RED"}')
    print(f'   restored: exit {rc2} passed {len(passed2)}/{a.expect} failed {failed2} -> {"GREEN" if green else "NOT GREEN"}')
    print(f'   verdict {"OK" if ok else "VIOLATION"}')
    sys.stdout.flush()
print(f'mutations: {"ALL OK" if not bad else f"{bad} VIOLATION(S)"}')
sys.exit(1 if bad else 0)
