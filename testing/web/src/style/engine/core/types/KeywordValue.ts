// KeywordValue.ts - Keyword primitive extractor.
// Keywords appear in many IR property shapes. Example fixtures:
//   examples/primitives/lengths-intrinsic.json -> bare strings 'auto'|'min-content'|'max-content'
//   Many enum-valued properties serialise as either a bare string or { keyword:'flex-start' }.
// Our job: collapse these into a single normalised lowercase+hyphenated form so callers
// can compare against static literals without per-property casing logic.

// Wrapped result so callers see a consistent { normalized } shape.
export interface KeywordValue { normalized: string }

// Canonicalise a raw keyword string: trim -> lowercase -> collapse whitespace/underscores to '-'.
// Matches how CSS enum values are spelled in the spec (kebab-case).
function canonicalise(raw: string): string {
  return raw
    .trim()                                                         // strip incidental whitespace
    .toLowerCase()                                                  // case-insensitive per CSS
    .replace(/[\s_]+/g, '-');                                       // 'min_content' / 'min content' -> 'min-content'
}

// Main entrypoint. Returns null when the input carries no identifiable keyword.
export function extractKeyword(data: unknown): KeywordValue | null {
  if (data === null || data === undefined) return null;

  // Bare string is the most common case (e.g. width: 'auto').
  if (typeof data === 'string') {
    if (data.length === 0) return null;                             // empty string is not a keyword
    return { normalized: canonicalise(data) };
  }

  if (typeof data !== 'object') return null;                        // numbers etc. are not keywords
  const obj = data as Record<string, unknown>;

  // IR shapes store keywords under various keys — try the common ones in order of specificity.
  // 'keyword' is the preferred IR key; 'value' is used by several enum properties; 'type' is a
  // last-resort fallback for shapes where 'type' doubles as a discriminator AND the keyword itself.
  if (typeof obj.keyword === 'string') return { normalized: canonicalise(obj.keyword) };
  if (typeof obj.value === 'string')   return { normalized: canonicalise(obj.value) };
  if (typeof obj.type === 'string')    return { normalized: canonicalise(obj.type) };

  return null;                                                      // no keyword found
}
