// JustifyTracksConfig.ts — CSS `justify-tracks` (Grid L3 masonry draft).
// IR shape: { type:'normal'|'start'|'end'|'center'|'stretch'|'space-between' } |
// { type:'multi', values: string[] }  — /tmp/layout_ir/grid-justify-tracks.
// csstype has no typed entry for this L3 draft property — widened emission.
// WHY widen: CSS Grid L3 draft — https://drafts.csswg.org/css-grid-3/#masonry-layout.
export interface JustifyTracksConfig { value?: string; }
export const JUSTIFY_TRACKS_PROPERTY_TYPE = 'JustifyTracks' as const;
export type JustifyTracksPropertyType = typeof JUSTIFY_TRACKS_PROPERTY_TYPE;
