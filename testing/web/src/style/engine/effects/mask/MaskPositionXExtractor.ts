// MaskPositionXExtractor.ts — single-axis position value.
import { foldLast, positionAxis, type IRPropertyLike } from '../_shared';
import type { MaskPositionXConfig } from './MaskPositionXConfig';
import { MASK_POSITION_X_PROPERTY_TYPE } from './MaskPositionXConfig';

export function extractMaskPositionX(properties: IRPropertyLike[]): MaskPositionXConfig {
  return { value: foldLast(properties, MASK_POSITION_X_PROPERTY_TYPE, positionAxis) };
}
