# fix r1: apply the round-1 plan-skeptic fixes to plan-build.py by exact, asserted string replacement (each old text must
# occur exactly once), so the edit is reviewable as a list of (defect, old, new) and never half-applies.
import sys
P = 'plan-build.py'
s = open(P).read()
def rep(tag, old, new, count=1):
    global s
    n = s.count(old)
    assert n == count, f'{tag}: expected {count} occurrence(s), found {n}'
    s = s.replace(old, new)

# ── CLI for dry runs (M3 self-test without a copy directory) ──────────────────────────────────────────────────────────
rep('cli', """import json, os

HERE = os.path.dirname(os.path.abspath(__file__))""",
"""import json, os, sys

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
assert not _argv, f'unknown arguments: {_argv}'""")

# ── S1: every prediction carries a pre-registered direction ───────────────────────────────────────────────────────────
rep('S1 pred', """    row = {'cell': line, 'from': frm, 'to': to, 'kind': kind, 'confidence': confidence, 'floor': floor,
           'gating': gating, 'geometry': geometry, 'units': list(units)}
    row.update(extra)
    return row""",
"""    row = {'cell': line, 'from': frm, 'to': to, 'kind': kind, 'confidence': confidence, 'floor': floor,
           'gating': gating, 'geometry': geometry, 'units': list(units),
           # fix r1 (plan-skeptic S1): the sign the row pre-registers. 'up-or-stay' (the default: a flip, a rise, or
           # "stays / byte-identical") is bound by revert rule 2 (delta <= -0.002 reverts its commit). 'undirected' (a
           # move whose sign the brief could not pre-register) is EXEMPT from rule 2 — a coin-flip row must not revert a
           # commit that carries HIGH flips — and is read and labelled instead; rules 1 and 5 still bind it.
           'direction': extra.pop('direction', 'up-or-stay')}
    row.update(extra)
    assert row['direction'] in ('up-or-stay', 'undirected'), row
    assert row['direction'] == 'up-or-stay' or not gating, f'{line}: a gating row must have a direction'
    return row""")
rep('S1 note', """    return {'cell': text, 'from': '-', 'to': '-', 'kind': 'non-corpus', 'confidence': 'see PLAN', 'floor': None,
            'gating': False, 'geometry': None, 'units': list(units)}""",
"""    return {'cell': text, 'from': '-', 'to': '-', 'kind': 'non-corpus', 'confidence': 'see PLAN', 'floor': None,
            'gating': False, 'geometry': None, 'units': list(units), 'direction': None}""")

# L3 rows
rep('S1 HC2 ios', """    pred(f'{HC[2]} ios', 'f 0.9261', 'mover', 'mover', 'LOW', None, False, ['U1', 'U2-ios', 'U3']),""",
"""    pred(f'{HC[2]} ios', 'f 0.9261', 'mover', 'mover', 'LOW', None, False, ['U1', 'U2-ios', 'U3'], direction='undirected',
         directionWhy='U3 alone replays UP (B 0.9382) but U1 + U2-ios change the glyph of a CF-dictionary fold that no replay models'),""")
rep('S1 HC2 android', """    pred(f'{HC[2]} android', 'f 0.9223', 'stays f (Minikin paints its own dictionary hyphen: a logged wall)', 'mover', 'LOW', None, False, ['U3']),""",
"""    pred(f'{HC[2]} android', 'f 0.9223', 'stays f (Minikin paints its own dictionary hyphen: a logged wall)', 'mover', 'LOW', None, False, ['U3'],
         directionWhy='U3 is its only unit (U2-android is identity on a dictionary run) and replay B, which models U3 alone, gives 0.923 >= 0.9223'),""")
rep('S1 HLC', """    pred(f'{HLC} ios', 'f 0.8919', 'mover (U+2010 -> U+002D in the dictionary fold)', 'mover', 'LOW', None, False, ['U2-ios']),""",
"""    pred(f'{HLC} ios', 'f 0.8919', 'mover (U+2010 -> U+002D in the dictionary fold)', 'mover', 'LOW', None, False, ['U2-ios'], direction='undirected',
         directionWhy='the glyph swap is not replayed; no sign is pre-registered'),""")

