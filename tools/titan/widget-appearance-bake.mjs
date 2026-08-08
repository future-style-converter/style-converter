// tools/titan/widget-appearance-bake.mjs — wave-36 THE WIDGET-APPEARANCE
// BAKE (the sixth bake).
//
// THE WALL: css-ui/compute-kind-widget-generated fails in one perfectly
// uniform block — 44 cells, ALL of them the `<progress>` family, ALL at
// ssim 0.9782 EXACTLY, all vetoed by coverage-ratio and nothing else,
// capture ink 0.544% vs ref 1.094% on every single one. Pixel-diffed
// (tools/titan/runs/wave35-webmap): our capture paints a 160×8 rounded
// blue bar at y=23..30; the frozen Chromium reference paints a 160×16
// square green/grey bar at y=19..34. Same width, exactly half the height,
// completely different colours — those are Chromium's two DIFFERENT
// progress renderings: the modern native widget chrome (appearance:auto)
// versus the fallback rendering (appearance:none). The 17 sibling widget
// families pass only because their native and fallback chrome happen to
// look nearly identical (button 0.9985, textarea 0.9999, listbox 1.000);
// `<progress>` is the one widget whose two renderings diverge visibly.
//
// WHY THE CAPTURE IS ON THE WRONG SIDE. Every test in the family is
// generated from ../tools/build-compute-kind-widget-fallback-props.py and
// carries the same script:
//
//     const elements = document.querySelectorAll('#container > *');
//     const props = "border-top-width".split(",");
//     for (const el of elements)
//       for (const prop of props)
//         el.style.setProperty(prop, getComputedStyle(el).getPropertyValue(prop));
//
// It re-declares each element's OWN UA-computed value at AUTHOR origin.
// The declared values are, by construction, byte-identical to what the UA
// already computed, so the mutation has exactly ONE observable effect: it
// makes the element carry author-origin appearance-disabling declarations,
// which per css-ui-4 §appearance-disabling-properties switches the widget
// to its fallback rendering. The static extractor does not execute
// scripts, so the emitted fixture's `<progress>` has an EMPTY property bag
// (verified: fixtures/wpt/css-ui/…-progress-border-top-width-001.json) and
// the harness renders native chrome.
//
// WHY THE BAKE IS SAFE — AND WHY IT IS SCRIPT-ONLY.
// This bake fires ONLY on that script idiom. It deliberately does NOT fire
// on appearance-disabling properties declared in the test's own CSS, and
// that is not an oversight: those declarations reach the capture DOM as
// real inline styles, so Chromium applies §appearance-disabling-properties
// to them ITSELF and baking an `appearance` on top would be a redundant
// second opinion with a corpus-wide blast radius. The script channel is
// the only one where the declaration is LOST, so it is the only one this
// module speaks for. Every fixture without the idiom is byte-identical by
// construction (`appearanceDisablingRules` returns [] and the caller
// short-circuits).
//
// THE FALLBACK TABLE IS PINNED BY THE WPT REFERENCES, NOT GUESSED.
// css-ui-4 leaves "which widgets are disabled" to the UA-ish prose; the
// generated family ships one reference per widget kind and those files ARE
// the contract. Read off tools/wpt/css/css-ui/compute-kind-widget-fallback-
// *-ref.html (and the grouped -all-elements-ref.html, which states all of
// them at once):
//
//   button, input[type=button|submit|reset], input[type=color],
//   input[type=text], input[type=search], textarea, meter, progress,
//   select[multiple]                                  → appearance: none
//   select (single, i.e. a menulist)                  → appearance: menulist-button
//   input[type=checkbox|radio|range]                  → UNCHANGED (native kept)
//   <a> and everything that is not a widget           → UNCHANGED
//
// Two entries in that table are non-obvious and both are ref-pinned:
//   • a `<select>` degrades to `menulist-button`, NOT to `none` — the
//     dropdown keeps its button chrome (…-select-dropdown-box-ref.html);
//   • an author `appearance: textfield` does NOT protect the widget — the
//     search-text ref (…-input-search-text-ref.html) still renders
//     `appearance: none` even though the test declares `textfield`. So the
//     bake resolves the KIND from the declared appearance and then applies
//     the fallback for that kind, rather than treating any author
//     `appearance` as a veto. The one keyword that IS its own fallback is
//     `menulist-button`.
//
// NO SILENT FALLTHROUGH: an element whose widget kind this module does not
// model (input[type=file|image|date|…], a future `appearance` keyword such
// as `base-select`) returns null — the fixture keeps the status quo ante
// and the caller emits nothing. Declining is visible in the fixture (no
// `appearance` key appears); inventing a fallback for a kind whose
// reference we have never seen would not be.

