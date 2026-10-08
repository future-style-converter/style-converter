# Fix-pass Gradle mutation runner (EXPORT only): mutate one file, run the focused tests, restore byte-exact, re-run.
import sys, subprocess, hashlib, json, os, re, glob
root, task, tests, rel, old, new = sys.argv[1:7]
old = old.encode().decode('unicode_escape'); new = new.encode().decode('unicode_escape')
p = os.path.join(root, rel); src = open(p, encoding='utf8').read()
h = lambda s: hashlib.sha256(s.encode()).hexdigest()[:16]
assert src.count(old) == 1, f'anchor count {src.count(old)} in {rel}'
env = dict(os.environ, JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21'], text=True).strip())
gw = os.path.join(root, 'apps/android-harness')
def run():
    args = ['nice', '-n', '19', './gradlew', '--no-daemon', '-q', task] + [a for t in tests.split(',') for a in ('--tests', t)]
    r = subprocess.run(args, cwd=gw, capture_output=True, text=True, env=env)
    out = r.stdout + r.stderr
    m = re.search(r'(\d+) tests? completed, (\d+) failed', out)
    failed = sorted({os.path.basename(x) for d in ('runtimes/compose/build/test-results/testDebugUnitTest', 'apps/android-harness/app/build/test-results/testDebugUnitTest') for x in glob.glob(os.path.join(root, d, '*.xml')) if '<failure' in open(x).read()})
    return r.returncode, (m.group(0) if m else 'all passed' if r.returncode == 0 else out[-300:]), failed
open(p, 'w', encoding='utf8').write(src.replace(old, new)); mut = h(open(p, encoding='utf8').read())
rc1, s1, f1 = run()
open(p, 'w', encoding='utf8').write(src)
rc2, s2, f2 = run()
print(json.dumps({'file': rel, 'before': h(src), 'mutated': mut, 'restored': h(open(p, encoding='utf8').read()),
                  'mutatedRun': [rc1, s1, f1], 'restoredRun': [rc2, s2]}))
