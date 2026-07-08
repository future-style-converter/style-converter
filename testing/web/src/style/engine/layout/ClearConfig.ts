// ClearConfig.ts — typed config for the CSS `clear` property.
// Mirrors CSS 2 — https://developer.mozilla.org/docs/Web/CSS/clear.
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface ClearConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const CLEAR_PROPERTY_TYPE = 'Clear' as const;
export type ClearPropertyType = typeof CLEAR_PROPERTY_TYPE;
