// InsetInlineEndExtractor.ts — delegates to edgeExtractor(); every edge property shares
// the same length-or-keyword IR shape.

import { InsetInlineEndConfig, INSET_INLINE_END_PROPERTY_TYPE } from './InsetInlineEndConfig';
import { edgeExtractor } from './_edge_shared';
import type { IRPropertyLike } from '../_shared';

const run = edgeExtractor(INSET_INLINE_END_PROPERTY_TYPE);

export function extractInsetInlineEnd(properties: IRPropertyLike[]): InsetInlineEndConfig {
  return run(properties);
}
