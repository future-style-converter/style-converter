/**
 * SDUI Component Renderer
 *
 * Renders IR components as HTML/CSS at runtime.
 * This is the web equivalent of the Android ComponentRenderer.
 */

import React, { useMemo } from 'react';
import type { IRComponent, IRProperty } from '@style-converter/web/core/ir/IRModels';
import { buildStyles, type CSSStyles } from '@style-converter/web/core/renderer/StyleBuilder';

/**
 * WPT-mode detector — reads the `?wpt=1` query parameter once per module load.
 *
 * Why this exists: TITAN's WPT capture pipeline (testing/titan/section-runner.sh)
 * extracts a one-component-per-test IR where the component name is the WPT
 * key `wpt__<section>__<stem>__<index>`. When the extracted IR is empty
 * (extractor dropped a body-level style, or the test renders nothing on a
 * 100×100 box) the only visible thing in the screenshot is the placeholder
 * text — `wpt css-backgrounds background-color-animation-in-body 0` etc. —
 * which the pilot agent (testing/titan/investigations/pilot-001/
 * css-backgrounds__background-color-animation-in-body.json) flagged as the
 * source of false `structural-divergence` labels across the entire WPT
 * corpus (the title overlay alone produces ~33 pHash hamming distance vs
 * the chromeless browser-ref).
 *
 * Suppress the placeholder text in WPT mode. Legacy 327-pair flow (no `?wpt=1`)
 * keeps the placeholder as before — it's load-bearing for the visual-test
 * fixture where empty containers need the name to be visually identifiable
 * against iOS / Android's PlaceholderLabel / PlaceholderContent.
 *
 * Read once at module-load time. URL params don't change during a capture
 * session (puppeteer navigates once); avoiding per-render `URLSearchParams`
 * construction keeps the hot render loop allocation-free.
 */
const WPT_MODE: boolean = (() => {
  if (typeof window === 'undefined') return false;
  return new URLSearchParams(window.location.search).get('wpt') === '1';
})();

interface ComponentRendererProps {
  component: IRComponent;
  depth?: number;
}

/**
 * Detect the display/layout type from properties.
 */
type DisplayType = 'block' | 'flex-row' | 'flex-column' | 'grid' | 'inline' | 'none';

function detectDisplayType(properties: IRProperty[]): DisplayType {
  let displayType: DisplayType = 'block';
  let flexDirection = 'row';

  for (const prop of properties) {
    if (prop.type === 'Display') {
      const keyword = typeof prop.data === 'string'
        ? prop.data
        : (prop.data as Record<string, unknown>)?.keyword || (prop.data as Record<string, unknown>)?.type;

      if (typeof keyword === 'string') {
        switch (keyword.toLowerCase().replace(/_/g, '-')) {
          case 'flex':
          case 'inline-flex':
            displayType = 'flex-row';
            break;
          case 'grid':
            displayType = 'grid';
            break;
          case 'inline':
          case 'inline-block':
            displayType = 'inline';
            break;
          case 'none':
            displayType = 'none';
            break;
        }
      }
    }

    if (prop.type === 'FlexDirection') {
      const direction = typeof prop.data === 'string'
        ? prop.data
        : (prop.data as Record<string, unknown>)?.keyword;

      if (typeof direction === 'string' && direction.toLowerCase().includes('column')) {
        flexDirection = 'column';
      }
    }
  }

  // Update flex direction
  if (displayType === 'flex-row' && flexDirection === 'column') {
    displayType = 'flex-column';
  }

  return displayType;
}

/**
 * Render a single IR component.
 */
