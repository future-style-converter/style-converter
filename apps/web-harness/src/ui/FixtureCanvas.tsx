/**
 * FixtureCanvas — single-component chromeless render for Tier 5
 * (interaction state) testing.
 *
 * Visited via `?fixture=<ComponentName>`. The Puppeteer driver in
 * tools/visual/interaction-states.mjs grabs `[data-testid="<ComponentName>"]`,
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
import type { IRDocument } from '@style-converter/web/core/ir/IRModels';
import { ComponentRenderer } from '../sdui/ComponentRenderer';
import { composeTree, findNode } from '../sdui/Composer';
// The debug label as capture chrome (wave 51 PR (A)) — a SIBLING of the
// component inside the wrapper, gated by this file's OWN predicate below;
// labelChromeFits stamps the svg-less case on the wrapper from one truth.
import { LabelChrome, labelChromeFits } from './LabelChrome';

/**
 * The Tier-5 frame width in px — the literal 390 `canvasStyle.width`
 * hardcodes (the shared capture contract, docs/DYNAMIC_CAPTURE.md). The
 * label chrome truncates against THIS number, never CaptureGallery's
 * `?width=`-driven CANVAS_WIDTH_PX: this canvas is always 390 wide, so a
 * `?width=250` Tier-5 URL must not truncate to 39 glyphs on a 390 frame.
 */
const FIXTURE_FRAME_WIDTH_PX = 390;

interface FixtureCanvasProps {
  document: IRDocument;
  fixtureName: string;
}

export function FixtureCanvas({ document, fixtureName }: FixtureCanvasProps) {
  // Compose the flat v2 wire into the preview tree, then look the target
  // up by name or id anywhere in the forest (fixture docs are usually
  // single-component, but slot-composed subtrees are legal). Memoised so
  // the sentinel useEffect below doesn't re-fire on every render (React
  // would otherwise see a fresh node reference).
  const component = React.useMemo(
    () => findNode(composeTree(document), fixtureName),
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

  // Label-chrome predicate (docs/DYNAMIC_CAPTURE.md "Harness label chrome"),
  // this canvas's OWN copy: `component` here is the ComposedNode, so the
  // children test reads its composed list and the text test reads the wire
  // component underneath (`component.component.text`) — reading `.text` on
  // the node itself would be undefined and label every root. No WPT term:
  // this file carries no WPT flag and the Tier-5 `?fixture=` path never
  // runs a WPT capture, so the gate is children + text alone.
  const showLabel = component.children.length === 0
    && !(typeof component.component.text === 'string' && component.component.text.length > 0);
  // The svg-less case (CaptureCanvas's twin): the label was due but the
  // name lays out no rect at this canvas's literal 390 — stamped on the
  // wrapper so tooling can grep it; LabelChrome warns once for the same.
  const labelDropped = showLabel && !labelChromeFits(component.component.name, FIXTURE_FRAME_WIDTH_PX);

  return (
    <div
      ref={wrapperRef}
      data-testid={fixtureName}
      data-fixture-name={fixtureName}
      // Present (empty value) iff the label was due and nothing fits;
      // absent otherwise (React drops undefined-valued data attributes).
      data-label-chrome-dropped={labelDropped ? '' : undefined}
      tabIndex={0}
      style={canvasStyle}
    >
      <ComponentRenderer node={component} />
      {/* HARNESS LABEL CHROME — a SIBLING after the component (the wrapper
          mirrors CaptureCanvas: position relative → frame coords, overflow
          hidden → frame clip, translateZ(0) → stacking root). frameWidth is
          this canvas's literal 390, matching canvasStyle.width below. */}
      {showLabel ? <LabelChrome name={component.component.name} frameWidth={FIXTURE_FRAME_WIDTH_PX} /> : null}
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
  // The literal 390 (FIXTURE_FRAME_WIDTH_PX) — emits `width:390px` exactly
  // as before; the constant only keeps the chrome's truncation in lockstep.
  width: `${FIXTURE_FRAME_WIDTH_PX}px`,
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
