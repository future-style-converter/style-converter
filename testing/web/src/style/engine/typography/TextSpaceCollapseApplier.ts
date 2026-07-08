// TextSpaceCollapseApplier.ts — emits CSS declarations from a TextSpaceCollapseConfig.
// Web is the privileged platform for typography: native CSS `textSpaceCollapse`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextSpaceCollapseConfig } from './TextSpaceCollapseConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/textSpaceCollapse
export type TextSpaceCollapseStyles = CSSProperties;

export function applyTextSpaceCollapse(config: TextSpaceCollapseConfig): TextSpaceCollapseStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ textSpaceCollapse: config.value } as unknown) as TextSpaceCollapseStyles;
}
