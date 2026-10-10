#!/usr/bin/env python3
# Wave 54 L5 RE-VERIFIER — executed mutations in the EXPORT tree only (reverify/export-tree.sh; the shared tree and
# its seam file are never written, so no lock is involved). One exact literal swap per mutation (asserted unique),
# red run → restore from the in-memory copy (sha256 before / mutated / after) → green run, each through jvm-run.sh,
# which clears this suite's result XML first (a compile failure can never read as red or green).
# Usage: mutate-export.py <export-tree> <name> <repo-rel-file> <old> <new>
import hashlib, os, subprocess, sys
tree, name, rel, old, new = sys.argv[1:6]
HERE = os.path.dirname(os.path.abspath(__file__))
sha = lambda b: hashlib.sha256(b).hexdigest()
path = os.path.join(tree, rel); orig = open(path, 'rb').read(); text = orig.decode('utf-8')
assert text.count(old) == 1, f'{name}: old literal occurs {text.count(old)} times in {rel}'
out = open(os.path.join(HERE, f'{name}.out.txt'), 'w')
def say(s): print(s, flush=True); out.write(s + '\n'); out.flush()
def run(tag):
    r = subprocess.run(['bash', os.path.join(HERE, 'jvm-run.sh'), tree, f'{name}.{tag}'], capture_output=True, text=True)
    for line in r.stdout.splitlines(): say(line)
open(path, 'wb').write(text.replace(old, new).encode('utf-8'))
say(f'[{name}] MUTATED {rel}: sha256 {sha(orig)[:16]} -> {sha(open(path, "rb").read())[:16]}  ({old.strip()!r} -> {new.strip()!r})')
try: run('red')
finally: open(path, 'wb').write(orig)
after = open(path, 'rb').read()
say(f'[{name}] restored {rel}: sha256 {sha(after)[:16]} {"BYTE-EXACT" if after == orig else "MISMATCH"}')
run('green')