# S3: block-ellipsis rows get their geometry key; -002 ios is relabelled (it stays DEGENERATE)
rep('S3 BE2 web', """    pred(f'{BE[2]} web', 'P 0.9868', 'P ≈1.0 (B replay 1)', 'picture-correctness (stray blank line removed)', 'MED-HIGH', 0.995, True, ['U3']),
    pred(f'{BE[2]} ios', 'P 0.9813', 'P ≈0.993 (B replay 0.9928)', 'picture-correctness', 'MED', None, False, ['U3']),""",
"""    pred(f'{BE[2]} web', 'P 0.9868', 'P ≈1.0 (B replay 1)', 'picture-correctness (stray blank line removed)', 'MED-HIGH', 0.995, True, ['U3'], GEO_OK),
    # fix r1 (S3): LOOKED AT — the iOS capture paints Line 4 and no "…" (its clamp is not applied); U3 removes the stray
    # blank lines (replay B, 0.9928) but leaves 4 lines vs the ref's 3 + ellipsis: a mover up that stays DEGENERATE.
    pred(f'{BE[2]} ios', 'P 0.9813 DEGENERATE', 'P ≈0.993 (B replay 0.9928); stays DEGENERATE (Line 4 painted, no "…")',
         'mover up; stays DEGENERATE (block-ellipsis-br.geometry.py keeps "4 bands vs ref 3")', 'MED', None, False, ['U3'], 'GEOMETRY WRONG (4 bands vs ref 3)'),""")
rep('S3 BE456 web', """  ] + [pred(f'{BE[n]} web', CELLS[f'{BE[n]} web'], 'P ≈0.998 (B replay 0.998)', 'picture-correctness (stray blank line removed)', 'MED-HIGH', 0.99, True, ['U3']) for n in (4, 5, 6)]""",
"""  ] + [pred(f'{BE[n]} web', CELLS[f'{BE[n]} web'], 'P ≈0.998 (B replay 0.998)', 'picture-correctness (stray blank line removed)', 'MED-HIGH', 0.99, True, ['U3'], GEO_OK) for n in (4, 5, 6)]""")

# S6: the shy document the U2 default-argument population missed
rep('S6', """    'hyphens-vertical-001')] + [T('css-overflow', 'line-clamp', 'block-ellipsis-014'), T('css-overflow', 'line-clamp', 'block-ellipsis-028')]""",
"""    'hyphens-vertical-001', 'hyphens-none-shy-on-2nd-line-001')] + [T('css-overflow', 'line-clamp', 'block-ellipsis-014'), T('css-overflow', 'line-clamp', 'block-ellipsis-028')]
# fix r1 (plan-skeptic S6): hyphens-none-shy-on-2nd-line-001 carries U+00AD on the wire (skeptic-r1/shy-census.out.txt, 17
# documents) and was missing; its natives (P 0.998 / 0.9988) take U2's defaulted parameter like every other member.
assert len(u2_default_population) == 19, len(u2_default_population)""")

# S4: U3b reads the host's OWN declaration
rep('S4', """    'U3b': 'a line-start <br> in a host whose cascade declares line-height (the font shorthand counts): 12 brs in 4 documents, all hyphenate-character-001..004 (b2 census)',""",
"""    'U3b': 'a line-start <br> whose HOST (the br\\'s parent element) declares line-height ON ITSELF (its own declared cascade; the font shorthand counts): 12 brs in 4 documents, all hyphenate-character-001..004 (b2 census). An INHERITED line-height (an ancestor\\'s) does NOT trigger U3b: under the inherited reading 23 more 20-px brs in 4 documents would move, css-multicol/baseline-002 and baseline-007 among them (not carriers; skeptic-r1/br-census-all.out.txt) — pinned negative in extract-fixture-br-line-context.test.mjs',""")

