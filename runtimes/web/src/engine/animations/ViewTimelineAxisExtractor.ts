// ViewTimelineAxisExtractor.ts — enum kebab.
import { foldLast, kebabEnum, type IRPropertyLike } from './_shared';
import { VIEW_TIMELINE_AXIS_PROPERTY_TYPE, type ViewTimelineAxisConfig } from './ViewTimelineAxisConfig';
export function extractViewTimelineAxis(properties: IRPropertyLike[]): ViewTimelineAxisConfig {
  return { value: foldLast(properties, VIEW_TIMELINE_AXIS_PROPERTY_TYPE, kebabEnum) };
}
