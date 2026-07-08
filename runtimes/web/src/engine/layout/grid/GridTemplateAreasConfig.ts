// GridTemplateAreasConfig.ts — CSS `grid-template-areas`.
// The IR shape (see GridTemplateAreasPropertyParser.kt) is either
//   { type:'none' }  → emits the `none` keyword
//   { type:'areas', rows: string[][] }  → emits `"a b c" "d e f"` (quoted rows)
// Dots in cells preserve their literal `.` meaning (empty cell).
export interface GridTemplateAreasConfig { value?: string; }
export const GRID_TEMPLATE_AREAS_PROPERTY_TYPE = 'GridTemplateAreas' as const;
export type GridTemplateAreasPropertyType = typeof GRID_TEMPLATE_AREAS_PROPERTY_TYPE;
