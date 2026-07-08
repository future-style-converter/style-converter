// SpeechRateApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/speech-rate.
import type { CSSProperties } from 'react';
import type { SpeechRateConfig } from './SpeechRateConfig';
export function applySpeechRate(c: SpeechRateConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ speechRate: c.value } as unknown as CSSProperties) as Record<string, string>;
}
