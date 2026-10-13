#!/usr/bin/env python3
"""Wave 55 L3 SKEPTIC mutation replay (the skeptic's own runner, not the lane's).

skeptic-mutate.py <worktree> <log> [ids...]

For each mutation: append `MUTATING <file> sha256 <pre> [<id>]` to <log> BEFORE
editing; apply one exact text replacement (asserted unique); run the PLAN §2 L3
focused suite (--rerun); read the JUnit XML; restore the saved bytes; append
`RESTORED <file> sha256 <post> [<id>]` (post must equal pre); run the suite
again (green expected). The full state (unit-U1 + seam-1 + unit-U2) is the
starting state; ComponentRenderer.kt's 'seam un-applied' mutation restores
its 611c9f93 bytes from git.
"""
import glob, hashlib, os, subprocess, sys, xml.etree.ElementTree as ET

wt, log = sys.argv[1], sys.argv[2]
only = set(sys.argv[3:])
POS = 'runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/'
GATE, ORIG, SLOT = POS + 'PositionedParentSlotGate.kt', POS + 'PositionedParentRtlOrigin.kt', POS + 'PositionedParentFlowSlot.kt'
CR = 'runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt'
M = [
 ('S-U1a1-drop-composedCapture', GATE, 'hostActive || (composedCapture && !parentRtl)', 'hostActive || (!parentRtl)'),
 ('S-U1a2-drop-not-parentRtl', GATE, 'hostActive || (composedCapture && !parentRtl)', 'hostActive || (composedCapture)'),
 ('S-U1a3-collapse-to-hostActive', GATE, 'hostActive || (composedCapture && !parentRtl)', 'hostActive'),
 ('S-U1cd-seam-unapplied', CR, None, 'GIT_BASE'),
 ('S-U1-drop-rtl-unmounted-breadcrumb', GATE, '                PropertyTracker.markUnhandled(RTL_UNMOUNTED_BREADCRUMB)\n', ''),
 ('S-U2a1-origin-sign', ORIG, 'boundedMaxPx?.let { -it }', 'boundedMaxPx?.let { it }'),
 ('S-U2b1-drop-px-width-rule', ORIG, '        if (!hasPxWidth(parentProperties)) return RtlOriginDecline.PARENT_WIDTH_NOT_PX\n', ''),
 ('S-U2c1-model-drop-origin', ORIG, 'val originX = if (origin) placedXPx(originPx(maxWidthPx)) else 0', 'val originX = 0'),
 ('S-U2c2-placedXPx-mirrored', ORIG, 'internal fun placedXPx(originPx: Int?): Int = originPx ?: 0', 'internal fun placedXPx(originPx: Int?): Int = -(originPx ?: 0)'),
 ('S-U2c3-literal-placeRelative', ORIG, 'placeable.place(x = placedXPx(origin), y = 0)', 'placeable.placeRelative(x = placedXPx(origin), y = 0)'),
 ('S-U2x-mechanism-origin-from-minWidth', ORIG, 'if (constraints.hasBoundedWidth) constraints.maxWidth else null', 'if (constraints.hasBoundedWidth) constraints.minWidth else null'),
 ('S-U2x-mechanism-origin-null', ORIG, 'val origin = originPx(if (constraints.hasBoundedWidth) constraints.maxWidth else null)', 'val origin: Int? = null'),
 ('S-U2d1-drop-gate-rtl-branch', GATE, '        if (rtlOriginEngages(hostActive, composedCapture, parentRtl) &&\n            PositionedParentRtlOrigin.applies(parentRtl, parentProperties, childProperties)\n        ) return SlotMount.MOUNT_RTL_ORIGIN\n', ''),
 ('S-U2e1-default-rtlOrigin-true', SLOT, 'rtlOrigin: Boolean = false', 'rtlOrigin: Boolean = true'),
 ('S-U2-drop-decline-breadcrumb-in-MOUNT', GATE, '            recordRtlOriginDecline(childProperties, parentProperties, hostActive, composedCapture, parentRtl)\n            PositionedParentFlowSlot.mount(childProperties)\n', '            PositionedParentFlowSlot.mount(childProperties)\n'),
]

def sha(p):
    return hashlib.sha256(open(p, 'rb').read()).hexdigest()

def suite(tag):
    res = os.path.join(wt, 'runtimes/compose/build/test-results/testDebugUnitTest')
    for x in glob.glob(res + '/*.xml'):
        os.remove(x)
    env = dict(os.environ)
    env['JAVA_HOME'] = subprocess.check_output(['/usr/libexec/java_home', '-v', '21'], text=True).strip()
    cmd = ['./gradlew', ':runtime:testDebugUnitTest', '--rerun', '--tests', '*PositionedParent*', '--tests', '*SeamReachabilityTest',
           '--tests', '*CanvasRootHoistTest', '--tests', '*StaticPositionSeamWiringTest', '--tests', '*OutOfFlowContainingBlockTest']
    p = subprocess.run(cmd, cwd=os.path.join(wt, 'apps/android-harness'), env=env, capture_output=True, text=True)
    tot = fail = skip = 0; failed = []
    for x in glob.glob(res + '/*.xml'):
        r = ET.parse(x).getroot()
        tot += int(r.get('tests')); skip += int(r.get('skipped'))
        for tc in r.iter('testcase'):
            f = tc.find('failure')
            if f is not None:
                fail += 1
                failed.append('%s > %s :: %s' % (tc.get('classname').split('.')[-1], tc.get('name'), (f.get('message') or '').replace('\n', ' ')[:150]))
    compile_err = [l for l in (p.stdout + p.stderr).splitlines() if l.startswith('e: ')][:3]
    return p.returncode, tot, fail, skip, failed, compile_err

with open(log, 'a') as L:
    for mid, rel, old, new in M:
        if only and mid not in only:
            continue
        path = os.path.join(wt, rel)
        pre = sha(path); data = open(path, 'rb').read()
        L.write('MUTATING %s sha256 %s   [%s]\n' % (rel, pre, mid)); L.flush()
        if new == 'GIT_BASE':
            mutated = subprocess.check_output(['git', '-C', wt, 'show', '611c9f93:' + rel])
        else:
            s = data.decode()
            assert s.count(old) == 1, (mid, s.count(old))
            mutated = s.replace(old, new).encode()
        open(path, 'wb').write(mutated)
        rc, tot, fail, skip, failed, ce = suite(mid)
        open(path, 'wb').write(data)
        post = sha(path)
        L.write('RESTORED %s sha256 %s   [%s]%s\n' % (rel, post, mid, '' if post == pre else '  !!! POST != PRE')); L.flush()
        L.write('  [%s] mutated: gradle exit %d, %d tests, %d failed, %d skipped%s\n' % (mid, rc, tot, fail, skip, (' COMPILE: ' + ' | '.join(ce)) if ce else ''))
        for f in failed:
            L.write('      FAILED %s\n' % f)
        L.flush()
        rc2, tot2, fail2, skip2, failed2, ce2 = suite(mid + '-green')
        L.write('  [%s] restored: gradle exit %d, %d tests, %d failed, %d skipped\n' % (mid, rc2, tot2, fail2, skip2)); L.flush()
