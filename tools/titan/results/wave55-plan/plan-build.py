#!/usr/bin/env python3
# tools/titan/results/wave55-plan/plan-build.py — writes watchlist.txt and expectations.json for PLAN.md (wave 55).
#
# Why a script: the closing-gate expectations are the lanes' carrier sets, predictions and must-not-move cells, and
# most of those lists already live in the family censuses beside this file. Typing them by hand is how wave 52 got 11
# dead watch lines (plan-skeptic-1 C7). This is the wave-54 generator (tools/titan/results/wave54-plan/plan-build.py)
# adapted to the seven wave-55 lanes. Everything wave 54 checked is still checked:
#   1. Every "from" value of a prediction is VERIFIED against cells-wave54-final.json (the scorer's own loader, written
#      by snapshot-cells.mjs — the wave-55 COPY beside this file: the wave-54 script writes into its own directory);
#      a brief value that disagrees with the run of record stops the build.
#   2. Every watch / must-not-move line names a SCORED cell of wave54-final and matches exactly ONE test under
#      score-gate.mjs watchCells' substring rule; every carrier stem has a capture PNG in tools/titan/runs/wave54-final.
#   3. Family-wide must-not-move lists are ENUMERATED from the snapshot / census, and every exclusion of another
#      lane's carrier is printed.
# Six things are new in wave 55, each the resolution of a briefs-audit conflict (PLAN §9; `planDecisions` below):
#   a. THE COMPLEMENT RULE (C1): every scored cell outside the UNION of all lanes' capture carriers (and outside the
#      ring-fenced test) is must-not-move. Each lane keeps its named list (its brief's radius controls, restated
#      outside the union); every other non-carrier cell lands ONCE in `mustNotMoveComplement` — so a cell inside
#      another lane's carrier set is that lane's prediction row, never a third lane's must-not-move (C2/C3/C5/C6).
#   b. MAY-MOVE (C1/C2): L6's 252-document legacy-border radius (+ the census (2b) opacity box, see D2) is a PIXEL
#      class of its own: a changed capture there is not a leak, its score keeps R5's 0.002 band (it is in the
#      complement). Other lanes' carriers are subtracted from it (C2: L6's rules never fire on L2 / L3 movers).
#   c. KEY SECTIONS (C15): every geometry expect key carries the cell it reads (`keyCells`), so geometry-gate.py and
#      probe-readout.mjs take --sections and a stage-1 read-out never reports an unprobed section's key UNMEASURED.
#   d. ONE INTERPRETER (C14): every probe cmd starts with PY (asserted).
#   e. FLOORS (C11 + the wave-54 lesson): every gating floor is the 0.95 pass threshold (the magnitude is the row's
#      `to`); an f→f row under a gating geometry key carries `floorNA` instead of a floor (rule 2 binds its direction).
#   f. ONE stage-1 device probe `wave55-pre` over the union of the briefs' device-probe sections (C1, C15).
# Plan-skeptic round 1 (fix-r1.done.md) added, each asserted below:
#   g. THE DECISION INPUTS ARE COMMITTED FILES (process-holes S3): probe-decisions/{reverted,restate,contingency,holds}.json
#      beside this file are read by DEFAULT and are append-only — every decision recorded in the installed
#      expectations.json must still be in them (a stage-2 regeneration given only its own decision cannot drop stage 1's);
#      `generatedWith` records the argv and the sha256 of every input file, and is the report's first line.
#   h. A reverted L6 U2 (or U1) withdraws the may-move class (process-holes M4) and every A/B arm whose unit is reverted
#      moves to `abArmsWithdrawn` (S8); `unionMayMoveCaptures` is computed AFTER the probe decisions.
#   i. L6 may-move honours Compose's MEDIUM default (generator-truth M2, D21): android-paint-order.maymove-medium.out.txt.
# Plan-skeptic round 2 (fix-r2.done.md) added, each asserted or rendered below:
#   j. stage-1 decisions revert a lane only for its OWN platform's evidence; a whole-section move is rule 5's, bisected to
#      its commit (generator-truth S1); R6 never retires a stayDegenerateEvenIfPass cell (S2, read by adjudicate.mjs);
#   k. contingency units generalised (process-holes S2): L5 U1-ios-LRM is pre-registered as L1 NB-ios-b is, and a landed
#      contingency is prepended to every revertOrder that holds ITS base unit; a contingency row names a known unit;
#   l. GO-SMALL is a before-landing decision only (S7): with goSmall.held, drop-L4-U2 drops U2-ios alone, and a U2-android
#      revert row is refused with that message;
#   m. beforeEveryDeviceRun is an executable string (S1 / S4 / S5): untracked code refused, a disk floor, the lane trees'
#      daemons by their wave55- names; the hash read waits on a NEW launch, bounded (S10); L4's probe reads --base (M4).
# Plan-skeptic round 3 (fix-r3.done.md): n. beforeEveryDeviceRun carries no prose (beforeEveryDeviceRunNote holds it),
#      exports PROJECT_ROOT and uses absolute paths; `bash -n` on it is asserted.
# Pure JSON/text plus os.path.exists / os.listdir; no image is decoded, nothing is run — except `bash -n` (noexec: a syntax
# check) on the beforeEveryDeviceRun string (round 3, verify M1).
#
# Usage: node tools/titan/results/wave55-plan/snapshot-cells.mjs wave54-final   (once; writes cells-wave54-final.json here)
#        /opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/plan-build.py
# Decision inputs (round 1, S3): probe-decisions/reverted.json, restate.json, contingency.json and holds.json beside this
#   file, read by default; the orchestrator APPENDS a row there (committed with named paths) and re-runs this script.
# Dry-run options (wave 54's, kept as OVERRIDES of the committed files, for self-checks only): --out DIR writes
#   expectations.json / watchlist.txt there instead of beside this file; --reverted FILE / --restate FILE /
#   --contingency FILE read that table from another JSON file; --go-small holds L4 U2-android (= holds.json goSmall).
# Check: RUN=wave54-final WATCH=tools/titan/results/wave55-plan/watchlist.txt \
#          node tools/titan/results/wave52-plan/watchlist-check.mjs        → must print "unmatched 0"
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
_argv = sys.argv[1:]
def _opt(name):
    if name in _argv:
        i = _argv.index(name); v = _argv[i + 1]; del _argv[i:i + 2]; return v
    return None
def _flag(name):
    if name in _argv:
        _argv.remove(name); return True
    return False
_ARGV_RECORD = [a for i, a in enumerate(sys.argv[1:]) if a != '--out' and (i == 0 or sys.argv[i] != '--out')]   # --out never changes the content
OUT = _opt('--out') or HERE
os.makedirs(OUT, exist_ok=True)           # a dry-run --out dir is created, never assumed
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
# Round 3 (verify M1): the SHARED tree every device run starts from (beforeEveryDeviceRun exports it as PROJECT_ROOT). A
# constant, not ROOT: the generator's dry runs in a throwaway worktree must render the same string byte for byte.
SHARED_ROOT = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
DECISIONS = os.path.join(HERE, 'probe-decisions')          # the committed, append-only decision inputs (round 1, S3)
_REVERTED_FILE = _opt('--reverted') or os.path.join(DECISIONS, 'reverted.json')
_RESTATE_FILE = _opt('--restate') or os.path.join(DECISIONS, 'restate.json')
_CONTINGENCY_FILE = _opt('--contingency') or os.path.join(DECISIONS, 'contingency.json')
_HOLDS_FILE = os.path.join(DECISIONS, 'holds.json')
_LOOKS_FILE = _opt('--looks') or os.path.join(DECISIONS, 'looks.json')       # stage 2 (PLAN §10 item 17): the may-move looks
INPUTS = {}                                # every file this generator reads → sha256 (generatedWith; round 1, S3)
def _track(path):
    import hashlib
    rel = os.path.relpath(os.path.abspath(path), ROOT)
    INPUTS[rel] = hashlib.sha256(open(path, 'rb').read()).hexdigest()
    return path
L4_GO_SMALL = _flag('--go-small') or bool(json.load(open(_track(_HOLDS_FILE))).get('goSmall', {}).get('held'))   # D1 (ua-heading-margin §4)
assert not _argv, f'unknown arguments: {_argv}'
RUN = 'wave54-final'                       # the gate of record every "from" is read against (re-read vs wave55-open: §10)
BASE = 'wave55-open'                       # the opening gate; every probe and the closing gate read Δ against it
load = lambda name: json.load(open(_track(os.path.join(HERE, name))))
PLATS = ('web', 'ios', 'android')
# ── may-move looks (stage 2, PLAN §10 item 17). mayMoveRule: "a may-move non-carrier with |Δ| > 0.002 stops the ship until its
# picture shows one of three pre-registered pictures" — the plan said what decides it and nothing said how adjudicate.mjs
# (which reads the complement, not the may-move class) learns the decision. A look recorded here (append-only, like the other
# decision files) is ENCODED as a non-gating, undirected prediction row of the lane that owns the may-move class: R4 asks
# nothing of it, rule 2 exempts it (read and labelled), probe-readout prints it as a prediction, and it leaves the complement, so
# R5 no longer holds it to the band. Never a tolerance: the band stays 0.002 for every other may-move cell, and a look names its
# picture (one of the three) and its evidence (a §10 item). A row for a cell that is not a may-move stem, or whose picture is not
# one of the three, is refused.
MAY_MOVE_PICTURES = ('a band now under its child', "the band now inside the element's opacity group", 'a UA-widget replica now over the band')
LOOKS = json.load(open(_track(_LOOKS_FILE)))
for _k in LOOKS:
    assert set(_k) >= {'lane', 'cell', 'run', 'open', 'probe', 'picture', 'why'}, f'probe-decisions/looks.json row needs lane / cell / run / open / probe / picture / why: {_k}'
    assert _k['picture'] in MAY_MOVE_PICTURES, f"looks.json {_k['cell']}: the picture must be one of mayMoveRule's three: {_k['picture']}"
LOOKED = {k['cell'] for k in LOOKS}
assert len(LOOKED) == len(LOOKS), 'looks.json: one row per cell'
CAPDIR = {'web': 'screenshots', 'ios': 'ios-screenshots', 'android': 'android-screenshots'}
# C14: ONE interpreter for every probe. /opt/homebrew/bin/python3.12 carries PIL 12.2 in its OWN site-packages;
# /usr/bin/python3 (Xcode 3.9) reaches PIL 11.3 only through the user site (~/Library/Python/3.9), which `-s` / `-I`
# / a sandboxed HOME disable — the likely reason the L2 lane saw "no PIL" (cbb-android.md R7) where the audit did not.
# -B: no __pycache__ beside wave53-plan/geometry_common.py (the stray .pyc of counter-suffix-rows.md R10).
PY = '/opt/homebrew/bin/python3.12 -B'
P55, G55 = 'tools/titan/results/wave55-plan', 'tools/titan/results/wave55-gate'
P54, P53 = 'tools/titan/results/wave54-plan', 'tools/titan/results/wave53-plan'
script_of = lambda cmd: os.path.basename(next(t for t in cmd.split() if t.endswith('.py')))

SNAP = load(f'cells-{RUN}.json')
CELLS = SNAP['cells']                      # "<sec>/<path>.html <platform>" -> "P 0.9818"
TESTS = sorted({k.rsplit(' ', 1)[0] for k in CELLS})
RUNDIR = os.path.join(ROOT, 'tools', 'titan', 'runs', RUN, 'sections')
# Every capture stem of the run of record, per platform (scored or not: -zoom / unscored natives are carriers too).
CAPS = {p: {} for p in PLATS}
for _sec in sorted(os.listdir(RUNDIR)):
    for _p in PLATS:
        _d = os.path.join(RUNDIR, _sec, CAPDIR[_p])
        if os.path.isdir(_d):
            for _f in os.listdir(_d):
                if _f.endswith('.png') and not re.search(r'__\d+\.png$', _f):
                    CAPS[_p][_f[:-4]] = _sec
ALLSTEMS = {s for p in PLATS for s in CAPS[p]}

def stem(test):
    """'css-text/bidi/bidi-lines-001.html' -> 'wpt__css-text__bidi__bidi-lines-001' (the capture / per-test IR stem)."""
    assert test.endswith('.html'), test
    return 'wpt__' + test[:-len('.html')].replace('/', '__')

def test_of(st):
    """The inverse of stem(): 'wpt__<sec>__<a>__<b>' -> '<sec>/<a>/<b>.html'."""
    return '/'.join(st.split('__')[1:]) + '.html'

def section_of(st):
    return st.split('__')[1]

def resolve(name):
    """A test path from a probe's short name ('abspos/abspos-autopos-htb-ltr', 'borders-008'): the ONE capture stem
    that equals it or ends in '__<name>' (refuses an ambiguous or missing name)."""
    tail = name.replace('/', '__')
    hits = sorted(s for s in ALLSTEMS if s.endswith('__' + tail))
    assert len(hits) == 1, f'resolve({name!r}): {hits[:4]}'
    return test_of(hits[0])

def scored(test, plat):
    return f'{test} {plat}' in CELLS

def cell(test, plat):
    """A must-not-move / watch line for a scored cell; refuses an unscored one (it would attribute nothing)."""
    line = f'{test} {plat}'
    assert line in CELLS, f'not a scored cell of {RUN}: {line}'
    return line

def cells_of(tests, plats=PLATS):
    """Every SCORED cell of these tests on these platforms (unscored platforms are skipped, never invented)."""
    return [f'{t} {p}' for t in tests for p in plats if scored(t, p)]

def caps(**per_plat):
    """{'web': [...stems], 'ios': [...], 'android': [...]} with every platform present."""
    return {p: sorted(set(per_plat.get(p, []))) for p in PLATS}

T = lambda *parts: '/'.join(parts) + '.html'     # readable test-path builder

# ── predictions ───────────────────────────────────────────────────────────────────────────────────────────────────────
# Plan rule (wave-53 plan-skeptic must-fix 2, kept, restated for C11): every HIGH / MED-HIGH row whose `to` is P, and
# every row whose `to` is P under a GATING geometry key, carries `gating: true` and the floor 0.95 — the pass threshold,
# never a replay magnitude (the magnitude is `to`; wave-54 lesson: two MED-HIGH iOS floors set from a replay fired on
# picture-correct flips, PLAN §10 item 7). An f→f row carries no floor whatever its tier: `floorNA` says why (C11 — a
# missing floor is never read as 0) and revert rule 2 binds its direction. MED and below without a gating key carry
# `floor: None, gating: False`. Rules 1 (P → f), 2 (Δ <= −0.002 on an up-or-stay row) and 5 (leak) bind every row.
PASS = 0.95
GEO_OK = 'GEOMETRY OK'
FLOOR_NA = 'n/a — stays f; direction-only (revert rule 2), so the adjudicator never reads a missing floor as 0 (briefs-audit C11)'
def pred(line, frm, to, kind, confidence, floor=None, gating=False, units=(), geometry=None, **extra):
    test, plat = line.rsplit(' ', 1)
    snap = CELLS.get(line)
    assert snap is not None, f'prediction on an unscored cell: {line}'
    # The brief's "from" must equal the run of record (first two tokens: verdict + score), or the plan is wrong.
    assert ' '.join(frm.split()[:2]) == snap, f'{line}: brief says "{frm}", {RUN} says "{snap}"'
    row = {'cell': line, 'from': frm, 'to': to, 'kind': kind, 'confidence': confidence, 'floor': floor,
           'gating': gating, 'geometry': geometry, 'units': list(units),
           # wave-54 fix r1 (S1), kept: 'up-or-stay' (a flip, a rise, or "stays / byte-identical") is bound by revert
           # rule 2 (Δ <= -0.002 reverts its commit); 'undirected' is EXEMPT from rule 2 (read and labelled instead);
           # NEW 'down-preregistered' (D5): the brief pre-registers a fall `predictedDelta`; rule 2 then fires at
           # Δ <= predictedDelta − 0.002 (the same 0.002 band, measured from the pre-registered value).
           'direction': extra.pop('direction', 'up-or-stay'),
           # wave-54 fix r2 (R2-S1), kept: the units that must be ON THE TREE for `to` (and the floor) to be reachable.
           'requires': list(extra.pop('requires', units))}
    row.update(extra)
    # every row says why its direction is what it is (the up-or-stay default: the brief pre-registers a rise or no change)
    row.setdefault('directionWhy', {'up-or-stay': 'the brief pre-registers a rise or no change (its replay / device evidence is in `to`); a fall of 0.002 contradicts it (rule 2)',
                                    'undirected': 'MISSING', 'down-preregistered': 'MISSING'}[row['direction']])
    assert row['directionWhy'] != 'MISSING', f'{line}: an undirected / down-preregistered row needs its own directionWhy'
    assert set(row['requires']) <= set(row['units']), f'{line}: requires names a unit the row does not carry'
    assert row['direction'] in ('up-or-stay', 'undirected', 'down-preregistered'), row
    assert row['direction'] != 'undirected' or not gating, f'{line}: a gating row must have a direction'
    assert row['direction'] != 'down-preregistered' or isinstance(row.get('predictedDelta'), float), f'{line}: predictedDelta'
    assert not gating or floor == PASS, f'{line}: a gating floor is the pass threshold {PASS} (the magnitude is `to`)'
    if to.startswith('f'):
        assert floor is None and not gating and row.get('floorNA'), f'{line}: an f→f row carries floorNA, never a floor'
    return row

def note(text, units=()):
    """A non-corpus prediction (a tripwire, a suite, a wire differential) — adjudicate.mjs prints it as 'read elsewhere'."""
    return {'cell': text, 'from': '-', 'to': '-', 'kind': 'non-corpus', 'confidence': 'see PLAN', 'floor': None,
            'gating': False, 'geometry': None, 'units': list(units), 'direction': None}

def probe(cmd, expect, keyCells, gating=None, report=(), requires=None, contains=None, **extra):
    """A geometryProbes entry. `keyCells` maps EVERY expect key to the cell it reads ('<test> <platform>'), so the
    readers can filter keys by section (C15) and the tally can be cross-checked; the cmd must start with PY (C14)."""
    assert cmd.startswith(PY + ' '), f'probe cmd not on the pinned interpreter (C14): {cmd}'
    assert set(keyCells) == set(expect), f'{script_of(cmd)}: keyCells {sorted(set(expect) ^ set(keyCells))[:4]}'
    for k, c in keyCells.items():
        t, p = c.rsplit(' ', 1)
        assert p in PLATS and stem(t) in CAPS[p], f'{script_of(cmd)} {k}: no {p} capture of {t} in {RUN}'
    d = {'cmd': cmd, 'expect': dict(expect), 'keyCells': dict(keyCells), 'gating': dict(gating or {}),
         'report': list(report)}
    if requires:
        d['requires'] = dict(requires)
    if contains:
        d['contains'] = dict(contains)
    d.update(extra)
    return d

# ── the censuses (each re-derived at planning by its own script: generator.done.md §2) ─────────────────────────────────
l1c = load('hyphen-ios-linebox.census.json')
l3c = load('positioned-parent-slot.census.json')
l4c = load('ua-heading-margin.census.json')
l6c = load('android-paint-order.census.json')
def section_lines(fname, start, stop=None):
    """The lines of a census read-out between a header line starting with `start` and the next starting with `stop`."""
    out, on = [], False
    for l in open(_track(os.path.join(HERE, fname))):
        if l.startswith(start):
            on = True; continue
        if on and stop and l.startswith(stop):
            break
        if on:
            out.append(l.rstrip('\n'))
    assert on, f'{fname}: no section {start!r}'
    return out

# Round 6 (plan-skeptic round 5 M1-M6, D40): every lane's stage-1 decision on its residual reads (geometry-gate.py, residual-read.py).
R6_STAGE1 = (' Round 6 (D40): a gating / tally key whose only reason is `residual:` (the device picture differs from wave55-open outside the key\'s '
             'declared change zone) is decided by the recorded look of PLAN §7 stage 1 step 8 — right: `fix-r6.zones.py --also wave55-pre:<cell> --write` '
             're-records that cell\'s windows from the device capture (a probe edit with the step-6 regeneration); wrong: it reverts like any gating FAIL; '
             'never a tolerance')

# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# L1 · hyphen-ios-linebox — NB-ios: a declared-`normal` WPT run on iOS lays out on Blink's rounded line box.
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
HC = {n: T('css-text', 'hyphens', f'hyphenate-character-00{n}') for n in (1, 2, 3, 4, 5)}
BSH = {n: T('css-text', 'boundary-shaping', f'boundary-shaping-0{n:02d}') for n in range(1, 11)}
TDD = {n: T('css-text-decor', f'text-decoration-dotted-00{n}') for n in (1, 2)}
DISC = T('css-counter-styles', 'counter-style-at-rule', 'disclosure-styles')
l1_ios = l1c['captureCarriers']['ios']
assert sorted(l1_ios) == sorted(stem(t) for t in [HC[1], HC[2], HC[3], HC[4], TDD[1], TDD[2]] + [BSH[n] for n in range(1, 9)]), l1_ios
assert not l1c['captureCarriers']['web'] and not l1c['captureCarriers']['android'] and not l1c['wireCarriers']
l1_radius = [test_of(s) for s in l1c['mustNotMoveInRadius']['ios']]
assert len(l1_radius) == 5 and DISC in l1_radius
# Pre-landing regeneration (PLAN §10 item 4; regen.done.md) — the L1 lane's own census (wave55-hyphen-ios-linebox/census.out.txt,
# written independently of the brief's .census.mjs, guard by guard from the delivered predicate) SHRANK the radius by one
# document: all-prop-001's `all: initial` resets its line-height, so NB-ios never reaches it. The brief's 60 components in 19
# documents (5 SAME) become 58 in 18 (4 SAME / DECLINED). all-prop-001 ios STAYS a named must-not-move row (l1_radius above, the
# brief's census): outside the radius a move there is a leak to bisect, never R1 evidence about NB-ios's snapping. Asserted here.
_l1n = open(_track(os.path.join(HERE, '..', 'wave55-hyphen-ios-linebox', 'census.out.txt'))).read().split('\n')
_l1a = next(i for i, l in enumerate(_l1n) if l.startswith('(1) declared-normal text runs:'))
_l1b = next(i for i, l in enumerate(_l1n) if l.startswith('(2) per document'))
_l1rows = [l.split() for l in _l1n[_l1a + 1:_l1b] if l.strip()]
assert _l1n[_l1a] == '(1) declared-normal text runs: 71 in 18 documents', _l1n[_l1a]
assert (len(_l1rows), len({(r[1], r[2]) for r in _l1rows}), len({r[1] for r in _l1rows})) == (71, 58, 18), 'the L1 census radius moved'
l1_same = sorted({'wpt__' + r[1] for r in _l1rows if r[0] in ('SAME', 'DECLINED')})
assert l1_same == sorted(stem(t) for t in l1_radius if t != T('css-cascade', 'all-prop-001')), l1_same
assert not any('all-prop-001' in r[1] for r in _l1rows) and len(l1_same) == 4
BSU = {1: '0.9792', 2: '0.9694', 3: '0.9781', 4: '0.9774', 5: '0.9774', 6: '0.9714', 7: '0.9764', 8: '0.9789'}   # radius replay U
LB = f'{PY} {P55}/hyphen-ios-linebox.geometry.py <run> --base <base>'     # --base: the glyph-identity reference (round 1, M7)
lb_stack = {f'hyphen-ios-linebox stack {stem(t)[len("wpt__css-text__"):] if t.startswith("css-text/") else stem(t)[len("wpt__css-text-decor__"):]} ios': t
            for t in [BSH[n] for n in range(1, 9)] + [TDD[1], TDD[2]]}
