// MaskBorderOutsetExtractor.ts — shape-by-type dispatch.
import { foldLast, type IRPropertyLike } from '../_shared';
import type { MaskBorderOutsetConfig } from './MaskBorderOutsetConfig';
import { MASK_BORDER_OUTSET_PROPERTY_TYPE } from './MaskBorderOutsetConfig';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  switch (o.type) {
    case 'length': return typeof o.px === 'number' ? `${o.px}px` : undefined;         // length (px)
    case 'number': return typeof o.value === 'number' ? String(o.value) : undefined;  // unitless multiplier
    case 'multi':  return `${o.top} ${o.right} ${o.bottom} ${o.left}`;                // already strings
    default:        return undefined;
  }
}

export function extractMaskBorderOutset(properties: IRPropertyLike[]): MaskBorderOutsetConfig {
  return { value: foldLast(properties, MASK_BORDER_OUTSET_PROPERTY_TYPE, parseOne) };
}
