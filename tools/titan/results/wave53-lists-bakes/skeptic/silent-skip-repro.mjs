// tools/titan/results/wave53-lists-bakes/skeptic/silent-skip-repro.mjs — L1 skeptic repro: when inPageMarkerProbe cannot
// find a list item by its walked rect (`if (!el) continue;`, bidi-marker-bake.mjs), the item gets NO facts, planMarker
// returns null, and the baked <li> keeps its marker unchanged with NO `marker-not-baked` stamp — the silent path the
// module banner says cannot happen. Drives the REAL collectMarkerFacts with a fake page whose in-page probe found nothing
// (exactly what inPageMarkerProbe returns then), then the REAL planBidiBake on counter-suffix's verbatim RTL geometry.
import { collectMarkerFacts } from '../../../bidi-marker-bake.mjs';
import { planBidiBake } from '../../../bidi-bake.mjs';
const page = { createCDPSession: async () => ({ send: async () => ({ strings: [], documents: [] }), detach: async () => {} }),
  evaluate: async () => ({}) };   // inPageMarkerProbe's answer when `[...getElementsByTagName(tag)].find(near)` is undefined
const style = { color: 'rgb(0, 0, 0)', fontFamily: 'Inter', fontSize: '16px', fontStyle: 'normal', fontWeight: '400', letterSpacing: 'normal', wordSpacing: '0px', textTransform: 'none' };
const el = (path, extra) => ({ path, tag: 'div', rect: { x: 0, y: 0, width: 160, height: 48 }, rectCount: 1, borderLeft: 0, borderTop: 0,
  display: 'block', position: 'static', decoration: 'none', texts: [], bidiAffected: false, ...extra });
const word = (t, x, y, adv) => { let at = x; return [{ style, chars: [...t].map((c, i) => { const r = { x: at, y, w: adv[i], h: 20 }; at += adv[i]; return { c, rects: [r] }; }) }]; };
const elements = [el([0], { rect: { x: 0, y: 0, width: 160, height: 288 } }),
  { ...el([0, 4], { tag: 'ol', rect: { x: 0, y: 192, width: 160, height: 48 }, bidiAffected: true }), padding: [0, 48, 0, 48], backgroundClip: 'border-box', backgroundOrigin: 'padding-box', overflow: ['visible', 'visible'] },
  el([0, 4, 0], { tag: 'li', display: 'list-item', bidiAffected: true, rect: { x: 48, y: 192, width: 64, height: 24 }, texts: word('foo', 87.36, 194, [5.4, 9.62, 9.62]) })];
const markers = await collectMarkerFacts(page, elements);
const { bail, plan } = planBidiBake({ bodyTextIsBidi: false, markers, elements });
const box = plan.boxes.find((b) => b.path.join('.') === '0.4.0');
console.log('SK facts', JSON.stringify(markers), '| bail', bail, '| li box list-style-type', box.props['list-style-type'] ?? '(unchanged)',
  '| li stamp', JSON.stringify(box.lossy ?? null), '| runs', JSON.stringify(plan.runs.map((r) => r.text)));
