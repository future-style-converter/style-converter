//
// tools/titan/bidi-marker-bake.mjs — the ::marker of a list item INSIDE a bidi-bake
// root, baked the way bidi-bake.mjs bakes text: wave-53 hunk M (b0edb788^) restored
// as wave-54 lane L1 unit M′ (tools/titan/results/wave54-plan/rtl-marker-bake.md).
//
// THE SILENT FALLTHROUGH IT CLOSES: the bidi bake retires a root's `direction`, but
// its walker collects TEXT NODES only and a `::marker` is no DOM node, so the one
// fact still depending on that direction — the marker's SIDE — was dropped.
// css-counter-styles/counter-suffix: web hangs the `dir=rtl` markers LEFT (x46-58),
// iOS/Android paint none, the ref hangs `.1 .2 .א .ב` RIGHT (x133-145) — css-lists-3:
// an `outside` marker sits on the item's INLINE-START side, UA `::marker { unicode-
// bidi: isolate; font-variant-numeric: tabular-nums; white-space: pre }` (App. A).
//
// Per kept list-item BOX (never a root: every runtime paints a root's marker today)
// Chromium gives the STRING + BOX (CDP DOMSnapshot: the marker pseudo node's layout
// box and LayoutText) and the GLYPHS (a hidden probe span in the computed ::marker
// style), the probe advance self-checked against the box at MARKER_PROBE_EPS. Out
// come positioned runs + `list-style-type: none` on the item (no `meta.markerText`).
// WAVE 54: the runs are OWNED BY THE ENCLOSING (relative) BAKE ROOT, measured from
// its padding box — under the abspos `<li>` the Compose Column loop gave run 2 +20 px
// and run 3 no slot (brief §3); bidi-bake.mjs planBidiBake makes that choice. A no-
// strong-letter run ("1.") is split per grapheme: one rtl `.1` run would rest on bidi.
//
// MODELS of Chromium, not its answer, each stamped on the item and MARKER-scoped
// (never a whole-test bail): no CDP box → the analytic inline-start content edge
// (`marker-box-modelled`; a box missing the advance by > EPS takes it too,
// `marker-probe-mismatch`); no CDP string → the counter-style bake's on a CLONE,
// ". " suffix space restored (`marker-text-modelled`). An image marker, no first-
// line run, a marker font unlike the first line's or an item not re-found by its
// rect leaves it as it was, stamped `marker-not-baked`.

// The bidi bake's own run machinery — one definition of a run, never a copy.
import { groupCharRuns, runProperties, hasStrongLtrCodepoint, hasStrongRtlLetter, RUN_ADJACENCY_EPS } from './bidi-bake.mjs';
// The string fallback (the counter-style bake, on a clone) and the shared path ⇄ component map.
import { bakeCounterStyles } from './counter-style-bake.mjs';
import { componentAtPath } from './post-load-extract.mjs';

// The probe self-check tolerance (CSS px, probe advance vs Chromium's box width) and
// the `_lossyReasons` stamps this module may put on a list-item box.
export const MARKER_PROBE_EPS = 0.5;
export const MARKER_STAMPS = { mismatch: 'marker-probe-mismatch', boxModel: 'marker-box-modelled', textModel: 'marker-text-modelled', notBaked: 'marker-not-baked' };

// A computed `display` that generates a ::marker (css-display-3 list-item).
const isListItem = (e) => String(e?.display ?? '').split(/\s+/).includes('list-item');

/** CDP DOMSnapshot.captureSnapshot → `[{hostTag, hostRect, box, text}]` per `::marker` node: the
 *  parent element's layout box (to match the walk), its own box, its LayoutText's text. Pure. */
export function parseSnapshotMarkers(snap) {
  const S = snap?.strings ?? [], d = snap?.documents?.[0], out = [], lay = new Map();
  if (!d) return out;
  // String-table lookup (-1 = absent reads as ''); [x, y, w, h] → a rect.
  const s = (i) => (Number.isInteger(i) && i >= 0 ? S[i] ?? '' : '');
  const rect = (b) => (b ? { x: b[0], y: b[1], width: b[2], height: b[3] } : null);
  // Layout entries per DOM node index (a pseudo node owns its box AND its text).
  (d.layout?.nodeIndex ?? []).forEach((ni, k) =>
    lay.set(ni, [...(lay.get(ni) ?? []), { b: d.layout.bounds[k], t: s(d.layout.text?.[k] ?? -1) }]));
  // pseudoType is RareStringData: parallel `index` (node) / `value` (string) arrays.
  (d.nodes?.pseudoType?.index ?? []).forEach((ni, k) => {
    if (s(d.nodes.pseudoType.value[k]) !== 'marker') return;
    const host = d.nodes.parentIndex[ni], ents = lay.get(ni) ?? [];
    out.push({ hostTag: s(d.nodes.nodeName[host]).toLowerCase(), hostRect: rect(lay.get(host)?.[0]?.b),
      box: rect(ents.find((e) => !e.t)?.b), text: ents.map((e) => e.t).join('') || null });
  });
  return out;
}

