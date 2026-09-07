#!/usr/bin/env node
//
// gen-control-fixture.mjs — derive a paired-control fixture from an existing one.
//
// ## The problem this solves
//
// A single component cannot distinguish "this property rendered correctly"
// from "this property was silently dropped". Both produce one image, and the
// harness has no second image to compare it against. So a runtime that
// ignores a property entirely scores a clean pass, on every platform, forever.
//
// That is not theoretical. Every instance below was invisible to the 327-pair
// suite until a control existed:
//
//   · backdrop-filter: blur() is dropped on BOTH iOS and Android. Because both
//     natives drop it, they AGREE — so the cross-platform gate is happy too.
//     Only the control saw it.
//   · visual-test.json's Glass_Effect has passed since it was written, because
//     it blurs a UNIFORM field where the correct result and the dropped result
//     are the same image.
//
// A control is the same component with ONE declaration removed. Case and
// control then differ if and only if that declaration has a visible effect on
// that platform. Zero difference is the finding.
//
// ## Why this is generated, not hand-authored
//
// The control must differ from its case in exactly one declaration. Authoring
// 100+ such pairs by hand guarantees drift — a typo in the control silently
// turns a real signal into noise, in the direction that looks like a pass.
// Deriving them mechanically makes that class of error impossible.
//
// It also means the source fixture is never edited: fixtures/visual-test.json
// keeps its 109 components, its 327 committed baselines (109 × 3 platforms —
// the earlier "363" matched no count at any commit; retrospective A5#5) and
// its ledger entries untouched, and this emits a SIBLING document.
//
// Usage:
//     node tools/visual/gen-control-fixture.mjs fixtures/visual-test.json \
//         --out fixtures/visual-test-controls.json [--all]
//
//     --all   also emit controls for layout/geometry declarations (see below)

import { readFileSync, writeFileSync } from 'node:fs';

// ── Which declarations get a control ────────────────────────────────────────
//
// Default is the APPEARANCE set: declarations whose removal plausibly leaves
// the geometry alone, so a zero-difference result means "no visible effect"
// rather than "the box moved and everything shifted".
//
// Geometry declarations (padding, width, display, gap, grid-*, …) are excluded
// by default not because they cannot be dropped, but because their controls
// are uninformative: removing `padding` obviously changes the render on every
// platform, so the pair can only ever report "differs" and costs a capture to
// say nothing. `--all` includes them for completeness.
const APPEARANCE = new Set([
  // paint
  'background-color', 'background-image', 'color', 'opacity', 'visibility',
  // borders + outline
  'border', 'border-top', 'border-right', 'border-bottom', 'border-left',
  'border-radius', 'border-top-left-radius', 'border-top-right-radius',
  'border-bottom-right-radius', 'border-bottom-left-radius',
  'outline', 'outline-offset',
  // effects — the highest-risk family; every confirmed silent drop is here
  'box-shadow', 'text-shadow', 'filter', 'backdrop-filter', 'clip-path',
  'mix-blend-mode',
  // transforms
  'transform', 'transform-origin', 'perspective', 'zoom',
  // typography
  'font-size', 'font-weight', 'font-family', 'font-style', 'font-stretch',
  'line-height', 'letter-spacing', 'text-align', 'text-decoration',
  'text-transform', 'text-indent', 'text-overflow', 'white-space',
  'vertical-align',
  // paint order / clipping
  'z-index', 'overflow',
]);

/**
 * Text pinned onto BOTH case and control. This is the load-bearing detail of
 * the whole method, and getting it wrong silently invalidates every result.
 *
 * A component with no `_text` renders its own NAME as a label —
 * `visibleText = name.replace(/_/g, ' ')` in
 * apps/web-harness/src/sdui/ComponentRenderer.tsx:1274-1276. A control's key
 * necessarily differs from its case's (`X` vs `X__no_filter`), so without
 * this pin the two render DIFFERENT label strings and every pair reports
 * "differs" — for a reason that has nothing to do with the property.
 *
 * Measured before the pin: 42 of 43 declarations came back "has effect" on
 * every component, with the diff confined to a 7px-tall band — the block
 * font's cell height. That was the label, not the property.
 *
 * Pinning identical text on both sides removes the confound. It also flips
 * components onto the REAL text path (`isBlockLabel` requires
 * `text === undefined`), which is a feature here: it makes typography
 * declarations genuinely testable instead of inert against a fixed-pitch
 * bitmap. The cross-platform glyph wall does not interfere, because a
 * control comparison is always WITHIN one platform — same text, same
 * renderer, same run.
 */
