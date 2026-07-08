// VoiceFamilyApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/voice-family.
import type { CSSProperties } from 'react';
import type { VoiceFamilyConfig } from './VoiceFamilyConfig';
export function applyVoiceFamily(c: VoiceFamilyConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ voiceFamily: c.value } as unknown as CSSProperties) as Record<string, string>;
}
