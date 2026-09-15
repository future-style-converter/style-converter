#!/usr/bin/env node
//
// wave50-B2/census.mjs — the two corpus censuses lane B2's decisions rest on,
// as a runnable predicate rather than a sentence in a report.
//
// WHY THIS FILE IS COMMITTED. Retro finding A1#3: wave 49's red-square census
// shipped with no script and no predicate, and its figures do not reproduce.
// The standing rule in docs/BACKLOG.md is that a census a later wave must act
// on lives in the repo. Both censuses below read ONLY the frozen run
// directory, so they give the same answer on any machine that has it.
//
// USAGE (from the repo root)
//   node tools/titan/results/wave50-B2/census.mjs insets
//       Every bare-number (percentage) inset carrier in the frozen corpus,
//       with the containing block its PARENT publishes for it and the one it
//       publishes for its OWN children — the two levels of queue item 0(b) —
//       and the USED inset PercentInsetResolve produces from each.
//       Expected on wave49-final: 7 carriers in 7 tests; the two containing
//       blocks differ on 6 of them, but the USED INSET differs on exactly ONE
//       (css-position/position-relative-006). That is the whole prediction:
//       one moved cell, six untouched.
//
//   node tools/titan/results/wave50-B2/census.mjs inline
//       Every component ComponentRenderer's composed-WPT block-auto-width
//       fill currently stretches that CSS 2.1 §10.3.9 says is shrink-to-fit:
//       a DECLARED atomic inline-level display (inline-block / inline-flex /
//       inline-grid — inline-table excluded, the table lane owns it), no
//       Width/MinWidth/InlineSize/MinInlineSize, no aspect-ratio, not
//       absolutely or fixed positioned. Expected on wave49-final: 33 tests,
//       22 of them in horizontal writing mode (the blast radius of the seam
//       patch beside this file) and 11 in a vertical one (left frozen).
//
//   RUN_DIR=tools/titan/runs/<other-run> node …   to re-derive on a later gate.
//
// LIMITS, stated. "Vertical writing mode" USED to be approximated here by "the
// document contains a vertical/sideways WritingMode value anywhere". That was
// called a safe over-count and it was not: it hid 13 tests from the blast
// radius, because a vertical declaration on a SIBLING subtree marked the whole
// document vertical (wave-50 skeptic S3, prescription 2 —
// tools/titan/results/wave50-S3/probe-output/atomic-inline-census.txt). The
// mode is now resolved PER COMPONENT by inheritance (`verticalWritingMode`
// below: own declaration wins, else the nearest `slot.parent` that declares
// one), which reproduces the 22 / 11 split the S3 probe measured through the
// renderer's real `mergeInherited` + `TextExtractor.extractWritingModeConfig`.
// What is still approximated: this file resolves ONLY `writing-mode`, not the
// full `TextExtractor` config (`text-orientation`, the `sideways-*` fallbacks),
// and it does not model `display: contents` re-parenting — neither differs on
// any wave49-final carrier, and both would be a real limit on a future corpus.

import fs from 'node:fs';
import path from 'node:path';

// The frozen gate run these numbers were derived against.
const RUN = process.env.RUN_DIR || 'tools/titan/runs/wave49-final';
const SECTIONS = path.join(RUN, 'sections');

// The eight inset longhands whose IR payload is a BARE NUMBER when the author
// wrote a percentage (InsetValueSerializer — see PercentInsetResolve's header).
const INSETS = new Set([
  'Top', 'Right', 'Bottom', 'Left',
  'InsetBlockStart', 'InsetBlockEnd', 'InsetInlineStart', 'InsetInlineEnd',
]);
// The atomic inline-level outer display roles (css-display-3 §2.1).
const ATOMIC = new Set(['INLINE_BLOCK', 'INLINE_FLEX', 'INLINE_GRID']);
// The css-writing-modes-4 §3.1 values whose block flow is VERTICAL. The wire
// carries the keyword underscore-upper-cased (`VERTICAL_RL`, `SIDEWAYS_LR`, …),
// and `HORIZONTAL_TB` is the only horizontal one.
const VERTICAL_WM = /^(VERTICAL|SIDEWAYS)_/;
// What ComponentRenderer's `hasExplicitWidth` reads in horizontal flow.
const WIDTHS = ['Width', 'MinWidth', 'InlineSize', 'MinInlineSize'];

