#!/usr/bin/env python3
# tools/titan/results/wave54-plan/plan-build.py — writes watchlist.txt and expectations.json for PLAN.md (wave 54).
#
# Why a script: the closing-gate expectations are the lanes' carrier sets, predictions and must-not-move cells, and
# most of those lists already live in the family censuses beside this file. Typing them by hand is how wave 52 got 11
# dead watch lines (plan-skeptic-1 C7). This is the wave-53 generator (tools/titan/results/wave53-plan/plan-build.py)
# adapted to the seven wave-54 lanes. Three things are new, each a check the wave-53 skeptics had to do by hand:
#   1. Every "from" value of a prediction is VERIFIED against cells-wave53-final.json (the scorer's own loader,
#      written by snapshot-cells.mjs); a brief value that disagrees with the run of record stops the build.
#   2. Every watch / must-not-move line must name a SCORED cell of wave53-final and match exactly ONE test under
#      score-gate.mjs watchCells' substring rule; every carrier stem must have a capture PNG (and, for a wire
#      carrier, a per-test IR document) in tools/titan/runs/wave53-final.
#   3. Family-wide must-not-move lists (every css-gaps cell, every web hyphens cell, the 60 Compose-table documents)
#      are ENUMERATED from the snapshot / census, and every exclusion of another lane's carrier is printed.
# Pure JSON/text plus os.path.exists; no image is decoded, nothing is run.
#
# Usage: node tools/titan/results/wave54-plan/snapshot-cells.mjs wave53-final   (once; writes cells-wave53-final.json)
#        python3 tools/titan/results/wave54-plan/plan-build.py
# Check: RUN=wave53-final WATCH=tools/titan/results/wave54-plan/watchlist.txt \
#          node tools/titan/results/wave52-plan/watchlist-check.mjs        → must print "unmatched 0"
import json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
# Dry-run options (fix r1, plan-skeptic M3): `--out DIR` writes expectations.json / watchlist.txt there instead of beside
# this file, `--reverted FILE` / `--restate FILE` read REVERTED / RESTATE from a JSON file instead of the tables below.
# The orchestrator's real procedure is unchanged (edit REVERTED / RESTATE here, re-run with no options); the options let
# the probe-decision hook be self-tested without copying this script.
_argv = sys.argv[1:]
def _opt(name):
    if name in _argv:
        i = _argv.index(name); v = _argv[i + 1]; del _argv[i:i + 2]; return v
    return None
OUT = _opt('--out') or HERE
_REVERTED_FILE, _RESTATE_FILE = _opt('--reverted'), _opt('--restate')
assert not _argv, f'unknown arguments: {_argv}'
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
RUN = 'wave53-final'                     # the gate of record every "from" is read against (re-read vs wave54-open: §10)
load = lambda name: json.load(open(os.path.join(HERE, name)))
PLATS = ('web', 'ios', 'android')
CAPDIR = {'web': 'screenshots', 'ios': 'ios-screenshots', 'android': 'android-screenshots'}

SNAP = load(f'cells-{RUN}.json')
CELLS = SNAP['cells']                    # "<sec>/<path>.html <platform>" -> "P 0.9818"
TESTS = sorted({k.rsplit(' ', 1)[0] for k in CELLS})

def stem(test):
    """'css-text/bidi/bidi-lines-001.html' -> 'wpt__css-text__bidi__bidi-lines-001' (the capture / per-test IR stem)."""
    assert test.endswith('.html'), test
    return 'wpt__' + test[:-len('.html')].replace('/', '__')

def test_of(st):
    """The inverse of stem(): 'wpt__<sec>__<a>__<b>' -> '<sec>/<a>/<b>.html'."""
    return '/'.join(st.split('__')[1:]) + '.html'

def section_of(st):
    return st.split('__')[1]

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

# ── predictions ───────────────────────────────────────────────────────────────────────────────────────────────────────
# Plan rule (wave-53 plan-skeptic must-fix 2, kept): every HIGH / MED-HIGH prediction carries a numeric `floor` and
# `gating: true`; MED and below carry `floor: None, gating: False` (read and labelled, never a floor trigger) — but
# revert rules 1 (P -> f), 2 (delta <= -0.002) and 5 (leak) apply to every row, whatever its tier. `units` names the
# revert unit(s) (commits) that carry the cell, so revert rule 6 always names a commit.
PASS = 0.95
GEO_OK = 'GEOMETRY OK'
def pred(line, frm, to, kind, confidence, floor=None, gating=False, units=(), geometry=None, **extra):
    test, plat = line.rsplit(' ', 1)
    snap = CELLS.get(line)
    assert snap is not None, f'prediction on an unscored cell: {line}'
    # The brief's "from" must equal the run of record (first two tokens: verdict + score), or the plan is wrong.
    assert ' '.join(frm.split()[:2]) == snap, f'{line}: brief says "{frm}", {RUN} says "{snap}"'
    row = {'cell': line, 'from': frm, 'to': to, 'kind': kind, 'confidence': confidence, 'floor': floor,
           'gating': gating, 'geometry': geometry, 'units': list(units),
           # fix r1 (plan-skeptic S1): the sign the row pre-registers. 'up-or-stay' (the default: a flip, a rise, or
           # "stays / byte-identical") is bound by revert rule 2 (delta <= -0.002 reverts its commit). 'undirected' (a
           # move whose sign the brief could not pre-register) is EXEMPT from rule 2 — a coin-flip row must not revert a
           # commit that carries HIGH flips — and is read and labelled instead; rules 1 and 5 still bind it.
           'direction': extra.pop('direction', 'up-or-stay'),
           # fix r2 (plan-skeptic R2-S1): the units that must be ON THE TREE for `to` (and a gating row's floor) to be
           # reachable. Default: every unit of the row. A unit missing from `requires` is ADDITIVE (its revert leaves a
           # pre-registered remainder: L1's M′ on counter-suffix android, L3's U3b everywhere). probe_decisions() demotes
           # a row one of whose required units is reverted (gating -> False, direction -> undirected) unless RESTATE
           # re-registers it, and an assertion below refuses a gating row that still needs a reverted unit.
           'requires': list(extra.pop('requires', units))}
    row.update(extra)
    assert set(row['requires']) <= set(row['units']), f'{line}: requires names a unit the row does not carry'
    assert row['direction'] in ('up-or-stay', 'undirected'), row
    assert row['direction'] == 'up-or-stay' or not gating, f'{line}: a gating row must have a direction'
    return row

def note(text, units=()):
    """A non-corpus prediction (a tripwire, a suite, a fixture-net line) — adjudicate.mjs prints it as 'read elsewhere'."""
    return {'cell': text, 'from': '-', 'to': '-', 'kind': 'non-corpus', 'confidence': 'see PLAN', 'floor': None,
            'gating': False, 'geometry': None, 'units': list(units), 'direction': None}

def caps(**per_plat):
    """{'web': [...stems], 'ios': [...], 'android': [...]} with every platform present."""
    return {p: sorted(set(per_plat.get(p, []))) for p in PLATS}

# ── the censuses ─────────────────────────────────────────────────────────────────────────────────────────────────────
rtl = load('rtl-marker-bake.census.json')
tbc = load('compose-table-body-cell.census.json')
hyc = load('hyphenate-character.census.json')
lay = load('queue-scout-layout.census.json')
uah = load('ua-heading-face.census.json')
oof_hy = load('web-out-of-flow-hyphen-box.census.json')
lcr = load('label-chrome-all-reset.census.json')

T = lambda *parts: '/'.join(parts) + '.html'     # readable test-path builder
CS = T('css-counter-styles', 'counter-suffix')
BL1, BL2 = T('css-text', 'bidi', 'bidi-lines-001'), T('css-text', 'bidi', 'bidi-lines-002')
ACSR = 'wpt__css-anchor-position__anchor-center-safe-rtl'          # unscored (scoreEligible:false) — capture + wire only

# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# L1 · rtl-marker-bake — units P (padding kept on a bake root is zeroed) then M′ (marker runs owned by the bake ROOT).
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
bake_tests = [test_of(d['stem']) for d in rtl['bakeDocs']]
assert len(bake_tests) == 17, len(bake_tests)
cssom = [t for t in TESTS if t.startswith('css-counter-styles/cssom/')]
assert len(cssom) == 15, cssom              # the M′ id shadow (+6 over every later document of tests.list)
L1 = {
  'dir': 'tools/titan/results/wave54-rtl-marker-bake',
  'briefs': ['rtl-marker-bake.md'],
  'captureCarriers': caps(web=[stem(CS)], ios=[stem(CS)], android=[stem(CS), stem(BL1), stem(BL2), ACSR]),
  'wireCarriers': sorted([stem(CS), stem(BL1), stem(BL2), ACSR]),
  'carrierRule': {
    'P': "a bidi-bake root (_lossyReasons 'baked-bidi-visual-order') whose browser-resolved padding is NON-ZERO on some side: 6 roots in 4 documents (census paddedRoots); the 8 zero-padding roots (dir-style-02a x6, dir-selector-change-003/-004) keep their padding-* keys in place and in order (pin V3b)",
    'Mprime': "a component with meta.markerText and Position ABSOLUTE|FIXED: exactly 4, all in counter-suffix (census markerTextAndOutOfFlow); after M′ every marker run is a child of the RELATIVE bake root and every <li> box keeps one run (pin V7; stack-shape census 10 host-inactive parents, 0 new)",
    'zeroPaddingBakeRootsMustStayByteIdentical': ['wpt__selectors__dir-style-02a', 'wpt__selectors__dir-selector-change-003', 'wpt__selectors__dir-selector-change-004'],
  },
  'probeSections': ['css-counter-styles', 'css-text', 'css-anchor-position', 'css-backgrounds', 'css-writing-modes', 'selectors'],
  'stage1Sections': ['css-counter-styles', 'css-text'],
  'predictions': [
    pred(f'{BL1} android', 'f 0.8934', 'P ≈0.9629 (device-measured: these hunk bytes at wave53-probe)',
         'flip (line starts on the ref x; the native Arabic face is wider, right edge x354 vs x348 — a font residual)', 'HIGH', 0.955, True, ['P'], GEO_OK),
    pred(f'{BL2} android', 'P 0.9534', 'P ≈0.9818 (device-measured at wave53-probe)',
         'mover up; stays DEGENERATE (the orange "!" sits on the LEFT on all three platforms: a bake-measurement defect, not P)', 'HIGH', 0.975, True, ['P'], GEO_OK),
    pred(f'{CS} android', 'P 0.9547 DEGENERATE', 'P ≈0.9815 with P alone (replay); ≈0.989 with M′ on P (replay 0.9894 / 0.9900)',
         'P: RTL text moves to the ref x, still no marker (DEGENERATE). M′: RTL rows picture-correct ONLY if [M] android prints GEOMETRY OK; the cell stays DEGENERATE on rows 5-6 (CJK marker 4 px left)',
         'MED-HIGH (P floor) / MED (M′ magnitude, geometry-gated)', 0.970, True, ['P', 'Mprime'], GEO_OK, requires=['P']),
    pred(f'{CS} web', 'P 0.9818', 'P 1 (wave53-probe measured 1 with the same marker geometry)',
         'faithful (the RTL markers move from the left x46-58 to the inline-start side)', 'HIGH', 0.999, True, ['Mprime'], GEO_OK),
    pred(f'{CS} ios', 'P 0.9802 DEGENERATE', 'P ≈0.987 (wave53-probe measured 0.9873)',
         'RTL rows picture-correct; cell stays DEGENERATE on rows 3-6 (iOS: Hebrew period order, CJK marker 4 px left)', 'MED-HIGH', 0.985, True, ['Mprime'], GEO_OK),
  ],
  # Pre-registered and never to be shipped (rtl brief §8): M′ without P loses counter-suffix android (replay f 0.9466).
  'neverShip': 'M′ without P (counter-suffix android → f ≈0.947, the wave-53 replay); M′ is reverted before P, never cherry-picked alone',
  'mustNotMove': sorted(set(
      [c for t in bake_tests if t not in (CS, BL1, BL2) for c in cells_of([t])]
      + cells_of([BL1, BL2], ('web', 'ios'))
      + cells_of(cssom))),
  'geometryProbes': [
    {'cmd': 'python3 tools/titan/results/wave54-plan/rtl-marker-bake.geometry.py <run> <base>',
     'expect': {'[P] bidi/bidi-lines-001 android': GEO_OK, '[P] bidi/bidi-lines-002 android': GEO_OK,
                '[P] bidi/bidi-lines-001 web': GEO_OK, '[P] bidi/bidi-lines-001 ios': GEO_OK,
                '[P] bidi/bidi-lines-002 web': GEO_OK, '[P] bidi/bidi-lines-002 ios': GEO_OK,
                '[M] counter-suffix web': GEO_OK, '[M] counter-suffix ios': GEO_OK, '[M] counter-suffix android': GEO_OK},
     'contains': {f'[M] counter-suffix {p}': 'rows 0-207 identical to <base>' for p in PLATS},
     'gating': {'[P] bidi/bidi-lines-001 android': 'P', '[P] bidi/bidi-lines-002 android': 'P',
                '[M] counter-suffix web': 'Mprime', '[M] counter-suffix ios': 'Mprime', '[M] counter-suffix android': 'Mprime'},
     'report': [],
     'onWave53Final': '[P] android rows WRONG (x33 / x35); [M] web/ios WRONG (no marker ink on text row y214-225), android WRONG (rtl text rows 0/4 in x95-128); every ref row OK; exit 0',
     'onWave53Probe': '[P] all OK; [M] web/ios OK, android WRONG (rtl row 1 no marker ink on text row y214-225 (marker ink y244-245 below the line)) — the reverted unit\'s defect, named'},
    {'cmd': 'python3 tools/titan/results/wave53-plan/lists-bakes.geometry.py <run> <base>',
     'expect': {f'counter-suffix {p}': GEO_OK for p in PLATS} | {f'counter-reset-reversed-nested {p}': GEO_OK for p in PLATS},
     'contains': {f'counter-suffix {p}': 'rows 0-207 identical to <base>' for p in PLATS},
     'gating': {f'counter-suffix {p}': 'Mprime' for p in ('web', 'android')},
     'report': ['counter-suffix ios'],
     'onWave53Final': 'counter-suffix x3 WRONG (web left x47; ios right x126; android left x152); counter-reset-reversed-nested x3 OK (wave-53 U1, a control now)'},
  ],
  'revertUnits': {
    'P': {'commit': 'tools/titan/bidi-bake.mjs hunk P (paddingIsSpent + rootProperties(rect, position, el) + applyBidiBakePlan padding-* delete; the b0edb788^ fix-pass state) + pins V3 / V3b / content-box + overflow guards',
          'captures': caps(android=[stem(CS), stem(BL1), stem(BL2), ACSR]),
          'wire': sorted([stem(CS), stem(BL1), stem(BL2), ACSR]), 'wireShadow': None,
          'revertOrder': ['Mprime', 'P'],
          'note': 'reverting P reverts M′ first (M′ never ships without P)'},
    'Mprime': {'commit': 'tools/titan/bidi-marker-bake.mjs (restored from b0edb788^, 200 lines) + the two bidi-bake.mjs call-site lines (l.925 origin = the enclosing ROOT, l.974 ownerPath = the enclosing ROOT) + import + pins V1/V2/V4/V5/VF/VF1-3 (root ownership) + V6 + V7',
               'captures': caps(web=[stem(CS)], ios=[stem(CS)], android=[stem(CS)]),
               'wire': [stem(CS)], 'wireShadow': {'css-counter-styles': {'after': stem(CS), 'shift': 6, 'documents': 15}},
               'revertOrder': ['Mprime']},
  },
  'stage1Decision': 'at wave54-pre: M′ is reverted on ANY [M] line that is not GEOMETRY OK (any platform), on a missing "rows 0-207 identical to wave54-open", on counter-suffix web < 0.999 or ios < 0.985, or on any leak in css-counter-styles beyond the 15 renumbered cssom documents; P stays unless its own [P] android rows or its floors fail (then M′ and P go, M′ first)',
}

# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# L2 · table-body-cell — the re-do of wave-53 L3 B-android: one commit, one seam hunk, one capture.
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
S006 = T('CSS2', 'css21-errata', 's-11-1-1b-006')
compose_tables = [r['test'] + '.html' for r in tbc['docs'] if r['composeTables']]
assert len(compose_tables) == 60 and S006 in compose_tables, len(compose_tables)
L2 = {
  'dir': 'tools/titan/results/wave54-table-body-cell',
  'briefs': ['compose-table-body-cell.md'],
  'captureCarriers': caps(android=[stem(S006)]),
  'wireCarriers': [],
  'carrierRule': "a synthetic TableBodyForest box (role 'anonymous-table'): only the forest of a body-root whose last Display is TABLE/INLINE_TABLE creates one — 1 document (census forestTriggerDocs); every other Compose table gets chrome(...) = (today's stroke, hug=false), byte-identical by construction",
  'probeSections': ['CSS2', 'css-tables', 'css-position', 'css-break', 'css-contain', 'css-display', 'css-values',
                    'css-writing-modes', 'selectors', 'css-text', 'css-backgrounds'],
  'stage1Sections': ['CSS2', 'css-tables'],
  'predictions': [
    pred(f'{S006} android', 'P 0.9944 DEGENERATE (square 5 px too high: rows 51-70 vs the ref 56-75)',
         'P ≈0.9983 (replay from both the final and the probe picture); square x24-43 rows 56-75, no stroke',
         'degenerate->faithful', 'MED-HIGH (geometry) / MED (score)', 0.996, True, ['TB-android'], GEO_OK),
  ],
  'mustNotMove': [],     # filled below, after every lane's carriers are known (59 Compose-table docs minus other lanes' carriers)
  'geometryProbes': [
    {'cmd': 'python3 tools/titan/results/wave54-plan/compose-table-body-cell.geometry.py <run>',
     'expect': {f'006 {p}': GEO_OK for p in PLATS}, 'gating': {'006 android': 'TB-android'}, 'report': [],
     'onWave53Final': '006 android WRONG (solid 300/400, stray ink 80 rows 52-55); web/ios/ref OK',
     'onWave53Probe': '006 android WRONG (solid 76/400, stray ink 400 at x44-63) — the reverted B-android picture'},
    {'cmd': 'python3 tools/titan/results/wave53-plan/display-table-body.geometry.py <run> 006',
     'expect': {f'006 {p}': 'square rows 56-75 (20) x 24-43 | red px 0' for p in PLATS}, 'gating': {'006 android': 'TB-android'}, 'report': [],
     'onWave53Final': '006 android "square rows 51-70 (20) x 24-43 | red px 0"; web/ios/ref rows 56-75'},
  ],
  'revertUnits': {
    'TB-android': {'commit': 'TableBodyForest.kt (re-land 276757ea + ANONYMOUS_ROLE + isAnonymous) + TableCellHug.kt (new) + TableApplier.kt (cellsHugContent param, provider, TableCell hugColumn) + ScreenshotCaptureScreen.kt composedCanvasRoots call site + ComponentRenderer.kt seam (chrome = TableCellHug.chrome(...)) + TableBodyForestTest / TableCellHugTest / ComposedCanvasTableBodyTest',
                   'captures': caps(android=[stem(S006)]), 'wire': [], 'revertOrder': ['TB-android']},
  },
  'stage1Decision': 'at wave54-pre: TB-android is reverted unless BOTH 006 android lines print their expected string and 006 android ≥ 0.996; css-tables android must be 48/48 identical (control)',
}

# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# L3 · hyphenate-character — U1 converter, U2 per native, U3 extractor br re-arm, U3b (probe-decided line box).
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
HC = {n: T('css-text', 'hyphens', f'hyphenate-character-00{n}') for n in (1, 2, 3, 4, 5)}
HLC = T('css-text', 'hyphens', 'hyphenate-limit-chars-001')
br_tests = [r['test'] for r in hyc['brHeight']]                 # '<sec>/<path>.html' (census rows carry the test path)
assert len(br_tests) == 18, br_tests
INVARIANT_U3 = [T('css-cascade', 'revert-layer-006'), T('css-cascade', 'revert-val-001'), T('css-cascade', 'revert-val-002')]
assert all(t in br_tests for t in INVARIANT_U3)
u3_capture_tests = [t for t in br_tests if t not in INVARIANT_U3]        # 15 documents whose pixels may move
assert len(u3_capture_tests) == 15
BE = {n: T('css-overflow', 'line-clamp', f'block-ellipsis-00{n}') for n in (2, 4, 5, 6)}
BF = {n: T('filter-effects', f'backdrop-filter-{n}') for n in ('clip-rect', 'edge-clipping', 'paint-order', 'plus-filter')}
U3F = [T('css-masking', 'clip-path', 'clip-path-filter-order'), T('css-multicol', 'balance-grid-container'), T('css-multicol', 'column-height-009')]
assert set(u3_capture_tests) == {HC[1], HC[2], HC[3], HC[4], *BE.values(), *BF.values(), *U3F}, sorted(u3_capture_tests)
u2_default_population = [T('css-text', 'hyphens', t) for t in (
    'hyphens-manual-010', 'hyphens-manual-011', 'hyphens-manual-012', 'hyphens-manual-013', 'hyphens-manual-inline-010',
    'hyphens-manual-inline-011', 'hyphens-manual-inline-012', 'hyphens-none-011', 'hyphens-none-012', 'hyphens-none-013',
    'hyphens-span-001', 'hyphens-span-002', 'hyphens-out-of-flow-001', 'hyphens-out-of-flow-002', 'hyphens-auto-control',
    'hyphens-vertical-001', 'hyphens-none-shy-on-2nd-line-001')] + [T('css-overflow', 'line-clamp', 'block-ellipsis-014'), T('css-overflow', 'line-clamp', 'block-ellipsis-028')]
# fix r1 (plan-skeptic S6): hyphens-none-shy-on-2nd-line-001 carries U+00AD on the wire (skeptic-r1/shy-census.out.txt, 17
# documents) and was missing; its natives (P 0.998 / 0.9988) take U2's defaulted parameter like every other member.
assert len(u2_default_population) == 19, len(u2_default_population)
GLYPH = 'GEOMETRY OK'
u3_units = ['U3', 'U3b']
# fix r2 (R2-S4): the U3-radius rows the plan had written "up or unchanged" / "up" without a replay.
CPF, BGC, CH9 = U3F
U3F_B = {  # (to, kind, confidence) from replay B on the cell's own pixels (hyphenate-character.replay-b-u3f-score.out.txt)
  (CPF, 'web'): ('P ≈1.0 (replay B on its own pixels: 1 — the capture minus the stray 20 px IS the ref)', 'flip (U3 removes the stray blank line; replay B)', 'MED'),
  (CPF, 'ios'): ('P ≈0.998 (replay B 0.9978)', 'flip (U3 removes the stray blank line; replay B)', 'MED'),
  (CPF, 'android'): ('up, ≈0.955 at best (replay B 0.9549: the leading-space residual stays)', 'possible flip (replay B)', 'LOW-MED'),
  (BGC, 'web'): ('P ≈1.0 (replay B 1: the two stray blank lines removed give the ref)', 'flip (U3 removes the stray blank lines; replay B)', 'MED'),
}
U3F_WHY = {(CH9, p): 'not replayable: the 15 stray brs feed a multicol balance (web: 3 balanced columns; natives: one column); removing them re-balances the columns, which no row cut models (skeptic-r2/look-column-height-009-ref-web-ios-android.png)' for p in PLATS}
U3F_WHY |= {(BGC, p): 'not replayable: the native paints the address on ONE line (no blank band to cut); the 20 px br height changes that line box, which a row cut does not model' for p in ('ios', 'android')}
BF_WHY = ('not replayable: the stray blank line sits under absolutely positioned boxes (green box, backdrop-filtered pill / white box) that do not move with the flow, '
          'and text shows through their backdrop filter; a row cut would move the boxes too (fix-r2/look-backdrop-filter-*-ref-web-ios-android.png)')
