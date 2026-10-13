#!/usr/bin/env node
// tools/titan/results/wave55-plan/briefs-audit.cells.mjs
//
// The wave-55 auditor's CELL read-out: every "from" value in the seven briefs' §1 / §5 / §6
// tables (transcribed below, one row per cell, with the brief and line it came from), checked
// against the run it names with score-gate.mjs's own loadRun — scored iff typeof ssim ===
// 'number' && !scoreExcluded, P iff wptPass === true. A written score is compared at the
// precision it is written in. Rows marked `unscored` must NOT be scored in that run (the
// manifest idiom). READ-ONLY; stdout only.
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

// [brief, briefLine, run, test (section-relative, no .html), platform, expected "P 0.9611" | "unscored"]
const ROWS = [
  // ---- L1 hyphen-ios-linebox §1 (:63-68) and §6 (:384-391)
  ['hyphen-ios-linebox', 65, 'wave54-open', 'css-text/hyphens/hyphenate-character-001', 'ios', 'f 0.9287'],
  ['hyphen-ios-linebox', 65, 'wave54-ab-drop-U3b', 'css-text/hyphens/hyphenate-character-001', 'ios', 'P 0.9778'],
  ['hyphen-ios-linebox', 384, 'wave54-final', 'css-text/hyphens/hyphenate-character-001', 'ios', 'P 0.9611'],
  ['hyphen-ios-linebox', 66, 'wave54-open', 'css-text/hyphens/hyphenate-character-002', 'ios', 'f 0.9261'],
  ['hyphen-ios-linebox', 66, 'wave54-ab-drop-U3b', 'css-text/hyphens/hyphenate-character-002', 'ios', 'P 0.9692'],
  ['hyphen-ios-linebox', 387, 'wave54-final', 'css-text/hyphens/hyphenate-character-002', 'ios', 'P 0.9534'],
  ['hyphen-ios-linebox', 67, 'wave54-open', 'css-text/hyphens/hyphenate-character-003', 'ios', 'f 0.9337'],
  ['hyphen-ios-linebox', 67, 'wave54-ab-drop-U3b', 'css-text/hyphens/hyphenate-character-003', 'ios', 'P 0.9758'],
  ['hyphen-ios-linebox', 385, 'wave54-final', 'css-text/hyphens/hyphenate-character-003', 'ios', 'P 0.957'],
  ['hyphen-ios-linebox', 68, 'wave54-open', 'css-text/hyphens/hyphenate-character-004', 'ios', 'f 0.9134'],
  ['hyphen-ios-linebox', 68, 'wave54-ab-drop-U3b', 'css-text/hyphens/hyphenate-character-004', 'ios', 'P 0.9742'],
  ['hyphen-ios-linebox', 386, 'wave54-final', 'css-text/hyphens/hyphenate-character-004', 'ios', 'f 0.9477'],
  ['hyphen-ios-linebox', 73, 'wave54-final', 'css-text/hyphens/hyphenate-character-004', 'android', 'P 0.9774'],
  ['hyphen-ios-linebox', 73, 'wave54-ab-drop-U3b', 'css-text/hyphens/hyphenate-character-004', 'android', 'f 0.9456'],
  ['hyphen-ios-linebox', 74, 'wave54-final', 'css-text/hyphens/hyphenate-character-001', 'web', 'P 0.985'],
  ['hyphen-ios-linebox', 74, 'wave54-final', 'css-text/hyphens/hyphenate-character-003', 'web', 'P 0.9825'],
  ['hyphen-ios-linebox', 74, 'wave54-final', 'css-text/hyphens/hyphenate-character-004', 'web', 'P 0.9832'],
  ['hyphen-ios-linebox', 74, 'wave54-ab-drop-U3b', 'css-text/hyphens/hyphenate-character-001', 'web', 'P 0.9665'],
  ['hyphen-ios-linebox', 74, 'wave54-ab-drop-U3b', 'css-text/hyphens/hyphenate-character-003', 'web', 'P 0.9617'],
  ['hyphen-ios-linebox', 74, 'wave54-ab-drop-U3b', 'css-text/hyphens/hyphenate-character-004', 'web', 'P 0.9534'],
  ['hyphen-ios-linebox', 75, 'wave54-final', 'css-text/hyphens/hyphenate-character-001', 'android', 'P 0.9802'],
  ['hyphen-ios-linebox', 75, 'wave54-final', 'css-text/hyphens/hyphenate-character-003', 'android', 'P 0.9792'],
  ['hyphen-ios-linebox', 75, 'wave54-ab-drop-U3b', 'css-text/hyphens/hyphenate-character-001', 'android', 'P 0.9594'],
  ['hyphen-ios-linebox', 75, 'wave54-ab-drop-U3b', 'css-text/hyphens/hyphenate-character-003', 'android', 'P 0.956'],
  ...[['001', 'P 0.9792'], ['002', 'P 0.9695'], ['003', 'P 0.9781'], ['004', 'P 0.9774'], ['005', 'P 0.9774'], ['006', 'P 0.9714'], ['007', 'P 0.9764'], ['008', 'P 0.9789']]
    .map(([n, v]) => ['hyphen-ios-linebox', 388, 'wave54-final', `css-text/boundary-shaping/boundary-shaping-${n}`, 'ios', v]),
  ['hyphen-ios-linebox', 389, 'wave54-final', 'css-text-decor/text-decoration-dotted-001', 'ios', 'f 0.5318'],
  ['hyphen-ios-linebox', 389, 'wave54-final', 'css-text-decor/text-decoration-dotted-002', 'ios', 'f 0.5426'],
  ['hyphen-ios-linebox', 357, 'wave54-final', 'css-cascade/all-prop-001', 'ios', 'P 0.9641'],
  ['hyphen-ios-linebox', 358, 'wave54-final', 'css-counter-styles/counter-style-at-rule/disclosure-styles', 'ios', 'f 0.6127'],
  ['hyphen-ios-linebox', 359, 'wave54-final', 'css-tables/anonymous-table-ws-001', 'ios', 'P 0.9988'],
  ['hyphen-ios-linebox', 360, 'wave54-final', 'css-text/boundary-shaping/boundary-shaping-010', 'ios', 'P 0.965'],
  ['hyphen-ios-linebox', 361, 'wave54-final', 'css-text/hyphens/hyphens-vertical-001', 'ios', 'f 0.8801'],
  // ---- L2 cbb-android §1 (:51-64) and §6 (:371-381), §5 (:361, :364)
  ...[['backdrop-filter-nested-border-radius-clip-3', 'f 0.9574', 'P 0.9668'], ['backdrop-filter-nested-border-radius-clip', 'P 0.9649', 'P 1'],
    ['backdrop-filter-nested-border-radius-clip-2', 'P 0.9689', 'P 1'], ['backdrop-filter-nested-border-radius-clip-4', 'P 0.977', 'P 0.9999']]
    .flatMap(([t, fin, pr]) => [['cbb-android', 371, 'wave54-final', `filter-effects/${t}`, 'android', fin], ['cbb-android', 51, 'wave54-probe', `filter-effects/${t}`, 'android', pr]]),
  ...[['htb-ltr', 'P 0.9966', 'P 0.9967'], ['vlr-ltr', 'P 0.9966', 'P 0.9967'], ['vrl-ltr', 'P 0.9966', 'P 0.9967'], ['htb-rtl', 'P 0.9869', 'P 0.9966'], ['vlr-rtl', 'P 0.9966', 'P 0.9966'], ['vrl-rtl', 'P 0.9966', 'P 0.9966']]
    .flatMap(([t, fin, pr]) => [['cbb-android', 375, 'wave54-final', `css-flexbox/abspos/abspos-autopos-${t}`, 'android', fin], ['cbb-android', 55, 'wave54-probe', `css-flexbox/abspos/abspos-autopos-${t}`, 'android', pr]]),
  ...[['button', 'f 0.9739', 'f 0.9843'], ['input', 'f 0.4117', 'f 0.4555'], ['other', 'f 0.4396', 'f 0.4679']]
    .flatMap(([t, fin, pr]) => [['cbb-android', 378, 'wave54-final', `css-position/position-absolute-semi-replaced-stretch-${t}`, 'android', fin], ['cbb-android', 61, 'wave54-probe', `css-position/position-absolute-semi-replaced-stretch-${t}`, 'android', pr]]),
  ['cbb-android', 381, 'wave54-final', 'css-gaps/flex/flex-gap-decorations-006', 'android', 'f 0.8223'],
  ['cbb-android', 64, 'wave54-probe', 'css-gaps/flex/flex-gap-decorations-006', 'android', 'f 0.8223'],
  ['cbb-android', 68, 'wave54-final', 'filter-effects/backdrop-filter-nested-border-radius-clip-3', 'web', 'P 0.9921'],
  ['cbb-android', 68, 'wave54-final', 'filter-effects/backdrop-filter-nested-border-radius-clip-3', 'ios', 'P 0.9921'],
  ['cbb-android', 364, 'wave54-final', 'filter-effects/backdrop-filter-basic-blur', 'web', 'P 1'],
  ['cbb-android', 364, 'wave54-final', 'filter-effects/backdrop-filter-basic-blur', 'ios', 'P 0.9526'],
  ['cbb-android', 364, 'wave54-final', 'filter-effects/backdrop-filter-basic-blur', 'android', 'f 0.904'],
  // ---- L3 positioned-parent-slot §1 (:61-71), §5 (:410-418), §6 (:427-436), §9 R7 (:569-571)
  ['positioned-parent-slot', 63, 'wave54-open', 'selectors/dir-style-02a', 'android', 'f 0.9425'],
  ['positioned-parent-slot', 427, 'wave54-final', 'selectors/dir-style-02a', 'android', 'f 0.9425'],
  ['positioned-parent-slot', 64, 'wave54-final', 'selectors/dir-style-02a', 'ios', 'f 0.9455'],
  ['positioned-parent-slot', 65, 'wave54-final', 'selectors/dir-style-02a', 'web', 'P 1'],
  ['positioned-parent-slot', 429, 'wave54-final', 'css-text/bidi/bidi-tab-001', 'android', 'P 0.9805'],
  ['positioned-parent-slot', 430, 'wave54-final', 'css-writing-modes/dynamic-offset-rtl-001', 'android', 'P 0.9952'],
  ['positioned-parent-slot', 431, 'wave54-final', 'css-writing-modes/dynamic-offset-rtl-002', 'android', 'P 0.9953'],
  ['positioned-parent-slot', 432, 'wave54-final', 'css-writing-modes/dynamic-offset-vrl-rtl-001', 'android', 'P 0.9952'],
  ['positioned-parent-slot', 433, 'wave54-final', 'css-writing-modes/dynamic-offset-vrl-rtl-002', 'android', 'P 0.9952'],
  ['positioned-parent-slot', 434, 'wave54-final', 'css-anchor-position/anchor-position-multicol-nested-001', 'android', 'f 0.9573'],
  ['positioned-parent-slot', 435, 'wave54-final', 'css-anchor-position/anchor-position-multicol-004', 'android', 'P 0.9686'],
  ['positioned-parent-slot', 436, 'wave54-final', 'css-text/hyphens/hyphenate-character-005', 'android', 'f 0.9474'],
  ['positioned-parent-slot', 410, 'wave54-final', 'css-break/block-end-aligned-abspos-nested', 'android', 'P 0.9967'],
  ['positioned-parent-slot', 410, 'wave54-final', 'css-counter-styles/counter-suffix', 'android', 'P 0.989'],
  ['positioned-parent-slot', 411, 'wave54-final', 'css-position/position-absolute-scrollable-overflow-zero', 'android', 'P 0.9984'],
  ['positioned-parent-slot', 411, 'wave54-final', 'css-text/bidi/bidi-lines-002', 'android', 'P 0.9818'],
  ['positioned-parent-slot', 412, 'wave54-final', 'css-view-transitions/css-tags-paint-order', 'android', 'P 0.9791'],
  ['positioned-parent-slot', 412, 'wave54-final', 'css-view-transitions/css-tags-paint-order-with-entry', 'android', 'P 0.9791'],
  ['positioned-parent-slot', 413, 'wave54-final', 'selectors/dir-selector-change-003', 'android', 'P 0.9994'],
  ['positioned-parent-slot', 413, 'wave54-final', 'selectors/dir-selector-change-004', 'android', 'P 0.9944'],
  ['positioned-parent-slot', 417, 'wave54-final', 'css-text/boundary-shaping/boundary-shaping-009', 'android', 'f 0.7531'],
  ['positioned-parent-slot', 418, 'wave54-final', 'css-text-decor/text-decoration-subelements-002', 'android', 'f 0.9047'],
  ['positioned-parent-slot', 570, 'wave54-final', 'css-text/bidi/bidi-tab-001', 'ios', 'P 0.9829'],
  ['positioned-parent-slot', 571, 'wave54-final', 'css-writing-modes/dynamic-offset-rtl-001', 'ios', 'P 0.9955'],
  // ---- L4 ua-heading-margin §1 (:72-89) and §5 (:437-443)
  ...[['005', 'f 0.8995', 'f 0.9', 'P 0.9983'], ['006', 'f 0.8987', 'f 0.8993', 'P 0.9985'], ['014', 'f 0.9238', 'f 0.923', 'P 0.9975'], ['011', 'f 0.6999', 'f 0.6852', 'P 0.9948']]
    .flatMap(([n, i, a, w]) => [['ua-heading-margin', 72, 'wave54-final', `css-text-decor/text-decoration-inset-${n}`, 'ios', i],
      ['ua-heading-margin', 73, 'wave54-final', `css-text-decor/text-decoration-inset-${n}`, 'android', a],
      ['ua-heading-margin', 72, 'wave54-open', `css-text-decor/text-decoration-inset-${n}`, 'ios', i],
      ['ua-heading-margin', 73, 'wave54-open', `css-text-decor/text-decoration-inset-${n}`, 'android', a],
      ['ua-heading-margin', 81, 'wave54-final', `css-text-decor/text-decoration-inset-${n}`, 'web', w]]),
  ...[['001', 'P 0.9707', 'P 0.9848'], ['002', 'P 0.9706', 'P 0.9858'], ['003', 'P 0.9706', 'P 0.984'], ['004', 'P 0.9634', 'P 0.9859'], ['009', 'P 0.9585', 'P 0.9638']]
    .flatMap(([n, i, a]) => [['ua-heading-margin', 86, 'wave54-final', `css-text-decor/text-decoration-inset-${n}`, 'ios', i], ['ua-heading-margin', 86, 'wave54-final', `css-text-decor/text-decoration-inset-${n}`, 'android', a]]),
  ['ua-heading-margin', 437, 'wave54-final', 'css-break/block-in-inline-015-print', 'ios', 'P 0.9894'],
  ['ua-heading-margin', 437, 'wave54-final', 'css-break/block-in-inline-015-print', 'android', 'P 0.9986'],
  ['ua-heading-margin', 441, 'wave54-final', 'CSS2/css21-errata/s-11-1-1b-005', 'ios', 'f 0.9536'],
  ['ua-heading-margin', 441, 'wave54-final', 'CSS2/css21-errata/s-11-1-1b-005', 'android', 'f 0.9307'],
  ['ua-heading-margin', 443, 'wave54-final', 'CSS2/css21-errata/s-11-1-1b-006', 'ios', 'P 0.9992'],
  ['ua-heading-margin', 443, 'wave54-final', 'CSS2/css21-errata/s-11-1-1b-006', 'android', 'P 0.9983'],
  // ---- L5 counter-suffix-rows §1 (:55-61) and §6 (:434-440)
  ['counter-suffix-rows', 55, 'wave54-open', 'css-counter-styles/counter-suffix', 'ios', 'P 0.9802'],
  ['counter-suffix-rows', 434, 'wave54-final', 'css-counter-styles/counter-suffix', 'ios', 'P 0.9873'],
  ['counter-suffix-rows', 56, 'wave54-open', 'css-counter-styles/counter-suffix', 'android', 'P 0.9547'],
  ['counter-suffix-rows', 435, 'wave54-final', 'css-counter-styles/counter-suffix', 'android', 'P 0.989'],
  ['counter-suffix-rows', 57, 'wave54-open', 'css-counter-styles/counter-suffix', 'web', 'P 0.9818'],
  ['counter-suffix-rows', 438, 'wave54-final', 'css-counter-styles/counter-suffix', 'web', 'P 1'],
  ['counter-suffix-rows', 436, 'wave54-final', 'css-counter-styles/cjk-decimal/css3-counter-styles-005', 'ios', 'P 0.993'],
  ['counter-suffix-rows', 437, 'wave54-final', 'css-counter-styles/cjk-decimal/css3-counter-styles-005', 'android', 'P 0.9759'],
  ['counter-suffix-rows', 439, 'wave54-final', 'css-counter-styles/counter-style-at-rule/disclosure-styles', 'ios', 'f 0.6127'],
  ['counter-suffix-rows', 60, 'wave54-final', 'css-counter-styles/cjk-decimal/css3-counter-styles-001', 'ios', 'unscored'],
  ['counter-suffix-rows', 60, 'wave54-final', 'css-counter-styles/cjk-decimal/css3-counter-styles-001', 'android', 'unscored'],
  ['counter-suffix-rows', 60, 'wave54-final', 'css-counter-styles/cjk-decimal/css3-counter-styles-004', 'ios', 'unscored'],
  ['counter-suffix-rows', 60, 'wave54-final', 'css-counter-styles/cjk-decimal/css3-counter-styles-004', 'android', 'unscored'],
  // ---- L6 android-paint-order §1 (:79-89) and §6 (:433-446), §9 (:613-614)
  ['android-paint-order', 79, 'wave54-open', 'filter-effects/backdrop-filter-clip-rect', 'android', 'f 0.9315'],
  ['android-paint-order', 433, 'wave54-final', 'filter-effects/backdrop-filter-clip-rect', 'android', 'P 0.9559'],
  ['android-paint-order', 80, 'wave54-open', 'filter-effects/backdrop-filter-clip-rect', 'web', 'P 0.959'],
  ['android-paint-order', 80, 'wave54-open', 'filter-effects/backdrop-filter-clip-rect', 'ios', 'P 0.956'],
  ['android-paint-order', 80, 'wave54-final', 'filter-effects/backdrop-filter-clip-rect', 'web', 'P 1'],
  ['android-paint-order', 80, 'wave54-final', 'filter-effects/backdrop-filter-clip-rect', 'ios', 'P 0.9961'],
  ['android-paint-order', 81, 'wave54-final', 'filter-effects/backdrop-filter-clip-rect-zoom', 'android', 'unscored'],
  ['android-paint-order', 82, 'wave54-open', 'css-masking/clip-path/clip-path-filter-order', 'android', 'f 0.8406'],
  ['android-paint-order', 444, 'wave54-final', 'css-masking/clip-path/clip-path-filter-order', 'android', 'P 0.9548'],
  ['android-paint-order', 83, 'wave54-open', 'css-masking/clip-path/clip-path-filter-order', 'web', 'f 0.8586'],
  ['android-paint-order', 83, 'wave54-open', 'css-masking/clip-path/clip-path-filter-order', 'ios', 'f 0.8578'],
  ['android-paint-order', 444, 'wave54-final', 'css-masking/clip-path/clip-path-filter-order', 'web', 'P 1'],
  ['android-paint-order', 444, 'wave54-final', 'css-masking/clip-path/clip-path-filter-order', 'ios', 'P 0.9978'],
  ['android-paint-order', 435, 'wave54-final', 'css-flexbox/abspos/flex-abspos-staticpos-align-self-safe-001', 'android', 'P 0.9619'],
  ['android-paint-order', 436, 'wave54-final', 'css-flexbox/abspos/flex-abspos-staticpos-align-self-safe-002', 'android', 'P 0.9635'],
  ['android-paint-order', 437, 'wave54-final', 'css-grid/abspos/grid-abspos-staticpos-align-self-001', 'android', 'P 0.9673'],
  ['android-paint-order', 438, 'wave54-final', 'css-grid/abspos/grid-abspos-staticpos-justify-self-001', 'android', 'P 0.9667'],
  ['android-paint-order', 439, 'wave54-final', 'css-grid/abspos/grid-abspos-staticpos-align-self-vertWM-001', 'android', 'P 0.9612'],
  ['android-paint-order', 440, 'wave54-final', 'css-grid/abspos/grid-abspos-staticpos-align-self-rtl-001', 'android', 'P 0.9646'],
  ['android-paint-order', 442, 'wave54-final', 'css-grid/abspos/grid-abspos-staticpos-align-self-safe-001', 'android', 'f 0.9441'],
  ['android-paint-order', 443, 'wave54-final', 'css-images/gradient/gradient-powerless-hue-hsl', 'android', 'f 0.7542'],
  ['android-paint-order', 443, 'wave54-final', 'css-images/gradient/gradient-powerless-hue-hwb', 'android', 'f 0.755'],
  ['android-paint-order', 443, 'wave54-final', 'css-images/gradient/gradient-powerless-hue-lch', 'android', 'f 0.7013'],
  ['android-paint-order', 446, 'wave54-final', 'filter-effects/backdrop-filter-basic-blur', 'android', 'f 0.904'],
  ['android-paint-order', 424, 'wave54-final', 'css-color/currentcolor-003', 'android', 'f 0.7794'],
  ['android-paint-order', 613, 'wave54-final', 'filter-effects/backdrop-filter-edge-clipping', 'android', 'P 0.9533'],
  ['android-paint-order', 613, 'wave54-final', 'filter-effects/backdrop-filter-paint-order', 'android', 'P 0.9533'],
  ['android-paint-order', 614, 'wave54-final', 'css-text-decor/text-decoration-skip-spaces-001', 'android', 'f 0.9389'],
  // ---- L7 instruments §1 (:66-70) and §6 (:585-591)
  ['instruments', 585, 'wave54-final', 'css-gaps/flex/flex-gap-decorations-033', 'web', 'P 1'],
  ['instruments', 586, 'wave54-final', 'css-gaps/flex/flex-gap-decorations-033', 'ios', 'P 0.9993'],
  ['instruments', 587, 'wave54-final', 'css-gaps/flex/flex-gap-decorations-033', 'android', 'P 1'],
  ['instruments', 589, 'wave54-final', 'css-counter-styles/arabic-indic/css3-counter-styles-101', 'web', 'P 0.9932'],
  ['instruments', 589, 'wave54-final', 'css-counter-styles/arabic-indic/css3-counter-styles-101', 'ios', 'P 0.9943'],
  ['instruments', 589, 'wave54-final', 'css-counter-styles/arabic-indic/css3-counter-styles-101', 'android', 'P 0.9736'],
  ['instruments', 589, 'wave54-final', 'css-counter-styles/arabic-indic/css3-counter-styles-102', 'web', 'P 0.9791'],
  ['instruments', 589, 'wave54-final', 'css-counter-styles/arabic-indic/css3-counter-styles-103', 'web', 'P 0.9986'],
  ['instruments', 589, 'wave54-final', 'css-counter-styles/arabic-indic/css3-counter-styles-103', 'ios', 'P 0.9978'],
  ['instruments', 589, 'wave54-final', 'css-counter-styles/arabic-indic/css3-counter-styles-103', 'android', 'P 0.9913'],
  ['instruments', 591, 'wave54-final', 'css-text/hyphens/hyphens-out-of-flow-002', 'web', 'P 1'],
  ['instruments', 591, 'wave54-final', 'css-text/hyphens/hyphens-out-of-flow-002', 'ios', 'P 0.9971'],
  ['instruments', 591, 'wave54-final', 'css-text/hyphens/hyphens-out-of-flow-002', 'android', 'P 0.9943'],
  ['instruments', 591, 'wave54-final', 'css-text/hyphens/hyphens-out-of-flow-001', 'web', 'P 1'],
];

