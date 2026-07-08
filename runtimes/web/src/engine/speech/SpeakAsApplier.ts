// SpeakAsApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/speak-as.
import type { CSSProperties } from 'react';
import type { SpeakAsConfig } from './SpeakAsConfig';
export function applySpeakAs(c: SpeakAsConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ speakAs: c.value } as unknown as CSSProperties) as Record<string, string>;
}
