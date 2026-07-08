// PositionAnchorConfig.ts — typed config for the CSS `positionAnchor` property.
// Mirrors CSS Anchor Positioning L1 draft — widened. IR emits the raw name verbatim..
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface PositionAnchorConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const POSITIONANCHOR_PROPERTY_TYPE = 'PositionAnchor' as const;
export type PositionAnchorPropertyType = typeof POSITIONANCHOR_PROPERTY_TYPE;
