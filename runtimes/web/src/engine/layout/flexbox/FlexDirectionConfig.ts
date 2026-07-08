// FlexDirectionConfig.ts — typed config for the CSS `flexDirection` property.
// Mirrors CSS Flexbox 1 — https://developer.mozilla.org/docs/Web/CSS/flex-direction.
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface FlexDirectionConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const FLEXDIRECTION_PROPERTY_TYPE = 'FlexDirection' as const;
export type FlexDirectionPropertyType = typeof FLEXDIRECTION_PROPERTY_TYPE;
