// VolumeApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/volume.
import type { CSSProperties } from 'react';
import type { VolumeConfig } from './VolumeConfig';
export function applyVolume(c: VolumeConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ volume: c.value } as unknown as CSSProperties) as Record<string, string>;
}
