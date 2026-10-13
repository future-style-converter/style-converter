#!/usr/bin/env python3
# tools/titan/results/wave55-hyphen-ios-linebox/skeptic-mutations.py — wave-55 L1 SKEPTIC mutation replay (independent of
# the lane's mutations.py: every mutation text below is re-written, several as different-but-equivalent variants, plus
# extra probes the lane did not run).
#
# Why: the skeptic must replay every pin's mutation itself (apply → focused test → RED → byte-exact restore → GREEN),
# bracketed (PLAN §0 "Mutations are bracketed"): `MUTATING <file> sha256 <pre>` is appended to skeptic.md BEFORE the edit
# and `RESTORED <file> sha256 <post>` after the restore (post must equal pre). Extra probes (SX*) are expected to
# SURVIVE (stay green) when they name an untested branch — that survival is the finding, recorded as such.
#
# Usage: python3 -B skeptic-mutations.py --wt <skeptic worktree> --rec <skeptic.md> --ir <run>/sections [--only ids]
#        [--expect N]   (N = NormalLineBoxTests cases on the state under test: 9 for NB-ios, 10 with NB-ios-b)
import argparse, hashlib, os, re, subprocess, sys

UNIT = 'runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift'
ROUND = '.rounded(.toNearestOrAwayFromZero)'
# (id, what, [(old, new) …], tests that must go red — [] means "expected to SURVIVE" (an untested branch))
MUTS = [
    ('K1', 'round the SUM of the raw terms (variant: terms unrounded, one rounding at the return)',
     [('let a = ascender' + ROUND, 'let a = ascender'), ('let d = (-descender)' + ROUND, 'let d = (-descender)'),
      ('let g = leading' + ROUND, 'let g = leading'), ('        return a + d + g\n', '        return (a + d + g)' + ROUND + '\n')],
     ['testP2EachTermRoundsAloneNeverTheSum']),
    ('K2', 'ceil the sum (variant: terms unrounded, .up at the return)',
     [('let a = ascender' + ROUND, 'let a = ascender'), ('let d = (-descender)' + ROUND, 'let d = (-descender)'),
      ('let g = leading' + ROUND, 'let g = leading'), ('        return a + d + g\n', '        return (a + d + g).rounded(.up)\n')],
     ['testP2EachTermRoundsAloneNeverTheSum']),
    ('K3', "banker's rounding on the DESCENDER term only (variant of M3)",
     [('let d = (-descender)' + ROUND, 'let d = (-descender).rounded(.toNearestOrEven)')],
     ['testP3HalvesRoundAwayFromZero']),
    ('K4', 'drop the exactness guard',
     [('guard declaredNormal, wptCapture, lineCountIsExact, horizontalWritingMode',
       'guard declaredNormal, wptCapture, horizontalWritingMode')], ['testP4BlinkBoxOnlyUnderAllFourGuards']),
    ('K5', 'drop the WPT guard',
     [('guard declaredNormal, wptCapture, lineCountIsExact, horizontalWritingMode',
       'guard declaredNormal, lineCountIsExact, horizontalWritingMode')], ['testP4BlinkBoxOnlyUnderAllFourGuards']),
    ('K6', 'Blink box overrides a non-nil effective (variant: effective ignored entirely)',
     [('        if let effective { return effective }\n', '        _ = effective\n')],
     ['testP5EffectiveValueIsReturnedVerbatim']),
    ('K7', 'faceMetrics: every no-face design resolves to the Inter table (variant of M7)',
     [('        if design == .default {\n', '        if design == .default || design != .default {\n')],
     ['testP6FaceResolutionMatchesLineBoxMetrics']),
    ('K8', 'drop the horizontal-writing-mode guard (D22)',
     [('guard declaredNormal, wptCapture, lineCountIsExact, horizontalWritingMode',
       'guard declaredNormal, wptCapture, lineCountIsExact')],
     ['testP4BlinkBoxOnlyUnderAllFourGuards', 'testP8VerticalRunIsDeclinedByTheHorizontalGuard']),
    ('K0', 'drop the declared-normal guard (no lane mutation names it)',
     [('guard declaredNormal, wptCapture, lineCountIsExact, horizontalWritingMode',
       'guard wptCapture, lineCountIsExact, horizontalWritingMode')], ['testP4BlinkBoxOnlyUnderAllFourGuards']),
    ('SX1', 'faceMetrics IGNORES the document face (branch 1 skipped) — the face 12 of the 14 carriers paint',
     [('        if let name = faceName, let f = UIFont(name: name, size: fontSizePx) {\n',
       '        if let name = faceName, false, let f = UIFont(name: name, size: fontSizePx) {\n')], []),
    ('SX2', 'the below-raw breadcrumb never fires (log-once removed)',
     [('        if box < raw {\n', '        if box < raw && false {\n')], []),
    ('SX3', "banker's rounding on the LINE-GAP term only",
     [('let g = leading' + ROUND, 'let g = leading.rounded(.toNearestOrEven)')], []),
    ('SX4', 'the size-0 guard removed (a 0 box returned instead of nil)',
     [('        guard box > 0 else { return nil }\n', '        _ = box > 0\n')], []),
    ('K9', 'NB-ios-b: verticalPads never splits (contingency state only)',
     [('        if blinkPinned {\n', '        if blinkPinned && false {\n')], ['testP9PadSplitOnlyForABlinkPinnedRun']),
]

