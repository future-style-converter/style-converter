// PositionAnchorApplier.ts — emits a CSS declaration for the `positionAnchor` property.
// csstype (React.CSSProperties) has no typed entry for this draft-level
// property; we therefore widen the return to `Record<string,string>`.
// WHY widen: CSS Anchor Positioning L1 draft — see https://drafts.csswg.org/css-anchor-position-1/#position-anchor.
// The value is a kebab-case keyword produced by the extractor and valid
// verbatim in any engine that recognises the property.

import type { PositionAnchorConfig } from './PositionAnchorConfig';

// Output is a plain string map — browsers ignore keys they don't know, so
// emitting here is safe even on engines without the feature.
export function applyPositionAnchor(config: PositionAnchorConfig): Record<string, string> {
  if (config.value === undefined) return {};                         // unset — no declaration
  return { positionAnchor: config.value };                                    // single-key output
}