const PINNED_TEXT = 'Hamburg 123';

const args = process.argv.slice(2);
const input = args.find((a) => !a.startsWith('--'));
const outIdx = args.indexOf('--out');
const output = outIdx >= 0 ? args[outIdx + 1] : null;
const all = args.includes('--all');

if (!input || !output) {
  console.error('usage: gen-control-fixture.mjs <fixture.json> --out <out.json> [--all]');
  process.exit(2);
}

const doc = JSON.parse(readFileSync(input, 'utf8'));
if (!doc.components || typeof doc.components !== 'object') {
  console.error(`${input}: no "components" object`);
  process.exit(2);
}

/**
 * Sanitise a declaration name for use inside a component key. Component names
 * become capture FILENAMES, so anything the harness would rewrite (or that
 * would collide after rewriting) has to go now rather than at capture time.
 */
const slug = (s) => s.replace(/[^A-Za-z0-9]+/g, '_');

const out = {};
let cases = 0, controls = 0, skipped = 0;
const perProperty = new Map();

for (const [name, comp] of Object.entries(doc.components)) {
  // Emit the CASE unchanged, under its original key. Case and control must be
  // captured in the same run, on the same canvas, at the same index spacing —
  // comparing against a previous run's capture would fold run-to-run noise
  // into every verdict.
  // Pin the text on the CASE too — case and control must differ in exactly
  // one thing, and that thing is the declaration, never the label.
  const withText = { ...JSON.parse(JSON.stringify(comp)), _text: comp._text ?? PINNED_TEXT };
  out[name] = withText;
  cases += 1;

  const props = comp.properties || {};
  for (const decl of Object.keys(props)) {
    if (!all && !APPEARANCE.has(decl)) { skipped += 1; continue; }
    // The control: identical component, this one declaration removed.
    // Structured-clone so nested `children` / `selectors` are not shared.
    const clone = JSON.parse(JSON.stringify(withText));
    delete clone.properties[decl];
    clone._note =
      `CONTROL for "${name}" — identical except \`${decl}\` is removed. ` +
      `If this renders the same as its case on a platform, that platform ` +
      `applied no visible effect for \`${decl}\`.`;
    out[`${name}__no_${slug(decl)}`] = clone;
    controls += 1;
    perProperty.set(decl, (perProperty.get(decl) ?? 0) + 1);
  }
}

const result = {
  _note: [
    `Generated from ${input} by tools/visual/gen-control-fixture.mjs — do not hand-edit.`,
    '',
    'Each `X__no_<decl>` component is X with exactly one declaration removed.',
    'Case and control differ if and only if that declaration has a visible',
    'effect on the platform that rendered them. ZERO difference is the finding:',
    'it means the property was dropped, or is inert on that corpus.',
    '',
    'This is a SIBLING of the source fixture. The source is never edited, so',
    'its committed baselines and ledger entries are untouched.',
    '',
    'RUN THIS WITH --no-cross-platform-gate. The question a control fixture',
    'answers is WITHIN one platform ("did this platform apply the property at',
    'all?"), not across platforms. The cross-platform gate has no ledger for',
    'these components and would report every genuine case/control difference',
    'as an unexpected divergence — 86 of them on the first run. That is the',
    'gate behaving correctly on the wrong question.',
    '',
    `cases ${cases} · controls ${controls}` + (all ? '' : ` · ${skipped} geometry declarations skipped (--all to include)`),
  ].join('\n'),
  components: out,
};

writeFileSync(output, `${JSON.stringify(result, null, 2)}\n`);

console.log(`✓ ${output}`);
console.log(`  ${cases} case(s) + ${controls} control(s) = ${cases + controls} components`);
if (!all) console.log(`  ${skipped} geometry declaration(s) skipped — rerun with --all to include them`);
console.log('  controls per declaration:');
for (const [d, n] of [...perProperty].sort((a, b) => b[1] - a[1])) {
  console.log(`    ${String(n).padStart(3)}  ${d}`);
}