L1 = {
  'dir': 'tools/titan/results/wave55-hyphen-ios-linebox',
  'briefs': ['hyphen-ios-linebox.md'],
  'ownership': {'owned': ['NEW runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift',
                          'NEW runtimes/swiftui/Tests/StyleConverterRuntimeTests/NormalLineBoxTests.swift',
                          'tools/titan/results/wave55-hyphen-ios-linebox/'],
                'seams': ['ComponentRenderer.swift seam 1 (PlaceholderLabel line-box block :5055-5160; seam-registry §2 order 6)',
                          'ComponentRenderer.swift seam 1b (:5335) — CONTINGENCY NB-ios-b only'],
                'readOnly': ['LineBoxMetrics.swift', 'LineHeightNormal.swift', 'LineHeightExtractor.swift', 'StyleBuilder.swift (L5 adds +2 lines)',
                             'TypographyAggregate.swift (L5 adds +1 field)', 'GreedyLineBreaker.swift', 'SpentHyphen.swift', 'InlineRunFlow.swift',
                             'tools/titan/extract-fixture.mjs (U3b stays)', 'every Compose and web file']},
  'captureCarriers': caps(ios=l1_ios),
  'wireCarriers': [],
  'carrierRule': {'NB-ios': l1c['rule'] + ': 58 text components (71 declared-normal runs) in 18 documents — the lane\'s census '
                            '(wave55-hyphen-ios-linebox/census.out.txt): all-prop-001 is OUTSIDE the radius (`all: initial` resets its '
                            'line-height), the brief\'s 60 in 19 counted it; 14 iOS captures move '
                            '(4 TARGET multi-line runs + 10 MOVES stacked one-line runs: Libertine 36 42→41, Inter 92 112→111); 4 SAME / DECLINED '
                            '(disclosure-styles — L5\'s U1-ios carrier by C5 —, anonymous-table-ws-001 and boundary-shaping-010: one-line box '
                            'unchanged; hyphens-vertical-001 is a vertical run DECLINED by the horizontal-writing-mode guard — '
                            'D22: TextKit draws it on 3 lines while the singleLineText proxy reads 1, so without the guard NB-ios would pin it '
                            'to one 19-px box); web 0 · android 0 · wire 0 (hyphen-ios-linebox.census.out.txt (4)-(5))'},
  'stage1Sections': ['css-text'],
  'probeSections': ['css-text', 'css-text-decor', 'css-cascade', 'css-counter-styles', 'css-tables'],
  'predictions': [
    pred(f'{HC[1]} ios', 'P 0.9611', 'P ≈0.978 (replay Rm 0.978; drop-U3b arm 0.9778; ceiling R0 0.9803; §9 R1 risk arm 0.9628)',
         'picture-correctness (P→P): in-run pitch 19, group firsts on the ref rows', 'MED', PASS, True, ['NB-ios'], 'LINEBOX OK'),
    pred(f'{HC[3]} ios', 'P 0.957', 'P ≈0.976 (Rm 0.9759; arm 0.9758; R0 0.9785; R1 0.9592)',
         'picture-correctness (P→P): in-run pitch 19, group firsts on the ref rows', 'MED', PASS, True, ['NB-ios'], 'LINEBOX OK'),
    pred(f'{HC[4]} ios', 'f 0.9477', 'P ≈0.975 (Rm 0.975; arm 0.9742; R0 0.9782; R1 0.9556)', 'flip (the wave-54 U3b drift removed)',
         'MED', PASS, True, ['NB-ios'], 'LINEBOX OK'),
    pred(f'{HC[2]} ios', 'P 0.9534', 'P ≈0.969 (Rm 0.9691; arm 0.9692); the TextKit emergency breaks stay',
         'mover up — DEGENERATE even if P (TextKit emergency breaks tati/on, atio/n: not a hyphenation position)', 'MED', None, False, ['NB-ios'],
         'LINEBOX OK (report key)'),
  ] + [pred(f'{BSH[n]} ios', CELLS[f'{BSH[n]} ios'], f'P, the same to ±0.0001 (radius replay U {BSU[n]})',
            'stacked one-line runs: boxes 2 / 3 up 1 / 2 rows (web\'s and Android\'s stacking); score flat', 'MED-HIGH', PASS, True, ['NB-ios'],
            'STACK OK (report key)',
            # D4: the brief's "flat (undirected, within 0.002)" is restated up-or-stay — the replay pre-registers ±0.0001,
            # so a fall of 0.002 contradicts it; the plan rule (MED-HIGH ⇒ floor) gives the 0.95 floor the brief left blank.
            directionWhy='D4: the replay pre-registers |Δ| ≤ 0.0001; rule 2 (Δ ≤ −0.002) cannot fire on the predicted picture')
       for n in range(1, 9)]
    + [pred(f'{TDD[n]} ios', CELLS[f'{TDD[n]} ios'], f'f ≈{v} (radius replay U), stays f', 'mover up, stays f (divs 2 / 3 up 1 / 2 rows)',
            'LOW-MED', None, False, ['NB-ios'], 'STACK OK (report key)', floorNA=FLOOR_NA)
       for n, v in ((1, '0.5355'), (2, '0.5472'))],
  'mustNotMove': sorted(set(
      cells_of([t for t in l1_radius if t != DISC], ('ios',))           # C5: disclosure-styles ios is L5's U1-ios carrier
      + cells_of([HC[1], HC[3], HC[4]], ('web', 'android'))             # U3b's lifts (wave 54): byte-identical, HIGH
      + [k for k in CELLS if k.startswith('css-text/')])),              # §8 KEEP: css-text outside the carriers (mnm() filters)
  'geometryProbes': [
    probe(LB,
          {**{f'hyphen-ios-linebox 00{n} {p}': 'LINEBOX OK' for n in (1, 3, 4) for p in PLATS},
           'hyphen-ios-linebox 002 ios': 'LINEBOX OK', **{k: 'STACK OK' for k in lb_stack}},
          {**{f'hyphen-ios-linebox 00{n} {p}': f'{HC[n]} {p}' for n in (1, 3, 4) for p in PLATS},
           'hyphen-ios-linebox 002 ios': f'{HC[2]} ios', **{k: f'{t} ios' for k, t in lb_stack.items()}},
          gating={f'hyphen-ios-linebox 00{n} ios': 'NB-ios' for n in (1, 3, 4)},
          report=['hyphen-ios-linebox 002 ios'] + sorted(lb_stack),
          reasonToUnit={'pitch:': 'NB-ios (or the wave-54 U3b reverted underneath it)', 'drift:': 'NB-ios (or U3b reverted underneath it)',
                        'anchor:': 'alone: swap in the contingency NB-ios-b (revertRule 8), re-probe css-text', 'lines:': 'not this lane (wave-54 U2-ios / U3)',
                        'glyphs:': 'NB-ios (a line-box unit moved glyphs or broke lines: read the picture against wave55-open)'},
          onWave54Final='3 gating rows WRONG (pitch: group 1 line 4 offset -1; drift [0, -1, -2, -2]); 6 web/android controls OK; 002 ios and the 10 stack rows WRONG (report); every ref row OK; exit 0',
          teeth=('--fakes (6 fakes built from the REF\'s own pixels: white, whole+1, u3b-today, drop-u3b, no-u3b, g4+2) all rejected on 001/003/004; --replay Rm/Rw/R0 print the 3 gating rows OK; R1 prints the NB-ios-b `anchor:` trigger; Rd prints drift (probes-selfcheck.txt §L1). '
                 'Round 1 (instrument-teeth M7) added `glyphs:` — every line\'s ink crop equals the base capture\'s same line, translated by whole rows only (correlation ≥ 0.97, same count): '
                 'text ±2/±3x, one line +3x, a missing hyphenate-character (003 / 004) and two swapped lines are WRONG; the standing fakes are probes-selfcheck.txt §T. '
                 'Round 2: four new fakes (one hyphen removed, one cell cut, one run +1x, all +1x) are all WRONG; the tally\'s hyphenate-character-001 / -003 ios controls '
                 'pass the fixed pictures and reject a cut last cell / a missing hyphen (probes-teeth-r2.py). '
                 'Round 3 (verify M2, D38): `ink:` — each line\'s ink mask = the base line\'s ±2 px (XOR, same crop): a blob in a line or a glyph painted twice is WRONG (probes-teeth-r3.py). '
                 'Round 4 (D39): that XOR counts both nested masks (min < 200 and the core, min < 100) and is summed over the lines — a 1-px smear that only darkens a glyph\'s edge is WRONG (probes-selfcheck.txt §T4). '
                 'Round 6 (verify-r5 M4, D40): geometry-gate.py\'s RESIDUAL read (residual-read.py) on the 3 gating keys and the 2 tally controls — outside the lines\' crop rows (the probe\'s own core bands ±3, x < 140, capture ∪ base) the capture = the base pixel for pixel, the vacated rows white, every changed px inside a grey: a faint 3×3 between word groups and a faint 30-px line below the text are WRONG (probes-teeth-r5.py; probes-selfcheck.txt §T5)')),
    probe(f'{PY} {P54}/hyphenate-character.geometry.py <run>',
          {f'hyphenate-character-00{n} {p}': GEO_OK for n in (1, 3, 4) for p in PLATS},
          {f'hyphenate-character-00{n} {p}': f'{HC[n]} {p}' for n in (1, 3, 4) for p in PLATS},
          restated=('wave-54 probe, registered unchanged as CONTROLS: its gating 001 / 003 ios (U2-ios|U3) and its report rows shipped; its ±4-px '
                    'tops cannot see 0(f) (OK on wave54-final AND on the drop-U3b arm) so it guards the glyph and the line count only; the ios '
                    'keys sit on L1 carriers (a FAIL there is read in the cell review: R4 cannot see a carrier)')),
  ],
  'revertUnits': {
    'NB-ios': {'commit': 'NEW StyleEngine/typography/line/NormalLineBox.swift (usedLineHeight declines a non-horizontal run: D22) + NEW Tests/…/NormalLineBoxTests.swift '
                         '(pins P1-P8, mutations M1-M8; P8 = hyphens-vertical-001 __2-320 verbatim → nil, M8 drops the guard; the Catalyst pin logs lineCountIsExact / '
                         'renderedLineCount for the in-radius SAME controls and all-prop-001 — 0 declared-normal runs: outside the radius) + tools/titan/results/wave55-hyphen-ios-linebox/seam-1.patch (ComponentRenderer.swift PlaceholderLabel: '
                         'cut :5055-5069, insert `let usedLineHeight = NormalLineBox.usedLineHeight(…, horizontalWritingMode: horizontalWritingMode)` after :5151-5152, '
                         're-insert the `leading` statement reading it, :5154 → usedLineHeight)',
               'captures': caps(ios=l1_ios), 'wire': [], 'revertOrder': ['NB-ios'],
               'crossWave': ('NB-ios is ADDITIVE on the wave-54 U3b (on the tree): after NB-ios lands U3b is load-bearing on iOS. Revert NB-ios BEFORE '
                             'U3b, never U3b alone (replay Rd: 004 ios P → f)')},
  },
  'contingencyUnits': {
    'NB-ios-b': {'base': 'NB-ios', 'trigger': ('the three gating rows print `LINEBOX WRONG` with the reason `anchor: group 1 line 1 offset 1` and nothing else (§9 R1); '
                                               'the iOS css-text movers outside the 12 carriers, if any, are only the in-radius one-line SAME controls '
                                               '(boundary-shaping-010; anonymous-table-ws-001, read at wave55-probe), each EXACTLY one row low — the R1 '
                                               'signature (pre-landing regeneration, L1 skeptic should-fix 1): NB-ios-b\'s top pad 0 restores them too'),
                 'commit': 'ComponentRenderer.swift seam-1b (:5335 `.padding(.vertical, leading.halfLeading)` → `NormalLineBox.verticalPads` (top 0 / bottom 2× half-leading for a pinned declared-normal run; (half, half) otherwise)) + NormalLineBox.verticalPads + its pin',
                 'captures': caps(ios=l1_ios), 'wire': [], 'revertOrder': ['NB-ios-b'],
                 'onLand': 'append it to probe-decisions/contingency.json (D25) and re-run this script: it becomes a revert unit, NB-ios\'s revertOrder becomes [NB-ios-b, NB-ios], the drop-NB-ios arm drops it first, re-probe css-text (a pre-registered stage1Probe.reprobes id)'},
  },
  'stage1Decision': ('at wave55-pre (union carriers): KEEP NB-ios iff the 3 gating rows print `→ LINEBOX OK`; 001 / 003 / 004 ios ≥ 0.95; iOS '
                     'boundary-shaping-010 and hyphens-vertical-001 byte-identical; no iOS css-text cell outside the 12 css-text carriers moves ≥ 0.002 '
                     '(the iOS clauses only: NB-ios is a Swift-only seam). Web / Android css-text captures outside the union carriers (L3: '
                     'bidi-tab-001, hyphenate-character-005 android; L6 may-move stems: |Δ| ≤ 0.002) are rule-5 must-not-move for EVERY lane: a '
                     'move there is bisected to its commit (probe-readout.mjs names none), never an NB-ios revert by itself (round 2, '
                     'generator-truth S1). The only reason `anchor: … offset 1` → swap to NB-ios-b, re-probe css-text. The R1 SIGNATURE — the gating '
                     'reason is `anchor: group 1 line 1 offset 1` only AND the iOS movers outside the carriers are only in-radius one-line SAME controls '
                     '(boundary-shaping-010 at stage 1; anonymous-table-ws-001 at wave55-probe), each exactly one row low (disclosure-styles ios\'s '
                     'horizontal runs are SAME too: on L5\'s U1-ios carrier the same one-row move is R1\'s, never an L5 revert by itself) — is the world NB-ios-b was '
                     'built for: it routes to `land-units.sh --contingency L1:NB-ios-b` (never a revert of NB-ios), and the pre-registered '
                     'wave55-pre-r1 re-probe must then print the 3 gating rows `→ LINEBOX OK` and those SAME controls byte-identical to wave55-open '
                     '(pre-landing regeneration, L1 skeptic should-fix 1; PLAN §10 item 4). Any other WRONG on the 3 gating rows or an iOS clause '
                     '→ revert NB-ios (one commit; after NB-ios-b, its revertOrder). css-cascade / css-counter-styles / css-tables / css-text-decor '
                     'radius rows: read at wave55-probe') + R6_STAGE1,
}

# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# L2 · cbb-android — U1 re-lands CBB-android (5b333c8a), U2 adds the in-flow % content-box band; a PAIR.
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
NBR = {k: T('filter-effects', f'backdrop-filter-nested-border-radius-clip{k}') for k in ('', '-2', '-3', '-4')}
APOS = {k: T('css-flexbox', 'abspos', f'abspos-autopos-{k}') for k in ('htb-ltr', 'vlr-ltr', 'vrl-ltr', 'htb-rtl', 'vlr-rtl', 'vrl-rtl')}
SRS = {k: T('css-position', f'position-absolute-semi-replaced-stretch-{k}') for k in ('button', 'input', 'other')}
G006 = T('css-gaps', 'flex', 'flex-gap-decorations-006')
l2_stems = sorted({l.split()[0] for l in section_lines('cbb-android.census.out.txt', '## A.', '## B.') if l.startswith('wpt__')})
assert l2_stems == sorted(stem(t) for t in [*NBR.values(), *APOS.values(), *SRS.values(), G006]), l2_stems
l2_u2 = sorted({l.split()[0] for l in section_lines('cbb-android.census.out.txt', 'B carriers', '## C.') if l.startswith('wpt__')})
assert l2_u2 == [stem(NBR['-3'])], l2_u2
# Round 1 (instrument-teeth M1): the WAVE-55 copy of the wave-54 probe, + the green-mass reason (the wave-54 file is a record).
CBBG, CBB2 = f'{PY} {P55}/compose-wpt-content-box-cb.geometry.py <run>', f'{PY} {P55}/cbb-android.geometry.py <run>'
AP_KEY = lambda k, p: f'abspos/abspos-autopos-{k} {p}'
NB_KEY = lambda k, p: f'backdrop-filter-nested-border-radius-clip{k} {p}'
RTL_LINE = 'GEOMETRY WRONG (green (16, 88, 105, 187) vs ref (16, 88, 115, 187))'
CROSS = T('css-images', 'cross-fade-target-alpha')
VIEWPORT = [T('css-multicol', f'abspos-autopos-contained-by-viewport-00{n}') for n in (0, 1)]
L2 = {
  'dir': 'tools/titan/results/wave55-cbb-android',
  'briefs': ['cbb-android.md'],
  'ownership': {'owned': ['NEW runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/ContainingBlockBands.kt (= git show 5b333c8a)',
                          'runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/DynamicValueResolver.kt (defaulted param :207-210 + one condition before :238)',
                          'runtimes/compose/src/test/java/com/styleconverter/runtime/core/variables/DynamicValueResolverTest.kt',
                          'NEW runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt',
                          'runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/SizingApplier.kt (two one-line call sites after :123 / :132 + one comment line)',
                          'NEW runtimes/compose/src/test/java/com/styleconverter/runtime/sizing/PercentContentBoxBandTest.kt',
                          'NEW (patch-borne, seam 1) runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/ChildContainingBlockSeamWiringTest.kt',
                          'tools/titan/results/wave55-cbb-android/'],
                'seams': ['ComponentRenderer.kt seam 1 (:1950-1951 childContainingBlock named argument; seam-registry §1 order 3)'],
                'readOnly': ['SizingExtractor.kt', 'ExactWidthOverflow.kt', 'PercentSizeClamp.kt', 'StyleApplier.kt (L6 owns; C13: cite the chain by symbol)',
                             'LayoutFacade.kt', 'OverflowApplier', 'BackdropModifier.kt', 'AbsposInsetStretch.kt', 'VerticalTextFlowLayout.kt',
                             'ContentBoxRelativeInflationTest.kt (C9: its stale "future work" phrase goes to the closing docs pass)']},
  'captureCarriers': caps(android=l2_stems),
  'wireCarriers': [],
  'carrierRule': {
    'U1': 'ContainingBlockBands.subtracts(properties, wptCaptureMode): under WPT capture an effective content-box publishes its declared size; a box is a carrier when its incoming block differs AND it reads it (oof / spc / vtext / dyn): 33 boxes in 14 documents, android only (cbb-android.census.out.txt A; identical to wave-54 probeDecisions CBB-android carriersWithdrawn)',
    'U2': 'PercentContentBoxBand.owns: WPT capture, content-box, a Relative(PERCENT) size, a non-zero band, PercentSizeClamp not owning the axis: 2 of the 122 percentage axes, #middle (0-172) W and H of nested-border-radius-clip-3 — already a U1 carrier (census B)',
  },
  'stage1Sections': ['filter-effects'],
  'probeSections': ['filter-effects', 'css-flexbox', 'css-position', 'css-gaps', 'css-images', 'css-multicol'],
  'predictions': [
    pred(f'{NBR["-3"]} android', 'f 0.9574', 'P ≈0.999 (replay on Android\'s own re-laid probe pixels 0.9999; siblings at device probe 1 / 1 / 0.9999)',
         'flip (the 0(g) target: #middle\'s band added at layout time)', 'MED-HIGH', PASS, True, ['U1', 'U2'], GEO_OK),
    pred(f'{NBR[""]} android', 'P 0.9649', 'P ≈1 (device: wave54-probe 1)', 'short box → faithful', 'HIGH', PASS, True, ['U1'], GEO_OK),
    pred(f'{NBR["-2"]} android', 'P 0.9689', 'P ≈1 (device: wave54-probe 1)', 'short box → faithful', 'HIGH', PASS, True, ['U1'], GEO_OK),
    pred(f'{NBR["-4"]} android', 'P 0.977', 'P ≈0.9999 (device: wave54-probe)', 'short box → faithful', 'HIGH', PASS, True, ['U1'], GEO_OK),
  ] + [pred(f'{APOS[k]} android', 'P 0.9966 DEGENERATE', 'P 0.9967 faithful (device: wave54-probe)', 'red-ink DEGENERATE → faithful', 'HIGH', PASS, True, ['U1'], GEO_OK)
       for k in ('htb-ltr', 'vlr-ltr', 'vrl-ltr')]
    + [pred(f'{APOS["htb-rtl"]} android', 'P 0.9869', 'P 0.9966 (device: wave54-probe); the picture becomes the iOS line (GEOMETRY WRONG)',
            'mover up — DEGENERATE even if P: the 10-px RTL static-position residue (green x16-105, red x106-115) is both natives\', not this lane (cbb-android R6)',
            'HIGH', PASS, True, ['U1'], RTL_LINE)]
    + [pred(f'{APOS[k]} android', 'P 0.9966', 'P 0.9966 (device Δ 0); the picture changes to the iOS line',
            'flat — DEGENERATE even if P: the 10-px RTL static-position residue (cbb-android R6)', 'HIGH', PASS, True, ['U1'], RTL_LINE)
       for k in ('vlr-rtl', 'vrl-rtl')]
    + [pred(f'{SRS[k]} android', CELLS[f'{SRS[k]} android'], f'f ≈{v} (device: wave54-probe), stays f', 'up, stays f (the native control chrome is not this unit\'s)',
            'HIGH', None, False, ['U1'], floorNA=FLOOR_NA) for k, v in (('button', '0.9843'), ('input', '0.4555'), ('other', '0.4679'))]
    + [pred(f'{G006} android', 'f 0.8223', 'f 0.8223 (device Δ 0)', 'U1 side reader (vtext)', 'MED', None, False, ['U1'], floorNA=FLOOR_NA,
            direction='undirected', directionWhy='a vtext side reader of the corrected block (296×296 → 300×300); device Δ 0 at wave54-probe, no sign pre-registered')],
  'mustNotMove': sorted(set(cells_of([test_of(s) for s in l2_stems], ('web', 'ios')) + cells_of([CROSS]) + cells_of(VIEWPORT)
                            + [k for k in CELLS if k.startswith('filter-effects/') and not k.endswith(' android')])),   # §8 read 4: 48/48 web + iOS
  'geometryProbes': [
    probe(CBBG,
          {**{AP_KEY(k, p): GEO_OK for k in APOS for p in ('web', 'android')},
           **{AP_KEY(k, 'ios'): GEO_OK for k in ('htb-ltr', 'vlr-ltr', 'vrl-ltr')},
           **{AP_KEY(k, p): RTL_LINE for k in ('htb-rtl', 'vlr-rtl', 'vrl-rtl') for p in ('ios', 'android')},
           **{NB_KEY(k, p): GEO_OK for k in ('', '-2', '-3', '-4') for p in PLATS}},
          {**{AP_KEY(k, p): f'{APOS[k]} {p}' for k in APOS for p in PLATS}, **{NB_KEY(k, p): f'{NBR[k]} {p}' for k in NBR for p in PLATS}},
          gating={**{AP_KEY(k, 'android'): 'U1' for k in ('htb-ltr', 'vlr-ltr', 'vrl-ltr')},
                  **{NB_KEY(k, 'android'): 'U1' for k in ('', '-2', '-4')}, NB_KEY('-3', 'android'): 'U2'},
          requires={NB_KEY('-3', 'android'): ['U1', 'U2']},
          report=[AP_KEY(k, 'android') for k in ('htb-rtl', 'vlr-rtl', 'vrl-rtl')],
          contains={**{AP_KEY(k, 'android'): "'green': (16, 88, 115, 187)" for k in ('htb-ltr', 'vlr-ltr', 'vrl-ltr')},
                    NB_KEY('', 'android'): "'green': (26, 26, 225, 125)", NB_KEY('-2', 'android'): "'green': (26, 26, 225, 125)",
                    NB_KEY('-4', 'android'): "'green': (26, 26, 185, 105)", NB_KEY('-3', 'android'): "'green': (36, 36, 235, 135)"},
          restated=('the wave-54 probe re-used as a WAVE-55 COPY (wave 54 withdrew its 7 CBB-android keys at wave54-probe): plan-skeptic round 1 (instrument-teeth M1) '
                    'added the green-mass reason after the bbox reason — |strict-green px − the ref\'s| ≤ max(40, 0.5 %), so a hollow box (396 px), two corner '
                    'pixels or a 30-px gap inside the right bbox is WRONG; round 2 (M2) REPLACED it with `mask:` — |green XOR ref| ≤ 40 px pixelwise (passing ≤ 28; '
                    'a 7×7 hole 49-77, a moved corner radius 283-339) — and the fail ink bound 2 % of the mass with ±2 px (passing 0; a 2×100 sliver 200); the 3 '
                    'android *-rtl rows expect U1\'s measured residue line (report), the 3 iOS *-rtl rows that line verbatim (control) — the bbox reason still '
                    'comes first, so both strings are unchanged'),
          onWave54Final='7 gating FAIL; 20 controls PASS; the 3 report keys FAIL against the U1 line (htb-rtl (66, 88, 135, 167), vlr/vrl (36, 108, 105, 187))',
          onWave54Probe='U1\'s 6 gating keys PASS; clip-3 FAILs (green (36, 36, 215, 115)): on the old unit\'s own picture the gate names U2; the 3 report keys print exactly the U1 line'),
    probe(CBB2, {NB_KEY('-3', p): GEO_OK for p in PLATS}, {NB_KEY('-3', p): f'{NBR["-3"]} {p}' for p in PLATS},
          gating={NB_KEY('-3', 'android'): 'U2'}, requires={NB_KEY('-3', 'android'): ['U1', 'U2']},
          teeth=('adds `bands:` and `corner:` to the bbox rule: rejects F3 centered / F4 unclipped / F6 double (F4 and F6 PASS the re-used rule at P 0.9841 / 0.9956), accepts the U1+U2 replay; wave54-probe (U1 only) FAILs (probes-selfcheck.txt §L2). '
                 'Round 1 (instrument-teeth M2): `corner:` counts all FOUR 20×20 corner squares (ref [251, 250, 252, 252], each ±8) and the green mass is read — '
                 'gut-cross (957 px) and square-3-corners ([400, 400, 400, 254]) are WRONG (probes-selfcheck.txt §T). Round 2 (M2): the mass is REPLACED by `mask:` '
                 '(|green XOR ref| ≤ 40 px pixelwise) and the fail ink (pink / red) must equal the ref\'s ±2 — a 2×40 pink sliver above row 80 and a 7×7 hole clear of the scanlines are WRONG. '
                 'Round 3 (verify M2, D38; both L2 probes): `core:` — green outside the ref mask\'s 1-px AA band ≤ 1 and ref green missing inside it ≤ 1: a blob in a rounded corner\'s cut-out and a 3×3 hole under the 40-px XOR are WRONG (probes-teeth-r3.py). '
                 'Round 6 (verify-r5 M1, D40): the RESIDUAL read on all eight L2 keys — outside strict green ∪ the backdrop pink (capture ∪ base; the autopos rows green ∪ red) and the recorded windows (the corner arcs\' anti-aliasing; clip-3\'s #middle band, derived from the U2 replay\'s own diff) the capture = the base, every changed px inside them strict green, or in a window a flat run colour / an arc pixel: a 50 % second box (+10000 px), black / #66ccff / #cccccc / yellow specks and lines anywhere are WRONG (probes-teeth-r5.py; probes-selfcheck.txt §T5)')),
  ],
  'revertUnits': {
    'U1': {'commit': 'CBB-android re-landed (5b333c8a, the revert of 8deddc19): NEW core/variables/ContainingBlockBands.kt + DynamicValueResolver.kt (defaulted wptCaptureMode + one condition) + DynamicValueResolverTest rows (census path → wave54-final; C8: + the `ContainingBlock[dark-stage-content-box-bands]` breadcrumb-assertion row, mutation: delete its markUnhandled) + tools/titan/results/wave55-cbb-android/seam-1.patch (ComponentRenderer.kt :1950-1951 `wptCaptureMode = wptCaptureModeForStretch`, patch-borne ChildContainingBlockSeamWiringTest.kt)',
           'captures': caps(android=l2_stems), 'wire': [], 'revertOrder': ['U2', 'U1']},
    'U2': {'commit': 'NEW sizing/PercentContentBoxBand.kt (owns / targetPx / percentFillPlusBand, breadcrumbs) + SizingApplier.kt two `else if` call sites (after :123 / :132) + NEW PercentContentBoxBandTest.kt (P-U2-1..6, mutations M-U2a..f)',
           'captures': caps(android=l2_u2), 'wire': [], 'revertOrder': ['U2', 'U1']},
  },
  'pair': 'U1 never without U2 and U2 never without U1 (cbb-android §4, R3; BACKLOG 0(g)): land U1 → U2, revert U2 → U1 always together',
  'stage1Decision': ('at wave55-pre (union carriers): (1) compose-wpt-content-box-cb.geometry.py — the 4 nested-clip android lines end in GEOMETRY OK '
                     'with their contains strings (the autopos rows are in css-flexbox, also in the stage-1 union: their 3 gating keys are READ, not '
                     'MISSING); (2) cbb-android.geometry.py clip-3 android OK; (3) clip-3 android ≥ 0.95 (≈0.999), clip / -2 / -4 ≈1; (4) filter-effects '
                     'web and iOS decoded-pixel identical to wave55-open; Android changes only on the union carriers (L2 nested-clip ×4, L6 clip-rect / '
                     '-zoom) and L6\'s may-move stems. A FAIL on read 1 or 2 reverts U2, then U1 (the pair)') + R6_STAGE1,
}

# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# L3 · positioned-parent-slot — U1 composed-capture engagement (LTR parents), U2 RTL physical-left origin.
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
_e = {l.split(':')[0].strip(): l.split(':', 1)[1].split() for l in section_lines('positioned-parent-slot.census.out.txt', '== (E)', None) if ':' in l}
l3_u1 = sorted(_e['U1 carriers (STACK)'][1:]); l3_u1_ctl = sorted(_e['U1 controls (identity/CLAMP/AUTO only)'][1:]); l3_u2 = sorted(_e['U2 carriers'][1:])
assert len(l3_u1) == 3 and len(l3_u1_ctl) == 13 and len(l3_u2) == 7 and len(set(l3_u1) | set(l3_u2)) == 9, (l3_u1, l3_u2)
DS2A, BTAB = T('selectors', 'dir-style-02a'), T('css-text', 'bidi', 'bidi-tab-001')
DOF = {k: T('css-writing-modes', f'dynamic-offset-{k}') for k in ('rtl-001', 'rtl-002', 'vrl-rtl-001', 'vrl-rtl-002', 'vrl-001')}
MCN, MC4 = T('css-anchor-position', 'anchor-position-multicol-nested-001'), T('css-anchor-position', 'anchor-position-multicol-004')
assert set(l3_u1) == {stem(DS2A), stem(MCN), stem(MC4)} and set(l3_u2) == {stem(DS2A), stem(BTAB), stem(HC[5])} | {stem(DOF[k]) for k in ('rtl-001', 'rtl-002', 'vrl-rtl-001', 'vrl-rtl-002')}
# The 40 host-ACTIVE LTR documents mounted today (positioned-parent-slot.md §6 "must not move"), from the census parents.
l3_active = sorted({p['stem'] for p in l3c['parents'] if p['hostActivates'] and not p['boxBranch'] and not p['composeRtl']})
assert len(l3_active) == 40, len(l3_active)
PPS = f'{PY} {P55}/positioned-parent-slot.geometry.py <run> <base>'
# [C] identity controls: the name the probe prints → its test. C6: anchor-center-safe-rtl is DROPPED (unscored, L6 may-move:
# an L6 band move would read as an L3 control failure); the probe still prints it.
L3_C = {n: resolve(n) for n in ('bidi-lines-002', 'boundary-shaping-009', 'css-tags-paint-order', 'css-tags-paint-order-with-entry',
                                'dir-selector-change-003', 'dir-selector-change-004', 'block-end-aligned-abspos-nested',
                                'position-absolute-scrollable-overflow-zero', 'text-decoration-subelements-002', 'attachment-local-positioning-3',
                                'attachment-local-positioning-4', 'composited-filters-under-opacity', 'css-transform-scale-002',
                                'conic-gradient-line-height-relative-units-003', 'conic-gradient-line-height-relative-units-004', 'dynamic-offset-vrl-001')}
L3_C_DROPPED = {'[C] anchor-center-safe-rtl android': 'C6: unscored and in L6\'s may-move set — an L6 band move would read as an L3 control failure; attributed by the drop-L6-U2 arm instead'}
l3_keys = {
  **{f'[S] dir-style-02a {p} {r}': f'{DS2A} {p}' for p in ('web', 'android') for r in ('row6', 'row7', 'row9')},
  **{f'[S] bidi-tab-001 {p} rtl-rows': f'{BTAB} {p}' for p in ('web', 'android')},
  **{f'[W] anchor-position-multicol-nested-001 {p} stack': f'{MCN} {p}' for p in ('web', 'android')},
  **{f'[S] dynamic-offset-{k} {p}': f'{DOF[k]} {p}' for k in ('rtl-001', 'vrl-rtl-001') for p in ('web', 'android')},
  **{f'[W] dynamic-offset-{k} {p}': f'{DOF[k]} {p}' for k in ('rtl-002', 'vrl-rtl-002') for p in ('web', 'android')},
  **{f'[C] {n} android': f'{t} android' for n, t in L3_C.items()},
  '[C] dir-style-02a android unaffected-rows': f'{DS2A} android', '[C] dir-style-02a web': f'{DS2A} web', '[C] dir-style-02a ios': f'{DS2A} ios',
  '[C] bidi-tab-001 android ltr-rows': f'{BTAB} android', '[C] bidi-tab-001 web': f'{BTAB} web', '[C] bidi-tab-001 ios': f'{BTAB} ios',
  '[C] anchor-position-multicol-004 android red': f'{MC4} android',
}
l3_gating = {'[S] dir-style-02a android row6': 'U2', '[S] dir-style-02a android row7': 'U1', '[S] dir-style-02a android row9': 'U2',
             '[S] bidi-tab-001 android rtl-rows': 'U2', '[W] anchor-position-multicol-nested-001 android stack': 'U1',
             '[S] dynamic-offset-rtl-001 android': 'U2', '[W] dynamic-offset-rtl-002 android': 'U2'}
