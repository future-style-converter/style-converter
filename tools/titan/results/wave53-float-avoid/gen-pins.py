#!/usr/bin/env python3
# tools/titan/results/wave53-float-avoid/gen-pins.py — writes the L4 pin files with the VERBATIM payloads embedded.
# Why generated: PLAN.md §0 requires verbatim corpus payloads; hand-pasting 50 KB of JSON into two languages is how a
# byte drifts. The templates below hold the test logic; this script only splices in `payloads.py`'s literals and their
# sha1s (each test re-hashes its literal + "\n" and compares). Re-run after any template edit:
#   python3 tools/titan/results/wave53-float-avoid/gen-pins.py
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from payloads import load, ROOT

HERE = os.path.dirname(os.path.abspath(__file__))
TARGETS = [
    # (template beside this script, generated file in the runtime test tree, literal formatter)
    ('FloatAvoidPlanTest.kt.tmpl',
     'runtimes/compose/src/test/java/com/styleconverter/runtime/layout/FloatAvoidPlanTest.kt',
     lambda n, rel, t, h: f'        // {rel} (sha1 {h})\n        const val {n} = """{t}"""\n        const val {n}_SHA1 = "{h}"\n'),
    ('FloatAvoidPlanTests.swift.tmpl',
     'runtimes/swiftui/Tests/StyleConverterRuntimeTests/FloatAvoidPlanTests.swift',
     lambda n, rel, t, h: f'    // {rel} (sha1 {h})\n    static let {n} = ##"""\n{t}\n"""##\n    static let {n}_SHA1 = "{h}"\n'),
]

payloads = load()
for tmpl, out, fmt in TARGETS:
    # Fail loudly on a missing template: a silently skipped twin is a stale pin file.
    src = open(os.path.join(HERE, tmpl)).read()
    block = ''.join(fmt(*p) for p in payloads)
    assert src.count('@@PAYLOADS@@') == 1, tmpl
    open(os.path.join(ROOT, out), 'w').write(src.replace('@@PAYLOADS@@\n', block))
    print('wrote', out)
