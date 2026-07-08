// GridRowStartConfig.ts — CSS `grid-row-start`.  IR shape mirrors
// GridColumnStart (see that file).
export interface GridRowStartConfig { value?: string; }
export const GRID_ROW_START_PROPERTY_TYPE = 'GridRowStart' as const;
export type GridRowStartPropertyType = typeof GRID_ROW_START_PROPERTY_TYPE;
