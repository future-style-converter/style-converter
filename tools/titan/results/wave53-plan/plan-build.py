#!/usr/bin/env python3
# tools/titan/results/wave53-plan/plan-build.py — writes watchlist.txt and expectations.json for PLAN.md.
#
# Why a script: the wave-53 watchlist and the closing-gate expectations are the lanes' carrier sets, predictions and
# must-not-move cells, and most of those lists already live in the seven family censuses beside this file. Typing them
# by hand is how wave 52 got 11 dead watch lines (plan-skeptic-1 C7). This reads the censuses, adds the hand-named cells
# from the briefs, de-duplicates, and writes both files. Pure JSON/text; no image is decoded, nothing is run.
#
# Usage: python3 tools/titan/results/wave53-plan/plan-build.py
# Check: RUN=wave52-ship WATCH=tools/titan/results/wave53-plan/watchlist.txt \
#          node tools/titan/results/wave52-plan/watchlist-check.mjs        → must print "unmatched 0"
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
load = lambda name: json.load(open(os.path.join(HERE, name)))

def stem_to_path(stem):
    # wpt__<section>__<a>__<b> → <section>/<a>/<b>.html (the manifest key shape the scorer matches on).
    parts = stem.split('__')[1:]
    return '/'.join(parts) + '.html'

def cellkey_to_line(key):
    # 'css/<sec>/<path>.html|<platform>' (census form) → '<sec>/<path>.html <platform>' (watch form).
    test, plat = key.split('|')
    return test[len('css/'):] + ' ' + plat

# Plan-skeptic must-fix 2: every HIGH / MED-HIGH prediction carries a numeric `floor` and `gating: true`. On
# wave53-probe a gating cell below its floor, or a gating picture whose geometry probe does not print exactly
# "GEOMETRY OK", reverts the commit that carries it (PLAN §6 revert rules 3/4; the unit is the commit, rule 6). MED and
# below carry `floor: None, gating: False`: their magnitude is read and reported, never a floor trigger -- but revert
# rules 1 (P -> f), 2 (delta <= -0.002) and 5 (leak) apply to every tier (plan-skeptic round 2, must-fix 1). PASS is the
# scorer's own SSIM pass mark.
PASS = 0.95
def pred(cell, frm, to, kind, confidence, floor=None, gating=False, geometry=None):
    return {'cell': cell, 'from': frm, 'to': to, 'kind': kind, 'confidence': confidence,
            'floor': floor, 'gating': gating, 'geometry': geometry}
GEO_OK = 'GEOMETRY OK'   # the exact verdict suffix every geometry probe prints for a right picture

nested = load('nested-list-extractor.census.json')
rtl = load('rtl-marker-bake.census.json')
shy = load('spaceless-soft-hyphen.census.json')
crb = load('contents-root-background.census.json')
dtb = load('display-table-body.census.json')
lay = load('layout-degenerates.census.json')

