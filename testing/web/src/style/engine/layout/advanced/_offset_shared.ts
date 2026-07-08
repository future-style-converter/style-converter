// _offset_shared.ts — helpers for the CSS Motion Path offset-* family.
// Layout-relevant extractors for position payloads (`{x: …, y: …}` pairs)
// shared by OffsetAnchor and OffsetPosition.  Also small IR renderers for
// `<basic-shape>`-style payloads used by OffsetPath.

import { layoutLength } from '../_shared';

// Convert a typed axis payload (see /tmp/layout_ir/offset-anchor) into a CSS
// position token: bare keyword ('left','center','top','bottom'), percentage
// or length.  Returns undefined on unknown shapes.
export function axisToken(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  if (o.type === 'left' || o.type === 'right' || o.type === 'top' ||
      o.type === 'bottom' || o.type === 'center') return String(o.type);            // keyword passthrough
  if (o.type === 'length' && typeof o.px === 'number') return `${o.px}px`;          // absolute length
  if (o.type === 'percentage' && typeof o.value === 'number') return `${o.value}%`; // percentage
  // Fallback: let layoutLength handle odd wrappers.
  return layoutLength(o);
}

// Render a position pair `{x, y}` — used by OffsetAnchor and OffsetPosition.
// Per CSS Values 4 spec, missing axis defaults to `center` — we don't apply
// that default here; our fixtures always ship both axes explicitly.
export function positionPair(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  if (o.type !== 'position') return undefined;                                      // only 'position' shape
  const x = axisToken(o.x);
  const y = axisToken(o.y);
  if (x && y) return `${x} ${y}`;                                                   // CSS 2-value position
  return undefined;
}
