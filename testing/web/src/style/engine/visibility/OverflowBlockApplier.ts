// OverflowBlockApplier.ts — csstype lacks `overflowBlock` on old versions; use
// Record<string,string> widening.  See MDN: overflow-block (CSS Overflow 3).
import type { OverflowBlockConfig } from './OverflowBlockConfig';

export function applyOverflowBlock(config: OverflowBlockConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { overflowBlock: config.value };                                           // React camelCase key
}
