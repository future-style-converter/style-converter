// OrderApplier.ts — emits `order`.  Native CSS integer.
// Spec: https://developer.mozilla.org/docs/Web/CSS/order.

import type { CSSProperties } from 'react';
import type { OrderConfig } from './OrderConfig';

export type OrderStyles = Pick<CSSProperties, 'order'>;

export function applyOrder(config: OrderConfig): OrderStyles {
  if (config.value === undefined) return {};                                      // unset
  return { order: config.value } as OrderStyles;                                  // integer passthrough
}
