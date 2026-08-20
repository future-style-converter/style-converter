/**
 * WidgetAttrs — the production `meta.attrs` → DOM-prop policy for the
 * package renderer (wave-20 lane W1).
 *
 * The wire (schema/ir-v2.schema.json `meta.attrs`) carries the widget-
 * identity attributes the WPT extractor found in the source markup:
 * present-in-source keys only, among {type, value, checked, multiple,
 * size, alt, min, max, selected, disabled}; booleans are presence-`true`,
 * min/max — and value on meter/progress — are numbers where numeric,
 * everything else verbatim strings. This module maps that object onto
 * React DOM props so a `<input type=checkbox checked>` on the wire paints
 * a real checked checkbox — in production SDUI (trusted wire, native
 * semantics wanted — the TagMapping.defaultMapTag philosophy) and in the
 * harness's WPT capture mode alike.
 *
 * Application is keyed on the RESOLVED element name, not the sourceTag:
 * when a skin demotes a widget tag to <div> (the non-WPT capture harness),
 * the attrs are deliberately NOT applied — a `type` attribute on a <div>
 * is inert noise and would perturb the byte-stable legacy capture DOM.
 */

// No-silent-fallthrough logging (CLAUDE.md): an attrs key this policy
// doesn't recognise is recorded via the tracker, never dropped silently.
import { logUnhandled } from '../engine/PropertyTracker';

/**
 * The form/widget tags whose chrome is attribute-dependent — byte-parallel
 * with the extractor's WIDGET_ATTR_TAGS (tools/titan/extract-fixture.mjs):
 * the wire only ever carries `meta.attrs` for these sourceTags, and the
 * renderer only ever applies attrs onto these element names.
 */
export const WIDGET_TAGS: ReadonlySet<string> = new Set([
  'a', 'button', 'input', 'textarea', 'select', 'option', 'meter', 'progress',
]);

/**
 * wave-27 lane CBAKE — the LIST-ORDINAL tags, a lane DISJOINT from
 * WIDGET_TAGS. `<ol start>` (HTML §4.4.5) and `<li value>` (§4.4.8) are
 * the counter origin the browser needs to number a list; without them
 * every captured `<ol start='1860'>` painted "1." while the reference
 * painted "1860.". Byte-parallel with the extractor's LIST_ATTR_TAGS.
 *
 * Kept SEPARATE from WIDGET_TAGS on purpose: that set is not merely an
 * attribute allow-list — the harness's ComponentRenderer also keys tag
 * passthrough, the `inert` + `tabIndex:-1` decoration, and an
 * unconditional inter-sibling ' ' separator off it. Folding `ol` in would
 * have injected that separator between every adjacent pair of lists in
 * the corpus. Only `widgetDomProps` consults this set.
 */
export const LIST_ATTR_TAGS: ReadonlySet<string> = new Set(['ol', 'li']);

/**
 * The button-like <input> type states (HTML §4.10.5.1.20-22: Submit
 * Button / Reset Button / Button) — the states whose `value` IS the
 * rendered label, not editable form text. React 19's initInput
 * (react-dom-client.development.js:1707-1716) early-returns for
 * type=submit/reset when no `value` PROP exists, so a wire value mapped
 * to `defaultValue` never reaches the mounted DOM node and the capture
 * races the mount state — the button paints the UA fallback label
 * ('Submit' ≈40px instead of 'input-submit' ≈77px intrinsic width),
 * shifting every wrap point in the inline widget flow. These types map
 * to the CONTROLLED `value` prop instead (see widgetDomProps).
 */
const BUTTON_LIKE_INPUT_TYPES: ReadonlySet<string> = new Set(['submit', 'reset', 'button']);

/**
 * Stable no-op change handler for the controlled button-like value.
 * A button-like input's value never mutates (the label is not editable
 * form state), but React's controlled-input advisory demands an
 * onChange (or readOnly) whenever `value` is set — the noop satisfies
 * it without serializing any attribute into the capture DOM (readOnly
 * would add `readonly=""` bytes; a handler adds none).
 */
const NOOP_ON_CHANGE = (): void => {};

