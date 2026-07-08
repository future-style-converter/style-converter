// PositionVisibilityConfig.ts — typed config for the CSS `positionVisibility` property.
// Mirrors CSS Anchor Positioning L1 draft — widened.
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface PositionVisibilityConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const POSITIONVISIBILITY_PROPERTY_TYPE = 'PositionVisibility' as const;
export type PositionVisibilityPropertyType = typeof POSITIONVISIBILITY_PROPERTY_TYPE;
