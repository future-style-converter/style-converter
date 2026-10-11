#!/usr/bin/env python3
# tools/titan/results/wave55-plan/probes-selfcheck.py — proves every GATING geometry key of the wave-55 expectations can
# FAIL (and, where the brief built one, can PASS on the predicted picture), by executing every registered probe on the
# run of record and on the briefs' own fakes / replays / arms. Writes probes-selfcheck.txt beside this file.
#
# Why: a geometry key that cannot fail is not a gate (wave-54 §0 "Pins can fail"; the wave-54 flex-gap probe printed
# OK on a row rule 5 px short). Each brief executed its fakes once, by hand, in its own output file; this re-runs them
# all on the GENERATED expectations.json (the keys, the classes and the pinned interpreter the gate will use) and
# reduces them to one line per gating key:  today (wave54-final: the defect, on device) | fakes the rule rejects |
# fixed pictures it accepts.  Exit 1 if a gating key never FAILs (today or on a fake), or if a self-test is violated.
# Plan-skeptic round 1 (instrument-teeth M9) added §T, THE STANDING REJECTION SET (probes-teeth.py): per gating key, its own fixed
# picture with one defect of each class — target ink removed, a run ±2 / ±3 px, a segment shortened, a count-preserving
# rearrangement — read through the real geometry-gate.py; exit 1 if any fake PASSes (or a fixed picture FAILs) against its
# pre-registered verdict, or if a gating key has no rejected fake. "Able to fail on the pre-fix picture" is no longer enough.
# Plan-skeptic round 3 (verify M2): §T also holds probes-teeth-r3.py (ADDED ink: a row painted twice, a doubled glyph, stray ink
# in the band), and §T3 runs plan-skeptic-round-3.fakes/sweep.py — the added-ink class over every fixed picture, each faked
# capture judged by the rows that read it; exit 1 on a HOLE. (verify N3) the per-key table's `fixed OK` column counts §T's
# fixed pictures too (it read the §L replays only, and so under-counted the keys whose fixed picture is a §T case).
# Plan-skeptic round 4 (verify M1-M4, S2): §T also holds probes-teeth-r4.py (STRICTLY-additive ink, the verifier's builders), and
# §T4 runs the round-4 verifier's own generator (plan-skeptic-round-4.fakes/fakes4.py — its plan-dir path made relative — and
# zones4.py) over every lane: a gating key that PASSes a fake adding > 2 new-ink px inside its OWN read zone (under its probe's
# own mask) is IN-ZONE, exit 1; a fake adding ≤ 2 px is the NAMED 2-px floor (PLAN §0 / D39), listed with its count. The file is
# written atomically (a temp file beside it, then os.replace): lanes read this directory while it is regenerated.
# Read-only over runs, refs and the replay folders beside this file. Three steps write, never into this tree (§T: fake runs
# and gate JSONs under --scratch):
#   - android-paint-order.geometry.py --fakes RE-WRITES its fake PNGs beside the script it runs. It is therefore run from the
#     THROWAWAY worktree given as --wt (a `git worktree add --detach` of HEAD whose tools/titan/runs and tools/wpt are symlinks
#     to this tree's), and its fresh fakes are compared by DECODED PIXELS with this tree's committed ones (PIL 12.2 re-encodes
#     the bytes differently from the PIL that wrote them: a first run on the shared tree rewrote 12 committed PNGs with
#     identical pixels; they were restored byte-exact — generator.done.md §3);
#   - the C4 check copies L5's ok-all replay PNGs into a fake run under --scratch (never under runs/).
#
# Usage: /opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/probes-selfcheck.py --scratch <dir> --wt <worktree>
#        [--exp <expectations.json>] [--out <file>]   (a mutated copy + an out file under the scratch dir: readers-selfcheck.sh D)
import hashlib, json, os, re, shutil, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
args = sys.argv[1:]
SCRATCH = args[args.index('--scratch') + 1] if '--scratch' in args else None
WT = args[args.index('--wt') + 1] if '--wt' in args else None
assert SCRATCH and os.path.isabs(SCRATCH) and WT and os.path.isabs(WT), 'usage: probes-selfcheck.py --scratch <absolute dir> --wt <throwaway worktree>'
assert os.path.realpath(WT) != os.path.realpath(ROOT), '--wt must be the throwaway worktree, never this tree'
os.makedirs(SCRATCH, exist_ok=True)
EXP_PATH = args[args.index('--exp') + 1] if '--exp' in args else os.path.join(HERE, 'expectations.json')   # a mutated copy proves this check can fail
EXP = json.load(open(EXP_PATH))
PY = EXP['interpreter'].split()
P55, P54, P53 = 'tools/titan/results/wave55-plan', 'tools/titan/results/wave54-plan', 'tools/titan/results/wave53-plan'
norm = lambda s: re.sub(r'\s+', ' ', s.strip())
out = []
def say(*lines):
    for l in lines:
        out.append(l)

