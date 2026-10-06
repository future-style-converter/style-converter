// ListStyleTypeApplier.ts — emits { listStyleType }.  MDN: list-style-type.
//
// Wave 52 (lane L6, T3) adds ONE narrow exception to the wave-27 rule that
// the web runtime "deliberately does NOT read `meta.markerText` — it
// renders a real <ol> and Blink's own ::marker is the better oracle"
// (tools/titan/counter-style-bake.mjs header). That premise is FALSIFIED on
// two measured shapes, and only those two take the baked string:
//
//  1. RANGE-LIMITED ADDITIVE styles (`armenian`, `upper-armenian`,
//     `lower-armenian`, `georgian`, `hebrew`). css-counter-styles-3 §6.2
//     gives them a finite `range` with `fallback: decimal`, so armenian
//     10000 MUST paint "10000." — the Chrome-151 ref does. The capture
//     browser (puppeteer 25.4.0's Chrome for Testing) instead painted a
//     legacy non-fallback string with a glyph the capture font lacks:
//     wave51-fix css-counter-styles/armenian/css3-counter-styles-008
//     `web f 0.9435`, rows 2–3 are 28 px tall with a 9×28 tofu box at
//     x220–228 (tools/titan/runs/wave51-fix/sections/css-counter-styles/
//     screenshots/…008.png) against the ref's `10000.` + wrapped item text
//     (6 ink bands, not 5). The wire already carries the correct baked
//     `meta.markerText` "10000." — both natives prefer it; web now does too,
//     for exactly these five keywords.
//  2. AUTHOR `@counter-style` NAMES. The at-rule never reaches the IR wire,
//     so `list-style-type: foo` reaches Chrome with `foo` undefined, and
//     css-counter-styles-3 §2 says an undefined name "behaves as decimal"
//     — "1. 2. 3." where the ref paints "001. 002. 003." (wave51-fix
//     css-counter-styles/cssom/cssom-pad-setter-invalid `web f 0.9821`).
//     The bake (T7, same lane) now resolves author rules and stamps the
//     string; a keyword that is NOT one of the §6/§7 predefined names and
//     arrives with a baked marker is that shape, and the baked string is
//     strictly better than Chrome's decimal guess.
//
// The emission is the css-lists-3 §3.3 `<string>` form of
// `list-style-type` — `"10000. "` — which Chrome renders as the marker's
// text while keeping `list-style-position` and the UA `::marker` style
// (the natives' 4 px gap is spelled here as the trailing space, because a
// `<string>` marker has NO suffix or padding of its own).
//
// ONE refinement, measured (bakedMarkerPlan below): an `inside` marker on
// a CHILDLESS item is emitted as a leading inline text box with
// `list-style-type: none` instead. The UA sheet gives `::marker`
// `font-variant-numeric: tabular-nums` (css-lists-3 Appendix A), so a
// `<string>` "10000. " lays its digits out TABULAR — wider than the ref's
// literal `<bdi>10000. </bdi>` text (proportional). Replayed in the capture
// browser (tools/titan/results/wave52-counters-and-lists/png-replay.mjs,
// replay/replay.json): armenian-008 with the `<string>` form paints the
// marker at x 219–301 (ref 217–293) and wraps row 3 (`10001.` / `10001` —
// 7 ink bands, the ref has 6), plain SSIM 0.947; the inline form is the
// ref's own markup. An inside marker IS the item's first inline box
// (css-lists-3 §3.5), so for an item with no children of its own the
// inline box is the same layout; items WITH children keep the `<string>`
// (a block child would move a synthetic box onto its own line).
//
// Every OTHER keyword keeps the wave-27 rule byte-for-byte: Chrome's own
// counter tables are the oracle for decimal / roman / alpha / the numeric
// digit families / the §7 East Asian styles / bullets, and the
// ordinal-only `start` forwarding (renderer/WidgetAttrs.ts) stays as is.
import type { CSSProperties } from 'react';
import type { ListStyleTypeConfig } from './ListStyleTypeConfig';

export function applyListStyleType(c: ListStyleTypeConfig): CSSProperties {
  return c.value === undefined ? {} : { listStyleType: c.value } as CSSProperties;
}

/** Shape 1 — the five §6.2 additive styles with a finite `range`. */
export const RANGE_LIMITED_ADDITIVE_STYLES: ReadonlySet<string> = new Set([
  'armenian', 'upper-armenian', 'lower-armenian', 'georgian', 'hebrew',
]);

/** Every counter-style KEYWORD css-counter-styles-3 predefines (§6.1
 *  bullets, §6.2 numeric + alphabetic + additive, §7.1 East Asian, §7.2
 *  ethiopic) plus `none`. A `list-style-type` value outside this set that
 *  is not a `<string>` and not a `symbols()` function is an AUTHOR name
 *  (shape 2). Kept complete on purpose: a predefined keyword mistaken for
 *  an author name would override Chrome's correct table with the bake's. */
