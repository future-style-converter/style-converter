#!/usr/bin/env python3
# s1fix-mutate.py — the wave-54 S1 FIX pass's executed proof for L2 (defect S5 + skeptic nit 2): each mutation is ONE
# exact-substring edit (asserted unique), the focused suite runs with --rerun (fresh XML, never FROM-CACHE / UP-TO-DATE),
# the red set is read from JUnit XML, and the original bytes are restored in a finally-block and re-hashed (sha256
# before = after). Usage (any cwd): python3 s1fix-mutate.py LABEL ID … — LABEL tags the phase ("before" = the pins as
# landed at 4f3853d7, "after" = the S1-fixed TableCellHugTest); GREEN runs the unmutated tree.
import glob, hashlib, os, subprocess, sys, time
import xml.etree.ElementTree as ET
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
RT = os.path.join(ROOT, 'runtimes/compose/build/test-results/testDebugUnitTest')     # the :runtime unit-test XML dir
HUG = 'runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableCellHug.kt'
APPL = 'runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableApplier.kt'
TEST = 'runtimes/compose/src/test/java/com/styleconverter/runtime/table/TableCellHugTest.kt'
CLASSES = ['com.styleconverter.runtime.table.TableCellHugTest', 'com.styleconverter.runtime.table.TableBodyForestTest']
M = {  # id: (file, old, new, what it probes) — X3/X6 verbatim from the L2 skeptic's skeptic-mutate.py; X7-X9 new
 'X3': (HUG, 'this@hugColumn.widthAtMaxIntrinsic(', 'this@hugColumn.widthAtMinIntrinsic(', 'hug at MIN-content (§17.5.2.2 quantity) — S1 S5'),
 'X6': (APPL, '                .then(cellModifier)\n                // §17.5.2.2', '                // §17.5.2.2', 'TableCell drops the weight link — skeptic nit 2 (indexOf -1 vacuous)'),
 'X8': (HUG, 'what is kept\n        )\n', 'what is kept\n        ).widthAtMinIntrinsic(logTag = "TableCellHug", refusalContext = TableCellHug.CELL_HUG_REFUSAL)\n',
        'max read kept, a min read chained after it — the never-Min clause alone'),
 'X9': (HUG, 'refusalContext = TableCellHug.CELL_HUG_REFUSAL,', 'refusalContext = "",', 'max read untagged — the CELL_HUG_REFUSAL clause alone'),
 'X7': (APPL, '                .hugColumn(TableCellHug.LocalTableCellsHugContent.current)\n                .fillMaxHeight()\n',
        '                .hugColumn(TableCellHug.LocalTableCellsHugContent.current)\n', 'TableCell drops fillMaxHeight — unbounded `cell` finds a later composable\'s'),
}
def sha(p): return hashlib.sha256(open(os.path.join(ROOT, p), 'rb').read()).hexdigest()
def suite():
    env = dict(os.environ, JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21']).decode().strip())
    for c in CLASSES:                                                       # delete ONLY this suite's XML (shared dir)
        for f in glob.glob(os.path.join(RT, f'TEST-{c}.xml')): os.remove(f)
    cmd = ['./gradlew', ':runtime:testDebugUnitTest', '--rerun'] + sum((['--tests', c] for c in CLASSES), [])
    t0 = time.time()
    p = subprocess.run(cmd, cwd=os.path.join(ROOT, 'apps/android-harness'), env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    total, red = 0, []
    for c in CLASSES:
        for f in glob.glob(os.path.join(RT, f'TEST-{c}.xml')):
            r = ET.parse(f).getroot(); n = c.split('.')[-1]; total += int(r.get('tests'))
            for tc in r.findall('testcase'):                                # the failing assertion's own message, first line
                bad = tc.find('failure') if tc.find('failure') is not None else tc.find('error')
                if bad is not None: red.append(f'{n}::{tc.get("name")} — {(bad.get("message") or "").splitlines()[0][:150] if bad.get("message") else bad.get("type")}')
    errs = [l for l in p.stdout.splitlines() if l.startswith('e: ')][:5]   # a compile error is NOT a red pin
    if p.returncode and total == 0: open(os.path.join(HERE, 's1fix-last-gradle.log'), 'w').write(p.stdout)
    return p.returncode, total, sorted(red), errs, round(time.time() - t0, 1)
label, ids = sys.argv[1], sys.argv[2:]
print(f'== {label} · TableCellHugTest.kt sha256 {sha(TEST)[:16]}', flush=True)
for w in ids:
    if w == 'GREEN':
        rc, total, red, errs, secs = suite()
        print(f'{time.strftime("%H:%M:%S")} {label} GREEN rc {rc} tests {total} red {len(red)} {red} errs {errs} {secs}s', flush=True); continue
    rel, old, new, what = M[w]; path = os.path.join(ROOT, rel); orig = open(path, 'rb').read(); h0 = hashlib.sha256(orig).hexdigest()
    text = orig.decode(); assert text.count(old) == 1, f'{w}: anchor matched {text.count(old)}x'
    try:
        open(path, 'w').write(text.replace(old, new, 1)); rc, total, red, errs, secs = suite()
    finally:
        open(path, 'wb').write(orig)                                        # byte-exact restore, whatever happened
    h1 = sha(rel)
    print(f'{time.strftime("%H:%M:%S")} {label} {w} [{what}] {rel.split("/")[-1]} {h0[:16]}→{h1[:16]} '
          f'{"byte-exact" if h0 == h1 else "MISMATCH"} | rc {rc} tests {total} red {len(red)} errs {errs} {secs}s', flush=True)
    for r in red: print('    red:', r, flush=True)