def run(argv, root=None):
    """Run one probe command (pinned interpreter, nice 19) from the repo root (or `root`); (exit code, normalized lines)."""
    p = subprocess.run(['nice', '-n', '19'] + PY + argv, cwd=root or ROOT, capture_output=True, text=True)
    return p.returncode, [norm(l) for l in p.stdout.splitlines() if l.strip()]

def verdict(line):
    return 'MISSING' if 'MISSING' in line else 'WRONG' if 'WRONG' in line else 'OK' if re.search(r'(GEOMETRY|LINEBOX|STACK) OK$', line) else '?'

def lines_for(lines, key):
    kt = key.split()
    return [l for l in lines if any(l.split()[i:i + len(kt)] == kt for i in range(0, 3))]

# Every gating key of the generated expectations, with its lane / script; the evidence collected per key.
GATING = {}
for lid, lane in EXP['lanes'].items():
    for pr in lane['geometryProbes']:
        script = os.path.basename(next(t for t in pr['cmd'].split() if t.endswith('.py')))
        for k, u in pr.get('gating', {}).items():
            GATING[(script, k)] = {'lane': lid, 'unit': u, 'today': None, 'fakesWrong': [], 'fixedOk': [], 'fixedWrong': []}

def record(script, key, label, v, kind):
    """kind: 'fake' (a wrong picture: the rule must print WRONG) or 'fixed' (the predicted picture: OK expected)."""
    g = GATING.get((script, key))
    if g is None:
        return
    if kind == 'fake' and v == 'WRONG':
        g['fakesWrong'].append(label)
    elif kind == 'fixed':
        (g['fixedOk'] if v == 'OK' else g['fixedWrong']).append(label)

def block(title, argv, mapping=None, kind=None, script=None, keep=None, root=None):
    """Run argv, print its lines (key + verdict, reasons cut at 160 chars), and record every line `mapping` sends to a
    gating key. mapping(line) -> key or None; kind 'fake' / 'fixed' / callable(line) -> kind."""
    rc, lines = run(argv, root)
    say(f'### {title}', f'$ {"(cd <wt> && ) " if root else ""}{" ".join(PY)} {" ".join(argv)}   (exit {rc})')
    for l in lines:
        if keep and not keep(l):
            continue
        say('  ' + (l if len(l) <= 230 else l[:230] + ' …'))
        if mapping:
            k = mapping(l)
            if k:
                kd = kind(l) if callable(kind) else kind
                record(script, k, title, verdict(l), kd)
    say('')
    return rc, lines

say('# probes-selfcheck.txt — every wave-55 geometry probe executed on the run of record and on the briefs\' fakes / replays / arms',
    f'# generated by {P55}/probes-selfcheck.py from {P55}/expectations.json (sha256 '
    f'{hashlib.sha256(open(EXP_PATH, "rb").read()).hexdigest()[:16]}…); interpreter `{EXP["interpreter"]}`',
    f'# HEAD {subprocess.run(["git", "-C", ROOT, "rev-parse", "--short", "HEAD"], capture_output=True, text=True).stdout.strip()}; '
    'read-only over tools/titan/runs/{wave54-final,wave54-probe,wave54-open}, the frozen refs and the replay folders beside the briefs', '')