# M3 + S5: the hyphenate probe's gating keys REQUIRE U1 + U2-ios + U3 (the glyph rides on U1's decoded wire); report keys
rep('M3/S5 hyc', """     'gating': {'hyphenate-character-001 ios': 'U2-ios|U3', 'hyphenate-character-003 ios': 'U2-ios|U3'},
     'reasonToUnit':""",
"""     'gating': {'hyphenate-character-001 ios': 'U2-ios|U3', 'hyphenate-character-003 ios': 'U2-ios|U3'},
     # fix r1 (M3): the units a gating key needs ON THE TREE to be satisfiable; a probe revert of any of them withdraws
     # the key (probe_decisions). U3b is not required: a reverted U3b leaves the U3-only picture, which the rule still
     # judges (revertUnits.U3b.probeDecided).
     'requires': {'hyphenate-character-001 ios': ['U1', 'U2-ios', 'U3'], 'hyphenate-character-003 ios': ['U1', 'U2-ios', 'U3']},
     # fix r1 (S5): non-gating keys expected WRONG today (MED / LOW targets); every other non-gating key is a control
     'report': [f'hyphenate-character-00{n} {p}' for n in (1, 3, 4) for p in PLATS if (n, p) not in ((1, 'ios'), (3, 'ios'))],
     'reasonToUnit':""")
rep('S3 be probe', """     'note': 'control: the wave-53 soft-hyphen pictures must survive U2\\'s defaulted parameter (hyphens-out-of-flow-002 web is L6\\'s carrier and is read by its own probe)'},
  ],""",
"""     'note': 'control: the wave-53 soft-hyphen pictures must survive U2\\'s defaulted parameter (hyphens-out-of-flow-002 web is L6\\'s carrier and is read by its own probe)'},
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
     'onReplayB': 'web 4/4 OK on the brief\\'s U3 replay pictures; natives unchanged (fix-r1/post-S3.block-ellipsis-fakes.out.txt)'},
  ],""")

# L4 rows (S1) + report keys (S5)
rep('S1 CC04', """    pred(f'{CC["04"]} android', 'f 0.8286', 'moves, stays f (the table itself renders wrong on both natives)', 'mover', 'MED', None, False, ['OOF-android']),""",
"""    pred(f'{CC["04"]} android', 'f 0.8286', 'moves, stays f (the table itself renders wrong on both natives)', 'mover', 'MED', None, False, ['OOF-android'], direction='undirected',
         directionWhy='oof-containing-block.md §10 R1: the positioned-container Box now hosts 004\\'s in-flow <span>FAIL</span>; its sign is not pre-registered'),""")
rep('S1 G006', """    + [pred(f'{G006} android', 'f 0.8223', 'may move, stays f (VerticalTextFlowLayout budget reads LocalContainingBlock)', 'watched side reader', 'LOW', None, False, ['CBB-android']),""",
"""    + [pred(f'{G006} android', 'f 0.8223', 'may move, stays f (VerticalTextFlowLayout budget reads LocalContainingBlock)', 'watched side reader', 'LOW', None, False, ['CBB-android'], direction='undirected',
            directionWhy='a side reader of the corrected containing block; no replay'),""")
rep('S5 oof', """                'abspos/static-fixed-inside-abspos ios': 'OOF-ios', 'abspos/static-fixed-inside-abspos android': 'OOF-android'},
     'onWave53Final':""",
"""                'abspos/static-fixed-inside-abspos ios': 'OOF-ios', 'abspos/static-fixed-inside-abspos android': 'OOF-android'},
     'report': [f'{k} {p}' for k in ('position-relative-003', 'backdrop-filter-containing-block') for p in ('ios', 'android')],
     'onWave53Final':""")
rep('S5 cbb', """                **{f'backdrop-filter-nested-border-radius-clip{k} android': 'CBB-android' for k in ('', '-2', '-3', '-4')}},
     'note':""",
"""                **{f'backdrop-filter-nested-border-radius-clip{k} android': 'CBB-android' for k in ('', '-2', '-3', '-4')}},
     'report': [f'abspos/abspos-autopos-{k} android' for k in ('htb-rtl', 'vlr-rtl', 'vrl-rtl')],
     'note':""")