/** Walk candidates ⇄ snapshot markers by tag + used border box (2-dp walker
 *  rounding); only a UNIQUE match counts — an ambiguous one is no fact. */
export function matchMarkers(cands, markers) {
  const out = {}, near = (a, b) => a && b && ['x', 'y', 'width', 'height'].every((k) => Math.abs(a[k] - b[k]) <= 0.02);
  for (const c of cands) {
    const hits = markers.filter((m) => m.hostTag === c.tag && near(m.hostRect, c.rect));
    if (hits.length === 1) out[c.key] = hits[0];
  }
  return out;
}

/** The string fallback: the counter-style bake on a CLONE, with the ". " suffix's trimmed U+0020 restored. */
export function modelMarkerTexts(fixture, html, stem, cands) {
  const clone = structuredClone(fixture), out = {};
  bakeCounterStyles(clone, html);
  for (const c of cands) {
    const t = componentAtPath(clone, stem, c.path)?._markerText;
    if (typeof t === 'string' && t) out[c.key] = t.endsWith('.') ? `${t} ` : t;
  }
  return out;
}

// In-page (dependency-free): per item, the li's computed facts and, given a string, the probe
// span's per-code-point glyph boxes RELATIVE to it; removed again — the walk is already done.
function inPageMarkerProbe(items) {
  const out = {};
  const near = (r, q) => Math.abs(r.left - q.x) <= 0.02 && Math.abs(r.top - q.y) <= 0.02
    && Math.abs(r.width - q.width) <= 0.02 && Math.abs(r.height - q.height) <= 0.02;
  for (const it of items) {
    const el = [...document.getElementsByTagName(it.tag)].find((n) => near(n.getBoundingClientRect(), it.rect));
    if (!el) { out[it.key] = { error: 'list item not re-found by rect' }; continue; }   // declined + stamped, never skipped
    const cs = getComputedStyle(el), ms = getComputedStyle(el, '::marker'), r = el.getBoundingClientRect();
    const f = {
      direction: cs.direction, position: cs.listStylePosition, type: cs.listStyleType,
      image: cs.listStyleImage !== 'none',
      contentStart: +(r.left + parseFloat(cs.borderLeftWidth) + parseFloat(cs.paddingLeft)).toFixed(2),
      contentEnd: +(r.right - parseFloat(cs.borderRightWidth) - parseFloat(cs.paddingRight)).toFixed(2),
      style: Object.fromEntries(['color', 'fontFamily', 'fontSize', 'fontStyle', 'fontWeight', 'letterSpacing',
        'wordSpacing', 'textTransform', 'fontVariantNumeric'].map((k) => [k, ms[k]])) };
    if (it.text) {
      const sp = document.createElement('span');
      // The UA ::marker contract (css-lists-3 Appendix A) + the item's direction.
      Object.assign(sp.style, { position: 'absolute', left: '0', top: '0', visibility: 'hidden', whiteSpace: 'pre',
        unicodeBidi: 'isolate', direction: cs.direction }, Object.fromEntries(['fontFamily', 'fontSize',
        'fontStyle', 'fontWeight', 'fontVariantNumeric', 'letterSpacing'].map((k) => [k, ms[k]])));
      sp.textContent = it.text;
      document.body.appendChild(sp);
      const o = sp.getBoundingClientRect(), t = sp.firstChild, chars = [];
      for (let i = 0; i < t.length;) {
        const n = t.data.codePointAt(i) > 0xffff ? 2 : 1, rg = document.createRange();
        rg.setStart(t, i); rg.setEnd(t, i + n);
        chars.push({ c: t.data.slice(i, i + n), rects: [...rg.getClientRects()].map((q) => ({
          x: +(q.left - o.left).toFixed(2), y: +(q.top - o.top).toFixed(2), w: +q.width.toFixed(2), h: +q.height.toFixed(2) })) });
        i += n;
      }
      f.glyphs = { width: +o.width.toFixed(2), chars };
      sp.remove();
    }
    out[it.key] = f;
  }
  return out;
}

/** Browser side: `{ "<path>": facts }` per kept list item ({} for a list-free walk,
 *  no CDP session opened). NEVER throws: a fault declines every item, stamped. */
