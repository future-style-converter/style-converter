// InsetInlineStartExtractor.ts — delegates to edgeExtractor(); every edge property shares
// the same length-or-keyword IR shape.

import { InsetInlineStartConfig, INSET_INLINE_START_PROPERTY_TYPE } from './InsetInlineStartConfig';
import { edgeExtractor } from './_edge_shared';
import type { IRPropertyLike } from '../_shared';

const run = edgeExtractor(INSET_INLINE_START_PROPERTY_TYPE);

export function extractInsetInlineStart(properties: IRPropertyLike[]): InsetInlineStartConfig {
  return run(properties);
}
