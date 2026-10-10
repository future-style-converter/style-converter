#!/usr/bin/env python3
# Skeptic mutation runner (independent of the lane's mutate.py): exact single-occurrence
# replace in an EXPORT tree file, run the focused vitest, record red/green, restore
# byte-exact (sha256 before == after), re-run to confirm green.
import hashlib, json, re, subprocess, sys
tree, cwd, testargs, muts_file = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
muts = json.load(open(muts_file))
VITEST = tree + '/node_modules/.bin/vitest'
def run():
    p = subprocess.run([VITEST, 'run', '--reporter=verbose'] + testargs.split(), cwd=cwd, capture_output=True, text=True)
    out = p.stdout + p.stderr
    fails = sorted(set(re.findall(r'^\s*[×✗]\s+(.*?)(?:\s+\d+ms)?$', out, re.M)))
    summ = re.findall(r'Tests\s+(.*)', out)
    return p.returncode, (summ[-1].strip() if summ else out[-400:]), fails
sha = lambda b: hashlib.sha256(b).hexdigest()
rc, s, _ = run(); print(f'BASELINE rc={rc} {s}')
for name, m in muts.items():
    path = tree + '/' + m['file']
    orig = open(path, 'rb').read(); h0 = sha(orig)
    txt = orig.decode()
    n = txt.count(m['old'])
    if n != 1: print(f'{name}: SKIP old occurs {n}x'); continue
    open(path, 'w').write(txt.replace(m['old'], m['new']))
    rc1, s1, f1 = run()
    open(path, 'wb').write(orig); h1 = sha(open(path, 'rb').read())
    rc2, s2, _ = run()
    verdict = ('RED' if rc1 != 0 else 'NOT-RED(green!)') + ' -> ' + ('GREEN' if rc2 == 0 else 'STILL-RED')
    print(f'{name}: {verdict} | mutated: {s1} | restored sha256 {h0[:12]}…=={h1[:12]}… {h0 == h1} | restored: {s2}')
    for f in f1[:12]: print(f'     red: {f}')
