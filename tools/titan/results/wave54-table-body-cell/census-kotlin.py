#!/usr/bin/env python3
# tools/titan/results/wave54-table-body-cell/census-kotlin.py — the lane's EXECUTED census: appends the temporary method
# in census-kotlin.method.kt.txt to the lane-owned ComposedCanvasTableBodyTest.kt (before its final `}`), runs it with the
# :app focused suite for each run named on the command line (env L2_CENSUS_RUN), and restores the file byte-exact
# (sha256 before/after printed). Output: census-kotlin.<run>.out.txt. Usage: python3 census-kotlin.py wave53-final wave54-open
import hashlib, os, subprocess, sys
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
T = os.path.join(ROOT, 'apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasTableBodyTest.kt')
orig = open(T, 'rb').read(); h0 = hashlib.sha256(orig).hexdigest()
text = orig.decode(); cut = text.rstrip().rfind('}')
try:
    open(T, 'w').write(text[:cut] + open(os.path.join(HERE, 'census-kotlin.method.kt.txt')).read() + '}\n')
    env = dict(os.environ, JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21']).decode().strip())
    for run in (sys.argv[1:] or ['wave53-final']):
        env['L2_CENSUS_RUN'] = run
        p = subprocess.run(['./gradlew', ':app:testDebugUnitTest', '--tests', '*ComposedCanvasTableBody*', '--rerun'],
                           cwd=os.path.join(ROOT, 'apps/android-harness'), env=env, stdout=subprocess.PIPE,
                           stderr=subprocess.STDOUT, text=True)
        print(run, 'gradle rc', p.returncode); print('\n'.join(l for l in p.stdout.splitlines() if 'FAIL' in l or l.startswith('e: ')))
finally:
    open(T, 'wb').write(orig)
h1 = hashlib.sha256(open(T, 'rb').read()).hexdigest()
print('ComposedCanvasTableBodyTest.kt sha256', h0, '→ restored', h1, 'byte-exact' if h0 == h1 else 'MISMATCH')
