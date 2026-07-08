// ContainerApplier.ts — emits { container }.  MDN: container.
import type { CSSProperties } from 'react';
import type { ContainerConfig } from './ContainerConfig';
export function applyContainer(c: ContainerConfig): CSSProperties {
  return c.value === undefined ? {} : { container: c.value } as CSSProperties;
}
