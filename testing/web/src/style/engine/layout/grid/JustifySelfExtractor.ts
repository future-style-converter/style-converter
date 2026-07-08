// JustifySelfExtractor.ts — parses {type:'…'} wrappers into kebab keywords.

import { JustifySelfConfig, JUSTIFY_SELF_PROPERTY_TYPE } from './JustifySelfConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

function parse(data: unknown): string | undefined {
  if (typeof data === 'string') return kebab(data);                                 // defensive bare form
  if (data && typeof data === 'object') {
    const o = data as Record<string, unknown>;
    if (typeof o.type === 'string') return kebab(o.type);                           // {type:'start'} etc.
  }
  return undefined;
}

export function extractJustifySelf(properties: IRPropertyLike[]): JustifySelfConfig {
  return { value: foldLast(properties, JUSTIFY_SELF_PROPERTY_TYPE, parse) };
}