# ── §0 the gate's own self-test over every lane on wave54-final (gating FAIL / control PASS / report FAIL) ──────────
gj = os.path.join(SCRATCH, 'geometry-selftest.wave54-final.json')
p = subprocess.run(['nice', '-n', '19'] + PY + [f'{P55}/geometry-gate.py', 'wave54-final', '--base', 'wave54-final', '--self-test', '--json', gj, '--exp', EXP_PATH],
                   cwd=ROOT, capture_output=True, text=True)
tail = [l for l in p.stdout.splitlines() if l.startswith(('geometry-gate', '  by class', 'self-test', 'SELF-TEST'))]
say('## §0 geometry-gate.py wave54-final --base wave54-final --self-test (all lanes, every registered probe)', f'exit {p.returncode}', *tail)
G = json.load(open(gj))
for lid in EXP['lanes']:
    rows = [r for r in G['rows'] if r['lane'] == lid]
    c = lambda cls, v: sum(1 for r in rows if r['class'] == cls and r['verdict'] == v)
    say(f'  {lid:28} gating {c("gating", "FAIL")}/{sum(1 for r in rows if r["class"] == "gating")} FAIL · control {c("control", "PASS")}/'
        f'{sum(1 for r in rows if r["class"] == "control")} PASS · report {c("report", "FAIL")}/{sum(1 for r in rows if r["class"] == "report")} FAIL')
for r in G['rows']:
    if r['class'] == 'gating':
        GATING[(r['script'], r['key'])]['today'] = r['verdict']
selftest_ok = p.returncode == 0
say('')

# ── §L1 hyphen-ios-linebox ─────────────────────────────────────────────────────────────────────────────────────────
LB = f'{P55}/hyphen-ios-linebox.geometry.py'
say('## §L1 hyphen-ios-linebox.geometry.py — fakes built from the REF\'s own pixels, and the replay arms')
fk = lambda l: (re.match(r'(hyphen-ios-linebox 00[134]) fake:', l) or [None, None])[1]
block('L1 --fakes (white, whole+1, u3b-today, drop-u3b, no-u3b, g4+2)', [LB, '--fakes'],
      mapping=lambda l: f'{fk(l)} ios' if fk(l) else None, kind='fake', script='hyphen-ios-linebox.geometry.py')
ios_row = lambda l: (lambda m: f'{m[1]} ios' if m else None)(re.match(r'(hyphen-ios-linebox 00[1234]) ios ', l))
for arm, kind, what in (('Rm', 'fixed', 'the unit\'s model'), ('Rw', 'fixed', 'model variant'), ('R0', 'fixed', 'every line on the ref (the U3b-exact ceiling)'),
                        ('R1', 'fake', '§9 R1 risk: every line one row low (the NB-ios-b trigger)'), ('Rd', 'fake', 'NB-ios with U3b reverted underneath'),
                        ('X', 'fake', 'the method check = today\'s picture')):
    block(f'L1 --replay {arm} ({what})', [LB, '--replay', arm], mapping=ios_row, kind=kind, script='hyphen-ios-linebox.geometry.py',
          keep=lambda l: ' ios ' in l or ' ref ' in l)
block('L1 --replay U (radius replay: the 10 STACK report keys)', [LB, '--replay', 'U'], keep=lambda l: 'stack' in l)

