// MarginTrimConfig.ts — CSS Level 4 `margin-trim` property config.
// The IR emits a bare SCREAMING_SNAKE_CASE string keyword; we normalise to
// the canonical CSS spelling (kebab-case, lower-case).

// CSS spec-permitted values.  Combined forms like "block inline" are NOT
// parsed by the current CSS layer (per Phase-2 survey); treat as unsupported.
export type MarginTrimKeyword =
  | 'none' | 'block' | 'inline'
  | 'block-start' | 'block-end'
  | 'inline-start' | 'inline-end';

// Trivial config — a single keyword.  Kept as a record for consistency with
// the rest of the engine so callers can treat every Config uniformly.
export interface MarginTrimConfig {
  value: MarginTrimKeyword;
}

// IR property-type recognised by this module.
export const MARGIN_TRIM_PROPERTY_TYPES = ['MarginTrim'] as const;
export type MarginTrimPropertyType = (typeof MARGIN_TRIM_PROPERTY_TYPES)[number];
