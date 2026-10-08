#!/usr/bin/env python3
# tools/titan/results/wave54-label-chrome/mutations.py — wave 54 lane L7 (label-chrome) U1: EXECUTED mutations of the
# mechanism, each red → restore byte-exact (sha256 logged) → green. Each mutation edits ONE owned file in the working
# tree (tools/visual/label-chrome-check.mjs | label-chrome-tripwire.test.mjs | label-chrome-exempt.json) by an exact,
# single-occurrence string replacement, runs the pin(s) that must turn red, restores the file from the bytes read
# before the edit, asserts the sha256 equals the pre-mutation sha256, and re-runs the pin(s), which must be green.
#   python3 tools/titan/results/wave54-label-chrome/mutations.py [ID ...]     (default: every mutation)
# Pins: SUITE = `node --test tools/visual/label-chrome-tripwire.test.mjs` (today's tree, entries pending);
#       SIM   = sim-post-seed.sh (the same suite on a mirror holding the 18 seed PNGs — the U2-seed tree);
#       DUMP  = verdict-dump.mjs vs verdict-dump.head.txt (R3: verdict objects identical to HEAD's checker).
import hashlib, os, re, subprocess, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))   # repo root
LANE = os.path.join(ROOT, 'tools/titan/results/wave54-label-chrome')                      # this lane's dir
CHECK, TEST, MANI = 'tools/visual/label-chrome-check.mjs', 'tools/visual/label-chrome-tripwire.test.mjs', 'tools/visual/label-chrome-exempt.json'

def sha(rel):                                                                              # sha256 of a repo file
    return hashlib.sha256(open(os.path.join(ROOT, rel), 'rb').read()).hexdigest()

def run(cmd):                                                                              # (exit code, stdout+stderr)
    p = subprocess.run(cmd, cwd=ROOT, shell=True, capture_output=True, text=True)
    return p.returncode, p.stdout + p.stderr

def suite():                                                                               # SUITE pin → (green?, summary)
    rc, out = run(f'node --test {TEST}')
    fails = re.findall(r'^not ok \d+ - (.+)$', out, re.M)                                  # failing test names
    tests = re.search(r'^# tests (\d+)', out, re.M)                                         # total
    return rc == 0, f'rc={rc} tests={tests.group(1) if tests else "?"} fail={len(fails)} {fails[:4]}'

def sim():                                                                                 # SIM pin → (green?, summary)
    rc, out = run(f'bash {LANE}/sim-post-seed.sh')
    fails = re.findall(r'^not ok \d+ - (.+)$', out, re.M)
    reds = [l.strip() for l in out.splitlines() if 'RED' in l and not l.startswith('not ok')][:3]  # first RED verdict lines
    return rc == 0, f'rc={rc} fail={len(fails)} {fails[:4]} {reds}'

def dump():                                                                                # DUMP pin → (green?, summary)
    rc, out = run(f"node --test-name-pattern='^__none__$' {LANE}/verdict-dump.mjs {CHECK}")
    got = [l for l in out.splitlines() if l.startswith(('baseline ', 'seeded '))]
    head = open(os.path.join(LANE, 'verdict-dump.head.txt')).read().splitlines()
    diff = sum(1 for a, b in zip(got, head) if a != b) + abs(len(got) - len(head))          # differing verdict lines
    return diff == 0, f'rc={rc} lines={len(got)} differing-vs-HEAD={diff}'

PINS = {'SUITE': suite, 'SIM': sim, 'DUMP': dump}
# id: (file, old, new, pins that must go red, why)
MUTS = {
  'MUT-1': (CHECK, 'if (wouldBe.length && lit === wouldBe.length) problems.push(', 'if (false) problems.push(', ['SUITE'],
            'clause (iv) deleted: a label drawn on an exempt capture is no longer red (brief m1/m2)'),
  'MUT-2': (CHECK, "const base = checkTriplet(pngs, '');", 'const base = checkTriplet(pngs, componentName);', ['SUITE', 'SIM'],
            'exempt stems keep P = name (no "iff"): the wave-53 red (i) 437/369/293 x3 returns post-seed'),
  'MUT-3': (CHECK, "  return 'leaf';", "  return 'container';", ['SUITE'],
            'nodeKind accepts a childless textless node as a container: a leaf entry verifies (brief m4)'),
  'MUT-4': (CHECK, 'return baselineFiles.some((f) => names.has(', 'return false && baselineFiles.some((f) => names.has(', ['SUITE'],
            'fixtureSeeded always false: a stale entry of a seeded fixture hides as "pending" (brief m5, stale half)'),
  'MUT-5': (TEST, "const exempt = VERIFIED.get(stem)?.status === 'verified';", 'const exempt = false;', ['SIM'],
            'the stem loop ignores the verified manifest (the wave-53 oracle): post-seed 001/003/005 red (i) x3; inert pre-seed'),
  'MUT-6': (MANI, '"why": "text",', '"why": "container",', ['SUITE', 'SIM'],
            'manifest says 005 is a container: verification names the text root and reds the entry'),
  'MUT-7': (CHECK, 'return P.length > 0 && pngs.every((png) => P.every(([x, y]) => isGround(png, x, y)));', 'return false;', ['SUITE'],
            'the (d) hint never fires'),
  'MUT-8': (CHECK, 'const MASK_TOL = 1;', 'const MASK_TOL = 0;', ['DUMP'],
            'the moved (iii) spread tolerance drifts: R3 verdict objects no longer identical to HEAD'),
  'MUT-9': (CHECK, "if (!have.length && fixtureDoc && !fixtureSeeded(fixtureDoc, baselineFiles)) status = 'pending';",
            "if (!have.length && fixtureDoc && !fixtureSeeded(fixtureDoc, baselineFiles)) status = 'verified';", ['SUITE'],
            'an unseeded entry claims "verified": the pending synthetic control goes red'),
}

def main(ids):
    ok = True
    for mid in ids:
        rel, old, new, pins, why = MUTS[mid]
        path = os.path.join(ROOT, rel)
        before_bytes = open(path, 'rb').read(); before = sha(rel)                         # the exact bytes to restore
        text = before_bytes.decode('utf8')
        n = text.count(old)
        if n != 1:                                                                         # the mutation must be unambiguous
            print(f'{mid} SKIPPED: anchor occurs {n} times in {rel}'); ok = False; continue
        open(path, 'w', encoding='utf8').write(text.replace(old, new))                     # apply
        print(f'{mid} [{rel}] {why}\n  mutated sha256 {sha(rel)[:16]} (pre {before[:16]})')
        for p in pins:                                                                     # each named pin must be RED
            green, s = PINS[p]()
            print(f'  {p} mutated: {"GREEN (MUTATION SURVIVED)" if green else "RED"}  {s}'); ok &= not green
        open(path, 'wb').write(before_bytes)                                               # restore byte-exact
        after = sha(rel)
        print(f'  restored sha256 {after} {"== pre" if after == before else "!= pre  RESTORE FAILED"}'); ok &= after == before
        for p in pins:                                                                     # and green again
            green, s = PINS[p]()
            print(f'  {p} restored: {"GREEN" if green else "RED"}  {s}'); ok &= green
    print('MUTATIONS OK — every mutation red, every restore byte-exact and green' if ok else 'MUTATIONS FAILED')
    return 0 if ok else 1

if __name__ == '__main__':
    sys.exit(main(sys.argv[1:] or list(MUTS)))
