/**
 * DocumentRenderer — the top-level production renderer of
 * @style-converter/web (issue #41): give it a DECODED IR document and it
 * renders the whole thing — slot composition (Composer), per-component
 * DOM with engine styles inline (NodeRenderer), and the document
 * stylesheet lifecycle (useDocumentRules: selector/media rules,
 * light-dark() opt-in, @keyframes — mounted on render, removed on
 * unmount).
 *
 * Decode stays explicit at the call site (`decodeIRDocument(json)`) so
 * apps control where wire errors surface; this component assumes a
 * well-formed v2 document.
 *
 * Renders pure CSS semantics by default; pass `options` (RendererOptions)
 * only when a host needs calibrated behaviour — the capture harness is
 * the canonical example of such a skin.
 */

import { createElement, Fragment, useMemo } from 'react';
import type { ReactElement } from 'react';
import type { IRDocument } from '../core/ir/IRModels';
import { composeTree } from './Composer';
import { NodeRenderer } from './NodeRenderer';
import type { RendererOptions } from './RendererOptions';
import { useDocumentRules } from './useDocumentRules';

/** Props for a whole-document render. */
export interface DocumentRendererProps {
  /** Decoded IR v2 document (run raw JSON through decodeIRDocument first). */
  document: IRDocument;
  /** Optional calibration hooks, applied at every composition depth. */
  options?: RendererOptions;
  /** Optional explicit Document for the stylesheet mount (iframes/tests). */
  styleTarget?: Document;
}

/**
 * Render a decoded IR document as a root forest. Mode A documents
 * compose via slot refs; Mode B (zero-slot) documents render as a flat
 * root list — both fall out of composeTree.
 */
export function DocumentRenderer({ document: doc, options, styleTarget }: DocumentRendererProps): ReactElement {
  // Composition is pure per document — memoise on identity.
  const roots = useMemo(() => composeTree(doc), [doc]);
  // Stylesheet lifecycle: mount rules for THIS document, clean up on
  // unmount (see useDocumentRules for the single-document contract).
  useDocumentRules(doc, styleTarget);
  // Root forest in flat-array order (spec 03 sibling-order rule); the
  // mapped array is keyed by component id like every child list.
  return createElement(
    Fragment,
    null,
    roots.map((root, index) =>
      createElement(NodeRenderer, {
        key: root.component.id || index,
        node: root,
        options,
      })),
  );
}
