// InputSecurityApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/input-security.
import type { CSSProperties } from 'react';
import type { InputSecurityConfig } from './InputSecurityConfig';
export function applyInputSecurity(c: InputSecurityConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ inputSecurity: c.value } as unknown as CSSProperties) as Record<string, string>;
}
