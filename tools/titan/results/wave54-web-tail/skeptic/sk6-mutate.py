#!/usr/bin/env python3
# Skeptic (wave 54 L6, sk6) mutation replayer. Runs in an EXPORT tree (git archive HEAD + the lane's files /
# seam-1.patch), never in the shared tree. Per mutation: sha256 before -> exact single-occurrence replace ->
# focused vitest (RED expected) -> restore the original bytes -> sha256 after == before -> vitest (GREEN expected).
# Usage: sk6-mutate.py <tree> <cwd-rel-to-tree> <mutations.json> <test files...>
import hashlib, json, re, subprocess, sys
tree, rel, mfile, tests = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:]
cwd = f'{tree}/{rel}'
def vitest():
    p = subprocess.run([f'{tree}/node_modules/.bin/vitest', 'run', '--reporter=verbose', *tests], cwd=cwd, capture_output=True, text=True)
    out = p.stdout + p.stderr
    red = sorted(set(m.strip() for m in re.findall(r'^\s*×\s+(.+?)(?:\s+\d+ms)?$', out, re.M)))
    tot = re.findall(r'^\s*Tests\s+(.+)$', out, re.M)
    return p.returncode, (tot[-1] if tot else out[-300:].replace('\n', ' ')), red
h = lambda b: hashlib.sha256(b).hexdigest()
rc, tot, red = vitest(); print(f'BASELINE rc={rc} {tot}', flush=True)
for name, m in json.load(open(mfile)).items():
    path = f'{tree}/{m["file"]}'; orig = open(path, 'rb').read(); before = h(orig); txt = orig.decode()
    n = txt.count(m['old'])
    if n != 1: print(f'{name}: NOT APPLIED (old string occurs {n}x)'); continue
    open(path, 'w').write(txt.replace(m['old'], m['new']))
    rc1, tot1, red1 = vitest()
    open(path, 'wb').write(orig); after = h(open(path, 'rb').read())
    rc2, tot2, _ = vitest()
    v = ('RED' if rc1 else 'SURVIVED (green under mutation)') + ' -> ' + ('GREEN' if rc2 == 0 else 'STILL RED')
    print(f'{name} [{m.get("what","")}] : {v} | mutated: {tot1} | sha256 {before[:16]} -> {after[:16]} equal={before == after} | restored: {tot2}', flush=True)
    for r in red1[:8]: print(f'     red: {r[:170]}')
