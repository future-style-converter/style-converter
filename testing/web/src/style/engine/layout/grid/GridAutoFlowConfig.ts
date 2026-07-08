// GridAutoFlowConfig.ts — CSS `grid-auto-flow`.
// IR may emit either a bare enum ('ROW'|'COLUMN') or a typed object
// { direction: 'ROW'|'COLUMN', dense: boolean }.  CSS accepts four forms:
// row | column | row dense | column dense.
export interface GridAutoFlowConfig { value?: string; }
export const GRID_AUTO_FLOW_PROPERTY_TYPE = 'GridAutoFlow' as const;
export type GridAutoFlowPropertyType = typeof GRID_AUTO_FLOW_PROPERTY_TYPE;
