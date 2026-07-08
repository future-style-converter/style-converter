// AlignTracksConfig.ts — CSS `align-tracks` (Grid L3 masonry draft).
// Shape identical to JustifyTracks; see that file.  Widened for the same
// reason — https://drafts.csswg.org/css-grid-3/#align-tracks-alignment.
export interface AlignTracksConfig { value?: string; }
export const ALIGN_TRACKS_PROPERTY_TYPE = 'AlignTracks' as const;
export type AlignTracksPropertyType = typeof ALIGN_TRACKS_PROPERTY_TYPE;
