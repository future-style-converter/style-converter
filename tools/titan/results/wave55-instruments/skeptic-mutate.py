#!/usr/bin/env python3
# skeptic mutation bracket: mutate.py <id> <relfile> <old-file> <new-file> -- <test cmd...>
# writes MUTATING before the edit and RESTORED after the byte-exact restore to the lane-dir log; runs the test red/green.
import sys, hashlib, subprocess, os
WT = os.environ['WT']; LOG = os.environ['MUTLOG']; OUT = os.environ['MUTOUT']
mid, rel, oldf, newf = sys.argv[1:5]; cmd = sys.argv[sys.argv.index('--') + 1:]
p = os.path.join(WT, rel); pre = open(p, 'rb').read(); h = hashlib.sha256(pre).hexdigest()
old = open(oldf, 'rb').read(); new = open(newf, 'rb').read()
if old.endswith(b'\n') and not old.endswith(b'\n\n'): old = old[:-1]
if new.endswith(b'\n') and not new.endswith(b'\n\n'): new = new[:-1]
n = pre.count(old)
if n != 1: print(f'{mid}: anchor count {n} != 1 — NOT MUTATED'); sys.exit(9)
with open(LOG, 'a') as L: L.write(f'MUTATING {rel} sha256 {h}   # skeptic {mid}\n')
open(p, 'wb').write(pre.replace(old, new))
r = subprocess.run(cmd, cwd=WT, capture_output=True, text=True)
red_out = r.stdout + r.stderr
open(p, 'wb').write(pre); post = hashlib.sha256(open(p, 'rb').read()).hexdigest()
with open(LOG, 'a') as L: L.write(f'RESTORED {rel} sha256 {post}   # skeptic {mid} ({"== pre" if post == h else "!= PRE"})\n')
g = subprocess.run(cmd, cwd=WT, capture_output=True, text=True)
with open(OUT, 'a') as O:
    O.write(f'=== {mid} {rel}\n--- mutated rc={r.returncode}\n')
    O.write('\n'.join(l for l in red_out.splitlines() if l.startswith(('not ok', '# pass', '# fail', ' FAIL', '×', 'FAIL', '  ✗', 'Tests ', '** TEST', 'Test Case', '    AssertionError', 'error:')) or ' failed' in l)[:6000] + '\n')
    O.write(f'--- restored rc={g.returncode}\n')
    O.write('\n'.join(l for l in (g.stdout + g.stderr).splitlines() if l.startswith(('# pass', '# fail', 'Tests ', '** TEST')) or 'Executed' in l)[:2000] + '\n')
print(f'{mid}: mutated rc={r.returncode} restored rc={g.returncode} post==pre {post == h}')
