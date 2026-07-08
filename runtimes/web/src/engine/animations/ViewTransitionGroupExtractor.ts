// ViewTransitionGroupExtractor.ts — keyword + raw-ident fallback.
import { foldLast, type IRPropertyLike } from './_shared';
import { VIEW_TRANSITION_GROUP_PROPERTY_TYPE, type ViewTransitionGroupConfig } from './ViewTransitionGroupConfig';

function parseOne(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  // Recognised spec keywords are stored as {type:'<kw>'} directly.
  if (o.type === 'normal' || o.type === 'nearest' || o.type === 'contain' || o.type === 'root') {
    return o.type;
  }
  // Unknown idents (incl. `auto`, `match-element`, custom dashed-idents) land
  // in the Raw branch; we emit them verbatim.
  if (o.type === 'Raw' && typeof o.value === 'string') return o.value;
  return undefined;
}

export function extractViewTransitionGroup(properties: IRPropertyLike[]): ViewTransitionGroupConfig {
  return { value: foldLast(properties, VIEW_TRANSITION_GROUP_PROPERTY_TYPE, parseOne) };
}