L3 = {
  'dir': 'tools/titan/results/wave55-positioned-parent-slot',
  'briefs': ['positioned-parent-slot.md'],
  'ownership': {'owned': ['NEW runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt (U1; U2 adds its RTL branch)',
                          'NEW runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOrigin.kt (U2)',
                          'runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentFlowSlot.kt (U1 comment; U2 defaulted rtlOrigin + one .then)',
                          'NEW …/test/…/layout/position/PositionedParentSlotGateTest.kt (U1)', 'NEW …/test/…/layout/position/PositionedParentRtlOriginTest.kt (U2)',
                          'runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/SeamReachabilityTest.kt (U1 line patch-borne in seam 1; U2 line in U2\'s commit, directly under it; C10 / round 1 M1: both cut on HEAD at the PINNED anchor — after `"transforms/TransformInheritance.kt",` (:182))',
                          'NEW (patch-borne, seam 1) …/core/renderer/PositionedParentSlotSeamWiringTest.kt', 'tools/titan/results/wave55-positioned-parent-slot/'],
                'seams': ['ComponentRenderer.kt seam 1 (:3633-3661 outOfFlowSlotMount; seam-registry §1 order 4)'],
                'readOnly': ['CanvasRootHoist.kt (reserved to L3, no hunk)', 'PositionedParentFlowSlotTest.kt (stays green)', 'PositionApplier.kt',
                             'PositionedAncestorAnchor.kt', 'every web / iOS file']},
  'captureCarriers': caps(android=sorted(set(l3_u1) | set(l3_u2))),
  'wireCarriers': [],
  'carrierRule': {
    'U1': 'engage = hostActive || (composedCapture && !parentRtl): every host-inactive Column-loop parent of a composed capture is mounted (53 parents / 17 documents reached); the carriers are the STACK class (an out-of-flow child after one of non-zero height): 3 documents; 13 reached documents are identity / CLAMP / AUTO controls (positioned-parent-slot.census.out.txt (E))',
    'U2': 'an RTL parent (LocalLayoutDirection Rtl) with a px Width whose child resolves an inline inset gets the physical-left origin (any host state): 11 parents / 7 documents (7 host-inactive, 4 active)',
  },
  'stage1Sections': ['selectors', 'css-text', 'css-writing-modes'],
  'probeSections': ['selectors', 'css-anchor-position', 'css-text', 'css-writing-modes', 'css-backgrounds', 'css-break', 'css-position',
                    'css-text-decor', 'css-view-transitions', 'css-transforms', 'css-images', 'css-counter-styles'],
  'predictions': [
    pred(f'{DS2A} android', 'f 0.9425', 'P ≈0.987 (replay U1+U2 0.9866, no veto)', 'flip (row 7\'s dot by U1; rows 6 / 9 un-mirrored by U2)', 'MED-HIGH', PASS, True,
         ['U1', 'U2'], GEO_OK,
         fallbackIfReverted={'U2': 'f ≈0.943 (replay U1 0.9428), gated by `[S] … row7` only, floor n/a (stays f) — RESTATE it with this text and a PLAN §10 why'}),
    pred(f'{BTAB} android', 'P 0.9805', 'P ≈0.990 (replay 0.9901)', 'DEGENERATE → faithful (the four RTL rows\' `0` on the ref x)', 'MED-HIGH', PASS, True, ['U2'], GEO_OK),
    pred(f'{DOF["rtl-001"]} android', 'P 0.9952', 'P ≈1 (replay 1)', 'DEGENERATE → faithful (magenta inside the yellow box)', 'MED-HIGH', PASS, True, ['U2'], GEO_OK),
    pred(f'{DOF["rtl-002"]} android', 'P 0.9953', 'P ≈0.9956 (web\'s picture)', 'flat — DEGENERATE even if P: the ref is post-script and the wire lacks the scripted inset',
         'MED', PASS, True, ['U2'], GEO_OK),
    pred(f'{DOF["vrl-rtl-001"]} android', 'P 0.9952', 'P ≈1 (replay 1)', 'DEGENERATE → faithful (report key: the vertical-rl path is not traced, R2)',
         'MED', PASS, True, ['U2'], 'GEOMETRY OK (report key)'),
    pred(f'{DOF["vrl-rtl-002"]} android', 'P 0.9952', 'P ≈0.9955 (web\'s picture)', 'flat — DEGENERATE even if P: the ref is post-script and the wire lacks the scripted inset',
         'MED', PASS, True, ['U2'], 'GEOMETRY OK (report key)'),
    pred(f'{MCN} android', 'f 0.9573', 'f ≈0.969 (replay 0.9688; the coverage veto holds: ratio 2.07 > 2) — NOT a flip', 'mover up, stays f (mechanism gated by `[W] … stack`)',
         'MED', None, False, ['U1'], GEO_OK, floorNA=FLOOR_NA),
    pred(f'{MC4} android', 'P 0.9686', 'P, |Δ| ≤ 0.01 (red unchanged)', 'LOW mover — DEGENERATE even if P: its red stays visible', 'LOW', None, False, ['U1'],
         direction='undirected', directionWhy='where the mounted green children land inside Compose\'s multicol clipping was not determined (R6); rule 1 (P → f) still binds it'),
    pred(f'{HC[5]} android', 'f 0.9474', 'f, undirected (its kashida run moves 4.5 px left)', 'LOW mover', 'LOW', None, False, ['U2'], floorNA=FLOOR_NA,
         direction='undirected', directionWhy='the score direction of a 4.5-px kashida-run shift was not determined (R not-determined 4)'),
  ],
  'mustNotMove': sorted(set(cells_of([test_of(s) for s in l3_u1_ctl], ('android',)) + cells_of([test_of(s) for s in l3_active], ('android',))
                            + cells_of([DS2A, BTAB], ('web', 'ios')) + [k for k in CELLS if k.startswith('selectors/')])),
  'geometryProbes': [
    probe(PPS, {k: GEO_OK for k in l3_keys}, l3_keys, gating=l3_gating,
          requires={k: ['U1', 'U2'] for k, u in l3_gating.items() if u == 'U2'},
          report=['[S] dynamic-offset-vrl-rtl-001 android', '[W] dynamic-offset-vrl-rtl-002 android'],
          contains={k: 'identical to <base>' for k in l3_keys if k.startswith('[C]')},
          dropped=L3_C_DROPPED,
          neverShip='U1 without the RTL guard ("PLAIN", the BACKLOG\'s literal "drop the LocalActive gate"): dir-style-02a passes DEGENERATE at 0.9663 with rows 6 / 9 blank and bidi-tab-001 drops to 0.977 — rows 6 / 9 print `no ink in the row`',
          onWave54Final='all 7 gating WRONG, both report WRONG, every control OK (wave54-final wave54-final, exit 0)',
          teeth=('--replay U1U2 OK on every android gating key; U1 OK only on row7 + stack; U2 OK only on the dynamic-offset rows; PLAIN / today / iOS-mirrored / U1-only / bidi-tab PLAIN / stacked squares / mounted-mirrored all rejected; wave54-open → wave54-final turns 2 identity controls WRONG (the controls can fail). '
                 'Round 1 (instrument-teeth M3): dir-style rows read the ink-column profile (peak ≥ 0.75 at shift 0 ±1), bidi-tab each band\'s blue px ±20 % and left edge ±1, stack the 1-px ring outside each cell = web\'s ±4, '
                 '[S] dynamic-offset the magenta count ±5, [W] dynamic-offset the full bbox = web\'s ±1 and 100 ±5 px (vrl-rtl twins too) — gut / mirror / ±2-3x / grown / slab / stacked / hollow / ±3y / wider are WRONG (probes-selfcheck.txt §T). '
                 'Round 2 (M1): dir rows `rows:` (the ink-row profile peaks at shift 0 ±1: text ±2y / upside down WRONG), bidi `order:` (each RTL band\'s blue column profile at shift 0 ≥ 0.80: a mirrored run WRONG) and '
                 '`boxes:` (the yellow inline-blocks = the ref ±1: erased WRONG), stack `red:` (0) and `others:` (green outside the cells = web ±10 %: the other squares erased WRONG), '
                 'dynamic-offset `parent:` (the yellow parent bbox = the ref / web ±1 and yellow + magenta inside it ±1 %: erased / +3x WRONG; vrl-rtl twins too). '
                 'Round 3 (verify M2, D38): dir rows `rows:` peak ≥ 0.97, `mass:` text ink = the base\'s row 3 (rows 6 / 9) / row 7 ±2, `lead:` the dot ±1, `yext:` the ink rows ±1; bidi `ink:` per RTL band against the base\'s blue components; '
                 'stack `others:` = web ±2 — row ghosts, horizontal copies, doubled dots / glyphs and 2×2 / 3×3 strays are WRONG (probes-teeth-r3.py; probes-selfcheck.txt §T3). '
                 'Round 6 (verify-r5 M2, D40): the RESIDUAL read — bidi outside the blue runs and the recorded \'0\'-glyph windows, stack outside the green squares, dyn outside the magenta child (capture ∪ base) = the base; a vacated px beside class ink that stayed is a hole; '
                 'the dyn magenta count ±2 (was ±5: a 2×2 of the parent\'s yellow over the child equals the base there) — faint run / parent / square copies (+216 … +9900 px), black strays on the stack picture and yellow over a run box are WRONG (probes-teeth-r5.py); the dir rows 6 / 7 / 9 read their three row bands (lime / blue text anti-aliasing only)')),
  ],
  'revertUnits': {
    'U1': {'commit': 'NEW layout/position/PositionedParentSlotGate.kt (engages + slotModifier + breadcrumbs) + PositionedParentFlowSlot.kt :87-89 comment + NEW PositionedParentSlotGateTest.kt + tools/titan/results/wave55-positioned-parent-slot/seam-1.patch (ComponentRenderer.kt :3633-3661 → PositionedParentSlotGate.slotModifier(…), patch-borne PositionedParentSlotSeamWiringTest.kt and the SeamReachabilityTest U1 line, inserted after `"transforms/TransformInheritance.kt",` (:182), cut on HEAD)',
           'captures': caps(android=l3_u1), 'wire': [], 'revertOrder': ['U2', 'U1']},
    'U2': {'commit': 'NEW layout/position/PositionedParentRtlOrigin.kt + the gate\'s RTL branch + PositionedParentFlowSlot.mount(…, rtlOrigin = false) + NEW PositionedParentRtlOriginTest.kt + the SeamReachabilityTest U2 line (directly under U1\'s line at the :182 anchor)',
           'captures': caps(android=l3_u2), 'wire': [], 'revertOrder': ['U2'],
           'dependsOn': 'U1 (U2 is the RTL branch of U1\'s gate file): a U1 revert reverts U2 first (U1 revertOrder [U2, U1]); U2-only rows list U2 alone, their gating keys require U1 + U2'},
  },
  'neverShip': 'PLAIN (U1 without the `!parentRtl` guard): a pre-registered DEGENERATE pass',
  'stage1Decision': ('at wave55-pre (union carriers): positioned-parent-slot.geometry.py wave55-pre wave55-open — `[S] dir-style-02a android row6 / row7 / '
                     'row9 → GEOMETRY OK`; `[C] dir-selector-change-003 / -004 android`, `dir-style-02a android unaffected-rows`, web, ios each `identical '
                     'to wave55-open → GEOMETRY OK`; dir-style-02a android ≥ 0.95 (≈0.987); every other ANDROID selectors capture outside the union '
                     'carriers byte-identical (L6 may-move nth-child-of-attr-largedom android: |Δ| ≤ 0.002). css-text (bidi-tab-001 rtl-rows) and css-writing-modes '
                     '(dynamic-offset-rtl-001 / -002) are in the stage-1 union (the audit\'s extension): their U2 keys are READ at wave55-pre. Row 6 / 9 / '
                     'rtl-rows / dynamic-offset not OK, or the cell < 0.95 → revert U2; row 7 not OK, or an Android selectors capture outside the union '
                     'carriers moved and the rule-5 bisect lands on U1 / U2 → revert U2, then U1; a web / iOS selectors move is never L3\'s (rule 5 '
                     'bisects it; round 2, generator-truth S1). css-anchor-position (`[W] … stack`) is first read at wave55-probe') + R6_STAGE1,
}

# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# L4 · ua-heading-margin — U1 the UA margin of a positioned heading (both natives); U2 the held folded-host face.
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
INS = {n: T('css-text-decor', f'text-decoration-inset-{n}') for n in ('001', '002', '003', '004', '005', '006', '007', '008', '009', '011', '012',
                                                                       '013', '014', '015', '016', '024')}
BII = T('css-break', 'block-in-inline-015-print')
S005, S006 = T('CSS2', 'css21-errata', 's-11-1-1b-005'), T('CSS2', 'css21-errata', 's-11-1-1b-006')
_l4_carrier_lines = section_lines('ua-heading-margin.census.out.txt', 'CARRIER STEMS', 'U2 RADIUS')
l4_fire = sorted(l.strip().split('/')[1] for l in _l4_carrier_lines if l.strip())
assert l4_fire == sorted(stem(t) for t in (S005, T('css-anchor-position', 'anchor-center-safe-rtl'), INS['005'], INS['006'], INS['011'], INS['014'])), l4_fire
L4_CAR = [INS[n] for n in ('005', '006', '011', '014')]       # the heading scope keeps 4 of the 6 firing documents
L4_U2 = [INS[n] for n in ('005', '006', '014')]               # 011 stands down (Bailed) — byte-identical under U2
l4_h1_docs = sorted({r['key'].replace('css/', '', 1) for r in l4c['rows'] if any(h['tag'] == 'h1' and h['pos'] == 'ABSOLUTE' for h in r['hits'])})
assert len(l4_h1_docs) == 16 and set(L4_CAR) <= set(l4_h1_docs), l4_h1_docs
L4_ANCHORS = [INS[n] for n in ('001', '002', '003', '004', '009', '015', '016', '024', '007', '008', '012', '013')]
UHM = f'{PY} {P55}/ua-heading-margin.geometry.py <run> --base <base>'     # --base: the crop identity read (round 2, M4)
ik = lambda tag, n, p: f'[{tag}] text-decoration-inset-{n} {p}'
l4_keys = {**{ik('M', n, p): f'{INS[n]} {p}' for n in ('005', '006', '014', '011') for p in PLATS},
           **{ik('F', n, p): f'{INS[n]} {p}' for n in ('005', '006', '014') for p in PLATS},
           **{f'[C] text-decoration-inset-{n} {p}': f'{INS[n]} {p}' for n in ('001', '002', '003', '004', '009', '015', '016', '024') for p in ('ios', 'android')},
           **{f'[C] block-in-inline-015-print {p}': f'{BII} {p}' for p in ('ios', 'android')},
           **{f'[C] s-11-1-1b-005 {p}': f'{S005} {p}' for p in ('ios', 'android')}}
l4_gating = {**{ik('M', n, 'ios'): 'U1-ios' for n in ('005', '006', '014', '011')}, **{ik('M', n, 'android'): 'U1-android' for n in ('005', '006', '014', '011')},
             **{ik('F', n, 'ios'): 'U2-ios' for n in ('005', '006', '014')}, ik('F', '014', 'android'): 'U2-android'}
l4_requires = {**{ik('F', n, 'ios'): ['U1-ios', 'U2-ios'] for n in ('005', '006', '014')}, ik('F', '014', 'android'): ['U1-android', 'U2-android']}
UAF = f'{PY} {P54}/ua-heading-face.geometry.py <run>'
uaf_keys = {**{f'{t} {p}': f'{T("css-break", t) if t.startswith("block") else T("css-text-decor", t)} {p}'
               for t in ('block-in-inline-015-print', 'text-decoration-inset-005', 'text-decoration-inset-006', 'text-decoration-inset-014') for p in PLATS},
            'text-decoration-inset-011 web': f'{INS["011"]} web'}
L4 = {
  'dir': 'tools/titan/results/wave55-ua-heading-margin',
  'briefs': ['ua-heading-margin.md'],
  'ownership': {'owned': ['NEW runtimes/compose/…/spacing/UaPositionedHeadingMargin.kt + NEW …/spacing/UaPositionedHeadingMarginTest.kt',
                          'NEW (patch-borne, seam 1) …/spacing/UaPositionedHeadingMarginSeamWiringTest.kt',
                          '…/typography/UAElementFontRuleSeamWiringTest.kt (patch-borne edits only: seam 1 handoff token, seam 3 fontStep token)',
                          '…/typography/UAElementFontRule.kt (U1 header sentence :26-27; U2 kdoc: headingStandsDown KEPT — superseded at seam 3, no caller after it; deletion queued, PLAN §10 item 7)', '…/typography/UAElementFontRuleTest.kt (U2 retargets)',
                          'RESTORED …/typography/UAHeadingFoldGate.kt + UAHeadingFoldGateTest.kt (held copies, SHA256SUMS 12/12)',
                          'NEW runtimes/swiftui/…/StyleEngine/spacing/UAPositionedHeadingMargin.swift + Tests/…/UAPositionedHeadingMarginTests.swift',
                          'NEW (patch-borne, seam 2) Tests/…/UAPositionedHeadingMarginSeamWiringTests.swift',
                          'StyleEngine/typography/UAElementFontRule.swift (docs only)', 'Tests/…/UAElementFontRuleTests.swift (U2 held diff)',
                          'RESTORED StyleEngine/typography/UAHeadingFoldGate.swift + Tests/…/UAHeadingFoldGateTests.swift (held copies)',
                          'SeamReachabilityTest.kt module lines for UaPositionedHeadingMargin.kt (seam 1) and UAHeadingFoldGate.kt (seam 3, directly under it), patch-borne (C10), cut on HEAD at the PINNED anchor after `"content/PseudoGeneratedBox.kt",` (:159) (round 1 M1)',
                          'tools/titan/results/wave55-ua-heading-margin/'],
                'seams': ['ComponentRenderer.kt seam 1 (:1157-1165, U1-android) and seam 3 (:1161, U2-android): apply 1 → 3, revert 3 → 1',
                          'ComponentRenderer.swift seam 2 (:340-356, U1-ios) and seam 4 (:352-356, U2-ios): apply 2 → 4, revert 4 → 2'],
                'readOnly': ['UaBlockMarginFontBasis.{kt,swift}', 'UaBlockChildMargins.kt', 'UABlockMargin.swift', 'MarginCollapsePlanner.swift', 'ComposedRootStack.swift',
                             'MonospaceUAFontSize.{kt,swift}', 'DynamicValueResolver.kt (L2\'s: re-run the pins if L2 changes fontSizePxOf semantics)',
                             'InlineRunFold.kt / InlineRunFlow.swift / InlineSpanRing.{kt,swift}', 'layout/position/** (L3)', 'LineBoxMetrics.swift (L1)']},
  'captureCarriers': caps(ios=[stem(t) for t in L4_CAR], android=[stem(t) for t in L4_CAR]),
  'wireCarriers': [],
  'carrierRule': {
    'U1': 'UaPositionedHeadingMargin: WPT capture, tag h1-h6, own Position ABSOLUTE|FIXED, ≥1 undeclared block edge, horizontal writing mode → MarginTop / MarginBottom = uaBlockMarginEm(tag) × the UA-cascade face: fires on 7 components in 6 documents, the heading scope keeps 4 documents (inset-005 / -006 / -011 / -014); the 12 declared-margin abspos <h1> documents are identity (author wins per edge); the out-of-scope abspos <p> documents (s-11-1-1b-005, anchor-center-safe-rtl; -006\'s hoisted root is T6\'s, not U1\'s — round-2 generator-truth N2) are logged once',
    'U2': 'UAHeadingFoldGate.standsDown: a heading with children stands down iff its runs do not fold — exactly 4 hosts; 005 / 006 / 014 Folded (the face applies), 011 Bailed (identical under U2)',
  },
  'stage1Sections': ['css-text-decor'],
  'probeSections': ['css-text-decor', 'css-break', 'CSS2'],
  'predictions': [
    pred(f'{INS["005"]} ios', 'f 0.8995', 'P ≈0.966 (arm C 0.9976 minus the iOS raster allowance: 0.9631-0.9695)', 'flip (margin + 2em face + the iOS wrap)', 'MED', PASS, True,
         ['U1-ios', 'U2-ios'], GEO_OK),
    pred(f'{INS["006"]} ios', 'f 0.8987', 'P ≈0.966 (0.9632-0.9696)', 'flip', 'MED', PASS, True, ['U1-ios', 'U2-ios'], GEO_OK),
    pred(f'{INS["014"]} ios', 'f 0.9238', 'P ≈0.967 (0.9635-0.9699; no monospace anchor, R6)', 'flip', 'MED-LOW', PASS, True, ['U1-ios', 'U2-ios'], GEO_OK),
    pred(f'{INS["014"]} android', 'f 0.923', 'P ≈0.985 (0.9832-0.9861)', 'flip (definite 16ch width: the wrap is right)', 'MED', PASS, True, ['U1-android', 'U2-android'], GEO_OK),
    # C11: [M]-gated and predicted P → the 0.95 floor; the stayDegenerateEvenIfPass label still stops any claim.
    pred(f'{INS["005"]} android', 'f 0.9', 'P ≈0.967 (0.9655-0.9684)', 'flip in score only — DEGENERATE even if P: Compose measures the abspos heading unbounded, "fox" stays on line 1 clipped at x390 (R2); never claimed',
         'MED-LOW', PASS, True, ['U1-android', 'U2-android'], '[M] GEOMETRY OK; [F] report (1 heading line band vs ref 2)'),
    pred(f'{INS["006"]} android', 'f 0.8993', 'P ≈0.967 (0.9656-0.9685)', 'flip in score only — DEGENERATE even if P: no wrap (R2); never claimed',
         'MED-LOW', PASS, True, ['U1-android', 'U2-android'], '[M] GEOMETRY OK; [F] report (1 heading line band vs ref 2)'),
    pred(f'{INS["011"]} ios', 'f 0.6999', 'f ≈0.712 (arm M 0.7106-0.714)', 'mover up, stays f (the margin is a pure translation; the face stands down)', 'MED', None, False,
         ['U1-ios'], GEO_OK, floorNA=FLOOR_NA),
    pred(f'{INS["011"]} android', 'f 0.6852', 'f ≈0.691 (arm M 0.6896-0.6933)', 'mover up, stays f', 'MED', None, False, ['U1-android'], GEO_OK, floorNA=FLOOR_NA),
  ],
  'mustNotMove': sorted(set(cells_of(L4_ANCHORS, ('ios', 'android')) + cells_of(l4_h1_docs, ('web',)) + cells_of([BII, S005, S006], ('ios', 'android')))),
  'geometryProbes': [
    probe(UHM, {k: GEO_OK for k in l4_keys}, l4_keys, gating=l4_gating, requires=l4_requires,
          report=[ik('F', '005', 'android'), ik('F', '006', 'android')],
          onWave54Final='all 8 [M] native rows WRONG; all 6 [F] native rows WRONG; every [C], ref and web row OK (exit 0): gating 14 FAIL, report 2 FAIL, controls PASS',
          teeth=('--arm T / R (painted basis) / X2 (double margin) / B (face, no margin) / C4 (4 px low) all WRONG on [M]; U1 / C / D OK on [M] ×8; [F] OK only on C and D (D keeps 005 / 006 android WRONG: the report rows) — probes-selfcheck.txt §L4. '
                 'Round 1 (instrument-teeth M4 + S4): [M] with the 2em face reads the heading\'s band LIST against the ref (tops / bottoms ±1, left ±3, line-1 right ±12, same count; 005 / 006 android band 1 only: Compose does not wrap), '
                 'with today\'s face every band = wave54-final + the margin ±1 and x ±1; [F] gains the same band rule (tops ±1, was ±3) — +12x, +3y, −2y, line 2 removed and line 1 cut to 40 % are WRONG (probes-selfcheck.txt §T). '
                 'Round 2 (M4): today\'s face and 011 `crop:` (every band\'s ink = the BASE capture\'s band + the margin, correlation ≥ 0.97; --base), the 2em face and [F] `profile:` (line 1\'s '
                 'column profile = the ref\'s at |shift| ≤ 12, ≥ 0.95) — line 1 mirrored / thirds swapped and 011 band 1 mirrored are WRONG; a 10-px clip of line 1\'s last glyph still passes '
                 'while X1_TOL = 12 (S1: re-set at stage 1 from the device faces; probes-teeth-r2.py names it, §9 D33). '
                 'Round 3 (verify M2, D38): today\'s face / 011 `ink:` (each band\'s ink mask = the base band + the margin, XOR ≤ 2) and face-present `face ink:` (the ref\'s component count, dark px ±6 %). '
                 'Round 4 (verify M1, D39): face-present natives `face xor:` — every band\'s ink (two nested masks) = the face reference\'s at its best whole-px shift (±3 x, ±1 y), XOR ≤ 2 over the band\'s rows ±2 and the whole width; '
                 'the Compose no-wrap rows read every band when the picture wraps and, when it does not, line 1 left of the ref\'s right edge plus "fox" against the ref\'s line 2 moved there; the reference is the frozen ref until the stage-1 step-8 edit sets FACE_RUN = wave55-pre — '
                 '"brownn", the heading copied 1 px, "fox" copied 5 px right, round 3\'s named 2×2 blob on a 014 glyph and D33\'s clip-10 are WRONG (probes-teeth-r4.py). '
                 'Round 6 (verify-r5 M3, D40): the RESIDUAL read on all 12 keys — outside the dark bands\' rows ±2 (capture ∪ base, from y96 / 011 y112 to y399: the rows `face xor:` / `ink:` read) the capture = the base, the vacated rows white, every changed px inside a grey: a faint 3×3 below the heading and a faint or iOS-grey (127,127,127) 30-px line at y276-368 are WRONG (probes-teeth-r5.py)')),
    probe(UAF, {k: GEO_OK for k in uaf_keys}, uaf_keys,
          report=[f'text-decoration-inset-{n} {p}' for n in ('005', '006', '014') for p in ('ios', 'android')],
          dropped={f'text-decoration-inset-011 {p}': ('C4: superseded by `[M] text-decoration-inset-011 ' + p + '` (gating U1-' + p + '): U1 moves 011 by 21 px by design, '
                                                      'so this wave-54 CONTROL fails on L4\'s units (ua-heading-margin.wave54-probe-on-arms.out.txt)') for p in ('ios', 'android')},
          restated=('C4: the wave-54 probe restated — block-in-inline-015-print android was gating (wave-54 U1-android, shipped) → control; the '
                    '005 / 006 / 014 natives stay report (a second reader of [F]: under arm D the iOS three and 014 android print OK, 005 / 006 '
                    'android stay `1 heading line band(s) vs ref 2`); the inset-011 natives are DROPPED (`dropped`)')),
  ],
  'revertUnits': {
    'U1-android': {'commit': 'NEW spacing/UaPositionedHeadingMargin.kt + test + UAElementFontRule.kt (one doc line) + tools/titan/results/wave55-ua-heading-margin/seam-1.patch (ComponentRenderer.kt after :1162 + :1165 re-point; patch-borne UaPositionedHeadingMarginSeamWiringTest.kt, the UAElementFontRuleSeamWiringTest.kt handoff token, the SeamReachabilityTest module line after `"content/PseudoGeneratedBox.kt",` (:159), cut on HEAD)',
                   'captures': caps(android=[stem(t) for t in L4_CAR]), 'wire': [], 'revertOrder': ['U2-android', 'U1-android']},
    'U1-ios': {'commit': 'NEW StyleEngine/spacing/UAPositionedHeadingMargin.swift + tests + UAElementFontRule.swift (one doc bullet) + seam-2.patch (ComponentRenderer.swift mergedProperties :340-356: mergedPreUA hoist, fontProperties, UAPositionedHeadingMargin.apply; patch-borne UAPositionedHeadingMarginSeamWiringTests.swift)',
               'captures': caps(ios=[stem(t) for t in L4_CAR]), 'wire': [], 'revertOrder': ['U2-ios', 'U1-ios']},
    'U2-android': {'commit': 'RESTORED typography/UAHeadingFoldGate.kt + UAHeadingFoldGateTest.kt + UAElementFontRule.kt (headingStandsDown KEPT for rule 2b — the unit compiles without seam 3; its kdoc says superseded; deletion queued) + UAElementFontRuleTest.kt retargets + seam-3.patch (ComponentRenderer.kt :1161 standsDown = UAHeadingFoldGate.standsDown(component, listUaProperties); patch-borne fontStep token + the SeamReachabilityTest line directly under seam 1\'s at the :159 anchor)',
                   'captures': caps(android=[stem(t) for t in L4_U2]), 'wire': [], 'revertOrder': ['U2-android']},
    'U2-ios': {'commit': 'RESTORED StyleEngine/typography/UAHeadingFoldGate.swift + UAHeadingFoldGateTests.swift + UAElementFontRuleTests.swift (held diff) + UAElementFontRule.swift (docs) + seam-4.patch (ComponentRenderer.swift :352-356 on top of seam 2)',
               'captures': caps(ios=[stem(t) for t in L4_U2]), 'wire': [], 'revertOrder': ['U2-ios']},
  },
  'dryRuns': 'the lane proves both revert orders of seam 3 / seam 1 and seam 4 / seam 2 in a `git archive` export tree (ua-heading-margin R8; C12)',
  'stage1Decision': ('at wave55-pre (union carriers): ua-heading-margin.geometry.py — a gating [M] FAIL reverts U1-<p> and U2-<p>; a gating [F] FAIL reverts '
                     'U2-<p>; a fall ≥ 0.002 on a directed row reverts its unit; the [F] 005 / 006 android report rows decide nothing. Round 2 '
                     '(instrument-teeth S1): read `Δx1` on every face-present [M] / [F] native line and set X1_TOL = max |Δ| + 2 (today ±12, which '
                     'rests on WEB\'s face) by a probe edit committed with named paths and a regeneration BEFORE wave55-probe, recorded in §10 — '
                     'a decision on the face keys is taken only after that re-read. Then the probe-time A/B arm drop-L4-U2 (abArms) on '
                     'css-text-decor, read against arm M. css-break / CSS2 controls: read at wave55-probe. Round 4 (D39): a face-present native line '
                     'whose only reason is `face xor:` (against Chromium\'s ref: a device face sits 1768-2510 px level-XOR from it) is decided by the '
                     'recorded look of PLAN §7 stage 1 step 8, never by widening FACE_XOR_TOL; the same probe edit sets FACE_RUN_DEFAULT = \'wave55-pre\' '
                     'for the face keys kept') + R6_STAGE1,
}
if L4_GO_SMALL:      # D1's alternative: hold U2-android (no DEGENERATE pass carried); Android +0, the four Android rows rise f→f
    L4['heldUnits'] = {'U2-android': 'HELD (GO-SMALL, ua-heading-margin §4): the Compose relative-parent abspos shrink-to-fit is not on the tree, so 005 / 006 android would pass DEGENERATE'}
    del L4['revertUnits']['U2-android']
    L4['revertUnits']['U1-android']['revertOrder'] = ['U1-android']
    L4['predictions'] = [p for p in L4['predictions'] if not p['cell'].endswith(' android') or p['cell'].startswith(INS['011'])] + [
        pred(f'{INS[n]} android', CELLS[f'{INS[n]} android'], f'f ≈{v} (arm M, U1 alone), stays f', 'mover up, stays f (GO-SMALL: the face is held)', 'MED', None, False,
             ['U1-android'], GEO_OK, floorNA=FLOOR_NA) for n, v in (('005', '0.9121'), ('006', '0.9117'), ('014', '0.9328'))]
    L4['captureCarriers'] = caps(ios=[stem(t) for t in L4_CAR], android=[stem(t) for t in L4_CAR])
    _g = L4['geometryProbes'][0]
    _g['gating'].pop(ik('F', '014', 'android')); _g['requires'].pop(ik('F', '014', 'android')); _g['report'].append(ik('F', '014', 'android'))

# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# L5 · counter-suffix-rows — U1-ios marker isolate, U2o / U2i the U+3001 suffix gap (both natives), U3-ios tabular, D.
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
CS = T('css-counter-styles', 'counter-suffix')
CJK = {n: T('css-counter-styles', 'cjk-decimal', f'css3-counter-styles-00{n}') for n in (1, 4, 5)}
_u2 = section_lines('counter-suffix-rows.census.txt', '(U2) markers ending', '(U2-table)')
l5_u2_docs = sorted({re.search(r'(wpt__\S+?):', l).group(1) for l in _u2 if 'wpt__' in l})
assert l5_u2_docs == sorted([stem(CS)] + [stem(t) for t in CJK.values()]), l5_u2_docs
_u1 = section_lines('counter-suffix-rows.census.txt', '(U1-ios) markers', '(U2) markers')
assert any(stem(CS) in l for l in _u1) and any('total: 2 markers in 1 documents' in l for l in _u1)
_d = section_lines('counter-suffix-rows.census.txt', '(D) <li> under an inherited', None)
assert _d and '6 in 1 documents' in open(_track(os.path.join(HERE, 'counter-suffix-rows.census.txt'))).read()
CSR = f'{PY} {P55}/counter-suffix-rows.geometry.py <run> <base>'
csr_keys = {**{f'[{t}] counter-suffix {p}': f'{CS} {p}' for t in ('H', 'C', 'T', 'L', 'K') for p in PLATS},
            **{f'[I] cjk-decimal/css3-counter-styles-005 {p}': f'{CJK[5]} {p}' for p in PLATS}}
L5 = {
  'dir': 'tools/titan/results/wave55-counter-suffix-rows',
  'briefs': ['counter-suffix-rows.md'],
  'ownership': {'owned': ['NEW runtimes/swiftui/…/StyleEngine/lists/ListMarkerBidi.swift (U1-ios) + ListMarkerBidiTests.swift',
                          'NEW …/StyleEngine/lists/ListMarkerSuffixGap.swift (U2o-ios) + ListMarkerSuffixGapTests.swift', '…/lists/ListMarkerRow.swift (gapPt doc only)',
                          '…/typography/font-variant/FontVariantNumericApplier.swift + TypographyAggregate.swift (+1 field) + Renderer/StyleBuilder.swift (+2 lines) + FontVariantNumericTabularTests.swift (U3-ios)',
                          'NEW runtimes/compose/…/lists/ListMarkerSuffixGap.kt (U2o-android) + ListMarkerSuffixGapTest.kt', '…/lists/ListMarkerRow.kt (KDoc only)',
                          'D (comment-only): ListMarkerOutsideHang.swift / .kt, KoreanHangulFormal.swift, ListMarkerText.swift, ListMarkerStyle.swift, and (C7) tools/titan/counter-style-table.mjs:21-23 SUFFIX NOTE (after U2o)',
                          'SeamReachabilityTest.kt module line for lists/ListMarkerSuffixGap.kt, patch-borne in seam 4 (C10), cut on HEAD at the PINNED anchor after `"images/ImageCandidateChain.kt",` (:167) (round 1 M1)', 'tools/titan/results/wave55-counter-suffix-rows/'],
                'seams': ['ComponentRenderer.swift seam 1 (U1-ios :3973/:4561/:4602/:4626/:4650), seam 2 (U2o-ios :4618), seam 3 (U2i-ios :4646), seam 6 (U3-ios :5537, ≈:5538 after L1)',
                          'ComponentRenderer.kt seam 4 (U2o-android :4482), seam 5 (U2i-android :4665)'],
                'readOnly': ['ListMarkerOutsideHang geometry', 'ListMarkerTextStyle.kt', 'ScriptFallbackFonts.swift', 'InheritedText.swift',
                             'counter-style-bake.mjs / counter-style-author.mjs', 'bidi-marker-bake.mjs', 'every web file']},
  'captureCarriers': caps(ios=[stem(CS), stem(DISC)] + [stem(t) for t in CJK.values()], android=[stem(CS)] + [stem(t) for t in CJK.values()]),
  'wireCarriers': [],
  'carrierRule': {
    'U1-ios': 'a painted marker whose UAX #9 natural direction ≠ the item direction: 2 baked markers (counter-suffix א. / ב.) + the 6 rtl <li> of disclosure-styles (identity predicted: a single neutral glyph has no order)',
    'U2o/U2i': 'a marker string ending in U+3001: 36 markers in 4 documents (2 outside: counter-suffix; 34 inside: css3-counter-styles-001 / -004 / -005); the 71 unbaked U+3001-type <li> are NOT reached (the key is the string)',
    'U3-ios': ('a component declaring FontVariantNumeric: 6 components, all in counter-suffix (not inherited on either native); pre-landing '
               'regeneration (L5 fix-r1, skeptic defect 1): Font.monospacedDigit() turns on Inter\'s WHOLE `tnum` for the run, so inside its one '
               'capture U3-ios repaints 6 glyphs in 4 rows — row 9\'s \'1\' (one.tf), the periods of rows 9-12 (period.tf) and row 10\'s \'2\' '
               '(two.tf) (fix-r1.inter-tnum / u3-glyph-census / u3-boxes.measure); the capture set is unchanged (1)'),
    'D': 'comment-only (five stale comments + the counter-style-table.mjs SUFFIX NOTE): no build output changes',
  },
  'stage1Sections': ['css-counter-styles'],
  'probeSections': ['css-counter-styles'],
  'predictions': [
    pred(f'{CS} ios', 'P 0.9873', 'P ≈0.993 (replay, all three arms; U1 0.9897 · U2o 0.9903 · U3 0.9875 · U1+U2o 0.9927 — the replay grafted only '
         'row 9\'s \'1\': U3-ios\'s five other tabular glyphs, ≤ 0.31 px each toward the ref\'s advances, are not modelled (L5 fix-r1))',
         'picture-correctness (P→P) rows 3-6 and 9-12 — DEGENERATE even if P until [H] [C] [T] [K] ios print OK (retired then)', 'MED', PASS, True,
         ['U1-ios', 'U2o-ios', 'U3-ios'],
         'GEOMETRY OK from wave55-probe on; at wave55-pre [H] ios and [T] ios are predicted WRONG on the twin `ink:` read only (no same-rasterizer '
         'twin before stage 1) → the step-8 recorded look → TWIN_RUN_DEFAULT = \'wave55-pre\' (L5 fix-r1 R5)', requires=[],
         requiresWhy='additive: each arm alone replays up (0.9897 / 0.9903 / 0.9875), so the 0.95 floor is reachable with any subset; the magnitude needs all three'),
    pred(f'{CS} android', 'P 0.989', 'P ≈0.9924 (replay U2o)', 'picture-correctness (P→P) rows 5-6 — DEGENERATE even if P until [C] [K] android print OK (retired then)',
         'MED-HIGH', PASS, True, ['U2o-android'], GEO_OK),
    pred(f'{CJK[5]} ios', 'P 0.993', 'P ≈0.997 (replay 0.9971)', 'picture-correctness (P→P): the inside "、" marker flush', 'MED', PASS, True, ['U2i-ios'], GEO_OK),
    # D5: the brief pre-registers a FALL (Android's 2-4 px vertical offset and lighter CJK face dominate); rule 2 is read from it.
    pred(f'{CJK[5]} android', 'P 0.9759', 'P ≈0.974 (replay 0.9741: down 0.0018, pre-registered)', 'picture-correctness (P→P) with a pre-registered score fall',
         'LOW-MED', PASS, True, ['U2i-android'], GEO_OK, direction='down-preregistered', predictedDelta=-0.0018,
         directionWhy='D5: replay Δ −0.0018 (horizontally right; the vertical offset dominates the SSIM); rule 2 fires at Δ ≤ −0.0038; kept iff [I] android OK and ≥ 0.95'),
    pred(f'{DISC} ios', 'f 0.6127', 'f 0.6127, byte-identical (read the picture if it moves)', 'U1-ios identity carrier (6 `•` markers wrapped in RLI…PDI)', 'MED', None, False,
         ['U1-ios'], floorNA=FLOOR_NA),
  ] + [note(f'{CJK[n]} ios / android: unscored (scoreExcluded native-font-parity) — the item text moves −3 / −4 px under U2i (capture carriers only)', [f'U2i-{p}' for p in ('ios', 'android')])
       for n in (1, 4)],
  'mustNotMove': sorted(set([k for k in CELLS if k.startswith('css-counter-styles/') and k != f'{DISC} android']   # C5: disclosure android = L6 may-move
                            + cells_of([CS], ('web',)))),
  'geometryProbes': [
    probe(CSR, {k: GEO_OK for k in csr_keys}, csr_keys,
          gating={'[H] counter-suffix ios': 'U1-ios', '[C] counter-suffix ios': 'U2o-ios', '[C] counter-suffix android': 'U2o-android',
                  '[T] counter-suffix ios': 'U3-ios', '[I] cjk-decimal/css3-counter-styles-005 ios': 'U2i-ios',
                  '[I] cjk-decimal/css3-counter-styles-005 android': 'U2i-android'},
          requires={'[I] cjk-decimal/css3-counter-styles-005 ios': ['U2o-ios', 'U2i-ios'], '[I] cjk-decimal/css3-counter-styles-005 android': ['U2o-android', 'U2i-android']},
          report=['[L] counter-suffix ios', '[L] counter-suffix android'],
          onWave54Final='gating 6/6 FAIL · control 10/10 PASS · report 2/2 FAIL (base wave54-final, exit 0, every ref row OK)',
          teeth=('replay:<tag> — ok-all OK on every gating key; H-broken / H-nodot / H-flush / H-low / C-shift8 / C-nocomma / C-space-only / I-wholeline / K-leak each turn exactly their own key WRONG (probes-selfcheck.txt §L5). '
                 'Round 1 (instrument-teeth M5): [I] ≥ 2 marker blobs ending at the ref ±2 and the ideograph\'s fill ≤ the ref\'s + 0.25, [C] the ideograph width ±2, [T] the digit rows ±1 and fill ≤ 60 %, '
                 '[H] the letter fill ≤ 60 %, [K] the natives\' item-text box x64-389 × y64-159 — the 、 erased, a block marker, a gutted ideograph, a bar / low \'1\', block letters and erased item text are WRONG (probes-selfcheck.txt §T). '
                 'Round 2 (M3): [C] the ideograph\'s stroke count and ink rows = the ref\'s (rows 5 / 6 trading 一 / 二, or 3 px low, WRONG), [H] each letter nearest the SAME row\'s ref letter and the period\'s rows ±1 '
                 '(swapped letters / a low period WRONG), [T] and [K] ios row 9 outside the digit window = the base (erased / moved item text WRONG), [I] the stroke count = the ref\'s and the marker rows = the BASE\'s ±1. '
                 'Round 3 (verify M2, D38): [C] / [I] natives `ink:` against the base (rows 5-6 marker zone / 005 item zone translated, 005 marker zone unmoved, XOR ≤ 2). '
                 'Round 4 (verify M3 / M4, D39): every `ink:` read counts both nested masks; [H] natives — rows 3-4 x0-63 = a native\'s own two glyphs (iOS\'s, or Android\'s: the ok-all graft), each moved by whole px; '
                 '[T] natives — the digit window = Android\'s tabular \'1\' (TWIN_RUN = wave55-pre added from stage 1); [C] (x0-63) and [I] summed over their zones; [I] 005 outside its line windows = the base — '
                 'the [H] zone copied 1 px, a doubled-glyph sliver, the [T] window copied 1 px down and a 2×2 / 3×3 blob beside the \'1\' are WRONG (probes-teeth-r4.py). '
                 'Round 6 (D40): the RESIDUAL read on the 6 gating keys and the [K] controls — outside the [H] / [C] / [T] / [I] zones the capture = the base (the [K] pixel identity, now on every key), every changed px inside a grey; the round-5 sweep accepted nothing here (> 2 px) and still does not. '
                 'Pre-landing regeneration (L5 fix-r1 R1-R3; regen.l5-proof.out.txt): U3-ios\'s `tnum` is run-wide, so ios\'s base-identity zones skip the five other run boxes (rows 9-12 x130-135, row 10 x136-150) and [T] ios reads them — '
                 'one dot / one digit blob at the ref\'s place ±1 (fill ≤ 60 %) and the twin `ink:` summed with the digit window; the residual SPEC declares the two rects; the standing replays re-graft the boxes from Android\'s base '
                 '(regen.replay-regraft.py): the correct-U3 picture reads [T] ios `ink:` only without a decided twin and OK with it, [K] ios OK, residual 0 / 0 / 0; the lane\'s 11 wrong pictures (a period / the \'2\' erased, moved ±2, a block, '
                 'a 2×2 blob, copied 1 px down, the row-11 letter erased) are each rejected; every committed replay\'s verdicts are unchanged')),
    probe(f'{PY} {P54}/rtl-marker-bake.geometry.py <run> <base>',
          {f'[M] counter-suffix {p}': GEO_OK for p in PLATS}, {f'[M] counter-suffix {p}': f'{CS} {p}' for p in PLATS},
          contains={'[M] counter-suffix web': 'rows 0-207 identical to <base>'},
          restated=('C4: the wave-54 [M] keys (gating Mprime, shipped) → CONTROLS on the RTL rows 9-12; `rows 0-207 identical to <base>` DROPPED on ios / '
                    'android (L5 U1 / U2o move rows 3-6 / 5-6 by design; L5\'s [K] replaces it), KEPT on web (no web unit). Executed on L5\'s ok-all replay '
                    'pixels: the three verdicts stay OK (probes-selfcheck.txt §C4). The [P] bidi-lines rows are not registered (no wave-55 carrier; R4 guards them)')),
    probe(f'{PY} {P53}/lists-bakes.geometry.py <run> <base>',
          {f'counter-suffix {p}': GEO_OK for p in PLATS}, {f'counter-suffix {p}': f'{CS} {p}' for p in PLATS},
          contains={'counter-suffix web': 'rows 0-207 identical to <base>'},
          restated='C4: as rtl-marker-bake — the wave-53/54 counter-suffix keys become controls, the rows-0-207 clause kept on web only; counter-reset-reversed-nested is not registered (css-lists, no wave-55 carrier)'),
  ],
  'revertUnits': {
    'U1-ios': {'commit': 'NEW lists/ListMarkerBidi.swift + ListMarkerBidiTests.swift + seam-1.patch (ComponentRenderer.swift markerPlacement itemIsRTL + `painted` at :4602 / :4626 / :4650)',
               'captures': caps(ios=[stem(CS), stem(DISC)]), 'wire': [], 'revertOrder': ['U1-ios'],
               'dryRun': 'C12: revert U1-ios with U2i-ios applied (:4646 / :4650 neighbourhood) in an export tree before stage 1'},
    'U2o-ios': {'commit': 'NEW lists/ListMarkerSuffixGap.swift + ListMarkerSuffixGapTests.swift + ListMarkerRow.swift (doc) + seam-2.patch (:4618)',
                'captures': caps(ios=[stem(CS)]), 'wire': [], 'revertOrder': ['D', 'U2i-ios', 'U2o-ios'],
                'revertOrderWhy': 'round 1 S1 (C7): unit D\'s SUFFIX NOTE is true only while U2o is on the tree, so a U2o revert reverts D first'},
    'U2i-ios': {'commit': 'seam-3.patch only (ComponentRenderer.swift :4646 HStack spacing → ListMarkerSuffixGap.points(forMarker:))',
                'captures': caps(ios=[stem(t) for t in CJK.values()]), 'wire': [], 'revertOrder': ['U2i-ios'],
                'dependsOn': 'U2o-ios (consumes ListMarkerSuffixGap): a U2o-ios revert reverts U2i-ios first'},
    'U2o-android': {'commit': 'NEW lists/ListMarkerSuffixGap.kt + ListMarkerSuffixGapTest.kt + ListMarkerRow.kt (KDoc) + seam-4.patch (ComponentRenderer.kt :4482, patch-borne SeamReachabilityTest line after `"images/ImageCandidateChain.kt",` (:167), cut on HEAD)',
                    'captures': caps(android=[stem(CS)]), 'wire': [], 'revertOrder': ['D', 'U2i-android', 'U2o-android'],
                    'revertOrderWhy': 'round 1 S1 (C7): unit D\'s SUFFIX NOTE is true only while U2o is on the tree, so a U2o revert reverts D first'},
    'U2i-android': {'commit': 'seam-5.patch only (ComponentRenderer.kt :4665 .padding(end = ListMarkerSuffixGap.gapDp(marker)))',
                    'captures': caps(android=[stem(t) for t in CJK.values()]), 'wire': [], 'revertOrder': ['U2i-android'],
                    'dependsOn': 'U2o-android (consumes ListMarkerSuffixGap): a U2o-android revert reverts U2i-android first'},
    'U3-ios': {'commit': 'FontVariantNumericApplier.swift + TypographyAggregate.swift (+tabularNums) + StyleBuilder.swift (+2 lines) + FontVariantNumericTabularTests.swift + seam-6.patch (labelFont: .monospacedDigit())',
               'captures': caps(ios=[stem(CS)]), 'wire': [], 'revertOrder': ['U3-ios']},
    'D': {'commit': 'comment-only: ListMarkerOutsideHang.swift (re-cut hunk-for-orchestrator-1.patch, its false sentence corrected) / .kt, KoreanHangulFormal.swift, ListMarkerText.swift, ListMarkerStyle.swift + (C7) tools/titan/counter-style-table.mjs:21-23 SUFFIX NOTE (after U2o: a U+3001 suffix has no stand-in gap)',
          'captures': caps(), 'wire': [], 'revertOrder': ['D']},
  },
  'degenerateRetirement': {f'{CS} ios': ['[H] counter-suffix ios', '[C] counter-suffix ios', '[T] counter-suffix ios', '[K] counter-suffix ios'],
                           f'{CS} android': ['[C] counter-suffix android', '[K] counter-suffix android'],
                           'rule': 'counter-suffix-rows §6: the stayDegenerateEvenIfPass label is retired at the closing gate iff EVERY listed key prints → GEOMETRY OK on wave55-final AND none of the cell\'s units is reverted (a cell review read; adjudicate.mjs reads gains only)'},
  'stage1Decision': ('at wave55-pre (union carriers): counter-suffix-rows.geometry.py wave55-pre wave55-open — [C] ios / android and [I] ios / android OK; '
                     '[H] ios and [T] ios predicted WRONG on the twin `ink:` read ONLY (no same-rasterizer twin draws the reordered marker glyphs or the '
                     'tabular run before stage 1) → the step-8 recorded look, then TWIN_RUN_DEFAULT = \'wave55-pre\' and OK from wave55-probe on (pre-landing '
                     'regeneration, L5 fix-r1 R5); the 10 controls OK ([K] ios included: U3-ios\'s five run boxes are carved out of it and read by [T] ios, '
                     'R1), [L] natives WRONG; the residual read 0 / 0 / 0 on counter-suffix ios / android and -005 ×2 (R3); counter-suffix ios ≈0.993 (MED: '
                     'U3-ios\'s other glyphs are not in the replay) / android ≈0.992 / web 1, -005 ios ≈0.997 / android ≈0.974, every carrier P and ≥ 0.95; the '
                     'other css-counter-styles captures decoded-pixel identical to wave55-open (disclosure-styles android: L6 may-move, |Δ| ≤ 0.002); per-test IR '
                     'of all 48 documents byte-identical. PRECEDENCE (pre-registered before stage 1, L5 fix-r1 R4): (i) an [H] ios / [T] ios line whose only '
                     'reason is `ink:` (the twin) or `residual:` goes to the step-8 recorded look FIRST — a right picture sets TWIN_RUN_DEFAULT = '
                     '\'wave55-pre\' (for `residual:`, the D40 fix-r6.zones.py --also record), a wrong one counts as that key\'s GEOMETRY reason ((ii) for [H] ios, (iii) for [T] ios); (ii) the LRM contingency '
                     'fires only on an [H] ios GEOMETRY reason (blob count, the period before the letter, the letter or the period off the ref\'s x ±2 / '
                     'rows ±1, the letter fill) or on a look that shows the H-low shape — never on a twin-only `ink:` or `residual:` line (the LRM re-cut '
                     'also reorders the glyphs: its re-probe would be twin-only WRONG again); (iii) a [T] ios GEOMETRY reason (the digit\'s width / '
                     'position / rows / fill, a U3 run-box glyph, a row-9 zone) reverts U3-ios; (iv) a [K] ios FAIL whose differing pixels all lie in '
                     'U3-ios\'s run boxes (rows 9-12 x130-135, row 10 x136-150) is U3-ios\'s, never U1-ios\'s — "U1 first" applies to rows 3-4 x0-63. '
                     'Any other non-OK gating key reverts ITS unit (U2i before U2o; D first on a U2o revert); U2i-android kept iff [I] android OK and '
                     '≥ 0.95; (ii) fired → the LRM fallback re-cut of U1 (contingencyUnits U1-ios-LRM: an appended probe-decisions/contingency.json row, '
                     'landed by land-units.sh --contingency L5:U1-ios-LRM), re-probe (a pre-registered stage1Probe.reprobes id) — OK, or twin-only with a '
                     'right look, keeps U1-ios + U1-ios-LRM; a geometry reason again → revert U1-ios (after U1-ios-LRM: its revertOrder). Round 4 (D39): '
                     'an [H] / [T] native line whose only reason is the twin `ink:` (another rasterizer\'s glyph) is decided by the recorded look of PLAN §7 '
                     'stage 1 step 8, never by widening INK_TOL; the same probe edit sets TWIN_RUN_DEFAULT = \'wave55-pre\'') + R6_STAGE1,
  # Round 2 (process-holes S2): the stage-1 swap between "keep U1-ios" and "revert U1-ios" is pre-registered like L1 NB-ios-b,
  # so it has a generator encoding and a landing row before stage 1 (never improvised at the probe).
  'contingencyUnits': {
    'U1-ios-LRM': {'base': 'U1-ios', 'trigger': '[H] counter-suffix ios prints WRONG, or its picture is H-low-shaped (the isolate face grew the marker line box), while [C] / [T] / [K] ios are OK (counter-suffix-rows.md §9 R1)',
                   'commit': 'tools/titan/results/wave55-counter-suffix-rows/unit-U1-ios-LRM.patch: lists/ListMarkerBidi.swift wraps the painted marker in LRM … LRM instead of LRI … PDI (no isolate run, so no fallback face for the controls) + its pin; ON TOP of U1-ios (it edits the file U1-ios creates)',
                   'captures': caps(ios=[stem(CS), stem(DISC)]), 'wire': [], 'revertOrder': ['U1-ios-LRM'],
                   'onLand': 'append it to probe-decisions/contingency.json (D25) and re-run this script: it becomes a revert unit, U1-ios\'s revertOrder becomes [U1-ios-LRM, U1-ios], re-probe css-counter-styles (a pre-registered stage1Probe.reprobes id)'},
  },
}

# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# L6 · android-paint-order — U1 self-paint band behind content, U2 legacy band behind content, U3 the filter→clip pin.
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
_scan = open(_track(os.path.join(HERE, 'android-paint-order.crossings-scan.out.txt'))).read().split('\n')
_i_car = next(i for i, l in enumerate(_scan) if l.startswith('U2 android capture carriers'))
_i_may = next(i for i, l in enumerate(_scan) if l.startswith('U2 may-move set'))
l6_u2 = sorted(l.strip() for l in _scan[_i_car + 1:_i_may] if l.strip().startswith('wpt__'))
l6_may_raw = sorted(l.strip() for l in _scan[_i_may + 1:] if l.strip().startswith('wpt__'))
assert len(l6_u2) == 25 and len(l6_may_raw) == 252, (len(l6_u2), len(l6_may_raw))
# D21 (round 1, generator-truth M2): the scan's may-move source counts a band only when Border*Width.px > 0; Compose also
# paints logical sides, shorthands, width keywords and — applyMediumDefault — a visible style with NO width at 3 dp. The
# census android-paint-order.maymove-medium.py reproduces the scan's 252 under the explicit rule (control arm, asserted)
# and lists what Compose's rule adds; none of the additions crosses (no new carrier).
_mm = section_lines('android-paint-order.maymove-medium.out.txt', 'MEDIUM-DEFAULT ADDITIONS', 'END ADDITIONS')
l6_may_medium = sorted(l.strip() for l in _mm if l.strip().startswith('wpt__'))
_mmtxt = open(_track(os.path.join(HERE, 'android-paint-order.maymove-medium.out.txt'))).read()
assert '(1) the brief\'s explicit-px rule (crossings-scan structural_docs): 252 documents' in _mmtxt, 'the medium census no longer reproduces the scan'
assert 'carriers among the additions: 0' in _mmtxt and not set(l6_may_medium) & set(l6_may_raw), 'a medium-default addition is a carrier / already counted'
assert len(l6_may_medium) == 26, len(l6_may_medium)
l6_u1 = sorted(r['stem'] for r in l6c['u1'])
assert l6_u1 == ['wpt__filter-effects__backdrop-filter-clip-rect', 'wpt__filter-effects__backdrop-filter-clip-rect-zoom'], l6_u1
# D2 (an under-reported radius, fixed here): census (2b) — a legacy-bordered component with opacity < 1 moves its band
# INSIDE ColorApplier's opacity group under U2 (android-paint-order §9 R3), whether or not it has a child; the 252-stem
# scan predicate (a child or own text) misses it. Exactly one in the corpus: the ring-fenced basic-blur.
l6_opacity = sorted(r['stem'] for r in l6c['opacityBordered'])
assert l6_opacity == ['wpt__filter-effects__backdrop-filter-basic-blur'] and l6_opacity[0] not in l6_may_raw
# Pre-landing regeneration (PLAN §10 item 3; regen.done.md) — L6's own behavioural census (Compose's extractConfig → applyConfig over
# all 10 719 components of the 1435 wave54-final per-test IR documents, on the delivered fix-r1 patches) found ONE exposed android
# capture outside carriers ∪ mayMove ∪ sharedStems ∪ ring-fence: a KEPT `display: contents` box with pseudos (Compose's
# ContentsUnboxing.isUnboxable is false when the box carries pseudos, so the box keeps its legacy band) whose band now paints behind
# its ::after. It joins mayMove BY RULE, asserted three ways: the lane's census and the independent re-verifier's census each name
# exactly this one growth stem on a pseudo exposure (wave55-android-paint-order/fix-r1/census.out.txt, reverify-r1/census.out.txt),
# and its run-of-record IR is that box (Display CONTENTS, pseudos, a four-side visible band). Unscored on android, so the complement
# and the watchlist are unchanged; predicted identical (MED: the ::after ink starts 1 px inside the content box).
def _l6_growth(rel, head):
    t = open(_track(os.path.join(HERE, '..', 'wave55-android-paint-order', rel))).read().split('\n')
    i = [k for k, l in enumerate(t) if l.startswith(head)]
    assert len(i) == 1, (rel, head, i)
    n = int(re.search(r'GROWTH (\d+)$', t[i[0]])[1])
    rows = t[i[0] + 1:i[0] + 1 + n]
    assert all(r.startswith('   wpt__') for r in rows), (rel, rows)
    return [(r.split()[0].rstrip(':'), r) for r in rows]
_g1, _g2 = _l6_growth('fix-r1/census.out.txt', 'fix: inside the plan set'), _l6_growth('reverify-r1/census.out.txt', 'fix-r1 movers (U1 + non-clipping U2)')
assert [s for s, _ in _g1] == [s for s, _ in _g2] == ['wpt__css-display__display-contents-dynamic-pseudo-insertion-001'], (_g1, _g2)
assert ' pseudos U2' in _g1[0][1] and ' pseudo U2' in _g2[0][1], (_g1, _g2)
l6_contents_pseudo = [_g1[0][0]]
_ir = json.load(open(os.path.join(RUNDIR, section_of(l6_contents_pseudo[0]), 'per-test-ir', l6_contents_pseudo[0] + '.json')))   # the run of record (read, never hashed: gitignored)
_kept = [c for c in _ir['components'] if {'type': 'Display', 'data': 'CONTENTS'} in c['properties'] and c.get('pseudos')
         and all(any(p_['type'] == f'Border{side}Width' and p_['data'].get('px', 0) > 0 for p_ in c['properties']) for side in ('Top', 'Right', 'Bottom', 'Left'))
         and all({'type': f'Border{side}Style', 'data': 'SOLID'} in c['properties'] for side in ('Top', 'Right', 'Bottom', 'Left'))]
assert len(_kept) == 1, _kept
assert l6_contents_pseudo[0] in CAPS['android'] and f'{test_of(l6_contents_pseudo[0])} android' not in CELLS, 'the growth stem is scored on android, or has no capture'
assert not set(l6_contents_pseudo) & (set(l6_may_raw) | set(l6_may_medium) | set(l6_opacity))
BBLUR = T('filter-effects', 'backdrop-filter-basic-blur')
CR, CRZ = T('filter-effects', 'backdrop-filter-clip-rect'), T('filter-effects', 'backdrop-filter-clip-rect-zoom')
CPFO = T('css-masking', 'clip-path', 'clip-path-filter-order')
XK = ['flex-abspos-staticpos-align-self-safe-001', 'flex-abspos-staticpos-align-self-safe-002', 'grid-abspos-staticpos-align-self-001',
      'grid-abspos-staticpos-justify-self-001', 'grid-abspos-staticpos-align-self-vertWM-001', 'grid-abspos-staticpos-align-self-rtl-001']
XT = {k: resolve(k) for k in XK}
assert all(stem(t) in l6_u2 for t in XT.values())
ID = ['borders-008', 'clip-path-borderBox-1e', 'clip-path-contentBox-1e', 'clip-path-paddingBox-1e', 'backdrop-filter-clip-rect-2', 'background-clip-002',
      's-11-1-1b-001', 'backdrop-filter-clip-radius-zoom', 'grid-abspos-staticpos-align-items-center', 'flex-abspos-staticpos-align-self-safe-003']
