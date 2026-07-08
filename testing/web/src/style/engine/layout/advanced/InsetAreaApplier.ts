// InsetAreaApplier.ts — emits `inset-area`.  Widened.
// WHY widen: legacy anchor-positioning draft key — see
// https://developer.chrome.com/blog/anchor-positioning-api#inset-area.

import type { InsetAreaConfig } from './InsetAreaConfig';

export function applyInsetArea(config: InsetAreaConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { insetArea: config.value };
}
