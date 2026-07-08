// GridColumnEndConfig.ts — CSS `grid-column-end`.  IR shape mirrors
// GridColumnStart; see that file.
export interface GridColumnEndConfig { value?: string; }
export const GRID_COLUMN_END_PROPERTY_TYPE = 'GridColumnEnd' as const;
export type GridColumnEndPropertyType = typeof GRID_COLUMN_END_PROPERTY_TYPE;
