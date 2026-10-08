#!/usr/bin/env python3
# tools/titan/results/wave54-rtl-marker-bake/mutate.py — run this lane's pin mutations, each red → byte-exact restore →
# green, logged with full sha256s to mutations.log beside this script (appended) and as JSON to <spec>.result.json.
# Usage: mutate.py <spec.json>  where spec = [{"label", "file", "old", "new", "expectRed": [substring of a red test name]}]
# Every `old` must match EXACTLY ONCE (a mutation that did not apply is no evidence). The file is restored from an
# in-memory copy of its bytes and the restore is verified by sha256 before the green run. Exit 1 on any failed
# expectation or restore.
import hashlib, json, os, re, subprocess, sys
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
CMD = ['nice', '-n', '19', 'node', '--test', 'tools/titan/bidi-bake.test.mjs']
sha = lambda b: hashlib.sha256(b).hexdigest()
def run():
    p = subprocess.run(CMD, cwd=ROOT, capture_output=True, text=True)
    out = p.stdout + p.stderr
    fails = re.findall(r'^not ok \d+ - (.*)$', out, re.M)
    summ = ' '.join(re.findall(r'^# (?:pass|fail) \d+', out, re.M))
    return summ, fails
spec_path = sys.argv[1]
spec = json.load(open(spec_path, encoding='utf-8'))
results, bad = [], 0
for m in spec:
    path = os.path.join(ROOT, m['file'])
    orig = open(path, 'rb').read()
    s = orig.decode('utf-8')
    n = s.count(m['old'])
    if n != 1:
        print(f"MUTATION NOT APPLIED ({n} matches): {m['label']}"); bad += 1; continue
    open(path, 'w', encoding='utf-8').write(s.replace(m['old'], m['new']))
    hm = sha(open(path, 'rb').read())
    red_summ, red_fails = run()
    open(path, 'wb').write(orig)
    h1 = sha(open(path, 'rb').read())
    green_summ, green_fails = run()
    ok = (h1 == sha(orig)) and not green_fails and all(any(e in f for f in red_fails) for e in m['expectRed']) and red_fails
    bad += 0 if ok else 1
    r = {'label': m['label'], 'file': m['file'], 'before': sha(orig), 'mutated': hm, 'restored': h1,
         'byteExact': h1 == sha(orig), 'mutatedRun': red_summ, 'red': red_fails, 'restoredRun': green_summ,
         'expectRed': m['expectRed'], 'verdict': 'OK' if ok else 'FAILED'}
    results.append(r)
    with open(os.path.join(HERE, 'mutations.log'), 'a', encoding='utf-8') as log:
        log.write(f"### {m['label']} [{r['verdict']}]\n- file: {m['file']}\n- sha256 before   {r['before']}\n"
                  f"- sha256 mutated  {hm}\n- sha256 restored {h1} {'(byte-exact)' if r['byteExact'] else '(NOT RESTORED)'}\n"
                  f"- mutated run:  {red_summ}\n" + ''.join(f"    red: not ok - {f}\n" for f in red_fails)
                  + f"- restored run: {green_summ}\n\n")
    print(f"{r['verdict']:6} {m['label']}: red {red_summ} {red_fails[:3]} | restored {green_summ} byte-exact={r['byteExact']}")
json.dump(results, open(spec_path.replace('.json', '.result.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
sys.exit(1 if bad else 0)
