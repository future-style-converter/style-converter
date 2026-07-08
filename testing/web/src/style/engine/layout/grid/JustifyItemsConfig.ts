// JustifyItemsConfig.ts — typed config for the CSS `justifyItems` property.
// Mirrors CSS Box Alignment 3 — https://developer.mozilla.org/docs/Web/CSS/justify-items.
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface JustifyItemsConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const JUSTIFYITEMS_PROPERTY_TYPE = 'JustifyItems' as const;
export type JustifyItemsPropertyType = typeof JUSTIFYITEMS_PROPERTY_TYPE;
