// AlignSelfConfig.ts — typed config for the CSS `alignSelf` property.
// Mirrors CSS Box Alignment 3 — https://developer.mozilla.org/docs/Web/CSS/align-self.
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface AlignSelfConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const ALIGNSELF_PROPERTY_TYPE = 'AlignSelf' as const;
export type AlignSelfPropertyType = typeof ALIGNSELF_PROPERTY_TYPE;
