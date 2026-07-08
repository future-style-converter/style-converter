// ObjectPositionApplier.ts — emits { objectPosition }.  MDN: object-position.
import type { CSSProperties } from 'react';
import type { ObjectPositionConfig } from './ObjectPositionConfig';
export function applyObjectPosition(c: ObjectPositionConfig): CSSProperties {
  return c.value === undefined ? {} : { objectPosition: c.value } as CSSProperties;
}
