// MaskBorderSliceExtractor.ts — number/string/TRBL IR shapes.
import { foldLast, type IRPropertyLike } from '../_shared';
import type { MaskBorderSliceConfig } from './MaskBorderSliceConfig';
import { MASK_BORDER_SLICE_PROPERTY_TYPE } from './MaskBorderSliceConfig';

// Convert one edge value (bare number or {pct:N}) to the CSS token.
function edge(raw: unknown): string {
  if (typeof raw === 'number') return String(raw);                                    // unitless number
  if (raw && typeof raw === 'object' && typeof (raw as { pct?: unknown }).pct === 'number')
    return `${(raw as { pct: number }).pct}%`;                                        // percentage wrapper
  return '0';                                                                          // default
}

function parseOne(data: unknown): string | undefined {
  if (typeof data === 'number') return String(data);                                  // uniform slice
  if (typeof data === 'string') {                                                     // 'fill' keyword alone
    return data === 'fill' ? 'fill' : undefined;
  }
  if (!data || typeof data !== 'object') return undefined;                            // unknown
  const o = data as Record<string, unknown>;
  const parts = [edge(o.top), edge(o.right), edge(o.bottom), edge(o.left)];           // TRBL
  if (o.fill === true) parts.push('fill');                                            // optional fill flag
  return parts.join(' ');                                                              // space-sep per spec
}

export function extractMaskBorderSlice(properties: IRPropertyLike[]): MaskBorderSliceConfig {
  return { value: foldLast(properties, MASK_BORDER_SLICE_PROPERTY_TYPE, parseOne) };
}
