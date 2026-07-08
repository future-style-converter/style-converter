// PositionTryOrderApplier.ts — emits a CSS declaration for the `positionTryOrder` property.
// csstype (React.CSSProperties) has no typed entry for this draft-level
// property; we therefore widen the return to `Record<string,string>`.
// WHY widen: CSS Anchor Positioning L1 draft — see https://drafts.csswg.org/css-anchor-position-1/#position-try-order.
// The value is a kebab-case keyword produced by the extractor and valid
// verbatim in any engine that recognises the property.

import type { PositionTryOrderConfig } from './PositionTryOrderConfig';

// Output is a plain string map — browsers ignore keys they don't know, so
// emitting here is safe even on engines without the feature.
export function applyPositionTryOrder(config: PositionTryOrderConfig): Record<string, string> {
  if (config.value === undefined) return {};                         // unset — no declaration
  return { positionTryOrder: config.value };                                    // single-key output
}
