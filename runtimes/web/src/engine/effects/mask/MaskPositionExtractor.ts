// MaskPositionExtractor.ts — IR layer list -> CSS comma-joined positions.
import { foldLast, positionAxis, type IRPropertyLike } from '../_shared';
import type { MaskPositionConfig } from './MaskPositionConfig';
import { MASK_POSITION_PROPERTY_TYPE } from './MaskPositionConfig';

function parseOne(data: unknown): string | undefined {
  if (!Array.isArray(data) || data.length === 0) return undefined;                  // require non-empty list
  const parts: string[] = [];
  for (const layer of data) {                                                        // one position per layer
    if (!layer || typeof layer !== 'object') continue;                               // skip malformed
    const o = layer as Record<string, unknown>;
    const x = positionAxis(o.x);                                                     // horizontal
    const y = positionAxis(o.y);                                                     // vertical
    if (x && y) parts.push(`${x} ${y}`);                                             // require both
  }
  return parts.length ? parts.join(', ') : undefined;                                // CSS: layers comma-separated
}

export function extractMaskPosition(properties: IRPropertyLike[]): MaskPositionConfig {
  return { value: foldLast(properties, MASK_POSITION_PROPERTY_TYPE, parseOne) };
}
