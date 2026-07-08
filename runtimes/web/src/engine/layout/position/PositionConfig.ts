// PositionConfig.ts — typed config for the CSS `position` property.
// Mirrors CSS Positioned Layout 3 — https://developer.mozilla.org/docs/Web/CSS/position.
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface PositionConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const POSITION_PROPERTY_TYPE = 'Position' as const;
export type PositionPropertyType = typeof POSITION_PROPERTY_TYPE;
