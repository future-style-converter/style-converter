// InsetBlockStartExtractor.ts — delegates to edgeExtractor(); every edge property shares
// the same length-or-keyword IR shape.

import { InsetBlockStartConfig, INSET_BLOCK_START_PROPERTY_TYPE } from './InsetBlockStartConfig';
import { edgeExtractor } from './_edge_shared';
import type { IRPropertyLike } from '../_shared';

const run = edgeExtractor(INSET_BLOCK_START_PROPERTY_TYPE);

export function extractInsetBlockStart(properties: IRPropertyLike[]): InsetBlockStartConfig {
  return run(properties);
}
