// OverlayApplier.ts — emits a CSS declaration for the `overlay` property.
// csstype (React.CSSProperties) has no typed entry for this draft-level
// property; we therefore widen the return to `Record<string,string>`.
// WHY widen: CSS Positioned Layout 4 (top-layer) — see https://drafts.csswg.org/css-position-4/#overlay.
// The value is a kebab-case keyword produced by the extractor and valid
// verbatim in any engine that recognises the property.

import type { OverlayConfig } from './OverlayConfig';

// Output is a plain string map — browsers ignore keys they don't know, so
// emitting here is safe even on engines without the feature.
export function applyOverlay(config: OverlayConfig): Record<string, string> {
  if (config.value === undefined) return {};                         // unset — no declaration
  return { overlay: config.value };                                    // single-key output
}