rep('S5 gap', """     'gating': {'flex/flex-gap-decorations-033 ios': 'GAP-ios', 'flex/flex-gap-decorations-033 android': 'GAP-android'},""",
"""     'gating': {'flex/flex-gap-decorations-033 ios': 'GAP-ios', 'flex/flex-gap-decorations-033 android': 'GAP-android'}, 'report': [],""")

# L5 rows (S1) + report keys (S5)
for t, why in (('CLI', 'moves (iOS with the rule: f 0.7864)'), ('CRIOU', 'direction unknown (iOS with the rule f 0.857; its digits are a counter defect)'),
               ('SUB3', 'moves (stacked sup -> smaller)'), ('BRC', 'moves (bold half only)'), ('CSC', 'moves (bold half only)')):
    pass
rep('S1 CLI', """    pred(f'{CLI} android', 'f 0.7307', 'moves (iOS with the rule: f 0.7864)', 'mover', 'LOW', None, False, ['U1-android']),""",
"""    pred(f'{CLI} android', 'f 0.7307', 'moves (iOS with the rule: f 0.7864)', 'mover', 'LOW', None, False, ['U1-android'], direction='undirected',
         directionWhy='only a cross-platform anchor (iOS), which the brief declined to turn into a magnitude'),""")
rep('S1 CRIOU', """    pred(f'{CRIOU} android', 'f 0.8699', 'direction unknown (iOS with the rule f 0.857; its digits are a counter defect)', 'mover', 'LOW', None, False, ['U1-android']),""",
"""    pred(f'{CRIOU} android', 'f 0.8699', 'direction unknown (iOS with the rule f 0.857; its digits are a counter defect)', 'mover', 'LOW', None, False, ['U1-android'], direction='undirected',
         directionWhy='the brief says "direction unknown"; the iOS anchor (0.857) sits BELOW today\\'s 0.8699'),""")
rep('S1 SUB3', """    pred(f'{SUB3} android', 'f 0.9084', 'moves (stacked sup -> smaller)', 'mover', 'LOW', None, False, ['U1-android']),""",
"""    pred(f'{SUB3} android', 'f 0.9084', 'moves (stacked sup -> smaller)', 'mover', 'LOW', None, False, ['U1-android'], direction='undirected',
         directionWhy='no replay; the stacked sup face change has no pre-registered sign'),""")
rep('S1 BRC', """    pred(f'{BRC} android', 'f 0.5977', 'moves (bold half only)', 'mover', 'LOW', None, False, ['U1-android']),
    pred(f'{CSC} android', 'f 0.4904', 'moves (bold half only)', 'mover', 'LOW', None, False, ['U1-android']),""",
"""    pred(f'{BRC} android', 'f 0.5977', 'moves (bold half only)', 'mover', 'LOW', None, False, ['U1-android'], direction='undirected',
         directionWhy='bold-only half of a sized h1; no replay'),
    pred(f'{CSC} android', 'f 0.4904', 'moves (bold half only)', 'mover', 'LOW', None, False, ['U1-android'], direction='undirected',
         directionWhy='bold-only half of a sized h1; no replay'),""")
rep('S5 ua', """     'gating': {'block-in-inline-015-print android': 'U1-android'},
     'note': 'inset-011 is the CONTROL""",
"""     'gating': {'block-in-inline-015-print android': 'U1-android'},
     'report': [f'text-decoration-inset-{n} {p}' for n in ('005', '006', '014') for p in ('ios', 'android')],
     'note': 'inset-011 is the CONTROL""")
