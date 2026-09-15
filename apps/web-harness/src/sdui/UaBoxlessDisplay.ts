// UaBoxlessDisplay.ts — wave-50 lane B5 seam.
//
// THE DEFECT. The harness maps every `meta.sourceTag` outside TAG_ALLOWLIST
// to a plain `<div>` (ComponentRenderer's divergence #3), and a `<div>` with
// no declared `display` is a BLOCK box. For most demoted tags that is
// harmless — they were block-level anyway. For the SIX tags below it is not,
// and for two DIFFERENT reasons. (This banner used to claim all of them were
// "css-display-3 §2.4 INTERNAL layout values"; that is wrong for one of them
// — skeptic S5 defect 6, corrected by wave-50 fix lane F6. Behaviour
// unchanged: the map and the hook are untouched.)
//   • `colgroup`, `col`, `rb`, `rt`, `rtc` DO take a css-display-3 §2.4
//     layout-internal display type — `table-column-group`, `table-column`,
//     `ruby-base`, `ruby-text`, `ruby-text-container` are five of the twelve
//     values §2.4 lists (the table-row-group…table-caption set plus the four
//     ruby-internal ones). None of them generates a block box of its own.
//   • `ruby` does NOT. `ruby` is a DISPLAY-INSIDE value (css-display-3 §2.2,
//     the inner display layout models), so `display: ruby` produces an
//     INLINE-LEVEL ruby container box — a real box, just not a block one, and
//     not an internal display type. It is in this map for the same practical
//     reason as the others: the UA rule the browser applies is
//     `ruby { display: ruby }`, from the HTML Living Standard's Rendering
//     section under the heading "Ruby annotations", and the harness's `<div>`
//     demotion replaces that inline-level box with a block one.
// Either way the demoted box invents geometry the reference never had.
//
// WHY IT MATTERS MOST IN A VERTICAL WRITING MODE. A spurious block box adds
// its extent along the BLOCK axis, and css-writing-modes-4 §3 turns the
// block axis HORIZONTAL in `vertical-rl`/`vertical-lr`/`sideways-*`. So each
// invented box widens the element physically, and a shrink-to-fit box (a
// float, css-writing-modes-4 §7.3) then clamps at its containing block's
// inline size and stops sharing a row.
//
// MEASURED (wave-50 B5, headless Chrome on the harness's own composed DOM,
// injected as XHTML so the parser's table foster-parenting cannot move
// nodes; per-box targets from the same WPT page under capture-browser-ref's
// frame CSS). css-writing-modes/direction-upright-002, composed canvas
// height / first float's border box:
//   as shipped                                   3045 px / 364x230
//   + colgroup/col at their UA display           3042 px / 330x266
//   + ruby/rt at their UA display                2769 px / 308x230
//   + both (this module)                         2766 px / 192x266
//   + both AND the extractor's 100x100 `<col>`
//     placeholder removed (seam S2, deferred)    1864 px / 192x184
//   Chromium ref (the frozen raster's source)     954 px /  98x150
// Web pixel mismatch vs the frozen ref (±8/channel), base -> with this
// module, over every wave49-final test that carries one of these tags:
//   CSS2/borders/border-conflict-style-107     39.276% -> 10.431%  BETTER
//   css-writing-modes/ch-units-vrl-003         11.497% -> 10.102%  BETTER
//   css-writing-modes/ch-units-vrl-007         17.226% -> 16.228%  BETTER
//   css-tables/border-collapse-dynamic-col-001  0.515% ->  0.515%  flat (0 px moved)
//   css-backgrounds/…-animation-with-table2      0.091% ->  0.090%  flat
//   css-text-decor/ruby-text-decoration-01       1.374% ->  1.374%  flat (0 px moved)
// No currently-PASSING web cell degrades; the two that move are failures.
// direction-upright-002 is excluded from that table on purpose: its canvas
// height changes, so a top-left-aligned mismatch is not comparable (the
// gate's frame-aware ssimRefFrame is the metric that judges it).
//
// NOT A TAG MAPPING. Promoting these to real `<colgroup>`/`<col>`/`<ruby>`
// elements would be the other way to get the UA display, but `col` is a
// VOID element (WHATWG HTML §13.1.2) and the harness gives every component a
// content wrapper, so the tag route needs the void-element branch AND a
// content-suppression rule. Declaring the display keeps the change to one
// style key on the box the renderer already emits.

