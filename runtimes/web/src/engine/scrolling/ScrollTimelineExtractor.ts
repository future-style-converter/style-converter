// ScrollTimelineExtractor.ts — emits "<name>" or "<name> <axis>".
import { foldLast, kebabEnum, type IRPropertyLike } from '../animations/_shared';
import { SCROLL_TIMELINE_PROPERTY_TYPE, type ScrollTimelineConfig } from './ScrollTimelineConfig';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  // `name` is a wrapper object { name: string } — mirror ScrollTimelineName.
  const nameWrap = o.name as Record<string, unknown> | undefined;
  const name = nameWrap && typeof nameWrap.name === 'string' ? nameWrap.name : undefined;
  if (!name) return undefined;
  const axis = kebabEnum(o.axis);                                               // SHOUTY -> kebab
  return axis ? `${name} ${axis}` : name;
}

export function extractScrollTimeline(properties: IRPropertyLike[]): ScrollTimelineConfig {
  return { value: foldLast(properties, SCROLL_TIMELINE_PROPERTY_TYPE, parseOne) };
}