export const PREDEFINED_COUNTER_STYLE_KEYWORDS: ReadonlySet<string> = new Set([
  'none', 'disc', 'circle', 'square', 'disclosure-open', 'disclosure-closed',
  'decimal', 'decimal-leading-zero', 'arabic-indic', 'armenian', 'upper-armenian',
  'lower-armenian', 'bengali', 'cambodian', 'khmer', 'cjk-decimal', 'devanagari',
  'georgian', 'gujarati', 'gurmukhi', 'hebrew', 'kannada', 'lao', 'malayalam',
  'mongolian', 'myanmar', 'oriya', 'persian', 'lower-roman', 'upper-roman', 'tamil',
  'telugu', 'thai', 'tibetan', 'lower-alpha', 'lower-latin', 'upper-alpha',
  'upper-latin', 'lower-greek', 'hiragana', 'hiragana-iroha', 'katakana',
  'katakana-iroha', 'japanese-informal', 'japanese-formal', 'korean-hangul-formal',
  'korean-hanja-informal', 'korean-hanja-formal', 'simp-chinese-informal',
  'simp-chinese-formal', 'trad-chinese-informal', 'trad-chinese-formal',
  'cjk-earthly-branch', 'cjk-heavenly-stem', 'ethiopic-numeric',
]);

/** Quote `s` as a CSS `<string>` token (css-syntax-3 §4.3.5: backslash and
 *  the delimiting quote are the only characters that need escaping). */
function cssStringLiteral(s: string): string {
  return `"${s.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

/**
 * The `list-style-type` a list item should emit when the wire carries a
 * baked `meta.markerText`, or `undefined` to keep the keyword.
 *
 * @param effectiveType the item's computed `list-style-type` keyword — its
 *   own declaration, else the value inherited from its list container
 *   (css-lists-3 §3.1 "Inherited: yes"; the renderer threads it down
 *   because the flat wire has no parent edge). `undefined` = nothing
 *   declared anywhere = the UA default, which Chrome resolves itself.
 * @param markerText the wire's `meta.markerText` (absent ⇒ keep keyword).
 * @returns the css-lists-3 §3.3 `<string>` form — the baked marker plus
 *   ONE trailing space (the natives' ListMarkerRow gap) — for the two
 *   shapes in the file header; `undefined` for everything else.
 */
export function bakedMarkerListStyleType(
  effectiveType: string | undefined,
  markerText: string | null | undefined,
): string | undefined {
  // No baked marker ⇒ nothing to say; Chrome synthesises ::marker itself.
  if (typeof markerText !== 'string' || markerText.length === 0) return undefined;
  // Nothing declared ⇒ the UA default (decimal under <ol>, disc under
  // <ul>) — an ordinal-only case the forwarded `start` already covers.
  if (typeof effectiveType !== 'string' || effectiveType.trim() === '') return undefined;
  const kw = effectiveType.trim().toLowerCase();
  // Already a `<string>` or a `symbols()` function: the author spelled the
  // marker text in CSS; Chrome honours it verbatim, so the bake stays out.
  if (kw.startsWith('"') || kw.startsWith("'") || kw.startsWith('symbols(')) return undefined;
  // Shape 1: finite-range additive styles whose fallback the capture
  // browser got wrong (armenian-008, measured).
  if (RANGE_LIMITED_ADDITIVE_STYLES.has(kw)) return cssStringLiteral(`${markerText} `);
  // Shape 2: an author `@counter-style` name the wire could not carry —
  // Chrome would otherwise fall back to decimal (§2, undefined name).
  if (!PREDEFINED_COUNTER_STYLE_KEYWORDS.has(kw)) return cssStringLiteral(`${markerText} `);
  // Every predefined keyword: Chrome's own table is the oracle (wave 27).
  return undefined;
}

/** What the renderer does with a baked marker: the `listStyleType` it
 *  emits, plus — for the inline form only — the text of the leading
 *  inline box that replaces `::marker` (see the file header). */
export interface BakedMarkerPlan {
  /** `'"10000. "'` (the `<string>` form) or `'none'` (the inline form). */
  listStyleType: string;
  /** The leading inline box's text — the baked marker plus ONE space, the
   *  ref's own `<bdi>10000. </bdi>` spelling. Absent on the `<string>` form. */
  inlineMarkerText?: string;
}

/**
 * The full T3 decision for one list item: WHETHER the baked marker applies
 * (bakedMarkerListStyleType's two shapes) and, if so, in WHICH form.
 *
 * @param effectivePosition the item's computed `list-style-position`
 *   (inherited like the type — css-lists-3 §3.1); `undefined` = the
 *   initial value `outside`.
 * @param hasChildren whether the item composes children of its own — only
 *   a childless item takes the inline form (header: a synthetic inline box
 *   before a BLOCK child would sit on a line of its own, where Chrome's
 *   inside ::marker joins the child's first line).
 * @returns `undefined` to keep the keyword (Chrome's ::marker decides).
 */
export function bakedMarkerPlan(
  effectiveType: string | undefined,
  effectivePosition: string | undefined,
  markerText: string | null | undefined,
  hasChildren: boolean,
): BakedMarkerPlan | undefined {
  // The shape gate is the one rule above — one place decides WHETHER.
  const stringForm = bakedMarkerListStyleType(effectiveType, markerText);
  if (stringForm === undefined) return undefined;
  // css-lists-3 §3.5: an `inside` marker is the item's first inline box —
  // for a childless item a real leading text box is that layout, minus the
  // ::marker's tabular digits the ref does not have (header, measured).
  if (typeof effectivePosition === 'string' && effectivePosition.trim().toLowerCase() === 'inside' && !hasChildren) {
    return { listStyleType: 'none', inlineMarkerText: `${markerText} ` };
  }
  // `outside` (or the initial value): the marker must hang outside the
  // principal box, which only a real ::marker does — the `<string>` form.
  return { listStyleType: stringForm };
}
