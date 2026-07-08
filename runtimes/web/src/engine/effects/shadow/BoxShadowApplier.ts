// BoxShadowApplier.ts — serialise BoxShadowConfig to inline CSS.
// Emits the native `boxShadow` CSS property.  Layers join with ", " exactly
// like the CSS spec (Backgrounds & Borders §7.1) so multi-layer shadows
// round-trip byte-for-byte on the web.
//
// Layer syntax (per spec): `[inset] <offset-x> <offset-y> [<blur>] [<spread>] [<color>]`.
// Missing blur/spread are omitted (not emitted as 0) so the browser's own
// defaults apply — matches Chrome/Firefox serialised form.

import type { CSSProperties } from 'react';
import { toCssLength } from '../../core/types/LengthValue';               // length -> CSS string
import { colorToCss } from '../../color/DynamicColorCss';                 // color -> CSS string
import type { BoxShadowConfig, BoxShadowLayer } from './BoxShadowConfig';

// Output surface narrowed to the one key we populate.
export type BoxShadowStyles = Pick<CSSProperties, 'boxShadow'>;

// Convert a single layer to its CSS token.
function layerToCss(l: BoxShadowLayer): string {
  const parts: string[] = [];                                             // assemble in spec order
  if (l.inset) parts.push('inset');                                       // inset flag FIRST per spec
  parts.push(toCssLength(l.x));                                           // mandatory X
  parts.push(toCssLength(l.y));                                           // mandatory Y
  if (l.blur)   parts.push(toCssLength(l.blur));                          // optional blur
  if (l.spread) parts.push(toCssLength(l.spread));                        // optional spread
  if (l.color)  parts.push(colorToCss(l.color));                          // optional color
  return parts.join(' ');                                                  // space-separated
}

// Pure function — emits {} when unset so callers can safely spread.
export function applyBoxShadow(config: BoxShadowConfig): BoxShadowStyles {
  if (config.none) return { boxShadow: 'none' };                          // explicit none
  if (config.raw !== undefined) return { boxShadow: config.raw };          // calc-containing fallback
  if (!config.layers || config.layers.length === 0) return {};             // nothing set
  const css = config.layers.map(layerToCss).join(', ');                    // multi-layer join
  return { boxShadow: css };
}
