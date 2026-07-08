// BlendModeApplier.ts — emits mixBlendMode + backgroundBlendMode from the
// BlendModeConfig produced by BlendModeExtractor.

import type { CSSProperties } from 'react';
import type { BlendModeConfig } from './BlendModeConfig';

// Restricted partial covering only our two keys.
export type BlendModeStyles = Pick<CSSProperties, 'mixBlendMode' | 'backgroundBlendMode'>;

// Pure emitter.
export function applyBlendMode(config: BlendModeConfig): BlendModeStyles {
  const out: BlendModeStyles = {};                                    // blank accumulator
  if (config.mix) out.mixBlendMode = config.mix as CSSProperties['mixBlendMode'];
  if (config.background && config.background.length > 0) {            // only when any layers
    out.backgroundBlendMode = config.background.join(', ');           // comma-joined list
  }
  return out;
}
