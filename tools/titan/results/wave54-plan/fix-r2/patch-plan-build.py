#!/usr/bin/env python3
# fix r2: the plan-build.py edits of the second fix pass (R2-M2 read-outs, R2-S1 demotion, R2-S3 movers-0.005 record,
# R2-S4 replay-backed / undirected rows, R2-N3 the 18 seed names). Reads ../plan-build.py, writes the patched text to
# argv[1] (the caller moves it into place atomically). Every replacement must match exactly once.
import sys, os
HERE = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(HERE, '..', 'plan-build.py')).read()
def rep(a, b):
    global src
    assert src.count(a) == 1, (src.count(a), a[:90]); src = src.replace(a, b)

# ── pred(): a `requires` list (R2-S1) ──
rep("""           'direction': extra.pop('direction', 'up-or-stay')}
    row.update(extra)""",
"""           'direction': extra.pop('direction', 'up-or-stay'),
           # fix r2 (plan-skeptic R2-S1): the units that must be ON THE TREE for `to` (and a gating row's floor) to be
           # reachable. Default: every unit of the row. A unit missing from `requires` is ADDITIVE (its revert leaves a
           # pre-registered remainder: L1's M′ on counter-suffix android, L3's U3b everywhere). probe_decisions() demotes
           # a row one of whose required units is reverted (gating -> False, direction -> undirected) unless RESTATE
           # re-registers it, and an assertion below refuses a gating row that still needs a reverted unit.
           'requires': list(extra.pop('requires', units))}
    row.update(extra)
    assert set(row['requires']) <= set(row['units']), f'{line}: requires names a unit the row does not carry'""")

# ── L1 counter-suffix android: P alone meets the 0.970 floor (replay 0.9815), so M′ is additive ──
rep("""         'MED-HIGH (P floor) / MED (M′ magnitude, geometry-gated)', 0.970, True, ['P', 'Mprime'], GEO_OK),""",
"""         'MED-HIGH (P floor) / MED (M′ magnitude, geometry-gated)', 0.970, True, ['P', 'Mprime'], GEO_OK, requires=['P']),""")

# ── L3: R2-S4 rows ──
rep("""    + [pred(f'{BF[n]} {p}', CELLS[f'{BF[n]} {p}'], 'up', 'mover (stray blank line before "No dark/black…" removed)', 'MED', None, False, ['U3'])
       for n in ('clip-rect', 'edge-clipping', 'paint-order') for p in ('web', 'ios')]
    + [pred(f'{BF[n]} android', CELLS[f'{BF[n]} android'], 'up; a flip is possible', 'mover', 'LOW', None, False, ['U3']) for n in ('clip-rect', 'edge-clipping', 'paint-order')]""",
"""    # fix r2 (plan-skeptic R2-S4): no row cut models U3 on backdrop-filter-clip-rect / -edge-clipping / -paint-order — the
    # stray blank line sits UNDER absolutely positioned boxes that do not move with the flow, and text shows through
    # their backdrop filter (fix-r2/look-backdrop-filter-*-ref-web-ios-android.png; hyphenate-character.replay-b-u3f.py
    # prints them NOT REPLAYABLE). The skeptic listed the three Android rows; the six web / iOS "up" rows have the same
    # missing replay and the same reason, so all nine are undirected (rule 1 still binds the six that are P today).
    + [pred(f'{BF[n]} {p}', CELLS[f'{BF[n]} {p}'], 'moves (the stray blank line before "No dark/black…" is removed); expected up, not replayable',
            'mover (undirected)', 'MED', None, False, ['U3'], direction='undirected', directionWhy=BF_WHY)
       for n in ('clip-rect', 'edge-clipping', 'paint-order') for p in ('web', 'ios')]
    + [pred(f'{BF[n]} android', CELLS[f'{BF[n]} android'], 'moves; expected up (a flip was called possible), not replayable', 'mover (undirected)', 'LOW', None, False, ['U3'],
            direction='undirected', directionWhy=BF_WHY) for n in ('clip-rect', 'edge-clipping', 'paint-order')]""")