# ── §L2 cbb-android ────────────────────────────────────────────────────────────────────────────────────────────────
say('## §L2 compose-wpt-content-box-cb.geometry.py (the wave-55 copy: + the green mass, round 1 M1) and cbb-android.geometry.py (supplementary)')
CBG, CB2 = f'{P55}/compose-wpt-content-box-cb.geometry.py', f'{P55}/cbb-android.geometry.py'
and_key = lambda l: (lambda t: f'{t[0]} android' if len(t) > 1 and t[1] == 'android' else None)(l.split())
block('L2 re-used probe on wave54-probe (= HEAD + the old U1 on device: U1\'s picture is the fixed one; clip-3 is the U1-only fake)',
      [CBG, 'wave54-probe'], mapping=and_key, script='compose-wpt-content-box-cb.geometry.py',
      kind=lambda l: 'fake' if 'clip-3' in l else 'fixed', keep=lambda l: ' android ' in l)
RPL = f'{P55}/cbb-android-replay'
block('L2 supplementary probe on wave54-final + the U2 replay and the fakes F3 / F4 / F6 (F4 and F6 PASS the re-used rule)',
      [CB2, 'wave54-final', '--png', f'replay-U1U2={RPL}/android-clip-3.png', f'F3-centered={RPL}/fake-centered.png',
       f'F4-unclipped={RPL}/fake-unclipped.png', f'F6-double={RPL}/fake-double.png'],
      mapping=lambda l: 'backdrop-filter-nested-border-radius-clip-3 android' if re.search(r'clip-3 (replay-U1U2|F\d-)', l) else None,
      kind=lambda l: 'fixed' if 'replay-U1U2' in l else 'fake', script='cbb-android.geometry.py')
block('L2 supplementary probe on wave54-probe (U1 only)', [CB2, 'wave54-probe'],
      mapping=lambda l: 'backdrop-filter-nested-border-radius-clip-3 android' if ' android ' in l else None, kind='fake', script='cbb-android.geometry.py')

# ── §L3 positioned-parent-slot ────────────────────────────────────────────────────────────────────────────────────
say('## §L3 positioned-parent-slot.geometry.py — the replays (android captures replaced) and the controls-can-fail pair')
PPS = f'{P55}/positioned-parent-slot.geometry.py'
l3_gating = {k for (s, k) in GATING if s == 'positioned-parent-slot.geometry.py'}
l3_map = lambda l: next((k for k in l3_gating if l.split()[:len(k.split())] == k.split()), None)
U_OF = {k: GATING[('positioned-parent-slot.geometry.py', k)]['unit'] for k in l3_gating}
for arm in ('U1U2', 'U1', 'U2', 'PLAIN'):
    # A key is a FIX under an arm that contains its unit (U1U2 holds both), a FAKE under an arm that lacks it; PLAIN is a fake for all.
    kindf = (lambda a: lambda l: 'fixed' if a != 'PLAIN' and U_OF.get(l3_map(l), '?') in (('U1', 'U2') if a == 'U1U2' else (a,)) else 'fake')(arm)
    block(f'L3 --replay {arm}', [PPS, 'wave54-final', 'wave54-final', '--replay', arm], mapping=l3_map, kind=kindf,
          script='positioned-parent-slot.geometry.py', keep=lambda l: ' android ' in l and (l.startswith('[S]') or l.startswith('[W]')))
block('L3 controls can fail: wave54-open vs base wave54-final (wave-54 hunk P moved two of the identity controls)',
      [PPS, 'wave54-open', 'wave54-final'], keep=lambda l: l.startswith('[C]') and 'WRONG' in l)

# ── §L4 ua-heading-margin ─────────────────────────────────────────────────────────────────────────────────────────
say('## §L4 ua-heading-margin.geometry.py — the eight arms (native captures replaced by the replay / fake pictures)')
UHM = f'{P55}/ua-heading-margin.geometry.py'
l4_gating = {k for (s, k) in GATING if s == 'ua-heading-margin.geometry.py'}
l4_map = lambda l: next((k for k in l4_gating if l.split()[:len(k.split())] == k.split()), None)
L4_FIX = {'U1': {'[M]'}, 'C': {'[M]', '[F]'}, 'D': {'[M]', '[F]'}}       # what each arm models as fixed (D: Compose 005 / 006 do not wrap)
for arm in ('T', 'U1', 'R', 'X2', 'B', 'C', 'D', 'C4'):
    kindf = (lambda a: lambda l: 'fixed' if l[:3] in L4_FIX.get(a, set()) and not (a == 'D' and l.startswith('[F]') and ('005 android' in l or '006 android' in l)) else 'fake')(arm)
    block(f'L4 --arm {arm}', [UHM, 'wave54-final', '--arm', arm], mapping=l4_map, kind=kindf, script='ua-heading-margin.geometry.py',
          keep=lambda l: (l.startswith('[M]') or l.startswith('[F]')) and (' ios ' in l or ' android ' in l))

