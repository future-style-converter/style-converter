#!/usr/bin/env python3
# tools/titan/results/wave53-lists-bakes/skeptic/smut.py — the L1 SKEPTIC's own mutation runner (independent of the
# lane's mutate.sh). One mutation: back up the file, sha256, replace OLD by NEW (must occur exactly once), run the
# focused command (red expected), restore from the in-memory backup, sha256 again (must equal), run again (green).
# Usage: smut.py <label> <file> <old-literal-file> <new-literal-file> -- <cmd...>
# Appends to skeptic/smut.log beside this script. Exit 1 when the restore is not byte-exact, 2 when OLD is not unique.
import hashlib, subprocess, sys, os, re, time
here = os.path.dirname(os.path.abspath(__file__))
label, path, oldf, newf = sys.argv[1:5]
cmd = sys.argv[sys.argv.index('--') + 1:]
raw = open(path, 'rb').read()
h = lambda b: hashlib.sha256(b).hexdigest()
old, new = open(oldf, 'rb').read(), open(newf, 'rb').read()
if raw.count(old) != 1:
    print(f'MUTATION NOT APPLIED ({raw.count(old)} matches): {label}', file=sys.stderr); sys.exit(2)
mut = raw.replace(old, new)
def run():
    p = subprocess.run(cmd, capture_output=True, text=True)
    out = p.stdout + p.stderr
    keep = [l for l in out.splitlines() if re.match(r'^(P[12] |SK |# (tests|pass|fail)|not ok|\s+✘|Test Case .* failed|Executed \d+ test|\*\* TEST|BUILD (SUCCESSFUL|FAILED)|.*FAILED$|\d+ tests completed)', l)]
    return p.returncode, keep
try:
    open(path, 'wb').write(mut)
    rc_red, red = run()
finally:
    open(path, 'wb').write(raw)
after = open(path, 'rb').read()
rc_green, green = run()
with open(os.path.join(here, 'smut.log'), 'a') as log:
    log.write(f'### {label}  ({time.strftime("%H:%M:%S")})\n- file: {path}\n- sha256 before   {h(raw)}\n- sha256 mutated  {h(mut)}\n'
              f'- sha256 restored {h(after)} {"(byte-exact)" if after == raw else "(NOT RESTORED)"}\n'
              f'- mutated run rc={rc_red}\n' + ''.join(f'    red: {l}\n' for l in red[:14]) +
              f'- restored run rc={rc_green}\n' + ''.join(f'    green: {l}\n' for l in green[-6:]) + '\n')
print(label, 'red rc', rc_red, '| green rc', rc_green, '| restored', after == raw)
for l in red[:14]: print('  red:', l)
for l in green[-4:]: print('  green:', l)
sys.exit(0 if after == raw else 1)
