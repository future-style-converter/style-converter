// MasonryAutoFlowConfig.ts — CSS `masonry-auto-flow` (Grid L3 masonry draft).
// IR emits { type: 'pack'|'next'|'ordered'|'definite-first'|
//           'pack-ordered'|'pack-definite-first'|'next-ordered'|'next-definite-first' }
// CSS value is a 1–2 space-separated token combo; the parser already normalised
// the combos to hyphen-joined forms — we split back on `-` to emit space-joined.
// WHY widen: csstype has no `masonry-auto-flow` — draft-level.
// See https://drafts.csswg.org/css-grid-3/#masonry-auto-flow.
export interface MasonryAutoFlowConfig { value?: string; }
export const MASONRY_AUTO_FLOW_PROPERTY_TYPE = 'MasonryAutoFlow' as const;
export type MasonryAutoFlowPropertyType = typeof MASONRY_AUTO_FLOW_PROPERTY_TYPE;
