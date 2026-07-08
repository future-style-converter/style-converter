// OverflowInlineApplier.ts — Record<string,string> widening (csstype lag).
// MDN: https://developer.mozilla.org/docs/Web/CSS/overflow-inline.
import type { OverflowInlineConfig } from './OverflowInlineConfig';

export function applyOverflowInline(config: OverflowInlineConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { overflowInline: config.value };
}
