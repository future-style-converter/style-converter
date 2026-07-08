// TopExtractor.ts — delegates to edgeExtractor(); every edge property shares
// the same length-or-keyword IR shape.

import { TopConfig, TOP_PROPERTY_TYPE } from './TopConfig';
import { edgeExtractor } from './_edge_shared';
import type { IRPropertyLike } from '../_shared';

const run = edgeExtractor(TOP_PROPERTY_TYPE);

export function extractTop(properties: IRPropertyLike[]): TopConfig {
  return run(properties);
}