IDT = {k: resolve(k) for k in ID}
GSAFE = resolve('grid-abspos-staticpos-align-self-safe-001')
GRAD = [resolve(f'gradient__gradient-powerless-hue-{k}') for k in ('hsl', 'hwb', 'lch')]
l6_rest = sorted(set(test_of(s) for s in l6_u2) - set(XT.values()) - {GSAFE} - set(GRAD))
assert len(l6_rest) == 15, len(l6_rest)
APO = f'{PY} {P55}/android-paint-order.geometry.py <run> --base <base>'
l6_keys = {**{f'[PO] backdrop-filter-clip-rect {p}': f'{CR} {p}' for p in PLATS}, **{f'[PO] backdrop-filter-clip-rect-zoom {p}': f'{CRZ} {p}' for p in PLATS},
           **{f'[FO] clip-path-filter-order {p}': f'{CPFO} {p}' for p in PLATS},
           **{f'[X] {k} {p}': f'{XT[k]} {p}' for k in XK for p in PLATS}, **{f'[ID] {k} android': f'{IDT[k]} android' for k in ID}}
assert len(l6_keys) == 46 - 9, len(l6_keys)     # the brief's 46 keys minus its 9 ref rows (the probe's own self-check, exit 1)
L6 = {
  'dir': 'tools/titan/results/wave55-android-paint-order',
  'briefs': ['android-paint-order.md'],
  'ownership': {'owned': ['NEW runtimes/compose/…/borders/BehindContentBorder.kt (U1) + NEW BehindContentBorderTest.kt', '…/borders/radius/BorderRadiusApplier.kt (U1 :207 + kdoc)',
                          '…/test/…/borders/BorderRadiusSelfPaintTest.kt (U1)', '…/borders/sides/BorderSideApplier.kt (U2: defaulted behindContent + two one-line conditions)',
                          '…/borders/BordersFacade.kt (U2)', 'runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt (U2: three call-site lines at :753-754)',
                          'NEW …/borders/sides/BorderSidesBehindContentTest.kt (U2)', 'NEW …/effects/EffectsClipFilterOrderTest.kt (U3, test only)',
                          'tools/titan/results/wave55-android-paint-order/'],
                'seams': [], 'readOnly': ['EffectsFacade.kt', 'FilterApplier.kt', 'FilterExtractor.kt', 'ColorApplier.kt', 'OutlineApplier.kt', 'BorderImageApplier.kt',
                                          'TableApplier.kt', 'all three ComponentRenderers', 'InlineRunFold.kt']},
  'captureCarriers': caps(android=l6_u1 + l6_u2),
  'wireCarriers': [],
  'carrierRule': {
    'U1': 'BorderRadiusApplier.selfPaintsWithoutClip route (10 bordered components): the band paints behind content — carriers are the parents with a child that can paint into the band, 2 parents in 2 documents (clip-rect, clip-rect-zoom); the 8 other self-paint documents are identity ([ID] keys on 5)',
    'U2': ('the legacy route (1428 explicit-px bordered components; 1963 under Compose\'s own side resolution — D21): the band moves inner of ColorApplier and paints behind content — capture carriers: '
           'android crossing excess ≥ 20 with web / iOS within 5, 25 captures (android-paint-order.crossings-scan.out.txt); may-move: every document with a bordered component that has a non-<br> '
           'child or own text (252 explicit-px + 26 D21 additions: logical sides, shorthands and the 3-dp MEDIUM default, android-paint-order.maymove-medium.out.txt) + D2 the opacity-bordered '
           'component of census (2b) (1) + the fix-r1 census growth by rule: a kept `display: contents` box with pseudos whose band now paints behind its '
           '::after (1; wave55-android-paint-order/fix-r1/census.out.txt, reverify-r1). Pre-landing regeneration (L6 fix-r1, skeptic D1): the band paints '
           'behind content only on a box that does NOT clip (`!config.overflow.shouldClip` — css-overflow-3 §3: a clipping box\'s content stops at its '
           'padding edge, so the base content-then-band chain is the faithful one there): the 29 clipping legacy bands in 26 documents keep the base chain '
           'byte-for-byte (census I1-I5; pins P3h / P3i), and 19 may-move stems are now over-coverage (harmless)'),
    'U3': 'test only: no carrier',
  },
  'mayMove': {'android': sorted((set(l6_may_raw) | set(l6_may_medium) | set(l6_opacity) | set(l6_contents_pseudo)) - set(l6_u1) - set(l6_u2))},    # other lanes' carriers subtracted below (C2)
  'mayMoveRule': ('a changed android capture in this set is NOT a leak (control-check: may-move); its score keeps R5\'s 0.002 band (each cell is a '
                  'must-not-move cell — of mustNotMoveComplement or of a lane\'s named list: R5 and probe-readout give both the same 0.002 band, and '
                  'read may-move first; round-1 N1); a may-move non-carrier with |Δ| > 0.002 stops the ship until its picture shows one of three '
                  'pre-registered pictures: a band now under its child (android-paint-order §6); the band now inside the element\'s opacity group '
                  '(css-color-4 §3.3: anchor-center-overflow-003, 48 bands into their 20 % groups; basic-blur too, ring-fenced); or a UA-widget '
                  'replica now over the band (flex-gap-decorations-023: the BUTTON replica overflows its band, replay Δ +0.0007, DEGENERATE, never '
                  'claimed) — pre-landing regeneration, L6 fix-r1 item 4. Other lanes\' carriers are subtracted (C2) — their moves are theirs, attributed by the drop-L6-U2 '
                  'arm inside its sections and BY RULE outside them. Withdrawn (emptied) when L6 U2 or U1 is reverted at a probe (round 1, M4)'),
  'stage1Sections': ['filter-effects', 'css-masking', 'css-flexbox', 'css-grid'],
  'probeSections': [],      # filled below: the carriers' and keys' sections + every may-move section but css-color (§ PROBE)
  'predictions': [
    pred(f'{CR} android', 'P 0.9559', 'P ≈0.957 (replay 0.9574)', 'picture-correctness (P→P): the menus\' red over the navbar band, CSS 2.1 App. E', 'HIGH', PASS, True, ['U1'], GEO_OK),
    pred(f'{XT["flex-abspos-staticpos-align-self-safe-001"]} android', 'P 0.9619', 'P ≈0.994 (replay 0.9938)', 'picture-correctness: crossings 1152 → 0', 'MED-HIGH', PASS, True, ['U2'], GEO_OK),
    pred(f'{XT["flex-abspos-staticpos-align-self-safe-002"]} android', 'P 0.9635', 'P ≈0.991 (replay 0.991, a lower bound: §7)', 'picture-correctness: crossings 980 → 0', 'MED-HIGH', PASS, True, ['U2'], GEO_OK),
  ] + [pred(f'{XT[k]} android', CELLS[f'{XT[k]} android'], f'P ≈{v} (replay)', 'picture-correctness: crossings → 0 (score flat)', 'MED', PASS, True, ['U2'], GEO_OK)
       for k, v in (('grid-abspos-staticpos-align-self-001', '0.9672'), ('grid-abspos-staticpos-justify-self-001', '0.9667'),
                    ('grid-abspos-staticpos-align-self-vertWM-001', '0.9613'), ('grid-abspos-staticpos-align-self-rtl-001', '0.9645'))]
    + [pred(f'{t} android', CELLS[f'{t} android'], f'{CELLS[f"{t} android"].split()[0]} ±0.0005 (replay); crossings expected 0, not keyed', 'flat; never claimed (no key)', 'MED',
            None, False, ['U2'], **({'floorNA': FLOOR_NA} if CELLS[f'{t} android'].startswith('f') else {})) for t in l6_rest]
    + [pred(f'{GSAFE} android', CELLS[f'{GSAFE} android'], 'f ≈0.9448 (replay), stays f', 'LOW mover, stays f', 'LOW', None, False, ['U2'], floorNA=FLOOR_NA)]
    + [pred(f'{t} android', CELLS[f'{t} android'], '+0.0002-0.0003 (replay), stays f', 'flat, stays f (its f is dominated by other defects)', 'LOW', None, False, ['U2'], floorNA=FLOOR_NA)
       for t in GRAD]
    + [note(f'{CRZ} android: unscored (scoreEligible false) — overlap rows red 416 / blue 0 under U1, read by its [PO] report key', ['U1']),
       note('U3 FO-pin: EffectsClipFilterOrderTest red under both mutations (clip below the filter; drop-shadow dropped), green restored — its only gate is its mutation log', ['U3']),
       note('[W1] optional evidence window: Filter_DropShadow_NoBlur android #111-class strip ≥ 900 px → 0(k)(2) closes as "rendered"; 0 → a next-wave unit (radius 3 components); cropped frame → UNMEASURABLE', ['U3'])],
  'mustNotMove': sorted(set(cells_of([IDT[k] for k in ID], ('android',)) + cells_of([CPFO]) + cells_of([test_of(s) for s in l6_u1 + l6_u2], ('web', 'ios')))),
  'geometryProbes': [
    probe(APO, {k: GEO_OK for k in l6_keys}, l6_keys,
          gating={'[PO] backdrop-filter-clip-rect android': 'U1', **{f'[X] {k} android': 'U2' for k in XK}},
          report=['[PO] backdrop-filter-clip-rect-zoom android'],
          contains={f'[ID] {k} android': 'identical to <base>' for k in ID},
          blindSpot='[FO] cannot see a DROPPED shadow (FO-shadow-dropped = the ref prints OK): it is a control; U3 is the order\'s proof',
          onWave54Final='wave54-final --base wave54-open: 38 OK · 8 WRONG (exactly the 7 gating + the 1 report key), exit 0',
          teeth=('--fakes: PO-today / band-dropped / menus-dropped / menus-through-navbar / backdrop-lost, FO-filter-after-clip / blurred-edge / box-20px-low, X-border-across-children / children-dropped / today all WRONG on their reason; --replays: U1 [PO] OK, U2 [X] OK on 5 of 6 (safe-002\'s replay keeps 50 crossing px: a replay artifact, the row\'s lower bound) — probes-selfcheck.txt §L6. '
                 'Round 1 (instrument-teeth M6): [X] also reads the child-fill bbox (= the BASE capture\'s ±1), the fill (≥ 0.98 × base) and the band (dark px ≥ 0.9 × min(base, ref)) — '
                 'border-erased, child ±3, child-shrunk and child-scrambled are WRONG; [PO] reads the navbar\'s whole blue (+ overlap red − 416) ≥ 0.95 × 866 — navbar-sides-erased (320) is WRONG (probes-selfcheck.txt §T). '
                 'Round 2 (M5): [X] reads the child fill against the base pixel for pixel — `lost` ≤ 4 and `extra` (outside dilate₁(base fill ∪ base band)) ≤ 4: one of 22-26 children moved 3 px inside the union bbox is WRONG. '
                 'Round 3 (verify M2, D38): [X] `extra` ≤ 1, [PO] red n and pink ±2. '
                 'Round 4 (verify M2, D39): `extra` counts fill outside base fill ∪ base band ∪ the band\'s 1-px ring where the base is not white (no ring around the base FILL) — '
                 'a child fill drawn again 1 px off (+24 … +500 px), a 1-px smear and a 1×4 bar of fill are WRONG (probes-teeth-r4.py); one stray fill px reads 1 (the 2-px floor). '
                 'Round 6 (verify-r5 M5 / M6, D40): the RESIDUAL read — [X] outside the child fill, [PO] outside the menus\' red (capture ∪ base) the capture = the base: black / purple / parent-yellow paint on white, faint child / parent copies and whole-picture ghosts (+2056 … +6294 px), blue specks below the navbar and pink outside its counted box are WRONG (probes-teeth-r5.py)')),
  ],
  'revertUnits': {
    'U1': {'commit': 'PO-selfpaint-android: NEW borders/BehindContentBorder.kt (behindContentBorder + drawInOrder + the androidx band arithmetic) + BorderRadiusApplier.kt :207 + kdoc + NEW BehindContentBorderTest.kt (P1, P2; fix-r1 P5a-f: the rect / rounded painters\' draw and clip calls through a recording DrawScope, the cache dropped on a width / brush / shape change, the generic branch\'s tint and mask config) + BorderRadiusSelfPaintTest.kt :158-185',
           'captures': caps(android=l6_u1), 'wire': [], 'revertOrder': ['U2', 'U1']},
    'U2': {'commit': 'PO-sides-android: BorderSideApplier.kt (defaulted behindContent + two one-line conditions) + BordersFacade.kt (sidesBehindContent + applySidesBehindContent) + StyleApplier.kt :753-754 three call-site lines, gated on `!config.overflow.shouldClip` (fix-r1: a clipping box keeps the base chain — css-overflow-3 §3) + NEW BorderSidesBehindContentTest.kt (P3a-g; fix-r1 P3h / P3i: a clipping INSET input and a clipping SOLID box keep their base bands)',
           'captures': caps(android=l6_u2), 'wire': [], 'revertOrder': ['U2']},
    'U3': {'commit': 'FO-pin: NEW effects/EffectsClipFilterOrderTest.kt (P4, test only)', 'captures': caps(), 'wire': [], 'revertOrder': ['U3']},
  },
  'stage1Decision': ('at wave55-pre (union carriers): android-paint-order.geometry.py wave55-pre --base wave55-open, then geometry-gate.py --lanes L6 --sections '
                     '<stage 1>; control-check over the four sections: web and iOS byte-identical; Android changed ⊆ the union carriers ∪ L6 mayMove. U2 '
                     'is reverted on an [X] android gating WRONG, a U2 [ID] row WRONG that L6 caused (clip-radius-zoom, grid align-items-center, flex '
                     'safe-003), an Android mover outside the union carriers ∪ mayMove that the rule-5 bisect lands on U2 (round 2, generator-truth S1: '
                     'L2 / L3\'s Compose units are on the same tree), or a mayMove non-carrier with |Δ| > 0.002 whose picture shows '
                     'none of mayMoveRule\'s three pictures (a band now under its child; the band now inside the element\'s opacity group; a UA-widget '
                     'replica now over the band — pre-landing regeneration, L6 fix-r1) — C2: never for another lane\'s carrier. U1 is reverted on `[PO] backdrop-filter-clip-rect '
                     'android` WRONG or a U1 [ID] row WRONG (clip-path-*Box-1e, clip-rect-2); U2 goes first. The [ID] rows outside the probe sections and '
                     'U2\'s 3 css-images carriers are read at wave55-probe') + R6_STAGE1,
}

# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# L7 · instruments — no corpus picture changes; every target is a must-not-move (instruments.md §6).
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
G033 = T('css-gaps', 'flex', 'flex-gap-decorations-033')
OOF1, OOF2 = T('css-text', 'hyphens', 'hyphens-out-of-flow-001'), T('css-text', 'hyphens', 'hyphens-out-of-flow-002')
SPAN2 = T('css-text', 'hyphens', 'hyphens-span-002')
_bb = section_lines('instruments.census.out.txt', '## (2)', '## (3)')
l7_baked = sorted(l.split()[0].replace('css/', '', 1) for l in _bb if l.strip().startswith('css/') and '.html' in l.split()[0])
assert len(l7_baked) == 17 and CS in l7_baked, l7_baked
L7 = {
  'dir': 'tools/titan/results/wave55-instruments',
  'briefs': ['instruments.md'],
  'ownership': {'owned': ['U1 tools/titan/results/wave55-plan/flex-zero-gap-rules.geometry.py (+ .fakes-run.sh, .geometry.out.txt, instruments-flex-gap-fakes/)',
                          'U2 NEW tools/titan/wire-differential.{sh,mjs,test.mjs}', 'U3 NEW tools/titan/results/wave55-gate/ab-arms55.{sh,selftest.sh,selftest.out.txt}',
                          'U4 schema/spec/03-children.md (one unnumbered §4.1 paragraph) + runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts :32 + InlineRuns.ts :52/:167/:174 + NEW runtimes/web/tests/renderer/InlineRunsLatitudeCitation.test.ts (+ optional U4c ComposedRootSeparator.ts one TODO line)',
                          'U5 tools/titan/bidi-marker-paint.mjs + tools/titan/bidi-bake.test.mjs', 'U6 the 21 pins in 8 test files (instruments §4 U6)',
                          'tools/titan/results/wave55-instruments/_note.md'],
                'seams': [], 'readOnly': ['every wave-54 record (flex-zero-gap-rules.geometry.py f71565f1…, wl3-wire-differential.sh 54b71953…, ab-arms54.sh e8478700…)']},
  'captureCarriers': caps(), 'wireCarriers': [],
  'carrierRule': 'none in the corpus: U5 is the only owned path on the extract route and is predicted wire-identical over its 17 bidi-baked documents (instruments.census.out.txt (2)); every capture of the radius is a must-not-move',
  'stage1Sections': [], 'probeSections': ['css-gaps', 'css-text', 'css-counter-styles'],
  'predictions': [
    note('U2 wire-differential: calibration on the parked wave-54 trees prints `identical 1397 · content-changed 23 · renumbered 15 · one-sided 0`, exit 0; with counter-suffix deleted from the expect file exit 5; the mis-invocation shape exit 4 — HIGH', ['U2']),
    note('U2 pins P1-P6 green; mutations M1-M5 each red then restored — HIGH', ['U2']),
    note('U3 ab-arms55.selftest.sh: exit 0, HEAD restored, the hash block present once, final/ab-drop-U.{txt,driver.txt} written, the real pool dir untouched, this repo\'s refs unchanged; M-L18 red, M-dry byte-identical — HIGH', ['U3']),
    note('U4: node schema/conformance/run.mjs 39/39 valid, byte-identical; InlineRunsLatitudeCitation pins (a) / (b) red under their mutations — HIGH', ['U4']),
    note('U5 [W-L7a] (Chromium + converter, no device): `::marker` and chain maskImage / mixBlendMode read `none` / `normal`; wire-differential over css-anchor-position, css-backgrounds, css-counter-styles, css-text, css-writing-modes, selectors → exit 0, content-changed 0 · renumbered 0 · one-sided 0; --classify-only with counter-suffix pre-registered → exit 5. Any other result: U5 does not land (C3: U5\'s claim is wire-only)', ['U5']),
    note('U6: 21 pins (C1-C7, S1-S6, W1-W6, T1-T2), each mutation red → restore → green, sha256-verified, in a throwaway worktree — HIGH', ['U6-compose', 'U6-swift', 'U6-web', 'U6-tooling']),
  ],
  # C3: counter-suffix ios / android (L5 carriers) and dir-style-02a / bidi-tab-001 / hyphenate-character-005 android (L3 carriers) leave
  # L7's capture list (mnm() excludes and prints them); they stay wire-identity rows of U5 ([W-L7a]).
  'mustNotMove': sorted(set(cells_of([G033]) + cells_of(l7_baked) + cells_of([OOF2]) + cells_of([OOF1], ('web',)))),
  'geometryProbes': [
    probe(f'{PY} {P55}/flex-zero-gap-rules.geometry.py <run>', {f'flex/flex-gap-decorations-033 {p}': GEO_OK for p in PLATS},
          {f'flex/flex-gap-decorations-033 {p}': f'{G033} {p}' for p in PLATS},
          restated=('C4 / X6: the WAVE-55 copy (U1: + the blue row-rule horizontal extent, rows y65 / y115) replaces the wave-54 file, whose 033 ios / '
                    'android keys were GATING (GAP-ios / GAP-android, shipped): CONTROL here. The wave-54 file is not registered'),
          teeth='fake-blue-r1-short5 / r2-lshort4 / r1-lshort2 / r1-split1 / r1-moved5 / r2-hshift2 / blue-none WRONG ×3 on the copy (the wave-54 probe passed the first five); blue-r1-short1 OK by design (1-px tolerance) — probes-selfcheck.txt §L7'),
    probe(f'{PY} {P54}/web-out-of-flow-hyphen-box.geometry.py <run>',
          {f'hyphens/{t} {p}': GEO_OK for t in ('hyphens-span-002', 'hyphens-out-of-flow-001', 'hyphens-out-of-flow-002') for p in PLATS},
          {f'hyphens/{t} {p}': f'{T("css-text", "hyphens", t)} {p}' for t in ('hyphens-span-002', 'hyphens-out-of-flow-001', 'hyphens-out-of-flow-002') for p in PLATS},
          restated='the wave-54 probe, unchanged: its gating `hyphens/hyphens-out-of-flow-002 web` (W1, shipped) is U4\'s CONTROL (a comment + a console string must not move it); the other 8 rows are controls as in wave 54'),
  ],
  'revertUnits': {u: {'commit': c, 'captures': caps(), 'wire': [], 'revertOrder': [u]} for u, c in (
    ('U1', 'records only: the wave-55 flex-gap probe copy + its fakes and outputs'),
    ('U2', 'NEW tools/titan/wire-differential.{sh,mjs,test.mjs}'),
    ('U3', 'NEW tools/titan/results/wave55-gate/ab-arms55.{sh,selftest.sh,selftest.out.txt}'),
    ('U4', 'schema/spec/03-children.md §4.1 latitude paragraph + the four web citations + InlineRunsLatitudeCitation.test.ts'),
    ('U5', 'bidi-marker-paint.mjs MARKER_PAINT_INITIAL +maskImage / +mixBlendMode + bidi-bake.test.mjs S6-2 rows — lands only after [W-L7a] holds'),
    ('U6-compose', 'C1-C7 pins'), ('U6-swift', 'S1-S6 pins'), ('U6-web', 'W1-W6 pins'), ('U6-tooling', 'T1-T2 pins'))},
  'landingOrder': ['U1', 'U2', 'U3', 'U4', 'U6-compose', 'U6-swift', 'U6-web', 'U6-tooling', 'U5'],
  'stage1Decision': 'none: L7 requests no device window (U5 is proven by [W-L7a]); its §6 rows in css-counter-styles / css-text are read with the union carrier set at wave55-pre and wave55-probe',
}

lanes = {'L1-hyphen-ios-linebox': L1, 'L2-cbb-android': L2, 'L3-positioned-parent-slot': L3, 'L4-ua-heading-margin': L4,
         'L5-counter-suffix-rows': L5, 'L6-android-paint-order': L6, 'L7-instruments': L7}

# ── contingency units (L1 NB-ios-b): EMPTY at planning; probe-decisions/contingency.json lists the units the probe landed ─
CONTINGENCY = json.load(open(_track(_CONTINGENCY_FILE)))      # [{'lane': 'L1-hyphen-ios-linebox', 'unit': 'NB-ios-b', 'run': 'wave55-pre', 'why': '…'}]
for _c in CONTINGENCY:
    # Round 2 (process-holes S2): a named refusal, never a KeyError — and the landed unit is prepended to every revertOrder
    # that holds ITS base unit (L1 NB-ios-b on NB-ios, L5 U1-ios-LRM on U1-ios), not to a hard-coded ['NB-ios'].
    assert set(_c) >= {'lane', 'unit', 'run', 'why'}, f'contingency row needs lane / unit / run / why: {_c}'
    assert _c['lane'] in lanes and _c['unit'] in lanes[_c['lane']].get('contingencyUnits', {}), \
        f"contingency row {_c['lane']} {_c['unit']}: not a pre-registered contingencyUnits entry (L1 NB-ios-b, L5 U1-ios-LRM)"
    _lane = lanes[_c['lane']]
    _base_u = _lane['contingencyUnits'][_c['unit']]['base']
    _lane['revertUnits'][_c['unit']] = dict(_lane['contingencyUnits'].pop(_c['unit']), landedAt=_c['run'], why=_c['why'])
    for _n, _u in _lane['revertUnits'].items():
        if _n != _c['unit'] and _base_u in _u['revertOrder'] and _c['unit'] not in _u['revertOrder']:
            _u['revertOrder'] = [_c['unit']] + _u['revertOrder']

# ── disjointness: a stem is a capture carrier of ONE lane per platform (the control attributes by lane) ────────────────
owner = {p: {} for p in PLATS}
for plat in PLATS:
    for name, lane in lanes.items():
        for s in lane['captureCarriers'][plat]:
            assert s not in owner[plat], f'{s} [{plat}] is a carrier of both {owner[plat][s]} and {name}'
            owner[plat][s] = name
wire_owner = {}
for name, lane in lanes.items():
    for s in lane['wireCarriers']:
        assert s not in wire_owner, f'wire {s} is a carrier of both {wire_owner[s]} and {name}'
        wire_owner[s] = name
union = {p: sorted(owner[p]) for p in PLATS}

def carrier_of(line):
    """The lane that may change this cell's capture, or None."""
    test, plat = line.rsplit(' ', 1)
    return owner[plat].get(stem(test))

# C2: L6's may-move set never holds another lane's carrier — those stems are the other lane's, attributed by the arm.
_shared = sorted(s for s in L6['mayMove']['android'] if s in owner['android'])
L6['sharedStems'] = {s: owner['android'][s] for s in _shared}
L6['mayMove']['android'] = sorted(s for s in L6['mayMove']['android'] if s not in owner['android'])
assert all(s in CAPS['android'] for s in L6['mayMove']['android']), 'a may-move stem has no android capture'

# ── ring-fence: never a carrier, never a must-not-move, report only (a generic mechanism that moves it is reported) ───
RING = BBLUR
assert all(stem(RING) not in union[p] for p in PLATS), 'the ring-fenced test is a carrier'
ring_cells = cells_of([RING])

excluded = []   # printed, so every exclusion of another lane's carrier from a must-not-move list is visible
def mnm(lane_name, lines):
    out = []
    for l in sorted(set(lines)):
        if l.rsplit(' ', 1)[0] == RING:
            excluded.append((lane_name, l, 'RING-FENCED')); continue
        o = carrier_of(l)
        if o is None:
            out.append(cell(*l.rsplit(' ', 1)))
        elif o != lane_name:
            excluded.append((lane_name, l, o))
    return out

for name, lane in lanes.items():
    lane['mustNotMove'] = mnm(name, lane['mustNotMove'])

# ── the complement (C1): every scored cell outside the union carriers and the ring-fence, not already named by a lane ─
named = {c for l in lanes.values() for c in l['mustNotMove']}
# ── the may-move looks (stage 2, PLAN §10 item 17) become prediction rows of their lane and leave the complement ──
for _k in LOOKS:
    _lane = lanes[_k['lane']]; _stem = 'wpt__' + _k['cell'].rsplit(' ', 1)[0].replace('.html', '').replace('/', '__')
    assert _stem in _lane.get('mayMove', {}).get('android', []), f"looks.json {_k['cell']}: not a may-move stem of {_k['lane']} (withdrawn, or never one)"
    assert carrier_of(_k['cell']) is None and _k['cell'] not in named, f"looks.json {_k['cell']}: a carrier or a named must-not-move cell, not a may-move non-carrier"
    _lane['predictions'].append(pred(_k['cell'], _k['open'], _k['probe'], f"may-move stem looked at ({_k['run']}): {_k['picture']} — never claimed", 'MED',
                                     units=('U2',), direction='undirected',
                                     directionWhy=f"a may-move non-carrier beyond the 0.002 band whose picture is mayMoveRule's \"{_k['picture']}\" ({_k['why'].split(':')[0]}); read and labelled, never a revert by itself (rule 2 exempt)",
                                     lookedAt={'run': _k['run'], 'picture': _k['picture'], 'why': _k['why']}))
complement = sorted(k for k in CELLS if carrier_of(k) is None and k.rsplit(' ', 1)[0] != RING and k not in named and k not in LOOKED)
carrier_cells = sorted(k for k in CELLS if carrier_of(k) is not None)
assert len(named) + len(complement) + len(carrier_cells) + len(ring_cells) + len(LOOKED) == len(CELLS), 'the complement does not partition the scored cells'

# ── every carrier stem has a capture in the run of record (and a per-test IR document if it is a wire carrier) ─────
for plat in PLATS:
    for s in union[plat]:
        png = os.path.join(RUNDIR, section_of(s), CAPDIR[plat], s + '.png')
        assert os.path.exists(png), f'carrier capture missing in {RUN}: {plat} {s}'
for s in wire_owner:
    assert os.path.exists(os.path.join(RUNDIR, section_of(s), 'per-test-ir', s + '.json')), f'wire carrier IR missing: {s}'

# ── revert units cover each lane's carriers exactly (so revert rule 1 always names a commit) ─────────────────────────
for name, lane in lanes.items():
    for plat in PLATS:
        covered = {s for u in lane['revertUnits'].values() for s in u['captures'][plat]}
        assert covered == set(lane['captureCarriers'][plat]), f'{name} [{plat}] revert units {sorted(covered)} != carriers'
    assert {s for u in lane['revertUnits'].values() for s in u['wire']} == set(lane['wireCarriers']), f'{name} wire units'
    for u in lane['revertUnits'].values():
        assert all(x in lane['revertUnits'] for x in u['revertOrder']), f'{name}: revertOrder names an unknown unit {u["revertOrder"]}'
    for p in lane['predictions']:
        assert all(u in lane['revertUnits'] for u in p['units']), f'{name} {p["cell"]} names an unknown unit {p["units"]}'
        # a may-move look row (stage 2, PLAN §10 item 17) is the one prediction on a NON-carrier: a may-move stem is a cell L6 U2
        # moves without carrying it (its band is the class's); the row only labels the recorded look
        if p['kind'] != 'non-corpus' and 'lookedAt' not in p:
            test, plat = p['cell'].rsplit(' ', 1)
            assert carrier_of(p['cell']) == name, f'{name} predicts {p["cell"]} but it is not its carrier ({carrier_of(p["cell"])})'
            for u in p['units']:
                assert stem(test) in lane['revertUnits'][u]['captures'][plat], f'{name} {p["cell"]}: unit {u} does not carry it'
# Every scored carrier cell has a prediction row (a carrier nobody predicts is read by rules 1 / 2 with nothing to compare).
_pcells_all = {p['cell'] for l in lanes.values() for p in l['predictions']}
_unpredicted = [c for c in carrier_cells if c not in _pcells_all]
assert not _unpredicted, f'scored carrier cells without a prediction row: {_unpredicted[:6]}'

