#!/usr/bin/env python3
# S1 mutation runner (my own, not a lane's): in an EXPORT tree only — green before, one exact-once
# string mutation, red run, byte-exact restore from the in-memory original (sha256 asserted),
# green after. Usage: smut.py <tree> <label> <relfile> <old> <new> <expect-red-substring> -- <cmd...>
import sys, subprocess, hashlib, json, re, os
tree, label, rel, old, new, expect = sys.argv[1:7]
cmd = sys.argv[sys.argv.index('--') + 1:]
p = os.path.join(tree, rel)
orig = open(p, 'rb').read(); h0 = hashlib.sha256(orig).hexdigest()
def run():
    r = subprocess.run(cmd, cwd=os.environ.get('SMUT_CWD', tree), capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr
def summary(out):
    keep = [l.strip() for l in out.split('\n') if re.search(r'^# (pass|fail)|Tests +\d|tests completed|BUILD (SUCCESSFUL|FAILED)|^not ok|FAILED$| FAILED|✗|×|AssertionError|failed', l.strip())]
    return keep[:14]
rc0, o0 = run()
s = orig.decode('utf8'); n = s.count(old)
assert n == 1, f'{label}: old string found {n} times'
open(p, 'w', encoding='utf8').write(s.replace(old, new)); hm = hashlib.sha256(open(p, 'rb').read()).hexdigest()
try:
    rc1, o1 = run()
finally:
    open(p, 'wb').write(orig)
h1 = hashlib.sha256(open(p, 'rb').read()).hexdigest()
rc2, o2 = run()
red = rc1 != 0 and expect in o1
res = dict(label=label, file=rel, sha_before=h0, sha_mutated=hm, sha_restored=h1, restored_byte_exact=h0 == h1,
           green_before_rc=rc0, red_rc=rc1, expected_pin_in_red=expect in o1, green_after_rc=rc2,
           verdict='RED->GREEN' if (rc0 == 0 and red and h0 == h1 and rc2 == 0) else 'FAILED-CHECK',
           green_before=summary(o0)[-3:], red_lines=summary(o1), green_after=summary(o2)[-3:])
print(json.dumps(res, indent=1, ensure_ascii=False))
