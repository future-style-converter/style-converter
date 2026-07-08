// AllApplier.ts — emits { all } from AllConfig.  CSS L3 native.
import type { CSSProperties } from 'react';
import type { AllConfig } from './AllConfig';
export function applyAll(c: AllConfig): CSSProperties {
  return c.value === undefined ? {} : { all: c.value } as CSSProperties;
}
