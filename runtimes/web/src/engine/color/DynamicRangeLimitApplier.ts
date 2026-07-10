// DynamicRangeLimitApplier.ts — csstype-widened (CSS Color HDR 1 is a draft;
// csstype has no dynamicRangeLimit key yet). Chromium
// supports the property natively; other engines ignore the unknown key.
import type { CSSProperties } from 'react';
import type { DynamicRangeLimitConfig } from './DynamicRangeLimitConfig';

// Pure function — emit only when the extractor produced a value.
export function applyDynamicRangeLimit(c: DynamicRangeLimitConfig): Record<string, string> {
  if (c.value === undefined) return {};                             // unset → no declaration
  // Widen through CSSProperties because the key is not in csstype yet.
  return ({ dynamicRangeLimit: c.value } as unknown as CSSProperties) as Record<string, string>;
}