rep("""    + [pred(f'{t} {p}', CELLS[f'{t} {p}'], 'up or unchanged', 'mover', 'LOW', None, False, ['U3']) for t in U3F for p in PLATS],""",
"""    # fix r2 (R2-S4): replay B on their OWN pixels (hyphenate-character.replay-b-u3f.py, scored by -score.mjs with the
    # gate's metric; the capture column reproduces the manifest). Four cells are faithfully replayable (everything below
    # the stray band is in flow) and LOOKED at (fix-r2/look-clip-path-filter-order-ref-Bweb-Bios-Bandroid.png,
    # fix-r2/look-balance-grid-container-ref-Bweb.png): they carry the replay value and stay directed. The other five
    # are NOT replayable (U3F_WHY) and are undirected.
    + [pred(f'{t} {p}', CELLS[f'{t} {p}'], *U3F_B[(t, p)], None, False, ['U3']) if (t, p) in U3F_B
       else pred(f'{t} {p}', CELLS[f'{t} {p}'], 'moves (not replayable)', 'mover (undirected)', 'LOW', None, False, ['U3'],
                 direction='undirected', directionWhy=U3F_WHY[(t, p)])
       for t in U3F for p in PLATS],""")
rep("""GLYPH = 'GEOMETRY OK'
u3_units = ['U3', 'U3b']""",
"""GLYPH = 'GEOMETRY OK'
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
          'and text shows through their backdrop filter; a row cut would move the boxes too (fix-r2/look-backdrop-filter-*-ref-web-ios-android.png)')""")
rep("""  'sequencing': 'U3 never lands without U1 + U2-android + U2-ios in the tree before it""",
"""  'sequencing': 'U3 never lands without U1 + U2-android + U2-ios in the tree before it""")
# requires for L3 rows: U3b is additive everywhere (every L3 `to` gives the U3-only value first; revertUnits.U3b.probeDecided)
rep("""# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# L4 · oof-layout""",
"""for _p in L3['predictions']:
    _p['requires'] = [u for u in _p['units'] if u != 'U3b']     # fix r2 (R2-S1): U3b is additive (its revert keeps the U3-only value)

# ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# L4 · oof-layout""")

# ── L7: the 18 seed names (N3) ──
rep("""    'U2-seed': {'commit': 'the 18 tools/visual/baseline/{Android,iOS,web}__00{0..5}_*.png from""",
"""    'U2-seed': {'commit': 'the 18 tools/visual/baseline/{Android,iOS,web}__{000_ATC_AllThenProps,001_ATC_PropsThenAll_InGreenParent,002_reset,003_ATC_InitialUnderRedParent,004_span,005_ATC_DirectionSurvives}.png (exactly these names: label-chrome-all-reset.seeded-77fe41e8/; the glob __00{0..5}_*.png also matches 66 committed baselines of four other fixtures) from""")

# ── probe_decisions: demotion (R2-S1) ──
rep("""        out.append(dict(r, withdrawnPredictions=gone, mustNotMoveAfter=gone, withdrawnGeometryKeys=geo,""",
"""        # fix r2 (plan-skeptic R2-S1): a prediction that keeps SOME units but needs this one (`requires`) can no longer
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
        out.append(dict(r, withdrawnPredictions=gone, mustNotMoveAfter=gone, demotedPredictions=demoted, withdrawnGeometryKeys=geo,""")
