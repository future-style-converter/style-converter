/**
 * RendererOptions — the calibration surface of the package renderer
 * (issue #41: "one renderer core, two skins").
 *
 * The core (NodeRenderer/DocumentRenderer) implements PURE CSS semantics:
 * inline styles are exactly what buildStyles produced, elements are
 * whatever `meta.sourceTag` trusts, text is a bare text node, an empty
 * component is an empty element. Every place the capture harness needs
 * to deviate for cross-platform screenshot comparability is an explicit
 * hook here — so the harness's calibration is visible API, not forked
 * renderer code. A production app normally passes NO options at all.
 *
 * Each hook documents the harness divergence it exists for and why the
 * harness keeps it (the issue-#41 divergence ledger).
 */

import type { ReactNode } from 'react';
import type { IRComponent } from '../core/ir/IRModels';
import type { CSSStyles } from '../core/renderer/StyleBuilder';
import type { RuntimeV1Condition } from '../core/renderer/RuleBuilder';
import type { ComposedNode } from './Composer';

/**
 * Per-node context handed to every hook. All fields are derived once per
 * render of the node, before any hook runs, so hooks see one consistent
 * snapshot (styles is the RAW buildStyles output — pre-decoration).
 */
export interface RenderContext {
  /** The flat-wire component being rendered. */
  component: IRComponent;
  /** The composed node (component + slot-composed children). */
  node: ComposedNode;
  /** Whether the node has composed children (drives content shape). */
  hasChildren: boolean;
  /** RAW engine output for the component's base properties. */
  styles: CSSStyles;
  /** Composition depth (0 = root of the rendered subtree). */
  depth: number;
}

/**
 * The calibration hooks. Every hook is optional; the documented default
 * is the pure-CSS behaviour the package ships with.
 */
export interface RendererOptions {
  /**
   * Gate rendering of a node entirely. Default: always render — a
   * `display:none` component stays IN the DOM with `display:none`
   * (pure CSS semantics: the box is suppressed visually, but the node
   * exists and can be toggled by state/media rules).
   * HARNESS DIVERGENCE: the harness returns null for display:none so a
   * hidden component can never contribute a capture canvas or bleed
   * paint into a neighbour's screenshot crop.
   */
  shouldRender?: (ctx: RenderContext) => boolean;

  /**
   * Transform the engine styles before they land on the element (the
   * variables map merges after, so `--name` keys can't be clobbered).
   * Default: identity — NO fit-content width, NO 50×30 px minimum
   * floors, NO max-width cap, NO display rewriting.
   * HARNESS DIVERGENCE: the harness injects `width:fit-content`,
   * `max-width:100%`, 50/30 px min floors and demotes empty grid/flex
   * to block — all to make a web <div> hug content the way SwiftUI /
   * Compose intrinsic sizing does, so the 3-platform SSIM comparison
   * measures style fidelity instead of block-flow width differences.
   * Real CSS must never do this: a plain block stretching to its
   * containing block IS the specified behaviour.
   */
  decorateStyles?: (styles: CSSStyles, ctx: RenderContext) => CSSStyles;

  /**
   * Choose the DOM element for a node. Receives the lowercased
   * `meta.sourceTag` (or null when absent). Default: defaultMapTag —
   * trust the wire, denylist only document-breaking tags (see
   * TagMapping.ts). HARNESS DIVERGENCE: the harness allowlists
   * structural/inline tags only, demoting replaced + interactive
   * elements (img/button/a/input) so captures never pick up native
   * control chrome that varies across headless builds.
   */
  mapTag?: (sourceTag: string | null, ctx: RenderContext) => string;

  /**
   * Render the component's `text` when it ALSO has children (the
   * mixed-content position: after ::before, before the children).
   * Default: the bare string — a real text node, exactly where HTML
   * would put it. HARNESS DIVERGENCE: the harness wraps it in a plain
   * <span> so its tests/tooling can target the glyph run.
   */
  renderText?: (text: string, ctx: RenderContext) => ReactNode;

  /**
   * Render the content of a CHILDLESS node (between ::before and
   * ::after when pseudos exist). Default: the component's text when
   * present, otherwise nothing — an empty component renders an empty
   * element. HARNESS DIVERGENCE: the harness substitutes its
   * PlaceholderContent (name label + bg-luminance contrast colour) so
   * empty capture fixtures stay visually identifiable against the
   * iOS/Android placeholder labels.
   */
  renderEmptyContent?: (ctx: RenderContext) => ReactNode;

  /**
   * Supply the `src` for a node whose element resolves to <img>.
   * Default: none — the wire carries no image source yet (the wave-9
   * IR content-contract gap), so the element renders with alt text
   * only rather than the renderer inventing bytes. HARNESS DIVERGENCE:
   * the harness injects a deterministic inline SVG placeholder so
   * object-fit/position captures have stable pixels.
   */
  resolveImageSource?: (ctx: RenderContext) => string | undefined;

  /**
   * Force one runtime-v1 interaction state on EVERY rendered element by
   * appending the `force-<state>` class (spec 06 §6 — the twin selector
   * on every RuleBuilder state rule makes a forced state resolve
   * byte-identically to real input). Not harness-only: preview tooling
   * in real apps wants this too. Default: null (base state).
   */
  forceState?: RuntimeV1Condition | null;

  /**
   * Group the node's composed children into ordered segments, wrapping
   * some in an extra <div>. Default: none — children render as direct
   * siblings, pure HTML.
   * HARNESS DIVERGENCE (wave-19 lane FLOAT, WPT capture only): the
   * harness wraps each run of ≥2 consecutive left-floating children in
   * a `display:flow-root; width:max-content` div so float rows break
   * ONLY at `<br clear>` markers — the captured IR's synthetic 100px
   * root frames would otherwise wrap the rows at an artifact width the
   * browser-ref (body-wide containing block) never saw. Segments MUST
   * cover every child index exactly once, in sibling order.
   */
  planChildRuns?: (children: ComposedNode[], ctx: RenderContext) => ChildRunPlan | null;
}

/**
 * A child grouping plan (see RendererOptions.planChildRuns): ordered
 * segments over the composed-children array. A segment with a
 * `wrapperStyle` renders as one <div> containing its members; a segment
 * without renders its members as plain siblings.
 */
export interface ChildRunPlan {
  /** Ordered segments — indices into the composed-children array. */
  segments: Array<{
    /** Member child indices, in sibling order. */
    indices: number[];
    /** Present → wrap the members in a <div> with this inline style. */
    wrapperStyle?: import('react').CSSProperties;
  }>;
}
