#!/usr/bin/env python3
# tools/titan/results/wave53-soft-hyphen/reverify-mutate.py — wave 53 L2 RE-VERIFIER.
# Replays, in a PRIVATE export tree (never the shared tree), the skeptic's five surviving predicate mutations
# (skeptic.md D2/D3: x5, x2, x3 -> post-D7 x3p, x1, x4) against the fix pass's final bytes, and runs the focused
# green suites before and after. Usage: reverify-mutate.py <export-tree root> <out.json>
# The export tree must hold: git archive HEAD (compose runtime + android-harness) + the shared tree's L2 Compose files
# + seam-2 applied to ComponentRenderer.kt. Every mutation is restored byte-exact (sha256 before == restored).
import glob, hashlib, json, os, subprocess, sys, xml.etree.ElementTree as ET

root, out = sys.argv[1], sys.argv[2]
IOM = 'runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InertOutOfFlowMember.kt'
IRF = 'runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InlineRunFold.kt'
# The skeptic's exact mutation texts (x3 re-stated on the D7 line, as the fix pass did — the old line no longer exists).
MUT = [
    ('x5', IOM, '            "Color" -> if (alphaIsZero(prop.data)) transparentInk = true else return false',
                '            "Color" -> transparentInk = true'),
    ('x2', IOM, '        return a <= 0f', '        return true'),
    ('x3p', IOM, '        val a = (srgb["a"] as? JsonPrimitive)?.floatOrNull ?: return false',
                 '        val a = (srgb["a"] as? JsonPrimitive)?.floatOrNull ?: return true'),
    ('x1', IOM, '        if (!member.children.isNullOrEmpty() || !member.runs.isNullOrEmpty() ||\n'
                '            !member.decorations.isNullOrEmpty()\n        ) return false', ''),
    ('x4', IRF, '} else if (tag in TEXT_MEMBER_TAGS && InertOutOfFlowMember.admits(child)) {',
                '} else if (InertOutOfFlowMember.admits(child)) {'),
]
env = dict(os.environ)
env['JAVA_HOME'] = subprocess.check_output(['/usr/libexec/java_home', '-v', '21'], text=True).strip()
sha = lambda p: hashlib.sha256(open(p, 'rb').read()).hexdigest()


def run(filters):
    # Focused JVM suite only (PLAN L2 "May run"), single-use daemon-less Gradle with in-process Kotlin.
    res = os.path.join(root, 'runtimes/compose/build/test-results/testDebugUnitTest')
    for x in glob.glob(res + '/*.xml'):
        os.remove(x)
    cmd = ['./gradlew', '--no-daemon', '-q', '-Pkotlin.compiler.execution.strategy=in-process', ':runtime:testDebugUnitTest']
    for t in filters:
        cmd += ['--tests', t]
    p = subprocess.run(cmd, cwd=os.path.join(root, 'apps/android-harness'), env=env, stdout=subprocess.PIPE,
                       stderr=subprocess.STDOUT, text=True)
    n, bad = 0, []
    for x in glob.glob(res + '/*.xml'):
        r = ET.parse(x).getroot()
        n += int(r.get('tests'))
        bad += [tc.get('classname').rsplit('.', 1)[-1] + '.' + tc.get('name') for tc in r.iter('testcase')
                if tc.find('failure') is not None or tc.find('error') is not None]
    return {'exit': p.returncode, 'total': n, 'failed': bad, 'tail': p.stdout[-600:] if n == 0 else ''}


FOCUS = ['*InlineRunFoldTest', '*RunFoldBreadcrumbSeamTest', '*PreBreakPipelineTest']
rows = {'greenBefore': run(FOCUS), 'mutations': []}
for mid, rel, old, new in MUT:
    p = os.path.join(root, rel)
    before = sha(p)
    s = open(p).read()
    assert s.count(old) == 1, (mid, 'mutation anchor not unique/present')
    open(p, 'w').write(s.replace(old, new))
    mutated = sha(p)
    red = run(['*InlineRunFoldTest'])
    open(p, 'w').write(s)
    restored = sha(p)
    green = run(['*InlineRunFoldTest'])
    rows['mutations'].append({'id': mid, 'file': rel, 'sha256Before': before, 'sha256Mutated': mutated,
                              'sha256Restored': restored, 'red': red, 'green': green,
                              'verdict': 'RED->GREEN' if red['failed'] and not green['failed'] and green['total'] and
                              before == restored else 'NOT-PROVEN'})
    print(mid, rows['mutations'][-1]['verdict'], red['failed'], flush=True)
rows['greenAfter'] = run(FOCUS)
json.dump(rows, open(out, 'w'), indent=1)
print('greenBefore', rows['greenBefore']['total'], rows['greenBefore']['failed'])
print('greenAfter', rows['greenAfter']['total'], rows['greenAfter']['failed'])
