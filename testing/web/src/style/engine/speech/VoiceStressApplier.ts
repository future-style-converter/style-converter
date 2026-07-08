// VoiceStressApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/voice-stress.
import type { CSSProperties } from 'react';
import type { VoiceStressConfig } from './VoiceStressConfig';
export function applyVoiceStress(c: VoiceStressConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ voiceStress: c.value } as unknown as CSSProperties) as Record<string, string>;
}
