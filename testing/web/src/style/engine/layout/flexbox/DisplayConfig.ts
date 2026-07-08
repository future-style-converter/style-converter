// DisplayConfig.ts — typed config for the CSS `display` property.
// Mirrors CSS 2 / Display L3 — https://developer.mozilla.org/docs/Web/CSS/display.
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface DisplayConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const DISPLAY_PROPERTY_TYPE = 'Display' as const;
export type DisplayPropertyType = typeof DISPLAY_PROPERTY_TYPE;
