#!/usr/bin/env python3
# rv2-l6 re-verifier mutation helper (independent of the lane's mutate.py):
#   mut.py <name>   apply ONE exact-substring mutation (must match once) to
#                   BorderSideApplier.kt, run the focused *BorderSide* pins with
#                   --rerun (fresh XML only), run Probe.java against the mutated
#                   bytecode, then restore from an in-memory copy and verify the
#                   sha256 is byte-exact back to the pre-mutation value.
import hashlib, subprocess, sys, os, time
R = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
D = os.path.dirname(os.path.abspath(__file__))
F = R + '/runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt'
EXPECT = '7bcc1fe79280d4b511cb921ce32a416c30b6959cd91b27b2f8fb3898288ca7f1'
MUT = {
    # The R1 regression itself: doubleGeom BOTTOM + END back to the per-line
    # clamp max(inset, extent - inset) (= innerEdgeStrokeCentre(extent, inset)).
    'double-mirror': [
        ('Side.BOTTOM -> Offset(0f, farEdgeBandCentre(box.height, sideWidth, inset)) to\n'
         '            Offset(box.width, farEdgeBandCentre(box.height, sideWidth, inset))',
         'Side.BOTTOM -> Offset(0f, kotlin.math.max(inset, box.height - inset)) to\n'
         '            Offset(box.width, kotlin.math.max(inset, box.height - inset))'),
        ('Side.END -> Offset(farEdgeBandCentre(box.width, sideWidth, inset), 0f) to\n'
         '            Offset(farEdgeBandCentre(box.width, sideWidth, inset), box.height)',
         'Side.END -> Offset(kotlin.math.max(inset, box.width - inset), 0f) to\n'
         '            Offset(kotlin.math.max(inset, box.width - inset), box.height)'),
    ],
    # Extra (not in the builder's set): groove/ridge plan returns (inner, outer)
    # — the shade order would swap on every side, every box.
    'groove-swap': [
        ('return doubleGeom(side, inset = half / 2f, sideWidth = width, box = box) to\n'
         '            doubleGeom(side, inset = half / 2f + half, sideWidth = width, box = box)',
         'return doubleGeom(side, inset = half / 2f + half, sideWidth = width, box = box) to\n'
         '            doubleGeom(side, inset = half / 2f, sideWidth = width, box = box)'),
    ],
}
def sha(b): return hashlib.sha256(b).hexdigest()
name = sys.argv[1]
orig = open(F, 'rb').read()
log = open(f'{D}/mut-{name}.txt', 'w')
def out(s): print(s); log.write(s + '\n'); log.flush()
out(f'{time.strftime("%H:%M:%S")} {name}: pre sha {sha(orig)} expected {EXPECT} match={sha(orig) == EXPECT}')
if sha(orig) != EXPECT: sys.exit('pre-mutation sha mismatch — refusing')
text = orig.decode('utf-8')
for a, b in MUT[name]:
    c = text.count(a)
    if c != 1: sys.exit(f'substring matched {c} times — refusing (nothing written)')
    text = text.replace(a, b)
try:
    open(F, 'wb').write(text.encode('utf-8'))
    out(f'mutated sha {sha(open(F, "rb").read())}')
    r = subprocess.run([f'{D}/run.sh', f'mut-{name}', '--tests', '*BorderSide*'], capture_output=True, text=True)
    out(r.stdout.strip())
    cp = open(f'{D}/cp.txt').read().strip()
    jh = subprocess.run(['/usr/libexec/java_home', '-v', '21'], capture_output=True, text=True).stdout.strip()
    p = subprocess.run([jh + '/bin/java', '-cp', cp, f'{D}/Probe.java'], capture_output=True, text=True)
    out('--- probe on MUTATED bytecode ---\n' + p.stdout.strip() + p.stderr.strip()[:400])
finally:
    open(F, 'wb').write(orig)
    after = sha(open(F, 'rb').read())
    out(f'{time.strftime("%H:%M:%S")} restored sha {after} byte_exact={after == sha(orig) == EXPECT}')
