/**
 * RootErrorBoundary — per-root render-error containment for
 * @style-converter/web.
 *
 * WHY THIS EXISTS: a document renders as a forest of composed roots, and
 * React unmounts the ENTIRE tree when any one component throws during
 * render. In a capture context that is catastrophic: the capture-ready
 * sentinel renders as a sibling of the component list, so one malformed
 * component silently converts a 100-test WPT run into 100 `no-data` rows
 * (this happened — a raw-map `pseudos` payload crashed buildStyles and
 * zeroed an entire smoke run). Wrapping EACH root means a bad component
 * fails alone, visibly, while its 99 neighbours still render and measure.
 *
 * The fallback is deliberately conspicuous, not blank: a fixed-size box
 * carrying `data-render-error` (tooling greps for it; SSIM fails it
 * against any real reference) — the "no silent fallthroughs" rule
 * applied to render crashes. The error itself is logged to the console
 * so capture logs retain the stack.
 *
 * Class component by necessity: error boundaries are the one React
 * feature with no hook equivalent. createElement-only (no JSX) like the
 * rest of the renderer.
 */

import { Component, createElement } from 'react';
import type { ReactNode } from 'react';

/** Props: the subtree to contain + an identity for diagnostics. */
export interface RootErrorBoundaryProps {
  /** Component id (or any stable label) surfaced on the fallback box. */
  componentId?: string;
  /** The root subtree this boundary contains. */
  children?: ReactNode;
}

/** State: the caught render error, if any. */
interface RootErrorBoundaryState {
  error: Error | null;
}

export class RootErrorBoundary extends Component<RootErrorBoundaryProps, RootErrorBoundaryState> {
  // No error until React reports one via getDerivedStateFromError.
  state: RootErrorBoundaryState = { error: null };

  /** React's render-error hook: capture the error, trigger the fallback. */
  static getDerivedStateFromError(error: Error): RootErrorBoundaryState {
    return { error };
  }

  /** Log with the component identity so capture logs pinpoint the root. */
  componentDidCatch(error: Error): void {
    console.error(`[RootErrorBoundary] root "${this.props.componentId ?? '?'}" crashed during render:`, error);
  }

  render(): ReactNode {
    if (this.state.error) {
      // Conspicuous fixed-size fallback — never pixel-matches a real
      // reference, so the failure is measured, not masked.
      return createElement('div', {
        'data-render-error': String(this.state.error.message ?? this.state.error),
        'data-component-id': this.props.componentId,
        style: { width: '100px', height: '30px', background: '#7a1f1f', color: '#fff', font: '10px monospace', overflow: 'hidden' },
      }, 'render-error');
    }
    // No error: transparent passthrough — adds zero DOM of its own, so
    // markup (and the RendererParity byte contract) is unchanged.
    return this.props.children;
  }
}
