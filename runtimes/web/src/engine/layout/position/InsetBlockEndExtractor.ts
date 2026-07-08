// InsetBlockEndExtractor.ts — delegates to edgeExtractor(); every edge property shares
// the same length-or-keyword IR shape.

import { InsetBlockEndConfig, INSET_BLOCK_END_PROPERTY_TYPE } from './InsetBlockEndConfig';
import { edgeExtractor } from './_edge_shared';
import type { IRPropertyLike } from '../_shared';

const run = edgeExtractor(INSET_BLOCK_END_PROPERTY_TYPE);

export function extractInsetBlockEnd(properties: IRPropertyLike[]): InsetBlockEndConfig {
  return run(properties);
}
