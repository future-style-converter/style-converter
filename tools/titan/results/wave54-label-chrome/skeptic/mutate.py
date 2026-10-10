#!/usr/bin/env python3
# skeptic/mutate.py — wave 54 L7 SKEPTIC replay of the lane's pins (written independently of the lane's mutations.py):
# each mutation is ONE exact single-occurrence replacement in an L7-owned working-tree file; the named pins must go
# RED, the file is restored from the bytes read before the edit (sha256 compared), and the pins must be GREEN again.
# Pins: SUITE = node --test tools/visual/label-chrome-tripwire.test.mjs (working tree, entries pending);
#       SEED  = skeptic/mirror.sh u1-seed (HEAD tree + working-tree U1 files + the 18 seed PNGs: the U2-seed tree);
#       R3    = skeptic/verdicts.mjs: HEAD checkTriplet vs working-tree checker on 130 committed stems, "differ 0".
# X* rows are the skeptic's OWN extra mutations (branches the lane did not mutate); "SURVIVED" there is a finding.
#   python3 mutate.py [ID ...]
import hashlib, os, re, subprocess, sys
SK = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.abspath(os.path.join(SK, '../../../../..'))
TMP = os.environ.get('TMPDIR', '/tmp')
CHECK, TEST, MANI = 'tools/visual/label-chrome-check.mjs', 'tools/visual/label-chrome-tripwire.test.mjs', 'tools/visual/label-chrome-exempt.json'
sha = lambda rel: hashlib.sha256(open(os.path.join(ROOT, rel), 'rb').read()).hexdigest()
def run(cmd):
    p = subprocess.run(cmd, cwd=ROOT, shell=True, capture_output=True, text=True); return p.returncode, p.stdout + p.stderr
def suite():
    rc, out = run(f'node --test {TEST}'); f = re.findall(r'^not ok \d+ - (.+)$', out, re.M); t = re.search(r'^# tests (\d+)', out, re.M)
    return rc == 0, f'rc={rc} tests={t.group(1) if t else "?"} fail={len(f)} {f[:4]}'
def seed():
    rc, out = run(f'bash {SK}/mirror.sh u1-seed'); f = re.findall(r'^not ok \d+ - (.+)$', out, re.M)
    t = re.search(r'^# tests (\d+)', out, re.M); return rc == 0, f'rc={rc} tests={t.group(1) if t else "?"} fail={len(f)} {f[:4]}'
def r3():
    d = os.path.join(TMP, 'l7-skeptic-headmod'); run(f'rm -rf {d}'); rc, out = run(f'node {SK}/verdicts.mjs {d}')
    m = re.search(r'committed stems (\d+): identical (\d+), differ (\d+)', out); return bool(m) and m.group(3) == '0', (m.group(0) if m else out[-300:])
PINS = {'SUITE': suite, 'SEED': seed, 'R3': r3}
MUTS = {  # id: (file, old, new, pins)
 'MUT-1': (CHECK, 'if (wouldBe.length && lit === wouldBe.length) problems.push(', 'if (false) problems.push(', ['SUITE']),
 'MUT-2': (CHECK, "const base = checkTriplet(pngs, '');", 'const base = checkTriplet(pngs, componentName);', ['SUITE', 'SEED']),
 'MUT-3': (CHECK, "  return 'leaf';", "  return 'container';", ['SUITE']),
 'MUT-4': (CHECK, 'return baselineFiles.some((f) => names.has(', 'return false && baselineFiles.some((f) => names.has(', ['SUITE']),
 'MUT-5': (TEST, "const exempt = VERIFIED.get(stem)?.status === 'verified';", 'const exempt = false;', ['SEED']),
 'MUT-6': (MANI, '"why": "text",', '"why": "container",', ['SUITE', 'SEED']),
 'MUT-7': (CHECK, 'return P.length > 0 && pngs.every((png) => P.every(([x, y]) => isGround(png, x, y)));', 'return false;', ['SUITE']),
 'MUT-8': (CHECK, 'const MASK_TOL = 1;', 'const MASK_TOL = 0;', ['R3']),
 'MUT-9': (CHECK, "!fixtureSeeded(fixtureDoc, baselineFiles)) status = 'pending';", "!fixtureSeeded(fixtureDoc, baselineFiles)) status = 'verified';", ['SUITE']),
 # skeptic extras
 'X1-first-hit-only': (CHECK, 'for (const node of hits) {', 'for (const node of hits.slice(0, 1)) {', ['SUITE', 'SEED']),
 'X2-drop-base-problems': (CHECK, 'const problems = [...base.problems];', 'const problems = [];', ['SUITE']),
 'X3-empty-text-is-text': (CHECK, "node._text.length > 0) return 'text';", "node._text.length >= 0) return 'text';", ['SUITE']),
 'X4-no-hint-call': (TEST, "if (!exempt && v.problems.length && labelAbsentEverywhere(pngs, name)) console.log(", "if (false) console.log(", ['SUITE', 'SEED']),
 'X5-pending-exempts': (TEST, "VERIFIED.get(stem)?.status === 'verified';", "VERIFIED.get(stem)?.status !== 'red';", ['SUITE', 'SEED']),
 'X6-fixture-elsewhere': (MANI, '"fixture": "fixtures/combinations/all-then-color.json",\n      "why": "text"', '"fixture": "fixtures/properties/global/longtail.json",\n      "why": "text"', ['SUITE', 'SEED']),
 'X7-stale-unconditional-off': (CHECK, "else if (!have.length) problems.push(", "else if (false) problems.push(", ['SUITE']),
 'X8-iv-any-not-every': (CHECK, 'if (wouldBe.length && lit === wouldBe.length) problems.push(', 'if (wouldBe.length && lit > 0) problems.push(', ['SUITE', 'SEED']),
}
def main(ids):
    for mid in ids:
        rel, old, new, pins = MUTS[mid]; path = os.path.join(ROOT, rel); pre = open(path, 'rb').read(); h0 = sha(rel)
        t = pre.decode('utf8'); n = t.count(old)
        if n != 1: print(f'{mid}: ANCHOR occurs {n}x — not run'); continue
        open(path, 'w', encoding='utf8').write(t.replace(old, new))
        try:
            print(f'{mid} [{rel}] pre {h0[:16]} mutated {sha(rel)[:16]}')
            for p in pins: g, s = PINS[p](); print(f'  {p:5} mutated : {"GREEN — MUTATION SURVIVED" if g else "RED"}  {s}')
        finally:
            open(path, 'wb').write(pre)                                         # restore byte-exact, always
        h1 = sha(rel); print(f'  restored sha256 {h1} {"== pre" if h1 == h0 else "!= pre RESTORE FAILED"}')
        for p in pins: g, s = PINS[p](); print(f'  {p:5} restored: {"GREEN" if g else "RED"}  {s}')
if __name__ == '__main__': main(sys.argv[1:] or list(MUTS))
