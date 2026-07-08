// MarginTrimExtractor.ts — parse the MarginTrim IR property into a MarginTrimConfig.
// IR carries a bare string like "NONE", "BLOCK", "INLINE_START", ...; we
// downcase and replace underscores with hyphens to match CSS spelling.

import type { MarginTrimConfig, MarginTrimKeyword, MarginTrimPropertyType } from './MarginTrimConfig';

// Local IR-property shape alias.
interface IRPropertyLike { type: string; data: unknown; }

// Renderer predicate — only one type in this family.
export function isMarginTrimProperty(type: string): type is MarginTrimPropertyType {
  return type === 'MarginTrim';
}

// Set of valid CSS keywords.  Guards against IR regressions introducing new
// tokens we haven't reviewed (e.g. the spec-proposed "block inline" combo).
const VALID_KEYWORDS = new Set<MarginTrimKeyword>([
  'none', 'block', 'inline',
  'block-start', 'block-end',
  'inline-start', 'inline-end',
]);

// Public entrypoint — first matching MarginTrim wins (CSS cascade last-wins
// is applied upstream, so properties arrive pre-cascaded).
export function extractMarginTrim(properties: IRPropertyLike[]): MarginTrimConfig | null {
  // Scan from the start; caller may also post-filter by isMarginTrimProperty.
  for (const p of properties) {
    if (!isMarginTrimProperty(p.type)) continue;                      // skip unrelated
    if (typeof p.data !== 'string') continue;                         // defensive: IR is usually a string
    // IR: "INLINE_START" → CSS: "inline-start".
    const normalised = p.data.toLowerCase().replace(/_/g, '-');
    // Reject unknown tokens rather than emitting a broken style.
    if (!VALID_KEYWORDS.has(normalised as MarginTrimKeyword)) continue;
    return { value: normalised as MarginTrimKeyword };                // first-match return
  }
  return null;                                                        // no MarginTrim → no config
}
