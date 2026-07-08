// ContainerNameExtractor.ts — folds IR `ContainerName` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { ContainerNameConfig } from './ContainerNameConfig';
export function extractContainerName(properties: IRPropertyLike[]): ContainerNameConfig {
  return { value: foldLast(properties, 'ContainerName', keywordOrRaw) };
}
