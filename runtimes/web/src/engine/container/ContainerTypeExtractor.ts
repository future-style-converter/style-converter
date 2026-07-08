// ContainerTypeExtractor.ts — folds IR `ContainerType` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ContainerTypeConfig } from './ContainerTypeConfig';
export function extractContainerType(properties: IRPropertyLike[]): ContainerTypeConfig {
  return { value: foldLast(properties, 'ContainerType', keywordOrRaw) };
}
