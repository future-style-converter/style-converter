// ContainerExtractor.ts — folds IR `Container` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ContainerConfig } from './ContainerConfig';
export function extractContainer(properties: IRPropertyLike[]): ContainerConfig {
  return { value: foldLast(properties, 'Container', keywordOrRaw) };
}
