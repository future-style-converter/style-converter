// VoiceRateApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/voice-rate.
import type { CSSProperties } from 'react';
import type { VoiceRateConfig } from './VoiceRateConfig';
export function applyVoiceRate(c: VoiceRateConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ voiceRate: c.value } as unknown as CSSProperties) as Record<string, string>;
}
