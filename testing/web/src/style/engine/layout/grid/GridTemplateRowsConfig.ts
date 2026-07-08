// GridTemplateRowsConfig.ts — CSS `grid-template-rows`.  Same shape as
// GridTemplateColumnsConfig; see that file for commentary.
export interface GridTemplateRowsConfig { value?: string; }
export const GRID_TEMPLATE_ROWS_PROPERTY_TYPE = 'GridTemplateRows' as const;
export type GridTemplateRowsPropertyType = typeof GRID_TEMPLATE_ROWS_PROPERTY_TYPE;
