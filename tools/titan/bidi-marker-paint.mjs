//
// tools/titan/bidi-marker-paint.mjs — the PAINT-EFFECT decline of the bidi
// marker bake: wave-54 S1 should-fix S6 (= the L1 skeptic's should-fix 1),
// closed in lane L1's S1 fix pass (tools/titan/results/wave54-rtl-marker-
// bake/_note.md, "S1 fix pass").
//
// THE SILENT FALLTHROUGH IT CLOSES. bidi-marker-bake.mjs bakes an RTL list
// item's ::marker as runs OWNED BY THE ENCLOSING BAKE ROOT (wave 54: under the
// abspos `<li>` the Compose Column loop mis-stacked them). But a marker is the
// ITEM's: css-lists-3 §3.1 makes `::marker` the list item's tree-abiding
// pseudo-element, so its box is a child of the item's principal box, and it
// inherits from the item (css-pseudo-4 §4). Whatever the item — and every box
// between it and the root — does to its content therefore also happened to
// the marker, and a root-owned run escapes all of it:
//   - GROUP effects on a box and everything inside it: `opacity`
//     (css-color-4 #transparency), `transform` (css-transforms-1
//     #transform-property), `filter` (filter-effects-1 #FilterProperty),
//     `clip-path` (css-masking-1 #the-clip-path);
//   - the CLIP of a non-`visible` `overflow` (css-overflow-3 §3: content is
//     clipped at the padding box, and an `outside` marker hangs past it);
//   - INHERITED paint state the run does not restate (runProperties carries
//     colour + font only, bidi-bake.mjs): `visibility` (css-display-3
//     #visibility) and `text-shadow` (css-text-decor-3 #text-shadow-property)
//     would come from the ROOT instead of the item.
// So a non-initial value anywhere on that chain DECLINES the item's marker
// bake: the item stays as it was (its own `list-style-type`, no marker runs),
// stamped `marker-not-baked` (MARKER_STAMPS.notBaked, at planBidiBake's call
// site) — a marker the runtimes may still mis-place, SAID SO on the wire,
// rather than one painted opaque / unmoved / unclipped / visible where the
// reference dimmed, moved, clipped or hid it. The ROOT's own effects (and its
// ancestors') still reach root-owned runs, so they are NOT read.
// TODO(S6 residue): `mask-image` and `mix-blend-mode` are group effects of the
// same class and are NOT read yet — no Chromium run in this fix pass could
// confirm their computed spellings, and a key Chromium does not expose would
// read as "unmeasured" and decline every marker. Corpus reach: 0 (census in
// the lane note).
//
// THE CHAIN. Chromium's computed values, read in-page right after the marker
// probe (bidi-marker-bake.mjs collectMarkerFacts): the item's `::marker`
// pseudo FIRST (a `::marker { text-shadow }` would be dropped the same way),
// then the item and each DOM ancestor below <body>. The walker's paths are
// kept-children indexes and every element with an element child is kept
// (bidi-bake.mjs isMergeable needs childElementCount 0), so DOM ancestor d of
// an item at path length L is the walk record at prefix length L − d; each
// entry carries its tag and planning checks it against that record. An
// unmeasured, failed, short or misaligned chain DECLINES too — never a silent
// "clean".

// The paint effects read on the chain: CSSStyleDeclaration key → the computed
// spelling of its CSS initial value (CSSOM getComputedStyle, Chromium's
// serialisation). `opacity` is compared numerically in markerPaintLoss.
export const MARKER_PAINT_INITIAL = Object.freeze({
  // css-color-4 #transparency: initial 1 (fully opaque).
  opacity: '1',
  // css-transforms-1 #transform-property: initial none.
  transform: 'none',
  // filter-effects-1 #FilterProperty: initial none.
  filter: 'none',
  // css-masking-1 #the-clip-path: initial none.
  clipPath: 'none',
  // css-overflow-3 §3: initial visible (no clip), read per physical
  // longhand because the two axes can differ (`overflow: hidden visible`).
  overflowX: 'visible',
  // The vertical twin: `hidden` / `clip` / `auto` / `scroll` on either clips.
  overflowY: 'visible',
  // css-display-3 #visibility: inherited, initial visible.
  visibility: 'visible',
  // css-text-decor-3 #text-shadow-property: inherited, initial none.
  textShadow: 'none',
});

