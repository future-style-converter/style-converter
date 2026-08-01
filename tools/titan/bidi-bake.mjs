#!/usr/bin/env node
//
// tools/titan/bidi-bake.mjs — wave-23 THE BIDI BAKE.
//
// THE WALL: css-text/bidi/{bidi-lines-001, bidi-lines-002, bidi-tab-001}
// fail on ALL THREE platforms (web 0.87–0.93 against the browser ref, iOS/
// Android 0.81–0.82). The cause is not three separate renderer bugs — it is
// one structural gap:
//
//   UAX#9 visual reordering is a WHOLE-PARAGRAPH computation. The IR carries
//   text runs in LOGICAL order together with the CSS that would drive the
//   reorder (`direction`, `unicode-bidi: plaintext`, `dir=` attributes), but
//   the reorder's INPUT is the paragraph context of the original document —
//   which the fixture's component tree has already destroyed (inline runs
//   merged, `<span>`s split into sibling components, `\t` tab stops resolved
//   against a line box nobody kept). Even the WEB harness, whose text stack
//   IS Chromium's, renders differently from the original document, because
//   it is re-laying-out fragments, not the paragraph.
//
// THE PRECEDENT: when a browser-resolvable computation cannot ride the IR,
// the extractor bakes Chromium's OWN resolution into the fixture and marks
// it LOUDLY. That is the sibling-index bake (wave 21), the keyframe
// t-sampler (wave 13), the `dir=auto` first-strong resolution (wave 15) and
// the post-load state/structure extraction (waves 16/20). This module is the
// same move for bidi: load the TEST page in headless Chromium under the
// browser-ref canvas contract, read the VISUAL text geometry straight out of
// the layout engine, and re-express it as ordinary positioned boxes.
//
// THE WIRE — deliberately, aggressively boring:
//
//   Every baked text run lands as a PLAIN component with `position:absolute`
//   + `left`/`top`/`width`/`height` in px and a `_text` string that is a
//   SINGLE bidi level (one uniform direction). No new IR property, no new
//   `meta.visualRuns` channel, no new native machinery: each of the three
//   runtimes already renders positioned text (the post-load state bake ships
//   `position`/inset px through the same lane, and every platform has a
//   layout/position triplet). A single-level run is also the one string
//   shape whose rendering all three text stacks agree on WITHOUT
//   whole-paragraph bidi — ICU (Compose), CoreText (SwiftUI) and Blink (web)
//   resolve a uniform-direction string identically. That is why the runs are
//   split at Chromium's OWN level boundaries (derived from geometry, below)
//   and why a run that would mix strong-L and strong-R characters BAILS
//   instead of shipping a reorder-ambiguous string.
//
//   The elements around the runs are baked the same way: inside a bake root
//   every kept component becomes an absolutely positioned box carrying its
//   used rect. That is not gold-plating — bidi-tab-001's whole signal is the
//   yellow `<span>` BACKGROUND box (the tab advance lives inside it), and an
//   emptied span would collapse to zero width.
//
// SCOPE BOUNDARY (documented, enforced, never silent):
//   - TRIGGERED ONLY BY REAL BIDI CONTENT. The static detector lives in
//     extract-fixture.mjs (`bidiBakeTrigger`) and fires only on RTL-range /
//     bidi-control codepoints, `dir=rtl|auto` attributes, or `direction:rtl`
//     / non-normal `unicode-bidi` declarations. A pure-LTR test never
//     reaches this module, so its text is NEVER reordered or repositioned.
//   - PER-ELEMENT, NOT PER-DOCUMENT. Even inside a triggered test only the
//     BAKE ROOTS are touched: the nearest block-level ancestor-or-self of an
//     element the BROWSER reports as bidi-affected (computed `direction:rtl`,
//     non-normal computed `unicode-bidi`, or own text containing RTL
//     codepoints). bidi-lines-002's intro `<p>` is pure ASCII and comes out
//     byte-identical.
//   - CHROMIUM IS THE ORACLE, WARTS INCLUDED. Measured on this corpus:
//     Chromium's rendering of bidi-lines-001 and bidi-tab-001 matches their
//     references pixel-for-pixel, but Chromium FAILS bidi-lines-002 (the
//     `unicode-bidi:plaintext` neutral-paragraph rule of css-text-3
//     §"Bidirectionality": lines 1 and 5 land left instead of right). The
//     bake therefore converges the three platforms ONTO CHROMIUM, not onto
//     the reference: platform-pair SSIM rises, the residual web-ref gap on
//     bidi-lines-002 is a BROWSER divergence from the reference and is
//     reported as such. Baking a wrong-but-real browser layout is honest;
//     hand-authoring the spec-correct one would not be.
//   - NO FRAGMENTED BOXES. A kept element with more than one client rect
//     (an inline split across line boxes / columns) has no single box to
//     bake, so the whole test bails to the static path.
//   - NO PSEUDO GEOMETRY, NO PROPAGATED DECORATIONS. `_pseudo` content and
//     `text-decoration-line` propagate from boxes we are about to dissolve;
//     both bail rather than silently drop.
//
// ACTIVATION (opt-in, same shape as the wave-16 post-load mode):
//   BIDI_BAKE=1 node tools/titan/extract-fixture.mjs <paths>...
//   node tools/titan/extract-fixture.mjs --bidi-bake <paths>...
//   node tools/titan/bidi-bake.mjs <paths>...          (this CLI, end-to-end)
//
// HONESTY STAMPS: `_wpt.bidiBaked: true` on the fixture, plus
// `_lossy` + `baked-bidi-visual-order` in `_lossyReasons` on every bake root
// and in the fixture-level roll-up. The three bidi tests carry NO
// EXTRACTION_WALL_TAGS (their notApplicable tags are requires-bundled-font /
// requires-inline-FC / requires-shared-inline-FC, none of which the score
// gate excludes on), so they already score normally — inject-wpt-block's
// applyNaScoreGate needs NO change for this wave, and the new lossy reason
// is a fresh string that can never collide with a SCORE_EXCLUDED_TAG.
//
// Exit codes: 0 — every input handled (baked, skipped or an expected bail);
// 1 — at least one hard error (browser / IO failure).

import puppeteer from 'puppeteer';
import { promises as fs } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

