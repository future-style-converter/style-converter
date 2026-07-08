// GridAutoColumnsConfig.ts — CSS `gridAutoColumns`.
// Track-list shape identical to GridTemplate{Columns,Rows}; see
// _grid_shared.trackSize for the IR shape catalogue.  Value is the already
// serialised CSS string or undefined.
export interface GridAutoColumnsConfig { value?: string; }
export const GRID_AUTO_COLUMNS_PROPERTY_TYPE = 'GridAutoColumns' as const;
export type GridAutoColumnsPropertyType = typeof GRID_AUTO_COLUMNS_PROPERTY_TYPE;
