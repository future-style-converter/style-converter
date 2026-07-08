// MaskModeApplier.ts — csstype has `maskMode`.  No -webkit- prefix needed.
import type { CSSProperties } from 'react';
import type { MaskModeConfig } from './MaskModeConfig';

export type MaskModeStyles = Pick<CSSProperties, 'maskMode'>;

export function applyMaskMode(config: MaskModeConfig): MaskModeStyles {
  if (config.value === undefined) return {};
  return { maskMode: config.value } as MaskModeStyles;
}
