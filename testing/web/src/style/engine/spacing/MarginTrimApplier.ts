// MarginTrimApplier.ts — MarginTrimConfig → React inline-style partial.
// CSS `margin-trim` is a native property (Level 4); we simply emit it.
// Some browsers ignore it at render time — that's fine, we still declare it.

import type { MarginTrimConfig } from './MarginTrimConfig';

// React's CSSProperties type only accepts a narrow subset of `margin-trim`
// values (missing 'block', 'block-start', etc.) that predates CSS Level 4,
// so we declare our own loose shape rather than fighting the React types.
export interface MarginTrimStyles {
  marginTrim?: string;
}

// Pure function — emits a single marginTrim key when config is present.
export function applyMarginTrim(config: MarginTrimConfig): MarginTrimStyles {
  // Single-key output — callers spread this into the aggregate style object.
  return { marginTrim: config.value };
}
