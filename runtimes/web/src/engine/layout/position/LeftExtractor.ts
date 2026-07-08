// LeftExtractor.ts — delegates to edgeExtractor(); every edge property shares
// the same length-or-keyword IR shape.

import { LeftConfig, LEFT_PROPERTY_TYPE } from './LeftConfig';
import { edgeExtractor } from './_edge_shared';
import type { IRPropertyLike } from '../_shared';

const run = edgeExtractor(LEFT_PROPERTY_TYPE);

export function extractLeft(properties: IRPropertyLike[]): LeftConfig {
  return run(properties);
}
