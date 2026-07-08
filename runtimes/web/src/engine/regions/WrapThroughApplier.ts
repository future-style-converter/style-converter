// WrapThroughApplier.ts — csstype-widened.  See https://developer.mozilla.org/docs/Web/CSS/wrap-through.
import type { CSSProperties } from 'react';
import type { WrapThroughConfig } from './WrapThroughConfig';
export function applyWrapThrough(c: WrapThroughConfig): Record<string, string> {
  if (c.value === undefined) return {};
  return ({ wrapThrough: c.value } as unknown as CSSProperties) as Record<string, string>;
}
