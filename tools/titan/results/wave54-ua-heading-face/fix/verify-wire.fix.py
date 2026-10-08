#!/usr/bin/env python3
# FIX-PASS copy of the skeptic's verify-wire.py (only the Kotlin path changed, Swift dropped) — Skeptic (wave 54 L5): independently re-check that every wire constant embedded in the
# Kotlin UAHeadingFaceWire.kt and the Swift UAHeadingFaceWire enum is byte-identical to the
# per-test IR file its comment names, in wave53-final AND wave54-open. Own parser (regex over
# the source), not the lane's generator.
import hashlib, os, re, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), *(['..'] * 5)))  # fix/ is skeptic/'s sibling: same depth
KT = os.path.join(ROOT, 'runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleTest.kt')
SW = os.path.join(ROOT, 'runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAHeadingFoldGateTests.swift')
bad = 0
def check(lang, rel, body):
    global bad
    for run in ('wave53-final', 'wave54-open'):
        p = os.path.join(ROOT, rel.replace('wave53-final', run))
        raw = open(p, 'rb').read()
        ok = raw == body or raw == body + b'\n'
        print(f'{lang} {run} {os.path.basename(rel)} {"IDENTICAL" if ok else "DIFFERS"} sha256={hashlib.sha256(raw).hexdigest()[:12]} len={len(raw)} const_len={len(body)}')
        bad += 0 if ok else 1
kt = open(KT, encoding='utf-8').read()
for m in re.finditer(r'// (tools/titan/runs/\S+\.json) — sha256 \w+ \(VERBATIM bytes\)\n\s+const val \w+: String = """(.*?)"""\n', kt, re.S):
    check('kt', m.group(1), m.group(2).encode('utf-8'))
sw = ''  # fix pass: the Swift wire is held (held/UAHeadingFoldGateTests.swift.txt), not in the tree
for m in re.finditer(r'/// (tools/titan/runs/\S+\.json) — sha256 \w+ \(VERBATIM bytes\)\n\s+static let \w+ = #"""\n(.*?)\n"""#', sw, re.S):
    check('swift', m.group(1), m.group(2).encode('utf-8'))
print('constants-checked kt=%d swift=%d' % (len(re.findall(r'const val', kt)), len(re.findall(r'static let \w+ = #"""', sw))))
print('RESULT', 'ALL-IDENTICAL' if bad == 0 else f'{bad} DIFFER')
sys.exit(1 if bad else 0)
