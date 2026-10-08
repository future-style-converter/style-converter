// skeptic/escape-repro.mjs — executed repro (read-only import of the shared tree's bake; no file is written):
// under unit M′ a list item's marker runs are re-parented from the item to the bake ROOT, so every paint effect
// and every inherited property the ITEM (or an intermediate box) carries, and which runProperties() does not
// restate, stops reaching the marker — with no decline and no `marker-*` stamp. Built on the VERBATIM frozen
// counter-suffix bake output with ONE authored change: `opacity: 0; visibility: hidden; text-shadow: …` on the
// first RTL <li> (an item whose ::marker Blink would not paint). Usage: node <this file>
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const T = join(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');          // tools/titan
const { planBidiBake, applyBidiBakePlan } = await import(join(T, 'bidi-bake.mjs'));
// The li's resolved text style (as the walker reads it) and one LTR word laid out per char.
const ST = { color: 'rgb(0, 0, 0)', fontFamily: 'Inter, -apple-system, "system-ui", "Segoe UI", Roboto, Oxygen, Ubuntu, sans-serif',
  fontSize: '16px', fontStyle: 'normal', fontWeight: '400', letterSpacing: 'normal', wordSpacing: '0px', textTransform: 'none' };
const word = (t, x, y, adv) => { let a = x; return [{ style: ST, chars: [...t].map((c, i) => { const r = { x: +a.toFixed(2), y, w: adv[i], h: 20 }; a += adv[i]; return { c, rects: [r] }; }) }]; };
const E = (path, o) => ({ path, tag: 'div', rectCount: 1, borderLeft: 0, borderTop: 0, display: 'block', position: 'static', decoration: 'none', texts: [], bidiAffected: false, ...o });
const root = (k, y) => ({ ...E([0, k], { tag: 'ol', bidiAffected: true, rect: { x: 0, y, width: 160, height: 48 } }),
  padding: [0, 48, 0, 48], backgroundClip: 'border-box', backgroundOrigin: 'padding-box', overflow: ['visible', 'visible'] });
const li = (k, i, y, t, x) => E([0, k, i], { tag: 'li', display: 'list-item', bidiAffected: true, rect: { x: 48, y, width: 64, height: 24 },
  texts: word(t, x, y + 2, t === 'foo' ? [5.4, 9.62, 9.62] : [9.66, 8.72, 6.59]) });
// Chromium's measured ::marker facts (CDP box x112; per-glyph span offsets reproducing the measured runs).
const MS = { ...ST, fontVariantNumeric: 'tabular-nums' };
const facts = (text, w, y, g) => ({ direction: 'rtl', position: 'outside', type: 'decimal', image: false, contentStart: 48, contentEnd: 112,
  style: MS, text, textModelled: false, cdpBox: { x: 112, y, width: w, height: 24 },
  glyphs: { width: +w.toFixed(2), chars: g.map(([c, x, gw]) => ({ c, rects: [{ x, y: 0, w: gw, h: 20 }] })) } });
const walk = { bodyTextIsBidi: false, elements: [E([0], { rect: { x: 0, y: 0, width: 160, height: 288 } }),
  root(4, 192), li(4, 0, 192, 'foo', 87.36), li(4, 1, 216, 'bar', 87.03)],
  markers: { '0.4.0': facts('1. ', 18.96875, 192, [['1', 8.59, 10.38], ['.', 4.3, 4.3], [' ', 0, 4.3]]),
             '0.4.1': facts('2. ', 18.96875, 216, [['2', 8.59, 10.38], ['.', 4.3, 4.3], [' ', 0, 4.3]]) } };
const { bail, plan } = planBidiBake(walk);
if (bail) throw new Error(bail);
// The ONE authored change, on the verbatim frozen fixture: the first RTL item is invisible.
const fx = JSON.parse(readFileSync(join(T, 'results', 'wave53-plan', 'bidi-baked-fixtures', 'css-counter-styles', 'counter-suffix.json'), 'utf8'));
const find = (n, id) => { for (const [k, c] of Object.entries(n.children ?? n.components ?? {})) { if (k === id) return c; const h = find(c, id); if (h) return h; } return null; };
Object.assign(find(fx, 'counter-suffix__0__4__0').properties, { opacity: '0', visibility: 'hidden', 'text-shadow': '2px 2px red' });
const markerRuns = plan.runs.filter((r) => !['foo', 'bar'].includes(r.text));
const lossyOf = (p) => plan.boxes.find((b) => b.path.join('.') === p)?.lossy ?? [];
applyBidiBakePlan(fx, 'counter-suffix', { ...plan, runs: markerRuns, hides: [] });
// Every component's ancestor chain (ids), so "does `opacity` / `visibility` / `text-shadow` reach it" is a lookup.
const chain = new Map();
const walkFx = (n, anc) => { for (const [k, c] of Object.entries(n.children ?? n.components ?? {})) { chain.set(k, { c, anc }); walkFx(c, [...anc, c]); } };
walkFx(fx, []);
const reach = (id, prop) => [chain.get(id).c, ...chain.get(id).anc].some((a) => a.properties?.[prop] !== undefined && a.properties[prop] !== 'visible' && a.properties[prop] !== '1' && a.properties[prop] !== 'none');
console.log('item counter-suffix__0__4__0 _lossyReasons:', JSON.stringify(find(fx, 'counter-suffix__0__4__0')._lossyReasons), '| plan box lossy:', JSON.stringify(lossyOf('0.4.0')));
for (const id of ['counter-suffix__0__4__0__0', 'counter-suffix__0__4__2', 'counter-suffix__0__4__3']) {
  const c = chain.get(id).c;
  console.log(`${id} ${JSON.stringify(c._text)} parent=${chain.get(id).anc.at(-1).id} | opacity reaches: ${reach(id, 'opacity')} · visibility:hidden reaches: ${reach(id, 'visibility')} · text-shadow reaches: ${reach(id, 'text-shadow')} | own: ${JSON.stringify(Object.keys(c.properties).filter((k) => ['opacity', 'visibility', 'text-shadow'].includes(k)))}`);
}
const leak = ['counter-suffix__0__4__2', 'counter-suffix__0__4__3'].some((id) => !reach(id, 'visibility') || !reach(id, 'opacity'));
console.log(leak ? 'REPRO: the item\'s text run is hidden, its marker runs (owned by the root) are NOT — and no marker-* stamp records it'
                 : 'no leak');
