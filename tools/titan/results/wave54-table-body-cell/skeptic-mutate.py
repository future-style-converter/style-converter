#!/usr/bin/env python3
# skeptic-mutate.py — the L2 SKEPTIC's own replay of the lane's pin mutations (not the lane's mutate.py), plus extra
# mutations aimed at what the pins might NOT see. For each: sha256 the owned file, apply ONE exact-substring edit
# (asserted unique), run the focused suite, parse the JUnit XML, record which tests went red, restore the original bytes
# (finally-block) and re-hash. A baseline GREEN run precedes the batch and a GREEN run follows it.
# Usage (repo root): python3 skeptic-mutate.py [ID …]   → appends to skeptic-mutations.json, prints one line per id.
import glob, hashlib, json, os, subprocess, sys, time
import xml.etree.ElementTree as ET
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
RT = os.path.join(ROOT, 'runtimes/compose/build/test-results/testDebugUnitTest')
APP = os.path.join(ROOT, 'apps/android-harness/app/build/test-results/testDebugUnitTest')
FOREST = 'runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableBodyForest.kt'
HUG = 'runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableCellHug.kt'
APPL = 'runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableApplier.kt'
SCREEN = 'apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt'
M = [  # (id, file, old, new, app-suite?, what it probes)
 ('BS', FOREST, 'return roots.take(bodyIdx + 1) + table + after.filter(::isOutOfFlow)', 'return roots', False, 'forest never applied'),
 ('BM1', FOREST, 'if (displayOf(body) !in TABLE_BODY || paintsOwnEdges(body)) return roots', 'if (paintsOwnEdges(body)) return roots', False, 'trigger dropped'),
 ('BM2', FOREST, 'r.copy(properties = r.properties.filterNot { it.type in MARGIN_TYPES })', 'r', False, '§8.3 strip skipped'),
 ('BM3', FOREST, 'val run = after.filterNot(::isOutOfFlow)', 'val run = after', False, 'abspos wrapped'),
 ('BM4', FOREST, '} else pending += r', '} else cells += r', False, 'non-cell not wrapped'),
 ('BM5', FOREST, 'body.properties.filter { it.type in TABLE_BOX_TYPES }', 'emptyList()', False, 'BorderSpacing not copied'),
 ('BK', SCREEN, 'return com.styleconverter.runtime.table.TableBodyForest.rewrite(owned)', 'return owned', True, 'harness call site dropped'),
 ('m6', FOREST, 'children = kids,\n            role = ANONYMOUS_ROLE)', 'children = kids)', False, 'marker dropped'),
 ('T1', HUG, 'stroke = fabricatedDefault && !anonymous,', 'stroke = fabricatedDefault,', False, 'D1 off'),
 ('T2', HUG, 'hug = anonymous,', 'hug = false,', False, 'D2 off'),
 ('T3', HUG, 'hug = anonymous,', 'hug = true,', False, 'D2 for every table'),
 ('T4', HUG, 'if (!enabled) this', 'if (false) this', False, 'disabled hug adds a node'),
 ('T5', APPL, '                .hugColumn(TableCellHug.LocalTableCellsHugContent.current)\n', '', False, 'TableCell drops the hug'),
 ('T6', APPL, ',\n            // Per table, so a nested table never inherits its host\'s hug.\n            TableCellHug.LocalTableCellsHugContent provides cellsHugContent', '', False, 'provider dropped'),
 # ── skeptic extras ──
 ('X1', APPL, '                .hugColumn(TableCellHug.LocalTableCellsHugContent.current)\n                .fillMaxHeight()\n',
  '                .fillMaxHeight()\n                .hugColumn(TableCellHug.LocalTableCellsHugContent.current)\n', False, 'hug after fillMaxHeight'),
 ('X2', APPL, 'TableCellHug.LocalTableCellsHugContent provides cellsHugContent', 'TableCellHug.LocalTableCellsHugContent provides true', False, 'every table hugs via the provider'),
 ('X3', HUG, 'this@hugColumn.widthAtMaxIntrinsic(', 'this@hugColumn.widthAtMinIntrinsic(', False, 'hug at MIN-content (wrong §17.5.2.2 quantity)'),
 ('X4', HUG, 'else with(IntrinsicChannel) {', 'else if (true) this.then(Modifier) else with(IntrinsicChannel) {', False, 'enabled hug is a no-op wrapper'),
 ('X5', FOREST, 'fun isAnonymous(c: IRComponent): Boolean = c.role == ANONYMOUS_ROLE', 'fun isAnonymous(c: IRComponent): Boolean = c.role == ANONYMOUS_ROLE || c.role == null', False, 'null-role tables read as anonymous'),
 ('X6', APPL, '                .then(cellModifier)\n                // §17.5.2.2', '                // §17.5.2.2', False, 'weight dropped (vacuous order assert?)'),
]
def sha(b): return hashlib.sha256(b).hexdigest()
def suite(app):
    env = dict(os.environ, JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21']).decode().strip())
    MINE = ('TableBodyForest', 'TableCellHug', 'TableBoxTree', 'ComposedCanvas', 'UaBlockMargins')
    for d in (RT, APP):                                                     # delete ONLY this suite's XML: other lanes share the dir
        for f in glob.glob(os.path.join(d, 'TEST-*.xml')):
            if any(k in f for k in MINE): os.remove(f)
    cmd = ['./gradlew', '--continue', ':runtime:testDebugUnitTest', '--tests', '*TableBodyForest*', '--tests', '*TableCellHug*', '--tests', '*TableBoxTree*']
    if app: cmd += [':app:testDebugUnitTest', '--tests', '*ComposedCanvas*', '--tests', '*UaBlockMargins*']
    t0 = time.time()
    p = subprocess.run(cmd, cwd=os.path.join(ROOT, 'apps/android-harness'), env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    total, red = 0, []
    for d in (RT, APP):
        for f in glob.glob(os.path.join(d, 'TEST-*.xml')):
            r = ET.parse(f).getroot(); n = r.get('name').split('.')[-1]
            if not any(k in n for k in MINE): continue                      # another lane's concurrent XML
            total += int(r.get('tests'))
            red += [f'{n}::{tc.get("name")}' for tc in r.findall('testcase') if tc.find('failure') is not None or tc.find('error') is not None]
    errs = [l for l in p.stdout.splitlines() if l.startswith('e: ')][:5]
    tail = [l for l in p.stdout.splitlines() if 'BUILD' in l or 'What went wrong' in l or 'Timeout' in l or 'lock' in l.lower()][-4:]
    if p.returncode and total == 0: open(os.path.join(HERE, 'skeptic-last-gradle.log'), 'w').write(p.stdout)
    return p.returncode, total, sorted(red), errs, round(time.time() - t0, 1), tail
OUT = os.path.join(HERE, 'skeptic-mutations.json')
log = json.load(open(OUT)) if os.path.exists(OUT) else {'runs': []}
want = sys.argv[1:] or ['GREEN0'] + [m[0] for m in M] + ['GREEN1']
for w in want:
    if w.startswith('GREEN'):
        rc, total, red, errs, secs, tail = suite(True)
        print(f'{time.strftime("%H:%M:%S")} {w} rc {rc} tests {total} red {red} errs {errs} {secs}s {tail}', flush=True)
        log['runs'].append({'id': w, 'rc': rc, 'tests': total, 'red': red, 'errs': errs, 'secs': secs}); json.dump(log, open(OUT, 'w'), indent=1); continue
    mid, rel, old, new, app, what = next(m for m in M if m[0] == w)
    path = os.path.join(ROOT, rel); orig = open(path, 'rb').read(); h0 = sha(orig); text = orig.decode()
    assert text.count(old) == 1, f'{mid}: anchor matched {text.count(old)}x'
    try:
        open(path, 'w').write(text.replace(old, new, 1)); rc, total, red, errs, secs, tail = suite(app)
    finally:
        open(path, 'wb').write(orig)
    h1 = sha(open(path, 'rb').read())
    print(f'{time.strftime("%H:%M:%S")} {mid} [{what}] {rel.split("/")[-1]} {h0[:16]}→{h1[:16]} {"byte-exact" if h0 == h1 else "MISMATCH"} | rc {rc} tests {total} red {len(red)} {secs}s errs {errs}', flush=True)
    for r in red: print('    red:', r, flush=True)
    log['runs'].append({'id': mid, 'what': what, 'file': rel, 'sha256': h0, 'restored': h1, 'rc': rc, 'tests': total, 'red': red, 'errs': errs, 'secs': secs})
    json.dump(log, open(OUT, 'w'), indent=1)
