#!/usr/bin/env python3
"""closing-fixes-mutations.py — the negative controls of the two wave-52 closing-gate fixes.

A pin that cannot fail is not a pin. Each mutation below breaks ONE line of the
fix, runs the focused Compose JVM tests, records which tests go red, and puts
the file back byte-exact (sha-256 checked). Output: closing-fixes-mutations.log
next to this script. Run from anywhere; needs JDK 21.

    python3 tools/titan/results/wave52-gate/closing-fixes-mutations.py
"""
import glob
import hashlib
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
MAIN = os.path.join(ROOT, 'runtimes/compose/src/main/java/com/styleconverter/runtime')
GATE = os.path.join(MAIN, 'lists/ListItemMarkerGate.kt')
CH = os.path.join(MAIN, 'spacing/ChUnitMetrics.kt')
RESOLVE = os.path.join(MAIN, 'spacing/SpacingResolve.kt')
RESULTS = os.path.join(ROOT, 'runtimes/compose/build/test-results/testDebugUnitTest')
TESTS = ['com.styleconverter.runtime.lists.ListItemMarkerGateTest', 'com.styleconverter.runtime.spacing.ChUnitMetricsTest']

# (name, file, exact old text, new text, the tests expected to go red)
MUTATIONS = [
    ('G1 displayTakesMarkerAway: != LIST_ITEM_KEYWORD -> ==', GATE,
     'takenAway = keyword.lowercase().replace(\'-\', \'_\') != LIST_ITEM_KEYWORD',
     'takenAway = keyword.lowercase().replace(\'-\', \'_\') == LIST_ITEM_KEYWORD',
     {'an li the author made display block or flex has no marker',
      'an explicit list-item display keeps the marker in both spellings',
      'the last Display entry decides whether the marker is taken away'}),
    ('G2 displayTakesMarkerAway: any Display entry strips the marker', GATE,
     'takenAway = keyword.lowercase().replace(\'-\', \'_\') != LIST_ITEM_KEYWORD',
     'takenAway = keyword.isNotEmpty()',
     {'an explicit list-item display keeps the marker in both spellings',
      'the last Display entry decides whether the marker is taken away'}),
    ('C1 horizontalAdvance: Paint(ADVANCE_PAINT_FLAGS) -> Paint()', CH,
     'android.graphics.Paint(ADVANCE_PAINT_FLAGS).apply',
     'android.graphics.Paint().apply',
     {'the production probe measures the font advance - no hinting, no ceil'}),
    ('C2 horizontalAdvance: getRunAdvance -> measureText', CH,
     'paint.getRunAdvance("0", 0, 1, 0, 1, false, 1)',
     'paint.measureText("0")',
     {'the production probe measures the font advance - no hinting, no ceil'}),
    ('C3 ADVANCE_PAINT_FLAGS: LINEAR_TEXT_FLAG dropped', CH,
     'android.graphics.Paint.LINEAR_TEXT_FLAG or android.graphics.Paint.SUBPIXEL_TEXT_FLAG\n\n',
     'android.graphics.Paint.SUBPIXEL_TEXT_FLAG\n\n',
     {'the advance probe asks for linear sub-pixel metrics'}),
    ('F1 fitSafePx: the snap replaced by the identity', CH,
     'return kotlin.math.ceil(px * scale - SNAP_NOISE) / scale',
     'return px',
     {'a ch length is rounded up to a whole device pixel and an exact one stays',
      'both ch resolution paths go through the snap'}),
    ('F2 SpacingResolve: the bare ch arm loses the snap', RESOLVE,
     'LengthUnit.CH -> ChUnitMetrics.fitSafePx(v * (ctx.chAdvancePx ?: 0.5f * ctx.fontSizePx))',
     'LengthUnit.CH -> v * (ctx.chAdvancePx ?: 0.5f * ctx.fontSizePx)',
     {'both ch resolution paths go through the snap'}),
    ('F3 SpacingResolve: the calc ch arm loses the snap', RESOLVE,
     '"ch" -> ChUnitMetrics.fitSafePx(v * (ctx.chAdvancePx ?: 0.5f * ctx.fontSizePx))',
     '"ch" -> v * (ctx.chAdvancePx ?: 0.5f * ctx.fontSizePx)',
     {'both ch resolution paths go through the snap'}),
    ('F4 fitSafePx: strictly-above instead of ceil (an exact tie grows a pixel — the wave52-probe2 regression)', CH,
     'return kotlin.math.ceil(px * scale - SNAP_NOISE) / scale',
     'return (kotlin.math.floor(px * scale + SNAP_NOISE) + 1f) / scale',
     {'a ch length is rounded up to a whole device pixel and an exact one stays'}),
]


def sha(path):
    return hashlib.sha256(open(path, 'rb').read()).hexdigest()


def run_tests():
    """Run the two focused classes; return the set of failing test names."""
    for f in glob.glob(os.path.join(RESULTS, 'TEST-*.xml')):
        os.remove(f)   # a stale report must never be read as this run's result
    env = dict(os.environ)
    env['JAVA_HOME'] = subprocess.check_output(['/usr/libexec/java_home', '-v', '21'], text=True).strip()
    args = ['./gradlew', ':runtime:testDebugUnitTest', '-q']
    for t in TESTS:
        args += ['--tests', t]
    subprocess.run(args, cwd=os.path.join(ROOT, 'apps/android-harness'), env=env,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    failed, total = set(), 0
    for f in glob.glob(os.path.join(RESULTS, 'TEST-*.xml')):
        xml = open(f, encoding='utf-8').read()
        for m in re.finditer(r'<testcase name="([^"]+)"[^>]*?(/>|>(.*?)</testcase>)', xml, re.S):
            total += 1
            if m.group(3) and ('<failure' in m.group(3) or '<error' in m.group(3)):
                failed.add(m.group(1).replace('&gt;', '>').replace('&amp;', '&'))
    return failed, total


def main():
    log = []
    base_failed, base_total = run_tests()
    log.append(f'baseline: {base_total} tests, {len(base_failed)} failing {sorted(base_failed)}')
    ok = not base_failed and base_total > 0
    for name, path, old, new, expected in MUTATIONS:
        src = open(path, encoding='utf-8').read()
        before = sha(path)
        if src.count(old) != 1:
            log.append(f'{name}: ANCHOR NOT UNIQUE ({src.count(old)}) — not run')
            ok = False
            continue
        open(path, 'w', encoding='utf-8').write(src.replace(old, new))
        try:
            failed, total = run_tests()
        finally:
            open(path, 'w', encoding='utf-8').write(src)
        restored = sha(path) == before
        verdict = 'RED as expected' if failed == expected else ('RED, different set' if failed else 'SURVIVED')
        log.append(f'{name}: {verdict} — {len(failed)}/{total} failing: {sorted(failed)}; restored byte-exact: {restored}')
        ok = ok and failed == expected and restored
    after_failed, after_total = run_tests()
    log.append(f'after restore: {after_total} tests, {len(after_failed)} failing')
    ok = ok and not after_failed
    log.append('RESULT: every mutation killed by exactly its named pins' if ok else 'RESULT: NOT CLEAN — read the lines above')
    out = '\n'.join(log) + '\n'
    open(os.path.join(HERE, 'closing-fixes-mutations.log'), 'w', encoding='utf-8').write(out)
    sys.stdout.write(out)
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
