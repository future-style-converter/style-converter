// ContainerNameApplier.ts — emits { containerName }.  MDN: container-name.
import type { CSSProperties } from 'react';
import type { ContainerNameConfig } from './ContainerNameConfig';
export function applyContainerName(c: ContainerNameConfig): CSSProperties {
  return c.value === undefined ? {} : { containerName: c.value } as CSSProperties;
}
