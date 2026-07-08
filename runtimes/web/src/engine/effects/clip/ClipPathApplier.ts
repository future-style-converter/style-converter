// ClipPathApplier.ts — native CSS `clipPath` key.
import type { CSSProperties } from 'react';
import type { ClipPathConfig } from './ClipPathConfig';

export type ClipPathStyles = Pick<CSSProperties, 'clipPath'>;

export function applyClipPath(config: ClipPathConfig): ClipPathStyles {
  if (config.value === undefined) return {};
  return { clipPath: config.value };
}
