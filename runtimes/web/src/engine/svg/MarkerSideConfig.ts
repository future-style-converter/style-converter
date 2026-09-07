// MarkerSideConfig.ts — SVG 2 §markers `marker-side` (spec-only; no browser
// ships it — csstype-widened pass-through keeps the wire honest).
// Issue #38 audit: was a coverage-only registry claim with no applier.
export interface MarkerSideConfig { value?: string }
export const MARKER_SIDE_PROPERTY_TYPE = 'MarkerSide' as const;