# ── §L5 counter-suffix-rows ───────────────────────────────────────────────────────────────────────────────────────
say('## §L5 counter-suffix-rows.geometry.py — replay:<tag> (ok-all = every unit; each fake turns exactly its own key WRONG)')
CSR = f'{P55}/counter-suffix-rows.geometry.py'
l5_gating = {k for (s, k) in GATING if s == 'counter-suffix-rows.geometry.py'}
l5_map = lambda l: next((k for k in l5_gating if l.split()[:len(k.split())] == k.split()), None)
for tag in ('ok-all', 'H-broken', 'H-nodot', 'H-flush', 'H-low', 'C-shift8', 'C-nocomma', 'C-space-only', 'I-wholeline', 'K-leak'):
    block(f'L5 replay:{tag}', [CSR, f'replay:{tag}', 'wave54-final'], mapping=l5_map, kind='fixed' if tag == 'ok-all' else 'fake',
          script='counter-suffix-rows.geometry.py', keep=lambda l: l.split()[2:3] != ['ref'])   # the ref row by its platform token (a WRONG reason says "vs ref")

# ── §L6 android-paint-order ───────────────────────────────────────────────────────────────────────────────────────
say('## §L6 android-paint-order.geometry.py --fakes / --replays')
APO = f'{P55}/android-paint-order.geometry.py'
fdir, wfdir = os.path.join(HERE, 'android-paint-order-fakes'), os.path.join(WT, P55, 'android-paint-order-fakes')
sha = lambda d: {f: hashlib.sha256(open(os.path.join(d, f), 'rb').read()).hexdigest() for f in sorted(os.listdir(d))} if os.path.isdir(d) else {}
before = sha(fdir)
def l6_fake_key(l):
    if l.startswith('[PO] fake'):
        return '[PO] backdrop-filter-clip-rect android'
    if l.startswith('[X] fake'):
        return '[X] grid-abspos-staticpos-align-self-001 android'      # the [X] fakes are built on grid align-self-001's pixels (ref fill 1056)
    return None
block('L6 --fakes in the throwaway worktree (the rule-family fakes; [FO] is a control: its blind spot prints OK by design)', [APO, '--fakes'],
      mapping=l6_fake_key, kind='fake', script='android-paint-order.geometry.py', root=WT)
from PIL import Image, ImageChops
_px = lambda a, b: ImageChops.difference(Image.open(a).convert('RGBA'), Image.open(b).convert('RGBA')).getbbox() is None
_same = [f for f in sorted(os.listdir(fdir)) if os.path.exists(os.path.join(wfdir, f)) and _px(os.path.join(fdir, f), os.path.join(wfdir, f))]
say(f'  the worktree\'s fresh fakes vs this tree\'s committed android-paint-order-fakes/: {len(_same)}/{len(os.listdir(fdir))} pixel-identical; '
    f'this tree\'s fakes untouched by this run: {sha(fdir) == before}', '')
def l6_replay_key(l):
    m = re.match(r'\[PO\] replay (\S+) U1', l) or re.match(r'\[X\] replay (\S+) U2', l)
    return (f'[PO] {m[1]} android' if l.startswith('[PO]') else f'[X] {m[1]} android') if m else None
block('L6 --replays (U1 / U2 modelled pictures)', [APO, '--replays'], mapping=l6_replay_key, kind='fixed', script='android-paint-order.geometry.py')

