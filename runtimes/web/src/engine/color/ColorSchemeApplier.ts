// ColorSchemeApplier.ts — emits the native CSS `color-scheme` declaration.
// One-key emitter: csstype's Property.ColorScheme accepts arbitrary strings
// (the grammar is open-ended: normal | [light|dark|<custom-ident>]+ && only?).
import type { CSSProperties } from 'react';
import type { ColorSchemeConfig } from './ColorSchemeConfig';

// Pure function — emit only when the extractor produced a value.
export function applyColorScheme(c: ColorSchemeConfig): CSSProperties {
  if (c.value === undefined) return {};                             // unset → no declaration
  return { colorScheme: c.value };                                  // e.g. 'light dark'
}
