#!/usr/bin/env python3
# Wave 55 lane L4 (ua-heading-margin) — generates the VERBATIM wire constants the U1 pins decode.
# Source: tools/titan/runs/<run>/sections/<sec>/per-test-ir/<stem>.json (default run wave54-final,
# whose per-test IR is byte-identical to wave55-open's: wave55-gate/control-open.txt wire 1435/1435),
# embedded as raw strings byte-for-byte minus the one trailing newline; each constant names the
# file's sha256 so a reader can re-verify it. The pattern is wave 54's gen-wire-constants.py.
#
#   gen-wire55.py --splice-kt  <file>   rewrite the Kotlin region between the BEGIN/END markers
#   gen-wire55.py --check-kt   <file>   exit 1 unless that region equals the generator's body
#   gen-wire55.py --splice-swift <file> / --check-swift <file>   the same for the Swift enum
#   gen-wire55.py --root <repo>          read the runs from another checkout (default: this one)
#   gen-wire55.py                        print the sha table
import hashlib, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ARGS = sys.argv[1:]
def opt(name):
    # The value after a flag, or None.
    return ARGS[ARGS.index(name) + 1] if name in ARGS else None
ROOT = opt('--root') or os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
RUN = opt('--run') or 'wave54-final'

# Kotlin: only the document UAElementFontRuleTest's UAHeadingFaceWire does not already carry.
KT_DOCS = [('S_11_1_1B_005', 'CSS2', 'wpt__CSS2__css21-errata__s-11-1-1b-005')]
# Swift: the U1-ios pins' own enum (UAHeadingFaceWire is U2-ios's and lands after U1-ios).
SWIFT_DOCS = [
    ('blockInInline015Print', 'css-break', 'wpt__css-break__block-in-inline-015-print'),
    ('inset001', 'css-text-decor', 'wpt__css-text-decor__text-decoration-inset-001'),
    ('inset005', 'css-text-decor', 'wpt__css-text-decor__text-decoration-inset-005'),
    ('inset006', 'css-text-decor', 'wpt__css-text-decor__text-decoration-inset-006'),
    ('inset011', 'css-text-decor', 'wpt__css-text-decor__text-decoration-inset-011'),
    ('inset014', 'css-text-decor', 'wpt__css-text-decor__text-decoration-inset-014'),
    ('textDecorationColor', 'css-text-decor', 'wpt__css-text-decor__text-decoration-color'),
    ('s11_1_1b_005', 'CSS2', 'wpt__CSS2__css21-errata__s-11-1-1b-005'),
]

def load(sec, stem):
    # The verbatim bytes, their sha256 and the repo-relative path.
    p = os.path.join(ROOT, 'tools/titan/runs', RUN, 'sections', sec, 'per-test-ir', stem + '.json')
    raw = open(p, 'rb').read()
    body = (raw[:-1] if raw.endswith(b'\n') else raw).decode('utf-8')
    # Raw-string safe on both languages (no template, no closing delimiter).
    assert '"""' not in body and '$' not in body and '"#' not in body, stem
    return body, hashlib.sha256(raw).hexdigest(), os.path.relpath(p, ROOT)

def kt_body():
    out = []
    for name, sec, stem in KT_DOCS:
        s, h, rel = load(sec, stem)
        out += [f'    // {rel} — sha256 {h} (VERBATIM bytes)', f'    const val {name}: String = """{s}"""']
    return '\n'.join(out) + '\n'

def swift_body():
    out = []
    for name, sec, stem in SWIFT_DOCS:
        s, h, rel = load(sec, stem)
        # The closing delimiter sits at column 0 so the content line keeps its bytes.
        out += [f'    /// {rel} — sha256 {h} (VERBATIM bytes)', f'    static let {name} = #"""', s, '"""#', '']
    return '\n'.join(out[:-1]) + '\n'

BEGIN, END = '    // ── BEGIN GENERATED WIRE (gen-wire55.py)\n', '    // ── END GENERATED WIRE\n'
def splice_or_check(flag, body):
    path = opt(flag)
    t = open(path, encoding='utf-8').read()
    i, j = t.index(BEGIN) + len(BEGIN), t.index(END)
    if flag.startswith('--splice'):
        open(path, 'w', encoding='utf-8').write(t[:i] + body + t[j:])
        print('spliced', path)
        return 0
    ok = t[i:j] == body
    print(flag, path, 'IDENTICAL-TO-GENERATOR' if ok else 'DIFFERS')
    return 0 if ok else 1

if opt('--splice-kt') or opt('--check-kt'):
    sys.exit(splice_or_check('--splice-kt' if opt('--splice-kt') else '--check-kt', kt_body()))
if opt('--splice-swift') or opt('--check-swift'):
    sys.exit(splice_or_check('--splice-swift' if opt('--splice-swift') else '--check-swift', swift_body()))
for name, sec, stem in KT_DOCS + SWIFT_DOCS:
    s, h, rel = load(sec, stem)
    print(h, rel, name)
