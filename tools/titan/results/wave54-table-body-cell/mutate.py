#!/usr/bin/env python3
# tools/titan/results/wave54-table-body-cell/mutate.py — EXECUTES the lane's mutations on its OWNED files (PLAN §0 "Pins
# can fail"): for each mutation, sha256 the file, apply ONE exact-substring edit (asserted to match once), run the focused
# JVM suite, read the JUnit XML, require every expected test red, write the original bytes back and re-hash (byte-exact).
# After the batch the suite is re-run on the restored tree and must be green. The seam mutations (S0-S2) live in
# seam1-verify.py (they need the lock). Usage (repo root, JDK 21): python3 mutate.py [ID …]
import glob, hashlib, json, os, subprocess, sys, time
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
RT = os.path.join(ROOT, 'runtimes/compose/build/test-results/testDebugUnitTest')
APP = os.path.join(ROOT, 'apps/android-harness/app/build/test-results/testDebugUnitTest')
OUT = os.path.join(HERE, 'mutations.json')
FOREST = 'runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableBodyForest.kt'
HUG = 'runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableCellHug.kt'
APPLIER = 'runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableApplier.kt'
SCREEN = 'apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt'
# (id, file, old, new, expected-red tests as Class::method, needs the :app suite)
MUTATIONS = [
    # ── the six re-landed 276757ea pins (wave-53 definitions, tools/titan/results/wave53-canvas-root/mutations.json) ──
    ('BS', FOREST, '        return roots.take(bodyIdx + 1) + table + after.filter(::isOutOfFlow)',
     '        return roots.take(bodyIdx) + body.copy(children = listOf(row)) + after.filter(::isOutOfFlow) + listOf(table).take(0)',
     ['TableBodyForestTest::shape_bodyRootUnchanged_tableRowCells_abspos'], False),
    ('BM1', FOREST, '        if (displayOf(body) !in TABLE_BODY || paintsOwnEdges(body)) return roots',
     '        if (paintsOwnEdges(body)) return roots', ['TableBodyForestTest::m1_identity_onNonTableBodies'], False),
    ('BM2', FOREST, '                cells += r.copy(properties = r.properties.filterNot { it.type in MARGIN_TYPES })  // §8.3',
     '                cells += r  // §8.3', ['TableBodyForestTest::m2_cellLosesItsMargins'], False),
    ('BM3', FOREST, '        val run = after.filterNot(::isOutOfFlow)                           // the in-flow run',
     '        val run = after', ['TableBodyForestTest::m3_absposStaysARoot_neverWrapped'], False),
    ('BM4', FOREST, '            } else pending += r', '            } else cells += r',
     ['TableBodyForestTest::m4_nonCellRunIsWrappedInAnAnonymousCell'], False),
    ('BM5', FOREST, '+ body.properties.filter { it.type in TABLE_BOX_TYPES }', '+ body.properties.filter { false }',
     ['TableBodyForestTest::m5_tableCarriesTheBodysBorderSpacing'], False),
    ('BK', SCREEN, '    return com.styleconverter.runtime.table.TableBodyForest.rewrite(owned)', '    return owned',
     ['ComposedCanvasTableBodyTest::stack_zeroGapAboveTheSyntheticTable_tdIsNoRoot'], True),
    # ── wave 54: the marker ──
    ('m6', FOREST, 'children = kids,\n            role = ANONYMOUS_ROLE)', 'children = kids)',
     ['TableBodyForestTest::m6_syntheticBoxesAreAnonymous_authoredBoxesAreNot',
      'TableCellHugTest::anonymousTable_noStroke_cellsHug',
      'TableCellHugTest::anonymousMarker_survivesTheRenderersPreDisplayFolds'], False),
    # ── wave 54: the chrome decision (TableCellHug.chrome) ──
    ('T1', HUG, 'stroke = fabricatedDefault && !anonymous,', 'stroke = fabricatedDefault,',
     ['TableCellHugTest::anonymousTable_noStroke_cellsHug',
      'TableCellHugTest::anonymousMarker_survivesTheRenderersPreDisplayFolds'], False),
    ('T2', HUG, 'hug = anonymous,', 'hug = false,', ['TableCellHugTest::anonymousTable_noStroke_cellsHug',
                                                    'TableCellHugTest::anonymousMarker_survivesTheRenderersPreDisplayFolds'], False),
    ('T3', HUG, 'hug = anonymous,', 'hug = true,',
     ['TableCellHugTest::declaredTableBodyRoot_keepsTodaysStroke_noHug',
      'TableCellHugTest::declaredTable_percentageSizing003_keepsTodaysStroke_noHug',
      'TableCellHugTest::uaTable_baselineEmptyCell001_noStroke_noHug',
      'TableCellHugTest::uaTable_containContent004_isTodaysExpression_noHug'], False),
    ('T4', HUG, 'if (!enabled) this', 'if (false) this', ['TableCellHugTest::hugColumn_disabled_isTheReceiverItself'], False),
    # ── wave 54: TableApplier's half of the wiring ──
    ('T5', APPLIER, '                .hugColumn(TableCellHug.LocalTableCellsHugContent.current)\n', '',
     ['TableCellHugTest::applierSource_tableProvidesTheHug_cellChainsHugBeforeFillMaxHeight'], False),
    ('T6', APPLIER, ',\n            // Per table, so a nested table never inherits its host\'s hug.\n'
                    '            TableCellHug.LocalTableCellsHugContent provides cellsHugContent', '',
     ['TableCellHugTest::applierSource_tableProvidesTheHug_cellChainsHugBeforeFillMaxHeight'], False),
]


