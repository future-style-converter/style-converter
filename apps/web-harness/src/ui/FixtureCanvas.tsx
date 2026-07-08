/**
 * FixtureCanvas — single-component chromeless render for Tier 5
 * (interaction state) testing.
 *
 * Visited via `?fixture=<ComponentName>`. The Puppeteer driver in
 * testing/interaction-states.mjs grabs `[data-testid="<ComponentName>"]`,
 * fires a state event (.hover / .focus / .click etc.), then element-screenshots.
 *
 * The wrapper div carries:
 *   - data-testid="<ComponentName>" — Puppeteer selector contract
 *   - tabIndex={0}                  — so .focus() works on a non-form div
 *   - data-fixture-ready="1"        — sentinel; set after the component mounts
 *                                     so Puppeteer can `waitForSelector` before
 *                                     firing the state event (otherwise we race
 *                                     against React's first paint)
 *
 * Mirrors the CaptureCanvas contract (390px wide, #1A1A2E background,
 * 16px padding, overflow:hidden) so cross-platform pixel-diffing semantics
 * match Tier 1 / Tier 3 captures.
 */

import React from 'react';
import type { IRComponent, IRDocument } from '@style-converter/web/core/ir/IRModels';
import { ComponentRenderer } from '../sdui/ComponentRenderer';

interface FixtureCanvasProps {
  document: IRDocument;
  fixtureName: string;
}

/**
 * Find the named component anywhere in the IR tree (including nested
 * children). The fixture-conversion pipeline produces single-component
 * docs, but the IR model permits trees, so walk defensively.
 */
function findByName(components: IRComponent[], name: string): IRComponent | null {
  for (const c of components) {
    if (c.name === name || c.id === name) return c;
    if (c.children) {
      const found = findByName(c.children, name);
      if (found) return found;
    }
  }
  return null;
}

export function FixtureCanvas({ document, fixtureName }: FixtureCanvasProps) {
  // Memoise the lookup so the sentinel useEffect below doesn't re-fire on
  // every render (React would otherwise see a fresh component reference).
  const component = React.useMemo(
    () => findByName(document.components, fixtureName),
    [document, fixtureName]
  );

  // Once the component has mounted, flip the data-fixture-ready attribute
  // on the wrapper. We do this in a useEffect (not as a static prop) so
  // Puppeteer's waitForSelector('[data-fixture-ready="1"]') only resolves
  // after React has committed the first render and the browser has had a
  // chance to paint. This avoids racing against the first state event.
  const wrapperRef = React.useRef<HTMLDivElement>(null);
  React.useEffect(() => {
    if (wrapperRef.current && component) {
      // Round 75: switched from requestAnimationFrame to setTimeout. In
      // headless Chrome (the default puppeteer launch mode) RAF callbacks
      // are throttled or outright suppressed when the page isn't actually
      // being painted to a display — same root cause CaptureGallery already
      // documents (see CaptureGallery.tsx ~L117 "Brief post-render settle"
      // comment). The previous double-RAF would hang indefinitely in
      // headless mode → puppeteer waitForSelector('[data-fixture-ready="1"]')
      // hits its 5-second timeout. setTimeout stays on the JS task queue
      // and is not throttled the same way; 50 ms gives layout + paint a
      // chance to settle before puppeteer screenshots.
      const t = setTimeout(() => {
        wrapperRef.current?.setAttribute('data-fixture-ready', '1');
      }, 50);
      return () => clearTimeout(t);
    }
  }, [component]);

  if (!component) {
    return (
      <div data-fixture-error={`component-not-found:${fixtureName}`} style={errorStyle}>
        Component "{fixtureName}" not found in fixture IR.
      </div>
    );
  }

  return (
    <div
      ref={wrapperRef}
      data-testid={fixtureName}
      data-fixture-name={fixtureName}
      tabIndex={0}
      style={canvasStyle}
    >
      <ComponentRenderer component={component} />
    </div>
  );
}

/** Mirrors CaptureCanvas exactly (see CaptureGallery.tsx for rationale).
 *
 *  Round 48 (per Auditor 48 recommendation f): mirror CaptureCanvas's
 *  round-47 `transform: translateZ(0)` + `position: relative` defensive
 *  pair. FixtureCanvas only renders ONE component at a time so the
 *  fixed-position-bleed problem CaptureGallery had can't manifest here
 *  in the same way (no neighbor canvases to contaminate). But if the
 *  fixture's component itself uses `position: fixed`, the inner element
 *  would still escape the canvas to cover the viewport — making the
 *  data-testid lookup return a 390-wide box but the screenshot pick up
 *  a fullscreen overlay. The translateZ(0) closes that hole the same
 *  way it closed the gallery one.
 */
const canvasStyle: React.CSSProperties = {
  width: '390px',
  boxSizing: 'border-box',
  padding: '16px',
  background: '#1A1A2E',
  overflow: 'hidden',
  transform: 'translateZ(0)',
  position: 'relative',
  // Suppress the default focus outline from the wrapper div so it doesn't
  // contaminate the :focus screenshot of the inner component. The component's
  // own focus styling (when we add IR pseudo-state support — Tier 13) will
  // be what we capture; for now Tier 5 captures browser-default state
  // changes on the inner ComponentRenderer output only.
  outline: 'none',
};

const errorStyle: React.CSSProperties = {
  ...canvasStyle,
  color: '#f87171',
  fontFamily: 'monospace',
  padding: '24px',
};

export default FixtureCanvas;
