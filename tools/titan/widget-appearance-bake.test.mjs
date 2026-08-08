// tools/titan/widget-appearance-bake.test.mjs — wave-36 lane M2.
//
// Pins the sixth bake against its two authorities:
//   1. css-ui-4 §appearance-disabling-properties + §appearance-switching —
//      the property list and the "declared appearance picks the widget"
//      rule;
//   2. the LIVE WPT references under tools/wpt/css/css-ui/ — every
//      expectation below is read off a compute-kind-widget-fallback-*-ref
//      file, so a table typo cannot pass. The grouped
//      compute-kind-widget-fallback-all-elements-ref.html states the whole
//      table in one place and is the row-by-row source for the fallback
//      test below.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  APPEARANCE_DISABLING_PROPERTIES, APPEARANCE_DISABLED_MARKER,
  collectAppearanceDisablingScripts, appearanceDisablingRules,
  nativeWidgetKind, widgetFallbackAppearance,
} from './widget-appearance-bake.mjs';

// The exact script every compute-kind-widget-generated test carries.
const generatedScript = (propCsv) => `
<script>
// Set author-level CSS that matches UA style, but don't use the 'revert' value.
const elements = document.querySelectorAll('#container > *');
const props = "${propCsv}".split(",");
for (const el of elements) {
  for (const prop of props) {
    el.style.setProperty(prop, getComputedStyle(el).getPropertyValue(prop));
  }
}
</script>`;

// ── the recogniser ──────────────────────────────────────────────────────────

test('recognises the generated appearance-disabling script', () => {
  const found = collectAppearanceDisablingScripts(generatedScript('border-top-width'));
  assert.deepEqual(found, [{ selector: '#container > *', props: ['border-top-width'] }]);
});

test('keeps only the appearance-disabling subset of the mutated props', () => {
  const found = collectAppearanceDisablingScripts(
    generatedScript('color,border-top-width,font-size,background-image'),
  );
  assert.deepEqual(found[0].props, ['border-top-width', 'background-image']);
});

test('a script mutating only NON-disabling props produces no bake', () => {
  assert.deepEqual(collectAppearanceDisablingScripts(generatedScript('color,font-size')), []);
});

test('the grouped multi-prop form is recognised too', () => {
  const found = collectAppearanceDisablingScripts(generatedScript(
    'border-top-width,border-right-width,border-bottom-width,border-left-width',
  ));
  assert.equal(found.length, 1);
  assert.equal(found[0].props.length, 4);
});

test('a setProperty that assigns some OTHER value is not this idiom', () => {
  // The back-reference in SELF_ASSIGN_RE is what rejects this: the property
  // read back is `other`, not the `prop` being written.
  const html = `
<script>
const elements = document.querySelectorAll('#container > *');
const props = "border-top-width".split(",");
for (const el of elements)
  for (const prop of props)
    el.style.setProperty(prop, getComputedStyle(el).getPropertyValue('other'));
</script>`;
  assert.deepEqual(collectAppearanceDisablingScripts(html), []);
});

test('the sibling no-fallback test is REJECTED (its widgets keep native chrome)', () => {
  // compute-kind-widget-no-fallback-props-001.html: a Set literal of the
  // disabling props it deliberately SKIPS, and a two-phase mutations.push
  // loop. Neither the quoted `.split(",")` list nor the self-assigning
  // setProperty call is present.
  const html = `
<script>
const elements = document.querySelectorAll('#container > *');
const fallbackProps = new Set(['background-color','border-top-width']);
let mutations = [];
const declarations = getComputedStyle(document.documentElement);
for (const prop of declarations) {
  if (fallbackProps.has(prop)) continue;
  for (const el of elements) mutations.push([el, prop, getComputedStyle(el).getPropertyValue(prop)]);
}
for (let mutation of mutations) { const [el, prop, value] = mutation; el.style.setProperty(prop, value); }
</script>`;
  assert.deepEqual(collectAppearanceDisablingScripts(html), []);
});

test('a document with no script at all short-circuits to no rules', () => {
  assert.deepEqual(appearanceDisablingRules('<div id=container><progress></progress></div>'), []);
  assert.deepEqual(appearanceDisablingRules(''), []);
  assert.deepEqual(appearanceDisablingRules(null), []);
});

test('rules carry the marker declaration and the script selector', () => {
  assert.deepEqual(appearanceDisablingRules(generatedScript('background-color')), [
    { selector: '#container > *', props: { [APPEARANCE_DISABLED_MARKER]: '1' } },
  ]);
});

