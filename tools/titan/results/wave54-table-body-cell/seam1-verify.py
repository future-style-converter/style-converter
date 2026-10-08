#!/usr/bin/env python3
# tools/titan/results/wave54-table-body-cell/seam1-verify.py — verifies seam-1.patch's pins WITH the patch applied, under
# the per-file lock (build-workflow rule 2): mkdir tools/titan/runs/wave54-lock/ComponentRenderer.kt (the mutex), record
# the seam's sha256, `git apply` the patch, run the focused suite GREEN, then EXECUTE the seam mutations (each red →
# re-apply the patched bytes), restore ComponentRenderer.kt byte-exact from HEAD (`git show HEAD:<path>`), delete the
# patch-borne test file, re-hash, rmdir the lock. Never leaves the seam modified (try/finally).
# Usage (repo root, JDK 21): python3 tools/titan/results/wave54-table-body-cell/seam1-verify.py
import glob, hashlib, json, os, subprocess, sys, time
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
HERE = os.path.dirname(os.path.abspath(__file__))
SEAM = 'runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt'
WIRE = 'runtimes/compose/src/test/java/com/styleconverter/runtime/table/TableCellHugSeamWiringTest.kt'
LOCK = os.path.join(ROOT, 'tools/titan/runs/wave54-lock/ComponentRenderer.kt')
RT = os.path.join(ROOT, 'runtimes/compose/build/test-results/testDebugUnitTest')
APP = os.path.join(ROOT, 'apps/android-harness/app/build/test-results/testDebugUnitTest')
OUT = os.path.join(HERE, 'seam1-verify.json')
INLINE = ("""fabricatedCellBorder = !(LocalWptCaptureMode.current &&
                        component.properties.none { it.type == "Display" } &&
                        com.styleconverter.runtime.table.TableBoxTree
                            .uaRoleOf(component._tag) ==
                            com.styleconverter.runtime.table.TableBoxTree.Role.TABLE),""")
# Seam mutations (applied to the PATCHED bytes): each must turn TableCellHugSeamWiringTest red.
MUTATIONS = {
    # S1 — the stroke argument back to the inline wave-38 expression (the decision computed but not spent).
    'S1': ('fabricatedCellBorder = chrome.stroke,', INLINE),
    # S2 — the hug argument dropped (TableApplier keeps its false default: the anonymous cells fill again).
    'S2': ('                    cellsHugContent = chrome.hug,\n', ''),
}


def sha(p): return hashlib.sha256(open(os.path.join(ROOT, p), 'rb').read()).hexdigest()


def head_bytes(p): return subprocess.check_output(['git', '-C', ROOT, 'show', f'HEAD:{p}'])


def suite(app=False):
    # The focused suite PLAN §2 L2 grants this lane (+ the patch-borne wiring pin, matched by *TableCellHug*).
    env = dict(os.environ, JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21']).decode().strip())
    # Stale result files would read as this run's: drop them (a set — the two globs overlap on ComposedCanvasTableBody).
    for d in (RT, APP):
        for f in set(glob.glob(os.path.join(d, 'TEST-*Table*.xml')) + glob.glob(os.path.join(d, 'TEST-*ComposedCanvas*.xml'))
                     + glob.glob(os.path.join(d, 'TEST-*UaBlockMargins*.xml'))):
            if os.path.exists(f): os.remove(f)
    cmd = ['./gradlew', ':runtime:testDebugUnitTest', '--tests', '*TableBodyForest*', '--tests', '*TableCellHug*',
           '--tests', '*TableBoxTree*']
    if app: cmd += [':app:testDebugUnitTest', '--tests', '*ComposedCanvas*', '--tests', '*UaBlockMargins*']
    p = subprocess.run(cmd + ['--continue'], cwd=os.path.join(ROOT, 'apps/android-harness'), env=env,
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    res = {}
    for d in (RT, APP):
        for f in glob.glob(os.path.join(d, 'TEST-*.xml')):
            r = ET.parse(f).getroot(); n = r.get('name').split('.')[-1]
            if not (('Table' in n) or ('ComposedCanvas' in n) or ('UaBlockMargins' in n)): continue
            red = [tc.get('name') for tc in r.findall('testcase') if tc.find('failure') is not None or tc.find('error') is not None]
            res[n] = {'tests': int(r.get('tests')), 'red': red}
    errs = [l for l in p.stdout.splitlines() if l.startswith('e: ')][:10]
    return p.returncode, res, errs


def show(tag, rc, res, errs):
    tot = sum(v['tests'] for v in res.values()); red = {k: v['red'] for k, v in res.items() if v['red']}
    print(f'{tag}: rc={rc} classes={len(res)} tests={tot} red={red} compile-errors={errs}', flush=True)
    return {'rc': rc, 'tests': tot, 'classes': {k: v['tests'] for k, v in res.items()}, 'red': red, 'compileErrors': errs}


# ── acquire the mutex (mkdir is atomic); wait 60 s and retry while another lane holds it ──
os.makedirs(os.path.dirname(LOCK), exist_ok=True)
while True:
    try:
        os.mkdir(LOCK); break
    except FileExistsError:
        print('lock held, waiting 60 s', flush=True); time.sleep(60)
log = {'seam': SEAM, 'lock': os.path.relpath(LOCK, ROOT), 'runs': []}
try:
    log['sha256Before'] = sha(SEAM)
    assert log['sha256Before'] == hashlib.sha256(head_bytes(SEAM)).hexdigest(), 'seam not at HEAD bytes before apply'
    assert not os.path.exists(os.path.join(ROOT, WIRE)), 'patch-borne test already present'
    subprocess.check_call(['git', '-C', ROOT, 'apply', os.path.join(HERE, 'seam-1.patch')])
    patched = open(os.path.join(ROOT, SEAM), 'rb').read()
    log['sha256Patched'] = hashlib.sha256(patched).hexdigest()
    log['runs'].append(dict(step='GREEN with seam-1 applied', **show('GREEN(seam)', *suite(app=True))))
    for m in (sys.argv[1:] or list(MUTATIONS)):
        old, new = MUTATIONS[m]; text = patched.decode()
        assert text.count(old) == 1, f'{m}: anchor matched {text.count(old)}x'
        open(os.path.join(ROOT, SEAM), 'w').write(text.replace(old, new))
        log['runs'].append(dict(step=m, **show(m, *suite())))
        open(os.path.join(ROOT, SEAM), 'wb').write(patched)
        assert sha(SEAM) == log['sha256Patched']
    # S0 — the renderer at HEAD bytes with the wiring pin present (the seam absent): the pin must be red.
    open(os.path.join(ROOT, SEAM), 'wb').write(head_bytes(SEAM))
    log['runs'].append(dict(step='S0 seam absent (HEAD bytes)', **show('S0', *suite())))
finally:
    # Restore byte-exact from HEAD and drop the patch-borne file, whatever happened above.
    open(os.path.join(ROOT, SEAM), 'wb').write(head_bytes(SEAM))
    if os.path.exists(os.path.join(ROOT, WIRE)): os.remove(os.path.join(ROOT, WIRE))
    log['sha256After'] = sha(SEAM)
    log['restoredByteExact'] = log['sha256After'] == hashlib.sha256(head_bytes(SEAM)).hexdigest()
    os.rmdir(LOCK)
    print('restored', log['sha256After'], 'byte-exact' if log['restoredByteExact'] else 'MISMATCH', '; lock released', flush=True)
    json.dump(log, open(OUT, 'w'), indent=1)
