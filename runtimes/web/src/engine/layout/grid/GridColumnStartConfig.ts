// GridColumnStartConfig.ts — CSS `grid-column-start`.
// IR uses a typed line object; _grid_shared.gridLine renders all four variants
// (auto, integer, span N, named).  Value is the serialised string or undefined.
export interface GridColumnStartConfig { value?: string; }
export const GRID_COLUMN_START_PROPERTY_TYPE = 'GridColumnStart' as const;
export type GridColumnStartPropertyType = typeof GRID_COLUMN_START_PROPERTY_TYPE;