const runs = {};
const need = [...new Set(ROWS.map((r) => r[2]))];
for (const r of need) { const d = resolveRunDir(r); runs[r] = d ? loadRun(d) : null; }
// Find a cell by section-relative test path; also report the full key so a wrong section reads as a miss.
function cell(run, test, plat) { const R = runs[run]; if (!R) return undefined;
  const key = `css/${test}.html|${plat}`; for (const s of Object.values(R.sections)) { const c = s.cells.get(key); if (c) return c; } return null; }
let ok = 0, bad = 0; const lines = [];
for (const [b, ln, run, test, plat, want] of ROWS) {
  const c = cell(run, test, plat);
  let pass;
  if (c === undefined) { lines.push(`RUN-MISSING ${b}:${ln} ${run}`); bad++; continue; }
  if (want === 'unscored') pass = c === null;
  else { const [v, s] = want.split(' '); const dec = (s.split('.')[1] || '').length;
    pass = !!c && (c.pass ? 'P' : 'f') === v && Math.abs(c.ssim - +s) <= 0.5 * 10 ** -dec + 1e-12; }
  const got = c === null ? 'unscored' : `${c.pass ? 'P' : 'f'} ${c.ssim}`;
  if (pass) ok++; else bad++;
  lines.push(`${pass ? 'OK  ' : 'FAIL'} ${b}:${ln} ${run} ${test} ${plat}: brief "${want}" · run "${got}"`);
}
console.log(lines.join('\n'));
console.log(`\ncells ${ROWS.length} · OK ${ok} · FAIL ${bad} (runs: ${need.join(', ')})`);
