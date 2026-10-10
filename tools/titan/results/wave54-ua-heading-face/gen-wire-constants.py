#!/usr/bin/env python3
# Wave 54 lane L5 — generates the VERBATIM wire constants the JVM and Catalyst pins
# decode (the UAHeadingFaceWire object inside UAElementFontRuleTest.kt since the fix pass; the
# held fold-gate pins' UAHeadingFaceWire.kt / Swift enum under held/ were generated the same way).
# Source: tools/titan/runs/<run>/sections/<sec>/per-test-ir/<stem>.json, embedded as raw
# strings byte-for-byte (minus one trailing newline when present); each constant carries
# the file's sha256 so a reader can re-verify it. Usage: gen-wire-constants.py [run] (prints
# the Kotlin body to stdout with --kt, the Swift body with --swift, the sha table otherwise).
# Fix pass: --splice-kt <file> rewrites the lines between "// ── BEGIN GENERATED WIRE" and
# "// ── END GENERATED WIRE" in <file> with the --kt body; --check-kt <file> exits 1 unless the
# region already equals it byte-for-byte (so the embedded wire is provably generated).
import hashlib, os, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
_paths = {sys.argv[i + 1] for i, a in enumerate(sys.argv[:-1]) if a in ('--splice-kt', '--check-kt')}  # option values
run = next((a for a in sys.argv[1:] if not a.startswith('--') and a not in _paths), 'wave53-final')
DOCS = [  # (constant name, section, stem)
    ('BLOCK_IN_INLINE_015_PRINT', 'css-break', 'wpt__css-break__block-in-inline-015-print'),
    ('INSET_001', 'css-text-decor', 'wpt__css-text-decor__text-decoration-inset-001'),
    ('INSET_005', 'css-text-decor', 'wpt__css-text-decor__text-decoration-inset-005'),
    ('INSET_006', 'css-text-decor', 'wpt__css-text-decor__text-decoration-inset-006'),
    ('INSET_011', 'css-text-decor', 'wpt__css-text-decor__text-decoration-inset-011'),
    ('INSET_014', 'css-text-decor', 'wpt__css-text-decor__text-decoration-inset-014'),
    ('TEXT_DECORATION_COLOR', 'css-text-decor', 'wpt__css-text-decor__text-decoration-color'),
]
def load(sec, stem):
    p = os.path.join(ROOT, 'tools/titan/runs', run, 'sections', sec, 'per-test-ir', stem + '.json')
    raw = open(p, 'rb').read()
    body = raw[:-1] if raw.endswith(b'\n') else raw
    s = body.decode('utf-8')
    assert '"""' not in s and '$' not in s and '"#' not in s, stem  # raw-string safe on both languages
    return s, hashlib.sha256(raw).hexdigest(), os.path.relpath(p, ROOT)
def kt_body():
    out = []
    for name, sec, stem in DOCS:
        s, h, rel = load(sec, stem)
        out += [f'    // {rel} — sha256 {h} (VERBATIM bytes)', f'    const val {name}: String = """{s}"""', '']
    return '\n'.join(out[:-1]) + '\n'
BEGIN, END = '    // ── BEGIN GENERATED WIRE\n', '    // ── END GENERATED WIRE\n'
def region(path):
    t = open(path, encoding='utf-8').read()
    i, j = t.index(BEGIN) + len(BEGIN), t.index(END)
    return t, i, j
if '--splice-kt' in sys.argv or '--check-kt' in sys.argv:
    flag = '--splice-kt' if '--splice-kt' in sys.argv else '--check-kt'
    path = sys.argv[sys.argv.index(flag) + 1]
    t, i, j = region(path)
    if flag == '--splice-kt':
        open(path, 'w', encoding='utf-8').write(t[:i] + kt_body() + t[j:])
        print('spliced', len(DOCS), 'constants into', path)
    else:
        ok = t[i:j] == kt_body()
        print('check-kt', path, 'IDENTICAL-TO-GENERATOR' if ok else 'DIFFERS'); sys.exit(0 if ok else 1)
elif '--kt' in sys.argv:
    for name, sec, stem in DOCS:
        s, h, rel = load(sec, stem)
        print(f'    // {rel} — sha256 {h} (VERBATIM bytes)')
        print(f'    const val {name}: String = """{s}"""')
        print()
elif '--swift' in sys.argv:
    for name, sec, stem in DOCS:
        s, h, rel = load(sec, stem)
        camel = name.lower().split('_'); camel = camel[0] + ''.join(w.capitalize() for w in camel[1:])
        print(f'    /// {rel} — sha256 {h} (VERBATIM bytes)')
        print(f'    static let {camel} = #"""')
        print(s)
        print('"""#')  # closing delimiter at column 0: the content line keeps its bytes
        print()
else:
    for name, sec, stem in DOCS:
        s, h, rel = load(sec, stem)
        print(h, rel)
