// FootnotePolicyApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/footnote-policy.
import type { CSSProperties } from 'react';
import type { FootnotePolicyConfig } from './FootnotePolicyConfig';
export function applyFootnotePolicy(c: FootnotePolicyConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ footnotePolicy: c.value } as unknown as CSSProperties) as Record<string, string>;
}