// The static extractor's own building blocks. Reusing them (never copying)
// is what guarantees the browser walk and the static walk cannot drift: the
// merge guard, the head-only filter and the trigger all come from ONE place.
import {
  stripComments, extractInlineStyle, parseCss, collectStyledTags,
  HEAD_ONLY_TAGS, INLINE_MERGE_TAGS,
  bidiBakeTrigger, RTL_CODEPOINT_RANGES,
  extractFixture, writeFixturePair,
} from './extract-fixture.mjs';
// The wave-16/20 post-load module's PURE cross-check surface. The traversal
// identity (static paths ⇄ browser walk) is exactly the mapping this bake
// needs, and sharing it means a fix to either walker fixes both bakes.
// (`getBrowser` itself is module-private there and this wave does not own
// that file, so the ~15-line page-setup sequence below is re-expressed here
// against the SAME exported canvas constants — the contract cannot drift
// even though the call sequence is stated twice.)
import {
  staticPathsForHtml, mappingMismatch, componentAtPath,
  postLoadDecline, CANVAS_FRAME_STYLE_ID,
} from './post-load-extract.mjs';
// The ONE canonical fixture-stem derivation — component ids are
// `<stem>__N…`, so the bake must rebuild them from the same subdir-encoded
// stem extractFixture seeded buildComponents with (wave-21 collision fix).
import { fixtureStem } from './safe-name.mjs';
// The browser-ref rendering contract: same launch flags, same 390-wide white
// canvas + 16px pad, same embedded Inter faces + line-height pin. Geometry
// must be measured under the environment the ref PNGs and the harness
// captures are produced in, or every baked left/top would carry a systematic
// offset (the corpus-v4.1 font-pin lesson).
import {
  BROWSER_LAUNCH_ARGS, CANVAS_BG, CANVAS_PAD_PX,
  REF_FONT_STACK, REF_LINE_HEIGHT, interFontFaceCss,
} from './capture-browser-ref.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname  = dirname(__filename);
const REPO_ROOT  = resolve(__dirname, '..', '..');
// Same corpus-root override every other titan tool honours (unit tests point
// WPT_DIR at scratch corpora).
const WPT_DIR    = process.env.WPT_DIR ?? join(REPO_ROOT, 'tools', 'wpt');

// ── Tunables (exported so the unit pins assert the exact numbers) ────────────

/** Geometry epsilon, in CSS px, for "these two rects are on the same line"
 *  and "this glyph box is adjacent to the previous one". Chromium reports
 *  sub-pixel advances (measured: 9.47, 5.25, 11.83 …) and consecutive glyph
 *  boxes in one bidi level abut EXACTLY, so a sub-pixel tolerance is enough
 *  to absorb float noise without ever bridging a real bidi-level gap (the
 *  smallest gap in this corpus is the 96.33px tab advance). */
export const RUN_ADJACENCY_EPS = 0.75;

/** Hard cap on baked runs per fixture. A bidi bake dissolves normal flow
 *  into absolute boxes; a pathological test (a whole RTL article) would turn
 *  into thousands of components and stop being a meaningful cross-platform
 *  comparison. Over the cap the test bails to the static path, honestly. */
export const MAX_BIDI_RUNS = 300;

/** Computed `display` values that can host absolutely positioned children
 *  AND accept an explicit used width/height — i.e. legal bake roots. Inline
 *  boxes are excluded on purpose: `width`/`height` do not apply to them, so
 *  a baked inline root could not carry the used geometry its children are
 *  measured against. */
export const BLOCKISH_DISPLAYS = [
  'block', 'flow-root', 'list-item', 'inline-block',
  'flex', 'inline-flex', 'grid', 'inline-grid',
  'table', 'inline-table', 'table-cell', 'table-caption',
];

/** Computed `unicode-bidi` values that mean an AUTHOR asked for bidi
 *  processing. Plain `isolate` is deliberately absent: the HTML rendering
 *  spec's UA stylesheet gives `unicode-bidi: isolate` to every block
 *  container (measured — Chromium reports it for a bare `<p>`), so treating
 *  it as a signal would make the bake fire on every block of every triggered
 *  document. An author-set `isolate` always arrives with a `dir` attribute
 *  or an RTL neighbour, both of which ARE signals. */
export const AUTHOR_UNICODE_BIDI_VALUES = [
  'embed', 'bidi-override', 'isolate-override', 'plaintext',
];

/** The LOUD marker every baked root (and the fixture roll-up) carries. Never
 *  consulted by applyNaScoreGate — SCORE_EXCLUDED_TAGS holds no such string —
 *  so it is informational provenance, exactly like 'baked-sibling-index'. */
export const BIDI_BAKE_LOSSY_REASON = 'baked-bidi-visual-order';

// ── Pure text helpers ───────────────────────────────────────────────────────

/** True when `str` contains a codepoint in the RTL / bidi-control ranges the
 *  detector is built from (RTL_CODEPOINT_RANGES lives in extract-fixture.mjs
 *  so the static trigger and this runtime check can never disagree about
 *  what "bidi content" means). */
export function hasRtlCodepoint(str) {
  // Iterate by CODE POINT: the ranges include astral planes (Adlam, Arabic
  // Mathematical Alphabetic Symbols), which a UTF-16 unit walk would miss.
  for (const ch of String(str ?? '')) {
    const cp = ch.codePointAt(0);
    for (const [lo, hi] of RTL_CODEPOINT_RANGES) if (cp >= lo && cp <= hi) return true;
  }
  return false;
}

/** True when `str` carries at least one strong LEFT-TO-RIGHT character.
 *  Approximated as "a Unicode letter that is not in the RTL ranges" — digits
 *  (\p{N}) are UAX#9 weak and punctuation is neutral, so neither can make a
 *  run direction-ambiguous, and a letter outside the RTL ranges is strong L
 *  for every script this corpus contains. Used ONLY by the mixed-run guard,
 *  whose failure mode is a conservative bail. */
export function hasStrongLtrCodepoint(str) {
  for (const ch of String(str ?? '')) {
    // Skip the RTL side first so a Hebrew/Arabic letter never counts as L.
    if (hasRtlCodepoint(ch)) continue;
    if (/\p{L}/u.test(ch)) return true;
  }
  return false;
}

/** True when `str` carries at least one strong right-to-left LETTER — an RTL
 *  codepoint that is also a Unicode letter. The letter test matters because
 *  RTL_CODEPOINT_RANGES is BLOCK-based: Arabic-Indic digits (U+0660–0669) and
 *  Arabic punctuation live inside the Arabic block but are UAX#9 AN/CS/ON,
 *  not strong R, and an AN run legitimately advances LEFT-TO-RIGHT (level 2)
 *  inside an RTL paragraph. Only a strong-R LETTER laid out left-to-right can
 *  mean an override. */
export function hasStrongRtlLetter(str) {
  for (const ch of String(str ?? '')) {
    if (hasRtlCodepoint(ch) && /\p{L}/u.test(ch)) return true;
  }
  return false;
}

