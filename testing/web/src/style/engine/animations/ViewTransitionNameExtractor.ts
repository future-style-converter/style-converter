// ViewTransitionNameExtractor.ts — none | ident.
import { foldLast, type IRPropertyLike } from './_shared';
import { VIEW_TRANSITION_NAME_PROPERTY_TYPE, type ViewTransitionNameConfig } from './ViewTransitionNameConfig';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  if (o.type === 'none') return 'none';
  if (o.type === 'named' && typeof o.name === 'string') return o.name;          // raw ident (e.g. `auto`, `--hero`)
  return undefined;
}

export function extractViewTransitionName(properties: IRPropertyLike[]): ViewTransitionNameConfig {
  return { value: foldLast(properties, VIEW_TRANSITION_NAME_PROPERTY_TYPE, parseOne) };
}
