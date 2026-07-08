/**
 * Core IR (Intermediate Representation) types for SDUI.
 *
 * These types mirror the Kotlin IR models from the main project,
 * providing a unified format for CSS property data that can be
 * rendered on any platform.
 */

/**
 * Root document containing all components.
 */
export interface IRDocument {
  components: IRComponent[];
}

/**
 * A single UI component with its styles.
 */
export interface IRComponent {
  /** Unique identifier for SDUI (e.g., "button-001") */
  id: string;
  /** Component type/class name (e.g., "Button", "Card") */
  name: string;
  /** List of CSS properties as IR */
  properties: IRProperty[];
  /** State-based styles (hover, focus, etc.) */
  selectors: IRSelector[];
  /** Responsive breakpoint styles */
  media: IRMedia[];
  /** Nested child components for containers */
  children: IRComponent[] | null;
  /**
   * Optional element text content carried through from extraction.
   *
   * Populated by the WPT extractor (testing/titan/extract-fixture.mjs) for
   * fixtures whose styled element has an inner text node — e.g. the green
   * sentence in `<p class=test>Test passes if this text is green</p>`.
   * When present, the renderer draws this string as the visible content of
   * the component (taking precedence over the placeholder name and over
   * the WPT_MODE empty-string suppression).
   *
   * Optional / nullable to preserve backward compatibility with existing
   * component-style fixtures (visual-test.json, examples/properties/*) that
   * never carry text content. Renderers MUST treat a missing/null `_text`
   * as "fall back to existing placeholder behaviour" so the 327-pair
   * baseline stays byte-stable.
   *
   * Why a leading underscore: matches the convention used elsewhere in the
   * IR for renderer-only metadata that doesn't correspond to a CSS property
   * (e.g. potential `_fullPage` hint discussed in
   * testing/titan/investigations/swarm-001/css-color__color-001.json
   * §recommendedFixes.tertiary).
   */
  _text?: string | null;
  /**
   * Optional originating HTML element tag (lowercase: 'ol', 'li', 'p', 'h1',
   * etc.). Populated by the WPT extractor so the renderer can choose a
   * matching native element type and inherit the browser's default styling
   * for that tag — list-marker generation on `<ol>`/`<ul>`/`<li>`, paragraph
   * spacing on `<p>`, table-row layout on `<tr>`, etc.
   *
   * Optional / nullable to preserve backward compatibility with the legacy
   * component fixtures (visual-test.json) which never carried tag info.
   * When absent, renderers MUST fall back to their pre-existing default
   * container element (a plain <div> on web, VStack on iOS, Column on
   * Android) so the 327-pair visual-test baseline stays byte-stable.
   *
   * See testing/titan/investigations/swarm-002/css-counter-styles__css3-counter-styles-101.json
   * for the motivating bug: list-style-type:arabic-indic was inert because
   * each list-item rendered as a <div>, so Chromium's native counter
   * algorithm never fired. Adding the tag lets the browser take over.
   */
  _tag?: string | null;
  /**
   * Optional CSS pseudo-element nodes attached to this component.
   *
   * Populated by the F-G-EXTRACTOR pipeline when the WPT source contains
   * `::before` / `::after` / `::marker` rules matching this element.
   * Each entry is a synthetic IRComponent whose properties carry the
   * pseudo-element's declarations (notably `content`, but also any
   * font/color/decoration declarations from the matched rule).
   *
   * The web renderer materialises these as inline `<span>` siblings
   * around the host's natural content (before-leading, after-trailing,
   * marker as a list-item-marker span). The `content` property is then
   * natively evaluated by Chromium — including functional values like
   * `counter(c, decimal-leading-zero)`, `counters()`, `attr()`, and
   * literal strings — so we lean on the browser's counter-tree machinery
   * without re-implementing it in the application layer.
   *
   * See:
   *   - testing/titan/investigations/swarm-003/css-lists__counter-001.json
   *   - testing/titan/investigations/swarm-003/css-pseudo__before-preceding-whitespace-dynamic.json
   *
   * Optional / nullable for backward compatibility: components without
   * `_pseudo` MUST render byte-identically to the pre-`_pseudo` world so
   * the 327-pair visual-test baseline stays stable.
   */
  _pseudo?: PseudoElements | null;
}

/**
 * Per-element pseudo-element payload — see {@link IRComponent._pseudo}.
 *
 * Each slot is independently optional: a `<li>` might only carry a
 * `marker`, a `<span>` only a `before`, etc. Renderers MUST treat
 * missing/null slots as "no synthetic node for that pseudo".
 *
 * The contained IRComponents are full components in their own right
 * (properties + selectors + media + optional nested children), so any
 * style declarations the original CSS attached to the pseudo
 * (color, text-decoration, font-*) reach the synthetic node intact.
 */
export interface PseudoElements {
  /** Synthetic ::before node — rendered as a leading inline span. */
  before?: IRComponent | null;
  /** Synthetic ::after node — rendered as a trailing inline span. */
  after?: IRComponent | null;
  /** Synthetic ::marker node — rendered as a list-item-marker span. */
  marker?: IRComponent | null;
}

/**
 * A CSS property in IR format.
 *
 * Uses generic JsonElement (any) for data to handle all 446+ property types flexibly.
 * Specific property handling is done in StyleApplier.
 */
export interface IRProperty {
  type: string;
  data: unknown;
}

/**
 * Pseudo-class selector styles (e.g., :hover, :focus).
 */
export interface IRSelector {
  condition: string;
  properties: IRProperty[];
}

/**
 * Media query styles (e.g., min-width: 768px).
 */
export interface IRMedia {
  query: string;
  properties: IRProperty[];
}
