// GridTemplateColumnsConfig.ts — CSS `grid-template-columns`.
// Value is a pre-serialised track list (already CSS-valid) or undefined.
// See GridTemplateColumnsPropertyParser.kt for the full grammar; this config
// just carries what renderTrackList produced.
export interface GridTemplateColumnsConfig { value?: string; }
export const GRID_TEMPLATE_COLUMNS_PROPERTY_TYPE = 'GridTemplateColumns' as const;
export type GridTemplateColumnsPropertyType = typeof GRID_TEMPLATE_COLUMNS_PROPERTY_TYPE;
