#!/usr/bin/env python3
"""S1 mutation replayer (runs ONLY in the scratch export, never in the shared tree).
usage: mutate.py <export-root> <label> <file> <old-literal-file> <new-literal-file> <expect: red|green> -- <test cmd...>
Asserts the old literal occurs exactly once, applies the mutation, runs the test command (red = rc != 0), restores
the file from its saved bytes, checks sha256(before) == sha256(after), re-runs the command (green = rc 0)."""
import hashlib, subprocess, sys, os, time
root, label, rel, oldf, newf, expect = sys.argv[1:7]
cmd = sys.argv[sys.argv.index('--') + 1:]
path = os.path.join(root, rel)
raw = open(path, 'rb').read()
sha = lambda b: hashlib.sha256(b).hexdigest()[:12]
old = open(oldf, 'rb').read(); new = open(newf, 'rb').read()
n = raw.count(old)
if n != 1:
    print(f'{label}: ANCHOR COUNT {n} != 1 — not run'); sys.exit(2)
mut = raw.replace(old, new)
def run():
    t = time.time()
    p = subprocess.run(cmd, cwd=root, capture_output=True, text=True)
    tail = '\n'.join((p.stdout + p.stderr).strip().splitlines()[-6:])
    return p.returncode, round(time.time() - t), tail, p.stdout + p.stderr
open(path, 'wb').write(mut)
try:
    rc1, t1, tail1, full1 = run()
finally:
    open(path, 'wb').write(raw)
after = open(path, 'rb').read()
rc2, t2, tail2, full2 = run()
red = rc1 != 0
verdict = ('RED-OK' if red else 'SURVIVED') if expect == 'red' else ('SURVIVED-AS-EXPECTED' if not red else 'RED (expected survive)')
ok_restore = sha(after) == sha(raw)
print(f'{label}: {rel} sha {sha(raw)} -> {sha(mut)} -> {sha(after)} restore={"BYTE-EXACT" if ok_restore else "MISMATCH"} | mutated rc={rc1} ({t1}s) {verdict} | restored rc={rc2} ({t2}s) {"GREEN" if rc2 == 0 else "RED!"}')
log = os.environ.get('MUTLOG')
if log:
    with open(log, 'a') as fh:
        fh.write(f'===== {label}\n--- mutated run tail\n{tail1}\n--- restored run tail\n{tail2}\n')
        import re
        fails = [l for l in full1.splitlines() if re.search(r'not ok |FAILED|✗|failed|× |Failing|AssertionError|XCTAssert|error:', l)][:12]
        fh.write('--- failing lines (mutated)\n' + '\n'.join(fails) + '\n')
