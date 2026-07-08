// OffsetDistanceExtractor.ts — delegates to layoutLength() which handles
// the { type:'length'|'percentage' } wrappers used by the motion-path group.

import { OffsetDistanceConfig, OFFSET_DISTANCE_PROPERTY_TYPE } from './OffsetDistanceConfig';
import { foldLast, layoutLength, type IRPropertyLike } from '../_shared';

export function extractOffsetDistance(properties: IRPropertyLike[]): OffsetDistanceConfig {
  return { value: foldLast(properties, OFFSET_DISTANCE_PROPERTY_TYPE, layoutLength) };
}
