// VoiceVolumeApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/voice-volume.
import type { CSSProperties } from 'react';
import type { VoiceVolumeConfig } from './VoiceVolumeConfig';
export function applyVoiceVolume(c: VoiceVolumeConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ voiceVolume: c.value } as unknown as CSSProperties) as Record<string, string>;
}