# ── Lane L1 · lists-bakes ─────────────────────────────────────────────────────────────────────────────────────────────
L1 = {
  'dir': 'tools/titan/results/wave53-lists-bakes',
  'briefs': ['nested-list-extractor.md', 'rtl-marker-bake.md'],
  'captureCarriers': {
    'web': ['wpt__css-lists__counter-reset-reversed-nested', 'wpt__css-counter-styles__counter-suffix'],
    'ios': ['wpt__css-lists__counter-reset-reversed-nested', 'wpt__css-counter-styles__counter-suffix'],
    'android': ['wpt__css-lists__counter-reset-reversed-nested', 'wpt__css-counter-styles__counter-suffix',
                'wpt__css-text__bidi__bidi-lines-001', 'wpt__css-text__bidi__bidi-lines-002',
                'wpt__css-anchor-position__anchor-center-safe-rtl'],
  },
  # The only lane that changes extraction: these per-test IR documents may change, every other one must be byte-identical.
  'wireCarriers': ['wpt__css-lists__counter-reset-reversed-nested', 'wpt__css-counter-styles__counter-suffix',
                   'wpt__css-text__bidi__bidi-lines-001', 'wpt__css-text__bidi__bidi-lines-002',
                   'wpt__css-anchor-position__anchor-center-safe-rtl'],
  # Hunk P's wire rule (plan-skeptic must-fix 1): only a bake root whose browser-resolved padding is NON-ZERO on some
  # side is rewritten (6 roots in 4 documents: counter-suffix ×2, anchor-center-safe-rtl ×2, bidi-lines-001/-002 ×1).
  # The 8 zero-padding roots (dir-style-02a ×6, dir-selector-change-003/-004 ×1) keep their padding-* keys in place and
  # in order, so those three per-test IR documents stay byte-identical.
  'carrierRule': {
    'unit1': "post-fix component with meta.sourceTag in {ol, ul, menu, dir, li} whose slot ancestors include an li, or one carrying both meta.runs and pseudos.before._text",
    'unit2': "pre-fix wire: a component with meta.markerText and Position ABSOLUTE|FIXED, or a bidi-bake root (_lossyReasons 'baked-bidi-visual-order') whose resolved padding is NON-ZERO on some side",
    'zeroPaddingBakeRootsMustStayByteIdentical': ['wpt__selectors__dir-style-02a', 'wpt__selectors__dir-selector-change-003', 'wpt__selectors__dir-selector-change-004'],
  },
  # Plan-skeptic should-fix 3: the briefs' own probe sections (rtl-marker-bake §8 + nested-list-extractor §8), not a
  # narrowed subset — selectors holds the three zero-padding bake roots, css-anchor-position the unscored wire carrier
  # anchor-center-safe-rtl, css-writing-modes bidi-plaintext-br-001, css-backgrounds attachment-local-positioning-3/-4,
  # css-pseudo and css-display the bake consumers / the runs+pseudos control.
  'probeSections': ['css-lists', 'css-counter-styles', 'css-text', 'css-writing-modes', 'selectors',
                    'css-anchor-position', 'css-backgrounds', 'css-pseudo', 'css-display'],
  'predictions': [
    pred('css-lists/counter-reset-reversed-nested.html web', 'P 0.9506', 'P >= 0.995', 'picture-correctness', 'HIGH', 0.995, True, GEO_OK),
    pred('css-lists/counter-reset-reversed-nested.html ios', 'P 0.9508', 'P >= 0.99', 'picture-correctness', 'MED-HIGH', 0.99, True, GEO_OK),
    pred('css-lists/counter-reset-reversed-nested.html android', 'P 0.9509', 'P >= 0.99', 'picture-correctness', 'MED', None, False, GEO_OK),
    pred('css-counter-styles/counter-suffix.html web', 'P 0.9818', 'P >= 0.995', 'picture-correctness', 'HIGH (P + geometry) / MED (magnitude)', PASS, True, GEO_OK),
    pred('css-counter-styles/counter-suffix.html ios', 'P 0.9802 DEGENERATE', 'P ~0.986', 'degenerate->faithful', 'HIGH (stays P) / MED (magnitude, geometry)', PASS, True, GEO_OK),
    pred('css-counter-styles/counter-suffix.html android', 'P 0.9547 DEGENERATE', 'P ~0.985', 'degenerate->faithful', 'MED-HIGH', 0.975, True, GEO_OK),
    pred('css-text/bidi/bidi-lines-002.html android', 'P 0.9534', 'P 0.975-0.98', 'mover (up)', 'MED'),
    pred('css-text/bidi/bidi-lines-001.html android', 'f 0.8934', '~0.95-0.96 (f->P possible)', 'possible flip', 'MED-LOW'),
  ],
  # Which predictions gate on the geometry verdict (vs report it): see geometryGating below.
  'mustNotMove': sorted(set(
      [m['cell'] for m in nested['mustNotMove']]
      + ['css-text/bidi/bidi-lines-001.html web', 'css-text/bidi/bidi-lines-001.html ios',
         'css-text/bidi/bidi-lines-002.html web', 'css-text/bidi/bidi-lines-002.html ios']
      + [' '.join(c.split(' ')[1:3]).replace(' ', '.html ', 1) for c in rtl['carriers']['scoredCellsOfTheCodePath_wave52ship']
         if not any(t in c for t in ('counter-suffix', 'bidi-lines-00'))])),
  'geometry': [
    'counter-reset-reversed-nested x3: six rows on a 20-px pitch; rows 3-5 (Eleven/Nine/Eight) start at x97; markers read 3. 2. 11. 9. 8. 1.; "1. One" painted at y136-147',
    'counter-suffix x3: rows 9-12 (y213-297) carry marker ink at x133-145; web has NO ink at x46-58 in those rows; android RTL text at x103-127',
  ],
  'geometryProbe': {
    'cmd': 'python3 tools/titan/results/wave53-plan/lists-bakes.geometry.py <run> wave53-open',
    'expect': {f'{t} {p}': GEO_OK for t in ('counter-reset-reversed-nested', 'counter-suffix') for p in ('web', 'ios', 'android')},
    'alsoExpect': {f'counter-suffix {p}': 'rows 0-207 identical to wave53-open' for p in ('web', 'ios', 'android')},
    'geometryGating': ['counter-reset-reversed-nested web', 'counter-reset-reversed-nested ios', 'counter-suffix web', 'counter-suffix android'],
    'onWave52Ship': 'every capture row prints GEOMETRY WRONG (rows 5/6; rtl row left/right edge) — the rule can fail; every ref row prints GEOMETRY OK',
  },
  # Plan-skeptic round 2, must-fix 1 (iii): the unit of revert is the COMMIT. A cell carried by a commit reverts that
  # commit only; the union of the units' captures is the lane's captureCarriers (asserted below).
  'revertUnits': {
    'U1': {'commit': 'seam-1 (findImpliedClose at both trigger sites) + counter-bake.mjs (B) + PseudoTextFold.swift (C) + PseudoTextFold.kt (D) + pins N1-N5',
           'captures': {p: ['wpt__css-lists__counter-reset-reversed-nested'] for p in ('web', 'ios', 'android')},
           'wire': ['wpt__css-lists__counter-reset-reversed-nested']},
    'U2': {'commit': 'bidi-marker-bake.mjs + bidi-bake.mjs (hunks M + P) + ListMarkerOutsideHang.swift comment + pins V1-V5',
           'captures': {'web': ['wpt__css-counter-styles__counter-suffix'], 'ios': ['wpt__css-counter-styles__counter-suffix'],
                        'android': ['wpt__css-counter-styles__counter-suffix', 'wpt__css-text__bidi__bidi-lines-001',
                                    'wpt__css-text__bidi__bidi-lines-002', 'wpt__css-anchor-position__anchor-center-safe-rtl']},
           'wire': ['wpt__css-counter-styles__counter-suffix', 'wpt__css-text__bidi__bidi-lines-001',
                    'wpt__css-text__bidi__bidi-lines-002', 'wpt__css-anchor-position__anchor-center-safe-rtl']},
  },
  # Plan-skeptic round 2, should-fix 5: hunk P's web/iOS invariance is argued, not captured (rtl-marker-bake.md §9 risk 3).
  # Its pre-registered fallback is P-narrow, so a web/iOS change on the three marker-less hunk-P documents re-lands U2
  # narrowed instead of reverting the lane (U1 and the counter-suffix fix stay).
  'pNarrowFallback': {
    'trigger': ('a decoded-pixel change vs wave53-open on web or ios of bidi-lines-001, bidi-lines-002 or anchor-center-safe-rtl '
                '(any capture read of U2: wave53-probe, or an earlier one), or revert rule 1 / 2 on bidi-lines-001 / -002 android '
                '(only hunk P reaches those marker-less documents)'),
    'action': 're-land U2 as U2-narrow = U2 + tools/titan/results/wave53-lists-bakes/u2-narrow.patch (prepared and pinned in the lane, V3c): hunk P fires only on a bake root that hosts a hunk-M marker run; never a whole-lane revert, U1 stays',
    'wireCarriersAfter': ['wpt__css-lists__counter-reset-reversed-nested', 'wpt__css-counter-styles__counter-suffix'],
    'captureCarriersAfter': {p: ['wpt__css-lists__counter-reset-reversed-nested', 'wpt__css-counter-styles__counter-suffix'] for p in ('web', 'ios', 'android')},
    'withdrawnPredictions': ['css-text/bidi/bidi-lines-001.html android', 'css-text/bidi/bidi-lines-002.html android'],
    'mustNotMoveAfter': ['css-text/bidi/bidi-lines-001.html android', 'css-text/bidi/bidi-lines-002.html android',
                         'wpt__css-anchor-position__anchor-center-safe-rtl (capture, all three platforms; unscored)',
                         'per-test IR of bidi-lines-001, bidi-lines-002, anchor-center-safe-rtl byte-identical'],
    'unchanged': 'every counter-suffix prediction and floor (its two roots host marker runs, so P still fires there; M-without-P is never shipped)',
    'reprobe': 'css-text, css-anchor-position, css-counter-styles, css-lists on the re-landed tree + the gate-flag wire differential (exactly 2 documents)',
    'ifStillLeaking': 'revert U2 (revert rule 5); U1 stays',
  },
}

