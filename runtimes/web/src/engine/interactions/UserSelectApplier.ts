// UserSelectApplier.ts — emits { userSelect }.  MDN: user-select.
import type { CSSProperties } from 'react';
import type { UserSelectConfig } from './UserSelectConfig';
export function applyUserSelect(c: UserSelectConfig): CSSProperties {
  return c.value === undefined ? {} : { userSelect: c.value } as CSSProperties;
}
