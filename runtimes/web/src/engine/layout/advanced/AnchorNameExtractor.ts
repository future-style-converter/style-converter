// AnchorNameExtractor.ts — fold IR into a CSS string.

import { AnchorNameConfig, ANCHOR_NAME_PROPERTY_TYPE } from './AnchorNameConfig';
import { foldLast, type IRPropertyLike } from '../_shared';

function parse(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  if (o.type === 'none') return 'none';                                             // keyword
  if (o.type === 'single' && typeof o.name === 'string') return o.name;             // one <dashed-ident>
  if (o.type === 'multiple' && Array.isArray(o.names)) {
    const list = (o.names as unknown[]).filter((s): s is string => typeof s === 'string');
    return list.length ? list.join(', ') : undefined;                               // CSSWG comma-separated form
  }
  return undefined;
}

export function extractAnchorName(properties: IRPropertyLike[]): AnchorNameConfig {
  return { value: foldLast(properties, ANCHOR_NAME_PROPERTY_TYPE, parse) };
}