# ── Lane L2 · soft-hyphen ─────────────────────────────────────────────────────────────────────────────────────────────
# PLAN decision: hyphens-auto-control (android) and hyphens-vertical-001 (ios) are NOT in the allowed set — the brief
# predicts both byte-identical (dictionary veto; vertical gated off), so a change there must fail the control.
L2 = {
  'dir': 'tools/titan/results/wave53-soft-hyphen',
  'briefs': ['spaceless-soft-hyphen.md'],
  'captureCarriers': {
    'web': [],
    'ios': [s for s in shy['carrierSets']['ios'] if 'hyphens-vertical-001' not in s],
    'android': [s for s in shy['carrierSets']['android'] if 'hyphens-auto-control' not in s],
  },
  'wireCarriers': [],
  # Plan-skeptic should-fix 3: css-text holds every carrier; 8 of the 9 F2-bail controls live in CSS2 (5), css-pseudo,
  # css-tables and css-position (probed for L1/L3/L4 anyway). css-overflow (block-ellipsis-014/-028: spaced U+00AD
  # strings, F1 identity by construction) and css-values (ch-unit-001: an F2 bail) are NOT probed; S-L2's JVM census over
  # all 24 space-less strings and all 21 abspos hosts replaces them, and the closing gate reads both sections.
  'probeSections': ['css-text', 'CSS2', 'css-pseudo', 'css-tables', 'css-position'],
  'predictions': [
    pred('css-text/hyphens/hyphens-span-001.html android', 'P 0.9532 DEGENERATE', 'P ~0.994', 'degenerate->faithful', 'HIGH', 0.99, True, GEO_OK),
    pred('css-text/hyphens/hyphens-out-of-flow-001.html android', 'P 0.9685 DEGENERATE', 'P ~0.994 (F1+F2; F1 alone ~0.975)', 'degenerate->faithful', 'MED-HIGH', 0.985, True, GEO_OK),
    pred('css-text/hyphens/hyphens-out-of-flow-002.html android', 'P 0.982 DEGENERATE', 'P ~0.994 (F2)', 'degenerate->faithful', 'MED-HIGH', 0.985, True, GEO_OK),
    pred('css-text/hyphens/hyphens-span-001.html ios', 'f 0.8652', 'P ~0.99', 'flip', 'MED-HIGH', 0.98, True, GEO_OK),
    pred('css-text/hyphens/hyphens-out-of-flow-001.html ios', 'f 0.8935', 'P ~0.99', 'flip', 'MED', None, False, GEO_OK),
    pred('css-text/hyphens/hyphenate-character-001.html android', 'f 0.9136', 'up', 'mover; a flip is DEGENERATE by construction', 'MED'),
    pred('css-text/hyphens/hyphenate-character-003.html android', 'f 0.9168', 'up', 'mover; a flip is DEGENERATE by construction', 'MED'),
    pred('css-text/hyphens/hyphenate-character-004.html android', 'f 0.9041', 'small', 'mover; a flip is DEGENERATE by construction', 'LOW'),
    pred('css-text/hyphens/hyphenate-character-001.html ios', 'f 0.9287', 'small', 'mover; a flip is DEGENERATE by construction', 'LOW'),
    pred('css-text/hyphens/hyphenate-character-003.html ios', 'f 0.9337', 'small', 'mover; a flip is DEGENERATE by construction', 'LOW'),
    pred('css-text/hyphens/hyphenate-character-004.html ios', 'f 0.9134', 'small', 'mover; a flip is DEGENERATE by construction', 'LOW'),
  ],
  'mustNotMove': sorted(set(
      [f'css-text/hyphens/{t}.html {p}' for p in ('android', 'ios') for t in (
          'hyphens-auto-control', 'hyphens-manual-011', 'hyphens-manual-012', 'hyphens-manual-013',
          'hyphens-manual-inline-011', 'hyphens-manual-inline-012', 'hyphens-none-011',
          'hyphens-none-shy-on-2nd-line-001', 'hyphens-span-002')]
      + ['css-text/hyphens/hyphens-vertical-001.html ios', 'css-text/hyphens/hyphens-punctuation-001.html android',
         'css-overflow/line-clamp/block-ellipsis-014.html android', 'css-overflow/line-clamp/block-ellipsis-014.html ios',
         'css-overflow/line-clamp/block-ellipsis-028.html android', 'css-overflow/line-clamp/block-ellipsis-028.html ios',
         'css-text/hanging-punctuation/hanging-punctuation-inline-001.html ios']
      # Plan-skeptic nit: hyphenate-character-005 (bidi-baked, white-space: pre — declined before L2's guard) is
      # must-not-move for L2 as well as L1, on every platform (§4 step 3 says so).
      + [f'css-text/hyphens/hyphenate-character-005.html {p}' for p in ('web', 'ios', 'android')]
      + [stem_to_path(m['stem']) + ' android' for m in shy['f2AbsposStillBail']])),
  'geometry': [
    'hyphens-span-001 / -out-of-flow-001 ios: every box border-box 46 px (y108..153, pitch 51); last box ends y561 / y459',
    'hyphens-span-001 / -out-of-flow-001 / -002 android: every box reads high‐ (ink x25..61) over way (ink x24..54); no h/ighway, highwa/y or high/way box',
  ],
  'geometryProbe': {
    'cmd': 'python3 tools/titan/results/wave53-plan/soft-hyphen.geometry.py <run>',
    'expect': {'hyphens/hyphens-span-001 android': GEO_OK, 'hyphens/hyphens-out-of-flow-001 android': GEO_OK,
               'hyphens/hyphens-out-of-flow-002 android': GEO_OK, 'hyphens/hyphens-span-001 ios': GEO_OK,
               'hyphens/hyphens-out-of-flow-001 ios': GEO_OK},
    'geometryGating': ['hyphens/hyphens-span-001 android', 'hyphens/hyphens-out-of-flow-001 android',
                       'hyphens/hyphens-out-of-flow-002 android', 'hyphens/hyphens-span-001 ios'],
    'onWave52Ship': 'the five target rows print GEOMETRY WRONG (box height 26 vs 46 on ios; line-1 ink x25-76 / x25-54 vs x25-61 on android); every ref row prints GEOMETRY OK',
  },
  # Round-2 must-fix 1 (iii): F1 and F2 are separate commits. hyphens-out-of-flow-001 android is carried by both
  # (F1 alone ~0.975): a trigger on it reverts F2 first and re-probes css-text; F1 goes only if the re-probe still fails.
  'revertUnits': {
    'F1': {'commit': 'PreBreakPipeline.kt (F1) + SoftHyphenPolicy.swift (F1-iOS) + ComponentRenderer.swift seam-1 + pins (a)-(d), Catalyst pins',
           'captures': {'web': [],
                        'ios': [s for s in shy['carrierSets']['ios'] if 'hyphens-vertical-001' not in s],
                        'android': [s for s in shy['carrierSets']['android'] if 'hyphens-auto-control' not in s and 'out-of-flow-002' not in s]},
           'wire': []},
    'F2': {'commit': 'InertOutOfFlowMember.kt + InlineRunFold.kt (F2) + ComponentRenderer.kt seam-2 (breadcrumb) + InlineRunFold / RunFoldBreadcrumbSeamTest pins',
           'captures': {'web': [], 'ios': [],
                        'android': ['wpt__css-text__hyphens__hyphens-out-of-flow-001', 'wpt__css-text__hyphens__hyphens-out-of-flow-002']},
           'wire': []},
  },
}

