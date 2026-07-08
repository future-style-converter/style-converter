// IsolationApplier.ts — emits { isolation } from an IsolationConfig.

import type { CSSProperties } from 'react';
import type { IsolationConfig } from './IsolationConfig';

// Scoped partial covering just the one field we populate.
export type IsolationStyles = Pick<CSSProperties, 'isolation'>;

// Pure emitter — omit when unset so parent cascade still wins.
export function applyIsolation(config: IsolationConfig): IsolationStyles {
  const out: IsolationStyles = {};                                    // blank accumulator
  if (config.value) out.isolation = config.value;                     // only when set
  return out;
}