L3 = {
  'dir': 'tools/titan/results/wave54-hyphenate-character',
  'briefs': ['hyphenate-character-compose.md'],
  'captureCarriers': caps(
      web=[stem(t) for t in u3_capture_tests],
      ios=[stem(t) for t in u3_capture_tests] + [stem(HLC)],
      android=[stem(t) for t in u3_capture_tests]),
  'wireCarriers': sorted({stem(t) for t in br_tests} | {stem(HC[5])}),
  'carrierRule': {
    'U1': 'a HyphenateCharacter declaration the parser rejected ("" -> Generic _unmapped) or left escaped (a backslash in the value): 4 documents (001, 002, 003, 005); "/-/" (004) and "-" (hyphenate-limit-chars-001) are byte-identical',
    'U2': 'a text run whose host declares a non-auto HyphenateCharacter and whose pre-break fires (Compose: space-less / spaced U+00AD runs of 001/003/004; iOS: the same plus the CF-dictionary fold of 002 and hyphenate-limit-chars-001); every other run takes the defaulted parameter, identity by construction',
    'U3': 'a <br> that ends a line opened by text interleaved BETWEEN children (node.runs {text} after the previous child): 54 brs in 18 documents (census brHeight); 3 of them (revert-layer-006, revert-val-001/-002) are pixel-invariant -> must-not-move captures, wire carriers',
    'U3b': 'a line-start <br> whose HOST (the br\'s parent element) declares line-height ON ITSELF (its own declared cascade; the font shorthand counts): 12 brs in 4 documents, all hyphenate-character-001..004 (b2 census). An INHERITED line-height (an ancestor\'s) does NOT trigger U3b: under the inherited reading 23 more 20-px brs in 4 documents would move, css-multicol/baseline-002 and baseline-007 among them (not carriers; skeptic-r1/br-census-all.out.txt) — pinned negative in extract-fixture-br-line-context.test.mjs',
  },
  'probeSections': ['css-text', 'css-overflow', 'filter-effects', 'css-cascade', 'css-masking', 'css-multicol'],
  'predictions': [
    pred(f'{HC[1]} ios', 'f 0.9287', 'P ≈0.979 (GBd 0.9852 − 0.006)', 'flip; faithful only with U1+U2-ios+U3 on the tree AND hyphenate-character.geometry.py OK on the row', 'MED-HIGH', 0.965, True, ['U1', 'U2-ios', 'U3', 'U3b'], GEO_OK),
    pred(f'{HC[3]} ios', 'f 0.9337', 'P ≈0.977 (GBd 0.9831 − 0.006)', 'flip; faithful only with U1+U2-ios+U3 AND geometry OK (U3 alone = 0.9566 with hyphens for bullets: DEGENERATE)', 'MED-HIGH', 0.965, True, ['U1', 'U2-ios', 'U3', 'U3b'], GEO_OK),
    pred(f'{HC[4]} ios', 'f 0.9134', 'P ≈0.979 (GBd 0.9855 − 0.006; needs the spent-hyphen hunk)', 'flip', 'MED', None, False, ['U2-ios', 'U3', 'U3b'], GEO_OK),
    pred(f'{HC[1]} web', 'f 0.935', 'P ≈0.960 (U3); ≈0.976-0.981 with U3b', 'flip', 'MED-LOW', None, False, ['U1', 'U3', 'U3b'], GEO_OK),
    pred(f'{HC[3]} web', 'f 0.8812', 'P ≈0.956 (U3); ≈0.976-0.981 with U3b', 'flip', 'MED-LOW', None, False, ['U1', 'U3', 'U3b'], GEO_OK),
    pred(f'{HC[4]} web', 'f 0.9249', '0.947-0.966 (U3; B on own pixels 0.966); ≈0.976-0.981 with U3b', 'coin flip; glyph already right on web, so not degenerate', 'LOW', None, False, ['U3', 'U3b'], GEO_OK),
    pred(f'{HC[1]} android', 'f 0.9301', '≈0.957 (U3); ≈0.976-0.981 with U3b', 'possible flip', 'LOW-MED', None, False, ['U1', 'U2-android', 'U3', 'U3b'], GEO_OK),
    pred(f'{HC[3]} android', 'f 0.9292', '≈0.953 (U3); ≈0.976-0.981 with U3b', 'possible flip', 'LOW', None, False, ['U1', 'U2-android', 'U3', 'U3b'], GEO_OK),
    pred(f'{HC[4]} android', 'f 0.905', '≈0.945 stays f (U3); ≈0.976-0.981 with U3b', 'mover', 'LOW', None, False, ['U2-android', 'U3', 'U3b'], GEO_OK),
    pred(f'{HC[2]} web', 'f 0.92', '≈0.96 (Chromium en breaks equal the shys; B alone 0.9426)', 'possible flip', 'LOW-MED', None, False, ['U1', 'U3']),
    pred(f'{HC[2]} ios', 'f 0.9261', 'mover', 'mover', 'LOW', None, False, ['U1', 'U2-ios', 'U3'], direction='undirected',
         directionWhy='U3 alone replays UP (B 0.9382) but U1 + U2-ios change the glyph of a CF-dictionary fold that no replay models'),
    pred(f'{HC[2]} android', 'f 0.9223', 'stays f (Minikin paints its own dictionary hyphen: a logged wall)', 'mover', 'LOW', None, False, ['U3'],
         directionWhy='U3 is its only unit (U2-android is identity on a dictionary run) and replay B, which models U3 alone, gives 0.923 >= 0.9223'),
    pred(f'{HLC} ios', 'f 0.8919', 'mover (U+2010 -> U+002D in the dictionary fold)', 'mover', 'LOW', None, False, ['U2-ios'], direction='undirected',
         directionWhy='the glyph swap is not replayed; no sign is pre-registered'),
    pred(f'{BE[2]} web', 'P 0.9868', 'P ≈1.0 (B replay 1)', 'picture-correctness (stray blank line removed)', 'MED-HIGH', 0.995, True, ['U3'], GEO_OK),
    # fix r1 (S3): LOOKED AT — the iOS capture paints Line 4 and no "…" (its clamp is not applied); U3 removes the stray
    # blank lines (replay B, 0.9928) but leaves 4 lines vs the ref's 3 + ellipsis: a mover up that stays DEGENERATE.
    pred(f'{BE[2]} ios', 'P 0.9813 DEGENERATE', 'P ≈0.993 (B replay 0.9928); stays DEGENERATE (Line 4 painted, no "…")',
         'mover up; stays DEGENERATE (block-ellipsis-br.geometry.py keeps "4 bands vs ref 3")', 'MED', None, False, ['U3'], 'GEOMETRY WRONG (4 bands vs ref 3)'),
    pred(f'{BE[2]} android', 'P 0.9884', 'identical or up (two lines painted; the clamp interplay is unknown)', 'watch', 'LOW', None, False, ['U3']),
  ] + [pred(f'{BE[n]} web', CELLS[f'{BE[n]} web'], 'P ≈0.998 (B replay 0.998)', 'picture-correctness (stray blank line removed)', 'MED-HIGH', 0.99, True, ['U3'], GEO_OK) for n in (4, 5, 6)]
    + [pred(f'{BE[n]} {p}', CELLS[f'{BE[n]} {p}'], 'byte-identical expected (no stray gap on the natives)', 'watch', 'MED', None, False, ['U3']) for n in (4, 5, 6) for p in ('ios', 'android')]
    # fix r2 (plan-skeptic R2-S4): no row cut models U3 on backdrop-filter-clip-rect / -edge-clipping / -paint-order — the
    # stray blank line sits UNDER absolutely positioned boxes that do not move with the flow, and text shows through
    # their backdrop filter (fix-r2/look-backdrop-filter-*-ref-web-ios-android.png; hyphenate-character.replay-b-u3f.py
    # prints them NOT REPLAYABLE). The skeptic listed the three Android rows; the six web / iOS "up" rows have the same
    # missing replay and the same reason, so all nine are undirected (rule 1 still binds the six that are P today).
    + [pred(f'{BF[n]} {p}', CELLS[f'{BF[n]} {p}'], 'moves (the stray blank line before "No dark/black…" is removed); expected up, not replayable',
            'mover (undirected)', 'MED', None, False, ['U3'], direction='undirected', directionWhy=BF_WHY)
       for n in ('clip-rect', 'edge-clipping', 'paint-order') for p in ('web', 'ios')]
    + [pred(f'{BF[n]} android', CELLS[f'{BF[n]} android'], 'moves; expected up (a flip was called possible), not replayable', 'mover (undirected)', 'LOW', None, False, ['U3'],
            direction='undirected', directionWhy=BF_WHY) for n in ('clip-rect', 'edge-clipping', 'paint-order')]
    + [pred(f'{BF["plus-filter"]} {p}', CELLS[f'{BF["plus-filter"]} {p}'], 'stays (the stray br is the <p>\'s trailing one; nothing in flow below it)', 'watch', 'MED', None, False, ['U3']) for p in PLATS]
    # fix r2 (R2-S4): replay B on their OWN pixels (hyphenate-character.replay-b-u3f.py, scored by -score.mjs with the
    # gate's metric; the capture column reproduces the manifest). Four cells are faithfully replayable (everything below
    # the stray band is in flow) and LOOKED at (fix-r2/look-clip-path-filter-order-ref-Bweb-Bios-Bandroid.png,
    # fix-r2/look-balance-grid-container-ref-Bweb.png): they carry the replay value and stay directed. The other five
    # are NOT replayable (U3F_WHY) and are undirected.
    + [pred(f'{t} {p}', CELLS[f'{t} {p}'], *U3F_B[(t, p)], None, False, ['U3']) if (t, p) in U3F_B
       else pred(f'{t} {p}', CELLS[f'{t} {p}'], 'moves (not replayable)', 'mover (undirected)', 'LOW', None, False, ['U3'],
                 direction='undirected', directionWhy=U3F_WHY[(t, p)])
       for t in U3F for p in PLATS],
  'mustNotMove': sorted(set(
      cells_of([HC[5]]) + cells_of(INVARIANT_U3) + cells_of([HLC], ('web', 'android'))
      + cells_of(u2_default_population, ('ios', 'android'))
      + [k for k in CELLS if k.startswith('css-text/hyphens/') and k.endswith(' web')
         and k.rsplit(' ', 1)[0] not in (HC[1], HC[2], HC[3], HC[4], T('css-text', 'hyphens', 'hyphens-out-of-flow-002'))])),
  'geometryProbes': [
    {'cmd': 'python3 tools/titan/results/wave54-plan/hyphenate-character.geometry.py <run>',
     'expect': {f'hyphenate-character-00{n} {p}': GLYPH for n in (1, 3, 4) for p in PLATS},
     'gating': {'hyphenate-character-001 ios': 'U2-ios|U3', 'hyphenate-character-003 ios': 'U2-ios|U3'},
     # fix r1 (M3): the units a gating key needs ON THE TREE to be satisfiable; a probe revert of any of them withdraws
     # the key (probe_decisions). U3b is not required: a reverted U3b leaves the U3-only picture, which the rule still
     # judges (revertUnits.U3b.probeDecided).
     'requires': {'hyphenate-character-001 ios': ['U1', 'U2-ios', 'U3'], 'hyphenate-character-003 ios': ['U1', 'U2-ios', 'U3']},
     # fix r1 (S5): non-gating keys expected WRONG today (MED / LOW targets); every other non-gating key is a control
     'report': [f'hyphenate-character-00{n} {p}' for n in (1, 3, 4) for p in PLATS if (n, p) not in ((1, 'ios'), (3, 'ios'))],
     'reasonToUnit': {'glyph:': 'U2-<platform> (U1 on web)', 'lines:': 'U3b, then U3 (a glyph-width line count goes to U2-<platform>)', 'offset:': 'U3b, then U3'},
     'onWave53Final': '9/9 capture rows WRONG (lines / glyph / offset), every ref row OK; the GB and GBd replays print 9/9 OK',
     'limitation': 'the ±4 px offset tolerance does not see the 1-3 px drift (brief §4.E): a score effect, not a wrong picture'},
    {'cmd': 'python3 tools/titan/results/wave53-plan/soft-hyphen.geometry.py <run>',
     'expect': {f'hyphens/{t} {p}': GEO_OK for t in ('hyphens-span-002', 'hyphens-span-001', 'hyphens-out-of-flow-001') for p in PLATS}
               | {f'hyphens/hyphens-out-of-flow-002 {p}': GEO_OK for p in ('ios', 'android')},
     'gating': {}, 'report': [],
     'note': 'control: the wave-53 soft-hyphen pictures must survive U2\'s defaulted parameter (hyphens-out-of-flow-002 web is L6\'s carrier and is read by its own probe)'},
    # fix r1 (plan-skeptic S3): the four line-clamp carriers the §1 tally counts get a key. Web rows gate U3; the native
    # rows are controls whose expected line is today's WRONG verdict verbatim (U3 predicted byte-identical on 004-006,
    # and -002 ios keeps 4 bands vs 3 after U3: replay B, fix-r1/post-S3.block-ellipsis-fakes.out.txt).
    {'cmd': 'python3 tools/titan/results/wave54-plan/block-ellipsis-br.geometry.py <run>',
     'expect': {f'block-ellipsis-00{n} web': GEO_OK for n in (2, 4, 5, 6)}
               | {'block-ellipsis-002 ios': 'GEOMETRY WRONG (4 bands vs ref 3)', 'block-ellipsis-002 android': 'GEOMETRY WRONG (2 bands vs ref 3)'}
               | {f'block-ellipsis-00{n} ios': 'GEOMETRY WRONG (3 bands vs ref 2)' for n in (4, 5, 6)}
               | {f'block-ellipsis-00{n} android': 'GEOMETRY WRONG (band bottom y72 vs ref y86 (top y40))' for n in (4, 5, 6)},
     'gating': {f'block-ellipsis-00{n} web': 'U3' for n in (2, 4, 5, 6)},
     'report': [],
     'onWave53Final': 'web 4/4 WRONG (002: band top y79 vs ref y59 — the stray blank line; 004-006: 3 bands vs ref 2); natives print their recorded lines; every ref row OK',
     'onReplayB': 'web 4/4 OK on the brief\'s U3 replay pictures; natives unchanged (fix-r1/post-S3.block-ellipsis-fakes.out.txt)'},
  ],
  'revertUnits': {
    'U1': {'commit': 'converter HyphenateCharacterPropertyParser.kt + new primitiveParsers/CssStringParser.kt (css-syntax-3 §4.3.5/§4.3.7) + HyphenateCharacterPropertyParserTest.kt',
           # On the integrated tree U1 also carries the natives' glyph: U2-<p> reads the DECODED string U1 puts on the wire
           # ("" for 001/002, "•" for 003), so reverting U1 with U2 in place changes those native captures too.
           'captures': caps(web=[stem(HC[1]), stem(HC[2]), stem(HC[3])], ios=[stem(HC[1]), stem(HC[2]), stem(HC[3])],
                            android=[stem(HC[1]), stem(HC[3])]),
           'wire': sorted([stem(HC[1]), stem(HC[2]), stem(HC[3]), stem(HC[5])]), 'revertOrder': ['U3b', 'U3', 'U1'],
           'note': 'reverting U1 leaves 001/002/003 on the unmapped / escaped wire on every platform; U3 stays only if its own rows hold, and the 001/003 flips are then DEGENERATE by construction'},
    'U2-android': {'commit': 'Compose typography/wrapping/HyphenateCharacter{Config,Extractor,Applier}.kt + SoftHyphenCuts.kt + PreBreakPipeline.kt defaulted hyphenChar + ComponentRenderer.kt seam-1 (:7072 one argument) + PreBreakPipelineTest / HyphenateCharacterExtractorTest',
                   'captures': caps(android=[stem(HC[1]), stem(HC[3]), stem(HC[4])]), 'wire': [], 'revertOrder': ['U2-android']},
    'U2-ios': {'commit': 'Swift typography/wrapping/HyphenateCharacter{Config,Extractor,Applier}.swift + SpentHyphen.swift + TypographyAggregate.swift (+1 field) + StyleBuilder.swift (+1 TextConfig field, +1 line) + GreedyLineBreaker.swift (defaulted spentHyphen) + ComponentRenderer.swift seam-2 (:5011, :5026) + GreedyLineBreakerTests / TypographyTests',
               'captures': caps(ios=[stem(HC[1]), stem(HC[2]), stem(HC[3]), stem(HC[4]), stem(HLC)]), 'wire': [], 'revertOrder': ['U2-ios']},
    'U3': {'commit': 'tools/titan/extract-fixture.mjs seam-3 (buildNode children loop re-arm, the child-scope twin of :11733) + tools/titan/extract-fixture-br-line-context.test.mjs',
           'captures': caps(**{p: [stem(t) for t in u3_capture_tests] for p in PLATS}), 'wire': sorted(stem(t) for t in br_tests),
           'revertOrder': ['U3b', 'U3']},
    'U3b': {'commit': 'tools/titan/extract-fixture.mjs seam-3b (a line-start br in a host declaring line-height gets that line box, normal = 1.2 x font-size) + its pins in extract-fixture-br-line-context.test.mjs',
            'captures': caps(**{p: [stem(HC[n]) for n in (1, 2, 3, 4)] for p in PLATS}), 'wire': sorted(stem(HC[n]) for n in (1, 2, 3, 4)),
            'revertOrder': ['U3b'],
            'probeDecided': 'U3b is IN at wave54-probe. It stays iff 001 ios and 003 ios meet their 0.965 floors with no `offset:`/`lines:` reason on THOSE TWO gating iOS rows, and rules 1/2/5 hold on its carriers; else it is reverted FIRST and css-text is re-probed (the brief\'s GBn risk: iOS loses its zero-drift compensation, 0.947-0.958). The report row 004 ios (MED) is read, never a trigger (revertRule tier), and a `lines:` re-wrap there from a glyph-width change is U2-ios\'s (reasonToUnit), not U3b\'s (fix r3, plan-skeptic R3-N2)'},
  },
  'sequencing': 'U3 never lands without U1 + U2-android + U2-ios in the tree before it (U3 alone manufactures a DEGENERATE -003 ios pass, replay B 0.9566); the landing order guarantees it, and a later revert of U1/U2-<p> keeps that platform\'s 001/003/004 flips labelled DEGENERATE by construction',
}

for _p in L3['predictions']:
    _p['requires'] = [u for u in _p['units'] if u != 'U3b']     # fix r2 (R2-S1): U3b is additive (its revert keeps the U3-only value)

# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# L4 · oof-layout — oof-containing-block (per native) + compose-wpt-content-box-cb + flex-zero-gap-rules (per native).
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
oof = lay['oofcb']
m2_tests = sorted({r['test'] for r in oof['m2']})
m1_tests = sorted({r['test'] for r in oof['m1']})
assert len(m2_tests) == 6 and len(m1_tests) == 3, (m2_tests, m1_tests)
CC = {n: T('css-contain', f'contain-content-0{n}') for n in ('03', '04', '11')}
PR3, PR4 = T('css-position', 'position-relative-003'), T('css-position', 'position-relative-004')
CHG = T('css-position', 'change-insets-inside-strict-containment-nested')
SFA = T('CSS2', 'abspos', 'static-fixed-inside-abspos')
BFCB = T('filter-effects', 'backdrop-filter-containing-block')
PFSNF = 'wpt__css-position__position-fixed-scroll-nested-fixed'          # unscored M1 carrier
oof_android = [stem(t) for t in m2_tests + m1_tests if t != 'css-position/position-fixed-scroll-nested-fixed.html'] + [PFSNF]
# PLAN §9 D3: contain-content-004 ios is must-not-move — the Swift change touches FIXED hoisting only (FixedHoist :309 /
# :347 / :115), and 004's only M2 box is ABSOLUTE, the same reason the brief gives for contain-content-003/-011 ios.
oof_ios = [stem(t) for t in (CHG, PR4, BFCB, SFA, PR3)] + [PFSNF]
cbb_tests = sorted({r['test'] for r in lay['cbborder']} - {T('css-images', 'cross-fade-target-alpha')})
assert len(cbb_tests) == 10, cbb_tests
G006, G033 = T('css-gaps', 'flex', 'flex-gap-decorations-006'), T('css-gaps', 'flex', 'flex-gap-decorations-033')
APOS = {k: T('css-flexbox', 'abspos', f'abspos-autopos-{k}') for k in ('htb-ltr', 'vlr-ltr', 'vrl-ltr', 'htb-rtl', 'vlr-rtl', 'vrl-rtl')}
NBR = {k: T('filter-effects', f'backdrop-filter-nested-border-radius-clip{k}') for k in ('', '-2', '-3', '-4')}
fixed_controls = [r['test'] for r in oof['fixedControls'] if 'css-view-transitions' not in r['test']]
vt_controls = [r['test'] for r in oof['fixedControls'] if 'css-view-transitions' in r['test']]
estab_controls = [r['test'] for r in oof['establisherControls']]
L4 = {
  'dir': 'tools/titan/results/wave54-oof-layout',
  'briefs': ['oof-containing-block.md', 'compose-wpt-content-box-cb.md', 'flex-zero-gap-rules.md', 'queue-scout-layout.md'],
  'captureCarriers': caps(ios=oof_ios + [stem(G033)],
                          android=oof_android + [stem(t) for t in cbb_tests] + [stem(G006), stem(G033)]),
  'wireCarriers': [],
  'carrierRule': {
    'OOF': 'an out-of-flow box whose spec containing block is a non-positioned contain:layout|paint|strict|content / filter / backdrop-filter (or will-change of one) ancestor (M2: 10 boxes in 6 tests), or an all-auto-inset FIXED box (M1: 4 boxes in 3 tests); Compose host activation flips true -> false on exactly the 7 hostFlips documents',
    'CBB': 'a content-box (no BoxSizing BORDER_BOX) container with a px/% size, a non-zero padding/border band and an out-of-flow child carrying a % size or inset: 10 containers in 10 tests (+ the in-flow control cross-fade-target-alpha); side reader flex-gap-decorations-006 (VerticalTextFlowLayout budget, watched)',
    'GAP': 'a flex container with a rule family on an axis whose gap is 0 or absent AND touching neighbours/lines: 033 only of the 17 gapzero containers',
  },
  'probeSections': ['css-position', 'css-contain', 'CSS2', 'filter-effects', 'css-transforms', 'css-flexbox', 'css-images',
                    'css-gaps', 'css-anchor-position', 'css-display', 'css-masking', 'css-pseudo'],
  'predictions': [
    pred(f'{CC["03"]} android', 'f 0.9405', 'P ≈0.997 (sim 0.9967)', 'flip (red-ink failure becomes the ref picture)', 'HIGH', 0.99, True, ['OOF-android'], GEO_OK),
    pred(f'{CC["11"]} android', 'f 0.9284', 'P ≈0.985 (sim ≤0.9854, an upper bound)', 'flip; the "25" counter (ref "17") stays wrong on ALL THREE platforms — not this lane', 'MED-HIGH', 0.97, True, ['OOF-android'], GEO_OK),
    pred(f'{BFCB} ios', 'f 0.7955', 'P (sim bracket 0.9414 … 1.0)', 'flip', 'MED', None, False, ['OOF-ios'], GEO_OK),
    pred(f'{BFCB} android', 'f 0.7506', 'P (sim bracket 0.9414 … 1.0)', 'flip', 'MED', None, False, ['OOF-android'], GEO_OK),
    pred(f'{PR4} ios', 'P 0.9594 DEGENERATE', 'P ≈0.9974', 'degenerate->faithful', 'MED-HIGH', 0.99, True, ['OOF-ios'], GEO_OK),
    pred(f'{PR4} android', 'P 0.9589 DEGENERATE', 'P ≈0.9967', 'degenerate->faithful', 'MED-HIGH', 0.99, True, ['OOF-android'], GEO_OK),
    pred(f'{CHG} ios', 'P 0.9594 DEGENERATE', 'P ≈0.9974', 'degenerate->faithful', 'MED-HIGH', 0.99, True, ['OOF-ios'], GEO_OK),
    pred(f'{CHG} android', 'P 0.9589 DEGENERATE', 'P ≈0.9967', 'degenerate->faithful', 'MED-HIGH', 0.99, True, ['OOF-android'], GEO_OK),
    pred(f'{SFA} ios', 'P 0.9841 DEGENERATE', 'P ≈0.9974', 'degenerate->faithful', 'HIGH', 0.99, True, ['OOF-ios'], GEO_OK),
    pred(f'{SFA} android', 'P 0.9835 DEGENERATE', 'P ≈0.9967 (via PositionedParentFlowSlot)', 'degenerate->faithful', 'MED-HIGH', 0.99, True, ['OOF-android'], GEO_OK),
    pred(f'{PR3} ios', 'P 0.9594 DEGENERATE', 'P ≈0.9974 (the relpos span chain must net 0)', 'degenerate->faithful', 'MED', None, False, ['OOF-ios'], GEO_OK),
    pred(f'{PR3} android', 'P 0.9589 DEGENERATE', 'P ≈0.9967', 'degenerate->faithful', 'MED', None, False, ['OOF-android'], GEO_OK),
    pred(f'{CC["04"]} android', 'f 0.8286', 'moves, stays f (the table itself renders wrong on both natives)', 'mover', 'MED', None, False, ['OOF-android'], direction='undirected',
         directionWhy='oof-containing-block.md §10 R1: the positioned-container Box now hosts 004\'s in-flow <span>FAIL</span>; its sign is not pre-registered'),
    pred(f'{NBR["-3"]} android', 'f 0.9574', 'P ≈0.99 (iOS twin of the same geometry 0.9921)', 'flip', 'MED-HIGH', 0.96, True, ['CBB-android'], GEO_OK),
  ] + [pred(f'{NBR[k]} android', CELLS[f'{NBR[k]} android'], 'P ≈0.99 (iOS twin 0.9936 / 0.9998 / 0.9981)', 'short box -> faithful', 'MED-HIGH', 0.98, True, ['CBB-android'], GEO_OK) for k in ('', '-2', '-4')]
    + [pred(f'{APOS[k]} android', CELLS[f'{APOS[k]} android'] + ' DEGENERATE', 'P 0.9967 (sim: green repainted 100x100)', 'degenerate->faithful', 'HIGH', 0.995, True, ['CBB-android'], GEO_OK) for k in ('htb-ltr', 'vlr-ltr', 'vrl-ltr')]
    + [pred(f'{APOS[k]} android', CELLS[f'{APOS[k]} android'] + ' DEGENERATE', 'P ≈0.9967 (the rtl static-position arithmetic runs with the corrected size for the first time)', 'degenerate->faithful', 'MED', None, False, ['CBB-android'], GEO_OK) for k in ('htb-rtl', 'vlr-rtl', 'vrl-rtl')]
    + [pred(f'{G006} android', 'f 0.8223', 'may move, stays f (VerticalTextFlowLayout budget reads LocalContainingBlock)', 'watched side reader', 'LOW', None, False, ['CBB-android'], direction='undirected',
            directionWhy='a side reader of the corrected containing block; no replay'),
       pred(f'{G033} ios', 'f 0.94', 'P ≈1.000 (sim 1.0000: every non-rule pixel is already ref-exact)', 'flip', 'HIGH', 0.99, True, ['GAP-ios'], GEO_OK),
       pred(f'{G033} android', 'f 0.94', 'P ≈1.000 (sim 1.0000)', 'flip', 'HIGH', 0.99, True, ['GAP-android'], GEO_OK)],
  'mustNotMove': [],     # filled below (needs every lane's carriers)
  'geometryProbes': [
    {'cmd': 'python3 tools/titan/results/wave54-plan/oof-containing-block.geometry.py <run>',
     'expect': {f'{k} {p}': GEO_OK for k in ('position-relative-003', 'abspos/static-fixed-inside-abspos', 'position-relative-004',
                                             'change-insets-inside-strict-containment-nested', 'contain-content-003', 'contain-content-011',
                                             'backdrop-filter-containing-block') for p in PLATS},
     'gating': {'contain-content-003 android': 'OOF-android', 'contain-content-011 android': 'OOF-android',
                'position-relative-004 ios': 'OOF-ios', 'position-relative-004 android': 'OOF-android',
                'change-insets-inside-strict-containment-nested ios': 'OOF-ios', 'change-insets-inside-strict-containment-nested android': 'OOF-android',
                'abspos/static-fixed-inside-abspos ios': 'OOF-ios', 'abspos/static-fixed-inside-abspos android': 'OOF-android'},
     'report': [f'{k} {p}' for k in ('position-relative-003', 'backdrop-filter-containing-block') for p in ('ios', 'android')],
     'onWave53Final': '12 native rows WRONG (green at the canvas origin / top-right), every ref and web row OK, iOS contain-content-003/-011 OK'},
    {'cmd': 'python3 tools/titan/results/wave54-plan/compose-wpt-content-box-cb.geometry.py <run>',
     'expect': {f'abspos/abspos-autopos-{k} {p}': GEO_OK for k in ('htb-ltr', 'vlr-ltr', 'vrl-ltr', 'htb-rtl', 'vlr-rtl', 'vrl-rtl') for p in ('web', 'android')}
               | {f'abspos/abspos-autopos-{k} ios': GEO_OK for k in ('htb-ltr', 'vlr-ltr', 'vrl-ltr')}
               | {f'abspos/abspos-autopos-{k} ios': 'GEOMETRY WRONG (green (16, 88, 105, 187) vs ref (16, 88, 115, 187))' for k in ('htb-rtl', 'vlr-rtl', 'vrl-rtl')}
               | {f'backdrop-filter-nested-border-radius-clip{k} {p}': GEO_OK for k in ('', '-2', '-3', '-4') for p in PLATS},
     'gating': {**{f'abspos/abspos-autopos-{k} android': 'CBB-android' for k in ('htb-ltr', 'vlr-ltr', 'vrl-ltr')},
                **{f'backdrop-filter-nested-border-radius-clip{k} android': 'CBB-android' for k in ('', '-2', '-3', '-4')}},
     'report': [f'abspos/abspos-autopos-{k} android' for k in ('htb-rtl', 'vlr-rtl', 'vrl-rtl')],
     'note': 'the three iOS *-rtl rows keep their own 10-px iOS offset (queue-scout-layout §3; not this lane): their expected line is today\'s WRONG verdict, verbatim',
     'onWave53Final': 'Android 10/10 WRONG; ref + web 20/20 OK; iOS 7 OK + the 3 rtl rows WRONG'},
    {'cmd': 'python3 tools/titan/results/wave54-plan/flex-zero-gap-rules.geometry.py <run>',
     'expect': {f'flex/flex-gap-decorations-033 {p}': GEO_OK for p in PLATS},
     'gating': {'flex/flex-gap-decorations-033 ios': 'GAP-ios', 'flex/flex-gap-decorations-033 android': 'GAP-android'}, 'report': [],
     'onWave53Final': 'ios / android WRONG (redPx 0 vs ref 2200); ref / web OK',
     'reads': ('red / blue counts ±2 % and bboxes ±1 px; ±1 px against the ref: the red runs on a row through every flex line (y20 / y95 / y145, '
               'fix r2 R2-M1), the blue runs on a column through every item column (x40 / x90 / x140), and the red runs on a column through each '
               'column-rule gap (x65 / x115: every segment\'s vertical extent, flex line by line; fix r3, plan-skeptic R3-S3)')},
  ],
  'revertUnits': {
    'OOF-android': {'commit': 'Compose layout/position/OutOfFlowContainingBlock.kt (new) + CanvasRootHoist.kt call-site lines :222 (one OR), :340 (inset clause), :389 (FIXED joins RC1) + OutOfFlowContainingBlockTest.kt + CanvasRootHoistTest rows',
                    'captures': caps(android=oof_android), 'wire': [], 'revertOrder': ['OOF-android']},
    'OOF-ios': {'commit': 'Swift StyleEngine/layout/position/OutOfFlowContainingBlock.swift (new) + FixedHoist.swift call-site lines :309, :347, :115 + OutOfFlowContainingBlockTests.swift + FixedHoist test rows',
                'captures': caps(ios=oof_ios), 'wire': [], 'revertOrder': ['OOF-ios']},
    'CBB-android': {'commit': 'DynamicValueResolver.kt childContainingBlock(…, wptCaptureMode = false) + one condition (or ContainingBlockBands.kt ≤ 60 lines) + DynamicValueResolverTest rows + ComponentRenderer.kt seam (:1934 one named argument)',
                    'captures': caps(android=[stem(t) for t in cbb_tests] + [stem(G006)]), 'wire': [], 'revertOrder': ['CBB-android']},
    'GAP-android': {'commit': 'Compose columns/GapDecoration{Lines,Segments,Geometry}.kt (keep a zero-extent gap as a rule position; isPositionable) + GapDecoration*Test rows',
                    'captures': caps(android=[stem(G033)]), 'wire': [], 'revertOrder': ['GAP-android']},
    'GAP-ios': {'commit': 'Swift StyleEngine/columns/GapDecoration{Lines,Segments,Geometry}.swift (the byte-parallel twin) + GapDecoration*Tests rows',
                'captures': caps(ios=[stem(G033)]), 'wire': [], 'revertOrder': ['GAP-ios']},
  },
  'sharedCells': {
    'css-contain/contain-content-004.html': 'in compose-table-body-cell\'s D2 radius but NOT an L2 carrier (L2\'s carrier set is 006 android only): an L4 carrier on android, must-not-move on ios/web',
    'filter-effects/backdrop-filter-clip-rect.html, -edge-clipping.html': 'L4 positioned-establisher CONTROLS (kept inert by F1\'s "not itself absolute/fixed" restriction) that are also L3 U3 CAPTURE carriers: the capture control cannot attribute them, so L4\'s invariance claim on them rests on pins P1 (last row) and P3 (pure walks hoist exactly as today) and on the closing A/B; a fall there reverts U3 first (rule 6x)',
    'wpt__css-anchor-position__anchor-center-safe-rtl (unscored)': 'an L4 fixed control and an L1 P android capture carrier: L4\'s invariance rests on pin P3 over its verbatim payload',
  },
}

# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# L5 · ua-heading-face — the Compose UAElementFontRule twin + the fold-aware heading gate on both natives.
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
BII = T('css-break', 'block-in-inline-015-print')
INS = {n: T('css-text-decor', f'text-decoration-inset-{n}') for n in ('001', '002', '003', '004', '005', '006', '007', '008', '009', '011', '012', '013', '014', '015', '016', '024')}
TDC = T('css-text-decor', 'text-decoration-color')
SUB2, SUB3 = T('css-text-decor', 'text-decoration-subelements-002'), T('css-text-decor', 'text-decoration-subelements-003')
CLI, CRIOU = T('css-lists', 'counter-list-item'), T('css-lists', 'counter-reset-increment-overflow-underflow')
BRC, CSC = T('filter-effects', 'backdrop-filter-border-radius-change'), T('filter-effects', 'backdrop-filter-corner-shape-change')
ua_android = [BII, INS['005'], INS['006'], INS['014'], TDC, SUB3, CLI, CRIOU, BRC, CSC]
ua_ios = [INS['005'], INS['006'], INS['014']]
assert {r['key'].replace('css/', '', 1) for r in uah['rows']} >= set(ua_android)
L5 = {
  'dir': 'tools/titan/results/wave54-ua-heading-face',
  'briefs': ['ua-heading-face.md', 'queue-scout-text-web.md'],
  'captureCarriers': caps(ios=[stem(t) for t in ua_ios], android=[stem(t) for t in ua_android]),
  'wireCarriers': [],
  'carrierRule': 'an author-UNSIZED h1-h6 (no own FontSize/Font/FontWeight; 8 documents) or a sub/sup leaf, on Compose; on iOS only a FOLDED heading host (the stand-down retires only where InlineRunFold/InlineRunFlow returns Folded): inset-005/-006/-014. 14 sized-heading documents stand down (byte-identical) except the bold-only half on two filter-effects h1s (android movers)',
  'probeSections': ['css-text-decor', 'css-break', 'css-lists', 'filter-effects'],
  'predictions': [
    pred(f'{BII} android', 'f 0.9489', 'P ≈0.989 (the identical rule on iOS gives P 0.9894 on the same IR)', 'flip', 'HIGH', 0.97, True, ['U1-android'], GEO_OK),
    pred(f'{INS["005"]} android', 'f 0.9', 'P ≈0.98 (anchors inset-001…004 android 0.984-0.9859)', 'flip', 'MED', None, False, ['U1-android'], GEO_OK),
    pred(f'{INS["006"]} android', 'f 0.8993', 'P ≈0.98', 'flip', 'MED', None, False, ['U1-android'], GEO_OK),
    pred(f'{INS["005"]} ios', 'f 0.8995', 'P ≈0.965 (anchors ios 0.9634-0.9707)', 'flip', 'MED', None, False, ['U2-ios'], GEO_OK),
    pred(f'{INS["006"]} ios', 'f 0.8987', 'P ≈0.965', 'flip', 'MED', None, False, ['U2-ios'], GEO_OK),
    pred(f'{INS["014"]} ios', 'f 0.9238', 'P (no monospace anchor; the probe decides)', 'flip', 'MED-LOW', None, False, ['U2-ios'], GEO_OK),
    pred(f'{INS["014"]} android', 'f 0.923', 'P (no monospace anchor; the probe decides)', 'flip', 'MED-LOW', None, False, ['U1-android'], GEO_OK),
    pred(f'{TDC} android', 'f 0.6164', '≈0.67 (the iOS anchor with the rule is f 0.674)', 'mover; look, never count', 'LOW', None, False, ['U1-android']),
    pred(f'{CLI} android', 'f 0.7307', 'moves (iOS with the rule: f 0.7864)', 'mover', 'LOW', None, False, ['U1-android'], direction='undirected',
         directionWhy='only a cross-platform anchor (iOS), which the brief declined to turn into a magnitude'),
    pred(f'{CRIOU} android', 'f 0.8699', 'direction unknown (iOS with the rule f 0.857; its digits are a counter defect)', 'mover', 'LOW', None, False, ['U1-android'], direction='undirected',
         directionWhy='the brief says "direction unknown"; the iOS anchor (0.857) sits BELOW today\'s 0.8699'),
    pred(f'{SUB3} android', 'f 0.9084', 'moves (stacked sup -> smaller)', 'mover', 'LOW', None, False, ['U1-android'], direction='undirected',
         directionWhy='no replay; the stacked sup face change has no pre-registered sign'),
    pred(f'{BRC} android', 'f 0.5977', 'moves (bold half only)', 'mover', 'LOW', None, False, ['U1-android'], direction='undirected',
         directionWhy='bold-only half of a sized h1; no replay'),
    pred(f'{CSC} android', 'f 0.4904', 'moves (bold half only)', 'mover', 'LOW', None, False, ['U1-android'], direction='undirected',
         directionWhy='bold-only half of a sized h1; no replay'),
  ],
  'mustNotMove': [],     # filled below
  'geometryProbes': [
    {'cmd': 'python3 tools/titan/results/wave54-plan/ua-heading-face.geometry.py <run>',
     'expect': {f'{t} {p}': GEO_OK for t in ('block-in-inline-015-print', 'text-decoration-inset-005', 'text-decoration-inset-006',
                                             'text-decoration-inset-014', 'text-decoration-inset-011') for p in PLATS},
     'gating': {'block-in-inline-015-print android': 'U1-android'},
     'report': [f'text-decoration-inset-{n} {p}' for n in ('005', '006', '014') for p in ('ios', 'android')],
     'note': 'inset-011 is the CONTROL: the probe prints OK only while its native bands EQUAL the recorded wave53-final bands',
     'onWave53Final': '7 target rows WRONG (android band 9 vs ref 18; inset-005/-006 one band vs 2; inset-014 line-1 11/12 px); ref, web, iOS block-in-inline and the inset-011 control OK',
     'teeth': 'fix r1 (plan-skeptic M2): block-in-inline-015-print also checks every band TOP within ±3 px of the ref; the skeptic\'s pitch fakes print WRONG from +2 px/line (fix-r1/post-M2.pitch-down6.out.txt), the iOS anchor (tops 30/66/105/144 vs 29/65/103/142) stays OK'},
  ],
  'revertUnits': {
    'U1-android': {'commit': 'Compose typography/UAElementFontRule.kt (new twin) + typography/UAHeadingFoldGate.kt (new) + their JVM tests + ComponentRenderer.kt seam-1 (:1141 wrap ListStyleUaRule.apply in UAElementFontRule.apply, before DynamicValueResolver :1206)',
                   'captures': caps(android=[stem(t) for t in ua_android]), 'wire': [], 'revertOrder': ['U1-android']},
    'U2-ios': {'commit': 'Swift StyleEngine/typography/UAHeadingFoldGate.swift (new) + UAHeadingFoldGateTests.swift + UAElementFontRule.swift (docs only) + UAElementFontRuleTests.swift (additions) + ComponentRenderer.swift seam-2 (:343-356 the hasElementChildren: argument)',
               'captures': caps(ios=[stem(t) for t in ua_ios]), 'wire': [], 'revertOrder': ['U2-ios']},
  },
  'stopRule': 'if the lane\'s first JVM / Catalyst pins say the verbatim inset-005/-006/-014 hosts BAIL (not Folded), the 005/006/014 rows are void and the lane stops at the GO-SMALL fallback (U1 leaf-only)',
}

# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# L6 · web-tail — web-root-separator (RS, harness) + web-out-of-flow-hyphen-box (W1, runtime; branch decided by step 0).
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
rs_lines = [l for l in open(os.path.join(HERE, 'web-root-separator.census.out.txt')) if '.html' in l]
rs_tests = [l.split()[0] for l in rs_lines]
assert len(rs_tests) == 27, len(rs_tests)
rs_scored = [t for t in rs_tests if scored(t, 'web')]
assert len(rs_scored) == 26, rs_scored                      # overlay-button-appearance is unscored
OOF2 = T('css-text', 'hyphens', 'hyphens-out-of-flow-002')
OOF1 = T('css-text', 'hyphens', 'hyphens-out-of-flow-001')
BS = {n: T('css-ui', f'box-sizing-0{n}') for n in ('07', '08', '09', '10', '11', '13', '14', '15', '16', '17', '18', '19', '20', '21', '22', '24', '25')}
SRO, SRI = T('css-position', 'position-absolute-semi-replaced-stretch-other'), T('css-position', 'position-absolute-semi-replaced-stretch-input')
w1_refused = [T('CSS2', 'abspos', 'between-float-and-text'), T('CSS2', 'abspos', 'hypothetical-inline-alone-on-second-line'),
              T('CSS2', 'abspos', 'static-inside-inline-001'), T('CSS2', 'abspos', 'static-inside-inline-002'),
              T('CSS2', 'abspos', 'static-inside-inline-003'), T('css-cascade', 'all-prop-001'), SRO,
              T('css-pseudo', 'first-letter-list-item-dynamic-001'), T('css-tables', 'abspos-container-change-dynamic-001'),
              T('css-values', 'ch-unit-001')]
L6 = {
  'dir': 'tools/titan/results/wave54-web-tail',
  'briefs': ['web-root-separator.md', 'web-out-of-flow-hyphen-box.md', 'queue-scout-text-web.md'],
  'captureCarriers': caps(web=[stem(t) for t in rs_tests] + [stem(OOF2)]),
  'wireCarriers': [],
  'carrierRule': {
    'RS': 'a root-level adjacent pair of inline-level roots whose earlier sibling carries meta.role "ws-after" (the WWS predicate, lifted verbatim from renderChildSeparator): 27 documents (web only; the natives already pack root atoms with ROOT_ATOM_GAP_PX)',
    'W1': 'a paint-inert out-of-flow run member (byte-for-byte the Compose InertOutOfFlowMember.admits predicate) splitting a word mid-word in a host whose OWN hyphens is auto: 4 members, all in hyphens-out-of-flow-002 (W1-u, the ungated fallback, would add -001: not taken, L6 owns NodeRenderer.ts)',
  },
  'probeSections': ['css-ui', 'css-position', 'css-cascade', 'css-display', 'CSS2', 'css-break', 'css-values',
                    'css-writing-modes', 'css-text', 'css-pseudo', 'css-tables'],
  'predictions': [
    pred(f'{BS["07"]} web', 'f 0.9036', 'P 0.985 (simulated separator 0.9852)', 'flip (bold <strong> residue: the corpus inline-run-merged convention)', 'HIGH', 0.975, True, ['RS'], GEO_OK),
    pred(f'{BS["08"]} web', 'f 0.8943', 'P 0.974 (0.9736)', 'flip', 'HIGH', 0.965, True, ['RS'], GEO_OK),
    pred(f'{BS["22"]} web', 'f 0.9442', 'P 0.970 (0.9704)', 'flip', 'MED-HIGH', 0.96, True, ['RS'], GEO_OK),
    pred(f'{SRO} web', 'f 0.941', 'P 0.967 (0.9667)', 'flip (the space moves the next abspos box\'s static position, as in the ref)', 'MED', None, False, ['RS'], GEO_OK),
  ] + [pred(f'{BS[n]} web', 'P 0.9713 DEGENERATE', '≈0.982 (simulated 0.9822)', 'degenerate->faithful (atoms packed)', 'MED', None, False, ['RS'], GEO_OK)
       for n in ('10', '11', '14', '15', '16', '17', '18', '19')]
    + [pred(f'{BS[n]} web', 'P 0.9599 DEGENERATE', '≈0.982 (simulated 0.9822)', 'degenerate->faithful', 'MED', None, False, ['RS'], GEO_OK) for n in ('20', '21', '24', '25')]
    + [pred(f'{BS["13"]} web', 'P 0.9538 DEGENERATE', '≈0.970 (simulated 0.9704)', 'degenerate->faithful', 'MED', None, False, ['RS'], GEO_OK),
       pred(f'{SRI} web', 'P 0.9596 DEGENERATE', '≈0.966 (simulated 0.9664)', 'degenerate->faithful', 'MED', None, False, ['RS'], GEO_OK),
       pred(f'{T("css-cascade", "scope-pseudo-element")} web', 'f 0.9353', 'up (box lefts to x16/123/229; its B/Foo wrap defect remains)', 'mover', 'LOW', None, False, ['RS']),
       pred(f'{T("css-display", "display-flow-root-list-item-001")} web', 'f 0.8003', 'mover', 'mover', 'LOW', None, False, ['RS'], direction='undirected',
            directionWhy='in the RS 27-document radius; not simulated')]
    # The at-risk passes (RS census): predicted to stay or rise; a prediction row each so revert rule 2 (delta <= -0.002) fires.
    + [pred(f'{t} web', CELLS[f'{t} web'], 'stays or rises (the separator replays a space the SOURCE had)', 'at-risk pass', 'MED', None, False, ['RS'])
       for t in (T('CSS2', 'abspos', 'static-inside-inline-block'), BII, T('css-ui', 'appearance-auto-input-non-widget-001'),
                 T('css-values', 'attr-style-sharing-1'), T('css-writing-modes', 'baseline-with-orthogonal-flow-001'), BS['09'])]
    + [pred(f'{OOF2} web', 'f 0.9411', 'P 1 (replay W1-exact: ssim 1, 0 mismatched px)', 'flip (boxes 4-5 become box 7\'s runs shape)', 'HIGH (branch R) / MED-HIGH (branch H)', 0.995, True, ['W1'], GEO_OK)],
  'mustNotMove': [],     # filled below
  'geometryProbes': [
    {'cmd': 'python3 tools/titan/results/wave54-plan/web-root-separator.geometry.py <run>',
     'expect': {f'{t} {p}': GEO_OK for t in ('box-sizing-007', 'box-sizing-008', 'box-sizing-010', 'box-sizing-013', 'box-sizing-022',
                                             'position-absolute-semi-replaced-stretch-other', 'position-absolute-semi-replaced-stretch-input') for p in ('web',)}
               | {f'{t} {p}': GEO_OK for t in ('box-sizing-007', 'box-sizing-008', 'box-sizing-010', 'box-sizing-022') for p in ('ios', 'android')}
               # fix r1 (plan-skeptic S3): one key per tallied box-sizing capture (011, 014-019 = the 010 class; 020/021/024/025)
               | {f'box-sizing-0{n} {p}': GEO_OK for n in ('11', '14', '15', '16', '17', '18', '19', '20', '21', '24', '25') for p in PLATS}
               | {'box-sizing-013 ios': 'GEOMETRY WRONG (second atom edge xNone vs ref x91 on row 150)',
                  'box-sizing-013 android': 'GEOMETRY WRONG (second atom edge xNone vs ref x91 on row 150)',
                  'position-absolute-semi-replaced-stretch-other ios': 'GEOMETRY WRONG (second atom edge xNone vs ref x192 on row 60)',
                  'position-absolute-semi-replaced-stretch-other android': 'GEOMETRY WRONG (second atom edge xNone vs ref x192 on row 60)',
                  'position-absolute-semi-replaced-stretch-input ios': 'GEOMETRY WRONG (second atom edge xNone vs ref x192 on row 60)',
                  'position-absolute-semi-replaced-stretch-input android': 'GEOMETRY WRONG (second atom edge xNone vs ref x192 on row 60)'},
     'gating': {'box-sizing-007 web': 'RS', 'box-sizing-008 web': 'RS', 'box-sizing-022 web': 'RS'},
     'report': [f'box-sizing-0{n} web' for n in ('10', '11', '13', '14', '15', '16', '17', '18', '19', '20', '21', '24', '25')]
               + [f'position-absolute-semi-replaced-stretch-{k} web' for k in ('other', 'input')],
     'teeth': 'fix r1 (plan-skeptic S2): after the recorded row, EVERY ref atom row band is checked on its middle row; the skeptic\'s partial fix (only the probed band separated) prints WRONG on 007 / 008 web (fix-r1/post-S2.rs-partial.out.txt), the ref itself 72/72 OK',
     'note': 'native rows are controls; box-sizing-013 and the semi-replaced natives keep their own (larger) native defects — their expected line is today\'s WRONG verdict, verbatim',
     'onWave53Final': 'every web row WRONG (x146 / xNone / x187); every ref row OK'},
    {'cmd': 'python3 tools/titan/results/wave54-plan/web-out-of-flow-hyphen-box.geometry.py <run>',
     'expect': {f'hyphens/{t} {p}': GEO_OK for t in ('hyphens-span-002', 'hyphens-out-of-flow-001', 'hyphens-out-of-flow-002') for p in PLATS},
     'gating': {'hyphens/hyphens-out-of-flow-002 web': 'W1'}, 'report': [],
     'onWave53Final': 'hyphens-out-of-flow-002 web WRONG (boxes 4,5 height 26 vs ref 46); every other row OK (anchor self-check)'},
  ],
  'revertUnits': {
    'RS': {'commit': 'apps/web-harness/src/ui/ComposedRootSeparator.ts (new) + ComposedRootSeparator.test.ts + the two ComposedCaptureGallery.tsx .map call sites + ComponentRenderer.tsx seam-1 (pure lift + export of the WWS branch as wsAfterSeparator)',
           'captures': caps(web=[stem(t) for t in rs_tests]), 'wire': [], 'revertOrder': ['RS']},
    'W1': {'commit': 'branch R/H2: runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts (new) + InlineRuns.ts (optional 4th arg, joinedOutOfFlowMembers) + NodeRenderer.ts :339 one line + tests/renderer/InertOutOfFlowWordJoin.test.tsx; branch H: ComponentRenderer.tsx seam-2 (renderText :991) INSTEAD of the runtime files',
           'captures': caps(web=[stem(OOF2)]), 'wire': [], 'revertOrder': ['W1']},
  },
  'step0': {'what': 'the CDP probe of web-out-of-flow-hyphen-box.md §5 (variants V0-V6, getBoundingClientRect heights, puppeteer Chromium, Inter embedded, 390-px viewport)',
            'preRegistered': 'V1 = 26, V3 = 46, V5 = 46; prediction (MED): V6 boxes 4/5 = 26 and V0 = 26 (branch R)',
            'decision': {'V6/V0 = 26': 'branch R: W1 in the runtime', 'V6 = V0 = 46, V2 = 46, V1 = 26': 'branch H: seam-2 (renderText) instead of W1, prediction rests on V2 (MED-HIGH)',
                         'V6 = 46, V2 = 26': 'branch H2: bisect V2 -> V0 one attribute at a time; land W1 and record the bisect', 'V3 != 46': 'STOP: the premise is false; W1 is not built, the -002 web row is withdrawn'}},
}

# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# L7 · label-chrome — the tripwire learns the chrome contract's "iff" (U1), then all-then-color is seeded (U2).
# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# census rows name the per-test IR document ('wpt__css-cascade__all-prop-001.json'); its stem gives the test path.
all_docs = [test_of(d['doc'][:-len('.json')]) for d in lcr['corpus']['docsWithAll']]
assert len(all_docs) == 11, all_docs
L7 = {
  'dir': 'tools/titan/results/wave54-label-chrome',
  'briefs': ['label-chrome-all-reset.md'],
  'captureCarriers': caps(), 'wireCarriers': [],
  'carrierRule': 'none in the corpus: WPT-mode captures draw no chrome on any platform; the radius is tools/visual/ (the tripwire, its exempt manifest, 18 new baseline PNGs)',
  'probeSections': [],
  'predictions': [
    note('tripwire: 001_ATC_PropsThenAll_InGreenParent / 003_ATC_InitialUnderRedParent / 005_ATC_DirectionSurvives RED -> green (exempt: container / container / text root; band rows 0..15 byte-identical x3) — HIGH (replay R1 on the same bytes)', ['U1', 'U2-seed']),
    note('tripwire: 000_ATC_AllThenProps / 002_reset / 004_span green with glyph px 224 / 80 / 65 — HIGH (R0/R1)', ['U1', 'U2-seed']),
    note('tripwire: the 130 committed stems green with verdict objects identical 130/130 — HIGH (R3)', ['U1']),
    note('tripwire: mutations m1-m5 red — HIGH (R2)', ['U1']),
    note('fixture net: fixtures/combinations/all-then-color.json exit 0 against committed baselines, "no regressions vs baseline (18 platform-comparisons ran)" — MED-HIGH, floor exit 0', ['U2-seed']),
    note('fresh seed byte-identical to label-chrome-all-reset.seeded-77fe41e8/ on 18/18 — MED (a wave-54 lane may legitimately move a pixel: then it is named and looked at, never waved through)', ['U2-seed']),
  ],
  'mustNotMove': sorted(set(cells_of(all_docs))),
  'geometryProbes': [],
  'geometrySeed': {'cmd': 'python3 tools/titan/results/wave54-plan/label-chrome-all-reset.geometry.py baseline',
                   'expect': '18 x "→ GEOMETRY OK", 6 x "band rows 0..15 byte-identical ×3: True", "label drawn ⇔ label-due …: 18/18", exit 0',
                   'teeth': '--naive prints GEOMETRY WRONG on 001/003/005 x3 and exits 1',
                   'onSeededCopy': '18 OK on label-chrome-all-reset.seeded-77fe41e8/ (executed at planning)'},
  'revertUnits': {
    'U1': {'commit': 'tools/visual/label-chrome-tripwire.test.mjs (verified exempt manifest, P = empty path, clause (iv), synthetic controls, hint on red) + tools/visual/label-chrome-exempt.json (new) + optional tools/visual/label-chrome-check.mjs (new, the pure checker)',
           'captures': caps(), 'wire': [], 'revertOrder': ['U2-seed', 'U1']},
    'U2-seed': {'commit': 'the 18 tools/visual/baseline/{Android,iOS,web}__{000_ATC_AllThenProps,001_ATC_PropsThenAll_InGreenParent,002_reset,003_ATC_InitialUnderRedParent,004_span,005_ATC_DirectionSurvives}.png (exactly these names: label-chrome-all-reset.seeded-77fe41e8/; the glob __00{0..5}_*.png also matches 66 committed baselines of four other fixtures) from UPDATE_BASELINE=1 ./test-all.sh fixtures/combinations/all-then-color.json on the CLOSING tree + the orchestrator doc lines (DYNAMIC_CAPTURE.md §5, tripwire header 130 -> 136 / 390 -> 408, STATUS.md:19, tooling counts, gate-fixtures.txt comment, fixture _comment, BACKLOG 0(e), wave53-gate/_note.md + STATUS.md:2678 correction lines)',
                'captures': caps(), 'wire': [], 'revertOrder': ['U2-seed'],
                'note': 'PNGs without U1 re-create the wave-53 red in CI test-tooling: U2-seed is reverted before U1, never after'},
  },
}

lanes = {'L1-rtl-marker-bake': L1, 'L2-table-body-cell': L2, 'L3-hyphenate-character': L3, 'L4-oof-layout': L4,
         'L5-ua-heading-face': L5, 'L6-web-tail': L6, 'L7-label-chrome': L7}

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

excluded = []   # printed, so every exclusion of another lane's carrier from a must-not-move list is visible
def mnm(lane_name, lines):
    out = []
    for l in sorted(set(lines)):
        o = carrier_of(l)
        if o is None:
            out.append(cell(*l.rsplit(' ', 1)))
        elif o != lane_name:
            excluded.append((lane_name, l, o))
    return out

# ── the must-not-move lists that need every lane's carriers ─────────────────────────────────────────────────────────
L2['mustNotMove'] = mnm('L2-table-body-cell',
    cells_of([S006], ('web', 'ios'))
    + cells_of([T('CSS2', 'css21-errata', f's-11-1-1b-00{n}') for n in (1, 2, 3, 4, 5, 7, 8, 9)])
    + cells_of([t for t in compose_tables if t != S006], ('android',))
    + cells_of([T('css-position', 'position-absolute-dynamic-static-position-table-cell'), T('css-tables', 'baseline-vertical')]
               + [t for t in TESTS if t.startswith('css-tables/fixup-dynamic-anonymous-')]))