// In-page half. page.evaluate serialises the function source, so it is
// dependency-free: the key list arrives as an argument (one definition, the
// map above). Each item is re-found by tag + used border box exactly as
// bidi-marker-bake.mjs inPageMarkerProbe re-finds it (2-dp walker rounding).
function inPageMarkerPaintChain({ items, keys }) {
  // The probe's own rect match (getBoundingClientRect vs the walk's rect):
  // all four of left / top / width / height within the 2-dp rounding.
  const near = (r, q) => Math.abs(r.left - q.x) <= 0.02 && Math.abs(r.top - q.y) <= 0.02
    && Math.abs(r.width - q.width) <= 0.02 && Math.abs(r.height - q.height) <= 0.02;
  // One chain entry: the node's tag + each key's computed value; a key the
  // browser does not expose reads null, which planning treats as unmeasured.
  const read = (cs, tag) => ({ tag, ...Object.fromEntries(keys.map((k) => [k, cs[k] ?? null])) });
  // `{ "<path>": chain | { error } }` — keyed like collectMarkerFacts' facts.
  const out = {};
  // One chain per measured list item.
  for (const it of items) {
    // The first element of that tag at that rect — the probe's choice.
    const el = [...document.getElementsByTagName(it.tag)].find((n) => near(n.getBoundingClientRect(), it.rect));
    // Not re-found: an error chain, which planning declines (never skipped).
    if (!el) { out[it.key] = { error: 'list item not re-found by rect' }; continue; }
    // The ::marker pseudo first (CSSOM getComputedStyle's pseudoElt argument)…
    const chain = [read(getComputedStyle(el, '::marker'), '::marker')];
    // …then the item and every ancestor up to, never including, <body>.
    for (let n = el; n && n !== document.body; n = n.parentElement) chain.push(read(getComputedStyle(n), n.tagName.toLowerCase()));
    // Entry 0 the pseudo; entry d ≥ 1 the item's (d − 1)-th ancestor (1 = the item).
    out[it.key] = chain;
  }
  // Plain JSON: page.evaluate structured-clones it back to node.
  return out;
}

/** Browser side — collectMarkerFacts calls it on its merged facts: attach
 *  `paintChain` (an entry array, or `{ error }`) to every MEASURED fact; an
 *  error fact is declined already and is left byte-identical. NEVER throws (a
 *  throw there costs the whole fixture, VF): a fault becomes `{ error }`. */
export async function readMarkerPaintChains(page, facts, items) {
  // Only the facts the probe measured, re-found by the same tag + rect.
  const todo = items.filter((it) => facts?.[it.key] && !facts[it.key].error).map(({ key, tag, rect }) => ({ key, tag, rect }));
  // A list-free or all-declined walk costs no page round-trip.
  if (!todo.length) return facts;
  // The in-page answer, or the fault stand-in below.
  let chains;
  // A page fault must not escape (see the doc comment).
  try {
    // One evaluate for every item; the key list travels with it.
    chains = await page.evaluate(inPageMarkerPaintChain, { items: todo, keys: Object.keys(MARKER_PAINT_INITIAL) });
  } catch (err) {   // puppeteer rejects on a detached frame / closed page / thrown script
    // A page fault: every chain unmeasured — markerPaintLoss declines each.
    chains = Object.fromEntries(todo.map((it) => [it.key, { error: String(err?.message ?? err) }]));
  }
  // An item the in-page half did not answer is unmeasured too.
  for (const it of todo) facts[it.key].paintChain = chains?.[it.key] ?? { error: 'no paint chain returned' };
  // The same object, mutated in place: collectMarkerFacts returns it as is.
  return facts;
}

/** Pure planning: the FIRST paint effect on the item's marker chain below its
 *  bake root, as a reason string, or null when the marker may move to the
 *  root. `f` the item's marker facts (f.paintChain), `elements` the walk
 *  records, `itemPath` / `rootPath` the item and its enclosing bake root (a
 *  strict path prefix — roots never nest, selectBakeRoots). */
export function markerPaintLoss(f, elements, itemPath, rootPath) {
  // readMarkerPaintChains' answer for this item (absent when never read).
  const chain = f?.paintChain;
  // Unmeasured (never read, a page fault, an item not re-found): decline.
  if (!Array.isArray(chain)) return `paint chain unmeasured${chain?.error ? ` (${chain.error})` : ''}`;
  // The walk's tag per path, to line the DOM chain up with the records.
  const tagAt = new Map(elements.map((e) => [e.path.join('.'), e.tag]));
  // Expected tags: the pseudo, then the item and each box strictly between
  // it and the root (prefix lengths L … R + 1) — never the root itself.
  const want = ['::marker', ...itemPath.slice(rootPath.length).map((_, d) => tagAt.get(itemPath.slice(0, itemPath.length - d).join('.')))];
  // Walk the expected entries in chain order (the first loss is reported).
  for (let d = 0; d < want.length; d++) {
    // Chromium's entry d (undefined past a short chain's end).
    const e = chain[d];
    // A short or misaligned chain cannot be read against the walk: decline.
    if (!e || e.tag !== want[d]) return `paint chain misaligned at ${d}: <${e?.tag ?? 'none'}> for <${want[d]}>`;
    // Every read key against its initial computed value.
    for (const [k, init] of Object.entries(MARKER_PAINT_INITIAL)) {
      // opacity numerically (NaN — unmeasured — declines); the rest by spelling.
      const lost = k === 'opacity' ? !(Number.parseFloat(e[k]) >= 1) : e[k] !== init;
      // The first loss names the property, its value and the box carrying it.
      if (lost) return `${k} ${e[k]} on <${want[d]}>`;
    }
  }
  // Every box the marker leaves behind paints it exactly as the root will.
  return null;
}
