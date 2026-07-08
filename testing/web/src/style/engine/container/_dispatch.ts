// _dispatch.ts — Phase-10 container long-tail dispatch (3 properties).
import type { CSSProperties } from 'react';
import { extractContainer } from './ContainerExtractor';
import { applyContainer } from './ContainerApplier';
import { extractContainerName } from './ContainerNameExtractor';
import { applyContainerName } from './ContainerNameApplier';
import { extractContainerType } from './ContainerTypeExtractor';
import { applyContainerType } from './ContainerTypeApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyContainerPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyContainer(extractContainer(properties)));
  Object.assign(out, applyContainerName(extractContainerName(properties)));
  Object.assign(out, applyContainerType(extractContainerType(properties)));
  return out;
}