# ── Lane L3 · canvas-root ─────────────────────────────────────────────────────────────────────────────────────────────
crb_stems = crb['carrierStems']
L3 = {
  'dir': 'tools/titan/results/wave53-canvas-root',
  'briefs': ['contents-root-background.md', 'display-table-body.md'],
  'captureCarriers': {p: crb_stems + dtb['carriers'] for p in ('web', 'ios', 'android')},
  'wireCarriers': [],
  # Plan-skeptic should-fix 3: the briefs' own probe sections — contents-root-background §8 (carriers + the
  # colour-only / contained / clipped root controls, incl. the web clip-path canvas variant) and display-table-body §8
  # (CSS2 neighbours, the table path, the table-cell-root neighbour).
  'probeSections': ['css-display', 'css-backgrounds', 'CSS2', 'css-contain', 'css-cascade', 'css-color', 'css-masking',
                    'filter-effects', 'css-images', 'css-tables', 'css-position'],
  'predictions': [
    pred('css-display/display-contents-root-background.html web', 'f 0.534', 'P (replay 1.0000)', 'flip', 'HIGH', 0.99, True, GEO_OK),
    pred('css-display/display-contents-root-background.html ios', 'f 0.5346', 'P (replay 0.9995)', 'flip', 'HIGH', 0.99, True, GEO_OK),
    pred('css-display/display-contents-root-background.html android', 'f 0.5355', 'P (replay 0.9991)', 'flip', 'HIGH', 0.99, True, GEO_OK),
    pred('css-backgrounds/background-attachment-margin-root-001.html web', 'f 0.4511', 'P (replay 0.9996)', 'flip', 'MED', None, False, GEO_OK),
    pred('css-backgrounds/background-attachment-margin-root-001.html ios', 'f 0.4509', 'P (replay 0.9992)', 'flip', 'MED', None, False, GEO_OK),
    pred('css-backgrounds/background-attachment-margin-root-001.html android', 'f 0.451', 'P (replay 0.9997)', 'flip', 'MED', None, False, GEO_OK),
    pred('css-backgrounds/background-attachment-margin-root-002.html web', 'f 0.339', 'P (replay 0.9996)', 'flip', 'MED', None, False, GEO_OK),
    pred('css-backgrounds/background-attachment-margin-root-002.html ios', 'f 0.3391', 'P (replay 0.9992)', 'flip', 'MED-LOW', None, False, GEO_OK),
    pred('css-backgrounds/background-attachment-margin-root-002.html android', 'f 0.3389', 'P (replay 0.9997)', 'flip', 'MED-LOW', None, False, GEO_OK),
    pred('CSS2/css21-errata/s-11-1-1b-006.html web', 'P 0.9941 (wrong: square y66-85)', 'P >= 0.998, square y56-75', 'picture-correctness', 'HIGH (geometry) / MED (score)', PASS, True, 'square rows 56-75 (20) x 24-43 | red px 0'),
    pred('CSS2/css21-errata/s-11-1-1b-006.html ios', 'P 0.9953 DEGENERATE', 'P ~0.998, square y56-75', 'degenerate->faithful (probe-gated)', 'MED (geometry) / LOW (score)', None, False, 'square rows 56-75 (20) x 24-43 | red px 0'),
    pred('CSS2/css21-errata/s-11-1-1b-006.html android', 'P 0.9944 DEGENERATE', 'P ~0.998, square y56-75', 'degenerate->faithful (probe-gated)', 'MED (geometry) / LOW (score)', None, False, 'square rows 56-75 (20) x 24-43 | red px 0'),
  ],
  'mustNotMove': sorted(set(
      [f'{t}.html {p}' for p in ('web', 'ios', 'android') for t in (
          'css-contain/contain-body-bg-001', 'css-contain/contain-body-bg-002', 'css-contain/contain-body-bg-003',
          'css-contain/contain-body-bg-004', 'css-contain/contain-html-bg-001', 'css-contain/contain-html-bg-002',
          'css-contain/contain-html-bg-003', 'css-contain/contain-html-bg-004', 'css-cascade/initial-background-color',
          'css-color/a98rgb-003', 'css-masking/clip-path/clip-path-document-element',
          'css-masking/clip-path/clip-path-document-element-will-change', 'filter-effects/backdrop-filter-root-element',
          'css-display/display-contents-text-only-001', 'css-images/css-image-fallbacks-and-annotations002',
          'css-images/css-image-fallbacks-and-annotations003',
          'CSS2/css21-errata/s-11-1-1b-001', 'CSS2/css21-errata/s-11-1-1b-002', 'CSS2/css21-errata/s-11-1-1b-003',
          'CSS2/css21-errata/s-11-1-1b-004', 'CSS2/css21-errata/s-11-1-1b-005', 'CSS2/css21-errata/s-11-1-1b-007',
          'CSS2/css21-errata/s-11-1-1b-008', 'CSS2/css21-errata/s-11-1-1b-009',
          'css-position/position-absolute-dynamic-static-position-table-cell', 'css-tables/baseline-vertical')])),
  'geometry': [
    'display-contents-root-background x3: background (0,128,0) over the whole 390x600 frame (~232 481 px), text bbox (17,35)-(258,51)',
    'background-attachment-margin-root-001/-002 x3: tiles cover the 358x568 ICB, phase anchored at (66,66) for 001 (scroll) and (16,16) for 002 (fixed); 16-px frame white',
    'python3 tools/titan/results/wave53-plan/display-table-body.geometry.py <run> 006 prints "square rows 56-75 (20) x 24-43 | red px 0" on all three platforms',
  ],
  'geometryProbe': {
    'cmd': ['python3 tools/titan/results/wave53-plan/canvas-root.geometry.py <run>',
            'python3 tools/titan/results/wave53-plan/display-table-body.geometry.py <run> 006'],
    'expect': dict({f'{t} {p}': GEO_OK for t in ('display-contents-root-background', 'background-attachment-margin-root-001',
                                                 'background-attachment-margin-root-002') for p in ('web', 'ios', 'android')},
                   **{f'006 {p}': 'square rows 56-75 (20) x 24-43 | red px 0' for p in ('web', 'ios', 'android')}),
    'geometryGating': ['display-contents-root-background web', 'display-contents-root-background ios',
                       'display-contents-root-background android', '006 web'],
    'probeGatedRevert': '006 ios / 006 android: a native that does not print the 006 string has its item-B call site reverted (web may ship alone)',
    'onWave52Ship': 'every capture row prints GEOMETRY WRONG (green 0.0; painted (66,66,323,365) vs the ICB); 006 prints y51-70 / y66-85; every ref row prints GEOMETRY OK',
  },
  # Round-2 must-fix 1 (iii): item A, B-web and each native B call site are separate commits, so the existing per-native
  # probe-gated revert of item B is a commit revert.
  'revertUnits': {
    'A': {'commit': 'RootBackgroundPropagation twins + the three canvas call sites + ColorApplier / BackgroundImageApplier one-liners + pins A1-A6',
          'captures': {p: crb_stems for p in ('web', 'ios', 'android')}, 'wire': []},
    'B-web': {'commit': 'CanvasTableBody.ts + its ComposedCaptureGallery.tsx call + B-web pin',
              'captures': {'web': dtb['carriers'], 'ios': [], 'android': []}, 'wire': []},
    'B-ios': {'commit': 'TableBodyForest.swift + its CaptureCanvas.swift call site (FixedHoist.split input) + Catalyst B pins',
              'captures': {'web': [], 'ios': dtb['carriers'], 'android': []}, 'wire': []},
    'B-android': {'commit': 'TableBodyForest.kt + its ScreenshotCaptureScreen.kt call site + JVM B pins / B-stack',
                  'captures': {'web': [], 'ios': [], 'android': dtb['carriers']}, 'wire': []},
  },
}