L4['mustNotMove'] = mnm('L4-oof-layout',
    cells_of(m2_tests + m1_tests + cbb_tests + [G033], ('web',))
    + cells_of([CC['03'], CC['11'], CC['04']], ('ios',))
    # the css-view-transitions fixed controls are not PROBED (PLAN §6) but the closing gate reads them: they stay here.
    + cells_of(fixed_controls + vt_controls) + cells_of([t for t in estab_controls if t not in (BF['clip-rect'], BF['edge-clipping'])])
    + cells_of([T('css-transforms', 'backface-visibility-hidden-001'), T('css-images', 'cross-fade-target-alpha')])
    + cells_of(cbb_tests, ('ios',))
    + [k for k in CELLS if k.startswith('css-gaps/') and k not in (f'{G033} ios', f'{G033} android', f'{G006} android')])
L5['mustNotMove'] = mnm('L5-ua-heading-face',
    cells_of([BII], ('ios', 'web'))
    + cells_of([INS[n] for n in ('011', '001', '002', '003', '004', '009', '015', '016', '024', '007', '008', '012', '013')], ('ios', 'android'))
    + cells_of([SUB2], ('ios', 'android'))
    + cells_of([TDC, SUB3, CLI, CRIOU, BRC, CSC], ('ios',))
    + cells_of(ua_android + ua_ios + [INS['011'], SUB2], ('web',)))
L6['mustNotMove'] = mnm('L6-web-tail',
    cells_of(rs_scored, ('ios', 'android'))
    + cells_of([OOF1, T('css-text', 'hyphens', 'hyphens-span-002'), T('css-text', 'hyphens', 'hyphens-auto-control'),
                T('css-text', 'hyphens', 'hyphens-auto-inline-010')], ('web',))
    + cells_of(w1_refused, ('web',))
    + cells_of([OOF1, OOF2], ('ios', 'android')))
for name in ('L1-rtl-marker-bake', 'L3-hyphenate-character', 'L7-label-chrome'):
    lanes[name]['mustNotMove'] = mnm(name, lanes[name]['mustNotMove'])

# ── every carrier stem has a capture in the run of record (and a per-test IR document if it is a wire carrier) ─────
RUNDIR = os.path.join(ROOT, 'tools', 'titan', 'runs', RUN, 'sections')
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
    for p in lane['predictions']:
        assert all(u in lane['revertUnits'] for u in p['units']), f'{name} {p["cell"]} names an unknown unit {p["units"]}'
        if p['kind'] != 'non-corpus':
            test, plat = p['cell'].rsplit(' ', 1)
            assert carrier_of(p['cell']) == name, f'{name} predicts {p["cell"]} but it is not its carrier ({carrier_of(p["cell"])})'
            for u in p['units']:
                assert stem(test) in lane['revertUnits'][u]['captures'][plat], f'{name} {p["cell"]}: unit {u} does not carry it'

# ── the probe runs ───────────────────────────────────────────────────────────────────────────────────────────────────
ORDER = ['CSS2', 'css-anchor-position', 'css-backgrounds', 'css-break', 'css-cascade', 'css-color', 'css-contain', 'css-counter-styles',
         'css-display', 'css-flexbox', 'css-gaps', 'css-grid', 'css-images', 'css-lists', 'css-masking', 'css-multicol', 'css-overflow',
         'css-position', 'css-pseudo', 'css-sizing', 'css-tables', 'css-text', 'css-text-decor', 'css-transforms', 'css-ui', 'css-values',
         'css-view-transitions', 'css-writing-modes', 'filter-effects', 'selectors']        # gate-driver.sh DEFAULT_SECTIONS
assert sorted(ORDER) == sorted(os.listdir(RUNDIR)), 'gate section list drifted'
in_order = lambda secs: [s for s in ORDER if s in set(secs)]
PROBE = in_order({s for l in lanes.values() for s in l['probeSections']})
STAGE1 = in_order({s for l in lanes.values() for s in l.get('stage1Sections', [])})
NOT_PROBED = [s for s in ORDER if s not in PROBE]
# Every carrier's section is probed (a carrier first read at the closing gate is the wave-53 nit-10 failure).
for plat in PLATS:
    for s in union[plat]:
        assert section_of(s) in PROBE, f'carrier {plat} {s} sits in an unprobed section'

# ── consistency: no lane names as must-not-move a capture another lane may change ───────────────────────────────────
for name, lane in lanes.items():
    for line in lane['mustNotMove']:
        assert carrier_of(line) is None, f'{name} must-not-move {line} is a carrier of {carrier_of(line)}'

# ── probe decisions (EMPTY at planning; the orchestrator fills these two tables after wave54-pre / wave54-probe and
# re-runs this script — expectations.json is never hand-edited). A reverted unit's carriers are WITHDRAWN (a change on
# them at the closing gate is a leak again, control-check.mjs) unless another, kept unit of the same lane still carries
# them; a prediction whose units are ALL reverted is withdrawn from R4 and held to must-not-move in R5
# (adjudicate.mjs); a prediction with SOME units reverted stays, restated in RESTATE (to / floor / confidence) with the
# PLAN §10 line that justifies it. Shape (wave-53 probeDecisions):
#   REVERTED = [{'lane': 'L1-rtl-marker-bake', 'unit': 'Mprime', 'run': 'wave54-pre', 'commit': '<sha>',
#                'revertCommit': '<sha>', 'rule': '4: [M] android GEOMETRY WRONG (…)'}]
#   RESTATE  = {'css-counter-styles/counter-suffix.html android': {'to': 'P ≈0.9815 (P alone)', 'why': 'PLAN §10 item n'}}
REVERTED = []
RESTATE = {}
if _REVERTED_FILE:
    REVERTED = json.load(open(_REVERTED_FILE))
if _RESTATE_FILE:
    RESTATE = json.load(open(_RESTATE_FILE))

def key_units(probe, key):
    """The units a gating key needs on the tree (its `requires` entry, else the units its `gating` value names)."""
    return set(probe.get('requires', {}).get(key) or probe['gating'][key].split('|'))

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
        for p in lane['predictions']:      # fix r2: a withdrawn row gates nothing (held to must-not-move by adjudicate R5)
            if p['cell'] in gone and 'demotedFrom' not in p:
                p['demotedFrom'] = {'gating': p['gating'], 'floor': p['floor'], 'direction': p['direction'], 'unit': r['unit'], 'run': r['run'], 'rule': r['rule'], 'withdrawn': True}
                p.update(gating=False, floor=None)
        # fix r1 (plan-skeptic M3): a gating geometry key that needs this unit can no longer pass once the unit is out
        # of the tree (its cell returns to the old picture), so it is WITHDRAWN — demoted out of `gating` into
        # `withdrawn` (geometry-gate.py prints it, never gates on it) — instead of naming a unit that is already gone.
        geo = []
        for probe in lane['geometryProbes']:
            script = os.path.basename(probe['cmd'].split()[1])
            for key in sorted(k for k in probe.get('gating', {}) if r['unit'] in key_units(probe, k)):
                probe.setdefault('withdrawn', {})[key] = (f"was gating -> {probe['gating'][key]}; unit {r['unit']} reverted at "
                                                          f"{r['run']} (rule {r['rule']})")
                del probe['gating'][key]
                geo.append(f'{script} {key}')
        # fix r2 (plan-skeptic R2-S1): a prediction that keeps SOME units but needs this one (`requires`) can no longer
        # reach its `to` — a gating floor would then be unmeetable (U2-ios reverted: 001 / 003 ios at U3 alone, replay
        # B 0.9304 f / 0.9566 P with hyphens for bullets) and rule 3 + rule 6 would cascade into U3b and U3. It is
        # DEMOTED: gating -> False, floor -> None, direction -> undirected (the remaining units' sign is not
        # pre-registered), the old values kept in `demotedFrom`. RESTATE (applied after) may re-register it.
        demoted = []
        for p in lane['predictions']:
            if p['cell'] in gone or p['kind'] == 'non-corpus' or r['unit'] not in p.get('requires', p['units']) or 'demotedFrom' in p:
                continue
            p['demotedFrom'] = {'gating': p['gating'], 'floor': p['floor'], 'direction': p['direction'], 'unit': r['unit'], 'run': r['run'], 'rule': r['rule']}
            p.update(gating=False, floor=None, direction='undirected',
                     directionWhy=f"demoted: required unit {r['unit']} reverted at {r['run']} (rule {r['rule']}); the remaining units' sign is not pre-registered")
            demoted.append(p['cell'])
        out.append(dict(r, withdrawnPredictions=gone, mustNotMoveAfter=gone, demotedPredictions=demoted, withdrawnGeometryKeys=geo,
                        carriersWithdrawn={'captures': {p: [s for s in unit['captures'][p] if s not in still[p]] for p in PLATS},
                                           'wire': [s for s in unit['wire'] if s not in still_wire]}))
    for name, lane in lanes.items():
        for p in lane['predictions']:
            if p['cell'] in RESTATE:
                p.update({k: v for k, v in RESTATE[p['cell']].items() if k in ('to', 'floor', 'confidence', 'gating', 'kind', 'direction', 'directionWhy')})
                p['restated'] = RESTATE[p['cell']].get('why', 'PLAN §10')
    # fix r2 (R2-S1), asserted: no gating prediction needs a reverted unit unless RESTATE re-registered it explicitly
    # (with its own `gating` and `why`); a withdrawn row (all units reverted) is never gating either.
    for name, lane in lanes.items():
        for p in lane['predictions']:
            need = [u for u in p.get('requires', p['units']) if (name, u) in rev]
            if p['gating'] and need:
                assert p['cell'] in RESTATE and 'gating' in RESTATE[p['cell']] and 'why' in RESTATE[p['cell']], \
                    f'{name} {p["cell"]}: gating, but its required unit(s) {need} are reverted — demote it or RESTATE it with a why'
                assert p.get('floor') is not None, f'{p["cell"]}: RESTATE re-gates it without a floor'
    return {'reverted': out} if out else None

# fix r2 (plan-skeptic R2-M2): the read-out of a device probe, base FIRST everywhere. ab-diff.mjs is
# <excludeRun> <includeRun> and prints Δ = include − exclude, so `ab-diff.mjs wave54-open <probe>` prints Δ = probe −
# open (the planned `ab-diff.mjs <probe> wave54-open` labelled every probe gain "flip P→f": wave 53's margin-root-002
# web, fix-r2/pre-M2.abdiff-plan-order.wave53-probe.out.txt). Rule 3 (floors) gets an executable reader:
# probe-readout.mjs over a --movers 0 score record (it refuses the wrong order and a thresholded record).
def PROBE_READOUT(run, sections, stage1=False):
    secs = ','.join(sections)
    tail = ' --stage1' if stage1 else ''
    return {
      'order': f'base first everywhere: wave54-open is the PREVIOUS / EXCLUDE run, {run} the CURRENT / INCLUDE run, so every Δ printed is {run} − wave54-open (a gain is +)',
      'abDiff': f'node tools/titan/results/wave52-gate/ab-diff.mjs wave54-open {run} --threshold 0.002   (per-cell flips and |Δ| >= 0.002, sections absent from {run} print "not compared")',
      'score': (f'node tools/titan/score-gate.mjs wave54-open {run} --watch tools/titan/results/wave54-plan/watchlist.txt --movers 0 '
                f'--json tools/titan/results/wave54-gate/score-{run.split("-")[1]}.json > tools/titan/results/wave54-gate/score-{run.split("-")[1]}.movers0.txt'),
      'floors': (f'node tools/titan/results/wave54-plan/probe-readout.mjs tools/titan/results/wave54-gate/score-{run.split("-")[1]}.json{tail}'
                 '   (revert rules 1, 2, 3 over every carrier, prediction row and non-carrier cell of the probed sections; exit 0 none fired, 1 fired (units named, each with its revertOrder), 2 wrong order / not --movers 0, 3 a probed section missing)'),
      'controls': f'node tools/titan/results/wave54-gate/control-check.mjs wave54-open {run} --sections {secs}   (R4 / R4b restricted to the probed sections: rule 5)',
      'geometry': (f'python3 tools/titan/results/wave54-plan/geometry-gate.py {run} --base wave54-open' + (' --lanes L1,L2' if stage1 else '')
                   + '   (rule 4; exit 0 = every gating key of a unit on the tree PASS; 1 a gating FAIL; 3 a gating key UNMEASURED)'),
      'executedAtPlanning': ('fix-r2/post-M2.*: ab-diff wave53-open wave53-probe prints background-attachment-margin-root-002 web "flip f→P f 0.339 → P 1 Δ+0.661"; '
                             'probe-readout --stage1 on a stage-1-shaped record (wave53-probe\'s CSS2, css-counter-styles, css-tables, css-text) passes the L1 rows and names only L2 TB-android (006 android 0.9906 < 0.996, Δ-0.0038), the unit wave 53 itself reverted; on the full wave-53 pair it prints margin-root-002 ×3 as f→P; '
                             'the reversed record exits 2 (ORDER); a --movers 0.005 record exits 2; the identity record fires rule 3 on 31 rows (34 minus the 3 vacuous autopos floors), 6 with --stage1'),
    }