/** A run whose string mixes strong-L and strong-R characters has no single
 *  bidi level, so the three text stacks would each re-resolve it against
 *  their own paragraph direction — the exact ambiguity this bake exists to
 *  remove. Geometry alone cannot produce such a run (adjacent glyph boxes in
 *  one direction ARE one level) except under `unicode-bidi: bidi-override`,
 *  where Chromium lays strong-R glyphs out left-to-right. Detecting it is
 *  cheap; the caller bails the whole test.
 *
 *  SKEPTIC WAVE-23 — `text` alone is NOT sufficient for the override case the
 *  paragraph above names. `bidi-override` on HOMOGENEOUS text produces a run
 *  with only ONE strong class, so the mixed test never fires: measured in
 *  Chromium, `<div style="direction:ltr;unicode-bidi:bidi-override">سلام</div>`
 *  paints `سلام` left-to-right, and the re-emitted run paints `مالس` — the
 *  string reversed, silently, with no bail. The geometric direction the
 *  grouper already measured is the missing evidence: when it CONTRADICTS the
 *  run's own strong class (strong-R letters advancing rightward, or strong-L
 *  letters advancing leftward) the layout can only be an override, and no
 *  plain positioned text box can reproduce it. `dir` is optional so every
 *  existing caller/pin that passes a bare string keeps its old behaviour. */
export function runDirectionConflict(text, dir = null) {
  if (hasRtlCodepoint(text) && hasStrongLtrCodepoint(text)) return true;
  // Override detection — only meaningful once the run has a measured
  // direction (a single glyph never continued, so `dir` stays null and there
  // is no order to contradict).
  if (dir === 'ltr' && hasStrongRtlLetter(text)) return true;
  if (dir === 'rtl' && hasStrongLtrCodepoint(text) && !hasStrongRtlLetter(text)) return true;
  return false;
}

// ── Pure geometry: characters → visual runs ─────────────────────────────────

/** Round to 2 dp — the precision every rect in this module is quantised to,
 *  matching post-load-extract's rect snapshots so the two bakes' numbers are
 *  comparable in a diff. */
export function r2(n) { return Math.round(n * 100) / 100; }

/** Format a px length for the fixture's CSS-shaped property map. */
export function px(n) { return `${r2(n)}px`; }

/**
 * Group a text node's per-character boxes into VISUAL RUNS — maximal spans
 * of logically-consecutive characters that Chromium laid out on one line,
 * contiguously, in ONE direction. That is the geometric definition of a
 * UAX#9 level run, read out of the layout engine instead of reimplemented:
 * inside a level, consecutive glyph boxes abut exactly (LTR: each box starts
 * where the previous ended; RTL: each box ENDS where the previous began), and
 * a level change or a tab stop breaks that adjacency.
 *
 * `chars` is `[{ c, rects }]` in LOGICAL order, `rects` being the character
 * range's client rects (already 2-dp rounded by the in-page walker).
 * Returns `[{ text, dir, x, y, width, height }]` in logical-run order, each
 * run's `text` still in LOGICAL order (a single-level string every text
 * stack orders identically) and its box the union of its characters' boxes.
 */
export function groupCharRuns(chars) {
  const runs = [];
  let cur = null;                    // the run under construction, or null

  // Close the current run: trim whitespace off both LOGICAL ends (a leading
  // or trailing space is not ink, and engines are free to hang or collapse
  // it — dropping it keeps the baked box exactly the ink box), recompute the
  // union box from what survives, and emit only if something is left.
  const flush = () => {
    if (!cur) return;
    const { items, dir } = cur;
    cur = null;                      // reset FIRST: no half-open run can leak
    let s = 0, e = items.length;
    while (s < e && /^\s$/.test(items[s].c)) s++;
    while (e > s && /^\s$/.test(items[e - 1].c)) e--;
    const kept = items.slice(s, e);
    if (!kept.length) return;
    // Union over the box-bearing characters only (combining marks carry no
    // box of their own — see the zero-width branch below).
    const boxed = kept.filter((k) => k.r);
    if (!boxed.length) return;
    const x  = Math.min(...boxed.map((k) => k.r.x));
    const y  = Math.min(...boxed.map((k) => k.r.y));
    const x2 = Math.max(...boxed.map((k) => k.r.x + k.r.w));
    const y2 = Math.max(...boxed.map((k) => k.r.y + k.r.h));
    runs.push({
      text: kept.map((k) => k.c).join(''),
      // Informational: which way Chromium advanced through this level.
      // Nothing in the emitted wire depends on it (a single-level string is
      // ordered by its own strong characters) — the unit pins assert it.
      dir,
      x: r2(x), y: r2(y), width: r2(x2 - x), height: r2(y2 - y),
    });
  };
  // Start a fresh run from one boxed character.
  const open = (c, b) => { cur = { items: [{ c, r: b }], dir: null, x: b.x, y: b.y, w: b.w, h: b.h }; };

  for (const ch of chars) {
    // A tab is a pure ADVANCE, never ink. Its box is the tab stop itself
    // (measured: 96.33px in bidi-tab-001), and re-emitting the `\t` would
    // make every engine recompute that advance against its own tab-stop rule
    // — the divergence the test is about. Break the run and let the NEXT
    // run's absolute left carry the browser's answer.
    if (ch.c === '\t') { flush(); continue; }
    // Positive-width boxes only; a character can never legitimately own two.
    const boxes = (ch.rects ?? []).filter((b) => b.w > 0);
    if (boxes.length > 1) { flush(); continue; }
    const b = boxes[0] ?? null;
    if (!b) {
      // Zero-width: either a non-rendered character (newline, collapsed
      // space, soft break) — which ends the run — or a combining mark / ZWJ
      // that belongs to the preceding glyph and must ride along.
      if (/\s/.test(ch.c) || !cur) { flush(); continue; }
      cur.items.push({ c: ch.c, r: null });
      continue;
    }
    if (!cur) { open(ch.c, b); continue; }
    // Same line? Line identity is the box's top edge and height — a bidi
    // level change never changes either, a line break always does.
    if (Math.abs(b.y - cur.y) > RUN_ADJACENCY_EPS || Math.abs(b.h - cur.h) > RUN_ADJACENCY_EPS) {
      flush(); open(ch.c, b); continue;
    }
    // Adjacency decides the direction: attaching on the right continues an
    // LTR level, attaching on the left continues an RTL one.
    const attachRight = Math.abs(b.x - (cur.x + cur.w)) <= RUN_ADJACENCY_EPS;
    const attachLeft  = Math.abs((b.x + b.w) - cur.x) <= RUN_ADJACENCY_EPS;
    const ok = cur.dir === 'ltr' ? attachRight
             : cur.dir === 'rtl' ? attachLeft
             : (attachRight || attachLeft);
    if (!ok) { flush(); open(ch.c, b); continue; }
    // First continuation fixes the run's direction for the rest of its life.
    if (cur.dir === null) cur.dir = attachRight ? 'ltr' : 'rtl';
    cur.items.push({ c: ch.c, r: b });
    // Grow the union box (x may move LEFT on an RTL run).
    const right = Math.max(cur.x + cur.w, b.x + b.w);
    cur.x = Math.min(cur.x, b.x);
    cur.w = right - cur.x;
    cur.h = Math.max(cur.h, b.h);
  }
  flush();
  return runs;
}

