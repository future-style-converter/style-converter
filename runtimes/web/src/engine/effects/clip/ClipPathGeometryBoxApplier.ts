// ClipPathGeometryBoxApplier.ts — emits a standalone `clipPath` value from the
// box keyword alone, matching CSS Shapes §5 "<geometry-box>" form
// (https://drafts.csswg.org/css-shapes/#typedef-shape-box).
// If `ClipPath` is also present, its applier runs later in the dispatch and
// overrides this value.
import type { CSSProperties } from 'react';
import type { ClipPathGeometryBoxConfig } from './ClipPathGeometryBoxConfig';

export type ClipPathGeometryBoxStyles = Pick<CSSProperties, 'clipPath'>;

export function applyClipPathGeometryBox(config: ClipPathGeometryBoxConfig): ClipPathGeometryBoxStyles {
  if (config.value === undefined) return {};
  return { clipPath: config.value };                                                 // e.g. 'border-box'
}