expectations = {
  'wave': 54,
  'readAgainst': RUN,
  # fix r1 (plan-skeptic M1): --movers 0 puts EVERY same-verdict cell in the JSON's `movers` list, so adjudicate.mjs R4
  # reads each gating cell's measured score (a cell absent from every list read as "did not move" and failed four P->P
  # rows predicted to move < 0.005) and R5 sees a must-not-move move in (0.002, 0.005). R5 keeps its own 0.002 rule.
  'scoreOfRecord': ('node tools/titan/score-gate.mjs wave54-open wave54-final --watch tools/titan/results/wave54-plan/watchlist.txt'
                    ' --movers 0 --json tools/titan/results/wave54-gate/score-final.json > tools/titan/results/wave54-gate/score-final.movers0.txt'),
  # fix r3 (plan-skeptic R3-S1 / R3-S2): adjudicate.mjs refuses (exit 2) a record that is not base-first or not --movers 0,
  # so the 0.005 JSON written beside it cannot be adjudicated by mistake; --geometry feeds R6's degenerateRetirement read.
  'adjudicate': ('node tools/titan/results/wave54-gate/adjudicate.mjs tools/titan/results/wave54-gate/score-final.json'
                 ' --geometry tools/titan/results/wave54-gate/geometry-final.json'),
  'closingGateFiles': {
    'tools/titan/results/wave54-gate/score-final.json': ('scoreOfRecord (--movers 0). Read by adjudicate.mjs (R1-R5, R6 read-out) and by nothing else; '
                                                         'adjudicate.mjs refuses every other record: exit 2 ORDER (prev is not wave54-open) or NOT A --movers 0 RECORD (a cell scored on both sides is missing from every list)'),
    'tools/titan/results/wave54-gate/score-final.movers0005.json': ('scoreReadout (--movers 0.005). Read by make-corpus.mjs (corpusSnapshotSource) and the PR mover list ONLY; '
                                                                    'never adjudicated (adjudicate.mjs exits 2 on it: fix r3, R3-S1)'),
    'tools/titan/results/wave54-gate/geometry-final.json': ('geometryGate.closingCmd. R6 is that command\'s EXIT; adjudicate.mjs --geometry reads its rows only to retire a '
                                                            'degenerateByConstruction gain (degenerateRetirementCheck), and refuses it (exit 2 GEOMETRY PAIR) unless run/base equal the score record\'s cur/prev'),
  },
  'scoreReadout': ('node tools/titan/score-gate.mjs wave54-open wave54-final --watch tools/titan/results/wave54-plan/watchlist.txt'
                   ' --movers 0.005 --json tools/titan/results/wave54-gate/score-final.movers0005.json > tools/titan/results/wave54-gate/score-final.txt'
                   '   (the human-readable mover list for the PR; adjudicates nothing)'),
  # fix r2 (plan-skeptic R2-S3): make-corpus.mjs publishes `movers: record.movers.length` under a hardcoded
  # "--movers 0.005" scorer string, and the --movers 0 record lists EVERY same-verdict cell (4096 movers on the
  # identity pair) — so the snapshot is built from the 0.005 JSON, never from score-final.json.
  'corpusSnapshotSource': ('tools/titan/results/wave54-gate/score-final.movers0005.json (the <record.json> of tools/titan/results/wave52-gate/make-corpus.mjs; '
                           'its perCellDiff.movers then counts |Δ| >= 0.005 exactly as its scorer string says — score-final.json, the --movers 0 record of R4/R5, would publish ≈4000)'),
  # Re-run at fix r3 (plan-skeptic R3-S1 / R3-S2) with the guarded adjudicate.mjs: a record must be base-first (--base,
  # default wave54-open) and --movers 0, else exit 2; the fix-r1 inputs are wave53-final-based, so they take --base wave53-final.
  'adjudicateCalibrations': [
    {'input': 'skeptic-r1/synth-score.json (every gating row at its predicted value, built with the old --movers 0.005 rule)',
     'expect': 'exit 2 ORDER (its prev is "wave54-open(=wave53-final)"); with --base "wave54-open(=wave53-final)" exit 2 NOT A --movers 0 RECORD (30 listed vs 0). Was FAIL R4 missed 4 (fix r1): the thresholded input is now refused instead of mis-read',
     'out': 'fix-r3/post-S1.skeptic-r1-synth.out.txt, fix-r3/post-S1.skeptic-r1-synth-based.out.txt'},
    {'input': 'fix-r1/synth-movers0-allmet.json --base wave53-final (score-gate diffRuns, moverThreshold 0, over the 4096 wave53-final cells with every gating row at its predicted value)',
     'expect': 'R1-R5 hold, exit 0 (R6 read-out: 001 / 003 ios DEGENERATE, "not read: no --geometry JSON")', 'out': 'fix-r3/post-cal.fix-r1-allmet.out.txt'},
    {'input': 'fix-r1/synth-movers0-floor.json --base wave53-final (as all-met, counter-suffix ios exactly at its floor 0.985, delta +0.0048)',
     'expect': 'R1-R5 hold, exit 0', 'out': 'fix-r3/post-cal.fix-r1-floor.out.txt'},
    {'input': 'fix-r1/synth-movers0-mnm3.json --base wave53-final (as all-met, one must-not-move cell +0.003)',
     'expect': 'FAIL R5 moved 1, exit 1 (the (0.002, 0.005) hole is closed)', 'out': 'fix-r3/post-cal.fix-r1-mnm3.out.txt'},
    {'input': 'fix-r1/synth-movers0-mnm3-old.json --base wave53-final (the same picture written with --movers 0.005)',
     'expect': 'exit 2 NOT A --movers 0 RECORD (30 listed vs 4096): fix r1 printed R5 moved 0 on it', 'out': 'fix-r3/post-cal.fix-r1-mnm3-old.out.txt'},
    {'input': 'fix-r1/score-identity-movers0.json (score-gate.mjs wave53-final wave54-open --movers 0: nothing moved)',
     'expect': 'exit 2 ORDER with the default --base wave54-open; with --base wave53-final FAIL R4 missed 31 = 34 gating rows minus the 3 autopos-ltr rows whose floor 0.995 sits below today\'s 0.9966 (their gate is geometry alone: PLAN §6); the same record with prev / cur swapped exits 2 ORDER',
     'out': 'fix-r3/post-S1.identity-unbased.out.txt, fix-r3/post-S1.identity-base53.out.txt, fix-r3/post-S1.reversed-identity.out.txt'},
    {'input': 'fix-r3/synth/{installed,abc}-{allmet,mnm3}.{m0,m0005}.json (fix-r3/synth-closing-r3.mjs: the identity record relabelled wave54-open -> wave54-final, every gating row of the installed / the Mprime + TB-android + CBB-android-reverted (fix-r3/hooks/abc) expectations at its prediction; mnm3 moves css-cascade/all-prop-001.html ios by -0.003; m0005 = the same picture as score-gate --movers 0.005 writes it)',
     'expect': 'm0: allmet R1-R5 hold exit 0, mnm3 FAIL R5 moved 1 exit 1 (both expectations; abc with --exp fix-r3/hooks/abc/expectations.json). m0005: exit 2 NOT A --movers 0 RECORD ×4 — the abc mnm3 picture used to print R1-R5 hold, exit 0 (the move unseen: plan-skeptic R3-S1)',
     'out': 'fix-r3/post-S1.{installed,abc}-{allmet,mnm3}.{m0,m0005}.out.txt'},
    {'input': 'fix-r3/synth/installed-allmet.m0.json with --geometry fix-r3/geometry-{allmet,u3only,otherpair}.json and without (fix-r3/synth-geometry.py: geometry-gate JSONs whose hyphenate-character.geometry.py rows are the probe\'s own lines on replay GB = U1+U2+U3, or B = U3 alone)',
     'expect': 'allmet geometry: RETIRED 2 (001 / 003 ios: gains), no DEGENERATE line; no --geometry: DEGENERATE 2 "not read"; U3-only geometry: DEGENERATE 2 (geometry FAIL: glyph); a geometry JSON of another pair: exit 2 GEOMETRY PAIR. R1-R5 hold, exit 0, in the first three (plan-skeptic R3-S2)',
     'out': 'fix-r3/post-S2.allmet-{geometry,nogeometry,geometry-u3only,geometry-otherpair}.out.txt'},
    {'input': 'fix-r3/synth/u2ios-closing.m0.json --exp fix-r3/hooks/u2ios/expectations.json (U2-ios reverted at wave54-probe; 003 ios at the replay-B DEGENERATE pass 0.9566) with --geometry fix-r3/geometry-u2ios.json, and with the all-OK geometry',
     'expect': 'R4 gating predictions 32, missed 0; R1-R5 hold; DEGENERATE 1: 003 ios "unit U2-ios reverted" in both (units are read before the picture)',
     'out': 'fix-r3/post-S2.u2ios-closing-geometry.out.txt, fix-r3/post-S2.u2ios-closing-geometry-allmet.out.txt'},
  ],
  'openingGate': {
    'cmd': 'node tools/titan/score-gate.mjs wave53-final wave54-open --watch tools/titan/results/wave53-plan/watchlist.txt',
    'expect': '0 gained / 0 lost / 0 movers / 0 newly measured / 0 unmeasured-now over 4096 cells; fixture net exit 0 on all 9 gate fixtures, the all-then-color child reading "(gate-only: no committed baseline)" exactly as at wave53-open (the obligation-0 clause "all-then-color now has committed baselines" is stale: label-chrome brief §9 R6)',
    'alsoRun': ['RUN=wave54-open WATCH=tools/titan/results/wave54-plan/watchlist.txt node tools/titan/results/wave52-plan/watchlist-check.mjs  -> unmatched 0',
                'per-test IR wave53-final -> wave54-open byte-identical (the lanes\' verbatim pins come from wave53-final)',
                'node tools/titan/results/wave54-gate/control-check.mjs wave53-final wave54-open  -> 0 changed, 0 renumbered, 0 leaks (same host)'],
    'onDivergence': 'the first divergence is wave 54\'s first item: PLAN §10 restates every affected prediction against wave54-open BEFORE any lane lands',
  },
  'stage1Probe': {'runId': 'wave54-pre', 'sections': STAGE1,
                  'cmd': 'tools/titan/gate-driver.sh wave54-pre --sections ' + ','.join(STAGE1) + ' --skip-fixture-net',
                  'decides': ['L1-rtl-marker-bake P / Mprime', 'L2-table-body-cell TB-android'],
                  'readWith': 'the UNION carrier set (BACKLOG wave-53 lesson: a probe of a shared tree measures the union); per-lane geometry decides L1 and L2 only; every other lane\'s cells in these sections are recorded and decided at wave54-probe',
                  'readOut': PROBE_READOUT('wave54-pre', STAGE1, stage1=True)},
  'probeRun': {'runId': 'wave54-probe', 'sections': PROBE,
               'cmd': 'tools/titan/gate-driver.sh wave54-probe --sections ' + ','.join(PROBE) + ' --skip-fixture-net',
               'readOut': PROBE_READOUT('wave54-probe', PROBE),
               'notProbed': {s: {'css-color': 'no carrier, no named control; the closing gate R4/R7',
                                 'css-grid': 'no carrier, no named control (the wave-53 float controls are closed); the closing gate R4/R7',
                                 'css-sizing': 'no carrier; holds only the unstaffed web singleton aspect-ratio/abspos-016; the closing gate R4/R7',
                                 'css-view-transitions': 'two L4 fixed controls (content-with-child-with-transparent-background, content-with-transparent-background, P x3) — replaced by the L4 skeptic\'s P3 pure-walk census over all 26 fixedControls payloads (JVM + Catalyst) and the closing gate R4/R7; the section runs 8-30 min under swap'}[s]
                             for s in NOT_PROBED}},
  'geometryGate': {'cmd': 'python3 tools/titan/results/wave54-plan/geometry-gate.py <run> --base wave54-open',
                   'closingCmd': ('python3 tools/titan/results/wave54-plan/geometry-gate.py wave54-final --base wave54-open'
                                  ' --json tools/titan/results/wave54-gate/geometry-final.json > tools/titan/results/wave54-gate/geometry-final.out.txt'),
                   'what': 'runs every lane\'s geometryProbes, prints one PASS/FAIL per expect key, names the unit each failing gating row reverts (rule 4), exits 1 on a gating FAIL or a probe self-check failure, 2 on a STALE json, 3 when no gating key FAILs but one is UNMEASURED (fix r2, R2-S2: never a silent pass); R6 = exit 0. A CONTROL FAIL never changes the exit (it is diagnosed through R4 / R7) — but 9 control keys sit on CARRIERS, where R4 and R7 cannot see them (U3\'s 8 block-ellipsis-002/-004/-005/-006 ios/android freeze keys; RS\'s ua-heading-face block-in-inline-015-print web): at the closing gate they are gated by NOTHING automatic, so the cell review reads every control FAIL and names it in the PR (fix r3, plan-skeptic R3-N4; at the probes revert rules 1 / 2 bind their scores)'},
  'revertRule': [
    '1 lost: a carrier cell (any lane, any tier, predicted or not) that is P on wave54-open and f on the probe reverts the commit that carries it (R1\'s empty lost list applied at the probe)',
    '2 moved down: delta <= -0.002 wave54-open -> probe on any prediction row whose direction is "up-or-stay" (any tier) reverts the commit that carries it. A row with direction "undirected" (a move whose sign is not pre-registered; lanes.*.predictions[].directionWhy) is EXEMPT from rule 2: its fall is looked at against the ref and named with its cause in the probe read-out and the PR, never a revert by itself; rules 1 and 5 still bind it. Rule 2 is read AT THE PROBES ONLY (probe-readout.mjs): at the closing gate adjudicate.mjs prints every prediction row\'s measured value but enforces only the gating floors (its R4) and must-not-move (its R5), so a directed non-gating row that falls between wave54-probe and wave54-final (on a byte-deterministic host it should not move at all) is named in the cell review and the PR, not a ship-stopper by itself (fix r3, plan-skeptic R3-N5)',
    '3 floor: a gating prediction (every HIGH / MED-HIGH row) below its floor reverts the commit that carries it. Read by probe-readout.mjs over the --movers 0 probe record (stage1Probe.readOut / probeRun.readOut). A row demoted by a probe decision (probeDecisions.reverted[].demotedPredictions: a required unit is gone) has no floor',
    '4 geometry: a geometryProbes gating key whose line does not end in its exact expect string (or lacks its contains string) reverts the unit geometry-gate.py names; an UNMEASURED gating key is not a pass (geometry-gate.py exit 3: re-run the section)',
    '5 leak: a composed capture outside every carrier set that differs in decoded pixels (R4), or a per-test IR document outside the wire carriers that differs in bytes and is not "renumbered" (R4b), reverts the commit it is bisected to (one re-probe of the leaked section per step; never guessed)',
    '6 unit: the commit (lanes.*.revertUnits); a cell carried by several commits of one lane reverts them in that unit\'s revertOrder (latest first), re-probing its sections between steps. Lane dependencies: M′ before P (M′ never without P); U3b before U3; U2-seed before U1. L3 reasons: a `glyph:` failure names U2-<platform> (U1 on web), an `offset:`/`lines:` failure names U3b, then U3',
    '6x shared cells (L4 sharedCells): carriers are disjoint by construction, so a cell always names ONE lane\'s commits; L4\'s invariance claims on L3 / L1 carriers are pinned in-lane (P1/P3) and attributed at the closing A/B',
    '7 stage 1: L1 and L2 are decided at wave54-pre (lanes.L1-rtl-marker-bake.stage1Decision, lanes.L2-table-body-cell.stage1Decision); a unit reverted there is withdrawn (probeDecisions) before wave54-probe runs',
    '8 U3b is probe-decided (lanes.L3-hyphenate-character.revertUnits.U3b.probeDecided)',
    'tier: MED and below carry no floor and no geometry trigger; a shortfall against their predicted magnitude is read and labelled, never a trigger. Rules 1 and 5 apply to every row of every tier; rule 2 to every "up-or-stay" row of every tier',
  ],
  'beforeEveryDeviceRun': ('source tools/titan/own-processes.sh && kill_own_gradle_daemons "$PROJECT_ROOT" <every tree on a lane note\'s TREES: line '
                           '(the shared tree is PROJECT_ROOT; list any export tree a lane or skeptic ran Gradle in)>  — never ./gradlew --stop'),
  'controlCalibrations': [
    {'pair': 'wave53-open -> wave53-final', 'expectations': 'tools/titan/results/wave53-plan/expectations.json',
     'expect': 'the copied control-check.mjs reproduces the wave-53 closing control verdict byte-for-byte (the copy changes only the expectations path)'},
    {'pair': 'wave53-final -> wave54-open', 'expectations': 'tools/titan/results/wave54-plan/expectations.json',
     'expect': 'HOLDS: 0 changed captures, 0 content-changed wire documents, 0 renumbered (the opening gate is the identity on this host)'},
    {'pair': 'wave53-open -> wave53-final', 'expectations': 'tools/titan/results/wave54-plan/expectations.json',
     'expect': 'FAILS with leaks (wave 53\'s landed changes — counter-reset-reversed-nested x3, display-contents-root-background x3, the float-avoid cells, … — are not wave-54 carriers): the wave-54 carrier set can fail'},
    {'pair': 'wave53-final -> wave54-open, then wave53-open -> wave53-final', 'expectations': 'tools/titan/results/wave54-plan/expectations.json', 'lane': 'L7-label-chrome',
     'expect': '--lane L7-label-chrome (an EMPTY carrier set) HOLDS on the identity pair and FAILS on the wave-53 pair: the per-lane path is exercised both ways'},
  ],
  'wireRenumbering': {
    'rule': ('Unchanged from wave 53 (BACKLOG "Wave 53 lessons"): a per-test IR document byte-different ONLY because an earlier content-changed '
             'document of the same section changed its component count — identical once the id counter is stripped from id / slot.parent, every id '
             'shifted by the same amount, equal to the running component-count delta of the content-changed documents before it — is RENUMBERED, not a leak.'),
    'expectedShadow': {'css-counter-styles': {'after': stem(CS), 'shift': 6, 'documents': 15, 'unit': 'L1 Mprime (6 root-owned marker runs: counter-suffix 23 -> 29 components)',
                                              'basis': 'rtl brief §4 predicted wire; the same +6 x 15 shadow the reverted wave-53 U2 produced (wave53-gate/_note.md item 6)'}},
    'noShadowFrom': 'L1 P (no component added: 13->13, 19->19, 14->14 at wave53-probe), L3 U1 (Generic -> HyphenateCharacter, same component), L3 U3 / U3b (br Height values only)',
  },
  'expected': {'lost': [], 'unmeasuredNow': [], 'newlyMeasured': [], 'missingSections': 0, 'columnShorts': 0,
               'fixtureNet': 'exit 0 on all 9 gate fixtures; all-then-color against its newly committed baselines (L7 U2-seed)'},
  'abArms': [
    {'arm': 'drop-W1', 'lane': 'L6-web-tail', 'sections': ['css-text'], 'platforms': ['web'],
     'expect': 'hyphens-out-of-flow-002 web returns to f 0.9411 (the flip is W1\'s alone)'},
    {'arm': 'drop-Mprime', 'lane': 'L1-rtl-marker-bake', 'sections': ['css-counter-styles'], 'platforms': ['web', 'ios', 'android'],
     'when': 'only if M′ is on the closing tree', 'expect': 'counter-suffix android returns to ≈0.9815 (P alone), web to 0.9818, ios to 0.9802'},
    {'arm': 'drop-U3b', 'lane': 'L3-hyphenate-character', 'sections': ['css-text'], 'platforms': ['web', 'ios', 'android'],
     'when': 'only if U3b is on the closing tree', 'expect': 'hyphenate-character-001..004 return to the U3-only values (web/android ≈0.95-0.96; iOS unchanged or up)'},
  ],
  # fix r3 (plan-skeptic R3-N6): the A/B read-out order, pre-registered after R2-M2. ab-diff.mjs is <excludeRun> <includeRun>
  # and prints Δ = include − exclude, so the ARM (the unit dropped) goes FIRST and the closing gate second: Δ = final − arm is
  # the mechanism's share, a gain the unit made prints "+" / "flip f→P".
  'abRead': {'runId': 'wave54-ab-<arm>   (e.g. wave54-ab-drop-W1)',
             'cmd': 'node tools/titan/results/wave52-gate/ab-diff.mjs wave54-ab-<arm> wave54-final --threshold 0.002 --platforms <arm.platforms>',
             'order': 'arm (exclude) first, wave54-final (include) second: Δ = wave54-final − arm = what the dropped unit does on device'},
  'unionCaptureCarriers': union,
  'unionWireCarriers': sorted(wire_owner),
  # Kept conservative (honest by default): adjudicate.mjs prints a gain on these as "DEGENERATE-BY-CONSTRUCTION, not a fix"
  # unless degenerateRetirementCheck holds for that cell on the --geometry JSON of the same run pair (fix r3, R3-S2); the
  # cell review still LOOKS at every retired cell, recorded per cell in the gate note.
  'degenerateByConstruction': [HC[1], HC[3], HC[4]],
  'degenerateRetirement': ('a hyphenate-character-001/003/004 cell on platform p is faithful only when U1, U2-p (none needed on web) and U3 are all on the '
                           'closing tree AND hyphenate-character.geometry.py prints "→ GEOMETRY OK" on its row; anything else stays DEGENERATE'),
  # fix r3 (plan-skeptic R3-S2): the same rule in the form adjudicate.mjs reads (its R6 line printed the plan's own iOS
  # gains "not fixes" on a fully correct tree). A degenerate gain is RETIRED (a gain) iff no unit of units[<platform>] is in
  # probeDecisions.reverted for `lane` AND the --geometry JSON's `script` row "<keys[test]> <platform>" is PASS and ends in
  # `lineEndsWith`; with no --geometry JSON nothing is retired.
  'degenerateRetirementCheck': {'lane': 'L3-hyphenate-character', 'script': 'hyphenate-character.geometry.py', 'lineEndsWith': '→ GEOMETRY OK',
                                'units': {'web': ['U1', 'U3'], 'ios': ['U1', 'U2-ios', 'U3'], 'android': ['U1', 'U2-android', 'U3']},
                                'keys': {HC[n]: f'hyphenate-character-00{n}' for n in (1, 3, 4)}},
  'stayDegenerateEvenIfPass': [f'{BL2} android (orange "!" on the left: bake measurement)', f'{CS} ios (rows 3-6)', f'{CS} android (rows 5-6)',
                               f'{BE[2]} ios (Line 4 painted and no "…": the iOS clamp is not applied; U3 only removes the stray blank lines)'],
  'recordedWall': ['css-anchor-position/anchor-position-multicol-007.html android'],
  'ringFenced': 'filter-effects/backdrop-filter-basic-blur — report only, never a target, never carved out (not a carrier of any lane; L4 OOF claims its leaf boxes inertly, L3 U3 does not reach it)',
  'lanes': lanes,
}
# fix r1 (plan-skeptic S3): the PLAN §1 tally "passing cells made picture-correct with no change of verdict", one
# entry per cell with the geometry key that alone may certify it at the closing gate (BACKLOG "Wave 53 lessons": a
# native is picture-correct only from a device capture plus the family's probe). block-ellipsis-002 ios is OUT (looked
# at: it stays DEGENERATE). Asserted below: every key exists in its lane's probes; every cell is a P->P prediction row.
def tk(cell_, script, key): return {'cell': cell_, 'key': f'{script} {key}'}
OOFG, CBG, RSG = 'oof-containing-block.geometry.py', 'compose-wpt-content-box-cb.geometry.py', 'web-root-separator.geometry.py'
TALLY = {
  'full': [tk(f'{CS} web', 'rtl-marker-bake.geometry.py', '[M] counter-suffix web'),
           tk(f'{S006} android', 'compose-table-body-cell.geometry.py', '006 android')]
          + [tk(f'{BE[n]} web', 'block-ellipsis-br.geometry.py', f'block-ellipsis-00{n} web') for n in (2, 4, 5, 6)]
          + [tk(f'{t} {p}', OOFG, f'{k} {p}') for t, k in ((PR4, 'position-relative-004'), (CHG, 'change-insets-inside-strict-containment-nested'),
                                                          (SFA, 'abspos/static-fixed-inside-abspos'), (PR3, 'position-relative-003')) for p in ('ios', 'android')]
          + [tk(f'{APOS[k]} android', CBG, f'abspos/abspos-autopos-{k} android') for k in APOS]
          + [tk(f'{NBR[k]} android', CBG, f'backdrop-filter-nested-border-radius-clip{k} android') for k in ('', '-2', '-4')]
          + [tk(f'{BS[n]} web', RSG, f'box-sizing-0{n} web') for n in ('10', '11', '13', '14', '15', '16', '17', '18', '19', '20', '21', '24', '25')]
          + [tk(f'{SRI} web', RSG, 'position-absolute-semi-replaced-stretch-input web')],
  'part': [tk(f'{CS} {p}', 'rtl-marker-bake.geometry.py', f'[M] counter-suffix {p}') for p in ('ios', 'android')],
}
assert len(TALLY['full']) == 37 and len(TALLY['part']) == 2, (len(TALLY['full']), len(TALLY['part']))
_keys = {f"{os.path.basename(pr['cmd'].split()[1])} {k}": ln for ln, l in lanes.items() for pr in l['geometryProbes'] for k in pr['expect']}
_pcells = {p['cell']: p for l in lanes.values() for p in l['predictions']}
for e in TALLY['full'] + TALLY['part']:
    assert e['key'] in _keys, f'tally key missing from every geometryProbes: {e["key"]}'
    assert e['cell'] in _pcells and _pcells[e['cell']]['from'].startswith('P'), f'tally cell is not a P->P prediction row: {e["cell"]}'
    assert e['cell'].rsplit(' ', 1)[1] == e['key'].rsplit(' ', 1)[1], f'tally key platform differs: {e}'