/**
 * Map one wire attrs object onto React DOM props for the resolved element.
 *
 * @param elementName the element ACTUALLY being rendered (post-mapTag).
 * @param attrs `component.meta.attrs` from the wire (or null/undefined).
 * @returns React props to merge into the element (empty when the element
 *          is not a widget tag or the wire carries no attrs).
 */
export function widgetDomProps(
  elementName: string,
  attrs: Record<string, string | number | boolean> | null | undefined,
): Record<string, unknown> {
  // No wire attrs, or the skin resolved a non-widget element (e.g. the
  // legacy-capture <div> demotion) → nothing to apply, byte-stable DOM.
  if (!attrs || !(WIDGET_TAGS.has(elementName) || LIST_ATTR_TAGS.has(elementName))) return {};
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(attrs)) {
    switch (key) {
      case 'checked':
        // Uncontrolled initial state (React `defaultChecked`): the wire is
        // a snapshot, not live form state — no onChange contract needed.
        out.defaultChecked = value === true;
        break;
      case 'value':
        if (elementName === 'input'
            && typeof attrs.type === 'string'
            && BUTTON_LIKE_INPUT_TYPES.has(attrs.type.toLowerCase())) {
          // Button-like inputs: the value is the painted LABEL (HTML
          // §4.10.5.1.20-22), never mutable form text. React 19's
          // initInput skips `defaultValue` application for submit/reset
          // when no `value` prop exists (see BUTTON_LIKE_INPUT_TYPES),
          // so ONLY the controlled prop reliably paints the source
          // label; the noop onChange satisfies React's controlled-input
          // contract without perturbing the DOM bytes.
          out.value = value;
          out.onChange = NOOP_ON_CHANGE;
        } else if (elementName === 'input' || elementName === 'textarea') {
          // Uncontrolled rule for text-carrying value (HTML `value`
          // is the INITIAL value; React's controlled `value` would demand
          // an onChange handler the renderer has no business supplying).
          out.defaultValue = value;
        } else {
          // meter/progress: float-valued display attributes (HTML
          // §4.10.13/14) — not form state, React accepts `value` plainly.
          // option/button: the plain `value` content attribute.
          // li (wave-27): the ordinal override of HTML §4.4.8 — the wire
          // carries it as a verbatim string and the browser re-parses it,
          // so no numeric coercion happens on this lane.
          out.value = value;
        }
        break;
      case 'selected':
        // <option selected> — React logs an advisory ("use defaultValue on
        // <select>") but still serializes the attribute; the renderer keeps
        // the per-option truth because the wire has no select-level value
        // model to translate it into. Documented, not silent.
        out.selected = value === true;
        break;
      case 'reversed':
        // wave-45 lane X4: `<ol reversed>` (HTML §4.4.5) — the countdown
        // switch for the list-item counter (css-lists-3 §4.4.2 reversed
        // instantiation). The extractor has carried it since wave-44
        // (extract-fixture.mjs LIST_ATTR_KEYS + LIST_BOOLEAN_ATTR_KEYS:
        // presence-`true` on the wire, exactly like `checked`), but this
        // policy dropped it to the tracker, so the browser numbered every
        // reversed list FORWARD (wave-44 counter-list-item capture: markers
        // 1./2./3. where the ref paints 3./2./1.). React DOM serializes the
        // boolean `reversed` prop as the bare attribute, and the capture
        // browser then counts down natively — including the §4.4.5
        // interaction with `start` and `<li value>` overrides, which the
        // renderer could not hope to re-derive itself.
        out.reversed = value === true;
        break;
      // Plain passthrough attributes — same name in React DOM.
      case 'type':      // input chrome selector (§4.10.5) / button type
      case 'multiple':  // select listbox-vs-menulist switch (§4.10.7)
      case 'size':      // select rows / input width in characters
      case 'alt':       // input[type=image] fallback text
      case 'min':       // range/meter/progress lower bound
      case 'max':       // range/meter/progress upper bound
      case 'disabled':  // grayed widget chrome + inertness
      case 'start':     // wave-27: <ol> counter origin (HTML §4.4.5)
        out[key] = value;
        break;
      default:
        // A key outside the frozen wire contract — future schema revision
        // or producer bug. Track loudly; never guess a mapping.
        logUnhandled('WidgetAttr', `${elementName}[${key}]`);
    }
  }
  return out;
}