# ── Lane L4 · float-avoid ─────────────────────────────────────────────────────────────────────────────────────────────
l4_stems = lay['fixCarrierSet']['controlCheckStems']
L4 = {
  'dir': 'tools/titan/results/wave53-float-avoid',
  'briefs': ['layout-degenerates.md'],
  'captureCarriers': {'web': [], 'ios': l4_stems, 'android': l4_stems},
  'wireCarriers': [],
  # Plan-skeptic should-fix 3: layout-degenerates §8 — CSS2 holds 26 float-carrying tests (incl. the floats-clear
  # controls) and css-grid 32, the largest block of the 92-test control set; the Swift FloatAvoidPlan runs in every
  # capture-mode block loop, and S-L4's all-docs census is JVM + Catalyst, not device.
  'probeSections': ['css-contain', 'css-display', 'CSS2', 'css-grid'],
  'predictions': [
    pred('css-contain/contain-inline-size-bfc-floats-001.html ios', 'P 0.9531 DEGENERATE', 'P ~0.998 (sim 0.9984)', 'degenerate->faithful', 'HIGH', 0.99, True, GEO_OK),
    pred('css-contain/contain-inline-size-bfc-floats-001.html android', 'P 0.9519 DEGENERATE', 'P ~0.997 (sim 0.9972)', 'degenerate->faithful', 'HIGH', 0.99, True, GEO_OK),
    pred('css-contain/contain-inline-size-bfc-floats-002.html ios', 'f 0.9322', 'P ~0.998 (sim 0.9985)', 'flip', 'HIGH', 0.99, True, GEO_OK),
    pred('css-contain/contain-inline-size-bfc-floats-002.html android', 'f 0.9311', 'P ~0.997 (sim 0.9974)', 'flip', 'HIGH', 0.99, True, GEO_OK),
    pred('css-display/display-flow-root-002.html ios', 'P 0.9734 DEGENERATE (unreviewed)', 'P ~1.000', 'degenerate->faithful', 'MED', None, False, GEO_OK),
    pred('css-display/display-flow-root-002.html android', 'P 0.9734 DEGENERATE (unreviewed)', 'P ~1.000', 'degenerate->faithful', 'MED', None, False, GEO_OK),
  ],
  'mustNotMove': sorted(set(
      [f'{stem_to_path(s)} web' for s in l4_stems]
      + [cellkey_to_line(f"{r['test']}|{p}") for r in lay['gateExcludedFloatThenBfcShapes']['rows'] for p in ('ios', 'android')]
      + [cellkey_to_line(k) for k in lay['floatCarryingControlSet']['nativeCellsPassing']]
      + ['css-anchor-position/anchor-position-multicol-007.html android'])),
  'geometry': [
    'contain-inline-size-bfc-floats-001 ios/android: orange x16-215, y288-307; blue 64 600 px',
    'contain-inline-size-bfc-floats-002 ios/android: orange x16-315, y88-107',
    'display-flow-root-002 ios/android: outline x265-266, y115-316; float1 x166 (right float placed from the 400-px container width)',
  ],
  'geometryProbe': {
    'cmd': 'python3 tools/titan/results/wave53-plan/float-avoid.geometry.py <run>',
    'expect': {f'{t} {p}': GEO_OK for t in ('contain-inline-size-bfc-floats-001', 'contain-inline-size-bfc-floats-002',
                                            'display-flow-root-002') for p in ('ios', 'android')},
    'geometryGating': [f'contain-inline-size-bfc-floats-00{n} {p}' for n in (1, 2) for p in ('ios', 'android')],
    'onWave52Ship': 'the six native rows print GEOMETRY WRONG (orange y388-407; outline x16 y215-416); web and ref rows print GEOMETRY OK',
  },
  # Round-2 must-fix 1 (iii): one commit per native (the two seams are already per-native patches), so a native whose
  # FloatAvoidLayout falsifies on device is reverted alone, as L3-B's natives are.
  'revertUnits': {
    'android': {'commit': 'FloatAvoidPlan.kt + FloatAvoidLayout.kt + ComponentRenderer.kt seam-1 + FloatAvoidPlanTest / FloatAvoidSeamWiringTest',
                'captures': {'web': [], 'ios': [], 'android': l4_stems}, 'wire': []},
    'ios': {'commit': 'FloatAvoidPlan.swift + FloatAvoidLayout.swift + ComponentRenderer.swift seam-2 + FloatAvoidPlanTests / FloatAvoidLayoutRasterTests',
            'captures': {'web': [], 'ios': l4_stems, 'android': []}, 'wire': []},
  },
}

