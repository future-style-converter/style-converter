// FloatConfig.ts — typed config for the CSS `float` property.
// Mirrors CSS 2 — https://developer.mozilla.org/docs/Web/CSS/float.
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface FloatConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const FLOAT_PROPERTY_TYPE = 'Float' as const;
export type FloatPropertyType = typeof FLOAT_PROPERTY_TYPE;
