// BackdropFilterApplier.ts — emits native `backdropFilter` AND the
// `WebkitBackdropFilter` prefix for Safari.  csstype has backdropFilter; the
// -webkit- prefix key isn't in the types so we widen via Record<string,string>.
// MDN: https://developer.mozilla.org/docs/Web/CSS/backdrop-filter#browser_compatibility.
import type { BackdropFilterConfig } from './BackdropFilterConfig';

export function applyBackdropFilter(config: BackdropFilterConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return {
    backdropFilter: config.value,                                                    // standard property
    WebkitBackdropFilter: config.value,                                              // Safari vendor prefix
  };
}
