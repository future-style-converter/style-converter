// OverlayConfig.ts — typed config for the CSS `overlay` property.
// Mirrors CSS Positioned Layout 4 (top-layer) — widened: csstype has no `overlay` key yet.
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface OverlayConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const OVERLAY_PROPERTY_TYPE = 'Overlay' as const;
export type OverlayPropertyType = typeof OVERLAY_PROPERTY_TYPE;
