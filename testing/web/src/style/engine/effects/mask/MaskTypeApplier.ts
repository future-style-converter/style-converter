// MaskTypeApplier.ts — SVG presentation attribute; csstype recognises.
import type { CSSProperties } from 'react';
import type { MaskTypeConfig } from './MaskTypeConfig';

export type MaskTypeStyles = Pick<CSSProperties, 'maskType'>;

export function applyMaskType(config: MaskTypeConfig): MaskTypeStyles {
  if (config.value === undefined) return {};
  return { maskType: config.value } as MaskTypeStyles;
}