# ── Lane L5 · harness-hygiene ─────────────────────────────────────────────────────────────────────────────────────────
L5 = {
  'dir': 'tools/titan/results/wave53-harness-hygiene',
  'briefs': ['harness-hygiene.md'],
  'captureCarriers': {'web': [], 'ios': [], 'android': []},
  'wireCarriers': [],
  # Round-2 should-fix 4: wave53-hh-probe also answers web run-to-run determinism on this host, so it runs the two
  # sections that hold L1's counter-suffix crop and the bidi-lines / hyphens controls (8 of the 20 cross-host web score
  # moves sit in the two added sections, 9 in all four).
  'probeSections': ['css-cascade', 'css-counter-styles', 'css-flexbox', 'css-text'],
  'predictions': [
    pred('ios-harness XCTest StyleConverterTestTests', '17/30', '30/30', 'suite', 'HIGH (padding 4) / HIGH-MED (IcbClip 7) / MED (InlineFlow 2)'),
  ],
  'mustNotMove': ['CSS2/abspos/static-inside-inline-block.html ios', 'css-cascade/scope-pseudo-element.html ios',
                  'css-flexbox/align-items-007.html ios', 'css-gaps/flex/flex-gap-decorations-027.html ios',
                  'css-masking/clip-path/clip-path-circle-007.html ios'],
  'geometry': ['driver.log of every later run: no "gradlew --stop", one kill_own_gradle_daemons line naming what it stopped and what it left'],
  'revertUnits': {'L5': {'commit': 'own-processes.sh helpers + gate-driver / section-runner / provision-devices / smoke.sh call sites + ios-harness project.yml + tests',
                         'captures': {'web': [], 'ios': [], 'android': []}, 'wire': []}},
}

lanes = {'L1-lists-bakes': L1, 'L2-soft-hyphen': L2, 'L3-canvas-root': L3, 'L4-float-avoid': L4, 'L5-harness-hygiene': L5}

# Disjointness check: a stem may be a capture carrier of ONE lane only per platform — the control attributes by lane.
for plat in ('web', 'ios', 'android'):
    seen = {}
    for name, lane in lanes.items():
        for s in lane['captureCarriers'][plat]:
            assert s not in seen, f'{s} [{plat}] is a carrier of both {seen[s]} and {name}'
            seen[s] = name

union = {p: sorted({s for l in lanes.values() for s in l['captureCarriers'][p]}) for p in ('web', 'ios', 'android')}

