// ReadingFlowApplier.ts — emits a CSS declaration for the `readingFlow` property.
// csstype (React.CSSProperties) has no typed entry for this draft-level
// property; we therefore widen the return to `Record<string,string>`.
// WHY widen: CSS Display L4 draft — see https://drafts.csswg.org/css-display-4/#reading-flow.
// The value is a kebab-case keyword produced by the extractor and valid
// verbatim in any engine that recognises the property.

import type { ReadingFlowConfig } from './ReadingFlowConfig';

// Output is a plain string map — browsers ignore keys they don't know, so
// emitting here is safe even on engines without the feature.
export function applyReadingFlow(config: ReadingFlowConfig): Record<string, string> {
  if (config.value === undefined) return {};                         // unset — no declaration
  return { readingFlow: config.value };                                    // single-key output
}
