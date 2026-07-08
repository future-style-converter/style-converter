// TransformApplier.ts — TransformConfig -> CSS declaration object.
// Emits the native `transform` key; browsers render this directly.

import type { CSSProperties } from 'react';
import type { TransformConfig } from './TransformConfig';

// Narrowed output surface — only the one key we ever populate.
export type TransformStyles = Pick<CSSProperties, 'transform'>;

export function applyTransform(config: TransformConfig): TransformStyles {
  if (config.value === undefined) return {};                                      // absent = emit nothing
  return { transform: config.value };                                              // native CSS key
}
