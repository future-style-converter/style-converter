/**
 * TagMapping — the production `meta.sourceTag` → DOM element policy for
 * the package renderer (issue #41).
 *
 * DIVERGENCE FROM THE HARNESS (documented per the issue-#41 contract):
 * the capture harness maps sourceTag through a positive ALLOWLIST of
 * structural/inline tags and demotes everything else — including
 * replaced and interactive elements (img/button/a/input) — to <div>,
 * because a capture surface must never acquire form-control focus rings,
 * native button chrome differences across headless builds, or network-
 * fetching replaced content that could destabilise screenshot bytes.
 * wave-20 W1 narrows that divergence to the LEGACY (327-pair) flow only:
 * in WPT capture mode the harness passes the form/widget tags
 * (WidgetAttrs.WIDGET_TAGS) through like production does — the WPT
 * browser-ref paints real Chromium widget chrome, so demotion there was
 * the divergence — while adding `inert` + `tabIndex:-1` (via
 * RendererOptions.decorateProps) so focus rings still cannot occur.
 *
 * In production SDUI the calculus flips: `meta.sourceTag` is TRUSTED
 * server content (the converter wrote it from the authored markup), and
 * a real app WANTS native semantics — <button> keyboard/AT behaviour,
 * <a> link semantics, <input> editability, <img> replaced-element CSS
 * (object-fit, aspect-ratio transfer). So the default policy here is a
 * small DENYLIST: emit whatever the wire says unless the tag would break
 * the document itself (script/style execution, frame embedding,
 * head-only metadata tags) or is syntactically not a tag name at all.
 */

// No-silent-fallthrough logging (CLAUDE.md): a demoted or malformed
// sourceTag is recorded once via the tracker, never dropped silently.
import { logUnhandled } from '../engine/PropertyTracker';

/**
 * HTML void elements (WHATWG HTML §13.1.2) — elements that cannot have
 * children. The renderer must NOT hand React children for these (React
 * throws for some, silently drops for others); NodeRenderer renders them
 * childless and warns loudly when the composed tree disagrees.
 */
export const VOID_ELEMENTS: ReadonlySet<string> = new Set([
  'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input',
  'link', 'meta', 'param', 'source', 'track', 'wbr',
]);

/**
 * Tags the production renderer refuses even from trusted wire content:
 * script-execution surfaces, external-document embedders, template/slot
 * machinery, and document-structure tags that can only appear once (or
 * only in <head>). None of these is a stylable content box — emitting
 * them could execute code, replace the page, or corrupt the DOM tree —
 * so they demote to <div> and log through the tracker.
 */
export const DENYLISTED_TAGS: ReadonlySet<string> = new Set([
  'script', 'style',                                   // code/style injection
  'iframe', 'frame', 'frameset', 'object', 'embed',    // external-document embedding
  'portal', 'applet',                                  // legacy/exotic embedders
  'template', 'slot', 'noscript',                      // inert/shadow machinery — content wouldn't render
  'html', 'head', 'body',                              // singleton document structure
  'base', 'link', 'meta', 'title',                     // head-only metadata (void or text-only)
]);

/**
 * Valid element-name syntax: lowercase ASCII start, then lowercase
 * alphanumerics or hyphens. The hyphen keeps custom elements
 * (`my-widget`) renderable — React passes unknown tags through — while
 * rejecting anything that couldn't be a tag (spaces, brackets, unicode).
 */
const TAG_SYNTAX = /^[a-z][a-z0-9-]*$/;

/**
 * The default (production) sourceTag → element mapping.
 *
 * @param sourceTag lowercased `meta.sourceTag`, or null when absent.
 * @returns the element name to render — `'div'` when there is no tag,
 *          the tag is malformed, or the tag is denylisted.
 */
export function defaultMapTag(sourceTag: string | null): string {
  // Absent tag → the neutral block container, same as the harness.
  if (!sourceTag) return 'div';
  // Malformed names can't reach createElement — log + demote.
  if (!TAG_SYNTAX.test(sourceTag)) {
    logUnhandled('SourceTag', sourceTag);
    return 'div';
  }
  // Denylisted tags demote with a tracker record (never silently).
  if (DENYLISTED_TAGS.has(sourceTag)) {
    logUnhandled('SourceTag', sourceTag);
    return 'div';
  }
  // Everything else is trusted wire content — emit it natively.
  return sourceTag;
}