/**
 * css-ui-4 §appearance-disabling-properties. Copied VERBATIM from the
 * `fallbackProps` set in tools/wpt/css/css-ui/compute-kind-widget-no-
 * fallback-props-001.html — that test enumerates the spec list in order to
 * assert that every property OUTSIDE it leaves the widget alone, so the
 * WPT corpus itself is the source of truth for this table.
 */
export const APPEARANCE_DISABLING_PROPERTIES = new Set([
  'background-color',
  'border-top-color', 'border-top-style', 'border-top-width',
  'border-right-color', 'border-right-style', 'border-right-width',
  'border-bottom-color', 'border-bottom-style', 'border-bottom-width',
  'border-left-color', 'border-left-style', 'border-left-width',
  'border-block-start-color', 'border-block-end-color',
  'border-inline-start-color', 'border-inline-end-color',
  'border-block-start-style', 'border-block-end-style',
  'border-inline-start-style', 'border-inline-end-style',
  'border-block-start-width', 'border-block-end-width',
  'border-inline-start-width', 'border-inline-end-width',
  'background-image', 'background-attachment',
  'background-position', 'background-position-x', 'background-position-y',
  'background-clip', 'background-origin', 'background-size',
  'border-image-source', 'border-image-slice', 'border-image-width',
  'border-image-outset', 'border-image-repeat',
  'border-top-left-radius', 'border-top-right-radius',
  'border-bottom-right-radius', 'border-bottom-left-radius',
  'border-start-start-radius', 'border-start-end-radius',
  'border-end-start-radius', 'border-end-end-radius',
]);

/**
 * The marker declaration the synthetic rules carry. It never reaches a
 * fixture: `appearanceDisablingRules`'s output is matched through a
 * SEPARATE propsForElement call whose `matchedRules` count is the only
 * thing read, so the marker cannot leak into any element's property bag.
 */
export const APPEARANCE_DISABLED_MARKER = '-sc-appearance-disabled';

// ── The generated-script recogniser ─────────────────────────────────────────

/** Every inline `<script>` body in the document. */
const SCRIPT_RE = /<script\b[^>]*>([\s\S]*?)<\/script[^>]*>/gi;

