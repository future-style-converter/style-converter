// AllApplier.ts — emits { all } from AllConfig.  CSS L3 native.
// Wave 52 (lane L11): StyleBuilder places this key FIRST in the style object
// and filters the declarations before it (applyAllReset, _dispatch.ts), so the
// browser applies the shorthand, then every longhand that followed it in the
// source (css-cascade-4 §6.4) — never the reverse.
import type { CSSProperties } from 'react';
import type { AllConfig } from './AllConfig';
export function applyAll(c: AllConfig): CSSProperties {
  // No keyword `All` → no key at all (the object stays byte-identical).
  return c.value === undefined ? {} : { all: c.value } as CSSProperties;
}
