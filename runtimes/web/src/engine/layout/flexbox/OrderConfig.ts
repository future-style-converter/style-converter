// OrderConfig.ts — typed config for CSS `order`.
// The IR emits a single integer (see OrderPropertyParser.kt); negatives OK.

export interface OrderConfig { value?: number; }

export const ORDER_PROPERTY_TYPE = 'Order' as const;
export type OrderPropertyType = typeof ORDER_PROPERTY_TYPE;
