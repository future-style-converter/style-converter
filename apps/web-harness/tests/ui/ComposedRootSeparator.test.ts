// wave-54 lane L6 (unit RS) — the root-level separator MECHANICS
// (apps/web-harness/src/ui/ComposedRootSeparator.ts), pinned WITHOUT the
// seam: the predicate is a parameter (PLAN §0 rule 2b), so these pins drive it
// with stubs and read only what this file owns — the interleave order, the
// first-root rule, the table-body decline and the container read. The pins on
// the REAL predicate over verbatim wire (box-sizing-007's 19 separators, the
// marker gate, the gallery call sites, call-equivalence with
// renderChildSeparator) ride seam-1.patch as ComposedRootSeparatorWire.test.tsx,
// because they need the lifted `wsAfterSeparator` export.
//
// EXECUTED MUTATIONS (sha256 before/after in tools/titan/results/wave54-web-tail/_note.md):
//   RS-m2 the table-box decline removed (`declined` forced false) → table_* red.
//   RS-m3 the first-root guard removed (index 0 also asks the predicate) → firstRoot_* red.

import { describe, it, expect } from 'vitest';
import type { IRDocument } from '@style-converter/web/core/ir/IRModels';
import { composeTree, type ComposedNode } from '../../src/sdui/Composer';
import {
  interleaveRootSeparators,
  rootSeparatorContainer,
  type RootSeparatorContainer,
} from '../../src/ui/ComposedRootSeparator';
import { resolveCanvasTableBody } from '../../src/ui/CanvasTableBody';

/** css-ui/box-sizing-007's first three roots, verbatim (wave53-final per-test IR): body-root, <p>, one <img>. */
const BS007_HEAD = [{"id":"wpt__css-ui__box-sizing-007__0-290","name":"wpt__css-ui__box-sizing-007__0","properties":[{"type":"MaxWidth","data":{"type":"length","px":700}}],"meta":{"role":"body-root"}},{"id":"wpt__css-ui__box-sizing-007__1-291","name":"wpt__css-ui__box-sizing-007__1","properties":[],"text":"Test passes if there are 20 filled green squares and they are the same size.","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-ui__box-sizing-007__2-292","name":"wpt__css-ui__box-sizing-007__2","properties":[{"type":"BoxSizing","data":"BORDER_BOX"},{"type":"Width","data":"auto"},{"type":"Height","data":"auto"},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"MarginTop","data":{"px":10}},{"type":"MarginRight","data":{"px":10}},{"type":"MarginBottom","data":{"px":10}},{"type":"MarginLeft","data":{"px":10}}],"meta":{"sourceTag":"img","role":"ws-after","attrs":{"src":"css/css-ui/support/w100_h100.svg"}}}];

/** A per-test document over a component list. */
const doc = (components: unknown[]): IRDocument =>
  ({ irVersion: 2, minReaderVersion: 2, components }) as unknown as IRDocument;
/** The forest of a document, as the gallery composes it. */
const forest = (components: unknown[]): ComposedNode[] => composeTree(doc(components));
/** A stub predicate that separates EVERY pair (isolates the mechanics from the WWS gates). */
const always = (): string => ' ';
/** Render each root as its id token, so the interleaved array reads as plain data. */
const ids = (r: ComposedNode): string => r.component.id;
const FLOW: RootSeparatorContainer = { display: 'flow-root', whiteSpace: undefined };

describe('interleaveRootSeparators — mechanics (wave-54 L6 RS)', () => {
  it('firstRoot_neverGetsASeparatorSlot_andPairsInterleaveInForestOrder', () => {
    const roots = forest(BS007_HEAD);
    // Forest order is the flat order: body-root, <p>, <img>.
    expect(interleaveRootSeparators(roots, ids, FLOW, always)).toEqual([
      'wpt__css-ui__box-sizing-007__0-290', ' ',
      'wpt__css-ui__box-sizing-007__1-291', ' ',
      'wpt__css-ui__box-sizing-007__2-292',
    ]);
  });

  it('nullAnswer_keepsThePairFlush_andTheArrayIsExactlyTheRenderedRoots', () => {
    const roots = forest(BS007_HEAD);
    // A document with no accepted pair is the bare `.map` — byte-identical DOM.
    expect(interleaveRootSeparators(roots, ids, FLOW, () => null)).toEqual(roots.map(ids));
  });

  it('predicate_isAskedAbout(prev, next, container)_inOrder', () => {
    const roots = forest(BS007_HEAD);
    const seen: string[] = [];
    // Record every question; the container object is passed through untouched.
    interleaveRootSeparators(roots, ids, FLOW, (p, n, c) => {
      seen.push(`${p.component.id}>${n.component.id}:${String(c.display)}`);
      return null;
    });
    expect(seen).toEqual([
      'wpt__css-ui__box-sizing-007__0-290>wpt__css-ui__box-sizing-007__1-291:flow-root',
      'wpt__css-ui__box-sizing-007__1-291>wpt__css-ui__box-sizing-007__2-292:flow-root',
    ]);
  });

  it('table_andInlineTable_wrapperDeclines_evenWhenThePredicateSeparates', () => {
    const roots = forest(BS007_HEAD);
    // The table-body plan's wrapper box: no separator in either table spelling.
    for (const display of ['table', 'inline-table']) {
      expect(interleaveRootSeparators(roots, ids, { display }, always)).toEqual(roots.map(ids));
    }
  });
});

describe('rootSeparatorContainer — the container read (wave-54 L6 RS)', () => {
  it('flowRoot_forEveryNonTableBody_withTheBodysDeclaredWhiteSpace', () => {
    // box-sizing-007: no table plan, the body declares no white-space.
    const d = doc(BS007_HEAD);
    expect(rootSeparatorContainer(d, resolveCanvasTableBody(d))).toEqual({ display: 'flow-root', whiteSpace: undefined });
  });

  it('table_whenTheBodyIsATableBox', () => {
    // The same roots under a `display: table` body take the plan's table box.
    const tableBody = [{ ...BS007_HEAD[0], properties: [{ type: 'Display', data: 'TABLE' }] }, ...BS007_HEAD.slice(1)];
    const d = doc(tableBody);
    expect(rootSeparatorContainer(d, resolveCanvasTableBody(d)).display).toBe('table');
  });

  it('whiteSpace_isTheBodyRootsOwnDeclaration_inCssSpelling', () => {
    // A preserving body is what the predicate's white-space decline reads.
    const preBody = [{ ...BS007_HEAD[0], properties: [{ type: 'WhiteSpace', data: 'PRE' }] }, ...BS007_HEAD.slice(1)];
    expect(rootSeparatorContainer(doc(preBody), null).whiteSpace).toBe('pre');
    // No body-root at all: nothing declared.
    expect(rootSeparatorContainer(doc(BS007_HEAD.slice(1)), null)).toEqual({ display: 'flow-root', whiteSpace: undefined });
  });
});
