// RotateApplier.ts — RotateConfig -> CSS declaration.
// `rotate` is a native CSS Transforms L2 longhand — csstype has no key for
// it yet (as of react@18 types), so we widen via Record<string,string>.
// See MDN: https://developer.mozilla.org/docs/Web/CSS/rotate.

import type { RotateConfig } from './RotateConfig';

export function applyRotate(config: RotateConfig): Record<string, string> {
  if (config.value === undefined) return {};                                       // absent
  return { rotate: config.value };                                                  // native CSS key
}