// ── Pure planning: which components get baked ───────────────────────────────

/** `b`'s path lies strictly inside `a`'s subtree. Paths are the kept-child
 *  index chains buildComponents assigns ids from, so a prefix relation IS
 *  the ancestor relation. */
export function isDescendantPath(a, b) {
  if (b.length <= a.length) return false;
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
  return true;
}

/**
 * Choose the BAKE ROOTS from a browser walk. For every element the browser
 * reported as bidi-affected we take the nearest block-level ancestor-or-self
 * (an inline box cannot carry the used width/height its absolute children
 * are measured against), then drop any root already covered by another —
 * baking an ancestor bakes its whole subtree.
 *
 * This is deliberately the MINIMAL enclosure, not the outermost one:
 * bidi-lines-002's intro `<p>` shares a document with an RTL `<div>` and
 * must come out byte-identical.
 *
 * Returns `{ roots, reason }` — `roots` is a list of element records,
 * `reason` a bail string when an affected element has no block-level
 * ancestor-or-self to anchor on.
 */
export function selectBakeRoots(elements) {
  const byKey = new Map(elements.map((e) => [e.path.join('.'), e]));
  const chosen = new Map();
  for (const el of elements) {
    if (!el.bidiAffected) continue;
    // Climb until a block-level box is found (self first).
    let cand = el;
    while (cand && !BLOCKISH_DISPLAYS.includes(cand.display)) {
      cand = cand.path.length > 1 ? byKey.get(cand.path.slice(0, -1).join('.')) : null;
    }
    if (!cand) {
      // An affected inline at the top level has nothing to anchor on — the
      // canvas root is not ours to reposition. That only MATTERS when the
      // element actually carries text the bake would have moved (measured:
      // css-text/bidi/empty-span-001's top-level `<span>`); an anchorless
      // affected element with no text of its own is simply passed over.
      if (!el.texts?.length) continue;
      return { roots: [], reason: `no block-level bake root for <${el.tag}> at ${el.path.join('.')}` };
    }
    chosen.set(cand.path.join('.'), cand);
  }
  // Drop nested roots: an ancestor root's bake already covers them.
  const all = [...chosen.values()];
  const roots = all.filter((r) => !all.some((o) => isDescendantPath(o.path, r.path)));
  return { roots, reason: null };
}

// ── Pure planning: geometry → property maps ─────────────────────────────────

/** The padding-box origin an element's absolutely positioned children are
 *  measured from: its border-box rect shifted in by its own border widths
 *  (CSS Position §"containing block" — an abspos child resolves against the
 *  PADDING box of its positioned ancestor). */
export function paddingBoxOrigin(el) {
  return { x: el.rect.x + el.borderLeft, y: el.rect.y + el.borderTop };
}

/** Property map for a baked BOX (a kept element inside a root): the used
 *  border-box rect, absolutely positioned against `origin`. `box-sizing:
 *  border-box` makes the width/height mean the same thing the rect measured,
 *  whatever padding/border the element's own static declarations carry. */
export function boxProperties(rect, origin) {
  return {
    position: 'absolute',
    left:   px(rect.x - origin.x),
    top:    px(rect.y - origin.y),
    width:  px(rect.width),
    height: px(rect.height),
    'box-sizing': 'border-box',
  };
}

/** Property map for a baked ROOT: the used border-box size, plus the
 *  containing block its children need. `position: relative` is added ONLY
 *  when the element is statically positioned — with no insets it moves
 *  nothing, it just establishes the containing block. An already-positioned
 *  root keeps its own scheme. */
export function rootProperties(rect, position) {
  const props = {
    width:  px(rect.width),
    height: px(rect.height),
    'box-sizing': 'border-box',
  };
  if (position === 'static') props.position = 'relative';
  return props;
}

/**
 * Property map for a baked RUN. Everything that decides the run's rendering
 * is stated explicitly so the box is context-free — it no longer inherits a
 * paragraph direction, an alignment or a font from boxes the bake dissolved:
 *
 *  - `position/left/top/width/height` — Chromium's own answer for where this
 *    level run landed, which is the whole point of the bake;
 *  - `line-height` pinned to the measured box height so half-leading is zero
 *    and the baseline sits where Chromium put it (a text client rect is the
 *    font's natural ascent+descent box, not the line box: measured 39px for
 *    a 40px line-height at 32px Inter, 24px for a 20px line-height at 20px
 *    monospace);
 *  - `white-space: pre` so internal runs of spaces survive intact and the
 *    run can never wrap inside its own box;
 *  - `direction` set to THE RUN'S OWN measured level direction, plus
 *    `text-align: left`, to replace (not merely neutralise) the inherited
 *    paragraph direction. The pre-fix version hard-coded `direction: ltr` on
 *    the theory that a single-level string is ordered by its own strong
 *    characters alone. That is true only for a run made ENTIRELY of strong
 *    characters. UAX#9 rules N1/N2 resolve a run's NEUTRALS (punctuation,
 *    brackets, interior spaces) against the surrounding levels and, at a run
 *    boundary, against the PARAGRAPH level — so a level-1 run whose logical
 *    string is `!سلام` renders `مالس!` inside its RTL paragraph but `!مالس`
 *    under `direction: ltr`: the exclamation mark jumps to the opposite end
 *    of the box. Measured in Chromium on `<div dir=rtl>!سلام</div>` (and the
 *    trailing-neutral and `سلام (12)` variants) — every one flipped, and
 *    `runDirectionConflict` cannot catch them because a neutral is not a
 *    strong-L letter. Emitting the run's own direction restores Chromium's
 *    order in all of them and is a byte-visible but PIXEL-IDENTICAL no-op for
 *    a run that is purely strong-L or purely strong-R (also measured). A
 *    direction-less run (`dir === null` — a single glyph that never continued,
 *    so there is no order to get wrong) keeps `ltr`. `text-align: left` stays
 *    PHYSICAL and the box is exactly the run's ink width, so the alignment is
 *    still a no-op in both directions;
 *  - the font/colour/spacing quintet, taken from the text node's own parent
 *    so a merged `<b>`/`<span>` keeps its rendering even though the static
 *    extractor folded it into its parent's text.
 */
/**
 * Does this run need its RTL paragraph direction stated explicitly?
 *
 * Only an RTL level run that carries at least one NON-RTL character does: a
 * run of pure strong-R codepoints resolves to level 1 under either paragraph
 * direction, so `ltr` is already correct for it (and stays byte-identical to
 * every fixture baked before this guard existed). The moment a neutral or
 * weak character rides along — punctuation, a bracket, an interior space,
 * a digit — UAX#9 N1/N2 resolve it against the surrounding levels and, at
 * the run's boundary, against the PARAGRAPH level. Under a hard-coded `ltr`
 * that boundary neutral flips to the opposite end of the box.
 *
 * Exported so the unit pins can assert the narrow trigger directly.
 */
