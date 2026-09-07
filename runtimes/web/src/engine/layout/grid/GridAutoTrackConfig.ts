// GridAutoTrackConfig.ts — `grid-auto-track` (early css-grid-3 masonry draft;
// no browser ships it — csstype-widened pass-through keeps the wire honest).
// Issue #38 audit: was a coverage-only registry claim with no applier even
// though GridAutoTrackPropertyParser is registered and CAN emit the type.
// Deliberately standalone: does NOT touch GridExtractor or its repeat() path.
export interface GridAutoTrackConfig { value?: string }
export const GRID_AUTO_TRACK_PROPERTY_TYPE = 'GridAutoTrack' as const;
