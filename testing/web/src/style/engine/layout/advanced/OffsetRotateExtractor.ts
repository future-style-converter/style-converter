// OffsetRotateExtractor.ts — emit per-variant per spec grammar.

import { OffsetRotateConfig, OFFSET_ROTATE_PROPERTY_TYPE } from './OffsetRotateConfig';
import { foldLast, type IRPropertyLike } from '../_shared';

function parse(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  switch (o.type) {
    case 'auto':
      return 'auto';
    case 'reverse':
      return 'reverse';
    case 'angle':
      // Parser always normalises to deg; emit the canonical CSS angle unit.
      return typeof o.deg === 'number' ? `${o.deg}deg` : undefined;
    case 'auto-angle': {
      // `auto [<angle>]?` or `reverse [<angle>]?` with optional angle tail.
      const base = o.reverse ? 'reverse' : (o.auto ? 'auto' : undefined);
      if (!base) return undefined;
      const angle = o.angle as Record<string, unknown> | undefined;
      if (angle && typeof angle.deg === 'number') return `${base} ${angle.deg}deg`; // with angle
      return base;                                                                  // keyword-only
    }
    default:
      return undefined;
  }
}

export function extractOffsetRotate(properties: IRPropertyLike[]): OffsetRotateConfig {
  return { value: foldLast(properties, OFFSET_ROTATE_PROPERTY_TYPE, parse) };
}
