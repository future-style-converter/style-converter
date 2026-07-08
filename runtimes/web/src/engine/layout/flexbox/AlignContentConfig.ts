// AlignContentConfig.ts — typed config for the CSS `alignContent` property.
// Mirrors CSS Box Alignment 3 — https://developer.mozilla.org/docs/Web/CSS/align-content.
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface AlignContentConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const ALIGNCONTENT_PROPERTY_TYPE = 'AlignContent' as const;
export type AlignContentPropertyType = typeof ALIGNCONTENT_PROPERTY_TYPE;
