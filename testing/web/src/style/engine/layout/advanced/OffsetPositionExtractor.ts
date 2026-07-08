// OffsetPositionExtractor.ts — emit keyword or position pair per spec.

import { OffsetPositionConfig, OFFSET_POSITION_PROPERTY_TYPE } from './OffsetPositionConfig';
import { foldLast, type IRPropertyLike } from '../_shared';
import { positionPair } from './_offset_shared';

function parse(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  if (o.type === 'auto' || o.type === 'normal') return String(o.type);              // bare keywords
  if (o.type === 'position') return positionPair(data);                             // two-axis
  return undefined;
}

export function extractOffsetPosition(properties: IRPropertyLike[]): OffsetPositionConfig {
  return { value: foldLast(properties, OFFSET_POSITION_PROPERTY_TYPE, parse) };
}
