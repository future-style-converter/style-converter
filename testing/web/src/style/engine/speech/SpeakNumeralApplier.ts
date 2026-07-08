// SpeakNumeralApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/speak-numeral.
import type { CSSProperties } from 'react';
import type { SpeakNumeralConfig } from './SpeakNumeralConfig';
export function applySpeakNumeral(c: SpeakNumeralConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ speakNumeral: c.value } as unknown as CSSProperties) as Record<string, string>;
}