# ── §L7 the flex-gap probe copy (controls only: the ability to fail is the fake table) ──────────────────────────────
say('## §L7 flex-zero-gap-rules.geometry.py (wave-55 copy) on the recorded and new flex-gap fakes (fake run ids resolve under tools/titan/runs/)')
FZ = f'{P55}/flex-zero-gap-rules.geometry.py'
for f in ('../results/wave54-plan/verify-r3/gap/fake-blue-r1-short5', '../results/wave54-plan/verify-r3/gap/fake-blue-r2-lshort4',
          '../results/wave55-plan/instruments-flex-gap-fakes/fake-blue-r1-lshort2', '../results/wave55-plan/instruments-flex-gap-fakes/fake-blue-r1-split1',
          '../results/wave55-plan/instruments-flex-gap-fakes/fake-blue-r1-moved5', '../results/wave55-plan/instruments-flex-gap-fakes/fake-blue-r2-hshift2',
          '../results/wave55-plan/instruments-flex-gap-fakes/fake-blue-none', '../results/wave55-plan/instruments-flex-gap-fakes/fake-blue-r1-short1',
          'wave54-open'):
    rc, lines = run([FZ, f])
    say(f'  {f.split("/")[-1]:22} exit {rc}  ' + ' | '.join(f'{l.split()[1]} {verdict(l)}' + (f' ({l.split("WRONG (", 1)[1][:70]}' if 'WRONG (' in l else '') for l in lines if l.startswith('flex/')))
say('')

# ── §C4 the restated wave-54 keys on L5's predicted picture (executed, not derived) ────────────────────────────────
say('## §C4 rtl-marker-bake / lists-bakes counter-suffix keys on L5\'s ok-all replay pixels (a fake run under the scratch dir)')
fake = os.path.join(SCRATCH, 'c4-okall')
for plat, d in (('web', 'screenshots'), ('ios', 'ios-screenshots'), ('android', 'android-screenshots')):
    os.makedirs(os.path.join(fake, 'sections', 'css-counter-styles', d), exist_ok=True)
    shutil.copyfile(os.path.join(HERE, 'counter-suffix-rows-replay', 'ok-all', f'counter-suffix.{plat}.png'),
                    os.path.join(fake, 'sections', 'css-counter-styles', d, 'wpt__css-counter-styles__counter-suffix.png'))
rel = os.path.relpath(fake, os.path.join(ROOT, 'tools', 'titan', 'runs'))
for title, script in (('rtl-marker-bake [M]', f'{P54}/rtl-marker-bake.geometry.py'), ('lists-bakes', f'{P53}/lists-bakes.geometry.py')):
    rc, lines = run([script, rel, 'wave54-final'])
    say(f'  {title} on ok-all (base wave54-final), exit {rc}:')
    for l in lines:
        if 'counter-suffix' in l and ' ref ' not in l:
            say('    ' + l[:230])
say('  → the restated keys (C4): web keeps `rows 0-207 identical to <base>`; ios / android drop it (L5 moves rows 3-6 / 5-6 by design)', '')

# ── §T the standing rejection set (round 1, M9): plausible wrong pictures built from each key's OWN fixed picture ──────
say('## §T the standing rejection set (probes-teeth.py): every gating key on its own fixed picture + one defect per fake class, through the real gate')
import importlib.util
_spec = importlib.util.spec_from_file_location('probes_teeth', os.path.join(HERE, 'probes-teeth.py'))
teeth = importlib.util.module_from_spec(_spec); _spec.loader.exec_module(teeth)
T_ONLY = args[args.index('--teeth-lanes') + 1].split(',') if '--teeth-lanes' in args else None    # a narrowed mutation proof (readers-selfcheck D2)
t_fixed = {}
t_viol, t_cov, t_gating, t_n = teeth.run_all(os.path.join(SCRATCH, 'teeth'), EXP_PATH, PY, say, T_ONLY, t_fixed)
for (script, key), nrej in t_cov.items():
    if (script, key) in GATING:
        GATING[(script, key)]['fakesWrong'].append(f'§T ×{nrej}')