export function ComponentRenderer({ component, depth = 0 }: ComponentRendererProps) {
  const displayType = useMemo(() => detectDisplayType(component.properties), [component.properties]);

  // Build styles from properties.
  //
  // Rules-of-Hooks note: this useMemo MUST run before the `display: none`
  // early return below. Hooks have to execute in the same order on every
  // render; when this call sat after the conditional return, a component
  // toggling between `display:none` and visible changed the number of
  // hooks React saw between renders (the FIX-G-era regression). Computing
  // styles for a display:none component is a trivially cheap memoised
  // no-op, so hoisting is safe.
  const styles = useMemo(() => buildStyles(component.properties), [component.properties]);

  // Don't render if display: none
  if (displayType === 'none') {
    return null;
  }

  // Add minimum sizing for empty components.
  //
  // Default sizing model has to match the native renderers.
  // - iOS  (CaptureCanvas.swift): `.frame(maxWidth: 358, alignment: .topLeading)`
  //   + ComponentRenderer uses SwiftUI intrinsic sizing — the component
  //   occupies its natural content width, capped at 358.
  // - Android (CaptureCanvas): `wrapContentSize()` equivalent — same deal,
  //   boxes hug their content.
  // - Web (this file, pre-fix): a plain <div> is display:block and stretches
  //   to 100% of the 358 px canvas inner width, regardless of declared width
  //   or content length. That's what dragged almost every SSIM into the
  //   0.15-0.40 range on visual-test.json — outlines, shadows, grids, flex,
  //   transforms, typography — because the web component was always ~2×
  //   wider than its iOS/Android counterparts.
  //
  // Fix: default `width` to `fit-content` so the <div> hugs its content the
  // way SwiftUI/Compose do, but let any IR-declared width (`Width` → `styles.width`)
  // win via the spread. `max-width: 100%` caps it at the canvas so wide text
  // runs can't overflow.
  //
  // Empty-container display override: when a `display: grid` (or `flex`)
  // container has no children, the placeholder span is the only grid/flex
  // item. Grid `fr` tracks expand to fill available space, and flex
  // children stretch by default — so even with `width: fit-content` the
  // container blows out to the full 358px canvas inner width. iOS bypasses
  // this by routing empty grids through a VStack (see ComponentRenderer.swift
  // `case .grid:` with `if hasChildren` guard); Android `wrapContentSize()`
  // collapses naturally. The web equivalent: rewrite display→block when the
  // child set is empty, so the placeholder lays out as a normal inline-ish
  // block and the container hugs it. Fixed Grid_ThreeCol (058) iOS-web
  // 0.42→~0.85, Grid_FixedTracks (059) 0.50→~0.85, plus the same gain on
  // Android-web pairs. Honors any explicit `width` declaration via the
  // `...styles` spread because that comes after `display`.
  const hasChildren = !!(component.children && component.children.length > 0);

  // Aspect-ratio fit-content suppression — see
  // testing/titan/investigations/swarm-001/css-sizing__block-aspect-ratio-032.json
  //
  // CSS aspect-ratio (css-sizing-4 §6.2) only transfers a size from the
  // constrained axis to the unconstrained axis when the cross axis is left
  // `auto`. Our default `width: 'fit-content'` initialiser (added to make
  // empty placeholder boxes hug their content the way SwiftUI/Compose do)
  // makes the inline size content-driven, which the spec treats as a
  // definite specification — so the height-to-width transfer never fires.
  //
  // Detection rule per the swarm-001 fixProposal: if the IR carries an
  // `aspect-ratio` AND exactly one of {width-axis, height-axis} is
  // unconstrained (no explicit Width/MinWidth/MaxWidth/InlineSize/
  // MinInlineSize/MaxInlineSize for the inline axis, or none of the
  // analogous block-axis properties), skip the `fit-content` initialiser
  // and the matching `min*` floor for the unconstrained axis. The browser
  // then performs the cross-axis transfer naturally.
  //
  // We read from the resolved `styles` object (post buildStyles spread)
  // because the SizeApplier converts every IR variant — physical, logical,
  // raw lengths, keywords — into the same CSS property names. That lets
  // us stay agnostic to which property type the parser produced.
  const hasAspectRatio = styles.aspectRatio !== undefined && styles.aspectRatio !== 'auto';
  const inlineAxisConstrained =
    styles.width !== undefined ||
    styles.minWidth !== undefined ||
    styles.maxWidth !== undefined ||
    styles.inlineSize !== undefined ||
    styles.minInlineSize !== undefined ||
    styles.maxInlineSize !== undefined;
  const blockAxisConstrained =
    styles.height !== undefined ||
    styles.minHeight !== undefined ||
    styles.maxHeight !== undefined ||
    styles.blockSize !== undefined ||
    styles.minBlockSize !== undefined ||
    styles.maxBlockSize !== undefined;
  // Only intervene when aspect-ratio + exactly-one-axis-constrained — the
  // case the spec actually covers. If both axes are constrained or neither
  // is, the browser doesn't transfer anyway and the existing fit-content
  // default is harmless.
  const aspectRatioInlineUnconstrained = hasAspectRatio && blockAxisConstrained && !inlineAxisConstrained;
  const aspectRatioBlockUnconstrained = hasAspectRatio && inlineAxisConstrained && !blockAxisConstrained;

  // Bug 1 — WPT block-flow widen carve-out — see
  // testing/titan/investigations/swarm-003/css-ui__negative-outline-offset.json
  //
  // The unconditional `width: 'fit-content' + minWidth:'50px' + minHeight:'30px'`
  // defaults are essential for the legacy 327-pair visual-test fixtures
  // (iOS/Android use intrinsic SwiftUI/Compose sizing — boxes hug their
  // content), but they actively break WPT tests whose pass criterion
  // depends on the styled element having its native block-flow body-width
  // layout (outline-offset, percentage backgrounds, calc()-extent widths,
  // any test where the box stretches to its containing block by default).
  //
  // Under WPT_MODE we skip the fit-content + minWidth/minHeight defaults
  // entirely so block-level elements get the browser's normal
  // width:auto / height:auto behaviour. The aspect-ratio carve-out is
  // a no-op in this branch since it was solving the same class of
  // problem (a different way) — WPT mode dominates. Legacy flow
  // (no `?wpt=1`) is unchanged.
  const containerStyles: CSSStyles = WPT_MODE ? {
    // In WPT mode we want browser-default block-flow: width:auto
    // (stretches to containing block), height:auto (hugs content),
    // no synthetic minimum floor. The IR's own `width` / `min-*` /
    // `max-*` come in via the spread below and take precedence.
    maxWidth: '100%',
    ...styles,
  } : {
    // Skip the `fit-content` initialiser when aspect-ratio needs to drive
    // the inline axis — see the block above. For every other component
    // (the 327-pair baseline) the existing default applies unchanged.
    ...(aspectRatioInlineUnconstrained ? {} : { width: 'fit-content' }),
    maxWidth: '100%',
    ...styles,
    // Ensure minimum dimensions for visibility, but ONLY when the IR
    // didn't declare an explicit max-width/max-height. Previously this
    // line set `minWidth: styles.width` which made `width: 300; max-width: 50`
    // resolve to 300 instead of 50 (CSS clamp: max(min, min(width, max)) → max(300,50)=300).
    // When max-* is declared we trust the IR fully and only fall back to a
    // hard 50/30 px floor if BOTH width and max-width are absent.
    // Respect IR-declared min/max first; only fall back to a height/
    // width-derived floor when the IR set NEITHER min nor max for that
    // axis. This keeps `width: 300; max-width: 50` resolving to 50 (was
    // forcing minWidth=300 → CSS clamp returned 300) AND
    // `height: 20; min-height: 120` resolving to 120 (was forcing
    // minHeight=20 → web ignored the larger min). Logical-axis variants
    // (min-/max-inline-size, min-/max-block-size) participate in the
    // same constraint resolution; in our LTR horizontal-tb canvas they
    // fold to width/height respectively, so we treat them as additional
    // sentinels that suppress the fallback.
    //
    // Aspect-ratio carve-out (swarm-001 css-sizing__block-aspect-ratio-032):
    // when we've intentionally left an axis unconstrained so the browser
    // can do aspect-ratio size transfer, we must NOT slap a min-* floor on
    // that axis either — a `min-width: 50px` would re-establish a
    // non-auto inline size and defeat the transfer the same way
    // `width: fit-content` did.
    //
    // Bug 3 — aspect-ratio + child intrinsic-size lift — see
    // testing/titan/investigations/swarm-003/css-sizing__block-aspect-ratio-015.json
    //
    // CSS Sizing 4 §6.2.2 says `min-width: auto` on an aspect-ratio box
    // resolves to the `min-content` of its contents. When the parent has
    // an aspect-ratio + a sized child, the spec wants the parent to grow
    // to fit the child's intrinsic width (which then makes both axes
    // definite and overrides the ratio transfer). Chromium's resolver
    // can lose that signal when the inner child is wrapped in our
    // placeholder shell, so make the rule explicit by injecting
    // `min-width: min-content` (resp. `min-height: min-content`) on the
    // aspect-ratio axis we left unconstrained, when there's a child to
    // lift from. `min-content` is harmless on the empty-box case (the
    // 032 fixture) — no children means min-content resolves to 0, so
    // the aspect-ratio transfer still fires as before.
    minWidth: aspectRatioInlineUnconstrained
      ? (hasChildren ? 'min-content' : undefined)
      : (styles.minWidth || styles.minInlineSize ||
        ((styles.maxWidth || styles.maxInlineSize) ? '0' : (styles.width || styles.inlineSize || '50px'))),
    minHeight: aspectRatioBlockUnconstrained
      ? (hasChildren ? 'min-content' : undefined)
      : (styles.minHeight || styles.minBlockSize ||
        ((styles.maxHeight || styles.maxBlockSize) ? '0' : (styles.height || styles.blockSize || '30px'))),
    // Empty grid/flex → behave like a block so the placeholder doesn't get
    // inflated by track/flex sizing. Applied AFTER the spread so it always
    // wins for empty containers; explicit `display` from styles is dropped
    // here because grid/flex layout makes no visual sense with zero items.
    ...(hasChildren ? {} : (
      styles.display === 'grid' || styles.display === 'flex' ||
      styles.display === 'inline-grid' || styles.display === 'inline-flex'
        ? { display: 'block' }
        : {}
    )),
  };

  // Render children or placeholder
  //
  // PlaceholderContent receives the parent's resolved background color so its
  // text colour can flip to dark-on-light or light-on-dark, matching iOS's
  // `PlaceholderLabel.resolvedColor` (luminance > 0.6 → dark text). Without
  // this, the web placeholder inherits whatever <body> color is in scope
  // (typically near-white from CapturePage), which renders nearly invisible
  // on white-card backgrounds (e.g. `Card_Complete`, `Input_Field`,
  // `Outline_Solid`). The mismatch was ~30% pixel divergence on every
  // light-card fixture.
  // Children recursion — see
  // testing/titan/investigations/swarm-001/css-overflow__clip-001.json
  //
  // Many WPT fixtures (overflow:clip on a parent, css-grid abspos, contain,
  // any composed parent>child geometry) require that the IR's tree structure
  // be preserved at render time. FIX-A teaches the extractor to emit
  // `children: IRComponent[]` on container components; this branch recurses
  // into them so the parent's CSS (clip context, scroll container, BFC,
  // grid track, flex slot) actually wraps the descendant's box in the DOM.
  // Components without children fall through to the placeholder branch
  // unchanged, preserving the 327-pair visual-test baseline.
  //
  // Mixed-content fix (Bug 1) — see
  // testing/titan/investigations/swarm-002/css-text-decor__text-decoration-decorating-box-thickness-001.json
  //
  // The two branches USED to be mutually exclusive: a component with both
  // _text and children would render only the children, silently dropping
  // the parent's surrounding text. For HTML mixed-content elements like
  // <div>abc <span>x</span> def</div> the parent's "abc def" never reached
  // the DOM, so its text-decoration had no glyph stream to underline. The
  // patch renders _text as a leading inline text node BEFORE the children,
  // wrapped in an inheriting <span> so the parent's CSS (color,
  // text-decoration, letter-spacing, font-*) propagates naturally. Known
  // limitation: text always renders BEFORE all children regardless of the
  // original DOM position; full inline-flow ordering needs an interleaved
  // inlineRuns IR shape (tracked as the secondary fix in the investigation).
  const text = component._text;
  const hasText = typeof text === 'string' && text.length > 0;
  // Bug 4 — pseudo-element rendering. The F-G-EXTRACTOR pipeline
  // populates `_pseudo.{before,after,marker}` from CSS rules like
  // `#test span::before { content: counter(c, decimal-leading-zero) }`.
  // We render each as an inline <span> whose styles come from the
  // synthetic IRComponent's properties (which include the `content`
  // declaration); Chromium then evaluates `counter()` / `counters()` /
  // `attr()` / literal strings natively against the parent's live
  // counter tree. See:
  //   testing/titan/investigations/swarm-003/css-lists__counter-001.json
  //   testing/titan/investigations/swarm-003/css-pseudo__before-preceding-whitespace-dynamic.json
  //
  // Components without `_pseudo` (the 327-pair baseline) skip these
  // branches entirely so the legacy DOM is byte-identical.
  const pseudo = component._pseudo;
  const hasBefore = !!(pseudo && pseudo.before);
  const hasAfter = !!(pseudo && pseudo.after);
  const hasMarker = !!(pseudo && pseudo.marker);
  // Render one pseudo-element node. The browser's `content` evaluator
  // only fires on actual ::before/::after/::marker boxes, so we cheat
  // by rendering an inline <span> whose styles include the IR's
  // `content` declaration — the browser ignores `content` on regular
  // elements, but the `content: counter(c, ...)` machinery is what we
  // actually need: we pull the resolved CSS via buildStyles and emit
  // the text via the leaf path so the glyph reaches the DOM. For now
  // we materialise the pseudo's `_text` directly (the simple-string
  // `content: "two"` case) AND attach the inline styles, so both
  // counter() (browser-evaluated) and literal-string cases land
  // visibly. The marker uses `display:list-item-marker`-style sizing
  // via `display: inline-block` and a small right-margin so it spaces
  // away from the host's leading content the way a native ::marker
  // would. Wrapped in <span> rather than <div> to preserve the inline
  // flow that ::before/::after participate in by default.
  const renderPseudo = (
    p: IRComponent,
    role: 'before' | 'after' | 'marker',
  ): React.ReactNode => {
    // Use buildStyles directly on the pseudo's properties so any
    // declarations from the originating CSS rule (font-weight, color,
    // letter-spacing, AND `content`) reach the inline style attribute.
    const ps = buildStyles(p.properties);
    // The pseudo's _text carries the literal value of `content:` when
    // it's a plain string (e.g. `content:"two"`); for functional
    // values (`counter(...)`), F-G-EXTRACTOR is expected to leave _text
    // empty and emit a `content` IRProperty that buildStyles forwards
    // into the inline style — the browser then evaluates the function.
    const pText = typeof p._text === 'string' ? p._text : '';
    // Marker pseudo gets a small trailing margin so it visually
    // separates from the host's content the way a native list-item
    // marker does (CSS-Lists 3 §4.3 — markers are typically preceded
    // by a marker-side gap).
    const markerStyle: React.CSSProperties = role === 'marker'
      ? { display: 'inline-block', marginInlineEnd: '0.5em' }
      : {};
    return (
      <span
        key={`pseudo-${role}-${p.id}`}
        data-pseudo={role}
        data-component-id={p.id}
        style={{ ...markerStyle, ...(ps as React.CSSProperties) }}
      >
        {pText}
      </span>
    );
  };
  const content = hasChildren ? (
    <>
      {/* Marker pseudo renders first (leading), then before, then
          the host text, then real children. The CSS spec orders
          ::marker before ::before, both before the inline content. */}
      {hasMarker ? renderPseudo(pseudo!.marker!, 'marker') : null}
      {hasBefore ? renderPseudo(pseudo!.before!, 'before') : null}
      {/* Leading parent text (Bug 1 mixed-content fix). Wrapped in a
          plain <span> so the parent <div>'s `color` /
          `text-decoration` / `font-*` / `letter-spacing` all inherit
          naturally. Suppressed when _text is missing/empty so legacy
          fixtures (the 327-pair baseline) stay byte-identical. */}
      {hasText ? <span>{text}</span> : null}
      {component.children!.map((child, index) => (
        <ComponentRenderer key={child.id || index} component={child} depth={depth + 1} />
      ))}
      {/* Trailing ::after pseudo renders last, after all children. */}
      {hasAfter ? renderPseudo(pseudo!.after!, 'after') : null}
    </>
  ) : (hasBefore || hasAfter || hasMarker) ? (
    // No real children but pseudo-elements present — still emit them
    // around the placeholder so counter/content/etc. become visible.
    <>
      {hasMarker ? renderPseudo(pseudo!.marker!, 'marker') : null}
      {hasBefore ? renderPseudo(pseudo!.before!, 'before') : null}
      <PlaceholderContent
        name={component.name}
        text={hasText ? text : undefined}
        backgroundColor={typeof styles.backgroundColor === 'string' ? styles.backgroundColor : undefined}
        explicitColor={typeof styles.color === 'string' ? styles.color : undefined}
      />
      {hasAfter ? renderPseudo(pseudo!.after!, 'after') : null}
    </>
  ) : (
    <PlaceholderContent
      name={component.name}
      // Inner-text rendering — see
      // testing/titan/investigations/swarm-001/css-color__color-001.json
      //
      // FIX-A is concurrently teaching the WPT extractor to preserve the
      // styled element's text content as `_text`. When that field is
      // present, the placeholder renders THAT string instead of the
      // component name (or the WPT_MODE empty string), so colour /
      // font / text-decor / letter-spacing / line-height tests have an
      // actual sentence to render and stop diverging at structural-
      // divergence with empty-card captures. When `_text` is absent, the
      // existing placeholder behaviour applies unchanged: WPT_MODE → '',
      // legacy mode → component name with underscores stripped.
      text={hasText ? text : undefined}
      backgroundColor={typeof styles.backgroundColor === 'string' ? styles.backgroundColor : undefined}
      // Pass through the user-declared text colour (CSS `color`) when
      // present, so a fixture like `Typography_FontUltraCondensed`
      // (`color: #e74c3c`) renders the placeholder in red instead of
      // the bg-luminance-derived dark/light grey. The container <div>
      // already carries the colour; without an explicit handoff the
      // <span> below would clobber it with its own pick. iOS / Android
      // both honour an explicit text colour via `textColor` parameter
      // — this is the web equivalent. When unset, falls back to the
      // bg-luminance contrast pick (matches iOS PlaceholderLabel).
      explicitColor={typeof styles.color === 'string' ? styles.color : undefined}
    />
  );

  // Bug 2: pick the DOM element type from `_tag` so the browser can
  // contribute its native default styling for that tag (list-marker
  // generation on <ol>/<ul>/<li>, paragraph spacing on <p>, table
  // layout on <table>/<tr>/<td>, etc.). Without this every IR node
  // rendered as <div> regardless of its source — making
  // `list-style-type: arabic-indic` (and ~350 other list-related WPT
  // tests) silently inert because Chromium's counter algorithm only
  // fires on `display: list-item`. See
  // testing/titan/investigations/swarm-002/css-counter-styles__css3-counter-styles-101.json.
  //
  // Allow-list rather than free-form: we only switch to tags whose
  // browser-default semantics we positively want (lists, headings,
  // paragraphs, tables, details). Anything else falls back to <div>
  // so we don't accidentally introduce form-control behaviour
  // (<input>, <button>), embed handling (<iframe>, <object>), or
  // script-context tags (<script>, <style>) into the SDUI renderer
  // surface. Lowercased for matching since the extractor emits
  // lowercase tags.
  const tag = (typeof component._tag === 'string' && component._tag.length > 0)
    ? component._tag.toLowerCase()
    : null;
  const TAG_ALLOWLIST = new Set([
    'ol', 'ul', 'li',
    'p',
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'table', 'thead', 'tbody', 'tfoot', 'tr', 'td', 'th',
    'details', 'summary',
    'blockquote', 'q',
    'dl', 'dt', 'dd',
    'figure', 'figcaption',
    'section', 'article', 'nav', 'header', 'footer', 'main', 'aside',
    // Bug 2 — emit <span> when F-G-EXTRACTOR forwards _tag:'span'. See
    // testing/titan/investigations/swarm-003/css-contain__content-visibility-hidden-and-innertext.json
    //
    // The CSS-Containment-2 spec says content-visibility:hidden does NOT
    // apply to non-atomic inline boxes (<span> with default display:inline
    // is the canonical non-atomic inline). Previously we always rendered
    // as <div>, which IS a block container, so the browser would
    // (correctly per its rule) hide the contents — but that's exactly
    // the opposite of what the test asserts. Emitting <span> lets the
    // browser observe the inline/block distinction the spec hinges on.
    'span',
    // Inline-level structural tags follow the same pattern — they need
    // to remain inline for surrounding inline-flow / generated-content
    // / whitespace-collapse rules to behave correctly.
    'strong', 'em', 'b', 'i', 'u', 's', 'mark', 'small', 'sub', 'sup',
    'code', 'kbd', 'samp', 'var', 'cite', 'dfn', 'abbr', 'time',
  ]);
  // React's createElement accepts a string element name, so we just
  // hand it the validated tag. Anything not in the allowlist (or
  // missing) keeps the legacy <div> path so the 327-pair baseline
  // doesn't shift. Use React.createElement (not a dynamic JSX element)
  // to keep the TypeScript prop-type intersection sensible across
  // every allowed tag — data-*, style, and children are valid on all
  // of them.
  const elementName: string = (tag && TAG_ALLOWLIST.has(tag)) ? tag : 'div';

  return React.createElement(
    elementName,
    {
      'data-component-id': component.id,
      'data-component-name': component.name,
      style: containerStyles as React.CSSProperties,
    },
    content
  );
}