/**
 * `meta.sourceTag` → the UA `display` of that element, for the six tags whose
 * UA display the harness's `<div>` demotion would replace with a block box.
 * They are not all the same KIND of value — see the banner above for the
 * §2.4-layout-internal / §2.2-display-inside split.
 *
 *  - `colgroup` / `col`: css-tables-3 §2.1 puts column and column-group
 *    boxes outside the table's content flow — they exist to carry column
 *    backgrounds and width contributions, and contribute no block-axis
 *    extent of their own.
 *  - `ruby` / `rb` / `rt` / `rtc`: css-ruby-1 §2 defines the ruby box model
 *    — an annotation rides ALONGSIDE its base inside one ruby container,
 *    it is not a sibling block box after it.
 *
 * WHICH OF THE SIX ENTRIES HAS A MEASUREMENT BEHIND IT (wave-50 fix lane F6,
 * on skeptic S5: "two of its six entries have no measurement behind them").
 * Counted off lane B5's `tools/titan/results/wave50-B5/carriers.json` — the 9
 * corpus tests that carry any of these tags, with the A/B numbers in the
 * banner above:
 *   MEASURED — `col` (32 occurrences across the carriers), `colgroup` (29),
 *   `rt` (46) and `ruby` (21). The A/B moves on their carriers: CSS2/borders/
 *   border-conflict-style-107 39.276% → 10.431%, ch-units-vrl-003 and -007,
 *   and direction-upright-002's canvas 3045 → 3042 px when colgroup/col take
 *   their UA display and 3045 → 2769 px when ruby/rt do.
 *   NOT MEASURED — `rb` and `rtc`. `rtc` has ZERO carriers corpus-wide (S5
 *   re-derived the same `meta.sourceTag` walk independently and found none).
 *   `rb`'s only carrier is css-text-decor/ruby-text-decoration-01 (6 of them),
 *   whose A/B is FLAT at 1.374% — 0 px moved — so nothing in this repo shows
 *   that entry changing anything. Both are kept because they come from the
 *   same "Ruby annotations" UA rules as `rt` and dropping them would leave
 *   one box model half-described; but they ride on the spec, not on a
 *   measurement, and this comment says so instead of letting the A/B table
 *   above imply six measured entries.
 *
 * Deliberately NOT listed: `caption`, `thead`, `tbody`, `tfoot`, `tr`,
 * `td`, `th` — all of those ARE in TAG_ALLOWLIST already, so the browser
 * applies their UA display from the real tag.
 */
export const UA_BOXLESS_DISPLAY: ReadonlyMap<string, string> = new Map([
  ['colgroup', 'table-column-group'],
  ['col', 'table-column'],
  ['ruby', 'ruby'],
  ['rb', 'ruby-base'],
  ['rt', 'ruby-text'],
  ['rtc', 'ruby-text-container'],
]);

/**
 * Add the UA `display` for [sourceTag] when the component declares none.
 *
 * The author's own declaration always wins (the cascade: a declared
 * `display` is a specified value, this is a UA default), so the guard is
 * `'display' in styles` — the engine writes that key only when the IR
 * carries a `Display` property. Every other tag, and every component that
 * declares a display, gets the input object back UNCHANGED (identity, not a
 * copy), so no non-carrier capture can move.
 *
 * @param styles    the engine's computed style object for this component.
 * @param sourceTag lowercased `meta.sourceTag`, or null/undefined when absent.
 */
export function uaBoxlessDisplay<S extends Record<string, unknown>>(
  // Generic over the style object: ComponentRenderer hands in the runtime's
  // `CSSStyles` (an index-signature map) and the tests hand in React
  // `CSSProperties`; both satisfy the bound and each caller gets its own type
  // back (the CI `tsc --noEmit` typecheck rejected the CSSProperties-only
  // signature at the CSSStyles call site — wave 50 ship fix).
  styles: S,
  sourceTag: string | null | undefined,
): S {
  // No tag on the wire → nothing to default from.
  if (!sourceTag) return styles;
  // Not one of the six tags the map covers → identity. ("internal-display"
  // would be the wrong word for all six: `ruby` is display-inside, see the
  // banner — wave-50 fix lane F6.)
  const display = UA_BOXLESS_DISPLAY.get(sourceTag);
  if (display === undefined) return styles;
  // Declared display wins (cascade) — a `display: block` on a <col> is a
  // real author choice and this default must not shadow it.
  if ('display' in styles) return styles;
  // Spread the default UNDER nothing: styles has no `display` key here, so
  // key order cannot matter and the result differs by exactly one key.
  return { ...styles, display } as S;
}
