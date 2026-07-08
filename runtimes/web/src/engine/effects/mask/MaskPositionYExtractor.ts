// MaskPositionYExtractor.ts — single-axis position value.
import { foldLast, positionAxis, type IRPropertyLike } from '../_shared';
import type { MaskPositionYConfig } from './MaskPositionYConfig';
import { MASK_POSITION_Y_PROPERTY_TYPE } from './MaskPositionYConfig';

export function extractMaskPositionY(properties: IRPropertyLike[]): MaskPositionYConfig {
  return { value: foldLast(properties, MASK_POSITION_Y_PROPERTY_TYPE, positionAxis) };
}
