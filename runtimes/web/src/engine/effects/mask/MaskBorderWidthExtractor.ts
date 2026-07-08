// MaskBorderWidthExtractor.ts — IR variant shapes -> CSS token.
import { foldLast, type IRPropertyLike } from '../_shared';
import type { MaskBorderWidthConfig } from './MaskBorderWidthConfig';
import { MASK_BORDER_WIDTH_PROPERTY_TYPE } from './MaskBorderWidthConfig';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;                            // require wrapper
  const o = data as Record<string, unknown>;
  switch (o.type) {
    case 'auto':   return 'auto';                                                     // keyword
    case 'length': return typeof o.px === 'number' ? `${o.px}px` : undefined;         // length
    case 'number': return typeof o.value === 'number' ? String(o.value) : undefined;  // unitless multiplier
    case 'multi':  return `${o.top} ${o.right} ${o.bottom} ${o.left}`;                // already CSS strings
    default:        return undefined;
  }
}

export function extractMaskBorderWidth(properties: IRPropertyLike[]): MaskBorderWidthConfig {
  return { value: foldLast(properties, MASK_BORDER_WIDTH_PROPERTY_TYPE, parseOne) };
}
