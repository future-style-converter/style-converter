// MasonryAutoFlowExtractor.ts — splits hyphen-joined combos into space-joined
// CSS values as the spec requires.

import { MasonryAutoFlowConfig, MASONRY_AUTO_FLOW_PROPERTY_TYPE } from './MasonryAutoFlowConfig';
import { foldLast, type IRPropertyLike } from '../_shared';

// Known keyword tokens per the spec; anything outside this set is rejected
// so the README's "parser may degrade to Raw(string)" warning can't leak
// garbage into the DOM.
const OK = new Set(['pack', 'next', 'ordered', 'definite-first']);

function parse(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;                          // expect typed wrapper
  const o = data as Record<string, unknown>;
  if (typeof o.type !== 'string') return undefined;
  // Split the IR's hyphen-joined combo into 1-or-2 tokens per the L3 grammar.
  const tokens = o.type.split('-');
  // The hyphenated variants are always two tokens from a fixed set:
  //   pack-ordered, pack-definite-first, next-ordered, next-definite-first,
  //   definite-first (single keyword, two-word).
  // Re-glue pairs when they form the single 'definite-first' keyword.
  const out: string[] = [];
  for (let i = 0; i < tokens.length; i++) {
    if (tokens[i] === 'definite' && tokens[i + 1] === 'first') {
      out.push('definite-first');
      i++;
    } else {
      out.push(tokens[i]);
    }
  }
  // Sanity-check every emitted token against the allow-list.
  for (const t of out) if (!OK.has(t)) return undefined;                             // bail on unknown
  return out.join(' ');                                                              // CSS space-joined
}

export function extractMasonryAutoFlow(properties: IRPropertyLike[]): MasonryAutoFlowConfig {
  return { value: foldLast(properties, MASONRY_AUTO_FLOW_PROPERTY_TYPE, parse) };
}