rep("""    for name, lane in lanes.items():
        for p in lane['predictions']:
            if p['cell'] in RESTATE:
                p.update({k: v for k, v in RESTATE[p['cell']].items() if k in ('to', 'floor', 'confidence', 'gating', 'kind')})
                p['restated'] = RESTATE[p['cell']].get('why', 'PLAN §10')
    return {'reverted': out} if out else None""",
"""    for name, lane in lanes.items():
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
                assert p['cell'] in RESTATE and 'gating' in RESTATE[p['cell']] and 'why' in RESTATE[p['cell']], \\
                    f'{name} {p["cell"]}: gating, but its required unit(s) {need} are reverted — demote it or RESTATE it with a why'
                assert p.get('floor') is not None, f'{p["cell"]}: RESTATE re-gates it without a floor'
    return {'reverted': out} if out else None""")
# withdrawn rows: gating off too (adjudicate already skips them; this keeps the gating count honest)
rep("""        gone = [p['cell'] for p in lane['predictions'] if p['units'] and all((r['lane'], u) in rev for u in p['units'])
                and r['unit'] in p['units'] and p['kind'] != 'non-corpus']""",
"""        gone = [p['cell'] for p in lane['predictions'] if p['units'] and all((r['lane'], u) in rev for u in p['units'])
                and r['unit'] in p['units'] and p['kind'] != 'non-corpus']
        for p in lane['predictions']:      # fix r2: a withdrawn row gates nothing (held to must-not-move by adjudicate R5)
            if p['cell'] in gone and 'demotedFrom' not in p:
                p['demotedFrom'] = {'gating': p['gating'], 'floor': p['floor'], 'direction': p['direction'], 'unit': r['unit'], 'run': r['run'], 'rule': r['rule'], 'withdrawn': True}
                p.update(gating=False, floor=None)""")

# ── R2-S3: the --movers 0.005 record as JSON (the corpus snapshot's source) ──
rep("""  'scoreReadout': ('node tools/titan/score-gate.mjs wave54-open wave54-final --watch tools/titan/results/wave54-plan/watchlist.txt'
                   ' --movers 0.005 > tools/titan/results/wave54-gate/score-final.txt   (the human-readable mover list for the PR; adjudicates nothing)'),""",
"""  'scoreReadout': ('node tools/titan/score-gate.mjs wave54-open wave54-final --watch tools/titan/results/wave54-plan/watchlist.txt'
                   ' --movers 0.005 --json tools/titan/results/wave54-gate/score-final.movers0005.json > tools/titan/results/wave54-gate/score-final.txt'
                   '   (the human-readable mover list for the PR; adjudicates nothing)'),
  # fix r2 (plan-skeptic R2-S3): make-corpus.mjs publishes `movers: record.movers.length` under a hardcoded
  # "--movers 0.005" scorer string, and the --movers 0 record lists EVERY same-verdict cell (4096 movers on the
  # identity pair) — so the snapshot is built from the 0.005 JSON, never from score-final.json.
  'corpusSnapshotSource': ('tools/titan/results/wave54-gate/score-final.movers0005.json (the <record.json> of tools/titan/results/wave52-gate/make-corpus.mjs; '
                           'its perCellDiff.movers then counts |Δ| >= 0.005 exactly as its scorer string says — score-final.json, the --movers 0 record of R4/R5, would publish ≈4000)'),""")

# ── R2-M2: the probe read-outs, in the right order, with a floor reader ──
rep("""                  'readWith': 'the UNION carrier set (BACKLOG wave-53 lesson: a probe of a shared tree measures the union); per-lane geometry decides L1 and L2 only; every other lane\\'s cells in these sections are recorded and decided at wave54-probe'},""",
"""                  'readWith': 'the UNION carrier set (BACKLOG wave-53 lesson: a probe of a shared tree measures the union); per-lane geometry decides L1 and L2 only; every other lane\\'s cells in these sections are recorded and decided at wave54-probe',
                  'readOut': PROBE_READOUT('wave54-pre', STAGE1, stage1=True)},""")