/**
 * Placeholder content for components without children.
 */
interface PlaceholderContentProps {
  name: string;
  /**
   * Inner element text content from the IR (`IRComponent._text`). When
   * provided, takes precedence over the placeholder name AND over the
   * WPT_MODE empty-string suppression — i.e. it's the actual visible
   * content of the component, not a debug label. Set by the WPT extractor
   * for fixtures with styled <p>/<span>/<h*> elements; absent for the
   * legacy component-style fixtures where the harness still draws a
   * synthetic placeholder.
   *
   * See testing/titan/investigations/swarm-001/css-color__color-001.json.
   */
  text?: string;
  /** Resolved CSS color of the parent element (e.g. "rgb(255,255,255)"). */
  backgroundColor?: string;
  /**
   * Explicit text colour from CSS `color` on the parent element. When
   * provided, overrides the bg-luminance-derived contrast pick — the
   * placeholder renders in this exact colour. Mirrors the `textColor`
   * passthrough in iOS `PlaceholderLabel` and Android `PlaceholderContent`.
   */
  explicitColor?: string;
}

/**
 * Parse a CSS color string into linear-ish [r,g,b] in 0-1, or null if we
 * can't. Only handles the forms our StyleEngine emits today: `rgb(...)`,
 * `rgba(...)`, `#RGB`, `#RRGGBB`. Anything else (gradients, var(),
 * named colors) returns null and the placeholder falls back to the
 * "dark bg" branch — same default as iOS / Android.
 */
