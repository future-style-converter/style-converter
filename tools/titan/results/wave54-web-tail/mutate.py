#!/usr/bin/env python3
# tools/titan/results/wave54-web-tail/mutate.py — wave 54 lane L6: one EXECUTED mutation, restored byte-exact.
# Usage: mutate.py <spec.json> <name> -- <test command…>
#   spec[name] = {"file": <repo-relative path>, "old": <exact text, must occur once>, "new": <replacement>}
# Prints: sha256 before → mutated → RED/GREEN of the test run → restore → sha256 after (== before) → GREEN/RED.
# Exit 0 only when the mutated run FAILED, the restored run PASSED and the bytes are back.
import hashlib, json, subprocess, sys, os
ROOT = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
spec_path, name = sys.argv[1], sys.argv[2]
cmd = ' '.join(sys.argv[sys.argv.index('--') + 1:])
m = json.load(open(spec_path))[name]
path = os.path.join(ROOT, m['file'])
sha = lambda b: hashlib.sha256(b).hexdigest()
orig = open(path, 'rb').read()
print(f"[{name}] file {m['file']} sha256 before {sha(orig)}")
text = orig.decode('utf8')
# The mutation must hit exactly one site, or it is not the mechanism we mean.
assert text.count(m['old']) == 1, f"{name}: old text found {text.count(m['old'])} times"
open(path, 'wb').write(text.replace(m['old'], m['new']).encode('utf8'))
print(f"[{name}] mutated sha256 {sha(open(path, 'rb').read())}")
try:
    red = subprocess.run(cmd, shell=True, cwd=ROOT, capture_output=True, text=True)
finally:
    # Restore byte-exact whatever the run did.
    open(path, 'wb').write(orig)
after = open(path, 'rb').read()
print(f"[{name}] mutated run exit {red.returncode} → {'RED' if red.returncode else 'GREEN (mutation NOT caught)'}")
# The failing test names, for the note.
for line in (red.stdout + red.stderr).splitlines():
    if ' FAIL ' in line or line.strip().startswith('×') or 'AssertionError' in line or line.strip().startswith('✗'):
        print('    ' + line.strip()[:200])
print(f"[{name}] restored sha256 {sha(after)} {'== before' if after == orig else '!= before (BUG)'}")
green = subprocess.run(cmd, shell=True, cwd=ROOT, capture_output=True, text=True)
print(f"[{name}] restored run exit {green.returncode} → {'GREEN' if green.returncode == 0 else 'RED (restore did not heal)'}")
sys.exit(0 if (red.returncode != 0 and green.returncode == 0 and after == orig) else 1)
