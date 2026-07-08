// MaskBorderRepeatExtractor.ts — one- or two-value keyword form.
import { foldLast, type IRPropertyLike } from '../_shared';
import type { MaskBorderRepeatConfig } from './MaskBorderRepeatConfig';
import { MASK_BORDER_REPEAT_PROPERTY_TYPE } from './MaskBorderRepeatConfig';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;                            // require wrapper
  const o = data as Record<string, unknown>;
  if (o.type === 'two-value') {                                                       // explicit H/V form
    return `${o.horizontal} ${o.vertical}`;                                           // strings already kebab
  }
  if (typeof o.type === 'string') return o.type;                                      // single keyword form
  return undefined;
}

export function extractMaskBorderRepeat(properties: IRPropertyLike[]): MaskBorderRepeatConfig {
  return { value: foldLast(properties, MASK_BORDER_REPEAT_PROPERTY_TYPE, parseOne) };
}
