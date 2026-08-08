// AppearanceExtractor.ts — IR `Appearance` → AppearanceConfig.
//
// Mirrors the parser's value flavors exactly — every arm of `AppearanceValue`
// in converter/src/main/kotlin/app/irmodels/properties/appearance/
// AppearanceProperty.kt, which AppearancePropertyParser.kt fills:
//
//   16 TAG-ONLY widget keywords  → {"type":"<keyword>"}   (none, auto,
//     button, checkbox, listbox, menulist, menulist-button, meter,
//     progress-bar, push-button, radio, searchfield, slider-horizontal,
//     square-button, textarea, textfield)
//   CSS-wide keyword             → {"type":"keyword","keyword":"inherit"}
//   var()/env() and anything the parser did not recognise
//                                → {"type":"raw","value":"var(--x)"}
//
// Why this is NOT the shared `keywordOrRaw` fold any more (wave-36 lane M2):
// that helper's tag-only branch admits exactly three names — none/auto/normal
// — so FOURTEEN of the sixteen widget keywords returned `undefined` and were
// dropped without a trace. `appearance: menulist-button` (the fallback a
// styled `<select>` degrades to per css-ui-4 §appearance-switching, and the
// value the css-ui compute-kind-widget references pin) never reached the
// DOM. It also ran `raw` payloads through `kebab()`, which lower-cases —
// silently corrupting the case-sensitive custom-property name in
// `var(--Foo)`.

import { foldLast, kebab, type IRPropertyLike } from '../_phase10_shared';
import { logUnhandled } from '../PropertyTracker';
import type { AppearanceConfig } from './AppearanceConfig';

/**
 * The tag-only variants: the serial name IS the CSS keyword, so the set is
 * the whole translation. Kept as an explicit allow-list (rather than
 * "anything that is not keyword/raw") so a future parser variant lands in
 * the tracked default arm instead of being echoed into the DOM unchecked.
 */
const WIDGET_KEYWORDS: ReadonlySet<string> = new Set([
  'none', 'auto',
  'button', 'checkbox', 'listbox', 'menulist', 'menulist-button',
  'meter', 'progress-bar', 'push-button', 'radio', 'searchfield',
  'slider-horizontal', 'square-button', 'textarea', 'textfield',
]);

/** One IR `Appearance` payload → the CSS token, or undefined. */
function appearanceToken(data: unknown): string | undefined {
  // A bare string payload (hand-written fixture / flattened wire) is already
  // the keyword.
  if (typeof data === 'string') return kebab(data);
  if (data === null || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  const tag = typeof o.type === 'string' ? o.type.toLowerCase() : '';
  if (WIDGET_KEYWORDS.has(tag)) return tag;
  if (tag === 'keyword') {
    // CSS-wide keyword (inherit/initial/unset/revert…) — already lower-case
    // out of the parser, which normalises before matching.
    return typeof o.keyword === 'string' ? o.keyword.toLowerCase() : undefined;
  }
  if (tag === 'raw') {
    // VERBATIM: a raw payload is an unparsed value such as `var(--Foo)`
    // whose custom-property name is case-sensitive (css-variables-1 §2).
    return typeof o.value === 'string' && o.value.trim() !== ''
      ? o.value.trim()
      : undefined;
  }
  // A variant the parser grew without this extractor — loud, per the
  // PropertyRegistry introspection contract.
  logUnhandled('Appearance', tag || 'unknown-shape');
  return undefined;
}

export function extractAppearance(properties: IRPropertyLike[]): AppearanceConfig {
  // Last write wins, matching the cascade every other engine phase uses.
  return { value: foldLast(properties, 'Appearance', appearanceToken) };
}
