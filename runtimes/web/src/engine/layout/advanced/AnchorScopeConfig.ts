// AnchorScopeConfig.ts — typed config for the CSS `anchorScope` property.
// Mirrors CSS Anchor Positioning L1 draft — widened.
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface AnchorScopeConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const ANCHORSCOPE_PROPERTY_TYPE = 'AnchorScope' as const;
export type AnchorScopePropertyType = typeof ANCHORSCOPE_PROPERTY_TYPE;
