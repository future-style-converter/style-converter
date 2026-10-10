#!/usr/bin/env python3
# Wave 54 lane L5 FIX PASS — one focused JVM run, optionally with seam-1 applied under the per-file lock and optionally
# with ONE exact-literal mutation (of an owned file, or of the PATCHED seam file). The skeptic's driver discipline:
# result XML cleared before every run (a compile failure can never read as red or green); every mutated file restored
# byte-exact from an in-memory copy (sha256 printed); the seam file restored from HEAD and the patch-borne new test
# file deleted before the lock is released, so nothing of the seam is ever left in the shared tree.
#
# Usage: fixrun.py <name> [--seam] [--mutate <repo-rel-file> <old-literal-file> <new-literal-file>] [--no-green]
#   --seam        apply tools/titan/results/wave54-ua-heading-face/seam-1.patch inside the lock
#   --mutate      swap ONE exact occurrence old→new in the file (red run), restore, then a green run (unless --no-green)
import glob, hashlib, os, subprocess, sys, time, xml.etree.ElementTree as ET
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..', '..'))
LANE = os.path.join(ROOT, 'tools/titan/results/wave54-ua-heading-face')
SEAM = 'runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt'
NEWTEST = 'runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleSeamWiringTest.kt'
XML = os.path.join(ROOT, 'runtimes/compose/build/test-results/testDebugUnitTest')
OUT = os.path.join(HERE, 'runs'); os.makedirs(OUT, exist_ok=True)
sha = lambda b: hashlib.sha256(b).hexdigest()
args = sys.argv[1:]; name = args.pop(0)
seam = '--seam' in args; green = '--no-green' not in args
mut = None
if '--mutate' in args:
    i = args.index('--mutate'); mut = (args[i + 1], open(args[i + 2], encoding='utf-8').read(), open(args[i + 3], encoding='utf-8').read())
def say(*a):
    line = ' '.join(str(x) for x in a); print(line, flush=True)
    open(os.path.join(OUT, f'{name}.out.txt'), 'a').write(line + '\n')
open(os.path.join(OUT, f'{name}.out.txt'), 'w').close()
def gradle(tag):
    for f in set(glob.glob(os.path.join(XML, 'TEST-com.styleconverter.runtime.typography.*.xml'))): os.remove(f)
    env = dict(os.environ, JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21']).decode().strip())
    log = os.path.join(OUT, f'{name}.{tag}.log')
    rc = subprocess.call(['./gradlew', ':runtime:testDebugUnitTest', '--tests', '*UAElementFontRule*', '--tests', '*UAHeadingFoldGate*',
                          '--tests', '*InlineRunFoldTest'], cwd=os.path.join(ROOT, 'apps/android-harness'),
                         stdout=open(log, 'w'), stderr=subprocess.STDOUT, env=env)
    say(f'[{name}] {tag}: gradle rc={rc} (log {os.path.relpath(log, ROOT)})')
    files = sorted(glob.glob(os.path.join(XML, 'TEST-com.styleconverter.runtime.typography.*.xml')))
    if not files: say(f'[{name}] {tag}: NO RESULT XML (compile failure?)')
    for f in files:
        r = ET.parse(f).getroot()
        say(f"[{name}] {tag}: {r.get('name').split('.')[-1]} tests={r.get('tests')} failures={r.get('failures')} errors={r.get('errors')} ts={r.get('timestamp')}")
        for tc in r.iter('testcase'):
            for bad in list(tc.iter('failure')) + list(tc.iter('error')):
                say(f"[{name}] {tag}:   RED {tc.get('name')} :: {((bad.get('message') or '').splitlines() or [''])[0][:170]}")
    return rc
lock = os.path.join(ROOT, 'tools/titan/runs/wave54-lock', os.path.basename(SEAM))
head_seam = subprocess.check_output(['git', '-C', ROOT, 'show', f'HEAD:{SEAM}'])
if seam:
    os.makedirs(os.path.dirname(lock), exist_ok=True)
    while True:
        try: os.mkdir(lock); break
        except FileExistsError: say(f'[{name}] lock {lock} held — waiting 60 s'); time.sleep(60)
    say(f'[{name}] lock taken {time.strftime("%H:%M:%S")}; seam sha256 before={sha(open(os.path.join(ROOT, SEAM), "rb").read())} (HEAD {sha(head_seam)})')
try:
    if seam:
        assert not os.path.exists(os.path.join(ROOT, NEWTEST)), 'patch-borne test already present'
        subprocess.check_call(['git', '-C', ROOT, 'apply', os.path.join(LANE, 'seam-1.patch')])
        say(f'[{name}] seam-1 applied: seam sha256={sha(open(os.path.join(ROOT, SEAM), "rb").read())}, +{NEWTEST}')
    if mut:
        path = os.path.join(ROOT, mut[0]); orig = open(path, 'rb').read(); text = orig.decode('utf-8')
        assert text.count(mut[1]) == 1, f'old literal occurs {text.count(mut[1])} times in {mut[0]}'
        open(path, 'wb').write(text.replace(mut[1], mut[2]).encode('utf-8'))
        say(f'[{name}] MUTATED {mut[0]} sha256 {sha(orig)[:16]} → {sha(open(path, "rb").read())[:16]}')
        try: gradle('red')
        finally: open(path, 'wb').write(orig)
        after = open(path, 'rb').read()
        say(f'[{name}] restored {mut[0]} sha256={sha(after)[:16]} {"BYTE-EXACT" if after == orig else "MISMATCH"}')
        if green: gradle('green')
    else:
        gradle('run')
finally:
    if seam:
        open(os.path.join(ROOT, SEAM), 'wb').write(head_seam)
        if os.path.exists(os.path.join(ROOT, NEWTEST)): os.remove(os.path.join(ROOT, NEWTEST))
        after = open(os.path.join(ROOT, SEAM), 'rb').read()
        say(f'[{name}] seam restored sha256={sha(after)} {"BYTE-EXACT-HEAD" if after == head_seam else "MISMATCH"}; patch-borne test removed={not os.path.exists(os.path.join(ROOT, NEWTEST))}')
        os.rmdir(lock); say(f'[{name}] lock released {time.strftime("%H:%M:%S")}')