rep('M2 ua note', """     'onWave53Final': '7 target rows WRONG (android band 9 vs ref 18; inset-005/-006 one band vs 2; inset-014 line-1 11/12 px); ref, web, iOS block-in-inline and the inset-011 control OK'},""",
"""     'onWave53Final': '7 target rows WRONG (android band 9 vs ref 18; inset-005/-006 one band vs 2; inset-014 line-1 11/12 px); ref, web, iOS block-in-inline and the inset-011 control OK',
     'teeth': 'fix r1 (plan-skeptic M2): block-in-inline-015-print also checks every band TOP within ±3 px of the ref; the skeptic\\'s pitch fakes print WRONG from +2 px/line (fix-r1/post-M2.pitch-down6.out.txt), the iOS anchor (tops 30/66/105/144 vs 29/65/103/142) stays OK'},""")

# L6 rows (S1, S3) + keys
rep('S3 BS rows', """  ] + [pred(f'{BS[n]} web', 'P 0.9713 DEGENERATE', '≈0.982 (simulated 0.9822)', 'degenerate->faithful (atoms packed)', 'MED', None, False, ['RS'], GEO_OK if n == '10' else None)
       for n in ('10', '11', '14', '15', '16', '17', '18', '19')]
    + [pred(f'{BS[n]} web', 'P 0.9599 DEGENERATE', '≈0.982 (simulated 0.9822)', 'degenerate->faithful', 'MED', None, False, ['RS']) for n in ('20', '21', '24', '25')]""",
"""  ] + [pred(f'{BS[n]} web', 'P 0.9713 DEGENERATE', '≈0.982 (simulated 0.9822)', 'degenerate->faithful (atoms packed)', 'MED', None, False, ['RS'], GEO_OK)
       for n in ('10', '11', '14', '15', '16', '17', '18', '19')]
    + [pred(f'{BS[n]} web', 'P 0.9599 DEGENERATE', '≈0.982 (simulated 0.9822)', 'degenerate->faithful', 'MED', None, False, ['RS'], GEO_OK) for n in ('20', '21', '24', '25')]""")
rep('S1 DFRLI', """       pred(f'{T("css-display", "display-flow-root-list-item-001")} web', 'f 0.8003', 'mover', 'mover', 'LOW', None, False, ['RS'])]""",
"""       pred(f'{T("css-display", "display-flow-root-list-item-001")} web', 'f 0.8003', 'mover', 'mover', 'LOW', None, False, ['RS'], direction='undirected',
            directionWhy='in the RS 27-document radius; not simulated')]""")
rep('S3 RS expect', """     'expect': {f'{t} {p}': GEO_OK for t in ('box-sizing-007', 'box-sizing-008', 'box-sizing-010', 'box-sizing-013', 'box-sizing-022',
                                             'position-absolute-semi-replaced-stretch-other', 'position-absolute-semi-replaced-stretch-input') for p in ('web',)}
               | {f'{t} {p}': GEO_OK for t in ('box-sizing-007', 'box-sizing-008', 'box-sizing-010', 'box-sizing-022') for p in ('ios', 'android')}""",
"""     'expect': {f'{t} {p}': GEO_OK for t in ('box-sizing-007', 'box-sizing-008', 'box-sizing-010', 'box-sizing-013', 'box-sizing-022',
                                             'position-absolute-semi-replaced-stretch-other', 'position-absolute-semi-replaced-stretch-input') for p in ('web',)}
               | {f'{t} {p}': GEO_OK for t in ('box-sizing-007', 'box-sizing-008', 'box-sizing-010', 'box-sizing-022') for p in ('ios', 'android')}
               # fix r1 (plan-skeptic S3): one key per tallied box-sizing capture (011, 014-019 = the 010 class; 020/021/024/025)
               | {f'box-sizing-0{n} {p}': GEO_OK for n in ('11', '14', '15', '16', '17', '18', '19', '20', '21', '24', '25') for p in PLATS}""")
