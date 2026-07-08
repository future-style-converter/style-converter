// _dispatch.ts — Phase-10 global (CSS `all` longhand-reset) dispatch.
import type { CSSProperties } from 'react';
import { extractAll } from './AllExtractor';
import { applyAll } from './AllApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyGlobalPhase10(properties: IRPropertyLike[]): CSSProperties {
  return applyAll(extractAll(properties));
}