for (script, key), nok in t_fixed.items():                  # round 3 (verify N3): §T's fixed pictures count as accepted fixes too
    if (script, key) in GATING:
        GATING[(script, key)]['fixedOk'].append(f'§T fixed ×{nok}')
t_uncov = sorted(t_gating - set(t_cov))
say(f'  §T: {t_n} cases; violations {len(t_viol)}; gating + tally keys with ≥ 1 rejected plausible fake {len(t_cov)} / {len(t_gating)}')
for v in t_viol:
    say(f'  TEETH VIOLATION: {v}')
for s_, k_ in t_uncov:
    say(f'  NO PLAUSIBLE FAKE REJECTED: {s_} {k_}')
say('')
teeth_ok = not t_viol and not t_uncov

# ── §T3 the round-3 ADDED-INK sweep (plan-skeptic-round-3.verify.md M2): every added_ink.py variant on every fixed picture ──
say('## §T3 the added-ink sweep (plan-skeptic-round-3.fakes/sweep.py): every fixed picture of the standing set painted again — '
    'the whole picture / its target region ±2-3 px, a glyph doubled, a 2×2 / 3×3 blob of the target\'s ink in its band, and the L3 '
    'row / band / cell variants — through the real gate; each FAKED capture must be rejected by a gating / control / tally row '
    'that reads it (a HOLE otherwise)')
_spec_sw = importlib.util.spec_from_file_location('r3_sweep', os.path.join(HERE, 'plan-skeptic-round-3.fakes', 'sweep.py'))
sweep = importlib.util.module_from_spec(_spec_sw); _spec_sw.loader.exec_module(sweep)
s_holes, s_acc, s_reads = sweep.run(os.path.join(SCRATCH, 'sweep'), EXP_PATH, T_ONLY, say)
say(f'  §T3: {s_reads} key reads on faked captures; HOLES {len(s_holes)}; named residuals {len(sweep.residual)}; key reads that PASS a fake a sibling row of the same capture rejects: {len(s_acc)}')
for h in s_holes:
    say(f'  ADDED-INK HOLE: {h[0]} {h[1]} {h[2]} — {h[3]}')
for r in sweep.residual:                                    # pre-registered in sweep.py RESIDUALS, each with its reason
    say(f'  NAMED RESIDUAL: {r[0]} {r[1]} {r[2]} — {r[3]}: {sweep.RESIDUALS[r]}')
say('')
teeth_ok = teeth_ok and not s_holes

# ── §T4 the round-4 STRICTLY-ADDITIVE sweep (plan-skeptic-round-4.verify.md §2): the verifier's generator over every lane ─────
say('## §T4 the round-4 strictly-additive sweep (plan-skeptic-round-4.fakes/fakes4.py + zones4.py, the verifier\'s own generator and '
    'zone adjudication): every fixed picture of the standing set with class ink copied ±1 / ±2 / +4 / +9 y and ±1 … +10 x, a glyph '
    'doubled / smeared / below itself, 1-px … 3×3 strays and whole-picture min() ghosts — written only where the destination is not '
    'already ink — through the real gate; a gating key that PASSes > 2 new-ink px inside its own zone is IN-ZONE (exit 1)')
