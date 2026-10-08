#!/usr/bin/env python3
# Skeptic (wave 54 L5) — own mutation driver. mutate.py <name> <platform kt|swift> <file> <old> <new>
# Swaps ONE exact occurrence old->new in <file> (an L5-owned file), runs the focused suite with the
# result XML/xcresult cleared first (so a compile failure can never read as red or green), records
# the red tests, restores the file byte-exact from an in-memory copy (sha256 checked), re-runs green.
import hashlib, glob, os, re, subprocess, sys, xml.etree.ElementTree as ET
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), *(['..'] * 5)))
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'mutations')
os.makedirs(OUT, exist_ok=True)
name, plat, rel, old, new = sys.argv[1:6]
path = os.path.join(ROOT, rel)
orig = open(path, 'rb').read()
sha = lambda b: hashlib.sha256(b).hexdigest()
text = orig.decode('utf-8')
assert text.count(old) == 1, f'old literal occurs {text.count(old)} times'
XML = os.path.join(ROOT, 'runtimes/compose/build/test-results/testDebugUnitTest')
def run(tag):
    log = os.path.join(OUT, f'{name}.{tag}.log')
    if plat == 'kt':
        for f in glob.glob(os.path.join(XML, 'TEST-com.styleconverter.runtime.typography.UA*.xml')): os.remove(f)
        env = dict(os.environ, JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21']).decode().strip())
        rc = subprocess.call(['./gradlew', ':runtime:testDebugUnitTest', '--tests', '*UAElementFontRule*', '--tests', '*UAHeadingFoldGate*', '-q'],
                             cwd=os.path.join(ROOT, 'apps/android-harness'), stdout=open(log, 'w'), stderr=subprocess.STDOUT, env=env)
        lines = [f'rc={rc}']
        files = sorted(glob.glob(os.path.join(XML, 'TEST-com.styleconverter.runtime.typography.UA*.xml')))
        if not files: lines.append('NO RESULT XML (compile failure?) — see ' + os.path.relpath(log, ROOT))
        for f in files:
            r = ET.parse(f).getroot()
            lines.append(f"{r.get('name').split('.')[-1]} tests={r.get('tests')} failures={r.get('failures')} errors={r.get('errors')}")
            for tc in r.iter('testcase'):
                for bad in list(tc.iter('failure')) + list(tc.iter('error')):
                    lines.append(f"  RED {tc.get('name')} :: {((bad.get('message') or '').splitlines() or [''])[0][:150]}")
        return lines
    else:
        rb = os.path.join(OUT, f'{name}.{tag}.xcresult')
        subprocess.call(['rm', '-rf', rb])
        rc = subprocess.call(['xcodebuild', 'test', '-scheme', 'StyleConverterRuntime', '-destination', 'platform=macOS,variant=Mac Catalyst,arch=arm64',
                              '-only-testing:StyleConverterRuntimeTests/UAElementFontRuleTests', '-only-testing:StyleConverterRuntimeTests/UAHeadingFoldGateTests',
                              '-resultBundlePath', rb], cwd=ROOT, stdout=open(log, 'w'), stderr=subprocess.STDOUT)
        subprocess.call(['rm', '-rf', rb])
        txt = open(log, encoding='utf-8', errors='replace').read()
        lines = [f'rc={rc}'] + re.findall(r'Executed \d+ tests?, with \d+ failures?.*', txt)[-1:]
        lines += sorted(set('  RED ' + m for m in re.findall(r"error: -\[StyleConverterRuntimeTests\.(\w+ \w+)\]", txt)))
        if 'BUILD FAILED' in txt or '** TEST FAILED **' not in txt and '** TEST SUCCEEDED **' not in txt:
            lines += [l for l in txt.splitlines() if ': error:' in l][:5]
        return lines
open(path, 'wb').write(text.replace(old, new).encode('utf-8'))
mut = open(path, 'rb').read()
print(f'[{name}] {rel} sha256 before={sha(orig)[:12]} mutated={sha(mut)[:12]}')
try:
    for l in run('red'): print(f'[{name}] mutated: {l}')
finally:
    open(path, 'wb').write(orig)
after = open(path, 'rb').read()
print(f'[{name}] restored sha256={sha(after)[:12]} {"BYTE-EXACT" if after == orig else "MISMATCH"}')
for l in run('green'): print(f'[{name}] restored: {l}')