export async function collectMarkerFacts(page, elements, { fixture = null, html = '', stem = '' } = {}) {
  const cands = elements.filter(isListItem).map((e) => ({ key: e.path.join('.'), path: e.path, tag: e.tag, rect: e.rect }));
  if (!cands.length) return {};
  let snap = [], session = null;
  // A CDP failure is not fatal: the models below take over, stamped.
  try {
    session = await page.createCDPSession();
    snap = parseSnapshotMarkers(await session.send('DOMSnapshot.captureSnapshot', { computedStyles: [] }));
  } catch { snap = []; } finally { await session?.detach().catch(() => {}); }
  try {
    const cdp = matchMarkers(cands, snap), model = fixture ? modelMarkerTexts(fixture, html, stem, cands) : {};
    const items = cands.map((c) => ({ key: c.key, tag: c.tag, rect: c.rect, text: cdp[c.key]?.text ?? model[c.key] ?? null }));
    const facts = await page.evaluate(inPageMarkerProbe, items);
    // The CDP half joins the in-page half (an error fact stays text-less: declined); `textModelled` records the fallback.
    for (const it of items.filter((i) => facts[i.key] && !facts[i.key].error)) Object.assign(facts[it.key], { text: it.text,
      cdpBox: cdp[it.key]?.box ?? null, textModelled: !cdp[it.key]?.text && it.text !== null });
    return facts;
  } catch (err) {
    // A text-less fact makes planMarker decline the item with `marker-not-baked`.
    return Object.fromEntries(cands.map((c) => [c.key, { error: String(err?.message ?? err) }]));
  }
}

/** A no-strong-letter run → one single-glyph run per grapheme (glyph + its zero-width marks). */
function splitWeakRun(run, chars) {
  const eps = RUN_ADJACENCY_EPS, out = [];
  const inRun = (q) => q.x >= run.x - eps && q.x + q.w <= run.x + run.width + eps && Math.abs(q.y - run.y) <= eps;
  let g = null;
  for (const ch of chars) {
    const q = ch.rects.find((b) => b.w > 0);
    if (q && inRun(q) && !/\s/u.test(ch.c)) { if (g) out.push(...groupCharRuns(g)); g = [{ c: ch.c, rects: [q] }]; }
    else if (!q && g && !/\s/u.test(ch.c)) g.push({ c: ch.c, rects: [] });
    else if (g) { out.push(...groupCharRuns(g)); g = null; }
  }
  if (g) out.push(...groupCharRuns(g));
  return out;
}

/** Pure planning for ONE kept list-item BOX: `f` its facts, `origin` the padding-box origin of the
 *  bake ROOT that will own the runs (wave 54), `first` the item's logically-first own run `{ run, style }`.
 *  → null (no ::marker), `{ lossy }` (declined: item unchanged, stamped) or `{ boxProps, runs, lossy }`. */
export function planMarker(f, origin, first) {
  if (!f || (f.type === 'none' && !f.image)) return null;
  const decline = { lossy: [MARKER_STAMPS.notBaked] };
  if (f.image || !f.text || !f.glyphs || !first) return decline;
  // Glyph tops come from the first line's run: only sound for the same font.
  if (f.style.fontSize !== first.style.fontSize || f.style.fontFamily !== first.style.fontFamily) return decline;
  // `w` the probe advance; `x` the marker box's left edge, measured or modelled.
  const w = f.glyphs.width, lossy = []; let x;
  if (f.cdpBox && Math.abs(f.cdpBox.width - w) <= MARKER_PROBE_EPS) x = f.cdpBox.x;
  else if (f.position !== 'outside') return decline;
  else {
    // The analytic inline-start edge — a MODEL of Chromium (see the banner).
    x = f.direction === 'rtl' ? f.contentEnd : f.contentStart - w;
    lossy.push(f.cdpBox ? MARKER_STAMPS.mismatch : MARKER_STAMPS.boxModel);
  }
  if (f.textModelled) lossy.push(MARKER_STAMPS.textModel);
  // Probe glyphs → absolute boxes: x from the chosen box, tops from the first run, less the span's half-leading (q0).
  const q0 = f.glyphs.chars.flatMap((ch) => ch.rects)[0]?.y ?? 0, chars = f.glyphs.chars.map((ch) => ({ c: ch.c,
    rects: ch.rects.map((q) => ({ x: x + q.x, y: first.run.y + q.y - q0, w: q.w, h: q.h })) }));
  const runs = groupCharRuns(chars).flatMap((r) =>
    (hasStrongLtrCodepoint(r.text) || hasStrongRtlLetter(r.text) ? [r] : splitWeakRun(r, chars)));
  const props = (r) => ({ ...runProperties(r, f.style, origin),
    ...(f.style.fontVariantNumeric && f.style.fontVariantNumeric !== 'normal'
      ? { 'font-variant-numeric': f.style.fontVariantNumeric } : {}) });
  return { boxProps: { 'list-style-type': 'none' }, lossy,
    runs: runs.sort((a, b) => a.x - b.x).map((r) => ({ props: props(r), text: r.text })) };
}