export function needsExplicitRtl(run) {
  if (run.dir !== 'rtl') return false;
  // A single non-RTL codepoint is enough — it is the one N1/N2 would move.
  for (const ch of String(run.text ?? '')) if (!hasRtlCodepoint(ch)) return true;
  return false;
}

export function runProperties(run, style, origin) {
  const props = {
    position: 'absolute',
    left:   px(run.x - origin.x),
    top:    px(run.y - origin.y),
    width:  px(run.width),
    height: px(run.height),
    'line-height': px(run.height),
    'white-space': 'pre',
    // The run's OWN level direction, but only where it can CHANGE anything —
    // see the banner. A run made entirely of strong-R characters orders
    // identically under either paragraph direction (measured), and emitting
    // `rtl` for it would move its ink by a sub-pixel (the RTL line box starts
    // at the box's right edge, and the box is the rounded INK width, not the
    // advance width — measured ~0.02px, enough to shift antialiasing on
    // bidi-lines-001's `فارسی` runs). So `rtl` is emitted exactly when the
    // run is an RTL level that ALSO carries a non-RTL character — the
    // neutral/weak case UAX#9 N1/N2 would re-resolve against the paragraph.
    direction: needsExplicitRtl(run) ? 'rtl' : 'ltr',
    'text-align': 'left',
    color: style.color,
    'font-family': style.fontFamily,
    'font-size': style.fontSize,
    'font-style': style.fontStyle,
    'font-weight': style.fontWeight,
  };
  // Only emit the optional metrics when they are NOT at their initial value:
  // a fixture full of `letter-spacing: normal` would be noise, and every
  // extra declaration is another parser surface for no gain.
  if (style.letterSpacing && style.letterSpacing !== 'normal') props['letter-spacing'] = style.letterSpacing;
  if (style.wordSpacing && style.wordSpacing !== 'normal' && style.wordSpacing !== '0px') props['word-spacing'] = style.wordSpacing;
  // `text-transform` must ride along: the run string is the RAW node value
  // (untransformed), while the geometry was measured on the TRANSFORMED
  // text, so the engines have to apply the same transform to agree.
  if (style.textTransform && style.textTransform !== 'none') props['text-transform'] = style.textTransform;
  return props;
}

// ── In-page walker ──────────────────────────────────────────────────────────
//
// Runs inside page.evaluate — dependency-free, everything arrives through
// the serialized params object. It replicates post-load-extract's
// inPageWalker filters ELEMENT FOR ELEMENT (head-only tags skipped at every
// depth; pure-inline mergeable children skipped with the wave-12 depth-0
// leading-run nuance; depth capped at 5) so `mappingMismatch` can cross-check
// its output against the SAME static path list the state bake uses. On top of
// that it collects, per kept element:
//   - the layout facts a box bake needs (border-box rect, client-rect COUNT,
//     border widths, computed display/position, text-decoration-line);
//   - the bidi verdict (computed direction / unicode-bidi / own-text RTL);
//   - the OWNED text: every text node whose nearest KEPT ancestor is this
//     element (so a merged `<b>`'s characters are attributed to the component
//     that absorbed them), with per-CODE-POINT client rects and the text
//     node's own font/colour.
function inPageBidiWalker(params) {
  const { headOnly, inlineMerge, styledTags, maxDepth, rtlRanges, authorUnicodeBidi } = params;
  const elements = [];
  let bodyTextIsBidi = false;   // bidi text with no element to anchor it

  // Codepoint test, inlined: the page has no module imports.
  const isRtlText = (s) => {
    for (const ch of s) {
      const cp = ch.codePointAt(0);
      for (const [lo, hi] of rtlRanges) if (cp >= lo && cp <= hi) return true;
    }
    return false;
  };

  // Mergeable ⇔ isPureInlineMergeable's four conditions in DOM terms (see
  // post-load-extract's walker banner for the mapping).
  const isMergeable = (el, tag) => inlineMerge.includes(tag)
    && el.attributes.length === 0
    && el.childElementCount === 0
    && !styledTags.includes(tag);

  // Per-code-point client rects for one text node. Code points, not UTF-16
  // units: splitting a surrogate pair with a Range yields garbage boxes.
  const charBoxes = (node) => {
    const t = node.nodeValue;
    const out = [];
    let i = 0;
    while (i < t.length) {
      const cp = t.codePointAt(i);
      const len = cp > 0xffff ? 2 : 1;
      const range = document.createRange();
      range.setStart(node, i);
      range.setEnd(node, i + len);
      const rects = [];
      for (const rc of range.getClientRects()) {
        rects.push({ x: +rc.left.toFixed(2), y: +rc.top.toFixed(2),
                     w: +rc.width.toFixed(2), h: +rc.height.toFixed(2) });
      }
      out.push({ c: t.slice(i, i + len), rects });
      i += len;
    }
    return out;
  };

  // The text style a run must carry, read off the text node's OWN parent.
  const textStyle = (el) => {
    const cs = getComputedStyle(el);
    return {
      color: cs.color, fontFamily: cs.fontFamily, fontSize: cs.fontSize,
      fontStyle: cs.fontStyle, fontWeight: cs.fontWeight,
      letterSpacing: cs.letterSpacing, wordSpacing: cs.wordSpacing,
      textTransform: cs.textTransform,
    };
  };

  // Collect the text OWNED by `owner` — every text node whose nearest KEPT
  // ancestor is `owner`'s element. `childDepth` is the walk depth the
  // children of `parent` occupy, so the two conditions that make a child its
  // own component (not pure-inline-mergeable AND inside the depth cap) are
  // checked exactly as `walk` checks them; anything else folds its text up.
  // `owner === null` means body level, which the static extractor folds into
  // a synthetic `__text` component with no traversal path — recorded, never
  // baked (see the bodyTextIsBidi bail).
  const collectText = (parent, owner, childDepth) => {
    for (const node of parent.childNodes) {
      if (node.nodeType === 3) {                        // Node.TEXT_NODE
        if (!node.nodeValue.trim()) continue;           // whitespace-only: no ink
        if (!owner) { if (isRtlText(node.nodeValue)) bodyTextIsBidi = true; continue; }
        owner.texts.push({ style: textStyle(parent), chars: charBoxes(node) });
        continue;
      }
      if (node.nodeType !== 1) continue;                // comments / PIs
      const tag = node.tagName.toLowerCase();
      if (headOnly.includes(tag)) continue;             // never a component, never text
      // Own component ⇒ it owns its own text; stop here.
      if (!isMergeable(node, tag) && childDepth < maxDepth) continue;
      collectText(node, owner, childDepth + 1);
    }
  };

  const walk = (parent, prefix, depth) => {
    let kept = 0;            // index among KEPT children — the id/path basis
    let seenBlock = false;   // wave-12 depth-0 leading-run marker
    for (const el of parent.children) {
      const tag = el.tagName.toLowerCase();
      if (headOnly.includes(tag)) continue;                       // filter 1
      const mergeable = isMergeable(el, tag);                     // filter 2
      if (mergeable) {
        if (depth !== 0 || !seenBlock) continue;
      } else {
        seenBlock = true;
      }
      const path = prefix.concat([kept]);
      kept += 1;
      const cs = getComputedStyle(el);
      const r = el.getBoundingClientRect();
      const rec = {
        path, tag,
        rect: { x: +r.left.toFixed(2), y: +r.top.toFixed(2),
                width: +r.width.toFixed(2), height: +r.height.toFixed(2) },
        // Fragmentation probe: an inline split across line boxes reports >1.
        rectCount: el.getClientRects().length,
        borderLeft: parseFloat(cs.borderLeftWidth) || 0,
        borderTop:  parseFloat(cs.borderTopWidth)  || 0,
        display: cs.display,
        position: cs.position,
        // Decorations propagate from a box to its inline descendants; an
        // absolutely positioned run would NOT receive them, so their presence
        // bails rather than silently dropping an underline.
        decoration: cs.textDecorationLine,
        texts: [],
        // The browser's own bidi verdict. Three author-level signals:
        //   - a `dir` attribute of ANY value (including `dir=ltr`) — an
        //     explicit bidi declaration, and the one that keeps
        //     bidi-tab-001's four dir=ltr/dir=ltr boxes baked in step with
        //     their dir=rtl twins (the test's whole assertion is that the
        //     two groups render identically);
        //   - computed `direction: rtl`;
        //   - an author-level computed `unicode-bidi` (NOT the UA-default
        //     `isolate` every block container carries — see
        //     AUTHOR_UNICODE_BIDI_VALUES).
        // The fourth signal, RTL codepoints in the element's OWN text, is
        // folded in after collectText below.
        bidiAffected: el.hasAttribute('dir')
          || cs.direction === 'rtl'
          || authorUnicodeBidi.includes(cs.unicodeBidi),
      };
      elements.push(rec);
      // Own text first (it feeds the codepoint half of the bidi verdict),
      // then the kept descendants. `el`'s children sit at depth + 1.
      collectText(el, rec, depth + 1);
      if (!rec.bidiAffected) {
        rec.bidiAffected = rec.texts.some((t) => isRtlText(t.chars.map((c) => c.c).join('')));
      }
      if (depth + 1 < maxDepth) walk(el, path, depth + 1);        // filter 3
    }
  };
  // Body-level text has no element component to anchor absolute children to
  // (the static extractor folds it into a synthetic `__text` component with
  // no traversal path) — record it so the caller can bail if it is bidi.
  for (const node of document.body.childNodes) {
    if (node.nodeType === 3 && node.nodeValue.trim() && isRtlText(node.nodeValue)) bodyTextIsBidi = true;
  }
  walk(document.body, [], 0);
  return { elements, bodyTextIsBidi };
}

