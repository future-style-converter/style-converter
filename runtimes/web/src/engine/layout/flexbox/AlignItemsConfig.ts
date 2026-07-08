// AlignItemsConfig.ts — typed config for the CSS `alignItems` property.
// Mirrors CSS Box Alignment 3 — https://developer.mozilla.org/docs/Web/CSS/align-items.
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface AlignItemsConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const ALIGNITEMS_PROPERTY_TYPE = 'AlignItems' as const;
export type AlignItemsPropertyType = typeof ALIGNITEMS_PROPERTY_TYPE;
