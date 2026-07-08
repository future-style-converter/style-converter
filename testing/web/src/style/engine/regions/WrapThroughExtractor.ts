// WrapThroughExtractor.ts — folds IR `WrapThrough` values via shared keywordOrRaw.
import { foldLast, keywordOrRaw, type IRPropertyLike } from '../_phase10_shared';
import type { WrapThroughConfig } from './WrapThroughConfig';
export function extractWrapThrough(properties: IRPropertyLike[]): WrapThroughConfig {
  return { value: foldLast(properties, 'WrapThrough', keywordOrRaw) };
}