# ── the probe runs ───────────────────────────────────────────────────────────────────────────────────────────────────
ORDER = ['CSS2', 'css-anchor-position', 'css-backgrounds', 'css-break', 'css-cascade', 'css-color', 'css-contain', 'css-counter-styles',
         'css-display', 'css-flexbox', 'css-gaps', 'css-grid', 'css-images', 'css-lists', 'css-masking', 'css-multicol', 'css-overflow',
         'css-position', 'css-pseudo', 'css-sizing', 'css-tables', 'css-text', 'css-text-decor', 'css-transforms', 'css-ui', 'css-values',
         'css-view-transitions', 'css-writing-modes', 'filter-effects', 'selectors']        # gate-driver.sh DEFAULT_SECTIONS
assert sorted(ORDER) == sorted(os.listdir(RUNDIR)), 'gate section list drifted'
in_order = lambda secs: [s for s in ORDER if s in set(secs)]
# L6's stage-2 sections: its carriers, keys and every may-move section but css-color (D7: two may-move stems and no
# carrier, read at the closing gate as wave 54 read its unprobed sections).
L6['probeSections'] = in_order({section_of(s) for s in L6['captureCarriers']['android'] + L6['mayMove']['android']} - {'css-color'}
                               | {c.split('/')[0] for pr in L6['geometryProbes'] for c in pr['keyCells'].values()})
PROBE = in_order({s for l in lanes.values() for s in l['probeSections']})
STAGE1 = in_order({s for l in lanes.values() for s in l.get('stage1Sections', [])})
NOT_PROBED = [s for s in ORDER if s not in PROBE]
# Every carrier's section is probed at stage 2, and every geometry key's section (else its key is first read at the gate).
for plat in PLATS:
    for s in union[plat]:
        assert section_of(s) in PROBE, f'carrier {plat} {s} sits in an unprobed section'
_unprobed_keys = sorted({(script_of(pr['cmd']), k) for l in lanes.values() for pr in l['geometryProbes'] for k, c in pr['keyCells'].items() if c.split('/')[0] not in PROBE})
# Every unit that positions boxes or changes glyphs on a native has ≥1 carrier read at stage 1 (BACKLOG "Wave 53 lessons").
stage1_cover = {}
for name, lane in lanes.items():
    for u, spec in lane['revertUnits'].items():
        native = [s for p in ('ios', 'android') for s in spec['captures'][p]]
        if native:
            hit = sorted(s for s in native if section_of(s) in STAGE1)
            assert hit, f'{name} {u}: no native carrier in a stage-1 section — the unit would first be read at wave55-probe'
            stage1_cover[f'{name} {u}'] = {'stage1': len(hit), 'stage2Only': sorted({s for s in native if section_of(s) not in STAGE1})}

# ── consistency: no lane names as must-not-move a capture another lane may change ───────────────────────────────────
for name, lane in lanes.items():
    for line in lane['mustNotMove']:
        assert carrier_of(line) is None, f'{name} must-not-move {line} is a carrier of {carrier_of(line)}'
for line in complement:
    assert carrier_of(line) is None

# ── probe decisions (EMPTY at planning; the orchestrator fills these tables after wave55-pre / wave55-probe and re-runs
# this script — expectations.json is never hand-edited). The wave-54 mechanism, unchanged: a reverted unit's carriers are
# WITHDRAWN (a change on them is a leak again), a prediction whose units are ALL reverted is withdrawn from R4 and held to
# must-not-move in R5, a prediction that `requires` a reverted unit is DEMOTED unless RESTATE re-registers it.
#   REVERTED = [{'lane': 'L2-cbb-android', 'unit': 'U2', 'run': 'wave55-pre', 'commit': '<sha>', 'revertCommit': '<sha>', 'rule': '4: …'}]
#   RESTATE  = {'selectors/dir-style-02a.html android': {'to': '…', 'floorNA': '…', 'gating': False, 'why': 'PLAN §10 item n'}}
REVERTED = json.load(open(_track(_REVERTED_FILE)))
RESTATE = json.load(open(_track(_RESTATE_FILE)))
for _r in REVERTED:      # round 2 (generator-truth N5 / process-holes N1): a named refusal, never a KeyError
    assert set(_r) >= {'lane', 'unit', 'run', 'rule'}, f'probe-decisions/reverted.json row needs lane / unit / run / rule: {_r}'
    assert _r['lane'] in lanes, f"reverted row names no lane: {_r['lane']}"
    assert not (L4_GO_SMALL and _r['lane'] == 'L4-ua-heading-margin' and _r['unit'] == 'U2-android'), \
        ('GO-SMALL is decided BEFORE the landing only (round 2, process-holes S7): once U2-android has landed, the same outcome is a rule-4 '
         'revert row of U2-android — never holds.json goSmall.held with that row')
    assert _r['unit'] in lanes[_r['lane']]['revertUnits'], \
        f"reverted row {_r['lane']} {_r['unit']}: not a revert unit (one row per UNIT; its -S1 fix commits go in that row's `commit`)"
# Append-only (round 1, S3): every decision the INSTALLED expectations.json records must still be an input — a regeneration
# fed only the newest decision would silently re-gate the keys an earlier probe withdrew (process-holes E8).
_installed = os.path.join(HERE, 'expectations.json')
if os.path.exists(_installed):
    _prev = json.load(open(_installed))
    _have = {(r['lane'], r['unit'], r['run']) for r in REVERTED}
    for r in ((_prev.get('probeDecisions') or {}).get('reverted') or []):
        assert (r['lane'], r['unit'], r['run']) in _have, f"decision row dropped: {r['lane']} {r['unit']} at {r['run']} is in the installed expectations.json but not in {os.path.relpath(_REVERTED_FILE, ROOT)} (append-only)"
    for c in (_prev.get('contingencyLanded') or []):
        assert any(x['lane'] == c['lane'] and x['unit'] == c['unit'] for x in CONTINGENCY), f"contingency row dropped: {c['lane']} {c['unit']} (append-only)"
    for _l in _prev.get('lanes', {}).values():
        for _p in _l.get('predictions', []):
            assert 'restated' not in _p or _p['cell'] in RESTATE, f"RESTATE row dropped: {_p['cell']} (append-only)"
    for r in ((_prev.get('probeDecisions') or {}).get('looks') or []):
        assert any(k['cell'] == r['cell'] and k['run'] == r['run'] for k in LOOKS), f"looks row dropped: {r['cell']} at {r['run']} (append-only)"

def key_units(probe_, key):
    """The units a gating key needs on the tree (its `requires` entry, else the units its `gating` value names)."""
    return set(probe_.get('requires', {}).get(key) or probe_['gating'][key].split('|'))

def probe_decisions():
    rev = {(r['lane'], r['unit']) for r in REVERTED}
    out = []
    for r in REVERTED:
        lane = lanes[r['lane']]
        kept = [u for u in lane['revertUnits'] if (r['lane'], u) not in rev]
        still = {p: {s for u in kept for s in lane['revertUnits'][u]['captures'][p]} for p in PLATS}
        still_wire = {s for u in kept for s in lane['revertUnits'][u]['wire']}
        unit = lane['revertUnits'][r['unit']]
        gone = [p['cell'] for p in lane['predictions'] if p['units'] and all((r['lane'], u) in rev for u in p['units'])
                and r['unit'] in p['units'] and p['kind'] != 'non-corpus']
        for p in lane['predictions']:
            if p['cell'] in gone and 'demotedFrom' not in p:
                p['demotedFrom'] = {'gating': p['gating'], 'floor': p['floor'], 'direction': p['direction'], 'unit': r['unit'], 'run': r['run'], 'rule': r['rule'], 'withdrawn': True}
                p.update(gating=False, floor=None)
        geo = []
        for pr in lane['geometryProbes']:
            for key in sorted(k for k in pr.get('gating', {}) if r['unit'] in key_units(pr, k)):
                pr.setdefault('withdrawn', {})[key] = f"was gating -> {pr['gating'][key]}; unit {r['unit']} reverted at {r['run']} (rule {r['rule']})"
                del pr['gating'][key]
                geo.append(f'{script_of(pr["cmd"])} {key}')
        demoted = []
        for p in lane['predictions']:
            if p['cell'] in gone or p['kind'] == 'non-corpus' or r['unit'] not in p.get('requires', p['units']) or 'demotedFrom' in p:
                continue
            p['demotedFrom'] = {'gating': p['gating'], 'floor': p['floor'], 'direction': p['direction'], 'unit': r['unit'], 'run': r['run'], 'rule': r['rule']}
            p.update(gating=False, floor=None, direction='undirected',
                     directionWhy=f"demoted: required unit {r['unit']} reverted at {r['run']} (rule {r['rule']}); the remaining units' sign is not pre-registered")
            demoted.append(p['cell'])
        # Round 1 (process-holes M4): the may-move class exists only because L6 U2 moves the legacy band; a reverted U2 (or U1,
        # whose revert reverts U2 first) WITHDRAWS it — those captures must be decoded-pixel identical again (R4 / rule 5), and
        # R5's 0.002 band no longer hides a sub-0.002 leak of another lane into them.
        may_wd = []
        if r['lane'] == 'L6-android-paint-order' and r['unit'] in ('U2', 'U1') and lane.get('mayMove', {}).get('android'):
            may_wd = lane['mayMove']['android']
            lane['mayMove'] = {'android': []}
            lane['mayMoveWithdrawn'] = {'android': may_wd, 'by': f"{r['unit']} reverted at {r['run']} (rule {r['rule']})"}
        out.append(dict(r, withdrawnPredictions=gone, mustNotMoveAfter=gone, demotedPredictions=demoted, withdrawnGeometryKeys=geo,
                        carriersWithdrawn={'captures': {p: [s for s in unit['captures'][p] if s not in still[p]] for p in PLATS},
                                           'wire': [s for s in unit['wire'] if s not in still_wire]},
                        **({'mayMoveWithdrawn': {'android': may_wd}} if may_wd else {})))
    for name, lane in lanes.items():
        for p in lane['predictions']:
            if p['cell'] in RESTATE:
                p.update({k: v for k, v in RESTATE[p['cell']].items() if k in ('to', 'floor', 'floorNA', 'confidence', 'gating', 'kind', 'direction', 'directionWhy')})
                p['restated'] = RESTATE[p['cell']].get('why', 'PLAN §10')
    for name, lane in lanes.items():
        for p in lane['predictions']:
            need = [u for u in p.get('requires', p['units']) if (name, u) in rev]
            if p['gating'] and need:
                assert p['cell'] in RESTATE and 'gating' in RESTATE[p['cell']] and 'why' in RESTATE[p['cell']], \
                    f'{name} {p["cell"]}: gating, but its required unit(s) {need} are reverted — demote it or RESTATE it with a why'
                assert p.get('floor') == PASS, f'{p["cell"]}: RESTATE re-gates it without the {PASS} floor'
    return {'reverted': out} if out else None

# ── the read-out of a device probe, base FIRST everywhere (wave-54 fix r2, R2-M2), restricted to its sections (C15) ───
# Round 1 (process-holes S2): every read writes a RECORD under wave55-gate/<stage>/<the full run id>/ — a D13 re-probe
# (wave55-pre-r1, -r2) gets its own directory and never overwrites stage 1's; (M2) every probe reads the installed-build
# hash while its devices are up, so a probe that decides a revert is evidence, not a score (BACKLOG standing constraint).
def HASH_READ(run, what):
    # Round 2 (process-holes S10): gate-driver.sh APPENDS to driver.log and provisions at EVERY launch (--resume, the
    # --skip-corpus net launch), so `until grep 'attempt 1 starting'` returned at once on a second launch of the same run id:
    # count the launches BEFORE starting this one, wait for a NEW one (bounded: 1800 s, ab-arms55.sh's AB_WAIT_MAX_SEC),
    # and label the block `<run> corpus launch (…, launch k)` — the label ab-arms55.sh's --against check reads (instrument-teeth
    # S4). (S9) both bundles embed the last test-all IR: compare digests only with the same `asset sha1` line, which
    # installed-build-hash.sh prints in every block.
    log = f'tools/titan/runs/{run}/gate-driver/driver.log'
    return (f"BEFORE the launch: n=$(grep -c 'attempt [0-9]* starting' {log} 2>/dev/null); n=${{n:-0}}; AFTER starting it: t=0; "
            f"until m=$(grep -c 'attempt [0-9]* starting' {log} 2>/dev/null); (( ${{m:-0}} > n || t >= 1800 )); do sleep 15; t=$((t+15)); done; "
            f"if (( ${{m:-0}} > n )); then {G55}/installed-build-hash.sh \"{run} corpus launch ({what}, launch $((n+1)))\"; "
            "else echo 'no new launch within 1800 s: no hash read — record why'; fi   "
            f"(one-shot per LAUNCH, right after provision rc=0 while the devices are up; then commit {G55}/build-hashes.txt with NAMED paths "
            "before the next device run: its `HEAD` line is the tree this probe ran — ab-arms55.sh reads the run's FIRST corpus-launch block "
            "for --against; the iOS digest and the apk sha1 are comparable across launches only with the same `asset sha1` line: both "
            "bundles embed the last test-all IR)")

def PROBE_READOUT(run, sections, stage1=False):
    secs = sections if isinstance(sections, str) else ','.join(sections)
    d = f'{G55}/{"stage1" if stage1 else "stage2"}/{run}'
    return {
      'order': f'base first everywhere: {BASE} is the PREVIOUS / EXCLUDE run, {run} the CURRENT / INCLUDE run, so every Δ printed is {run} − {BASE} (a gain is +)',
      'records': f'mkdir -p {d}; every read below writes there (a re-probe has its own run id, so its own directory: nothing is overwritten)',
      'abDiff': (f'node tools/titan/results/wave52-gate/ab-diff.mjs {BASE} {run} --threshold 0.002 > {d}/abdiff.txt'
                 f'   (per-cell flips and |Δ| >= 0.002; sections absent from {run} print "not compared")'),
      'score': (f'node tools/titan/score-gate.mjs {BASE} {run} --watch {P55}/watchlist.txt --movers 0 '
                f'--json {d}/score.json > {d}/score.movers0.txt'),
      'floors': (f'node {P55}/probe-readout.mjs {d}/score.json --sections {secs}' + (' --stage1' if stage1 else '') + f' > {d}/floors.txt'
                 + '   (revert rules 1, 2, 3 over every carrier, prediction row and non-carrier cell of THESE sections; exit 0 none fired, 1 fired (units named with their revertOrder), 2 usage / wrong order / not --movers 0 / an unknown --lanes, 3 a listed section missing or a gating cell unmeasured)'),
      'controls': (f'node {G55}/control-check.mjs {BASE} {run} --sections {secs} --json {d}/controls.json > {d}/controls.txt'
                   '   (R4 / R4b over these sections, with the may-move and ring-fence classes: rule 5; a pre capture or wire document absent after is a LEAK; exit 2 when a listed section is absent or compares 0 captures)'),
      'geometry': (f'{PY} {P55}/geometry-gate.py {run} --base {BASE} --sections {secs} --json {d}/geometry.json > {d}/geometry.txt'
                   + '   (rule 4; keys of other sections print NOT PROBED and gate nothing; exit 0 = every gating key of a unit on the tree in these sections PASS; 1 a gating FAIL; 2 usage / STALE / a --sections or --lanes entry that matches nothing / 0 keys in scope; 3 a gating key of these sections UNMEASURED)'),
    }

# Round 1 (process-holes S7): counts the calibrations quote are computed from the rows, never typed (a probe decision changes them).
N_GATING_FLIPS = sum(1 for l in lanes.values() for p in l['predictions'] if p['gating'] and p['from'].startswith('f') and p['to'].startswith('P'))

# ── the A/B arms (round 1: generator-truth S1-S3, process-holes S8) ─────────────────────────────────────────────────
# Each arm names its lane, the unit(s) it drops, the run it is read AGAINST, and `drops`: what the arm tree reverts, child
# first — a landed contingency on the unit (it calls the unit's code: the arm tree would not build without its revert),
# every `<unit>-S1…` fix commit (resolved from git log when the arms file is built, PLAN §8), then the unit itself.
def drops(lane_id, *units_):
    lane = lanes[lane_id]
    # a landed contingency sits on the unit (after --contingency the unit's revertOrder lists it first): drop it before
    child = [c['unit'] for c in CONTINGENCY if c['lane'] == lane_id and any(c['unit'] in lane['revertUnits'][u]['revertOrder'] for u in units_)]
    return [x for c in child for x in (f'{c}-S1*', c)] + [f'{u}-S1*' for u in units_] + list(units_)
ARMS = [
  {'arm': 'drop-NB-ios', 'lane': 'L1-hyphen-ios-linebox', 'units': ['NB-ios'], 'when': 'closing gate', 'against': 'wave55-final',
   'sections': ['css-text', 'css-text-decor'], 'platforms': ['ios'], 'drops': drops('L1-hyphen-ios-linebox', 'NB-ios'),
   'expect': ('exactly 6 cells differ (ab-diff lists a flip or |Δ| ≥ 0.002): hyphenate-character-001 / -002 / -003 / -004 ios, Δ (final − arm) ≈ '
              '+0.017 / +0.016 / +0.019 / +0.027 (-004 a flip), and text-decoration-dotted-001 / -002 ios ≈ +0.004 / +0.005; boundary-shaping-001…008 '
              'ios within 0.0002 (not listed); hyphens-vertical-001 ios byte-identical (D22: the vertical guard declines it); any other differing iOS '
              'cell is a leak. With NB-ios-b landed the arm drops NB-ios-b, then NB-ios: the same 6 cells')},
  {'arm': 'drop-L4-U2', 'lane': 'L4-ua-heading-margin', 'units': ['U2-ios'] if L4_GO_SMALL else ['U2-ios', 'U2-android'], 'against': 'wave55-pre',
   'when': ('probe time: right after the wave55-pre read-out, BEFORE any stage-1 revert or swap commit (HEAD == the wave55-pre tree, '
            'proved by its hash block; ab-arms55.sh refuses otherwise — round 1, S5)'),
   'sections': ['css-text-decor'], 'platforms': ['ios'] if L4_GO_SMALL else ['ios', 'android'],
   'drops': drops('L4-ua-heading-margin', *(['U2-ios'] if L4_GO_SMALL else ['U2-ios', 'U2-android'])),     # round 2 (S7): GO-SMALL narrows the arm
   'expect': ('the arm is U1 alone (arm M): 005 ios ≈0.9167 / android ≈0.9121; 006 ≈0.9164 / ≈0.9117; 014 ≈0.9336 / ≈0.9328 — every one up from '
              'wave55-open and f (MED-HIGH); read against wave55-pre, Δ (pre − arm) on those six cells is U2\'s own contribution; the inset-011 '
              'natives are identical (U2 stands down on 011)')},
  {'arm': 'drop-L6-U2', 'lane': 'L6-android-paint-order', 'units': ['U2'], 'when': 'closing gate', 'against': 'wave55-final', 'platforms': ['android'],
   'sections': in_order({section_of(s) for s in L6['revertUnits']['U2']['captures']['android']} | {section_of(s) for s in _shared} | {'filter-effects'}),
   'drops': drops('L6-android-paint-order', 'U2'),
   'expect': ('the 25 U2 carriers return to wave55-open (crossings back); C2: the shared L2 / L3 stems (sharedStems) show what L6 U2 does to them — their '
              'own lanes\' keys are read on the closing run; filter-effects (round-1 S2): U1\'s clip-rect / clip-rect-zoom identical (U1 stays) and '
              'basic-blur android shows D2\'s ring-fenced band move (report only); L6 may-move stems OUTSIDE these sections are attributed by '
              'mayMoveRule, not by this arm')},
]
assert len({a['arm'] for a in ARMS}) == len(ARMS), 'two A/B arms share a name: their run id wave55-ab-<arm> would collide (ab-arms55.sh refuses)'
assert all(a['against'] in ('wave55-final', 'wave55-pre') for a in ARMS)

