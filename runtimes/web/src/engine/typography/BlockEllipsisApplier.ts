// BlockEllipsisApplier.ts — emits CSS declarations from a BlockEllipsisConfig.
// Web is the privileged platform for typography: native CSS `blockEllipsis`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { BlockEllipsisConfig } from './BlockEllipsisConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/blockEllipsis
export type BlockEllipsisStyles = CSSProperties;

export function applyBlockEllipsis(config: BlockEllipsisConfig): BlockEllipsisStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ blockEllipsis: config.value } as unknown) as BlockEllipsisStyles;
}