test('the disabling-property list is the spec list the WPT corpus enumerates', () => {
  // Spot-pins across every family in css-ui-4's list — physical, logical,
  // radius, border-image and background.
  for (const p of [
    'background-color', 'background-attachment', 'background-clip',
    'border-top-width', 'border-inline-start-style', 'border-block-end-color',
    'border-top-left-radius', 'border-end-end-radius',
    'border-image-source', 'border-image-outset',
  ]) assert.ok(APPEARANCE_DISABLING_PROPERTIES.has(p), p);
  // Properties the no-fallback test proves DO NOT disable the widget.
  for (const p of ['color', 'font-size', 'width', 'padding-top', 'box-shadow', 'outline-width']) {
    assert.ok(!APPEARANCE_DISABLING_PROPERTIES.has(p), p);
  }
});

// ── the widget-kind table ───────────────────────────────────────────────────

test('native widget kinds follow HTML §4.10 / §"widgets"', () => {
  assert.equal(nativeWidgetKind('button', {}), 'button');
  assert.equal(nativeWidgetKind('textarea', {}), 'textarea');
  assert.equal(nativeWidgetKind('meter', {}), 'meter');
  assert.equal(nativeWidgetKind('progress', {}), 'progress-bar');
  // A missing/empty `type` is Text (HTML §4.10.5.1).
  assert.equal(nativeWidgetKind('input', {}), 'textfield');
  assert.equal(nativeWidgetKind('input', { type: '' }), 'textfield');
  assert.equal(nativeWidgetKind('input', { type: 'SUBMIT' }), 'button');
  assert.equal(nativeWidgetKind('input', { type: 'range' }), 'slider');
  // `multiple` / `size > 1` is a list box; the bare form is a dropdown.
  assert.equal(nativeWidgetKind('select', { multiple: '' }), 'listbox');
  assert.equal(nativeWidgetKind('select', { size: '4' }), 'listbox');
  assert.equal(nativeWidgetKind('select', { size: '1' }), 'menulist');
  assert.equal(nativeWidgetKind('select', {}), 'menulist');
  // Not widgets / unmodelled — no kind, so no bake.
  assert.equal(nativeWidgetKind('a', { href: '' }), null);
  assert.equal(nativeWidgetKind('div', {}), null);
  assert.equal(nativeWidgetKind('option', {}), null);
  assert.equal(nativeWidgetKind('input', { type: 'file' }), null);
});

// ── the fallback table, row by row from the WPT references ──────────────────

test('fallback matches compute-kind-widget-fallback-all-elements-ref.html', () => {
  const f = (tag, attrs, declared) => widgetFallbackAppearance(tag, attrs, declared, true);
  // → appearance: none
  assert.equal(f('button', {}), 'none');
  assert.equal(f('input', { type: 'button' }), 'none');
  assert.equal(f('input', { type: 'submit' }), 'none');
  assert.equal(f('input', { type: 'reset' }), 'none');
  assert.equal(f('input', { type: 'text' }), 'none');
  assert.equal(f('input', { type: 'search' }), 'none');
  assert.equal(f('input', { type: 'color' }), 'none');
  assert.equal(f('textarea', {}), 'none');
  assert.equal(f('meter', {}), 'none');
  assert.equal(f('progress', {}), 'none');
  assert.equal(f('select', { multiple: '' }), 'none');
  // → appearance: menulist-button (the dropdown keeps its button chrome)
  assert.equal(f('select', {}), 'menulist-button');
  // → untouched (the ref carries no rule for these)
  assert.equal(f('input', { type: 'checkbox' }), null);
  assert.equal(f('input', { type: 'radio' }), null);
  assert.equal(f('input', { type: 'range' }), null);
  assert.equal(f('a', { href: '' }), null);
});

test('a declared appearance selects the KIND, it is not a veto', () => {
  // …-input-search-text-ref.html: the test declares `appearance: textfield`
  // on the search input and the reference STILL renders `appearance: none`.
  assert.equal(widgetFallbackAppearance('input', { type: 'search' }, 'textfield', true), 'none');
  // …-select-menulist-button-ref.html: menulist-button IS its own fallback.
  assert.equal(
    widgetFallbackAppearance('select', {}, 'menulist-button', true), 'menulist-button',
  );
  // `!important` and casing are stripped before the lookup.
  assert.equal(
    widgetFallbackAppearance('select', {}, ' MENULIST-BUTTON !important', true),
    'menulist-button',
  );
  // Already no widget → nothing left to disable.
  assert.equal(widgetFallbackAppearance('progress', {}, 'none', true), null);
  // `auto` means "the element's own native widget".
  assert.equal(widgetFallbackAppearance('progress', {}, 'auto', true), 'none');
  // An appearance keyword this table does not model → decline, never guess.
  assert.equal(widgetFallbackAppearance('select', {}, 'base-select', true), null);
});

test('no script hit ⇒ no bake, whatever the element is', () => {
  assert.equal(widgetFallbackAppearance('progress', {}, undefined, false), null);
  assert.equal(widgetFallbackAppearance('select', {}, undefined, false), null);
});