// ── Pure planning: walk → bake plan ─────────────────────────────────────────

/**
 * Turn a browser walk into the list of edits the fixture needs, or a bail.
 * Everything here is pure so the unit pins can drive it from a recorded walk
 * without a browser.
 *
 * Returns `{ plan }` or `{ bail }`, where `plan` is
 * `{ roots: [{ path, props }], boxes: [{ path, props }], hides: [path],
 *    runs: [{ ownerPath, props, text }] }`.
 */
export function planBidiBake(walk) {
  // Body-level bidi text: nothing to anchor absolute runs to (see walker).
  if (walk.bodyTextIsBidi) return { bail: 'bidi text directly under <body> (no anchor component)' };
  const { roots: candidates, reason } = selectBakeRoots(walk.elements);
  if (reason) return { bail: reason };
  if (!candidates.length) return { bail: null, plan: null, note: 'no bidi-affected element' };

  // Every element's own padding-box origin. Independent of the plan (it is a
  // function of the used rect and border widths alone), so it can be computed
  // once, up front, for the root-pruning pass below.
  const originOf = new Map(walk.elements.map((e) => [e.path.join('.'), paddingBoxOrigin(e)]));
  const inScopeOf = (rootPaths) => walk.elements.filter((e) =>
    rootPaths.some((rp) => rp.join('.') === e.path.join('.') || isDescendantPath(rp, e.path)));

  // Group runs by owning element ONCE — the grouping is the expensive part
  // and the root-pruning pass needs its result.
  const runsByPath = new Map();
  for (const e of inScopeOf(candidates.map((r) => r.path))) {
    const grouped = [];
    for (const t of e.texts) for (const run of groupCharRuns(t.chars)) grouped.push({ run, style: t.style });
    if (grouped.length) runsByPath.set(e.path.join('.'), grouped);
  }

  // PRUNE roots with no text runs anywhere in their subtree. This is the
  // scope guard that keeps the bake honest: `direction: rtl` on a text-free
  // layout test (measured — css-flexbox/abspos/abspos-autopos-*-rtl and
  // css-grid/abspos/descendant-static-position-00{2,4}, all currently
  // PERFECT sections) is a LAYOUT question the engines already answer, not a
  // UAX#9 reordering the IR cannot carry. Freezing those boxes into absolute
  // positions would replace a real test with a coordinate replay for no gain.
  const roots = candidates.filter((r) =>
    [...runsByPath.keys()].some((k) => {
      const p = k.split('.').map(Number);
      return r.path.join('.') === k || isDescendantPath(r.path, p);
    }));
  if (!roots.length) return { bail: null, plan: null, note: 'no bidi text runs to bake' };

  const rootKeys = roots.map((r) => r.path);
  const isRootPath = (p) => rootKeys.some((rp) => rp.join('.') === p.join('.'));
  const inScope = inScopeOf(rootKeys);

  // Structural bails, checked over the FINAL scope and BEFORE any edit is
  // planned, so a bail leaves the fixture byte-identical.
  for (const e of inScope) {
    if (e.decoration && e.decoration !== 'none') {
      // A decoration set on a box propagates to the inline boxes inside it;
      // an absolutely positioned run is NOT one of them, so the underline
      // would silently vanish. Bail instead.
      return { bail: `text-decoration-line: ${e.decoration} on <${e.tag}> at ${e.path.join('.')}` };
    }
    // Roots must be a single unfragmented box — they carry the used size.
    if (isRootPath(e.path) && e.rectCount !== 1) {
      return { bail: `bake root <${e.tag}> at ${e.path.join('.')} has ${e.rectCount} client rects` };
    }
    if (!isRootPath(e.path) && e.rectCount > 1) {
      return { bail: `fragmented inline <${e.tag}> at ${e.path.join('.')} (${e.rectCount} client rects)` };
    }
  }

  const plan = { roots: [], boxes: [], hides: [], runs: [] };
  const hidden = new Set();
  for (const e of inScope) {
    if (isRootPath(e.path)) {
      plan.roots.push({ path: e.path, props: rootProperties(e.rect, e.position) });
    } else if (e.rectCount === 0 || e.tag === 'br') {
      // Nothing to bake:
      //   - rectCount 0 — the browser painted no box at all (display:none);
      //   - <br> — its entire job is to break a line, and every line in this
      //     subtree is now an absolutely positioned run whose top/left came
      //     from Chromium. A surviving `_role: line-break` component would
      //     inject flow the baked layout no longer expects.
      // Both are removed explicitly rather than left half-alive.
      plan.hides.push(e.path);
      hidden.add(e.path.join('.'));
      continue;
    } else {
      const parent = originOf.get(e.path.slice(0, -1).join('.'));
      // Parent must be in scope by construction (a root encloses the chain).
      if (!parent) return { bail: `missing containing block for <${e.tag}> at ${e.path.join('.')}` };
      plan.boxes.push({ path: e.path, props: boxProperties(e.rect, parent) });
    }
  }

  // Runs, in element order then logical order — the fixture's children map is
  // insertion-ordered, and later siblings paint on top of earlier ones.
  for (const e of inScope) {
    const key = e.path.join('.');
    if (hidden.has(key)) continue;               // hidden element hosts nothing
    for (const { run, style } of runsByPath.get(key) ?? []) {
      // The MEASURED direction is passed alongside the string: without it the
      // `unicode-bidi: bidi-override` case (homogeneous text laid out against
      // its own strong class) has only one strong class and slips the mixed
      // test — see runDirectionConflict's skeptic note.
      if (runDirectionConflict(run.text, run.dir)) {
        return { bail: `direction-ambiguous run ${JSON.stringify(run.text)} at ${key}` };
      }
      plan.runs.push({ ownerPath: e.path, props: runProperties(run, style, originOf.get(key)), text: run.text });
    }
  }
  if (plan.runs.length > MAX_BIDI_RUNS) {
    return { bail: `run budget exceeded (${plan.runs.length} > ${MAX_BIDI_RUNS})` };
  }
  return { bail: null, plan };
}