# Round-2 must-fix 1 (iii): every carrier capture / wire document of a lane belongs to at least one of its revert units,
# and no unit claims a capture outside its lane's carrier set (so revert rule 1 always names a commit).
for name, lane in lanes.items():
    for plat in ('web', 'ios', 'android'):
        covered = {s for u in lane['revertUnits'].values() for s in u['captures'][plat]}
        assert covered == set(lane['captureCarriers'][plat]), f'{name} [{plat}] revert units {sorted(covered)} != carriers'
    assert {s for u in lane['revertUnits'].values() for s in u['wire']} == set(lane['wireCarriers']), f'{name} wire units'
# The probe run is the union of the lanes' probe sections, in gate order (CSS2 first, then alphabetical as the driver runs).
PROBE_SECTIONS = sorted({s for l in lanes.values() for s in l['probeSections']}, key=lambda s: (s != 'CSS2', s))

# Consistency check: no lane may name as must-not-move a capture another lane is allowed to change (a contradiction
# the gate could not adjudicate). A must-not-move line is '<sec>/<path>.html <platform>' → stem 'wpt__<sec>__<path>'.
line_stem = lambda line: 'wpt__' + line.split(' ')[0][:-len('.html')].replace('/', '__')
for name, lane in lanes.items():
    for line in lane['mustNotMove']:
        stem, plat = line_stem(line), line.split(' ')[1]
        assert stem not in union[plat], f'{name} must-not-move {line} is an allowed carrier on {plat}'
expectations = {
  'wave': 53,
  # Identical to PLAN.md §6 "Score of record" (plan-skeptic nit: the movers threshold and the JSON sink were missing).
  'scoreOfRecord': ('node tools/titan/score-gate.mjs wave53-open wave53-final --watch tools/titan/results/wave53-plan/watchlist.txt'
                    ' --movers 0.005 --json tools/titan/results/wave53-gate/score-final.json'),
  # The integrated-tree probe (PLAN §6 "Probe-then-ship"): the union of every lane's probeSections (20 sections).
  'probeRun': {'runId': 'wave53-probe', 'sections': PROBE_SECTIONS,
               'cmd': 'tools/titan/gate-driver.sh wave53-probe --sections ' + ','.join(PROBE_SECTIONS) + ' --skip-fixture-net'},
  # PLAN §6 "Revert rule", rewritten at plan-skeptic round 2 (must-fix 1). Read wave53-open -> wave53-probe (same host).
  'revertRule': [
    '1 lost: a carrier cell (any lane, any confidence tier, predicted or not) that is P on wave53-open and f on wave53-probe reverts the commit that carries it (R1 empty lost list applied at the probe)',
    '2 moved down: delta <= -0.002 (the ab-diff --threshold 0.002) wave53-open -> wave53-probe on any target or mover (every row of predictions, any tier) reverts the commit that carries it',
    '3 floor: a gating prediction (every HIGH / MED-HIGH row; 17) below its floor reverts the commit that carries it',
    '4 geometry: a geometryGating row whose probe line does not end in its exact expect string reverts the commit that carries it; 006 ios / 006 android are probeGatedRevert rows (B-ios / B-android)',
    '5 leak: a composed capture outside every carrier set that differs in decoded pixels (R4), or a per-test IR document outside the wire carriers that differs in bytes (R4b), reverts the commit it is bisected to — except rule 7',
    '6 unit: the commit (lanes.*.revertUnits); a cell carried by two commits of one lane (hyphens-out-of-flow-001 android: F1 + F2) reverts the later commit first, then its probe sections are re-probed',
    '7 P-narrow: a web/ios change on bidi-lines-001, bidi-lines-002 or anchor-center-safe-rtl, or rule 1 / 2 on bidi-lines-001 / -002 android, re-lands L1 U2 as U2-narrow (lanes.L1-lists-bakes.pNarrowFallback); it never reverts U1 or the whole lane; U2 goes only if U2-narrow still trips a rule',
    'tier: MED and below carry no floor and no geometry trigger; a shortfall against their predicted magnitude is read and labelled, never a trigger. Rules 1, 2 and 5 apply to every tier',
  ],
  'predictionsReadAgainst': ('wave52-ship; restated in the PLAN §10 pre-registration addendum for every watched cell whose wave53-open '
                             'value differs (certain: bidi-lines-001, display-contents-root-background, hyphens-manual-inline-011 and '
                             'hyphens-auto-001 web already differ by 0.0001)'),
  # PLAN §8 step 7a (round-2 should-fix 2): ONE multi-root call before every device run from L5's landing on.
  'beforeEveryDeviceRun': ('source tools/titan/own-processes.sh && kill_own_gradle_daemons "$PROJECT_ROOT" <every builder, skeptic and '
                           'fix-lane worktree and every export tree that ran Gradle this wave, from the TREES: lines of the lane notes>'),
  # PLAN §4 step 2 (round-2 should-fix 4): L5's own probe, which also measures web run-to-run determinism on this host.
  'hhProbe': {'runId': 'wave53-hh-probe', 'sections': ['css-cascade', 'css-counter-styles', 'css-flexbox', 'css-text'],
              'cmd': 'tools/titan/gate-driver.sh wave53-hh-probe --sections css-cascade,css-counter-styles,css-flexbox,css-text --skip-fixture-net',
              'expect': '0 changed composed captures vs wave53-open by DECODED pixels on all three platforms (empty carrier set); any web decoded-pixel difference restates R4/R7 web in the PLAN §10 addendum before any further lane lands'},
  # PLAN §6 (round-2 should-fix 3): the wave-53 control-check is trusted only after these three calibrations and mutations.
  'controlCalibrations': [
    {'pair': 'wave52-calib -> wave52-final', 'expect': 'leaks reported (747 under the old byte rule)'},
    {'pair': 'wave52-preview -> wave52-ship', 'expect': '0 changed, 0 re-encoded (0 of 1435 byte-different)'},
    {'pair': 'wave52-ship -> wave53-open', 'sections': ['CSS2', 'css-lists', 'css-counter-styles', 'css-display', 'css-contain'],
     'expect': ('720 compared; web: re-encoded + changed = 236, re-encoded about 235, changed contains css-counter-styles/counter-suffix '
                '(129 px, max delta 27) and every other decoded-pixel difference; ios and android: 0 changed, 0 re-encoded'),
     'mutations': ['byte rule only (no decode) -> 236 changed, 0 re-encoded -> red',
                   'decode without a pixel compare (every byte difference "re-encoded") -> counter-suffix web re-encoded -> red']},
  ],
  'expected': {'lost': [], 'unmeasuredNow': [], 'newlyMeasured': [], 'missingSections': 0, 'columnShorts': 0,
               'fixtureNet': 'exit 0 on all 9 gate fixtures'},
  'unionCaptureCarriers': union,
  'unionWireCarriers': sorted({s for l in lanes.values() for s in l['wireCarriers']}),
  'degenerateByConstruction': ['css-text/hyphens/hyphenate-character-001.html', 'css-text/hyphens/hyphenate-character-003.html',
                               'css-text/hyphens/hyphenate-character-004.html'],
  'recordedWall': ['css-anchor-position/anchor-position-multicol-007.html android'],
  'ringFenced': 'filter-effects/backdrop-filter-basic-blur — report only, never a target, never carved out',
  'lanes': lanes,
}
json.dump(expectations, open(os.path.join(HERE, 'expectations.json'), 'w'), indent=1, ensure_ascii=False)

