// VoiceBalanceApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/voice-balance.
import type { CSSProperties } from 'react';
import type { VoiceBalanceConfig } from './VoiceBalanceConfig';
export function applyVoiceBalance(c: VoiceBalanceConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ voiceBalance: c.value } as unknown as CSSProperties) as Record<string, string>;
}
