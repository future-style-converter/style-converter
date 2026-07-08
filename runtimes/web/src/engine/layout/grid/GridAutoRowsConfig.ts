// GridAutoRowsConfig.ts — CSS `gridAutoRows`.
// Track-list shape identical to GridTemplate{Columns,Rows}; see
// _grid_shared.trackSize for the IR shape catalogue.  Value is the already
// serialised CSS string or undefined.
export interface GridAutoRowsConfig { value?: string; }
export const GRID_AUTO_ROWS_PROPERTY_TYPE = 'GridAutoRows' as const;
export type GridAutoRowsPropertyType = typeof GRID_AUTO_ROWS_PROPERTY_TYPE;
