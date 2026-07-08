// OrderExtractor.ts — IR shape is a bare integer (see /tmp/layout_ir/flex-order).

import { OrderConfig, ORDER_PROPERTY_TYPE } from './OrderConfig';
import { foldLast, type IRPropertyLike } from '../_shared';

function parse(data: unknown): number | undefined {
  // Integers arrive as plain JS numbers; round defensively to stay integer.
  if (typeof data === 'number' && Number.isFinite(data)) return Math.round(data);
  if (data && typeof data === 'object') {
    const o = data as Record<string, unknown>;
    if (typeof o.value === 'number') return Math.round(o.value);                  // {value:N} shape — defensive
  }
  return undefined;                                                               // drop unknown
}

export function extractOrder(properties: IRPropertyLike[]): OrderConfig {
  return { value: foldLast(properties, ORDER_PROPERTY_TYPE, parse) };
}
