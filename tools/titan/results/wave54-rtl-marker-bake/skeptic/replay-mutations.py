#!/usr/bin/env python3
# skeptic/replay-mutations.py — the L1 SKEPTIC's own mutation replayer (not the lane's mutate.py).
# It NEVER touches the shared tree: it mutates a throwaway EXPORT (git archive of HEAD's tools/titan +
# tools/visual + package.json, with the lane's owned files overlaid and node_modules / tools/wpt /
# fixtures/wpt / wpt-buckets.json symlinked), runs the focused suite there, restores the file from an
# in-memory copy (sha256 verified) and re-runs green.
# Usage: replay-mutations.py <export-dir> <spec.json> [<spec.json> ...]
#   spec = [{"label", "file", "old", "new", "expectRed": [substring of a red test name]}]
# A mutation whose `old` does not match EXACTLY once is reported NOT-APPLIED (no evidence).
# An expectRed of [] means "the skeptic expects NO pin to go red" (a GAP probe): verdict GAP if green.
import hashlib, json, os, re, subprocess, sys
EXP = os.path.abspath(sys.argv[1])
CMD = ['nice', '-n', '19', 'node', '--test', 'tools/titan/bidi-bake.test.mjs']
sha = lambda b: hashlib.sha256(b).hexdigest()
def run():
    # The focused suite, in the export; failing test names and the pass/fail summary.
    p = subprocess.run(CMD, cwd=EXP, capture_output=True, text=True)
    out = p.stdout + p.stderr
    return ' '.join(re.findall(r'^# (?:pass|fail|skipped) \d+', out, re.M)), re.findall(r'^not ok \d+ - (.*)$', out, re.M)
bad = 0
for spec_path in sys.argv[2:]:
    for m in json.load(open(spec_path, encoding='utf-8')):
        path = os.path.join(EXP, m['file'])
        orig = open(path, 'rb').read()
        s = orig.decode('utf-8')
        n = s.count(m['old'])
        if n != 1:
            print(f"NOT-APPLIED ({n} matches) {m['label']}"); bad += 1; continue
        open(path, 'w', encoding='utf-8').write(s.replace(m['old'], m['new']))
        hm = sha(open(path, 'rb').read())
        red_summ, red = run()
        open(path, 'wb').write(orig)                     # byte-exact restore from memory
        hr = sha(open(path, 'rb').read())
        green_summ, green = run()
        exp = m['expectRed']
        if not exp:                                      # a GAP probe: does ANY pin catch it?
            verdict = 'GAP(no pin red)' if not red else 'CAUGHT'
        else:
            verdict = 'OK' if (red and all(any(e in f for f in red) for e in exp) and hr == sha(orig) and not green) else 'FAILED'
            bad += verdict != 'OK'
        print(f"{verdict:16} {m['label']}\n    sha256 {sha(orig)[:12]} -> {hm[:12]} -> {hr[:12]} byte-exact={hr == sha(orig)}"
              f"\n    mutated: {red_summ} red={red}\n    restored: {green_summ}")
sys.exit(1 if bad else 0)
