// ClipApplier.ts — native CSS `clip` key (deprecated but still valid).
import type { CSSProperties } from 'react';
import type { ClipConfig } from './ClipConfig';

export type ClipStyles = Pick<CSSProperties, 'clip'>;

export function applyClip(config: ClipConfig): ClipStyles {
  if (config.value === undefined) return {};
  return { clip: config.value };
}