expectations['pictureCorrectTally'] = dict(TALLY, rule='a cell is counted picture-correct at the closing gate ONLY when its key prints "→ GEOMETRY OK" on the closing run (geometry-gate.py); a pass whose key is not OK stays DEGENERATE and is not counted')

# fix r1 (plan-skeptic S5): every non-gating key is either a CONTROL (expected to PASS today and to keep passing) or a
# REPORT key (a MED/LOW target expected WRONG today); report keys are listed per probe, controls are the rest.
for ln, l in lanes.items():
    for pr in l['geometryProbes']:
        rep_ = set(pr.get('report', []))
        assert rep_ <= set(pr['expect']), f'{ln}: report key not in expect: {rep_ - set(pr["expect"])}'
        assert not rep_ & set(pr.get('gating', {})), f'{ln}: a key is both gating and report'
        assert set(pr.get('gating', {})) <= set(pr['expect']), f'{ln}: gating key not in expect'
        for k in pr.get('requires', {}):
            assert k in pr['gating'], f'{ln}: requires names a non-gating key {k}'
            assert all(u in l['revertUnits'] for u in pr['requires'][k]), f'{ln}: requires names an unknown unit'
        for k, v in pr.get('gating', {}).items():
            assert all(u in l['revertUnits'] for u in v.split('|')), f'{ln}: gating key {k} names an unknown unit {v}'

# fix r3 (plan-skeptic R3-S2): the machine form of degenerateRetirement names exactly the degenerate cells, known units
# of its lane, and a geometry key of that lane's probe for every (cell, platform) — so adjudicate.mjs can always read it.
_rc = expectations['degenerateRetirementCheck']
assert set(_rc['keys']) == set(expectations['degenerateByConstruction']), 'degenerateRetirementCheck.keys != degenerateByConstruction'
assert all(u in lanes[_rc['lane']]['revertUnits'] for us in _rc['units'].values() for u in us), 'degenerateRetirementCheck names an unknown unit'
assert set(_rc['units']) == set(PLATS), 'degenerateRetirementCheck.units must name every platform'
_rcp = [pr for pr in lanes[_rc['lane']]['geometryProbes'] if os.path.basename(pr['cmd'].split()[1]) == _rc['script']]
assert len(_rcp) == 1 and all(f'{k} {p}' in _rcp[0]['expect'] for k in _rc['keys'].values() for p in PLATS), 'degenerateRetirementCheck key missing from its probe'
assert all(_rcp[0]['expect'][f'{k} {p}'] == _rc['lineEndsWith'].lstrip('→ ') for k in _rc['keys'].values() for p in PLATS), 'retirement verdict string differs from the probe expect'

_pd = probe_decisions()                     # None at planning: no unit has been reverted yet
if _pd:
    expectations['probeDecisions'] = _pd
json.dump(expectations, open(os.path.join(OUT, 'expectations.json'), 'w'), indent=1, ensure_ascii=False)

# ── watchlist.txt ─────────────────────────────────────────────────────────────────────────────────────────────────────
out, written = [], set()
def block(title, lines):
    out.append(f'# {title}')
    for l in lines:
        if l not in written:
            written.add(l); out.append(l)

out += [
  '# tools/titan/results/wave54-plan/watchlist.txt — the wave-54 gate watchlist (every target, predicted mover and',
  '# must-not-move cell of PLAN.md §2; generated by plan-build.py from the family censuses + the briefs\' named cells,',
  '# every line verified to be a scored cell of wave53-final). Consumed by:',
  '#   node tools/titan/score-gate.mjs wave54-open <run> --watch tools/titan/results/wave54-plan/watchlist.txt',
  '# One watch per line: "<sec>/<path>.html <platform>" (or a bare "<sec>/<path>.html" = all three platforms). The ".html"',
  '# suffix makes each line match exactly ONE test under score-gate.mjs watchCells\' substring rule (asserted by',
  '# plan-build.py). Unlike wave 53 there is NO family line: css-text/hyphens web holds carriers of two lanes (L3, L6).',
  '# A line already written under an earlier lane is not repeated. Precondition before any gate read-out:',
  '#   RUN=wave54-open WATCH=tools/titan/results/wave54-plan/watchlist.txt node tools/titan/results/wave52-plan/watchlist-check.mjs',
  '# must print "unmatched 0".',
]
pred_cells = lambda lane: [p['cell'] for p in lane['predictions'] if p['kind'] != 'non-corpus']
for name, lane in lanes.items():
    short = name.split('-', 1)[1]
    if pred_cells(lane):
        block(f'{name.split("-")[0]} {short} — targets + movers ({", ".join(lane["revertUnits"])})', pred_cells(lane))
    if lane['mustNotMove']:
        block(f'{name.split("-")[0]} {short} — must not move', lane['mustNotMove'])
block('RING-FENCED, report only (a generic mechanism may move it; never a target, never carved out)', ['filter-effects/backdrop-filter-basic-blur.html'])
block('Not staffed this wave — read at the gate so the queue is re-trued from measurements (PLAN §5)', [
  'css-anchor-position/anchor-position-multicol-007.html android',
  'css-writing-modes/abs-pos-border-offset-001.html ios', 'css-writing-modes/abs-pos-border-offset-001.html android',
  'css-writing-modes/abs-pos-border-offset-002.html ios', 'css-writing-modes/abs-pos-border-offset-002.html android',
  'css-grid/abspos/grid-abspos-staticpos-align-self-safe-001.html ios', 'css-grid/abspos/grid-abspos-staticpos-align-self-safe-001.html android',
  'css-position/position-relative-002.html ios',
  'css-flexbox/align-self-003.html ios', 'css-flexbox/align-self-003.html android',
  'css-flexbox/align-self-009.html ios', 'css-flexbox/align-self-009.html android',
  'css-text/hanging-punctuation/hanging-punctuation-inline-001.html ios',
  'css-overflow/line-clamp/block-ellipsis-032.tentative.html android',
  'css-pseudo/first-letter-005.html',
  'css-view-transitions/column-span-during-transition-doesnt-skip.html',
  'css-color/border-color-currentcolor.html web', 'css-sizing/aspect-ratio/abspos-016.html web',
  'css-overflow/line-clamp/discard/discard-multicol-003.html web',
])

# Every watch line must be a scored cell (or a bare test with ≥1 scored cell) and match exactly ONE test under the
# scorer's substring rule: pattern ⊂ "<sec>/css/<sec>/<path>.html" (score-gate.mjs watchCells).
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
assert all(isinstance(p['floor'], float) for p in gating), 'every gating prediction needs a numeric floor'
assert all(p['gating'] for l in lanes.values() for p in l['predictions']
           if p['confidence'].startswith(('HIGH', 'MED-HIGH')) and p['kind'] != 'non-corpus' and 'demotedFrom' not in p), 'a HIGH/MED-HIGH prediction is not gating'
print(f"watchlist.txt: {sum(1 for l in out if l and not l.startswith('#'))} watch lines")
print('expectations.json: union capture carriers', {p: len(v) for p, v in union.items()}, '· wire carriers', len(wire_owner))
print(f'stage-1 probe ({len(STAGE1)}):', ','.join(STAGE1))
print(f'probe sections ({len(PROBE)}):', ','.join(PROBE), '· not probed:', ','.join(NOT_PROBED))
print(f'gating predictions with numeric floors: {len(gating)}')
units = sum(len(l['revertUnits']) for l in lanes.values())
print(f'revert units (commits): {units}')
flips = {p: {'HIGH/MED-HIGH': 0, 'MED/MED-LOW': 0, 'LOW': 0} for p in PLATS}
for l in lanes.values():
    for p in l['predictions']:
        if p['kind'] == 'non-corpus' or not p['from'].startswith('f') or 'P' not in p['to'].split('(')[0] + p['kind']:
            continue
        if 'stays f' in p['to'] or 'mover' == p['kind'] or p['kind'].startswith('mover'):
            continue
        plat = p['cell'].rsplit(' ', 1)[1]
        c = p['confidence']
        tier = 'HIGH/MED-HIGH' if c.startswith(('HIGH', 'MED-HIGH')) else ('MED/MED-LOW' if c.startswith('MED') else 'LOW')
        flips[plat][tier] += 1
print('predicted f->P flips by tier:', json.dumps(flips))
for name, lane in lanes.items():
    print(f"  {name}: carriers {[len(lane['captureCarriers'][p]) for p in PLATS]} wire {len(lane['wireCarriers'])} "
          f"predictions {len(lane['predictions'])} mustNotMove {len(lane['mustNotMove'])} units {list(lane['revertUnits'])}")
und = [p['cell'] for l in lanes.values() for p in l['predictions'] if p.get('direction') == 'undirected']
print(f'undirected prediction rows (exempt from revert rule 2): {len(und)}: ' + '; '.join(und))
_gk = sum(len(pr.get('gating', {})) for l in lanes.values() for pr in l['geometryProbes'])
_rk = sum(len(pr.get('report', [])) for l in lanes.values() for pr in l['geometryProbes'])
_ak = sum(len(pr['expect']) for l in lanes.values() for pr in l['geometryProbes'])
_wk = sum(len(pr.get('withdrawn', {})) for l in lanes.values() for pr in l['geometryProbes'])
print(f'geometry keys: {_ak} = gating {_gk} + report {_rk} + control {_ak - _gk - _rk - _wk} + withdrawn {_wk}')
print(f"picture-correct tally: {len(TALLY['full'])} in full + {len(TALLY['part'])} in part, each with its geometry key")
if _pd:
    for d in _pd['reverted']:
        print(f"probe decision {d['lane']} {d['unit']}: withdrawn predictions {len(d['withdrawnPredictions'])}, demoted {d['demotedPredictions']}, "
              f"geometry keys {d['withdrawnGeometryKeys']}, captures {sum(len(v) for v in d['carriersWithdrawn']['captures'].values())}")
print(f'must-not-move exclusions (another lane\'s carrier on that platform): {len(excluded)}')
for lane_name, line, o in excluded:
    print(f'    {lane_name}: {line}  -> carrier of {o}')
