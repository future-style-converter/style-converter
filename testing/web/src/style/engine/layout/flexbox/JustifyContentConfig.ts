// JustifyContentConfig.ts — typed config for the CSS `justifyContent` property.
// Mirrors CSS Box Alignment 3 — https://developer.mozilla.org/docs/Web/CSS/justify-content.
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface JustifyContentConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const JUSTIFYCONTENT_PROPERTY_TYPE = 'JustifyContent' as const;
export type JustifyContentPropertyType = typeof JUSTIFYCONTENT_PROPERTY_TYPE;
