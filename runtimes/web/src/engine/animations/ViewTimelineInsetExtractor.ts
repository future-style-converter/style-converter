// ViewTimelineInsetExtractor.ts — start + optional end (`auto` | length | %).
import { foldLast, scalarOrLengthToCss, type IRPropertyLike } from './_shared';
import { VIEW_TIMELINE_INSET_PROPERTY_TYPE, type ViewTimelineInsetConfig } from './ViewTimelineInsetConfig';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  const start = scalarOrLengthToCss(o.start);                                   // `auto` maps via shared helper
  if (start === undefined) return undefined;
  const end = scalarOrLengthToCss(o.end);
  // Per spec, a single value applies to both edges.  Collapse `x x` -> `x`
  // to keep the output compact when start and end match verbatim.
  if (end === undefined || end === start) return start;
  return `${start} ${end}`;
}

export function extractViewTimelineInset(properties: IRPropertyLike[]): ViewTimelineInsetConfig {
  return { value: foldLast(properties, VIEW_TIMELINE_INSET_PROPERTY_TYPE, parseOne) };
}
