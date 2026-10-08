#!/usr/bin/env python3
# tools/titan/results/wave53-soft-hyphen/run-census-fixpass.py — fix-pass re-run of the lane's JVM census, arm "f2-on"
# only, on the fix-pass tree (InertOutOfFlowMember.kt narrowed by D4 and hardened by D7). Same mechanism as
# run-census.py: census-jvm.kt.txt is appended to InlineRunFoldTest.kt for one focused run, then the test file is
# restored byte-exact (sha256 asserted). Output: census-jvm.fixpass-f2-on.txt, to be diffed against census-jvm.f2-on.txt
# (the builder's F1+F2 arm): any difference is a fold decision the fix pass moved.
import hashlib, os, subprocess
T = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
L = T + '/tools/titan/results/wave53-soft-hyphen'
TEST = T + '/runtimes/compose/src/test/java/com/styleconverter/runtime/typography/inline/InlineRunFoldTest.kt'
sha = lambda p: hashlib.sha256(open(p, 'rb').read()).hexdigest()
test0, h_test = open(TEST, 'rb').read(), sha(TEST)
env = dict(os.environ, JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21']).decode().strip())
try:
    # Append the census class for exactly one focused run.
    with open(TEST, 'ab') as f:
        f.write(b'\n' + open(L + '/census-jvm.kt.txt', 'rb').read())
    p = subprocess.run(['./gradlew', ':runtime:testDebugUnitTest', '-q', '--tests', '*SoftHyphenCensusProbe'],
                       cwd=T + '/apps/android-harness', env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    print('f2-on (fix pass) exit', p.returncode, p.stdout.decode()[-800:])
    os.replace(L + '/census-jvm.out.txt', L + '/census-jvm.fixpass-f2-on.txt')
finally:
    # Restore the test file whatever happened.
    open(TEST, 'wb').write(test0)
assert sha(TEST) == h_test
print('restored InlineRunFoldTest.kt', h_test)
