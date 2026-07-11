/**
 * CssText — serialises a StyleBuilder `CSSStyles` object (React-style
 * camelCase keys) into real CSS declaration text for the stylesheet path
 * (RuleBuilder.ts). buildStyles stays the inline-style producer; this
 * module is the ONLY place its output is re-spelled as `prop: value`
 * declarations, so the two paths can never disagree on values.
 */

import type { CSSStyles } from './StyleBuilder';

/**
 * Properties whose numeric values are unitless in CSS. Mirrors React's
 * own `isUnitlessNumber` set, restricted to properties our appliers
 * actually emit as numbers today (opacity, z-index, flex factors, …).
 * Everything else numeric gets a `px` suffix — the same rule ReactDOM
 * applies when writing inline styles, so stylesheet and inline output
 * stay value-identical for the same CSSStyles input.
 */
const UNITLESS = new Set([
  'opacity', 'z-index', 'flex-grow', 'flex-shrink', 'flex', 'order',
  'font-weight', 'line-height', 'zoom', 'aspect-ratio', 'orphans',
  'widows', 'column-count', 'columns', 'tab-size', 'scale',
  'animation-iteration-count', 'fill-opacity', 'stroke-opacity',
  'stop-opacity', 'shape-image-threshold', 'font-size-adjust',
]);

/**
 * Convert a React camelCase style key to its CSS property name.
 * - `--custom-prop` keys pass through verbatim (case-SENSITIVE per
 *   css-variables-1 §2 — never case-mangle a custom property).
 * - `WebkitMaskImage` → `-webkit-mask-image` (leading capital already
 *   yields the leading dash via the uppercase-insertion rule).
 * - `msOverflowStyle` → `-ms-overflow-style` (React's lowercase `ms`
 *   vendor quirk needs the explicit leading dash).
 */
export function cssPropertyName(key: string): string {
  if (key.startsWith('--')) return key;                              // custom property — verbatim
  const kebab = key.replace(/([A-Z])/g, '-$1').toLowerCase();        // camelCase → kebab-case
  return /^ms[A-Z]/.test(key) ? `-${kebab}` : kebab;                 // ms vendor prefix quirk
}

/**
 * Serialise one CSSStyles value for a given (already kebab-cased)
 * property: numbers get `px` unless the property is unitless — the
 * exact ReactDOM inline-style rule, so both emit paths agree.
 */
function cssValue(prop: string, value: string | number): string {
  if (typeof value !== 'number') return value;                       // strings pass through verbatim
  return UNITLESS.has(prop) ? String(value) : `${value}px`;          // React's unitless rule
}

/**
 * Serialise a CSSStyles object into CSS declaration text
 * (`background-color: rgba(1, 2, 3, 1) !important; opacity: 0.5 !important`).
 *
 * `important` is set by RuleBuilder for every bucket rule: base styles
 * are INLINE (the unchanged buildStyles path), and per css-cascade-5 §6
 * only an author `!important` declaration can override a normal inline
 * declaration — without it no bucket could ever visibly beat its base.
 * Layering between bucket rules is then decided by specificity +
 * document order (see RuleBuilder for the spec-06 §3 mapping).
 *
 * Object insertion order is preserved (buildStyles output order), so
 * the declaration text is deterministic for a given property list.
 */
export function declarationsToCss(styles: CSSStyles, important: boolean): string {
  const parts: string[] = [];                                        // one entry per declaration
  for (const [key, value] of Object.entries(styles)) {
    if (value === undefined) continue;                               // unset — applier omitted it
    const prop = cssPropertyName(key);                               // camelCase → CSS spelling
    const bang = important ? ' !important' : '';                     // cascade escalation (see above)
    parts.push(`${prop}: ${cssValue(prop, value)}${bang}`);          // assembled declaration
  }
  return parts.join('; ');                                           // no trailing semicolon
}