rep("""  'probeRun': {'runId': 'wave54-probe', 'sections': PROBE,
               'cmd': 'tools/titan/gate-driver.sh wave54-probe --sections ' + ','.join(PROBE) + ' --skip-fixture-net',""",
"""  'probeRun': {'runId': 'wave54-probe', 'sections': PROBE,
               'cmd': 'tools/titan/gate-driver.sh wave54-probe --sections ' + ','.join(PROBE) + ' --skip-fixture-net',
               'readOut': PROBE_READOUT('wave54-probe', PROBE),""")
rep("""expectations = {
  'wave': 54,""",
"""# fix r2 (plan-skeptic R2-M2): the read-out of a device probe, base FIRST everywhere. ab-diff.mjs is
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
                             'probe-readout on score-gate wave53-open wave53-probe --movers 0 names L2 TB-android (006 android 0.9906 < 0.996, Δ-0.0038) and prints margin-root-002 ×3 as f→P; '
                             'the reversed record exits 2 (ORDER); a --movers 0.005 record exits 2; the identity record fires rule 3 on 31 rows (34 minus the 3 vacuous autopos floors), 6 with --stage1'),
    }

expectations = {
  'wave': 54,""")
rep("""  'geometryGate': {'cmd': 'python3 tools/titan/results/wave54-plan/geometry-gate.py <run> --base wave54-open',
                   'what': 'runs every lane\\'s geometryProbes, prints one PASS/FAIL per expect key, names the unit each failing gating row reverts (rule 4), exits 1 on a gating FAIL or a probe self-check failure'},""",
"""  'geometryGate': {'cmd': 'python3 tools/titan/results/wave54-plan/geometry-gate.py <run> --base wave54-open',
                   'what': 'runs every lane\\'s geometryProbes, prints one PASS/FAIL per expect key, names the unit each failing gating row reverts (rule 4), exits 1 on a gating FAIL or a probe self-check failure, 2 on a STALE json, 3 when no gating key FAILs but one is UNMEASURED (fix r2, R2-S2: never a silent pass); R6 = exit 0'},""")
rep("""    '3 floor: a gating prediction (every HIGH / MED-HIGH row) below its floor reverts the commit that carries it',""",
"""    '3 floor: a gating prediction (every HIGH / MED-HIGH row) below its floor reverts the commit that carries it. Read by probe-readout.mjs over the --movers 0 probe record (stage1Probe.readOut / probeRun.readOut). A row demoted by a probe decision (probeDecisions.reverted[].demotedPredictions: a required unit is gone) has no floor',""")
rep("""    '4 geometry: a geometryProbes gating key whose line does not end in its exact expect string (or lacks its contains string) reverts the unit geometry-gate.py names',""",
"""    '4 geometry: a geometryProbes gating key whose line does not end in its exact expect string (or lacks its contains string) reverts the unit geometry-gate.py names; an UNMEASURED gating key is not a pass (geometry-gate.py exit 3: re-run the section)',""")

# ── report: a demoted HIGH / MED-HIGH row is not gating by construction ──
rep("""assert all(p['gating'] for l in lanes.values() for p in l['predictions']
           if p['confidence'].startswith(('HIGH', 'MED-HIGH')) and p['kind'] != 'non-corpus'), 'a HIGH/MED-HIGH prediction is not gating'""",
"""assert all(p['gating'] for l in lanes.values() for p in l['predictions']
           if p['confidence'].startswith(('HIGH', 'MED-HIGH')) and p['kind'] != 'non-corpus' and 'demotedFrom' not in p), 'a HIGH/MED-HIGH prediction is not gating'""")
rep("""        print(f"probe decision {d['lane']} {d['unit']}: withdrawn predictions {len(d['withdrawnPredictions'])}, \"""",
"""        print(f"probe decision {d['lane']} {d['unit']}: withdrawn predictions {len(d['withdrawnPredictions'])}, demoted {d['demotedPredictions']}, \"""")
open(sys.argv[1], 'w').write(src)
print('patched ->', sys.argv[1])