def sha(b):
    # the bracket checksum (sha256 of the file bytes)
    return hashlib.sha256(b).hexdigest()

def rec(path, line):
    # append-only bracket record in skeptic.md (never the lane's note)
    with open(path, 'a', encoding='utf-8') as f:
        f.write(line + '\n')

def suite(wt, ir):
    # NormalLineBoxTests only, Catalyst, per-worktree DerivedData (PLAN §0); TEST_RUNNER_* reaches the runner's env
    cmd = ['nice', 'xcodebuild', 'test', '-scheme', 'StyleConverterRuntime',
           '-destination', 'platform=macOS,variant=Mac Catalyst,arch=arm64', '-derivedDataPath', os.path.join(wt, '.dd'),
           '-only-testing:StyleConverterRuntimeTests/NormalLineBoxTests']
    p = subprocess.run(cmd, cwd=wt, capture_output=True, text=True, env=dict(os.environ, TEST_RUNNER_NB_IOS_PER_TEST_IR=ir))
    out = p.stdout + p.stderr
    failed = sorted(set(re.findall(r"NormalLineBoxTests (test\w+)\]' failed", out)))
    passed = sorted(set(re.findall(r"NormalLineBoxTests (test\w+)\]' passed", out)))
    skipped = sorted(set(re.findall(r"NormalLineBoxTests (test\w+)\]' skipped", out)))
    return p.returncode, failed, passed, skipped, ('BUILD FAILED' in out or 'TEST BUILD FAILED' in out)

ap = argparse.ArgumentParser()
ap.add_argument('--wt', required=True); ap.add_argument('--rec', required=True); ap.add_argument('--ir', required=True)
ap.add_argument('--only', default=''); ap.add_argument('--expect', type=int, default=9)
a = ap.parse_args()
only = [x for x in a.only.split(',') if x]
path = os.path.join(a.wt, UNIT)
bad = 0
for mid, what, edits, want in MUTS:
    if only and mid not in only:
        continue
    if not only and mid == 'K9':
        continue  # the contingency mutation needs NB-ios-b on the tree
    pre = open(path, 'rb').read()
    src = pre.decode('utf-8')
    for old, new in edits:
        assert src.count(old) == 1, f'{mid}: anchor not unique/missing: {old!r}'
        src = src.replace(old, new)
    rec(a.rec, f'MUTATING {UNIT} sha256 {sha(pre)}  ({mid}: {what})')
    open(path, 'wb').write(src.encode('utf-8'))
    rc, failed, passed, skipped, buildfail = suite(a.wt, a.ir)
    open(path, 'wb').write(pre)
    post = open(path, 'rb').read()
    rec(a.rec, f'RESTORED {UNIT} sha256 {sha(post)}  ({mid})')
    rc2, failed2, passed2, skipped2, _ = suite(a.wt, a.ir)
    green = rc2 == 0 and not failed2 and len(passed2) == a.expect
    if want:
        red = rc != 0 and not buildfail and all(w in failed for w in want)
        ok = red and green and post == pre
        tag = 'RED as required' if red else 'NOT RED'
    else:
        survived = rc == 0 and not failed
        ok = green and post == pre
        tag = 'SURVIVED (untested branch)' if survived else f'RED {failed}'
    bad += not ok
    print(f'{mid} [{what}] pre {sha(pre)[:16]} post {sha(post)[:16]}')
    print(f'   mutated: exit {rc} buildFailed {buildfail} failed {failed} (want {want or "none: probe"}) -> {tag}')
    print(f'   restored: exit {rc2} passed {len(passed2)}/{a.expect} failed {failed2} skipped {skipped2} -> {"GREEN" if green else "NOT GREEN"}')
    print(f'   verdict {"OK" if ok else "VIOLATION"}')
    sys.stdout.flush()
print(f'skeptic mutations: {"ALL OK" if not bad else f"{bad} VIOLATION(S)"}')
sys.exit(1 if bad else 0)
