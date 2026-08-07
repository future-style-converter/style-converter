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
import { buildStyles, buildVariables } from '../core/renderer/StyleBuilder';
// Stylesheet path (spec 06): every element carries its `sc-<id>` class so
// RuleBuilder selector/media rules can target it; forceClassName is the
// spec 06 §6 forced-state twin hook.
import { componentClassName, forceClassName } from '../core/renderer/RuleBuilder';
import type { ComposedNode } from './Composer';
import type { RenderContext, RendererOptions } from './RendererOptions';
import { defaultMapTag, VOID_ELEMENTS } from './TagMapping';
// wave-20 W1: the wire `meta.attrs` → DOM-prop policy (checked/value/
// multiple/… onto real widget elements; no-op for non-widget elements).
import { widgetDomProps } from './WidgetAttrs';
// wave-22 lane DECOR: the wire `meta.decorations` → nested decorating-box
// spans (per-line colours a single element cannot express).
import { decorationHostStyle, withDecorationSpans } from './DecorationSpans';
// Pseudo-element span rendering, split out for file size (wave-20 W1);
// styleFromRawDeclarations is RE-EXPORTED below so existing import sites
// (tests, downstream tooling) keep resolving through this module.
import { renderPseudoNode } from './PseudoNodeRenderer';
export { styleFromRawDeclarations } from './PseudoNodeRenderer';
// wave-28 lane PG: root-scope (`html::before`) generated boxes are placed
// by the ROOT's inline direction, not the contained body's — see
// RootPseudoPlacement.ts for the measured divergence and the spec chain.
import { rootPseudoPlacementStyle } from './RootPseudoPlacement';
// wave-32 lane R: the wire `meta.runs` → an ordered render plan over the
// composed children (the inline anonymous-run box) — see InlineRuns.ts.
import { resolveRuns } from './InlineRuns';

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
  // wave-22 lane DECOR: a collapsed inline run carries the ordered
  // per-line list in `meta.decorations`, which is AUTHORITATIVE. When it
  // is present the element stops painting its own (merged, root-wins)
  // line and the nested wrapper spans below own every line — see
  // DecorationSpans.ts for why that is both necessary and sufficient on a
  // real browser. Null for every other component, so their style objects
  // and DOM stay byte-identical.
  const decorations = component.meta?.decorations;
  const hostDecoration = decorationHostStyle(decorations);
  // Variables merge LAST — `--name` keys are disjoint from every regular
  // CSS key, so this can never clobber a declaration. The decoration host
  // override rides with them: it only ever sets `text-decoration-line`,
  // which the wrappers are about to re-declare per entry.
  const styleProp = { ...decorated, ...variableStyles, ...hostDecoration } as CSSProperties;

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
    // wave-20 W1: widget-identity attrs from the wire (input is the only
    // void widget). Applied AFTER the text-derived default above so an
    // explicit source `value` attribute wins over the text fallback.
    Object.assign(voidProps, widgetDomProps(elementName, component.meta?.attrs));
    // Same style pipeline as every other element.
    voidProps.style = styleProp;
    // Skin's last word on the props (harness: inert widgets in WPT mode).
    return createElement(
      elementName,
      options?.decorateProps ? options.decorateProps(voidProps, elementName, ctx) : voidProps,
    );
  }

  // ── textarea — React-managed content (value lives in props, not DOM
  // children) ─────────────────────────────────────────────────────────
  if (elementName === 'textarea') {
    // React forbids child nodes on <textarea> (the value IS the content);
    // map the wire text to the uncontrolled initial value instead. The
    // static serialization is byte-identical to the old text-child path
    // (`<textarea>…</textarea>`), minus React's console advisory.
    if (hasChildren) {
      // No silent fallthrough: composed children cannot live inside a
      // textarea's character data — warn (capture logs / app consoles)
      // and drop, exactly like the void-element branch above.
      console.warn(
        `[NodeRenderer] component "${component.id}" has sourceTag 'textarea' but ` +
          `${node.children.length} composed child(ren) — <textarea> holds text only; children not rendered`,
      );
    }
    // Identity props in the shared order (id, name, class, value, style).
    const taProps: Record<string, unknown> = {
      'data-component-id': component.id,
      'data-component-name': component.name,
      className,
    };
    // Wire text is the initial value (uncontrolled — same rule as input).
    if (hasText) taProps.defaultValue = text;
    // wave-20 W1: a source `value`-ish attribute set (disabled etc.) —
    // applied after so explicit wire attrs win over the text fallback.
    Object.assign(taProps, widgetDomProps(elementName, component.meta?.attrs));
    taProps.style = styleProp;
    // Skin's last word, then a childless createElement (React contract).
    return createElement(
      elementName,
      options?.decorateProps ? options.decorateProps(taProps, elementName, ctx) : taProps,
    );
  }

  // ── pseudo-element spans (spec 01 `pseudos`, extractor-owned) ───────
  const pseudo = component.pseudos;
  // wave-28 lane PG: a bucket hanging off the synthetic body-root holds a
  // box the CSS generated on the DOCUMENT ROOT (`html::before`), not on the
  // body — so when containment takes the body off the propagation path its
  // `direction: rtl` must not decide where that box sits. Null for every
  // other component, so their spans are byte-identical to wave 27.
  const rootPlacement = rootPseudoPlacementStyle(component);
  // CSS orders ::marker before ::before, both before the inline content;
  // ::after trails everything (including real children).
  const markerNode = pseudo?.marker ? renderPseudoNode(pseudo.marker, 'marker', rootPlacement) : null;
  const beforeNode = pseudo?.before ? renderPseudoNode(pseudo.before, 'before', rootPlacement) : null;
  const afterNode = pseudo?.after ? renderPseudoNode(pseudo.after, 'after', rootPlacement) : null;

  // ── content assembly ────────────────────────────────────────────────
  // Children are passed as POSITIONAL createElement arguments (marker,
  // before, text/empty, children-array, after) so only the mapped array
  // needs keys — identical semantics to a static JSX child list.
  let textSlot: ReactNode = null;
  let childSlot: ReactNode = null;
  if (hasChildren) {
    // Mixed content: text renders BEFORE the children — the pre-wave-32
    // approximation, still the default because it is what the `text`
    // string alone can say. `meta.runs` (below) supersedes it whenever the
    // producer measured a real interleave. Default is a bare text node.
    textSlot = hasText
      ? (options?.renderText ? options.renderText(text as string, ctx) : text)
      : null;
    // Composed children recurse in flat-array sibling order (spec 03),
    // carrying the SAME options so the skin applies at every depth.
    const renderChild = (index: number) => {
      const child = node.children[index];
      return createElement(NodeRenderer, {
        key: child.component.id || index,
        node: child,
        depth: depth + 1,
        options,
      });
    };
    // ── wave-32 lane R: the inline anonymous-run box ────────────────────
    //
    // `meta.runs` is the ordered inline content (spec 03 §4.1): the
    // component's own text and its kept children INTERLEAVED, the one
    // shape `text` + sibling order cannot express. When it is present it
    // is AUTHORITATIVE — the `text` slot is dropped (its string is the
    // concatenation `runs` was split FROM, so painting both would double
    // the glyphs) and every referenced child renders AT ITS RUN SLOT
    // instead of in the sibling walk.
    //
    // WHY A BARE TEXT NODE AND NOT A WRAPPER: a `{text}` entry is an
    // anonymous INLINE run. Wrapping it in an element — the obvious
    // shape, and the one wave-31 measured and rejected — puts a box
    // inside the inline flow; a block-level one splits the containing
    // inline box (CSS 2.1 §9.2.1.1) and re-breaks the very line box the
    // interleave exists to preserve. `createElement` accepts strings as
    // children directly, so the run costs no DOM node at all.
    //
    // The two calibration hooks below (planChildRuns, renderChildSeparator)
    // are DELIBERATELY skipped on this path, and neither loses anything:
    // a float run is block-level packing, which by definition is not the
    // inline flow `runs` describes; and the separator hook exists to
    // re-invent the inter-sibling space the flat wire dropped — a space
    // `runs` now carries for real, as a whitespace-only text entry.
    //
    // wave-34 lane R MEASURED that argument instead of leaving it as an
    // argument. The producer widened emission from "the reorder fired" to
    // "own text and element children interleave at all", which grew the
    // population by 515 components across the bucket-A corpus — a big
    // enough jump that "floats are not inline flow" deserved a count. Of
    // those 515 newly-listed components, the number carrying a float run
    // (≥2 consecutive left-floating children, the only shape planChildRuns
    // groups) is ZERO. The skip is inert on the new population, not merely
    // defensible on it.
    const inlineRuns = resolveRuns(component.meta?.runs, node.children, component.id);
    if (inlineRuns) {
      // The runs own the content slot; the sibling walk carries only the
      // children the list did NOT name (spec 03 §4.1 rule 4), so a
      // referenced child can never be painted twice (rule 2).
      textSlot = inlineRuns.entries.map((entry) =>
        entry.kind === 'text'
          // Skin hook parity: an interleaved run is still the component's
          // OWN text, so the harness's renderText calibration applies to
          // it exactly as it does to the leading-text slot above.
          ? (options?.renderText ? options.renderText(entry.text, ctx) : entry.text)
          : renderChild(entry.index),
      );
      childSlot = inlineRuns.unreferenced.length > 0
        ? inlineRuns.unreferenced.map(renderChild)
        : null;
    } else {
    // Optional child-run grouping (wave-19 float-run calibration — see
    // RendererOptions.planChildRuns). Null plan = the pure default:
    // every child a direct sibling, byte-identical to the pre-hook DOM.
    const runPlan = options?.planChildRuns ? options.planChildRuns(node.children, ctx) : null;
    // Optional inter-sibling separator (wave-20 W2 follow-up — see
    // RendererOptions.renderChildSeparator): a non-null return renders
    // BETWEEN adjacent siblings (harness: the WPT-mode ' ' text node
    // between inline widget atoms). Skipped entirely under a runPlan —
    // float runs are block-level, where whitespace renders nothing
    // (CSS 2.1 §9.2.2.1), and the two plans never co-occur today.
    const separator = options?.renderChildSeparator;
    childSlot = !runPlan
      ? node.children.flatMap((_, index) => {
        const el = renderChild(index);
        // First child (or no hook): no separator slot before it.
        if (index === 0 || !separator) return [el];
        // Ask the skin for the (prev, next) gap node; null = flush.
        const sep = separator(node.children[index - 1], node.children[index], ctx);
        // Bare strings need no React key; elements from the hook would —
        // the harness only ever returns text, keeping this warning-free.
        return sep !== null && sep !== undefined ? [sep, el] : [el];
      })
      : runPlan.segments.map((seg, s) =>
        seg.wrapperStyle
          // Wrapped segment: one <div> carrying the skin's run style
          // (float-run BFC), members rendered inside in sibling order.
          // data-float-run marks the synthetic box for tooling/tests.
          ? createElement(
            'div',
            { key: `run-${s}`, 'data-float-run': '', style: seg.wrapperStyle },
            seg.indices.map(renderChild),
          )
          // Plain segment: members stay direct siblings (keyed array —
          // React flattens nested arrays with stable keys).
          : seg.indices.map(renderChild));
    }
  } else {
    // Childless: the empty-content slot (skin: placeholder label;
    // default: the text itself, or nothing — an empty element).
    textSlot = options?.renderEmptyContent
      ? options.renderEmptyContent(ctx)
      : (hasText ? text : null);
  }

  // wave-22 lane DECOR — reconstruct the collapsed chain's decorating
  // boxes around the run's CONTENT, outermost-first, one <span> per
  // entry. Applied to the content slot (not to the children array): the
  // extractor only ever collapses a chain whose whole subtree is
  // decoration-only inline wrappers, which by construction leaves ONE
  // childless text component. `decorated` supplies the run's shared
  // decoration style + thickness (per-run, not per-entry). Identity when
  // there is no wire — every other component's DOM is byte-identical.
  textSlot = withDecorationSpans(textSlot, decorations, decorated);

  // Identity/capture attributes + rule class + inline styles — the exact
  // prop order the old harness renderer used (byte-parity contract).
  // wave-20 W1: widget attrs slot between the class and the style (only
  // ever non-empty when the RESOLVED element is a widget tag — a skin's
  // <div> demotion keeps this an empty spread, byte-identical DOM).
  const elementProps: Record<string, unknown> = {
    'data-component-id': component.id,
    'data-component-name': component.name,
    className,
    ...widgetDomProps(elementName, component.meta?.attrs),
    style: styleProp,
  };
  return createElement(
    elementName,
    // Skin's last word on the props (harness: inert widgets in WPT mode).
    options?.decorateProps ? options.decorateProps(elementProps, elementName, ctx) : elementProps,
    markerNode,   // ::marker first (CSS Lists ordering)
    beforeNode,   // then ::before
    textSlot,     // then the element's own text (or empty-content slot)
    childSlot,    // then real children (keyed array)
    afterNode,    // ::after trails everything
  );
}
