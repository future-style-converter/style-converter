// PositionTryOrderConfig.ts — typed config for the CSS `positionTryOrder` property.
// Mirrors CSS Anchor Positioning L1 draft — widened.
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface PositionTryOrderConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const POSITIONTRYORDER_PROPERTY_TYPE = 'PositionTryOrder' as const;
export type PositionTryOrderPropertyType = typeof POSITIONTRYORDER_PROPERTY_TYPE;