// ── Pure merge: bake plan → fixture ─────────────────────────────────────────

/** Append a synthetic child component under `parent`, using the flat
 *  children-map shape buildComponents emits (`{ "<parentId>__<i>": {…} }`,
 *  with the id repeated inside the object exactly as the static path does).
 *  The index continues past whatever children already exist so the static
 *  siblings keep their ids — and so the run paints ON TOP of them, which is
 *  what a text-over-background stack needs. */
export function appendChildComponent(parent, parentId, props, text) {
  parent.children ??= {};
  const id = `${parentId}__${Object.keys(parent.children).length}`;
  parent.children[id] = { id, properties: props, _text: text };
  return id;
}

/** Does this fixture carry any `_pseudo` content? ::before/::after generate
 *  BOXES with their own geometry, anchored to a box the bake is about to
 *  dissolve into absolute children — a run cannot carry them and dropping
 *  them silently would be exactly the fallthrough this repo forbids. A
 *  whole-fixture scan on purpose: conservative, and cheap enough that no
 *  scoping subtlety can defeat it. Exported for the unit pin. */
export function fixtureHasPseudo(fixture) {
  return JSON.stringify(fixture?.components ?? {}).includes('"_pseudo"');
}

/** Rebuild a component id from a traversal path — the same reconstruction
 *  componentAtPath performs, needed here because appended children key off
 *  their parent's id string. */
export function componentIdForPath(stem, path) {
  return [stem, ...path].join('__');
}

/**
 * Apply a bake plan to a fixture in place. Returns the number of components
 * touched. The caller has already validated the plan; every lookup failure
 * here is a hard mapping error (the paths came from a walk that passed
 * `mappingMismatch`), so it throws rather than half-baking.
 */
export function applyBidiBakePlan(fixture, stem, plan) {
  let touched = 0;
  // 1. Roots: used size + containing block, text dissolved into runs, LOUD
  //    lossy marker. Property assignment REPLACES the static declaration, so
  //    a lossy `width: 10em` becomes the used px and stops being lossy.
  for (const { path, props } of plan.roots) {
    const cmp = componentAtPath(fixture, stem, path);
    if (!cmp) throw new Error(`bidi-bake: no component at ${path.join('.')}`);
    Object.assign(cmp.properties ??= {}, props);
    delete cmp._text;
    cmp._lossy = true;
    cmp._lossyReasons = [...new Set([...(cmp._lossyReasons ?? []), BIDI_BAKE_LOSSY_REASON])];
    touched++;
  }
  // 2. Boxes: absolutely positioned used rects; their text moves to runs.
  for (const { path, props } of plan.boxes) {
    const cmp = componentAtPath(fixture, stem, path);
    if (!cmp) throw new Error(`bidi-bake: no component at ${path.join('.')}`);
    Object.assign(cmp.properties ??= {}, props);
    delete cmp._text;
    touched++;
  }
  // 3. Hides: elements the browser painted nothing for (<br>, display:none).
  for (const path of plan.hides) {
    const cmp = componentAtPath(fixture, stem, path);
    if (!cmp) throw new Error(`bidi-bake: no component at ${path.join('.')}`);
    (cmp.properties ??= {}).display = 'none';
    delete cmp._text;
    touched++;
  }
  // 4. Runs: new positioned text components under their owning element.
  for (const { ownerPath, props, text } of plan.runs) {
    const owner = componentAtPath(fixture, stem, ownerPath);
    if (!owner) throw new Error(`bidi-bake: no component at ${ownerPath.join('.')}`);
    appendChildComponent(owner, componentIdForPath(stem, ownerPath), props, text);
    touched++;
  }
  // 5. Honesty stamps: the delivery record plus the fixture-level roll-up.
  fixture._wpt ??= {};
  fixture._wpt.bidiBaked = true;
  if ('lossy' in fixture._wpt) {
    fixture._wpt.lossy = true;
    fixture._wpt.lossyReasons =
      [...new Set([...(fixture._wpt.lossyReasons ?? []), BIDI_BAKE_LOSSY_REASON])];
  }
  return touched;
}

// ── Browser plumbing ────────────────────────────────────────────────────────

// One shared browser per process (a launch per test would dominate wall
// time). Lazily created; callers MUST closeBidiBakeBrowser() before exit.
let _browser = null;
async function getBrowser() {
  if (_browser) return _browser;
  _browser = await puppeteer.launch({
    headless: 'new',                 // same mode as browser-ref / post-load
    args: BROWSER_LAUNCH_ARGS,       // the shared flag contract
    protocolTimeout: 300_000,        // long-run safety margin
  });
  return _browser;
}

