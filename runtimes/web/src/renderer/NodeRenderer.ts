/**
 * NodeRenderer — the shared renderer CORE for one composed IR node
 * (issue #41). Renders a component + its slot-composed subtree as real
 * DOM elements with the engine's styles inline, pseudo-element spans,
 * text content, and the per-component stylesheet class.
 *
 * PURE CSS SEMANTICS BY DEFAULT — the five capture-calibration
 * behaviours the old harness renderer hard-coded are NOT here (each is
 * an explicit RendererOptions hook the harness now passes instead):
 * no fit-content width default, no 50×30 px floors, no empty-container
 * display demotion, no placeholder label text, no display:none DOM
 * suppression. See RendererOptions.ts for the divergence ledger.
 *
 * Deliberately JSX-free (.ts, createElement only) so the module stays
 * consumable by any TypeScript toolchain without a JSX transform.
 */

import { createElement, useMemo } from 'react';
import type { CSSProperties, ReactElement, ReactNode } from 'react';
import type { IRPseudoNode } from '../core/ir/IRModels';
import { buildStyles, buildVariables } from '../core/renderer/StyleBuilder';
// Stylesheet path (spec 06): every element carries its `sc-<id>` class so
// RuleBuilder selector/media rules can target it; forceClassName is the
// spec 06 §6 forced-state twin hook.
import { componentClassName, forceClassName } from '../core/renderer/RuleBuilder';
import type { ComposedNode } from './Composer';
import type { RenderContext, RendererOptions } from './RendererOptions';
import { defaultMapTag, VOID_ELEMENTS } from './TagMapping';

/** Props for one composed node render. */
export interface NodeRendererProps {
  /** Composed node: the flat-wire component + its slot-composed children. */
  node: ComposedNode;
  /** Composition depth — bookkeeping only, never reaches the DOM. */
  depth?: number;
  /** Calibration hooks (see RendererOptions). Omit for pure CSS semantics. */
  options?: RendererOptions;
}

/**
 * Render one pseudo-element node as an inline <span> (shared verbatim
 * with the old harness renderer — pseudo rendering is a semantic choice,
 * not a capture calibration). The span carries the pseudo rule's styles
 * inline (including `content:` for browser-evaluated counter()/attr()),
 * and materialises the literal `content` string as text. The marker role
 * gets `inline-block` + a trailing 0.5em gap approximating the native
 * marker-side spacing (CSS Lists 3 §4.3).
 */
function renderPseudoNode(p: IRPseudoNode, role: 'before' | 'after' | 'marker'): ReactElement {
  // Same engine as component styles: the pseudo rule's declarations
  // (color, font-*, AND content) reach the inline style attribute.
  const ps = buildStyles(p.properties ?? []);
  // Literal `content:` string — both wire spellings tolerated (`_text`
  // is what the extractor emits today; `text` is the v2 spelling).
  const pText = typeof p._text === 'string' ? p._text : (typeof p.text === 'string' ? p.text : '');
  // Marker-side gap (see the function doc above); other roles inherit flow.
  const markerStyle: CSSProperties = role === 'marker'
    ? { display: 'inline-block', marginInlineEnd: '0.5em' }
    : {};
  // <span> preserves the inline flow ::before/::after participate in.
  return createElement(
    'span',
    {
      key: `pseudo-${role}-${p.id}`,                     // stable per role+id
      'data-pseudo': role,                               // role marker for tooling
      'data-component-id': p.id,                         // debug identity (may be absent)
      style: { ...markerStyle, ...(ps as CSSProperties) }, // rule styles win over the gap
    },
    pText,                                               // literal content string (may be '')
  );
}

/**
 * The core renderer. One component's properties go through buildStyles
 * exactly once; children recurse with the SAME options so a skin applies
 * uniformly at every composition depth.
 */