/** `document.querySelectorAll('<selector>')` — the mutated element set. */
const QSA_RE = /document\s*\.\s*querySelectorAll\s*\(\s*(['"])([^'"]*)\1\s*\)/;

/** `"<a,b,c>".split(",")` — the property list the loop walks. */
const PROP_LIST_RE = /(['"])([^'"]*)\1\s*\.\s*split\s*\(\s*(['"]),\3\s*\)/;

/**
 * `<el>.style.setProperty(<p>, getComputedStyle(<el>).getPropertyValue(<p>))`
 * — the "re-declare the UA value at author origin" idiom. The back-
 * reference on the property identifier (`\2` … `\2`) is what makes this
 * tight: a loop that sets a property to some OTHER value is not this
 * pattern and must not be baked as a no-op re-declaration.
 */
const SELF_ASSIGN_RE =
  /\.\s*style\s*\.\s*setProperty\s*\(\s*([A-Za-z_$][\w$]*)\s*,\s*getComputedStyle\s*\([^)]*\)\s*\.\s*getPropertyValue\s*\(\s*\1\s*\)\s*\)/;

/**
 * Recognise the generated appearance-disabling script(s) in a document.
 *
 * Returns one entry per qualifying `<script>` body:
 *   `{ selector, props }` — the querySelectorAll selector whose matches are
 *   mutated, and the appearance-disabling subset of the mutated properties.
 *
 * Requires ALL FOUR signals in the same script body (querySelectorAll +
 * a quoted `.split(",")` property list + the self-assigning setProperty
 * call + a non-empty intersection with APPEARANCE_DISABLING_PROPERTIES), so
 * a script that merely contains one of them cannot trigger the bake. The
 * sibling test compute-kind-widget-no-fallback-props-001.html — which
 * mutates every property EXCEPT the disabling ones, via a different
 * `mutations.push(...)` idiom — is rejected on both the property-list and
 * the setProperty signal, which is exactly right: its widgets must keep
 * their native appearance.
 *
 * Exported for unit testing.
 */
export function collectAppearanceDisablingScripts(html) {
  const out = [];
  if (!html) return out;
  SCRIPT_RE.lastIndex = 0;
  let m;
  while ((m = SCRIPT_RE.exec(html)) !== null) {
    const body = m[1];
    // Signal 1: the self-assignment. Cheapest discriminator, checked first.
    if (!SELF_ASSIGN_RE.test(body)) continue;
    // Signal 2: the element set. Without a selector we have no idea WHICH
    // elements were mutated, and guessing "all of them" would be a fiction.
    const qsa = QSA_RE.exec(body);
    if (!qsa) continue;
    const selector = qsa[2].trim();
    if (!selector) continue;
    // Signal 3: the property list.
    const list = PROP_LIST_RE.exec(body);
    if (!list) continue;
    // Signal 4: at least one of the listed properties actually disables
    // native appearance. A script re-declaring only, say, `color` changes
    // nothing about the widget and must not produce a bake.
    const props = list[2]
      .split(',')
      .map((p) => p.trim().toLowerCase())
      .filter((p) => APPEARANCE_DISABLING_PROPERTIES.has(p));
    if (props.length === 0) continue;
    out.push({ selector, props });
  }
  return out;
}

/**
 * The recognised scripts as synthetic CSS rules, in the `{ selector, props }`
 * shape parseCss() emits — so the caller can run them through the SAME
 * selector matcher the real stylesheet uses (specificity, combinators,
 * `:nth-child`, the whole machinery) instead of reimplementing selector
 * matching here. Returns [] for the overwhelming majority of the corpus,
 * which is the caller's short-circuit.
 */
export function appearanceDisablingRules(html) {
  return collectAppearanceDisablingScripts(html).map(({ selector }) => ({
    selector,
    props: { [APPEARANCE_DISABLED_MARKER]: '1' },
  }));
}

// ── The widget-kind table ───────────────────────────────────────────────────

/**
 * `<input type>` → widget kind. HTML §4.10.5 states the control each type
 * renders; the kind names below are the css-ui-4 `appearance` keywords for
 * those controls so that `appearanceKeywordKind` and this function speak
 * one vocabulary.
 *
 * Text-ish types (email/tel/url/password/number) share the text-field
 * widget with `text` per HTML §4.10.5.1.x. Types with no reference in the
 * generated family and no unambiguous kind (file, image, date/time family,
 * hidden) are deliberately absent — see the module banner's no-silent-
 * fallthrough clause.
 */
const INPUT_TYPE_KINDS = new Map([
  ['button', 'button'], ['submit', 'button'], ['reset', 'button'],
  ['color', 'color-well'],
  ['checkbox', 'checkbox'], ['radio', 'radio'], ['range', 'slider'],
  ['search', 'searchfield'],
  ['text', 'textfield'], ['email', 'textfield'], ['tel', 'textfield'],
  ['url', 'textfield'], ['password', 'textfield'], ['number', 'textfield'],
]);

/**
 * `appearance` keyword → widget kind, for the case where the author
 * DECLARED a non-auto appearance and thereby chose the widget themselves
 * (css-ui-4 §appearance-switching: a non-auto, non-none value selects the
 * primitive widget directly). Unknown keywords map to null so the bake
 * declines rather than inventing a fallback.
 */
const APPEARANCE_KEYWORD_KINDS = new Map([
  ['button', 'button'], ['push-button', 'button'], ['square-button', 'button'],
  ['checkbox', 'checkbox'], ['radio', 'radio'],
  ['slider-horizontal', 'slider'], ['slider-vertical', 'slider'],
  ['textfield', 'textfield'], ['searchfield', 'searchfield'],
  ['textarea', 'textarea'],
  ['listbox', 'listbox'], ['menulist', 'menulist'],
  ['menulist-button', 'menulist-button'],
  ['meter', 'meter'], ['progress-bar', 'progress-bar'],
]);

/**
 * Widget kinds whose NATIVE rendering survives author-origin appearance-
 * disabling declarations. Pinned by the three references that carry no
 * `appearance` rule at all (…-checkbox-input-ref.html,
 * …-radio-input-ref.html, …-range-ref.html) and confirmed by the grouped
 * all-elements reference, which lists every OTHER widget and omits these.
 */
const NATIVE_PRESERVING_KINDS = new Set(['checkbox', 'radio', 'slider']);

/**
 * Widget kinds that degrade to `menulist-button` rather than to `none`:
 * a styled `<select>` keeps its dropdown button chrome
 * (…-select-dropdown-box-ref.html / …-select-menulist-button-ref.html).
 */
const MENULIST_KINDS = new Set(['menulist', 'menulist-button']);

/**
 * The element's NATIVE widget kind from its tag + attributes (HTML
 * §"widgets"), or null when the element renders no widget at all
 * (`<a>`, `<div>`, `<option>`, …). Exported for unit testing.
 */
export function nativeWidgetKind(tag, attrs) {
  const a = attrs ?? {};
  switch (tag) {
    case 'button':
      return 'button';
    case 'textarea':
      return 'textarea';
    case 'meter':
      return 'meter';
    case 'progress':
      return 'progress-bar';
    case 'input': {
      // HTML §4.10.5.1: a missing/invalid `type` falls back to Text.
      const raw = typeof a.type === 'string' ? a.type.trim().toLowerCase() : '';
      const type = raw === '' ? 'text' : raw;
      return INPUT_TYPE_KINDS.get(type) ?? null;
    }
    case 'select':
      // HTML §4.10.7: `multiple`, or `size` greater than 1, renders a list
      // box; anything else is the dropdown (menulist).
      if (a.multiple !== undefined) return 'listbox';
      if (a.size !== undefined && Number(a.size) > 1) return 'listbox';
      return 'menulist';
    default:
      return null;
  }
}

/** Normalise a raw `appearance` declaration value for comparison. */
function normaliseAppearance(value) {
  if (typeof value !== 'string') return null;
  const v = value.replace(/\s*!important\s*$/i, '').trim().toLowerCase();
  return v === '' ? null : v;
}

/**
 * The `appearance` value to bake onto an element whose author-origin
 * appearance-disabling declarations arrived through the recognised script,
 * or null to leave the element exactly as extracted.
 *
 * @param tag              resolved element name (lower-case).
 * @param attrs            the element's source attributes.
 * @param declaredAppearance the element's own resolved `appearance`
 *                         declaration (matched rules + inline style), if any.
 * @param disabled         did the recognised script mutate this element?
 *
 * Exported for unit testing.
 */
export function widgetFallbackAppearance(tag, attrs, declaredAppearance, disabled) {
  // The bake has exactly one trigger (see the banner): the script channel.
  if (!disabled) return null;
  const declared = normaliseAppearance(declaredAppearance);
  // `appearance: none` already means "no widget" — there is nothing left to
  // disable, and rewriting it would be noise in the fixture bytes.
  if (declared === 'none') return null;
  // css-ui-4 §appearance-switching: a declared non-auto value picks the
  // primitive widget; `auto` (and no declaration) means the element's own
  // native widget. Both paths land on one kind vocabulary.
  const kind = declared && declared !== 'auto'
    ? APPEARANCE_KEYWORD_KINDS.get(declared) ?? null
    : nativeWidgetKind(tag, attrs);
  // Not a widget, or a kind whose reference rendering we have never seen —
  // decline (banner: no silent fallthrough, and no invented fallback).
  if (!kind) return null;
  // Checkbox / radio / slider keep their native chrome.
  if (NATIVE_PRESERVING_KINDS.has(kind)) return null;
  // A dropdown degrades to its button form, not to bare fallback.
  if (MENULIST_KINDS.has(kind)) return 'menulist-button';
  // Every remaining widget kind falls back to no widget at all.
  return 'none';
}