/** Close the shared browser (no-op when never launched). */
export async function closeBidiBakeBrowser() {
  if (_browser) { await _browser.close(); _browser = null; }
}

/**
 * Run the bidi bake for one test and, on success, bake the result into
 * `fixture` in place. Returns `{ status, reason?, runs?, roots? }` with
 * status ∈ 'baked' | 'skipped' | 'declined' | 'bailed':
 *   - 'skipped'  — the static trigger did not fire, or the browser found no
 *                  bidi-affected element; no edit, no stamp;
 *   - 'declined' — the shared top-layer pre-check says the rendered state is
 *                  unrepresentable; no browser was launched;
 *   - 'bailed'   — a cross-check failed (mapping drift, fragmented inline,
 *                  pseudo geometry, decoration, ambiguous run, run budget);
 *                  the fixture is left byte-identical;
 *   - 'baked'    — visual geometry delivered, fixture stamped.
 */
export async function bidiBakeFixture(fixture, testRel) {
  const testAbs = join(WPT_DIR, testRel);
  const html = await fs.readFile(testAbs, 'utf8');
  // Cheap static gates first — neither needs a browser.
  const trigger = bidiBakeTrigger(html);
  if (!trigger) return { status: 'skipped', reason: 'no bidi trigger' };
  const decline = postLoadDecline(html);
  if (decline) return { status: 'declined', reason: decline };
  // `_pseudo` content generates boxes with their own geometry that no run
  // can carry; checked on the FIXTURE (the static extractor is the only
  // thing that knows about pseudo elements) before anything is edited.
  if (fixtureHasPseudo(fixture)) {
    return { status: 'bailed', reason: 'pseudo-element geometry present' };
  }
  // The static traversal identity, for the mapping cross-check.
  const { paths: staticPaths } = await staticPathsForHtml(html, testAbs);

  const browser = await getBrowser();
  const page = await browser.newPage();
  try {
    // Viewport BEFORE goto so the page lays out at the pipeline's 390-wide
    // canvas, not puppeteer's 800×600 default.
    await page.setViewport({ width: 390, height: 600, deviceScaleFactor: 1 });
    await page.goto('file://' + encodeURI(testAbs), { waitUntil: 'load', timeout: 30_000 });
    // The identical zero-specificity canvas frame the ref capture and the
    // post-load bake inject — same constants, same id, so the geometry read
    // below is measured in the environment the ref PNGs were rendered in.
    await page.evaluate(({ id, css }) => {
      const s = document.createElement('style');
      s.id = id;
      s.textContent = css;
      document.head.appendChild(s);
    }, {
      id: CANVAS_FRAME_STYLE_ID,
      css: `
        ${await interFontFaceCss()}
        :where(html, body) { margin: 0; padding: 0; background: ${CANVAS_BG}; }
        :where(body) { padding: ${CANVAS_PAD_PX}px; box-sizing: border-box;
                       min-height: 100vh; color: #000;
                       font-family: ${REF_FONT_STACK};
                       line-height: ${REF_LINE_HEIGHT}; }
      `,
    });
    // fonts.ready + double-rAF: the data-URI Inter faces load async, and
    // glyph boxes read before the relayout with the loaded face would encode
    // fallback-font advances — a systematic error in every baked left/width.
    await page.evaluate(() => document.fonts.ready.then(
      () => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))),
    ));
    // The walk. styledTags comes from the SAME rule set the static tree was
    // built from so both sides of the mapping share one merge guard.
    const walk = await page.evaluate(inPageBidiWalker, {
      headOnly:    [...HEAD_ONLY_TAGS],
      inlineMerge: [...INLINE_MERGE_TAGS],
      styledTags:  [...collectStyledTags(parseCss(extractInlineStyle(stripComments(html))))],
      maxDepth: 5,
      rtlRanges: RTL_CODEPOINT_RANGES,
      authorUnicodeBidi: AUTHOR_UNICODE_BIDI_VALUES,
    });
    // Element-mapping cross-check: the browser walk must agree with the
    // static walk path-for-path and tag-for-tag, or every baked rect would
    // land on the wrong component.
    const mismatch = mappingMismatch(staticPaths, walk.elements);
    if (mismatch) return { status: 'bailed', reason: `element-mapping-mismatch (${mismatch})` };
    const { bail, plan, note } = planBidiBake(walk);
    if (bail) return { status: 'bailed', reason: bail };
    // A triggered test that turns out to have nothing to bake is a SKIP, not
    // a bail — the trigger is a source-level over-approximation on purpose
    // (see extract-fixture.mjs's bidiBakeTrigger banner) and the browser is
    // the authority that narrows it.
    if (!plan) return { status: 'skipped', reason: `triggered (${trigger}) but ${note}` };
    // Only now — after every cross-check passed — is the fixture touched.
    const stem = fixtureStem(testRel);
    const touched = applyBidiBakePlan(fixture, stem, plan);
    return { status: 'baked', trigger, roots: plan.roots.length, runs: plan.runs.length, touched };
  } finally {
    await page.close(); // one page per test; the browser is shared
  }
}

// ── CLI ─────────────────────────────────────────────────────────────────────
//
// End-to-end per test: static extraction → bidi bake → writeFixturePair.
// Skips/declines/bails still WRITE the static pair (that IS the bail-to-
// static contract) and are reported per test.
async function main() {
  const inputs = process.argv.slice(2).filter((a) => !a.startsWith('--'));
  if (inputs.length === 0) {
    console.error('usage: bidi-bake.mjs <relative-test-path>...');
    console.error('       (paths are repo-relative, e.g. "css/css-text/bidi/bidi-lines-001.html")');
    process.exit(1);
  }
  let hardFail = 0;
  const tally = { baked: 0, skipped: 0, declined: 0, bailed: 0 };
  try {
    for (const rel of inputs) {
      try {
        const result = await extractFixture(rel);          // static pass
        const outcome = await bidiBakeFixture(result.fixture, rel);
        await writeFixturePair(result);                    // write either way
        tally[outcome.status]++;
        console.log(`${outcome.status.padEnd(8)} ${rel}` +
          (outcome.status === 'baked'
            ? ` (${outcome.roots} roots, ${outcome.runs} runs — ${outcome.trigger})`
            : ` (${outcome.reason})`));
      } catch (err) {
        hardFail++;
        console.error(`ERROR    ${rel}: ${err.message ?? err}`);
      }
    }
  } finally {
    await closeBidiBakeBrowser(); // never leak the shared browser
  }
  console.log(`bidi-bake: baked=${tally.baked} skipped=${tally.skipped} ` +
              `declined=${tally.declined} bailed=${tally.bailed} errors=${hardFail}`);
  process.exit(hardFail > 0 ? 1 : 0);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((err) => {
    console.error('bidi-bake: fatal:', err);
    process.exit(1);
  });
}
