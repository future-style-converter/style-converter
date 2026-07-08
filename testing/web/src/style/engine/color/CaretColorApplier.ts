// CaretColorApplier.ts — emits { caretColor } from a CaretColorConfig.
// 'auto' is omitted so the browser keeps its default caret color.

import type { CSSProperties } from 'react';
import { colorToCss } from './DynamicColorCss';
import type { CaretColorConfig } from './CaretColorConfig';

// Output restricted to the single CSS field we populate.
export type CaretColorStyles = Pick<CSSProperties, 'caretColor'>;

// Pure emitter.
export function applyCaretColor(config: CaretColorConfig): CaretColorStyles {
  const out: CaretColorStyles = {};                                   // blank accumulator
  if (!config.mode) return out;                                       // unset -> emit nothing
  if (config.mode.kind === 'auto') return out;                        // 'auto' -> defer to UA default
  out.caretColor = colorToCss(config.mode.color);                     // emit reconstructed color
  return out;
}