/** Every per-test IR document of the frozen run, with its section name. */
function* documents() {
  for (const sec of fs.readdirSync(SECTIONS).sort()) {
    const dir = path.join(SECTIONS, sec, 'per-test-ir');
    if (!fs.existsSync(dir)) continue;
    for (const f of fs.readdirSync(dir).sort()) {
      if (!f.endsWith('.json')) continue;
      yield { sec, test: f.replace(/\.json$/, ''), doc: JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')) };
    }
  }
}

/** Top-level px of the first of [types] present — the `pxOf` of childContainingBlock. */
const pxOf = (props, ...types) => {
  for (const t of types) {
    const d = (props || []).find((p) => p.type === t)?.data;
    if (d && typeof d === 'object' && typeof d.px === 'number') return d.px;
  }
  return null;
};

/**
 * DynamicValueResolver.childContainingBlock, transcribed: the CONTENT box a
 * component publishes for its children, null on an axis with no definite size.
 * The percentage-of-a-known-parent leg is omitted because no carrier uses it.
 */
const childContainingBlock = (props) => {
  const w = pxOf(props, 'Width', 'InlineSize');
  const h = pxOf(props, 'Height', 'BlockSize');
  return [
    w === null ? null : w
      - (pxOf(props, 'PaddingLeft', 'PaddingInlineStart') ?? 0)
      - (pxOf(props, 'PaddingRight', 'PaddingInlineEnd') ?? 0)
      - (pxOf(props, 'BorderLeftWidth') ?? 0) - (pxOf(props, 'BorderRightWidth') ?? 0),
    h === null ? null : h
      - (pxOf(props, 'PaddingTop', 'PaddingBlockStart') ?? 0)
      - (pxOf(props, 'PaddingBottom', 'PaddingBlockEnd') ?? 0)
      - (pxOf(props, 'BorderTopWidth') ?? 0) - (pxOf(props, 'BorderBottomWidth') ?? 0),
  ];
};

/** The three browser-ref verdicts of one test, as `<platform><P|f><ssim>`. */
const verdicts = (sec, test) => {
  const mp = path.join(SECTIONS, sec, 'manifest.json');
  if (!fs.existsSync(mp)) return '(no manifest)';
  const m = JSON.parse(fs.readFileSync(mp, 'utf8'));
  // The manifest keys are WPT paths; the per-test-ir basename is the same path
  // with every separator folded to "__" and a "wpt__" prefix.
  const key = Object.keys(m.wpt.results).find(
    (k) => 'wpt__' + k.replace(/^css\//, '').replace(/\.html$/, '').replace(/\//g, '__') === test,
  );
  const d = key ? (m.wpt.results[key].browserRef?.diffs || {}) : {};
  return ['web-ref', 'ios-ref', 'android-ref']
    .map((p) => (d[p] ? `${p.split('-')[0]}:${d[p].wptPass ? 'P' : 'f'}/${d[p].ssim}` : `${p.split('-')[0]}:-`))
    .join('  ');
};

/**
 * PercentInsetResolve.resolve, transcribed for ONE side: the used inset in px.
 * `cb` null-on-both-axes is the channel-gap guard (keep the legacy
 * number-as-pixels value); a null on the RELEVANT axis alone is
 * CSS-indefinite, which css-position-3 §relpos-insets resolves to 0.
 */
const usedInset = (type, percent, cb) => {
  if (cb === 'ROOT') return percent;                       // no parent modelled
  const [w, h] = cb;
  if (w === null && h === null) return percent;            // channel-gap guard
  const blockAxis = type === 'Top' || type === 'Bottom'
    || type === 'InsetBlockStart' || type === 'InsetBlockEnd';
  const base = blockAxis ? h : w;
  return base === null ? 0 : (base * percent) / 100;
};

function insets() {
  let n = 0, cbDiffer = 0, usedDiffer = 0;
  for (const { sec, test, doc } of documents()) {
    const byId = new Map((doc.components || []).map((c) => [c.id, c]));
    for (const c of doc.components || []) {
      const pct = (c.properties || []).filter((p) => INSETS.has(p.type) && typeof p.data === 'number');
      if (!pct.length) continue;
      n++;
      const parent = c.slot?.parent ? byId.get(c.slot.parent) : null;
      // The element's OWN containing block: what its PARENT publishes for it.
      // This is what the seam patch beside this file makes the resolver read.
      const elementCb = parent ? childContainingBlock(parent.properties) : 'ROOT';
      // What a `Modifier.composed` factory reads today: what the element
      // publishes for its own children — one level too deep.
      const ambientCb = childContainingBlock(c.properties);
      const sameCb = JSON.stringify(elementCb) === JSON.stringify(ambientCb);
      if (!sameCb) cbDiffer++;
      const used = pct.map((p) => {
        const right = usedInset(p.type, p.data, elementCb);
        const today = usedInset(p.type, p.data, ambientCb);
        return { type: p.type, pct: p.data, right, today, moves: right !== today };
      });
      const moves = used.some((u) => u.moves);
      if (moves) usedDiffer++;
      console.log(
        `${moves ? '≠ ' : '  '}${sec}/${test}\n    ${c.id}` +
        `\n    element-level cb ${JSON.stringify(elementCb)}   child-level cb ${JSON.stringify(ambientCb)}` +
        used.map((u) => `\n    ${u.type}=${u.pct}%  used: element-level ${u.right}px  child-level ${u.today}px` +
          `${u.moves ? '   ← MOVES' : ''}`).join('') +
        `\n    ${verdicts(sec, test)}`,
      );
    }
  }
  console.log(`\ncarriers: ${n} · containing block differs on: ${cbDiffer} · USED INSET differs on: ${usedDiffer}`);
}

/**
 * The component's USED `writing-mode`, resolved the way the renderer resolves
 * it: `writing-mode` INHERITS (css-writing-modes-4 §3.1), so a component's own
 * declaration wins and, failing that, the nearest ancestor's does — walking
 * `slot.parent` up the flat v2 component list (schema/spec/03-children.md).
 * No declaration anywhere on the chain ⇒ the `horizontal-tb` initial value.
 *
 * Wave-50 lane F1 (skeptic S3) replaced a DOCUMENT-WIDE regex here
 * (`/"WritingMode"[^}]*?"(VERTICAL|SIDEWAYS)/` over the whole serialized doc),
 * which called a whole test vertical when ANY component in it declared a
 * vertical mode. That over-count was the stated "safe direction", but it was
 * wrong by 13 tests: the census reported 9 horizontal carriers when the
 * measured split is 22 horizontal / 11 vertical
 * (tools/titan/results/wave50-S3/probe-output/atomic-inline-census.txt, a
 * probe that runs the renderer's real `mergeInherited` +
 * `TextExtractor.extractWritingModeConfig`). Thirteen tests whose vertical
 * declaration sits on a SIBLING subtree — the eight `ch-units-vrl-*`, both
 * orthogonal-flow tests, the three `css-tables` collapsed-border overflow
 * tests — were hidden from the blast radius by it.
 *
 * @param byId  every component of this document, keyed by id.
 * @param c     the component whose used mode is wanted.
 */
const verticalWritingMode = (byId, c) => {
  // Guard against a malformed `slot.parent` cycle: at most one hop per
  // component in the document, which no acyclic chain can exceed.
  let node = c, hops = byId.size + 1;
  while (node && hops-- > 0) {
    // Last declaration wins within one component — the wave-7 bucket fold
    // appends the cascade winner, the same "last wins" the predicate uses.
    const d = [...(node.properties || [])].reverse()
      .find((p) => p.type === 'WritingMode')?.data;
    // Both live wire shapes: the bare string every corpus carrier uses and
    // the {"keyword": …} wrapper (see AtomicInlineShrinkToFit.declaredDisplay).
    const kw = typeof d === 'string' ? d
      : (d && typeof d === 'object' && typeof d.keyword === 'string' ? d.keyword : null);
    if (kw) return VERTICAL_WM.test(kw.toUpperCase().replace(/-/g, '_'));
    node = node.slot?.parent ? byId.get(node.slot.parent) : null;
  }
  return false; // css-writing-modes-4 §3.1 initial value: horizontal-tb.
};

function inline() {
  const horz = [], vert = [];
  for (const { sec, test, doc } of documents()) {
    // The flat v2 component list, keyed for the `slot.parent` walk above.
    const byId = new Map((doc.components || []).map((c) => [c.id, c]));
    // Per-test bucketing still, because a cell is a test: a test lands in the
    // vertical bucket only if the carrier the predicate found is itself in a
    // vertical flow. (On wave49-final no test carries both kinds.)
    let isVertical = false;
    const hits = [];
    for (const c of doc.components || []) {
      const props = c.properties || [];
      // Last Display wins — the wave-7 bucket fold appends the winner.
      const d = [...props].reverse().find((p) => p.type === 'Display')?.data;
      const kw = typeof d === 'string' ? d.toUpperCase().replace(/-/g, '_')
        : (d && typeof d === 'object' && typeof d.keyword === 'string' ? d.keyword.toUpperCase().replace(/-/g, '_') : null);
      if (!kw || !ATOMIC.has(kw)) continue;
      if (props.some((p) => WIDTHS.includes(p.type))) continue;          // hasExplicitWidth
      if (props.some((p) => p.type === 'AspectRatio')) continue;         // hasAspectRatio
      const pos = [...props].reverse().find((p) => p.type === 'Position')?.data;
      if (pos === 'ABSOLUTE' || pos === 'FIXED') continue;               // isOutOfFlow
      // The predicate's own scope gate (AtomicInlineShrinkToFit scope note 4):
      // a vertical flow keeps the wave-47 Z2 orthogonal branch's geometry.
      const vwm = verticalWritingMode(byId, c);
      if (vwm) isVertical = true;
      hits.push(`${c.id}:${kw}|vwm=${vwm}`);
    }
    if (hits.length) (isVertical ? vert : horz).push(`${sec}/${test}\n    ${hits.join('\n    ')}\n    ${verdicts(sec, test)}`);
  }
  console.log('── HORIZONTAL writing mode — the seam patch changes these ──');
  console.log(horz.join('\n'));
  console.log('\n── VERTICAL writing mode — left on the frozen orthogonal branch ──');
  console.log(vert.join('\n'));
  console.log(`\nhorizontal tests: ${horz.length} · vertical tests: ${vert.length}`);
}

const mode = process.argv[2];
if (mode === 'insets') insets();
else if (mode === 'inline') inline();
else { console.error('usage: census.mjs insets|inline'); process.exit(2); }