expectations = {
  'wave': 55,
  'readAgainst': RUN,
  'base': BASE,
  'interpreter': PY,
  'scoreOfRecord': (f'node tools/titan/score-gate.mjs {BASE} wave55-final --watch {P55}/watchlist.txt'
                    f' --movers 0 --json {G55}/score-final.json > {G55}/score-final.movers0.txt'),
  'adjudicate': f'node {G55}/adjudicate.mjs {G55}/score-final.json --geometry {G55}/geometry-final.json',
  'closingGateFiles': {
    f'{G55}/score-final.json': ('scoreOfRecord (--movers 0). Read by adjudicate.mjs (R1-R5, R6 read-out) and by nothing else; adjudicate.mjs refuses every '
                                f'other record: exit 2 ORDER (prev is not {BASE}) or NOT A --movers 0 RECORD'),
    f'{G55}/score-final.movers0005.json': (f'scoreReadout (--movers 0.005). Read by {G55}/make-snapshot55.mjs (corpusSnapshotSource) and the PR mover list ONLY; never '
                                           'adjudicated (adjudicate.mjs exits 2 on it; make-snapshot55.mjs exits 2 NOT THE 0.005 RECORD on the --movers 0 one)'),
    f'{G55}/geometry-final.json': ('geometryGate.closingCmd. R6 is that command\'s EXIT; adjudicate.mjs --geometry reads its rows only to retire a '
                                   'degenerateByConstruction gain, and refuses it (exit 2 GEOMETRY PAIR) unless run/base equal the score record\'s cur/prev'),
  },
  'scoreReadout': (f'node tools/titan/score-gate.mjs {BASE} wave55-final --watch {P55}/watchlist.txt --movers 0.005 --json {G55}/score-final.movers0005.json'
                   f' > {G55}/score-final.txt   (the human-readable mover list for the PR; adjudicates nothing)'),
  'corpusSnapshotSource': (f'{G55}/score-final.movers0005.json (the <score.movers0005.json> of {G55}/make-snapshot55.mjs — D20, round-1 S4: the wave-55 '
                           'snapshot generator, refusing ORDER / NOT THE 0.005 RECORD / NO CELL REVIEW / NO INSTALLED BUILD; its perCellDiff.movers counts '
                           '|Δ| >= 0.005 as its scorer string says — score-final.json, the --movers 0 record, would publish ≈4000)'),
  # The wave-55 copies of control-check.mjs / adjudicate.mjs read three things the wave-54 copies did not; both are WRITTEN (this
  # wave's generator stage) and calibrated before they are trusted (generator.done.md §6).
  'readerChanges': {
    'control-check.mjs': (f'{G55}/control-check.mjs = the wave-54 copy with the expectations path → {P55}/expectations.json and two classes: a '
                          'changed capture of ringFenced.captures is "RING-FENCED — report plainly" (read FIRST: basic-blur android is also in L6 '
                          'mayMove, D2), one in unionMayMoveCaptures[<platform>] (or lanes.<id>.mayMove with --lane) is "may-move" (not a leak: its '
                          'score is bound by adjudicate R5 through mustNotMoveComplement)'),
    'adjudicate.mjs': (f'{G55}/adjudicate.mjs = the wave-54 copy with the expectations path → {P55}/expectations.json, --base default {BASE}, '
                       'R5 also walking mustNotMoveComplement.cells (labelled "complement"), and (round 2, generator-truth S2) R6 never retiring a '
                       'cell stayDegenerateEvenIfPass lists (inset-005 / -006 android stay DEGENERATE whatever their [F] row prints)'),
    'roundTwo': ('control-check.mjs refuses (exit 2) when a CARRIER capture is present on one side only (instrument-teeth S3: a carrier may change, '
                 'never vanish); every reader refuses a flag given without its value (exit 2, S5); geometry-gate.py refuses --base equal to the run '
                 'outside --self-test and a --base run without sections/ (S2)'),
    'calibrated': (f'{G55}/readers-calibration.sh → {G55}/readers-calibration.txt (K1-K14 control-check, J1-J6 adjudicate; each new branch is '
                   'shown load-bearing by a mutated copy run in a throwaway worktree)'),
  },
  'controlCalibrations': [
    {'pair': 'wave54-final -> wave54-final (= wave54-final -> wave55-open: the opening gate measured them byte-identical)', 'expect': 'HOLDS', 'record': f'{G55}/readers-calibration.txt K1'},
    {'pair': 'wave54-open -> wave54-final', 'expect': 'FAILS with leaks: wave 54\'s landed changes are not wave-55 carriers (the wave-55 carrier set can fail)', 'record': 'K2'},
    {'pair': 'both pairs, --lane L7-instruments (an EMPTY carrier set)', 'expect': 'HOLDS on the identity pair, FAILS on the wave-54 pair', 'record': 'K3'},
    {'pair': 'wave54-final -> a synthetic two-section post run (one pixel nudged on a may-move, the ring-fenced and a complement android capture)', 'expect': 'exactly 1 LEAK (the complement); may-move and RING-FENCED printed, not leaks; without the complement nudge it HOLDS; a copy without the may-move class FAILS it', 'record': 'K4-K6'},
  ],
  'adjudicateCalibrations': [
    {'input': 'the identity record relabelled wave55-open → wave55-final', 'expect': f'FAIL R4: the {N_GATING_FLIPS} gating flip rows missed (nothing landed)', 'record': f'{G55}/readers-calibration.txt J1'},
    {'input': f'all-met synthetic (the {N_GATING_FLIPS} gating f→P rows at P 0.97)', 'expect': 'R1-R5 hold, exit 0', 'record': 'J2'},
    {'input': 'all-met + one complement cell +0.003', 'expect': 'FAIL R5, labelled "complement"; a copy without the complement read does not see it (exit 0)', 'record': 'J3, J5'},
    {'input': 'a record whose prev is not the base; a --movers 0.005 record relabelled', 'expect': 'exit 2 ORDER; exit 2 NOT A --movers 0 RECORD', 'record': 'J4, J4b'},
  ],
  'openingGate': {
    'cmd': f'node tools/titan/score-gate.mjs wave54-final {BASE} --watch {P55}/watchlist.txt',
    'expect': '0 gained / 0 lost / 0 movers / 0 newly measured / 0 unmeasured-now over 4096 cells; fixture net exit 0 on all 9 gate fixtures (all-then-color against its committed baselines since wave-54 U2-seed)',
    'alsoRun': [f'RUN={BASE} WATCH={P55}/watchlist.txt node tools/titan/results/wave52-plan/watchlist-check.mjs  -> unmatched 0',
                f'per-test IR wave54-final -> {BASE} byte-identical (the lanes\' verbatim pins come from wave54-final)',
                f'node {G55}/control-check.mjs wave54-final {BASE}  -> 0 changed, 0 renumbered, 0 leaks (same host)'],
    'onDivergence': 'the first divergence is wave 55\'s first item: PLAN §10 restates every affected prediction against wave55-open BEFORE any lane lands',
    'recorded': (f'{G55}/_note.md and score-open.* / control-open.* (commit 640276a0): 0 gained / 0 lost / 0 movers at any Δ / 0 newly measured / '
                 '0 unmeasured-now over 4096 cells; control identity 1435/1435 on web, iOS, Android and the wire; fixture net exit 0 ×9 — so every '
                 '"from" this generator verified against wave54-final holds against wave55-open; no restatement is needed (§10 empty)'),
  },
  'stage1Probe': {'runId': 'wave55-pre', 'sections': STAGE1,
                  'cmd': 'tools/titan/gate-driver.sh wave55-pre --sections ' + ','.join(STAGE1) + ' --skip-fixture-net',
                  'hashRead': HASH_READ('wave55-pre', 'stage-1 probe'),
                  'decides': sorted(f'{n} {u}' for n, l in lanes.items() for u in l['revertUnits'] if any(s for p in ('ios', 'android') for s in l['revertUnits'][u]['captures'][p])),
                  'readWith': 'the UNION carrier set and the may-move / ring-fence classes (C1); one run for every lane\'s §8 device probe (the briefs named five run ids: wave55-cbb-probe, wave55-pre, wave55-l5-probe, wave55-l6-probe and two unnamed); each lane decides by its stage1Decision on the keys of these sections; a key or row of another section prints NOT PROBED / is skipped (C15)',
                  'unitCoverage': stage1_cover,
                  'readOut': PROBE_READOUT('wave55-pre', STAGE1, stage1=True),
                  # D13 re-probes (an NB-ios-b swap, the L5 LRM re-cut, a re-probe after a rule-6 step): pre-registered ids,
                  # each with its own hash read and its own record directory (round 1, M2 / S2)
                  'reprobes': {rid: {'cmd': f'tools/titan/gate-driver.sh {rid} --sections <sections> --skip-fixture-net',
                                     'sections': '<the sections of the units it re-decides (default: the stage-1 union)>',
                                     'hashRead': HASH_READ(rid, 'stage-1 re-probe'),
                                     'readOut': PROBE_READOUT(rid, '<sections>', stage1=True)}
                               for rid in ('wave55-pre-r1', 'wave55-pre-r2')}},
  'probeRun': {'runId': 'wave55-probe', 'sections': PROBE,
               'cmd': 'tools/titan/gate-driver.sh wave55-probe --sections ' + ','.join(PROBE) + ' --skip-fixture-net',
               'hashRead': HASH_READ('wave55-probe', 'stage-2 probe'),
               'readOut': PROBE_READOUT('wave55-probe', PROBE),
               'notProbed': {s: {'css-color': 'no carrier; two L6 may-move stems (|Δ| ≤ 0.002, R5 at the closing gate) and the L3 identity control `[C] composited-filters-under-opacity android` (a host-ACTIVE LTR parent: identity by construction under U1 and U2) — read at the closing gate (R4 / R7), as wave 54 read its unprobed sections',
                                 'css-pseudo': 'no carrier, no key, no may-move stem; the closing gate R4 / R7',
                                 'css-sizing': 'no carrier, no key, no may-move stem; the closing gate R4 / R7',
                                 'css-ui': 'no carrier, no key, no may-move stem; L3\'s host-active box-sizing-001 / -003 / -005 are identity by construction (the closing gate R4 / R7)'}.get(s, 'UNEXPECTED: no reason recorded')
                             for s in NOT_PROBED},
               'keysFirstReadAtTheGate': [f'{s} {k}' for s, k in _unprobed_keys]},
  'geometryGate': {'cmd': f'{PY} {P55}/geometry-gate.py <run> --base {BASE} [--sections a,b]',
                   'closingCmd': (f'{PY} {P55}/geometry-gate.py wave55-final --base {BASE}'
                                  f' --json {G55}/geometry-final.json > {G55}/geometry-final.out.txt'),
                   'what': ('runs every lane\'s geometryProbes (one pinned interpreter: `interpreter`), prints one PASS/FAIL per expect key, names the unit each '
                            'failing gating row reverts (rule 4), exits 1 on a gating FAIL or a probe self-check failure, 2 on a STALE json, 3 when no gating key '
                            'FAILs but one is UNMEASURED; with --sections a key whose keyCells section is not listed prints NOT PROBED and gates nothing (C15). '
                            'R6 = exit 0 with no --sections. A CONTROL FAIL never changes the exit; controls that sit on CARRIERS (L1\'s 3 hyphenate-character ios, '
                            'L5\'s 2 rtl-marker-bake / lists-bakes natives, L4\'s ua-heading-face block-in-inline / web rows on non-carriers excepted) are read in the '
                            'cell review and named in the PR. Round 6 (D40): every GATING and pictureCorrectTally key also carries the RESIDUAL read (residual-read.py, one '
                            'declaration per key cell): outside the key\'s declared change zone the capture = the --base capture pixel for pixel, where the base\'s class ink '
                            'left only background shows, and inside the zone every changed px is background, a declared ink class or its declared anti-aliasing — '
                            'tolerance 0 (every fixed picture reads 0 / 0 / 0: fix-r6.zones.out.txt); it only turns a probe PASS into FAIL (`residual: …` on the row, '
                            'UNMEASURED when the base capture is missing), and a gating / tally key with no declaration is a refusal (exit 2)')},
  'revertRule': [
    f'1 lost: a carrier cell (any lane, any tier, predicted or not) that is P on {BASE} and f on the probe reverts the commit that carries it',
    ('2 moved down: Δ <= -0.002 (base → probe) on any prediction row whose direction is "up-or-stay" reverts the commit that carries it; a '
     '"down-preregistered" row fires at Δ <= predictedDelta − 0.002 (D5); an "undirected" row is EXEMPT (looked at, named with its cause, never a '
     'revert by itself). Rule 2 is read AT THE PROBES (probe-readout.mjs); at the closing gate adjudicate.mjs enforces the gating floors (R4) and '
     'must-not-move (R5) only'),
    ('3 floor: a gating prediction below its floor (0.95, every gating row) or not the predicted verdict reverts the commit that carries it; a row '
     'demoted by a probe decision has no floor; an f→f row has floorNA (C11)'),
    ('4 geometry: a geometryProbes gating key whose line does not end in its exact expect string (or lacks its contains string) reverts the unit '
     'geometry-gate.py names; an UNMEASURED gating key of a probed section is not a pass (exit 3: re-run the section)'),
    ('5 leak: a composed capture outside every carrier set, outside L6 mayMove and outside the ring-fence that differs in decoded pixels (R4), or a '
     'per-test IR document that differs in bytes and is not "renumbered" (R4b: no wire carrier this wave), reverts the commit it is bisected to'),
    ('6 unit: the commit (lanes.*.revertUnits); a cell carried by several commits of one lane reverts them in revertOrder (latest first), '
     're-probing between steps. Dependencies: L1 NB-ios before the wave-54 U3b (never U3b alone); L2 U2 ↔ U1 a pair; L3 U2 before U1; L4 '
     'U2-<p> before U1-<p>; L5 D before U2o (its SUFFIX NOTE is true only with U2o: round-1 S1), U2i before U2o; L6 U2 before U1. Every rule-6 '
     'state is compiled and unit-tested by its lane before stage 1 (round-1 S12: revertStatesCompiled in the lane note)'),
    ('6x shared stems (C2): carriers are disjoint by construction; L6\'s raw may-move set holds 12 stems that are L2 / L3 carriers '
     '(lanes.L6-android-paint-order.sharedStems) — a move there is the carrier lane\'s, attributed by the drop-L6-U2 arm; never an L6 revert by '
     'itself'),
    '7 stage 1: every lane is decided at wave55-pre by its stage1Decision on the stage-1 sections; a unit reverted there is withdrawn (probeDecisions) before wave55-probe',
    ('8 contingency: L1 NB-ios-b and L5 U1-ios-LRM land only on their triggers (lanes.*.contingencyUnits; an appended probe-decisions/contingency.json '
     'row; land-units.sh --contingency <L>:<U>); L4 GO-SMALL (probe-decisions/holds.json goSmall.held) holds U2-android and is decided BEFORE the '
     'landing only — after the landing the same outcome is a rule-4 revert row of U2-android, never holds.json (round 2, process-holes S7)'),
    'tier: a MED or lower row without a gating key carries no floor; a shortfall against its magnitude is read and labelled. Rules 1 and 5 bind every row; rule 2 every up-or-stay / down-preregistered row',
  ],
  # Round 2 (process-holes S1 / S4 / S5): an EXECUTABLE string. (1) code differing from HEAD — tracked, staged OR UNTRACKED
  # (most units CREATE files), root Package.swift included (the iOS harness builds it) — refuses; (2) under 20 GiB free refuses
  # (section-runner's watchdog trips under 8 GB; every Swift-building worktree keeps its own DerivedData); (3) every lane tree's
  # Gradle daemons stop: the TREES: lines AND every registered worktree named /wave55- (PLAN §0 D18 names them so), the shared
  # tree as PROJECT_ROOT — kill_own_gradle_daemons spares a daemon that served ANY unlisted root, so one missing root keeps them
  # all alive.
  # Round 3 (verify M1): the round-2 string ended in a prose note — `bash -n` read a syntax error (`(` / an apostrophe), so run
  # verbatim NOTHING ran and it exited 2 like a refusal. The prose is now its own field (beforeEveryDeviceRunNote); the string
  # exports PROJECT_ROOT (the shared tree, a constant: the generator's own location would differ in a throwaway dry run) and
  # every path in it is absolute, so it runs from any cwd; `bash -n` on it is asserted below (syntax only: -n never executes).
  'beforeEveryDeviceRun': (f'export PROJECT_ROOT={SHARED_ROOT}; '
                           '[[ -z "$(git --no-optional-locks -C "$PROJECT_ROOT" status --porcelain --untracked-files=all -- runtimes apps converter tools/titan Package.swift '
                           '\':(exclude)tools/titan/results\' \':(exclude)*.md\')" ]] || { echo "the tree differs from HEAD in code (tracked, staged or untracked): '
                           'a device run would build it (round-1 S9, round-2 S1)"; exit 2; }; '
                           '(( $(df -g "$PROJECT_ROOT" | awk \'NR==2{print $4}\') >= 20 )) || { echo "disk < 20 GiB free (round-2 S5)"; exit 2; }; '
                           'source "$PROJECT_ROOT/tools/titan/own-processes.sh" && kill_own_gradle_daemons "$PROJECT_ROOT" '
                           '$(sed -n \'s/^TREES: *\\([^ ]*\\).*/\\1/p\' "$PROJECT_ROOT"/tools/titan/results/wave55-*/_note.md 2>/dev/null) '
                           '$(git --no-optional-locks -C "$PROJECT_ROOT" worktree list --porcelain | sed -n \'s/^worktree //p\' | grep -E \'/wave55-\')'),
  'beforeEveryDeviceRunNote': ('Run the string verbatim with bash from any directory — e.g. bash -c "$(jq -r .beforeEveryDeviceRun '
                               f'{SHARED_ROOT}/{P55}/expectations.json)" — exit 2 = refused (code differs from HEAD, or under 20 GiB free), '
                               'exit 0 = both checks hold and the Gradle daemons of the shared tree, of every TREES: line and of every '
                               'registered /wave55- worktree are stopped. Never ./gradlew --stop: the /wave55- filter keeps other '
                               'sessions\' worktrees out, and kill_own_gradle_daemons leaves (and names on stderr) any daemon that also served '
                               'an unlisted checkout, so no foreign process is stopped. Bash only (it uses [[ ]] and (( )); not sh).'),
  'wireRenumbering': {
    'rule': ('Unchanged from wave 53 (BACKLOG "Wave 53 lessons"): a per-test IR document byte-different ONLY because an earlier content-changed '
             'document of the same section changed its component count — identical once the id counter is stripped from id / slot.parent, every id '
             'shifted by the same amount, equal to the running component-count delta of the content-changed documents before it — is RENUMBERED, not a leak.'),
    'expectedShadow': {},
    'noShadowFrom': 'every wave-55 unit: L1-L6 change no converter / extractor byte; L7 U5 is predicted wire-identical and lands only behind [W-L7a] (content-changed 0, renumbered 0)',
  },
  'expected': {'lost': [], 'unmeasuredNow': [], 'newlyMeasured': [], 'missingSections': 0, 'columnShorts': 0,
               'fixtureNet': 'exit 0 on all 9 gate fixtures against committed baselines; no ledger edit'},
  'abArms': ARMS,
  'abRead': {'runId': 'wave55-ab-<arm>', 'runner': (f'{G55}/ab-arms55.sh <arms-file> --run --against <arm.against> (L7 U3; records written only after a verified restore; '
                                                    'refuses an --against run whose hash block names a tree that differs from HEAD in code — round 1, S5)'),
             'armsFile': ('one line per arm, `<arm> <sections,> <platforms,> <sha…>`, the shas child first = the landed commits of the arm\'s `drops`, resolved '
                          'by subject (`wave55 <L> <unit>: ` / `wave55 <L> <unit>-S1…`) from git log on the tree the arm runs; one arms file per `against`'),
             'cmd': 'node tools/titan/results/wave52-gate/ab-diff.mjs wave55-ab-<arm> <arm.against> --threshold 0.002 --platforms <arm.platforms>',
             'order': 'arm (exclude) first, <arm.against> (include) second: Δ = against − arm = what the dropped unit(s) do on device'},
  'unionCaptureCarriers': union,
  'unionWireCarriers': sorted(wire_owner),
  'unionMayMoveCaptures': {p: (L6['mayMove'].get(p, []) if p == 'android' else []) for p in PLATS},
  'mustNotMoveComplement': {'rule': ('C1: every scored cell of wave54-final outside the union capture carriers and the ring-fenced test, not already named '
                                     'by a lane\'s mustNotMove; within 0.002, never lost / gained (adjudicate R5). L6 may-move cells are here or in a lane\'s '
                                     'named list (round-2 generator-truth N1): their 0.002 band IS this rule'),
                            'count': len(complement), 'cells': complement},
  'degenerateByConstruction': [INS['005'], INS['006']],
  'degenerateRetirement': ('a text-decoration-inset-005 / -006 gain on platform p is faithful only when U1-p and U2-p are on the closing tree AND '
                           'ua-heading-margin.geometry.py prints "→ GEOMETRY OK" on its [F] row (iOS wraps at the containing block; Compose does not, so '
                           'the android rows stay DEGENERATE this wave)'),
  'degenerateRetirementCheck': {'lane': 'L4-ua-heading-margin', 'script': 'ua-heading-margin.geometry.py', 'lineEndsWith': '→ GEOMETRY OK',
                                'units': {'web': [], 'ios': ['U1-ios', 'U2-ios'], 'android': ['U1-android', 'U2-android']},
                                'stayDegenerateWins': ('round 2 (generator-truth S2): adjudicate.mjs R6 reads stayDegenerateEvenIfPass FIRST — a listed cell '
                                                       '(inset-005 / -006 android this wave) is never retired, whatever its [F] row prints; `units.android` '
                                                       'records which units the cell needs, not a retirement path'),
                                'keys': {INS[n]: f'[F] text-decoration-inset-{n}' for n in ('005', '006')}},
  'stayDegenerateEvenIfPass': [
    f'{HC[2]} ios (TextKit emergency breaks tati/on, atio/n: not hyphenation positions)',
    f'{DOF["rtl-002"]} android (the ref is post-script; the wire lacks the scripted inset)',
    f'{DOF["vrl-rtl-002"]} android (the ref is post-script; the wire lacks the scripted inset)',
    f'{MC4} android (its red stays visible)',
    f'{INS["005"]} android (no wrap: Compose measures the abspos heading unbounded, "fox" clipped at x390)',
    f'{INS["006"]} android (no wrap: as 005)',
    f'{CS} ios (rows 3-6 and 9-12 until [H] [C] [T] [K] ios print OK: lanes.L5-counter-suffix-rows.degenerateRetirement)',
    f'{CS} android (rows 5-6 until [C] [K] android print OK)',
    # D3: the 10-px RTL static-position residue (both natives, cbb-android R6) keeps a red strip in U1's picture.
    f'{APOS["htb-rtl"]} android (U1 paints the iOS line: green x16-105, red x106-115 — the RTL static-position residue, both natives)',
    f'{APOS["vlr-rtl"]} android (as htb-rtl)',
    f'{APOS["vrl-rtl"]} android (as htb-rtl)',
  ],
  'recordedWall': ['css-anchor-position/anchor-position-multicol-007.html android'],
  'ringFenced': {'test': RING, 'cells': ring_cells, 'captures': {p: [stem(RING)] for p in PLATS},
                 'rule': ('report only, never a target, never carved out by name in code; not a carrier, not in any must-not-move list. D2: L6 U2 '
                          'moves its opacity-0.6 bordered box\'s band inside the opacity group (census (2b)) — a generic mechanism: an android move is '
                          'reported plainly in the PR, never claimed, never reverted for')},
  'lanes': lanes,
}

# ── the PLAN §1 tally "passing cells made picture-correct with no change of verdict", one entry per cell with the
# geometry key(s) that alone may certify it at the closing gate (a native is picture-correct only from a device capture
# plus the family's probe; a replay never earns it). Hand-listed from the briefs' P→P rows; asserted below.
def tk(cell_, script, *keys): return {'cell': cell_, 'keys': [f'{script} {k}' for k in keys]}
LBS, CBG, PPG, CSG, APG = 'hyphen-ios-linebox.geometry.py', 'compose-wpt-content-box-cb.geometry.py', 'positioned-parent-slot.geometry.py', 'counter-suffix-rows.geometry.py', 'android-paint-order.geometry.py'
# Round 1 (instrument-teeth M8): an entry lists EVERY key that can tell its picture wrong — the L1 entries add the wave-54
# hyphenate-character controls (they catch a missing hyphenate-character the LINEBOX key reads past); the four grid [X]
# cells leave `full` for `paintOrderOnly` (their key reads paint order only, and the lane's own U2 replay leaves 22-26 abspos
# children 3-22 px off the ref with the yellow containers in place: a static-position residue, xpos_kzone.out.txt).
HCG = 'hyphenate-character.geometry.py'
TALLY = {
  'full': [{'cell': f'{HC[n]} ios', 'keys': [f'{LBS} hyphen-ios-linebox 00{n} ios', f'{HCG} hyphenate-character-00{n} ios']} for n in (1, 3)]
          + [tk(f'{APOS[k]} android', CBG, AP_KEY(k, 'android')) for k in ('htb-ltr', 'vlr-ltr', 'vrl-ltr')]
          + [tk(f'{NBR[k]} android', CBG, NB_KEY(k, 'android')) for k in ('', '-2', '-4')]
          + [tk(f'{BTAB} android', PPG, '[S] bidi-tab-001 android rtl-rows'), tk(f'{DOF["rtl-001"]} android', PPG, '[S] dynamic-offset-rtl-001 android'),
             tk(f'{DOF["vrl-rtl-001"]} android', PPG, '[S] dynamic-offset-vrl-rtl-001 android')]
          + [tk(f'{CS} ios', CSG, '[H] counter-suffix ios', '[C] counter-suffix ios', '[T] counter-suffix ios', '[K] counter-suffix ios'),
             tk(f'{CS} android', CSG, '[C] counter-suffix android', '[K] counter-suffix android'),
             tk(f'{CJK[5]} ios', CSG, '[I] cjk-decimal/css3-counter-styles-005 ios'), tk(f'{CJK[5]} android', CSG, '[I] cjk-decimal/css3-counter-styles-005 android')]
          + [tk(f'{CR} android', APG, '[PO] backdrop-filter-clip-rect android')]
          + [tk(f'{XT[k]} android', APG, f'[X] {k} android') for k in XK[:2]],          # flex safe-001 / -002: positioned right (bbox Δ0)
  'part': [],
  'paintOrderOnly': [tk(f'{XT[k]} android', APG, f'[X] {k} android') for k in XK[2:]],
}
assert len(TALLY['full']) == 2 + 6 + 3 + 4 + 3 and len(TALLY['paintOrderOnly']) == 4, (len(TALLY['full']), len(TALLY['paintOrderOnly']))   # L1 2 · L2 6 · L3 2+1 · L5 4 · L6 3 (+ 4 paint order only)
_keys = {f"{script_of(pr['cmd'])} {k}": ln for ln, l in lanes.items() for pr in l['geometryProbes'] for k in pr['expect']}
_pcells = {p['cell']: p for l in lanes.values() for p in l['predictions']}
for e in TALLY['full'] + TALLY['part'] + TALLY['paintOrderOnly']:
    for k in e['keys']:
        assert k in _keys, f'tally key missing from every geometryProbes: {k}'
        assert e['cell'].rsplit(' ', 1)[1] in k.split(), f'tally key platform differs: {e}'
    assert e['cell'] in _pcells and _pcells[e['cell']]['from'].startswith('P'), f'tally cell is not a P→P prediction row: {e["cell"]}'
expectations['pictureCorrectTally'] = dict(TALLY, rule='a cell is counted picture-correct at the closing gate ONLY when EVERY one of its keys prints "→ GEOMETRY OK" / "→ LINEBOX OK" on the closing run (geometry-gate.py); a pass whose keys are not all OK stays DEGENERATE and is not counted'
                                                ' — round 6 (D40): every key\'s geometry-gate.py row PASS, i.e. its probe line OK AND its residual read 0 / 0 / 0 (outside the cell\'s declared change zone the capture = wave55-open)',
                                           paintOrderOnlyRule=('paint order corrected; static-position residue: these cells are NEVER counted picture-correct — their [X] key '
                                                               'certifies only that the parent band paints behind the child (CSS 2.1 App. E); the abspos children sit 3-22 px '
                                                               'off the ref in the lane\'s own U2 replay (grid static-position alignment, not this lane). With every key OK the '
                                                               'PR may say "paint order corrected" for them, nothing more (round-1 M8, D23)'))

# Every non-gating key is either a CONTROL (expected to PASS today and to keep passing) or a REPORT key (a target
# expected WRONG today); report keys are listed per probe, controls are the rest (wave-54 fix r1, S5).
for ln, l in lanes.items():
    for pr in l['geometryProbes']:
        rep_ = set(pr.get('report', []))
        assert rep_ <= set(pr['expect']), f'{ln}: report key not in expect: {rep_ - set(pr["expect"])}'
        assert not rep_ & set(pr.get('gating', {})), f'{ln}: a key is both gating and report'
        assert set(pr.get('gating', {})) <= set(pr['expect']), f'{ln}: gating key not in expect'
        assert set(pr.get('contains', {})) <= set(pr['expect']), f'{ln}: contains key not in expect'
        assert not set(pr.get('dropped', {})) & set(pr['expect']), f'{ln}: a dropped key is still expected'
        for k in pr.get('requires', {}):
            assert k in pr['gating'], f'{ln}: requires names a non-gating key {k}'
            assert all(u in l['revertUnits'] for u in pr['requires'][k]), f'{ln}: requires names an unknown unit'
        for k, v in pr.get('gating', {}).items():
            assert all(u in l['revertUnits'] for u in v.split('|')), f'{ln}: gating key {k} names an unknown unit {v}'
            # a gating key reads a carrier of its own lane (a gate on someone else's capture would revert the wrong commit)
            t, p = pr['keyCells'][k].rsplit(' ', 1)
            assert owner[p].get(stem(t)) == ln, f'{ln}: gating key {k} reads {pr["keyCells"][k]}, a carrier of {owner[p].get(stem(t))}'

# The machine form of degenerateRetirement names exactly the degenerate cells, known units of its lane, and a geometry
# key of that lane's probe for every (cell, platform).
_rc = expectations['degenerateRetirementCheck']
assert set(_rc['keys']) == set(expectations['degenerateByConstruction']), 'degenerateRetirementCheck.keys != degenerateByConstruction'
assert all(u in lanes[_rc['lane']]['revertUnits'] for us in _rc['units'].values() for u in us) or L4_GO_SMALL, 'degenerateRetirementCheck names an unknown unit'
assert set(_rc['units']) == set(PLATS), 'degenerateRetirementCheck.units must name every platform'
_rcp = [pr for pr in lanes[_rc['lane']]['geometryProbes'] if script_of(pr['cmd']) == _rc['script']]
assert len(_rcp) == 1 and all(f'{k} {p}' in _rcp[0]['expect'] for k in _rc['keys'].values() for p in PLATS), 'degenerateRetirementCheck key missing from its probe'
assert all(_rcp[0]['expect'][f'{k} {p}'] == _rc['lineEndsWith'].lstrip('→ ') for k in _rc['keys'].values() for p in PLATS), 'retirement verdict string differs'

_pd = probe_decisions()                     # None at planning: no unit has been reverted yet
# Round 1 (process-holes S7 / E9): the calibration count is read AFTER the decisions (a demoted gating row is not a flip the
# identity record can miss), so a regeneration restates it — and readers-calibration.sh is re-run on the new sha256.
N_GATING_FLIPS = sum(1 for l in lanes.values() for p in l['predictions'] if p['gating'] and p['from'].startswith('f') and p['to'].startswith('P'))
expectations['adjudicateCalibrations'][0]['expect'] = f'FAIL R4: the {N_GATING_FLIPS} gating flip rows missed (nothing landed)'
expectations['adjudicateCalibrations'][1]['input'] = f'all-met synthetic (the {N_GATING_FLIPS} gating f→P rows at P 0.97)'
# Round 1 (process-holes M4): the union may-move set is read AFTER the decisions (a withdrawn class is empty here too).
expectations['unionMayMoveCaptures'] = {p: (L6['mayMove'].get(p, []) if p == 'android' else []) for p in PLATS}
# Round 1 (process-holes S8): an arm drops only units still on the tree — a reverted unit leaves its arm (the arm keeps the
# others, its `drops` regenerated); an arm with nothing left to drop moves to abArmsWithdrawn.
_rev = {(r['lane'], r['unit']): r for r in REVERTED}
_arms, _wd_arms = [], []
for _a in expectations['abArms']:
    _gone = [x for x in _a['units'] if (_a['lane'], x) in _rev]
    _why = '; '.join(f"{x} reverted at {_rev[(_a['lane'], x)]['run']} (rule {_rev[(_a['lane'], x)]['rule']})" for x in _gone)
    _left = [x for x in _a['units'] if x not in _gone]
    if not _gone:
        _arms.append(_a)
    elif _left:
        _arms.append(dict(_a, units=_left, drops=drops(_a['lane'], *_left), narrowedBy=_why))
    else:
        _wd_arms.append(dict(_a, withdrawnBy=_why))
expectations['abArms'] = _arms
if _wd_arms:
    expectations['abArmsWithdrawn'] = _wd_arms
_wd_keys = {f"{script_of(pr['cmd'])} {k}" for l in lanes.values() for pr in l['geometryProbes'] for k in pr.get('withdrawn', {})}
_tally_wd = [e for part in ('full', 'part', 'paintOrderOnly') for e in TALLY[part] if set(e['keys']) & _wd_keys]
expectations['pictureCorrectTally'] = dict({p: [e for e in TALLY[p] if not set(e['keys']) & _wd_keys] for p in ('full', 'part', 'paintOrderOnly')},
                                           withdrawn=_tally_wd, rule=expectations['pictureCorrectTally']['rule'],
                                           paintOrderOnlyRule=expectations['pictureCorrectTally']['paintOrderOnlyRule'])
if _pd:
    expectations['probeDecisions'] = _pd
if LOOKS:
    expectations['probeDecisions'] = dict(expectations.get('probeDecisions') or {}, looks=LOOKS)     # stage 2 (PLAN §10 item 17)
if CONTINGENCY:
    expectations['contingencyLanded'] = CONTINGENCY
# The honesty list and the rows agree (wave-54 S1 should-fix 8): each entry names a prediction row; each row whose kind
# says "DEGENERATE even if P" is in the list.
_pcells = {p['cell']: p for l in lanes.values() for p in l['predictions']}
_sd = {x.split(' (', 1)[0] for x in expectations['stayDegenerateEvenIfPass']}
assert _sd <= set(_pcells), f'stayDegenerateEvenIfPass names a cell with no prediction row: {sorted(_sd - set(_pcells))}'
_kd = {c for c, p_ in _pcells.items() if 'DEGENERATE even if P' in p_['kind']}
assert _kd <= _sd, f'a prediction kind says DEGENERATE even if P but stayDegenerateEvenIfPass omits it: {sorted(_kd - _sd)}'