function parseRgb(css: string | undefined): [number, number, number] | null {
  if (!css) return null;
  const s = css.trim();
  // #RRGGBB or #RGB
  const hex = s.match(/^#([0-9a-f]{3,8})$/i);
  if (hex) {
    const h = hex[1];
    if (h.length === 3 || h.length === 4) {
      // #RGB → expand each digit
      return [
        parseInt(h[0] + h[0], 16) / 255,
        parseInt(h[1] + h[1], 16) / 255,
        parseInt(h[2] + h[2], 16) / 255,
      ];
    }
    if (h.length === 6 || h.length === 8) {
      return [
        parseInt(h.slice(0, 2), 16) / 255,
        parseInt(h.slice(2, 4), 16) / 255,
        parseInt(h.slice(4, 6), 16) / 255,
      ];
    }
    return null;
  }
  // rgb()/rgba(): pull the first three numeric components, sRGB.
  const rgb = s.match(/rgba?\(\s*([0-9.]+)[,\s]+([0-9.]+)[,\s]+([0-9.]+)/i);
  if (rgb) {
    return [
      parseFloat(rgb[1]) / 255,
      parseFloat(rgb[2]) / 255,
      parseFloat(rgb[3]) / 255,
    ];
  }
  return null;
}

function PlaceholderContent({ name, text, backgroundColor, explicitColor }: PlaceholderContentProps) {
  // Critical: inherit font properties from the parent so typography fixtures
  // render at their declared sizes/weights/etc. The previous implementation
  // hardcoded `fontSize: '11px'` here, which clobbered every Typography_*
  // component that had no children — the sidecar reporter caught it as
  // "ios=24 web=11" on Typography_Size and friends. Inheritance lets the
  // parent component's applied styles actually reach the rendered glyphs.
  //
  // Color resolution mirrors iOS `PlaceholderLabel.resolvedColor` byte-for-byte:
  //   • luminance = 0.299·R + 0.587·G + 0.114·B  (Rec.601 luma)
  //   • > 0.6 → dark text  Color(white: 0.2).opacity(0.7)  ≈ rgba(51,51,51,0.7)
  //   • else  → light text Color(white: 0.93).opacity(0.7) ≈ rgba(237,237,237,0.7)
  //   • no bg → light branch (matches iOS fallback when `bg.rgbComponents` nil)
  // This pulls Card_Complete / Input_Field / Outline_Solid / Button_Primary /
  // Tag_Chip etc. — every light-bg placeholder fixture — back into pixel
  // parity with iOS without touching the surrounding renderer.
  // Honour an explicit `color` from the IR before falling back to the
  // bg-luminance contrast pick. Without this, fixtures that set
  // `color: #e74c3c` (red) on a placeholder-only component get rendered
  // in dark grey because the auto-pick is keyed solely on background
  // brightness — masking real cross-platform divergences (iOS/Android
  // both honour explicit text colour, web didn't).
  const rgb = parseRgb(backgroundColor);
  const luminance = rgb ? 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2] : 0;
  const color = explicitColor
    ?? (luminance > 0.6
      ? 'rgba(51, 51, 51, 0.7)'    // dark on light
      : 'rgba(237, 237, 237, 0.7)'); // light on dark (and the no-bg fallback)
  // Visible-text resolution priority — see
  // testing/titan/investigations/swarm-001/css-color__color-001.json
  //
  //   1. `text` from IR (component._text) — the actual element text content
  //      preserved by the WPT extractor. Takes precedence over EVERYTHING
  //      including WPT_MODE: this IS the real content, not debug noise.
  //      Without this branch, css-color / css-text / css-fonts WPT tests
  //      render an empty card and sit permanently at structural-divergence.
  //
  //   2. WPT_MODE without `text` → empty string. Preserves the wrapping
  //      <span> (so an empty grid item still takes a track slot) but
  //      suppresses the pHash-poisoning `wpt__css-…__N` placeholder label
  //      that pilot-001 identified.
  //
  //   3. Legacy 327-pair flow (no `?wpt=1`, no `text`) → component name
  //      with underscores stripped. Unchanged from before.
  const visibleText = text !== undefined
    ? text
    : (WPT_MODE ? '' : name.replace(/_/g, ' '));
  return (
    <span
      style={{
        display: 'block',
        padding: '4px',
        // fontSize/fontWeight/letterSpacing/textTransform/etc. all
        // inherit by default — don't set them explicitly. Colour is the
        // exception: we drive it from bg luminance to match iOS.
        color,
        textAlign: 'inherit',
        wordBreak: 'break-word',
      }}
    >
      {visibleText}
    </span>
  );
}

/**
 * Render a list of components.
 */
interface ComponentListProps {
  components: IRComponent[];
}

export function ComponentList({ components }: ComponentListProps) {
  return (
    <>
      {components.map((component, index) => (
        <ComponentRenderer key={component.id || index} component={component} />
      ))}
    </>
  );
}

export default ComponentRenderer;
