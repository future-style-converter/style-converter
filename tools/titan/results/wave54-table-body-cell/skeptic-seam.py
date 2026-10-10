#!/usr/bin/env python3
# skeptic-seam.py — the L2 SKEPTIC's own replay of seam-1.patch under the per-file lock (PLAN §0 / rule 2): mkdir the
# lock, sha256 ComponentRenderer.kt, `git apply` the lane's seam patch (renderer hunk + patch-borne
# TableCellHugSeamWiringTest.kt), run the focused suite GREEN, then mutations of the patched renderer (S0-S2 the lane's,
# S3-S5 the skeptic's), each restored to the PATCHED bytes; finally restore the renderer from `git show HEAD:` byte-exact,
# delete the patch-borne test, re-hash, rmdir the lock (all in finally).
import hashlib, json, os, subprocess, sys, time, importlib.util
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
REN = 'runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt'
WIRE = 'runtimes/compose/src/test/java/com/styleconverter/runtime/table/TableCellHugSeamWiringTest.kt'
LOCK = os.path.join(ROOT, 'tools/titan/runs/wave54-lock/ComponentRenderer.kt')
spec = importlib.util.spec_from_file_location('sm', os.path.join(HERE, 'skeptic-mutate.py'))
def sha(p): return hashlib.sha256(open(p, 'rb').read()).hexdigest()
def suite():
    env = dict(os.environ, JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21']).decode().strip())
    import glob, xml.etree.ElementTree as ET
    RT = os.path.join(ROOT, 'runtimes/compose/build/test-results/testDebugUnitTest'); MINE = ('TableBodyForest', 'TableCellHug', 'TableBoxTree')
    for f in glob.glob(os.path.join(RT, 'TEST-*.xml')):
        if any(k in f for k in MINE): os.remove(f)
    p = subprocess.run(['./gradlew', '--continue', ':runtime:testDebugUnitTest', '--tests', '*TableBodyForest*', '--tests', '*TableCellHug*',
                        '--tests', '*TableBoxTree*'], cwd=os.path.join(ROOT, 'apps/android-harness'), env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    total, red, classes = 0, [], {}
    for f in glob.glob(os.path.join(RT, 'TEST-*.xml')):
        r = ET.parse(f).getroot(); n = r.get('name').split('.')[-1]
        if not any(k in n for k in MINE): continue
        total += int(r.get('tests')); classes[n] = int(r.get('tests'))
        red += [f'{n}::{tc.get("name")}' for tc in r.findall('testcase') if tc.find('failure') is not None or tc.find('error') is not None]
    return p.returncode, total, sorted(red), classes, [l for l in p.stdout.splitlines() if l.startswith('e: ')][:3]
MUT = [  # (id, old, new) on the PATCHED renderer
 ('S1', 'fabricatedCellBorder = chrome.stroke,', 'fabricatedCellBorder = !(LocalWptCaptureMode.current &&\n                        component.properties.none { it.type == "Display" } &&\n                        com.styleconverter.runtime.table.TableBoxTree\n                            .uaRoleOf(component._tag) ==\n                            com.styleconverter.runtime.table.TableBoxTree.Role.TABLE),'),
 ('S2', '                    cellsHugContent = chrome.hug,\n', ''),
 ('S3', 'fabricatedDefault = !(LocalWptCaptureMode.current &&', 'fabricatedDefault = (LocalWptCaptureMode.current &&'),
 ('S4', 'cellsHugContent = chrome.hug,', 'cellsHugContent = chrome.stroke,'),
 ('S5', 'val chrome = com.styleconverter.runtime.table.TableCellHug.chrome(\n                    component,', 'val chrome = com.styleconverter.runtime.table.TableCellHug.chrome(\n                    component.copy(role = null),'),
]
log = {'at': time.strftime('%Y-%m-%dT%H:%M:%S'), 'steps': []}
def rec(**k): log['steps'].append(k); print(json.dumps(k), flush=True)
while True:
    try: os.mkdir(LOCK); break
    except FileExistsError: print('lock held — waiting 60 s', flush=True); time.sleep(60)
ren = os.path.join(ROOT, REN); wire = os.path.join(ROOT, WIRE)
h_before = sha(ren); head = subprocess.check_output(['git', '-C', ROOT, 'show', f'HEAD:{REN}'])
rec(step='lock+sha-before', sha256=h_before, equalsHEAD=h_before == hashlib.sha256(head).hexdigest())
try:
    assert h_before == hashlib.sha256(head).hexdigest(), 'renderer is not at HEAD bytes — someone left it modified'
    assert not os.path.exists(wire)
    subprocess.check_call(['git', '-C', ROOT, 'apply', os.path.join(HERE, 'seam-1.patch')])
    patched = open(ren, 'rb').read(); rec(step='applied', patchedSha256=hashlib.sha256(patched).hexdigest())
    rc, total, red, classes, errs = suite(); rec(step='GREEN-patched', rc=rc, tests=total, red=red, classes=classes, errs=errs)
    for mid, old, new in MUT:
        t = patched.decode(); assert t.count(old) == 1, f'{mid} anchor {t.count(old)}x'
        try:
            open(ren, 'w').write(t.replace(old, new, 1)); rc, total, red, classes, errs = suite()
        finally:
            open(ren, 'wb').write(patched)
        rec(step=mid, rc=rc, tests=total, red=red, errs=errs, restoredToPatched=sha(ren) == hashlib.sha256(patched).hexdigest())
    open(ren, 'wb').write(head)                                             # S0: renderer at HEAD bytes, wiring test present
    rc, total, red, classes, errs = suite(); rec(step='S0', rc=rc, tests=total, red=red, errs=errs)
finally:
    open(ren, 'wb').write(head)
    if os.path.exists(wire): os.remove(wire)
    h_after = sha(ren); rec(step='restored', sha256=h_after, byteExact=h_after == h_before, wireTestRemoved=not os.path.exists(wire))
    os.rmdir(LOCK); rec(step='lock released')
    json.dump(log, open(os.path.join(HERE, 'skeptic-seam.json'), 'w'), indent=1)
