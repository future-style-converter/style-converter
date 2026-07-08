// RightExtractor.ts — delegates to edgeExtractor(); every edge property shares
// the same length-or-keyword IR shape.

import { RightConfig, RIGHT_PROPERTY_TYPE } from './RightConfig';
import { edgeExtractor } from './_edge_shared';
import type { IRPropertyLike } from '../_shared';

const run = edgeExtractor(RIGHT_PROPERTY_TYPE);

export function extractRight(properties: IRPropertyLike[]): RightConfig {
  return run(properties);
}
