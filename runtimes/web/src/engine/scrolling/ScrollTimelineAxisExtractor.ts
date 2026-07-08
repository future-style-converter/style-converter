// ScrollTimelineAxisExtractor.ts — enum kebab.
import { foldLast, kebabEnum, type IRPropertyLike } from '../animations/_shared';
import { SCROLL_TIMELINE_AXIS_PROPERTY_TYPE, type ScrollTimelineAxisConfig } from './ScrollTimelineAxisConfig';
export function extractScrollTimelineAxis(properties: IRPropertyLike[]): ScrollTimelineAxisConfig {
  return { value: foldLast(properties, SCROLL_TIMELINE_AXIS_PROPERTY_TYPE, kebabEnum) };
}
