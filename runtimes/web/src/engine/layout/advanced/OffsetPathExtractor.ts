// OffsetPathExtractor.ts — serialises every basic-shape variant into its CSS form.
// Spec: https://developer.mozilla.org/docs/Web/CSS/offset-path.

import { OffsetPathConfig, OFFSET_PATH_PROPERTY_TYPE } from './OffsetPathConfig';
import { foldLast, type IRPropertyLike } from '../_shared';

function parse(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;                          // typed envelope expected
  const o = data as Record<string, unknown>;
  switch (o.type) {
    case 'none':
      return 'none';                                                                // explicit keyword
    case 'path-string':
      // path() accepts single quotes per spec; wrap the raw SVG path data.
      return typeof o.path === 'string' ? `path("${o.path}")` : undefined;
    case 'circle': {
      // circle(<radius> [at <position>]) — radius IR is a pre-formatted string.
      const r = typeof o.radius === 'string' ? o.radius : undefined;
      if (!r) return undefined;
      const at = typeof o.position === 'string' ? ` at ${o.position}` : '';         // optional position
      return `circle(${r}${at})`;
    }
    case 'ellipse': {
      // ellipse(<rx> <ry>) — rx/ry pre-formatted.
      const rx = typeof o.radiusX === 'string' ? o.radiusX : undefined;
      const ry = typeof o.radiusY === 'string' ? o.radiusY : undefined;
      return rx && ry ? `ellipse(${rx} ${ry})` : undefined;
    }
    case 'polygon': {
      // polygon(<points>) — each point is already a CSS coord pair string.
      if (!Array.isArray(o.points)) return undefined;
      const pts = (o.points as unknown[]).filter((s): s is string => typeof s === 'string');
      return pts.length ? `polygon(${pts.join(', ')})` : undefined;
    }
    case 'ray': {
      // ray() ships in two IR shapes — simple { deg:N } and typed { angle:{deg}, size }.
      if (typeof (o as Record<string, unknown>).deg === 'number') {
        return `ray(${(o as Record<string, unknown>).deg}deg)`;                     // simple form
      }
      const angle = o.angle as Record<string, unknown> | undefined;
      if (angle && typeof angle.deg === 'number') {
        const size = typeof o.size === 'string' ? ` ${o.size}` : '';                // optional ray size
        return `ray(${angle.deg}deg${size})`;
      }
      return undefined;
    }
    default:
      return undefined;                                                              // unknown shape — drop
  }
}

export function extractOffsetPath(properties: IRPropertyLike[]): OffsetPathConfig {
  return { value: foldLast(properties, OFFSET_PATH_PROPERTY_TYPE, parse) };
}
