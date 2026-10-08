#!/usr/bin/env python3
# tools/titan/results/wave53-soft-hyphen/run-census.py — runs census-jvm.kt.txt on two arms and restores byte-exact.
# Arm "f2-on": the lane's tree as is (F1 + F2). Arm "f2-off": InlineRunFold.kt's F2 arm disabled (`else if (false)`),
# i.e. the pre-F2 fold decisions. Arm "f1-off": F2 back on, PreBreakPipeline.kt's guard restored to the space-only
# decline, i.e. the pre-F1 pre-break decisions. Each source file touched is sha256-checked before/after; the outputs are renamed per arm.
import hashlib, os, subprocess, sys
T = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
L = T + '/tools/titan/results/wave53-soft-hyphen'
TEST = T + '/runtimes/compose/src/test/java/com/styleconverter/runtime/typography/inline/InlineRunFoldTest.kt'
FOLD = T + '/runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InlineRunFold.kt'
PBP = T + '/runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipeline.kt'
F1 = "if (text.indexOf(' ') < 0 && text.indexOf('\\u00AD') < 0) return identity"
F2 = '} else if (tag in TEXT_MEMBER_TAGS && InertOutOfFlowMember.admits(child)) {'
sha = lambda p: hashlib.sha256(open(p, 'rb').read()).hexdigest()
test0, fold0, pbp0 = open(TEST, 'rb').read(), open(FOLD, 'rb').read(), open(PBP, 'rb').read()
h_test, h_fold, h_pbp = sha(TEST), sha(FOLD), sha(PBP)
env = dict(os.environ, JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21']).decode().strip())
def run(arm):
    p = subprocess.run(['./gradlew', ':runtime:testDebugUnitTest', '-q', '--tests', '*SoftHyphenCensusProbe'],
                       cwd=T + '/apps/android-harness', env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    print(arm, 'exit', p.returncode, p.stdout.decode()[-800:])
    os.replace(L + '/census-jvm.out.txt', L + f'/census-jvm.{arm}.txt')
try:
    with open(TEST, 'ab') as f:
        f.write(b'\n' + open(L + '/census-jvm.kt.txt', 'rb').read())
    run('f2-on')
    s = fold0.decode()
    assert s.count(F2) == 1
    open(FOLD, 'w').write(s.replace(F2, '} else if (false) {'))
    run('f2-off')
    open(FOLD, 'wb').write(fold0)
    t = pbp0.decode()
    assert t.count(F1) == 1
    open(PBP, 'w').write(t.replace(F1, "if (text.indexOf(' ') < 0) return identity"))
    run('f1-off')
finally:
    open(TEST, 'wb').write(test0); open(FOLD, 'wb').write(fold0); open(PBP, 'wb').write(pbp0)
assert sha(TEST) == h_test and sha(FOLD) == h_fold and sha(PBP) == h_pbp
print('restored', 'InlineRunFoldTest.kt', h_test, 'InlineRunFold.kt', h_fold, 'PreBreakPipeline.kt', h_pbp)