rep('S5 RS', """     'gating': {'box-sizing-007 web': 'RS', 'box-sizing-008 web': 'RS', 'box-sizing-022 web': 'RS'},
     'note': 'native rows are controls""",
"""     'gating': {'box-sizing-007 web': 'RS', 'box-sizing-008 web': 'RS', 'box-sizing-022 web': 'RS'},
     'report': [f'box-sizing-0{n} web' for n in ('10', '11', '13', '14', '15', '16', '17', '18', '19', '20', '21', '24', '25')]
               + [f'position-absolute-semi-replaced-stretch-{k} web' for k in ('other', 'input')],
     'teeth': 'fix r1 (plan-skeptic S2): after the recorded row, EVERY ref atom row band is checked on its middle row; the skeptic\\'s partial fix (only the probed band separated) prints WRONG on 007 / 008 web (fix-r1/post-S2.rs-partial.out.txt), the ref itself 72/72 OK',
     'note': 'native rows are controls""")
rep('S5 W1', """     'gating': {'hyphens/hyphens-out-of-flow-002 web': 'W1'},""",
"""     'gating': {'hyphens/hyphens-out-of-flow-002 web': 'W1'}, 'report': [],""")

# L1 / L2 / soft-hyphen report lists (S5)
rep('S5 rtl', """                '[M] counter-suffix web': 'Mprime', '[M] counter-suffix ios': 'Mprime', '[M] counter-suffix android': 'Mprime'},
     'onWave53Final': '[P] android rows WRONG""",
"""                '[M] counter-suffix web': 'Mprime', '[M] counter-suffix ios': 'Mprime', '[M] counter-suffix android': 'Mprime'},
     'report': [],
     'onWave53Final': '[P] android rows WRONG""")
rep('S5 lists', """     'gating': {f'counter-suffix {p}': 'Mprime' for p in ('web', 'android')},""",
"""     'gating': {f'counter-suffix {p}': 'Mprime' for p in ('web', 'android')},
     'report': ['counter-suffix ios'],""")
rep('S5 tbc', """     'expect': {f'006 {p}': GEO_OK for p in PLATS}, 'gating': {'006 android': 'TB-android'},""",
"""     'expect': {f'006 {p}': GEO_OK for p in PLATS}, 'gating': {'006 android': 'TB-android'}, 'report': [],""")
rep('S5 dtb', """     'expect': {f'006 {p}': 'square rows 56-75 (20) x 24-43 | red px 0' for p in PLATS}, 'gating': {'006 android': 'TB-android'},""",
"""     'expect': {f'006 {p}': 'square rows 56-75 (20) x 24-43 | red px 0' for p in PLATS}, 'gating': {'006 android': 'TB-android'}, 'report': [],""")
rep('S5 soft', """     'gating': {},
     'note': 'control: the wave-53 soft-hyphen pictures""",
"""     'gating': {}, 'report': [],
     'note': 'control: the wave-53 soft-hyphen pictures""")

# stayDegenerateEvenIfPass: block-ellipsis-002 ios (S3, looked at)
rep('S3 stayDeg', """  'stayDegenerateEvenIfPass': [f'{BL2} android (orange "!" on the left: bake measurement)', f'{CS} ios (rows 3-6)', f'{CS} android (rows 5-6)'],""",
"""  'stayDegenerateEvenIfPass': [f'{BL2} android (orange "!" on the left: bake measurement)', f'{CS} ios (rows 3-6)', f'{CS} android (rows 5-6)',
                               f'{BE[2]} ios (Line 4 painted and no "…": the iOS clamp is not applied; U3 only removes the stray blank lines)'],""")

