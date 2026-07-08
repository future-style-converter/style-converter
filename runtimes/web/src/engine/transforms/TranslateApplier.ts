// TranslateApplier.ts — csstype lacks `translate` longhand key (MDN:
// https://developer.mozilla.org/docs/Web/CSS/translate).  Widen to Record.

import type { TranslateConfig } from './TranslateConfig';

export function applyTranslate(config: TranslateConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { translate: config.value };
}