def sha(b): return hashlib.sha256(b).hexdigest()


def suite(app):
    # The focused suite PLAN §2 L2 grants this lane.
    env = dict(os.environ, JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21']).decode().strip())
    pats = ['*Table*', '*ComposedCanvas*', '*UaBlockMargins*']
    for d in (RT, APP):
        for f in {g for p in pats for g in glob.glob(os.path.join(d, f'TEST-{p}.xml'))}:
            if os.path.exists(f): os.remove(f)
    cmd = ['./gradlew', '--continue', ':runtime:testDebugUnitTest', '--tests', '*TableBodyForest*', '--tests', '*TableCellHug*',
           '--tests', '*TableBoxTree*']
    if app: cmd += [':app:testDebugUnitTest', '--tests', '*ComposedCanvas*', '--tests', '*UaBlockMargins*']
    p = subprocess.run(cmd, cwd=os.path.join(ROOT, 'apps/android-harness'), env=env, stdout=subprocess.PIPE,
                       stderr=subprocess.STDOUT, text=True)
    total, red = 0, []
    for d in (RT, APP):
        for f in glob.glob(os.path.join(d, 'TEST-*.xml')):
            r = ET.parse(f).getroot(); n = r.get('name').split('.')[-1]
            if not any(k in n for k in ('Table', 'ComposedCanvas', 'UaBlockMargins')): continue
            total += int(r.get('tests'))
            red += [f'{n}::{tc.get("name")}' for tc in r.findall('testcase')
                    if tc.find('failure') is not None or tc.find('error') is not None]
    errs = [l for l in p.stdout.splitlines() if l.startswith('e: ')][:5]
    return p.returncode, total, sorted(red), errs


log = json.load(open(OUT)) if os.path.exists(OUT) else {'runs': []}
want = sys.argv[1:] or [m[0] for m in MUTATIONS]
for mid, rel, old, new, expect, app in MUTATIONS:
    if mid not in want: continue
    path = os.path.join(ROOT, rel); orig = open(path, 'rb').read(); h0 = sha(orig); text = orig.decode()
    assert text.count(old) == 1, f'{mid}: anchor matched {text.count(old)}x in {rel}'
    try:
        open(path, 'w').write(text.replace(old, new))
        rc, total, red, errs = suite(app)
    finally:
        open(path, 'wb').write(orig)                                # byte-exact restore, whatever happened
    h1 = sha(open(path, 'rb').read())
    ok = rc != 0 and all(e in red for e in expect) and h0 == h1 and not errs
    line = (f"{time.strftime('%Y-%m-%dT%H:%M:%S')} {mid} {rel} sha256 {h0[:16]} → mutated → restored {h1[:16]} "
            f"{'byte-exact' if h0 == h1 else 'MISMATCH'} | rc {rc} tests {total} red {len(red)} expected-red-seen "
            f"{all(e in red for e in expect)} {'RED-OK' if ok else 'NOT-PROVEN'}")
    print(line, flush=True); [print('    red:', r, flush=True) for r in red]
    if errs: print('    compile errors:', errs, flush=True)
    log['runs'].append({'id': mid, 'file': rel, 'sha256': h0, 'restoredSha256': h1, 'rc': rc, 'tests': total, 'red': red,
                        'expectedRed': expect, 'proven': ok, 'compileErrors': errs, 'at': time.strftime('%Y-%m-%dT%H:%M:%S')})
    json.dump(log, open(OUT, 'w'), indent=1)
rc, total, red, errs = suite(True)
print(f"GREEN-AFTER-RESTORE rc {rc} tests {total} red {red} compile-errors {errs}", flush=True)
log['green'] = {'rc': rc, 'tests': total, 'red': red, 'compileErrors': errs, 'at': time.strftime('%Y-%m-%dT%H:%M:%S'),
                'sha256': {r: sha(open(os.path.join(ROOT, r), 'rb').read()) for r in (FOREST, HUG, APPLIER, SCREEN)}}
json.dump(log, open(OUT, 'w'), indent=1)
