// BackgroundAttachmentApplier.ts — emits { backgroundAttachment } from config.

import type { CSSProperties } from 'react';
import type { BackgroundAttachmentConfig } from './BackgroundAttachmentConfig';

// Scoped partial.
export type BackgroundAttachmentStyles = Pick<CSSProperties, 'backgroundAttachment'>;

// Pure emitter — comma-joins per-layer keywords.
export function applyBackgroundAttachment(
  config: BackgroundAttachmentConfig,
): BackgroundAttachmentStyles {
  const out: BackgroundAttachmentStyles = {};                         // blank accumulator
  if (config.layers.length === 0) return out;                         // unset -> emit nothing
  out.backgroundAttachment = config.layers.map((l) => l.css).join(', '); // multi-layer CSS value
  return out;
}
