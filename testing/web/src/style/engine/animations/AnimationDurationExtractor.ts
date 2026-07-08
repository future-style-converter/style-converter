// AnimationDurationExtractor.ts — IR -> AnimationDurationConfig.
// IR sum type: Durations (list of IRTime), Keyword (e.g. `auto`), Expression
// (raw `calc(...)`/`var(--x)`).  See AnimationDurationPropertyParser.kt.
import { foldLast, timeListToCss, type IRPropertyLike } from './_shared';
import { ANIMATION_DURATION_PROPERTY_TYPE, type AnimationDurationConfig } from './AnimationDurationConfig';

// The kotlinx.serialization `type` discriminator uses fully-qualified class
// names — we match on suffixes to stay resilient to package refactors.
const DURATIONS_SUFFIX = '.Durations';
const KEYWORD_SUFFIX   = '.Keyword';
const EXPR_SUFFIX      = '.Expression';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;                      // defensive
  const o = data as Record<string, unknown>;
  const t = typeof o.type === 'string' ? o.type : '';
  if (t.endsWith(DURATIONS_SUFFIX)) return timeListToCss(o.durations);          // normal case
  if (t.endsWith(KEYWORD_SUFFIX)   && typeof o.keyword === 'string') return o.keyword;
  if (t.endsWith(EXPR_SUFFIX)      && typeof o.expr === 'string') return o.expr;
  return undefined;
}

export function extractAnimationDuration(properties: IRPropertyLike[]): AnimationDurationConfig {
  return { value: foldLast(properties, ANIMATION_DURATION_PROPERTY_TYPE, parseOne) };
}
