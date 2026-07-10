// BoxSizingApplier.ts — emits the native CSS `box-sizing` declaration.
// One-key emitter: csstype's Property.BoxSizing already includes both
// literals, so no widening cast is needed.
import type { CSSProperties } from 'react';
import type { BoxSizingConfig } from './BoxSizingConfig';

// Pure function — emit only when the extractor produced a value.
export function applyBoxSizing(c: BoxSizingConfig): CSSProperties {
  if (c.value === undefined) return {};                             // unset → no declaration
  return { boxSizing: c.value };                                    // 'content-box' | 'border-box'
}