import re as _re
F4 = os.path.join(HERE, 'plan-skeptic-round-4.fakes')
t4_in, t4_floor, t4_big = 0, 0, 0
for L in (T_ONLY or ['L1', 'L2', 'L3', 'L4', 'L5', 'L6']):
    sc4, fo4, zo4 = os.path.join(SCRATCH, 'sweep4', L), os.path.join(SCRATCH, f'fakes4.{L}.out.txt'), os.path.join(SCRATCH, f'zones4.{L}.out.txt')
    shutil.rmtree(sc4, ignore_errors=True)
    subprocess.run(['nice', '-n', '19'] + PY + [os.path.join(F4, 'fakes4.py'), '--scratch', sc4, '--groups', L, '--out', fo4], cwd=ROOT, capture_output=True, text=True)
    zt = subprocess.run(['nice', '-n', '19'] + PY + [os.path.join(F4, 'zones4.py'), sc4, fo4], cwd=ROOT, capture_output=True, text=True).stdout
    open(zo4, 'w').write(zt)
    summ = next((l for l in open(fo4) if 'fake runs;' in l), '(no summary: the generator failed)').strip() if os.path.exists(fo4) else '(no output: the generator failed)'
    nin = int((_re.search(r'## IN-ZONE: (\d+)', zt) or [0, -1])[1]); nben = int((_re.search(r'## benign: (\d+)', zt) or [0, 0])[1])
    big = int((_re.search(r'HOLES \d+ \((\d+) with > 2', summ) or [0, 0])[1])
    t4_in += max(nin, 0) + (1 if nin < 0 else 0); t4_floor += nben; t4_big += big
    say(f'  {L}: {summ}', f'      zones4: IN-ZONE {nin if nin >= 0 else "(no adjudication)"} · benign (≤ 2 px in the key\'s own zone, or the ink in a sibling\'s zone) {nben}')
    for l in zt.split('## benign')[0].splitlines()[1:]:
        if l.strip():
            say(f'  IN-ZONE ACCEPTANCE: {l.strip()}')
say(f'  §T4: IN-ZONE acceptances {t4_in}; fakes4 HOLES with > 2 new-ink px (any mask) {t4_big}; benign / floor acceptances {t4_floor} — the '
    f'2-px floor is a NAMED residual class (PLAN §0, D39): a fake adding ≤ 2 px under the probe\'s own mask passes every ±2 / XOR ≤ 2 '
    f'read by construction; the passing pictures read 0, so closing it is a 0-px tolerance, a stage-1 device question', '')
teeth_ok = teeth_ok and t4_in == 0 and t4_big == 0

# ── the per-gating-key table ──────────────────────────────────────────────────────────────────────────────────────
say('## Every GATING key: today on wave54-final | fakes its rule rejected | fixed pictures it accepted')
bad = []
for (script, key), g in sorted(GATING.items(), key=lambda kv: (kv[1]['lane'], kv[0])):
    can_fail = g['today'] == 'FAIL' or g['fakesWrong']
    if not can_fail:
        bad.append(f'{script} {key}')
    say(f'  {g["lane"]:26} {script:40} {key:56} → {g["unit"]:12} today {g["today"] or "—":4} | fakes WRONG {len(g["fakesWrong"])}'
        f' | fixed OK {", ".join(g["fixedOk"]) or "—"}' + (f' | fixed WRONG {", ".join(g["fixedWrong"])}' if g['fixedWrong'] else ''))
say('', f'gating keys {len(GATING)}: able to fail {len(GATING) - len(bad)} (today FAIL on device, or a fake rejected); with a fixed picture accepted '
    f'{sum(1 for g in GATING.values() if g["fixedOk"])}; §T standing rejection set {"HOLDS" if teeth_ok else "FAILS"} '
    f'({len(t_cov)} / {len(t_gating)} gating + tally keys reject a plausible fake, {len(t_viol)} violation(s); §T3 added-ink holes {len(s_holes)}; '
    f'§T4 strictly-additive IN-ZONE {t4_in}, > 2-px holes {t4_big}); '
    f'geometry-gate self-test {"HOLDS" if selftest_ok else "FAILS"}')
for b in bad:
    say(f'  CANNOT FAIL: {b}')
OUT_TXT = args[args.index('--out') + 1] if '--out' in args else os.path.join(HERE, 'probes-selfcheck.txt')
_tmp = OUT_TXT + '.tmp-' + str(os.getpid())                # round 4: atomic — never a half-written record under a reading lane
open(_tmp, 'w').write('\n'.join(out) + '\n'); os.replace(_tmp, OUT_TXT)
print('\n'.join(out[-12:]))
sys.exit(1 if bad or not selftest_ok or not teeth_ok else 0)