export function NodeRenderer({ node, depth = 0, options }: NodeRendererProps): ReactElement | null {
  // The component under render — the only wire input besides children.
  const component = node.component;
  // Engine styles are pure per-properties — memoise on identity. Hooks
  // MUST run before any conditional return (Rules of Hooks).
  const styles = useMemo(() => buildStyles(component.properties), [component.properties]);
  // Custom-property DEFINITIONS (`variables` map) become `--name` inline
  // keys on THIS element; CSS inheritance then resolves descendants'
  // var() references natively (css-variables-1 §2.3).
  const variableStyles = useMemo(() => buildVariables(component.variables), [component.variables]);
  // Composed-children presence drives the content shape below.
  const hasChildren = node.children.length > 0;
  // One consistent context snapshot for every hook on this node.
  const ctx: RenderContext = { component, node, hasChildren, styles, depth };

  // Skin gate (harness: display:none → no DOM). Default: always render —
  // pure CSS keeps the box in the tree with display:none applied.
  if (options?.shouldRender && !options.shouldRender(ctx)) return null;

  // Skin style decoration (harness: sizing calibration). Default:
  // identity — the element gets exactly what the engine emitted.
  const decorated = options?.decorateStyles ? options.decorateStyles(styles, ctx) : styles;
  // Variables merge LAST — `--name` keys are disjoint from every regular
  // CSS key, so this can never clobber a declaration.
  const styleProp = { ...decorated, ...variableStyles } as CSSProperties;

  // Element choice: lowercase the trusted wire tag, then let the skin
  // (or the production default policy) map it to an element name.
  const sourceTag = component.meta?.sourceTag;
  const rawTag = (typeof sourceTag === 'string' && sourceTag.length > 0)
    ? sourceTag.toLowerCase()
    : null;
  const elementName = (options?.mapTag ?? defaultMapTag)(rawTag, ctx);

  // Rule target + optional forced-state activation (spec 06 §6).
  const className = componentClassName(component.id)
    + (options?.forceState ? ` ${forceClassName(options.forceState)}` : '');

  // Element text content; empty string means "extracted, was empty" and
  // renders nothing (same truthiness the wire contract pins).
  const text = component.text;
  const hasText = typeof text === 'string' && text.length > 0;

  // ── void elements (img/input/hr/…) — no children possible ──────────
  if (VOID_ELEMENTS.has(elementName)) {
    // No silent fallthrough: a void element cannot host the composed
    // children — warn (reaches capture logs / app consoles) and drop,
    // exactly as a browser would discard nested markup.
    if (hasChildren) {
      console.warn(
        `[NodeRenderer] component "${component.id}" has sourceTag '${elementName}' but ` +
          `${node.children.length} composed child(ren) — <${elementName}> is void; children not rendered`,
      );
    }
    // Prop order matters for DOM byte-parity with the old harness
    // renderer: id, name, class, (src, alt | value), style.
    const voidProps: Record<string, unknown> = {
      'data-component-id': component.id,
      'data-component-name': component.name,
      className,
    };
    if (elementName === 'img') {
      // Image source: skin-supplied (harness placeholder) or absent —
      // the wire carries no src yet (wave-9 content-contract gap).
      const src = options?.resolveImageSource ? options.resolveImageSource(ctx) : undefined;
      if (src !== undefined) voidProps.src = src;
      // The component's text is the natural alt text of an <img>.
      voidProps.alt = typeof text === 'string' ? text : '';
    } else if (elementName === 'input' && hasText) {
      // For an <input>, wire text maps to the initial value
      // (uncontrolled, so React never demands an onChange handler).
      voidProps.defaultValue = text;
    }
    // Same style pipeline as every other element.
    voidProps.style = styleProp;
    return createElement(elementName, voidProps);
  }

  // ── pseudo-element spans (spec 01 `pseudos`, extractor-owned) ───────
  const pseudo = component.pseudos;
  // CSS orders ::marker before ::before, both before the inline content;
  // ::after trails everything (including real children).
  const markerNode = pseudo?.marker ? renderPseudoNode(pseudo.marker, 'marker') : null;
  const beforeNode = pseudo?.before ? renderPseudoNode(pseudo.before, 'before') : null;
  const afterNode = pseudo?.after ? renderPseudoNode(pseudo.after, 'after') : null;

  // ── content assembly ────────────────────────────────────────────────
  // Children are passed as POSITIONAL createElement arguments (marker,
  // before, text/empty, children-array, after) so only the mapped array
  // needs keys — identical semantics to a static JSX child list.
  let textSlot: ReactNode = null;
  let childSlot: ReactNode = null;
  if (hasChildren) {
    // Mixed content: text renders BEFORE the children (the wire has no
    // interleaved inline-runs shape yet). Default is a bare text node.
    textSlot = hasText
      ? (options?.renderText ? options.renderText(text as string, ctx) : text)
      : null;
    // Composed children recurse in flat-array sibling order (spec 03),
    // carrying the SAME options so the skin applies at every depth.
    childSlot = node.children.map((child, index) =>
      createElement(NodeRenderer, {
        key: child.component.id || index,
        node: child,
        depth: depth + 1,
        options,
      }));
  } else {
    // Childless: the empty-content slot (skin: placeholder label;
    // default: the text itself, or nothing — an empty element).
    textSlot = options?.renderEmptyContent
      ? options.renderEmptyContent(ctx)
      : (hasText ? text : null);
  }

  // Identity/capture attributes + rule class + inline styles — the exact
  // prop order the old harness renderer used (byte-parity contract).
  return createElement(
    elementName,
    {
      'data-component-id': component.id,
      'data-component-name': component.name,
      className,
      style: styleProp,
    },
    markerNode,   // ::marker first (CSS Lists ordering)
    beforeNode,   // then ::before
    textSlot,     // then the element's own text (or empty-content slot)
    childSlot,    // then real children (keyed array)
    afterNode,    // ::after trails everything
  );
}
