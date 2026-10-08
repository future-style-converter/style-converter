#!/usr/bin/env python3
# tools/titan/results/wave53-soft-hyphen/skeptic-mutate.py — the wave-53 L2 SKEPTIC's own mutation replayer (independent of
# the lane's mutate.py). It runs ONLY inside an export tree (git archive HEAD + L2's owned files + both seam patches),
# never on the shared tree: apply ONE exact-string replacement (asserted to match exactly once), run the focused suite,
# parse the JUnit XML / xcresult summary for per-test outcomes, restore the original bytes, assert sha256 equal, re-run.
# Usage: skeptic-mutate.py <export-root> <compose|swift> <rel-file> <old-file> <new-file> <test-filter>...
#   (old/new are files holding the exact strings so no shell quoting can alter them)
import glob, hashlib, json, os, re, subprocess, sys, xml.etree.ElementTree as ET
root, kind, rel, oldf, newf, *filters = sys.argv[1:]
path = os.path.join(root, rel)
old, new = open(oldf, encoding='utf-8').read(), open(newf, encoding='utf-8').read()
orig = open(path, 'rb').read()
sha = lambda b: hashlib.sha256(b).hexdigest()
h0 = sha(orig)
env = dict(os.environ, JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21']).decode().strip())
def run():
    if kind == 'compose':
        res = os.path.join(root, 'runtimes/compose/build/test-results/testDebugUnitTest')
        for f in glob.glob(res + '/*.xml'): os.remove(f)
        cmd = ['./gradlew', '--no-daemon', '-q', '-Pkotlin.compiler.execution.strategy=in-process', ':runtime:testDebugUnitTest']
        for t in filters: cmd += ['--tests', t]
        p = subprocess.run(cmd, cwd=os.path.join(root, 'apps/android-harness'), env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        out = {}
        for f in glob.glob(res + '/*.xml'):
            for tc in ET.parse(f).getroot().iter('testcase'):
                bad = tc.find('failure') is not None or tc.find('error') is not None
                out[tc.get('classname').rsplit('.', 1)[-1] + '.' + tc.get('name')] = 'FAIL' if bad else 'pass'
        return p.returncode, out, p.stdout.decode()[-1500:]
    else:
        dd = os.path.join(root, '..', 'dd-sk')
        cmd = ['xcodebuild', 'test', '-scheme', 'StyleConverterRuntime', '-destination', 'platform=macOS,variant=Mac Catalyst,arch=arm64', '-derivedDataPath', dd]
        for t in filters: cmd += ['-only-testing:StyleConverterRuntimeTests/' + t]
        p = subprocess.run(cmd, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        log = p.stdout.decode(errors='replace'); out = {}
        for m in re.finditer(r"Test Case '-\[StyleConverterRuntimeTests\.(\w+) (\w+)\]' (passed|failed)", log):
            out[m.group(1) + '.' + m.group(2)] = 'pass' if m.group(3) == 'passed' else 'FAIL'
        for m in re.finditer(r'Test case \'(\w+)\.(\w+)\(\)\' (passed|failed)', log):
            out[m.group(1) + '.' + m.group(2)] = 'pass' if m.group(3) == 'passed' else 'FAIL'
        return p.returncode, out, log[-2500:]
src = orig.decode('utf-8')
assert src.count(old) == 1, f'mutation anchor matches {src.count(old)} times in {rel}'
try:
    open(path, 'w', encoding='utf-8').write(src.replace(old, new))
    rc_red, red, tail_red = run()
finally:
    open(path, 'wb').write(orig)
h1 = sha(open(path, 'rb').read())
assert h0 == h1, 'RESTORE FAILED'
rc_green, green, tail_green = run()
fails = sorted(k for k, v in red.items() if v == 'FAIL')
gfails = sorted(k for k, v in green.items() if v == 'FAIL')
print(json.dumps({'file': rel, 'sha256_before': h0, 'sha256_restored': h1, 'red_exit': rc_red, 'red_ran': len(red),
                  'red_failing': fails, 'green_exit': rc_green, 'green_ran': len(green), 'green_failing': gfails,
                  'red_tail': tail_red if not red else '', 'green_tail': tail_green if not green else ''}, indent=1))
