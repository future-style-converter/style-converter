// BottomExtractor.ts — delegates to edgeExtractor(); every edge property shares
// the same length-or-keyword IR shape.

import { BottomConfig, BOTTOM_PROPERTY_TYPE } from './BottomConfig';
import { edgeExtractor } from './_edge_shared';
import type { IRPropertyLike } from '../_shared';

const run = edgeExtractor(BOTTOM_PROPERTY_TYPE);

export function extractBottom(properties: IRPropertyLike[]): BottomConfig {
  return run(properties);
}