# ── decisions taken against (or beyond) a brief: the PLAN §9 source (every briefs-audit conflict C1-C15 + D1-D7) ─────
expectations['planDecisions'] = [
  {'id': 'C1', 'conflict': 'five probe run ids; every lane\'s identity / "outside carriers" rule written against its own carriers (X7, X9, X11, X12, X13)',
   'resolution': 'ONE stage-1 run wave55-pre over the union of the briefs\' §8 sections (stage1Probe), read with the union carriers; every identity rule restated as the complement of the union (mustNotMoveComplement); L6 may-move keeps its own 0.002 band (unionMayMoveCaptures + R5)'},
  {'id': 'C2', 'conflict': 'L6 U2\'s revert rules would fire on L2 / L3 movers (X10)',
   'resolution': f'other lanes\' carriers are subtracted from L6 mayMove ({len(_shared)} stems: lanes.L6-android-paint-order.sharedStems); a move there is the carrier lane\'s, attributed by the drop-L6-U2 arm (abArms); without the arm they are read by their own keys only'},
  {'id': 'C3', 'conflict': 'L7 U5\'s counter-suffix / bidi-baked capture claim collides with L5 and L3 carriers (X3)',
   'resolution': 'L7 probe 2 (rtl-marker-bake as U5\'s control) is NOT registered; U5 is gated by [W-L7a] wire identity alone; L7\'s must-not-move drops counter-suffix ios / android and L3\'s three android carriers (printed exclusions)'},
  {'id': 'C4', 'conflict': 'wave-54 keys the wave-55 units break by design (X4, X5, X6)',
   'resolution': 'rtl-marker-bake [M] / lists-bakes counter-suffix: registered under L5 as controls, `rows 0-207 identical` dropped on ios / android (L5 [K] replaces it), kept on web; ua-heading-face: inset-011 natives DROPPED (superseded by L4 [M] 011, gating), the rest restated (block-in-inline android gating → control); flex-zero-gap-rules: the wave-54 file is not registered, L7\'s wave-55 copy carries 033 ×3 as CONTROL; the wave-54 must-not-move rows of the 8 inset natives are not carried (L4 carriers)'},
  {'id': 'C5', 'conflict': 'disclosure-styles in L1\'s R1 tripwire and L5\'s 44-capture identity read (X1, X2)',
   'resolution': 'iOS: L5 U1-ios carrier (identity predicted, a prediction row) — it leaves L1\'s tripwire (3 in-radius controls remain: anonymous-table-ws-001, boundary-shaping-010, hyphens-vertical-001 — all-prop-001 is outside the radius, D41); Android: L6 may-move — it leaves L5\'s list (the complement keeps its 0.002 band)'},
  {'id': 'C6', 'conflict': 'L3 control `[C] anchor-center-safe-rtl android` is unscored and L6 may-move (X8)',
   'resolution': 'dropped from L3\'s expect (`dropped`); the probe still prints it; the drop-L6-U2 arm attributes it'},
  {'id': 'C7', 'conflict': 'orphan O1: counter-style-table.mjs:21-23 SUFFIX NOTE', 'resolution': 'L5 unit D (comment-only, landed after U2o)'},
  {'id': 'C8', 'conflict': 'orphan O2: the ContainingBlock[dark-stage-content-box-bands] breadcrumb pin', 'resolution': 'a breadcrumb-assertion row in L2 U1\'s DynamicValueResolverTest rows, mutation: delete its markUnhandled'},
  {'id': 'C9', 'conflict': 'orphan O3: ContentBoxRelativeInflationTest.kt\'s stale "future work" phrase', 'resolution': 'the closing docs pass (orchestrator); L2\'s read-only list stands'},
  {'id': 'C10', 'conflict': 'SeamReachabilityTest.kt: three more seam-only Compose modules', 'resolution': 'the wave-52 rule extended: L4 UaPositionedHeadingMargin.kt (seam 1) and UAHeadingFoldGate.kt (seam 3), L5 ListMarkerSuffixGap.kt (seam 4) each carry their module line patch-borne in their own seam patch; round 1 (process-holes M1): each writer CUT ON HEAD AT ITS PINNED ANCHOR, ≥ 8 lines apart — L4 seams 1 → 3 after `"content/PseudoGeneratedBox.kt",` (:159), L5 seam 4 after `"images/ImageCandidateChain.kt",` (:167), L3 seam 1 → unit-U2 after `"transforms/TransformInheritance.kt",` (:182) — so they land in §4 order (L4 → L3 → L5) AND in any order, and every earlier unit still reverts (the end-of-list re-cut chain made the five lines adjacent: L4\'s revert and the drop-L4-U2 arm\'s `git apply -R --check` failed)'},
  {'id': 'C11', 'conflict': 'three geometry-gated prediction rows without a floor (F1-F3)', 'resolution': 'every P-predicted gating row floors at 0.95 (L4 inset-005 / -006 android included, still stayDegenerateEvenIfPass); every f→f row carries `floorNA` (L3 multicol-nested-001, L4 inset-011 ×2, and all other f→f rows); L3\'s "U1 only" row is `fallbackIfReverted` on dir-style-02a'},
  {'id': 'C12', 'conflict': 'intra-lane seam revert dry-runs', 'resolution': 'L4 proves seam 3 / 1 and 4 / 2 (dryRuns); L5 adds U1-ios reverted with U2i-ios applied (revertUnits.U1-ios.dryRun), both before stage 1'},
  {'id': 'C13', 'conflict': 'L2 quotes StyleApplier :702 / :758 / :786; L6 U2 adds lines above :758', 'resolution': 'L2\'s pins and note cite the chain by symbol (sizing → overflow clip → padding), never by line'},
  {'id': 'C14', 'conflict': 'probe interpreter', 'resolution': f'every probe cmd starts with `{PY}` (asserted)'},
  {'id': 'C15', 'conflict': 'geometry-gate.py --lanes narrows by lane, not section: a stage-1 read-out reports unprobed keys UNMEASURED', 'resolution': 'every expect key carries keyCells; geometry-gate.py and probe-readout.mjs take --sections'},
  {'id': 'D1', 'conflict': 'L4: GO (4 units, two DEGENERATE-labelled Android passes) or GO-SMALL', 'resolution': 'GO, the brief\'s recommendation; 005 / 006 android on stayDegenerateEvenIfPass and degenerateByConstruction (never a gain); probe-decisions/holds.json goSmall.held (D25; `--go-small` for dry runs) regenerates with U2-android held'},
  {'id': 'D2', 'conflict': 'L6 U2\'s pixel radius under-reported', 'resolution': 'census (2b): basic-blur\'s opacity-0.6 bordered box (no child, so outside the 252-document scan) moves under U2 — added to L6 mayMove by rule; ring-fenced, so report only (excluded from every must-not-move)'},
  {'id': 'D3', 'conflict': 'autopos *-rtl android rows: P with a red strip after U1', 'resolution': 'added to stayDegenerateEvenIfPass (the 10-px RTL static-position residue, both natives)'},
  {'id': 'D4', 'conflict': 'boundary-shaping ×8 ios: MED-HIGH with no floor, "undirected within 0.002"', 'resolution': 'floor 0.95, gating, direction up-or-stay (the replay pre-registers ±0.0001)'},
  {'id': 'D5', 'conflict': 'css3-counter-styles-005 android: a pre-registered −0.0018 fall on a gating row', 'resolution': 'direction down-preregistered, predictedDelta −0.0018: rule 2 fires at Δ ≤ −0.0038'},
  {'id': 'D6', 'conflict': 'HIGH-tier f→f rows (L2 semi-replaced ×3)', 'resolution': 'floorNA (C11), not gating; rule 2 binds'},
  {'id': 'D7', 'conflict': 'stage 2 sections', 'resolution': f'{len(PROBE)} sections: every section with a carrier, a geometry key, a named radius control or an L6 may-move stem, minus css-color (its {sum(1 for s_ in L6["mayMove"]["android"] if section_of(s_) == "css-color")} may-move stems and 1 identity key are read at the closing gate); not probed: {", ".join(NOT_PROBED)}'},
  # ── plan-skeptic round 1 (fix-r1.done.md): the decisions its fixes took, each encoded above ──
  {'id': 'D21', 'conflict': 'generator-truth M2: L6 may-move counts a band only when Border*Width.px > 0, but Compose paints logical sides, shorthands, width keywords and a visible style with no width (3-dp medium default)',
   'resolution': f'android-paint-order.maymove-medium.py mirrors BorderSideExtractor (reproducing the scan\'s 252 under the explicit rule): +{len(l6_may_medium)} documents added to L6 mayMove by rule (none crosses: no new carrier); the brief\'s census line amended'},
  {'id': 'D22', 'conflict': 'generator-truth M1: NB-ios as specified pins hyphens-vertical-001 ios (a stage-1 byte-identity control) to one 19-px box — the singleLineText proxy reads its space-less vertical run as one line while TextKit draws three',
   'resolution': 'usedLineHeight declines a non-horizontal run (seam passes PlaceholderLabel.horizontalWritingMode); pin P8 / mutation M8 on __2-320 verbatim; the Catalyst pin logs lineCountIsExact / renderedLineCount for the 5 SAME controls; the census line corrected, the cell stays a control'},
  {'id': 'D23', 'conflict': 'instrument-teeth M8: the tally claimed picture-correctness its keys cannot certify',
   'resolution': 'the four grid [X] cells move to pictureCorrectTally.paintOrderOnly (never counted picture-correct: their abspos children sit 3-22 px off the ref in the lane\'s own U2 replay); the L1 entries add the hyphenate-character keys; the L5 [K] key reads the item-text zone'},
  {'id': 'D24', 'conflict': 'instrument-teeth M1-M7 / S4 and M9: 41 of the 43 gating keys passed a plausible wrong picture built from their own fixed picture; probes-selfcheck held no rejection set',
   'resolution': 'every gating probe re-ruled (L2 green mass + four corners; L3 glyph profile / band mass / ring / box; L4 band lists, tops ±1; L5 glyph fills, the 、, the [K] item-text zone; L6 base bbox / fill / band, navbar mass; L1 base-line glyph identity) and probes-selfcheck.py §T holds the standing fakes (target ink removed, ±2/±3 px, a segment shortened, a count-preserving rearrangement) read through the real gate: exit 1 if one PASSes'},
  {'id': 'D25', 'conflict': 'process-holes S3: the regeneration inputs were unnamed, uncommitted and non-cumulative',
   'resolution': 'probe-decisions/{reverted,restate,contingency,holds}.json beside the generator, read by default and append-only (asserted against the installed expectations.json); generatedWith records argv and the sha256 of every input'},
  {'id': 'D26', 'conflict': 'process-holes M4 / S8: a probe revert of L6 U2 kept its may-move class and its arm',
   'resolution': 'probe_decisions() withdraws lanes.L6.mayMove (mayMoveWithdrawn) when L6 U2 or U1 is reverted, unionMayMoveCaptures is computed after it, and an A/B arm drops only units still on the tree (narrowed, or moved to abArmsWithdrawn)'},
  {'id': 'D27', 'conflict': 'process-holes S1: L5 unit D\'s SUFFIX NOTE is true only after U2o, but no revert order said so',
   'resolution': 'U2o-ios revertOrder [D, U2i-ios, U2o-ios], U2o-android [D, U2i-android, U2o-android] (plan-build.py and land-units.sh rows 22 / 24); rule 6 names it'},
  {'id': 'D28', 'conflict': 'generator-truth S1-S4 (owed D8 / D9 / D20): two arms named drop-U2, no per-arm against, a 4-cell expect that its own numbers contradict, an unobservable clip-rect clause, a stale snapshot reader',
   'resolution': 'arms drop-NB-ios / drop-L4-U2 / drop-L6-U2 with units, drops (child first: contingency, -S1 fix commits, unit) and against (wave55-pre for L4); names asserted unique; abRead.cmd reads <arm.against>; drop-NB-ios expects 6 cells; drop-L6-U2 adds filter-effects; corpusSnapshotSource / closingGateFiles name make-snapshot55.mjs'},
  # ── plan-skeptic round 2 (fix-r2.done.md) ──
  {'id': 'D29', 'conflict': 'generator-truth S1: three stage-1 decisions (L1, L3, L6) turned a whole-section identity clause into a revert of their own lane, although the union tree carries every lane\'s units',
   'resolution': 'each lane is reverted only on its own platform\'s evidence: L1 keeps the iOS clauses (web / Android css-text moves are rule-5 must-not-move for every lane, bisected), L3 reads Android selectors moves only when the rule-5 bisect lands on U1 / U2, L6 reverts U2 for an outside mover only when the bisect lands on it'},
  {'id': 'D30', 'conflict': 'generator-truth S2: degenerateRetirementCheck.units.android let adjudicate.mjs R6 retire inset-005 / -006 android gains that stayDegenerateEvenIfPass forbids',
   'resolution': 'option (a): adjudicate.mjs R6 reads stayDegenerateEvenIfPass FIRST and never retires a listed cell (degenerateRetirementCheck.stayDegenerateWins); calibration J6 (ios retires, android stays) and J6b (the copy without the read retires both)'},
  {'id': 'D31', 'conflict': 'instrument-teeth M1-M6: new fakes of the same four classes passed most gating keys (the round-1 rules were tuned to round 1\'s fakes), and the tally\'s non-gating keys had no fakes',
   'resolution': 'every lane probe re-ruled from measured passing pictures and fakes (L2 mask XOR ≤ 40 + fail ink ±2; L3 row profile, run order, boxes, red, others, parent; L4 crop identity vs --base and line-1 profile; L5 strokes, letter identity, row-9 zones, base-relative marker rows; L6 lost / extra subset vs the base); probes-teeth-r2.py ports the round-2 builders, every tally key must reject a fake too'},
  {'id': 'D32', 'conflict': 'instrument-teeth S2-S5: --base = the run passed vacuously; a vanished carrier HELD; the --against check read the net-launch block and ignored MISMATCH; value-less flags exited 1',
   'resolution': 'geometry-gate.py refuses --base = the run outside --self-test and a --base with no sections/; control-check.mjs refuses a carrier present on one side only; ab-arms55.sh reads the CORPUS-launch blocks and refuses MISMATCH / MISSING / two trees; every reader exits 2 on a value-less flag or an empty scope entry'},
  {'id': 'D33', 'conflict': 'instrument-teeth S1: line 1\'s right edge at ±12 rests on web\'s face (Δx1 −8 / +2 / −8) and passes a 10-px clip of the heading\'s last glyph; no device face was ever measured',
   'resolution': 'stage 1 measures Δx1 per platform and test from the device captures (every face-present line prints it) and re-sets X1_TOL = max |Δ| + 2 by a recorded probe edit + regeneration before wave55-probe (§10); until then the clip-10 fake is a NAMED in-tolerance exception of the standing set (PASS while X1_TOL ≥ 10, FAIL after), never silently tolerated'},
  {'id': 'D34', 'conflict': 'process-holes M1 / S6: the arm runner checked each commit\'s reverse alone against the current tree, refusing every arm with an -S1 fix or a landed contingency; the destructive-path self-tests cited older bytes',
   'resolution': 'ab-arms55.sh reverse-applies a non-chain list cumulatively in a private mktemp export (T5l / T5m, mutant M-onebyone; ab-arms55.revert-check-proof.out.txt on the current bytes); the arms file lists the shas in git log order; §8 step 6 re-runs land-units.selftest.sh and ab-arms55.selftest.sh on the current sha256s and commits both records before the landing'},
  {'id': 'D35', 'conflict': 'process-holes S1 / S4 / S5: no device-run guard saw an UNTRACKED source; the daemon roots could not find lane worktrees; no disk floor and no worktree cleanup',
   'resolution': 'beforeEveryDeviceRun is an executable string: git status --porcelain --untracked-files=all over the code paths + root Package.swift, a 20-GiB disk floor, kill_own_gradle_daemons over the TREES: lines and every registered /wave55- worktree; D18 names every lane / skeptic / fix / S1 / [W-L7a] worktree <scratchpad>/wave55-<role>-<lane> with its own -derivedDataPath, removed with its DerivedData at §8 step 9½; the hash block and the arm runner read untracked code too'},
  {'id': 'D36', 'conflict': 'process-holes S2 / S3 / S7: the L5 LRM re-cut had no encoding or landing row; an S1 fix patch tripped the stale-patch guard; GO-SMALL could not be taken after the landing and did not narrow the L4 arm',
   'resolution': 'L5 contingencyUnits U1-ios-LRM (land-units.sh row 30, --contingency <L:U>, prepended to every revertOrder holding its base); fix-S1-<U>.patch is a known lane-dir patch (still sha-named); GO-SMALL is a before-landing decision only (a U2-android revert row with goSmall.held is refused) and narrows drop-L4-U2 to U2-ios'},
  {'id': 'D37', 'conflict': 'process-holes S8-S12: final55-analysis.sh drifted silently on a regeneration; the iOS .app embeds the test-all IR like the apk; the hash wait fired on a resumed launch; two §6 caveats went false; a killed fix lane or mutation was not resumable',
   'resolution': 'the generated read-out refuses a foreign expectations sha (exit 2) and §7 step 6 / stage 2 regenerate it with readers-calibration; installed-build-hash.sh prints both asset sha1s and reads untracked code; HASH_READ counts launches (bounded 1800 s, label `<run> corpus launch (…, launch k)`); the false caveats are deleted; fix lanes write STATUS: PARTIAL first and every pin mutation is bracketed by MUTATING / RESTORED sha256 lines'},
  # ── plan-skeptic rounds 3 and 4 (fix-r3.done.md, fix-r4.done.md): the added-ink class ──
  {'id': 'D38', 'conflict': 'verify round 3 M2: a fifth class, ADDED ink (a row painted twice, a horizontal copy, a doubled glyph, a 2×2 / 3×3 blob in the band), passed the L3 dir-style rows and, swept over every lane, 112 faked captures of L1 / L4 / L5 / L6',
   'resolution': 'every lane probe reads how MUCH ink against a same-renderer reference (L1 `ink:`; L2 `core:`; L3 rows / mass / lead / yext, bidi `ink:`, stack `others:`; L4 `ink:` and `face ink:`; L5 [C] / [I] `ink:`; L6 `extra` ≤ 1, [PO] ±2); probes-teeth-r3.py pins the class and probes-selfcheck.txt §T3 sweeps it over every fixed picture (plan-skeptic-round-3.fakes/sweep.py: exit 1 on a HOLE)'},
  {'id': 'D39', 'conflict': 'verify round 4 M1-M4 / S1 / S2: STRICTLY-additive ink (nothing moved or erased) passed 17 gating keys inside their own read zones — L4 face-present ×10 (FACE_INK_TOL 6 %; the Compose no-wrap rows never read below band 1), L6 [X] ×5 (`extra` dilated by 1 px), L5 [H] ios (no ink read), L5 [T] ios (ink inside its digit window); the 2-px floor was unnamed',
   'resolution': 'L4 `face xor:` (face-present natives: every band = the face reference, two nested masks, XOR ≤ FACE_XOR_TOL 2; the reference is the frozen ref until the stage-1 step-8 edit sets FACE_RUN = wave55-pre, because a device 2em face sits 1768-2510 px level-XOR / −14 … +9 % dark px from Chromium\'s, so a re-set ref tolerance would re-open the class); L6 `extra` without a ring around the base fill; L5 [H] / [T] twin-glyph `ink:` (TWIN_RUN = wave55-pre from stage 1), [C] / [I] summed, two nested masks; L1 `ink:` summed, two nested masks; probes-teeth-r4.py pins every variant the verifier recorded IN-ZONE and probes-selfcheck.txt §T4 re-runs its generator over every lane (exit 1 on an IN-ZONE acceptance); a fake adding ≤ 2 px under a probe\'s own mask is the NAMED 2-px floor (the passing pictures read 0: a 0-px tolerance is a stage-1 device question); round 3\'s named 014 residual and D33\'s clip-10 exception are closed'},
  # ── plan-skeptic round 5 (plan-skeptic-round-5.verify.md) / round 6 (fix-r6.done.md): background-only paint ──
  {'id': 'D40', 'conflict': 'verify round 5 M1-M6: BACKGROUND-ONLY paint — 50 % copies of the class ink, ink in a colour the masks never test (black / #66ccff / #cccccc / yellow / iOS grey), lines in rows no XOR covers — passed 33 of 43 gating keys and 17 tally entries (704 fakes adding > 2 px: L2 ×8, L3 bidi / stack / dyn ×2 and the vrl-rtl tally entry, L4 ×12, L1 ×3, L6 [X] ×5 and [PO]); round 4\'s "class closed" held for its own variant set only',
   'resolution': 'geometry-gate.py applies the RESIDUAL read (residual-read.py) to every gating and pictureCorrectTally key: outside the key cell\'s declared change zone (the colour classes its probe reads pixel for pixel, or the probe\'s own row bands, capture ∪ base, plus windows recorded from the fixed pictures\' own diff by fix-r6.zones.py) the capture equals the --base capture pixel for pixel, a vacated px shows only background (never beside class ink that stayed), and inside the zone every changed px is background, a declared ink class or its declared anti-aliasing — tolerance 0, every fixed picture 0 / 0 / 0; a key with no declaration is a refusal. L3 dyn\'s magenta count is ±2 (a hole of the parent\'s own colour equals the base, so only the probe sees it). probes-teeth-r5.py pins the verifier\'s fake runs FAIL, probes-selfcheck.txt §T5 re-runs its generator (exit 1 on a > 2-px acceptance) and §R re-measures the declarations; round 3\'s named hyphenate-character hdup+2x blind spot is closed. The 2-px floor stays the named residual; the windows are replay / wave54-probe footprints, re-recorded from a device capture only after a stage-1 look (never a tolerance)'},
  # ── the pre-landing regeneration (regen.done.md; PLAN §8 step 4 ¶2, §10 items 3-8): the lane hand-offs, encoded above ──
  {'id': 'D41', 'conflict': 'pre-landing audit (wave55-gate/prelanding/citations.md, censuses-and-deliveries.md): after the plan was cut the lane hand-offs changed a census, a prediction, a probe zone and two decision rules — L6 census GROWTH 1 (display-contents-dynamic-pseudo-insertion-001 android) and two may-move pictures mayMoveRule did not name; L5 U3-ios repaints 6 glyphs in 4 rows, not 1 (the probe, the residual SPEC and the replays modelled 1: a correct U3 would have been reverted); L1 radius 58 / 18 with all-prop-001 outside, and a stage-1 rule that reverted NB-ios in the R1 world NB-ios-b was built for; L4 headingStandsDown kept (the plan said removed)',
   'resolution': f'L6: +{len(l6_contents_pseudo)} mayMove stem by an asserted rule (both lane censuses + the run-of-record IR; unscored: complement unchanged), mayMoveRule / stage1Decision name the opacity-group and UA-widget-replica pictures, U2\'s clip condition and P3h / P3i, U1\'s P5a-f; L5 (fix-r1 R1-R5): the probe carves U3\'s five run boxes out of ios\'s base-identity zones and reads them in [T] ios, residual-read.py declares them, the 10 standing replays re-graft them from Android\'s base (regen.replay-regraft.py), the counter-suffix ios row is MED and its stage1Decision pre-registers the [H] / [T] precedence (twin-only / residual → the look first; LRM only on an [H] geometry reason; a [K] FAIL inside U3\'s boxes is U3\'s) — proven by regen.l5-proof.py and probes-selfcheck.txt §RG; L1: carrierRule 58 / 18 / 4 SAME asserted from the lane census, the R1 signature routes to --contingency L1:NB-ios-b (never a revert of NB-ios) with a re-probe requiring the SAME controls byte-identical; L4: the U2 strings say kept; L2 clip-3 android stays MED-HIGH (§10). No carrier, unit, floor or must-not-move cell changed'},
]
# Round 1 (process-holes S3): what produced this file — the argv (minus --out, which never changes the content) and the
# sha256 of every input the generator read (the committed decision files included), so the committed tree reproduces it.
expectations = {'generatedWith': {'script': f'{P55}/plan-build.py', 'argv': _ARGV_RECORD, 'inputs': dict(sorted(INPUTS.items()))}, **expectations}
# Round 3 (verify M1): the device-run guard must PARSE as bash (the round-2 string's trailing prose did not: exit 2, nothing ran).
# `bash -n` reads the string from stdin and never executes it; no prose, no apostrophe-quoted note, may follow the last clause.
import subprocess
_bn = subprocess.run(['bash', '-n'], input=expectations['beforeEveryDeviceRun'], capture_output=True, text=True)
assert _bn.returncode == 0, f'beforeEveryDeviceRun does not parse as bash (bash -n exit {_bn.returncode}): {_bn.stderr.strip()}'
assert not re.search(r'\(never|\(the ', expectations['beforeEveryDeviceRun']), 'beforeEveryDeviceRun carries prose: it belongs in beforeEveryDeviceRunNote'
json.dump(expectations, open(os.path.join(OUT, 'expectations.json'), 'w'), indent=1, ensure_ascii=False)

# ── watchlist.txt ─────────────────────────────────────────────────────────────────────────────────────────────────────
out, written = [], set()
def block(title, lines):
    out.append(f'# {title}')
    for l in lines:
        if l not in written:
            written.add(l); out.append(l)

out += [
  '# tools/titan/results/wave55-plan/watchlist.txt — the wave-55 gate watchlist (every target, predicted mover and NAMED',
  '# must-not-move cell of PLAN.md §2; generated by plan-build.py from the family censuses + the briefs\' named cells, every',
  f'# line verified to be a scored cell of {RUN}). Consumed by:',
  f'#   node tools/titan/score-gate.mjs {BASE} <run> --watch {P55}/watchlist.txt',
  '# One watch per line: "<sec>/<path>.html <platform>". The ".html" suffix makes each line match exactly ONE test under',
  '# score-gate.mjs watchCells\' substring rule (asserted by plan-build.py). A line already written under an earlier lane is',
  f'# not repeated. The COMPLEMENT ({len(complement)} cells: every other non-carrier scored cell) is not watched line by line: it is',
  '# expectations.json mustNotMoveComplement, read by adjudicate.mjs R5 over the --movers 0 record, and by control-check R4.',
  f'# Precondition before any gate read-out: RUN={BASE} WATCH={P55}/watchlist.txt node tools/titan/results/wave52-plan/watchlist-check.mjs',
  '# must print "unmatched 0".',
]
pred_cells = lambda lane: [p['cell'] for p in lane['predictions'] if p['kind'] != 'non-corpus']
for name, lane in lanes.items():
    short = name.split('-', 1)[1]
    if pred_cells(lane):
        block(f'{name.split("-")[0]} {short} — targets + movers ({", ".join(lane["revertUnits"])})', pred_cells(lane))
    if lane['mustNotMove']:
        block(f'{name.split("-")[0]} {short} — must not move', lane['mustNotMove'])
block('RING-FENCED, report only (a generic mechanism may move it — L6 U2, D2; never a target, never carved out)', ring_cells)
block('Queued by the briefs, not staffed this wave — read at the gate so the queue is re-trued from measurements', [
  f'{INS["009"]} android',                                     # the Compose relative-parent abspos shrink-to-fit (ua-heading-margin §4)
  f'{S005} ios', f'{S005} android',                            # the non-heading out-of-flow UA margin (ua-heading-margin R9)
  f'{DS2A} ios', f'{BTAB} ios', f'{DOF["rtl-001"]} ios',      # the SwiftUI out-of-flow RTL twin (positioned-parent-slot R7)
] + [f'{APOS[k]} ios' for k in ('htb-rtl', 'vlr-rtl', 'vrl-rtl')]   # the RTL static-position residue, both natives (cbb-android R6)
  + ['css-anchor-position/anchor-position-multicol-007.html android'])

def matches(pattern_test):
    return [t for t in TESTS if pattern_test in f'{t.split("/")[0]}/css/{t}']
for l in out:
    if not l or l.startswith('#'):
        continue
    parts = l.split(' ')
    test = parts[0]
    hit = matches(test)
    assert len(hit) == 1, f'watch line {l!r} matches {len(hit)} tests: {hit[:4]}'
    if len(parts) == 2:
        assert l in CELLS, f'watch line {l!r} is not a scored cell'
    else:
        assert any(scored(test, p) for p in PLATS), f'watch line {l!r} has no scored cell'
open(os.path.join(OUT, 'watchlist.txt'), 'w').write('\n'.join(out) + '\n')

# ── report ────────────────────────────────────────────────────────────────────────────────────────────────────────────
gating = [p for l in lanes.values() for p in l['predictions'] if p['gating']]
assert all(p['floor'] == PASS for p in gating), 'every gating prediction floors at the pass threshold'
assert all(p['gating'] for l in lanes.values() for p in l['predictions']
           if p['confidence'].startswith(('HIGH', 'MED-HIGH')) and p['kind'] != 'non-corpus' and 'demotedFrom' not in p and p['to'].startswith('P')), \
    'a HIGH/MED-HIGH prediction to P is not gating'
_cmds = [pr['cmd'] for l in lanes.values() for pr in l['geometryProbes']]
assert all(c.startswith(PY + ' ') for c in _cmds), 'C14: a probe cmd off the pinned interpreter'
print(f"generatedWith: argv {_ARGV_RECORD} · {len(INPUTS)} input files (sha256 each in expectations.json generatedWith.inputs); decisions: "
      f"reverted {len(REVERTED)} · restate {len(RESTATE)} · contingency {len(CONTINGENCY)} · goSmall {L4_GO_SMALL}")
print(f"watchlist.txt: {sum(1 for l in out if l and not l.startswith('#'))} watch lines")
print('expectations.json: union capture carriers', {p: len(v) for p, v in union.items()}, '· wire carriers', len(wire_owner),
      '· may-move android', len(expectations['unionMayMoveCaptures']['android']),
      f'(raw {len(l6_may_raw)} + D21 medium {len(l6_may_medium)} + (2b) {len(l6_opacity)} + fix-r1 growth {len(l6_contents_pseudo)} − shared {len(_shared)} − L6 carriers' + (' — WITHDRAWN: L6 U2 reverted)' if L6.get('mayMoveWithdrawn') else ')'))
print(f'scored cells {len(CELLS)} = carrier cells {len(carrier_cells)} + named must-not-move {len(named)} + complement {len(complement)} + ring-fenced {len(ring_cells)}')
print(f'stage-1 probe ({len(STAGE1)}):', ','.join(STAGE1))
print(f'probe sections ({len(PROBE)}):', ','.join(PROBE), '· not probed:', ','.join(NOT_PROBED))
print(f'geometry keys first read at the closing gate (section not probed): {len(_unprobed_keys)}', [f'{s} {k}' for s, k in _unprobed_keys])
print(f'gating predictions (floor {PASS}): {len(gating)}')
units = sum(len(l['revertUnits']) for l in lanes.values())
print(f'revert units (commits): {units} (+ contingency {sum(len(l.get("contingencyUnits", {})) for l in lanes.values())})')
flips = {p: {'HIGH/MED-HIGH': 0, 'MED/MED-LOW': 0, 'LOW': 0} for p in PLATS}
for l in lanes.values():
    for p in l['predictions']:
        if p['kind'] == 'non-corpus' or not p['from'].startswith('f') or not p['to'].startswith('P'):
            continue
        plat = p['cell'].rsplit(' ', 1)[1]
        c = p['confidence']
        tier = 'HIGH/MED-HIGH' if c.startswith(('HIGH', 'MED-HIGH')) else ('MED/MED-LOW' if c.startswith('MED') else 'LOW')
        flips[plat][tier] += 1
print('predicted f->P flips by tier (DEGENERATE ones included — the scorer counts them):', json.dumps(flips))
for name, lane in lanes.items():
    print(f"  {name}: carriers {[len(lane['captureCarriers'][p]) for p in PLATS]} wire {len(lane['wireCarriers'])} "
          f"predictions {len(lane['predictions'])} mustNotMove {len(lane['mustNotMove'])} units {list(lane['revertUnits'])} probeSections {len(lane['probeSections'])}")
und = [p['cell'] for l in lanes.values() for p in l['predictions'] if p.get('direction') == 'undirected']
print(f'undirected prediction rows (exempt from revert rule 2): {len(und)}: ' + '; '.join(und))
print('down-preregistered rows:', [p['cell'] for l in lanes.values() for p in l['predictions'] if p.get('direction') == 'down-preregistered'])
print('f→f rows with floorNA:', sum(1 for l in lanes.values() for p in l['predictions'] if p.get('floorNA')))
_gk = sum(len(pr.get('gating', {})) for l in lanes.values() for pr in l['geometryProbes'])
_rk = sum(len(pr.get('report', [])) for l in lanes.values() for pr in l['geometryProbes'])
_ak = sum(len(pr['expect']) for l in lanes.values() for pr in l['geometryProbes'])
_wk = sum(len(pr.get('withdrawn', {})) for l in lanes.values() for pr in l['geometryProbes'])
_dk = sum(len(pr.get('dropped', {})) for l in lanes.values() for pr in l['geometryProbes'])
print(f'geometry keys: {_ak} = gating {_gk} + report {_rk} + control {_ak - _gk - _rk - _wk} + withdrawn {_wk}; dropped (C4 / C6) {_dk}; probes {len(_cmds)}')
print(f"picture-correct tally: {len(expectations['pictureCorrectTally']['full'])} in full + {len(expectations['pictureCorrectTally']['part'])} in part"
      f" + {len(expectations['pictureCorrectTally']['paintOrderOnly'])} paint order only (never counted)"
      f" (+{len(_tally_wd)} withdrawn by probe decisions), each with its geometry key(s)")
print('A/B arms: ' + '; '.join(f"{a['arm']} ({a['lane'].split('-')[0]}, against {a['against']}, drops {' → '.join(a['drops'])})" for a in expectations['abArms'])
      + (f" · withdrawn: {', '.join(a['arm'] for a in expectations['abArmsWithdrawn'])}" if expectations.get('abArmsWithdrawn') else ''))
if _pd:
    for d in _pd['reverted']:
        print(f"probe decision {d['lane']} {d['unit']}: withdrawn predictions {len(d['withdrawnPredictions'])}, demoted {d['demotedPredictions']}, "
              f"geometry keys {d['withdrawnGeometryKeys']}, captures {sum(len(v) for v in d['carriersWithdrawn']['captures'].values())}")
print(f'L6 shared stems (C2, in the raw may-move set but another lane\'s carrier): {len(_shared)}: ' + ', '.join(f'{s} ({o})' for s, o in L6['sharedStems'].items()))
print(f'must-not-move exclusions (another lane\'s carrier on that platform, or the ring-fence): {len(excluded)}')
for lane_name, line, o in excluded:
    print(f'    {lane_name}: {line}  -> {"carrier of " + o if o != "RING-FENCED" else o}')
