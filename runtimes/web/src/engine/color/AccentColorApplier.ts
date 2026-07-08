// AccentColorApplier.ts — turns an AccentColorConfig into { accentColor }.
// For the 'auto' variant we omit the property entirely so the browser default
// (which varies by UA + dark mode) keeps applying.

import type { CSSProperties } from 'react';
import { colorToCss } from './DynamicColorCss';
import type { AccentColorConfig } from './AccentColorConfig';

// Partial CSSProperties scoped to our single field.
export type AccentColorStyles = Pick<CSSProperties, 'accentColor'>;

// Emit the color string only when the user asked for an explicit color.
export function applyAccentColor(config: AccentColorConfig): AccentColorStyles {
  const out: AccentColorStyles = {};                                  // blank accumulator
  if (!config.mode) return out;                                       // unset -> emit nothing
  if (config.mode.kind === 'auto') return out;                        // 'auto' -> omit to use UA default
  out.accentColor = colorToCss(config.mode.color);                    // color -> rgba(...) or dynamic
  return out;
}
