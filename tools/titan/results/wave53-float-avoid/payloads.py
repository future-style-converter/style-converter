#!/usr/bin/env python3
# tools/titan/results/wave53-float-avoid/payloads.py — the VERBATIM wave52-ship per-test IR the L4 pins embed.
# Why: PLAN.md §0 "Verbatim payloads" — the Kotlin and Swift pin files are GENERATED from these bytes by
# gen-pins.py, never hand-copied, and each test asserts the sha1 of its literal (+ the file's trailing "\n")
# against the corpus file's own sha1, so an edited literal cannot pass as verbatim.
import hashlib, os
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))
RUN = os.path.join(ROOT, 'tools/titan/runs/wave52-ship/sections')
# (constant name, section, per-test-ir file): the 3 carriers, then the 5 P5 refusal documents (PLAN §2 L4 P5).
PAYLOADS = [
    ('P001', 'css-contain', 'wpt__css-contain__contain-inline-size-bfc-floats-001.json'),
    ('P002', 'css-contain', 'wpt__css-contain__contain-inline-size-bfc-floats-002.json'),
    ('PFR2', 'css-display', 'wpt__css-display__display-flow-root-002.json'),
    ('PFR1', 'css-display', 'wpt__css-display__display-flow-root-001.json'),
    ('PNFC', 'CSS2', 'wpt__CSS2__floats-clear__adjoining-float-new-fc.json'),
    ('PBFC3', 'CSS2', 'wpt__CSS2__floats-clear__floats-bfc-003.json'),
    ('PCC1', 'css-contain', 'wpt__css-contain__contain-content-001.json'),
    ('PDU1', 'css-writing-modes', 'wpt__css-writing-modes__direction-upright-001.json'),
]


def load():
    """[(name, relpath, text-without-trailing-newline, sha1-of-file-bytes)] — asserts each file is one JSON line."""
    out = []
    for name, sec, f in PAYLOADS:
        p = os.path.join(RUN, sec, 'per-test-ir', f)
        b = open(p, 'rb').read()
        # One line + trailing newline: the literal is everything before it.
        assert b.endswith(b'\n') and b.count(b'\n') == 1, p
        t = b[:-1].decode('utf-8')
        # Escape-sensitive bytes would change under Kotlin raw / Swift raw strings.
        assert '$' not in t and '\\' not in t and '"""' not in t and '"##' not in t, p
        out.append((name, os.path.relpath(p, ROOT), t, hashlib.sha1(b).hexdigest()))
    return out


if __name__ == '__main__':
    for n, rel, t, h in load():
        print(n, h, len(t), rel)