# ── watchlist.txt ─────────────────────────────────────────────────────────────────────────────────────────────────────
out, written = [], set()
def block(title, lines):
    out.append(f'# {title}')
    for l in lines:
        if l not in written:
            written.add(l); out.append(l)

out += [
  '# tools/titan/results/wave53-plan/watchlist.txt — the wave-53 gate watchlist (every target, predicted mover and',
  '# must-not-move cell of PLAN.md §2; generated by plan-build.py from the family censuses + the briefs\' named cells).',
  '# Consumed by: node tools/titan/score-gate.mjs wave53-open <run> --watch tools/titan/results/wave53-plan/watchlist.txt',
  '# One watch per line: "<sec>/<path>.html <platform>" or a bare "<sec>/<path>.html" (all three platforms). The ".html"',
  '# suffix makes each line match exactly one test under score-gate.mjs watchCells\' substring rule; the one deliberate',
  '# exception is the family line "css-text/hyphens/ web" (L2: every web hyphens cell is a control). A line already',
  '# written under an earlier lane is not repeated. Precondition before any gate read-out:',
  '#   RUN=wave53-open WATCH=tools/titan/results/wave53-plan/watchlist.txt node tools/titan/results/wave52-plan/watchlist-check.mjs',
  '# must print "unmatched 0".',
]
pred_cells = lambda lane: [p['cell'] for p in lane['predictions'] if '.html' in p['cell']]
block('L1 lists-bakes — targets + movers (picture-correctness; bidi-lines android are the hunk-P movers)', pred_cells(L1))
block('L1 lists-bakes — must not move (nested-list census mustNotMove; the bidi-baked docs outside the carrier set)', L1['mustNotMove'])
block('L2 soft-hyphen — targets (3 android DEGENERATE -> faithful, 2 ios flips) + f movers (a flip there is DEGENERATE by construction)', pred_cells(L2))
block('L2 soft-hyphen — must not move (spaced / dictionary / vertical / F2-bail controls; the wave-52 thin pass hanging-punctuation-inline-001 ios)', L2['mustNotMove'])
block('L2 soft-hyphen — web is a control on every hyphens test', ['css-text/hyphens/ web'])
block('L3 canvas-root — targets (A: root background image x3 tests; B: display:table body, probe-gated natives)', pred_cells(L3))
block('L3 canvas-root — must not move (colour-only / contained / clipped roots; the s-11-1-1b neighbours; table-internal roots)', L3['mustNotMove'])
block('L3 canvas-root — RING-FENCED, report only (a generic canvas mechanism may move it; never a target, never carved out)', ['filter-effects/backdrop-filter-basic-blur.html'])
block('L4 float-avoid — targets (4 DEGENERATE -> faithful, 2 flips)', pred_cells(L4))
block('L4 float-avoid — must not move (web carriers; the 7 refused float-then-BFC shapes; 143 passing native float-control cells; the recorded wall)', L4['mustNotMove'])
block('L5 harness-hygiene — must not move (the cells the unblocked ios-harness XCTests pin)', L5['mustNotMove'])
block('Not staffed this wave — watched so the opening obligations 0(c) are read at the gate (BACKLOG 0(ad), 7(e¹))',
      ['css-view-transitions/column-span-during-transition-doesnt-skip.html',
       'css-gaps/flex/flex-gap-decorations-033.html ios', 'css-gaps/flex/flex-gap-decorations-033.html android'])
open(os.path.join(HERE, 'watchlist.txt'), 'w').write('\n'.join(out) + '\n')
print(f"watchlist.txt: {sum(1 for l in out if l and not l.startswith('#'))} watch lines")
print('expectations.json: union carriers', {p: len(v) for p, v in union.items()}, 'wire', len(expectations['unionWireCarriers']))
print(f'probe sections ({len(PROBE_SECTIONS)}):', ','.join(PROBE_SECTIONS))
gating = [p for l in lanes.values() for p in l['predictions'] if p['gating']]
assert all(isinstance(p['floor'], float) for p in gating), 'every gating prediction needs a numeric floor'
assert all(p['gating'] for l in lanes.values() for p in l['predictions']
           if p['confidence'].startswith(('HIGH', 'MED-HIGH')) and p['kind'] != 'suite'), 'a HIGH/MED-HIGH prediction is not gating'
print(f'gating predictions with numeric floors: {len(gating)}')
for name, lane in lanes.items():
    print(f"  {name}: carriers {[len(lane['captureCarriers'][p]) for p in ('web', 'ios', 'android')]} predictions {len(lane['predictions'])} mustNotMove {len(lane['mustNotMove'])}")
