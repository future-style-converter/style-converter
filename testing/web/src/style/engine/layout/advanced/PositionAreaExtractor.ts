// PositionAreaExtractor.ts — fold row + column into a single CSS string.

import { PositionAreaConfig, POSITION_AREA_PROPERTY_TYPE } from './PositionAreaConfig';
import { foldLast, type IRPropertyLike } from '../_shared';

function axis(d: unknown): string | undefined {
  if (!d || typeof d !== 'object') return undefined;
  const o = d as Record<string, unknown>;
  if (typeof o.type === 'string') return o.type;                                    // parser already kebabs
  return undefined;
}

function parse(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  const r = axis(o.row);
  const c = axis(o.column);
  if (r && c) return `${r} ${c}`;                                                   // canonical two-value form
  return undefined;
}

export function extractPositionArea(properties: IRPropertyLike[]): PositionAreaConfig {
  return { value: foldLast(properties, POSITION_AREA_PROPERTY_TYPE, parse) };
}
