// ContainerTypeApplier.ts — emits { containerType }.  MDN: container-type.
import type { CSSProperties } from 'react';
import type { ContainerTypeConfig } from './ContainerTypeConfig';
export function applyContainerType(c: ContainerTypeConfig): CSSProperties {
  return c.value === undefined ? {} : { containerType: c.value } as CSSProperties;
}
