// FlexWrapConfig.ts — typed config for the CSS `flexWrap` property.
// Mirrors CSS Flexbox 1 — https://developer.mozilla.org/docs/Web/CSS/flex-wrap.
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface FlexWrapConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const FLEXWRAP_PROPERTY_TYPE = 'FlexWrap' as const;
export type FlexWrapPropertyType = typeof FLEXWRAP_PROPERTY_TYPE;
