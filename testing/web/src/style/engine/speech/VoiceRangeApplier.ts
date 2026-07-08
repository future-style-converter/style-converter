// VoiceRangeApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/voice-range.
import type { CSSProperties } from 'react';
import type { VoiceRangeConfig } from './VoiceRangeConfig';
export function applyVoiceRange(c: VoiceRangeConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ voiceRange: c.value } as unknown as CSSProperties) as Record<string, string>;
}