# ── M3: probe decisions withdraw a reverted unit's gating geometry keys ──────────────────────────────────────────────
rep('M3 REVERTED', """REVERTED = []
RESTATE = {}
def probe_decisions():""",
"""REVERTED = []
RESTATE = {}
if _REVERTED_FILE:
    REVERTED = json.load(open(_REVERTED_FILE))
if _RESTATE_FILE:
    RESTATE = json.load(open(_RESTATE_FILE))

def key_units(probe, key):
    \"\"\"The units a gating key needs on the tree (its `requires` entry, else the units its `gating` value names).\"\"\"
    return set(probe.get('requires', {}).get(key) or probe['gating'][key].split('|'))

def probe_decisions():""")
rep('M3 body', """        out.append(dict(r, withdrawnPredictions=gone, mustNotMoveAfter=gone,
                        carriersWithdrawn={'captures': {p: [s for s in unit['captures'][p] if s not in still[p]] for p in PLATS},
                                           'wire': [s for s in unit['wire'] if s not in still_wire]}))""",
"""        # fix r1 (plan-skeptic M3): a gating geometry key that needs this unit can no longer pass once the unit is out
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
        out.append(dict(r, withdrawnPredictions=gone, mustNotMoveAfter=gone, withdrawnGeometryKeys=geo,
                        carriersWithdrawn={'captures': {p: [s for s in unit['captures'][p] if s not in still[p]] for p in PLATS},
                                           'wire': [s for s in unit['wire'] if s not in still_wire]}))""")

# ── M1: the score of record is produced with --movers 0 ──────────────────────────────────────────────────────────────
rep('M1', """  'scoreOfRecord': ('node tools/titan/score-gate.mjs wave54-open wave54-final --watch tools/titan/results/wave54-plan/watchlist.txt'
                    ' --movers 0.005 --json tools/titan/results/wave54-gate/score-final.json'),""",
"""  # fix r1 (plan-skeptic M1): --movers 0 puts EVERY same-verdict cell in the JSON's `movers` list, so adjudicate.mjs R4
  # reads each gating cell's measured score (a cell absent from every list read as "did not move" and failed four P->P
  # rows predicted to move < 0.005) and R5 sees a must-not-move move in (0.002, 0.005). R5 keeps its own 0.002 rule.
  'scoreOfRecord': ('node tools/titan/score-gate.mjs wave54-open wave54-final --watch tools/titan/results/wave54-plan/watchlist.txt'
                    ' --movers 0 --json tools/titan/results/wave54-gate/score-final.json > tools/titan/results/wave54-gate/score-final.movers0.txt'),
  'adjudicate': 'node tools/titan/results/wave54-gate/adjudicate.mjs tools/titan/results/wave54-gate/score-final.json',
  'scoreReadout': ('node tools/titan/score-gate.mjs wave54-open wave54-final --watch tools/titan/results/wave54-plan/watchlist.txt'
                   ' --movers 0.005 > tools/titan/results/wave54-gate/score-final.txt   (the human-readable mover list for the PR; adjudicates nothing)'),
  'adjudicateCalibrations': [
    {'input': 'skeptic-r1/synth-score.json (every gating row at its predicted value, built with the old --movers 0.005 rule)',
     'expect': 'FAIL R4 missed 4 (abspos-autopos-{htb,vlr,vrl}-ltr android, s-11-1-1b-006 android): the defect, reproduced', 'out': 'fix-r1/pre-M1.adjudicate-synth.out.txt'},
    {'input': 'fix-r1/synth-movers0-allmet.json (score-gate diffRuns, moverThreshold 0, over the 4096 wave53-final cells with every gating row at its predicted value)',
     'expect': 'R1-R5 hold, exit 0', 'out': 'fix-r1/post-M1.adjudicate-allmet.out.txt'},
    {'input': 'fix-r1/synth-movers0-floor.json (as all-met, counter-suffix ios exactly at its floor 0.985, delta +0.0048)',
     'expect': 'R1-R5 hold, exit 0', 'out': 'fix-r1/post-M1.adjudicate-floor.out.txt'},
    {'input': 'fix-r1/synth-movers0-mnm3.json (as all-met, one must-not-move cell +0.003)',
     'expect': 'FAIL R5 moved 1 (the (0.002, 0.005) hole is closed)', 'out': 'fix-r1/post-M1.adjudicate-mnm3.out.txt'},
    {'input': 'fix-r1/score-identity-movers0.json (score-gate.mjs wave53-final wave54-open --movers 0: nothing moved)',
     'expect': 'FAIL R4 missed 31 = 34 gating rows minus the 3 autopos-ltr rows whose floor 0.995 sits below today\\'s 0.9966 (their gate is geometry alone: PLAN §6)', 'out': 'fix-r1/post-M1.adjudicate-identity.out.txt'},
  ],""")

# ── S1: revert rule 2 reads the direction ────────────────────────────────────────────────────────────────────────────
rep('S1 rule2', """    '2 moved down: delta <= -0.002 wave54-open -> probe on any row of a lane\\'s predictions (any tier) reverts the commit that carries it',""",
"""    '2 moved down: delta <= -0.002 wave54-open -> probe on any prediction row whose direction is "up-or-stay" (any tier) reverts the commit that carries it. A row with direction "undirected" (a move whose sign is not pre-registered; lanes.*.predictions[].directionWhy) is EXEMPT from rule 2: its fall is looked at against the ref and named with its cause in the probe read-out and the PR, never a revert by itself; rules 1 and 5 still bind it',""")
rep('S1 tier', """    'tier: MED and below carry no floor and no geometry trigger; a shortfall against their predicted magnitude is read and labelled, never a trigger. Rules 1, 2 and 5 apply to every tier',""",
"""    'tier: MED and below carry no floor and no geometry trigger; a shortfall against their predicted magnitude is read and labelled, never a trigger. Rules 1 and 5 apply to every row of every tier; rule 2 to every "up-or-stay" row of every tier',""")

# ── S3: the picture-correct tally, machine-checked ───────────────────────────────────────────────────────────────────
rep('S3 tally', """_pd = probe_decisions()                     # None at planning: no unit has been reverted yet""",
"""# fix r1 (plan-skeptic S3): the PLAN §1 tally "passing cells made picture-correct with no change of verdict", one
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

_pd = probe_decisions()                     # None at planning: no unit has been reverted yet""")

# ── outputs go to OUT (dry runs) ─────────────────────────────────────────────────────────────────────────────────────
rep('OUT json', """json.dump(expectations, open(os.path.join(HERE, 'expectations.json'), 'w'), indent=1, ensure_ascii=False)""",
"""json.dump(expectations, open(os.path.join(OUT, 'expectations.json'), 'w'), indent=1, ensure_ascii=False)""")
rep('OUT watch', """open(os.path.join(HERE, 'watchlist.txt'), 'w').write('\\n'.join(out) + '\\n')""",
"""open(os.path.join(OUT, 'watchlist.txt'), 'w').write('\\n'.join(out) + '\\n')""")

# ── report additions ─────────────────────────────────────────────────────────────────────────────────────────────────
rep('report', """print(f'must-not-move exclusions (another lane\\'s carrier on that platform): {len(excluded)}')""",
"""und = [p['cell'] for l in lanes.values() for p in l['predictions'] if p.get('direction') == 'undirected']
print(f'undirected prediction rows (exempt from revert rule 2): {len(und)}: ' + '; '.join(und))
_gk = sum(len(pr.get('gating', {})) for l in lanes.values() for pr in l['geometryProbes'])
_rk = sum(len(pr.get('report', [])) for l in lanes.values() for pr in l['geometryProbes'])
_ak = sum(len(pr['expect']) for l in lanes.values() for pr in l['geometryProbes'])
_wk = sum(len(pr.get('withdrawn', {})) for l in lanes.values() for pr in l['geometryProbes'])
print(f'geometry keys: {_ak} = gating {_gk} + report {_rk} + control {_ak - _gk - _rk - _wk} + withdrawn {_wk}')
print(f"picture-correct tally: {len(TALLY['full'])} in full + {len(TALLY['part'])} in part, each with its geometry key")
if _pd:
    for d in _pd['reverted']:
        print(f"probe decision {d['lane']} {d['unit']}: withdrawn predictions {len(d['withdrawnPredictions'])}, "
              f"geometry keys {d['withdrawnGeometryKeys']}, captures {sum(len(v) for v in d['carriersWithdrawn']['captures'].values())}")
print(f'must-not-move exclusions (another lane\\'s carrier on that platform): {len(excluded)}')""")

open(P, 'w').write(s)
print('plan-build.py patched')
