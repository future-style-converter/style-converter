#!/usr/bin/env node
// tools/titan/results/wave55-plan/android-paint-order.census.mjs
//
// Census for the wave-55 lane L6 brief `android-paint-order.md` (BACKLOG obligation 0(k)).
// READ-ONLY over a gate run: every per-test IR document of every section, joined to that run's scored
// cells through the scorer's own loader (score-gate.mjs loadRun: a cell counts only if typeof ssim ===
// "number" && !scoreExcluded; P iff wptPass === true).
//
// Questions answered:
//  (1) U1 radius — the Compose SELF-PAINT route (BorderRadiusApplier.selfPaintsWithoutClip true AND a
//      uniform-solid border band, i.e. applySelfPaint calls androidx Modifier.border(width, color, shape),
//      whose BorderModifierNode draws drawContent() FIRST and the band AFTER): which elements take it, and
//      which of their children can paint into the band (the only pixels a behind-content band changes).
//  (2) U2 radius — the LEGACY route (BordersFacade.apply → BorderSideApplier: Modifier.border fast path or
//      drawWithContent { drawContent(); paintSide… } slow path, both chained OUTER of ColorApplier, i.e. the
//      band paints over every descendant AND outside the opacity group): the same overlap question, plus the
//      bordered elements carrying opacity < 1 (whose band a reorder would move inside the group).
//  (3) every `filter: drop-shadow()` carrier, with its clip-path / background / text context.
//
// Overlap classes (child → parent band), from px-resolvable IR only (anything else is `unknown`):
//   abspos-band   ABSOLUTE/FIXED child of a parent that is its containing block (positioned, or a
//                 transform/filter/backdrop-filter/contain CB — CanvasRootHoist.establishesTransformContainingBlock
//                 mirrors this) whose px border box intersects the parent's border band. A child whose CB is
//                 NOT this parent is hoisted (CanvasRootHoist) and paints at the canvas root: not a carrier.
//   abspos-unknown same, geometry not resolvable in px (%, auto, calc, ch, em …).
//   flow-negmargin in-flow child whose negative margin exceeds the parent's padding on that side.
//   flow-overflow  in-flow child whose px border-box width exceeds the parent's content+padding width.
//   rel-offset    RELATIVE child with a non-zero px offset that crosses the parent's padding.
//
// Usage: node android-paint-order.census.mjs [run-id=wave54-final] [--json out.json]
import fs from 'node:fs';
import path from 'node:path';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

const args = process.argv.slice(2);
const runId = args.find((a) => !a.startsWith('--')) ?? 'wave54-final';
const jsonOut = args.includes('--json') ? args[args.indexOf('--json') + 1] : null;
const runDir = resolveRunDir(runId);
const run = loadRun(runDir);

// ---- property readers (wire shapes as the corpus carries them; see schema/spec/02-values.md) ----
const prop = (c, t) => (c.properties ?? []).find((p) => p.type === t)?.data;
const px = (d) => (d && typeof d === 'object' && typeof d.px === 'number' ? d.px : (typeof d === 'number' ? d : null));
const kw = (d) => (typeof d === 'string' ? d.toUpperCase() : null);
const colorKey = (d) => (d?.srgb ? JSON.stringify(d.srgb) : JSON.stringify(d ?? null));
const SIDES = ['Top', 'Right', 'Bottom', 'Left'];
const RADII = ['BorderTopLeftRadius', 'BorderTopRightRadius', 'BorderBottomRightRadius', 'BorderBottomLeftRadius'];

function side(c, s) {
  const w = px(prop(c, `Border${s}Width`));
  const st = kw(prop(c, `Border${s}Style`));
  const visible = !!st && st !== 'NONE' && st !== 'HIDDEN' && (w ?? 0) > 0;
  return { w: visible ? w : 0, st, col: colorKey(prop(c, `Border${s}Color`)), visible };
}
function borders(c) {
  const s = SIDES.map((x) => side(c, x));
  const any = s.some((x) => x.visible);
  // logical border longhands are not folded into the physical sides here: flag them, never guess
  const logical = (c.properties ?? []).some((p) => /^Border(Block|Inline)(Start|End)?(Width|Style|Color)$/.test(p.type));
  const uniformSolid = any && s.every((x) => x.visible && x.w === s[0].w && x.st === 'SOLID' && x.col === s[0].col);
  return { sides: s, any, uniformSolid, logical };
}
function hasRadius(c) {
  return RADII.some((t) => {
    const d = prop(c, t);
    if (!d) return false;
    if (typeof d.px === 'number') return d.px > 0;
    const o = d.original;
    if (o && typeof o.v === 'number') return o.v > 0;
    if (d.x || d.y) return true;
    return JSON.stringify(d) !== '{}';
  });
}
function overflowClips(c) {
  return ['Overflow', 'OverflowX', 'OverflowY'].some((t) => {
    const v = kw(prop(c, t));
    return v && v !== 'VISIBLE';
  });
}
function opacity(c) {
  const d = prop(c, 'Opacity');
  if (d == null) return 1;
  if (typeof d.alpha === 'number') return d.alpha;
  if (typeof d === 'number') return d;
  return 1;
}
function selfPaint(c) {
  // BorderRadiusApplier.selfPaintsWithoutClip, field by field (StyleApplier.applyConfig step 5./6.)
  if (!hasRadius(c)) return false;
  if (overflowClips(c)) return false;
  const imgs = prop(c, 'BackgroundImage');
  if (Array.isArray(imgs) && imgs.length) return false;
  const clip = prop(c, 'BackgroundClip');
  if (Array.isArray(clip) && clip.some((x) => String(x).toUpperCase() !== 'BORDER_BOX')) return false;
  if (opacity(c) < 1) return false;
  const b = borders(c);
  return !b.any || b.uniformSolid;
}
const position = (c) => kw(prop(c, 'Position')) ?? 'STATIC';
const establishesCB = (c) => {
  // css-position-3 §3.1 positioned box, or the CB-establishing paints CanvasRootHoist reads
  // (TransformContainingBlock / OutOfFlowContainingBlock: transform, filter, backdrop-filter, contain …)
  if (position(c) !== 'STATIC') return true;
  return (c.properties ?? []).some((p) => ['Transform', 'Filter', 'BackdropFilter', 'Perspective'].includes(p.type) ||
    (p.type === 'Contain' && /LAYOUT|PAINT|STRICT|CONTENT/i.test(JSON.stringify(p.data))) ||
    (p.type === 'WillChange' && /transform|filter|perspective/i.test(JSON.stringify(p.data))));
};
const lenPx = (c, t) => px(prop(c, t)?.px !== undefined ? prop(c, t) : prop(c, t));

// border-box size of a component in px (content-box sizing unless BoxSizing BORDER_BOX); null when unresolvable
function boxSize(c) {
  const W = lenPx(c, 'Width'); const H = lenPx(c, 'Height');
  const b = borders(c).sides; const pad = (s) => px(prop(c, `Padding${s}`)) ?? 0;
  const bb = kw(prop(c, 'BoxSizing')) === 'BORDER_BOX';
  const w = W == null ? null : (bb ? W : W + pad('Left') + pad('Right') + b[3].w + b[1].w);
  const h = H == null ? null : (bb ? H : H + pad('Top') + pad('Bottom') + b[0].w + b[2].w);
  return { w, h };
}

function overlapClass(parent, child) {
  const pb = borders(parent).sides; // [t, r, b, l]
  const pbox = boxSize(parent);
  const pad = (s) => px(prop(parent, `Padding${s}`)) ?? 0;
  const pos = position(child);
  if (pos === 'ABSOLUTE' || pos === 'FIXED') {
    if (!establishesCB(parent)) return null; // hoisted to the canvas root: paints above, not a carrier
    if (pbox.w == null || pbox.h == null) return 'abspos-unknown';
    // parent padding box, origin at its top-left (abspos insets resolve against the padding box, CSS 2.1 §10.1 item 4)
    const PW = pbox.w - pb[1].w - pb[3].w, PH = pbox.h - pb[0].w - pb[2].w;
    const cs = boxSize(child);
    const L = px(prop(child, 'Left')), T = px(prop(child, 'Top')), Rr = px(prop(child, 'Right')), Bb = px(prop(child, 'Bottom'));
    const ml = px(prop(child, 'MarginLeft')) ?? 0, mt = px(prop(child, 'MarginTop')) ?? 0;
    // an axis with BOTH insets `auto` (absent on the wire) takes the STATIC position (CSS 2.1 §10.3.7 / §10.6.4):
    // the in-flow origin inside the parent's content box, i.e. padding + margin — in the band only on a negative
    // margin larger than the padding. An inset that IS declared but not in px is `abspos-unknown`.
    const declared = (t) => prop(child, t) !== undefined;
    const staticX = !declared('Left') && !declared('Right'), staticY = !declared('Top') && !declared('Bottom');
    let x = staticX ? pad('Left') + ml : (L != null ? L + ml : (Rr != null && cs.w != null ? PW - Rr - cs.w : null));
    let y = staticY ? pad('Top') + mt : (T != null ? T + mt : (Bb != null && cs.h != null ? PH - Bb - cs.h : null));
    if (x == null || y == null) return 'abspos-unknown';
    if (cs.w == null || cs.h == null) {
      // size not in px (auto / % / intrinsic): only the ORIGIN can be tested; a static or non-negative origin
      // inside the padding box cannot reach the top/left band, and the right/bottom band needs the size → unknown
      if (x < 0 || y < 0) return 'abspos-band';
      return staticX && staticY ? null : 'abspos-unknown';
    }
    const x0 = x, y0 = y, x1 = x + cs.w, y1 = y + cs.h;
    // border band = [-bl, PW+br] × [-bt, PH+bb] minus [0, PW] × [0, PH]
    const inOuter = x1 > -pb[3].w && x0 < PW + pb[1].w && y1 > -pb[0].w && y0 < PH + pb[2].w;
    const insideInner = x0 >= 0 && x1 <= PW && y0 >= 0 && y1 <= PH;
    return inOuter && !insideInner ? 'abspos-band' : null;
  }
  const m = (s) => px(prop(child, `Margin${s}`));
  if ((m('Left') ?? 0) < -pad('Left') || (m('Top') ?? 0) < -pad('Top') || (m('Right') ?? 0) < -pad('Right')) return 'flow-negmargin';
  const cs = boxSize(child);
  if (pbox.w != null && cs.w != null) {
    const contentPlusPad = pbox.w - pb[1].w - pb[3].w - pad('Left');
    if (cs.w + (m('Left') ?? 0) > contentPlusPad + 0.5) return 'flow-overflow';
  }
  if (pos === 'RELATIVE') {
    const L = px(prop(child, 'Left')) ?? 0, T = px(prop(child, 'Top')) ?? 0;
    const R = px(prop(child, 'Right')) ?? 0, B = px(prop(child, 'Bottom')) ?? 0;
    if (L < -pad('Left') || T < -pad('Top') || R < -pad('Right') || B < -pad('Bottom')) return 'rel-offset';
  }
  return null;
}

function keyIndex(sec) {
  const m = JSON.parse(fs.readFileSync(path.join(runDir, 'sections', sec, 'manifest.json'), 'utf8'));
  const idx = new Map();
  for (const k of Object.keys(m.wpt?.results ?? {})) {
    const stem = 'wpt__' + k.replace(/^css\//, '').replace(/\.[a-z]+$/, '').split('/').join('__');
    idx.set(stem, k);
  }
  return idx;
}
const cellsOf = (sec, key) => {
  const o = {};
  for (const p of ['web', 'ios', 'android']) { const x = key && run.sections[sec].cells.get(`${key}|${p}`); o[p] = x ? `${x.pass ? 'P' : 'f'} ${x.ssim}` : '—'; }
  return o;
};

const u1 = [], u2 = [], opacityBordered = [], dropShadow = [], leadingSpace = [];
let docs = 0, selfPaintBordered = 0, legacyBordered = 0;
for (const sec of Object.keys(run.sections).sort()) {
  const irDir = path.join(runDir, 'sections', sec, 'per-test-ir');
  if (!fs.existsSync(irDir) || !run.sections[sec].hasManifest) continue;
  const idx = keyIndex(sec);
  for (const f of fs.readdirSync(irDir).filter((x) => x.endsWith('.json')).sort()) {
    docs++;
    const doc = JSON.parse(fs.readFileSync(path.join(irDir, f), 'utf8'));
    const comps = doc.components ?? [];
    const kids = new Map();
    for (const c of comps) if (c.slot?.parent) { if (!kids.has(c.slot.parent)) kids.set(c.slot.parent, []); kids.get(c.slot.parent).push(c); }
    const stem = f.replace(/\.json$/, '');
    const key = idx.get(stem) ?? null;
    // (4) NOT this lane — the residual both target cells keep on Android: a {text, <br>}*-only runs host (Compose
    // InlineRunFold bails `br-stacked-equivalent` and stacks one block per run) whose run AFTER a <br> starts with a
    // collapsible space, which css-text-3 §4.1.2 phase II removes at the start of the line (web and iOS do).
    const byName = new Map(comps.map((c) => [c.name, c]));
    for (const c of comps) {
      const runs = c.meta?.runs;
      if (!runs) continue;
      let brs = 0, onlyTextBr = true;
      for (const r of runs) {
        if ('text' in r) continue;
        if (byName.get(r.child)?.meta?.role === 'line-break') brs++; else { onlyTextBr = false; break; }
      }
      if (!onlyTextBr || !brs) continue;
      let prevBr = false, hit = false;
      for (const r of runs) {
        if ('text' in r) { if (prevBr && r.text.startsWith(' ') && r.text.trim()) hit = true; prevBr = false; } else prevBr = true;
      }
      if (hit) leadingSpace.push({ sec, stem, id: c.id, cells: cellsOf(sec, key) });
    }
    for (const c of comps) {
      const filt = prop(c, 'Filter');
      if (Array.isArray(filt) && filt.some((x) => x.fn === 'drop-shadow')) {
        dropShadow.push({ sec, stem, id: c.id, fns: filt.map((x) => x.fn), clipPath: prop(c, 'ClipPath')?.type ?? null,
          bg: !!prop(c, 'BackgroundColor'), text: !!c.text, kids: (kids.get(c.id) ?? []).length, cells: cellsOf(sec, key) });
      }
      const b = borders(c);
      if (!b.any) continue;
      const sp = selfPaint(c) && b.uniformSolid;
      if (sp) selfPaintBordered++; else legacyBordered++;
      if (!sp && opacity(c) < 1) opacityBordered.push({ sec, stem, id: c.id, opacity: opacity(c), kids: (kids.get(c.id) ?? []).length, cells: cellsOf(sec, key) });
      const hits = [];
      for (const k of kids.get(c.id) ?? []) {
        if (k.meta?.role === 'line-break') continue;
        const cls = overlapClass(c, k);
        if (cls) hits.push({ child: k.id, cls });
      }
      if (!hits.length) continue;
      const row = { sec, stem, id: c.id, route: sp ? 'self-paint' : 'legacy', radius: hasRadius(c), logical: b.logical, hits, cells: cellsOf(sec, key) };
      (sp ? u1 : u2).push(row);
    }
  }
}

const docsOf = (rows) => [...new Set(rows.map((r) => `${r.sec}/${r.stem}`))];
const byClass = (rows) => { const o = {}; for (const r of rows) for (const h of r.hits) o[h.cls] = (o[h.cls] ?? 0) + 1; return o; };
const line = (r) => `  ${r.sec}/${r.stem.replace(/^wpt__[^_]+(?:-[^_]+)*__/, '')}  ${r.id}  [${r.hits.map((h) => h.cls).join(',')}]  web ${r.cells.web} · ios ${r.cells.ios} · android ${r.cells.android}`;

console.log(`run ${runId}: ${docs} per-test IR documents`);
console.log(`bordered components: self-paint route (radius + uniform-solid band) ${selfPaintBordered} · legacy route ${legacyBordered}`);
console.log(`\n(1) U1 — self-paint parents with a child that can paint into the band: ${u1.length} parents in ${docsOf(u1).length} docs; child classes ${JSON.stringify(byClass(u1))}`);
for (const r of u1) console.log(line(r));
const u2band = u2.filter((r) => r.hits.some((h) => h.cls !== 'abspos-unknown'));
console.log(`\n(2) U2 — legacy-route parents with a child that can paint into the band: ${u2.length} parents in ${docsOf(u2).length} docs; child classes ${JSON.stringify(byClass(u2))}`);
console.log(`    of which resolvable (not only abspos-unknown): ${u2band.length} parents in ${docsOf(u2band).length} docs`);
for (const r of u2band) console.log(line(r));
const u2unk = u2.filter((r) => !u2band.includes(r));
console.log(`    abspos-unknown only (declared non-px inset or non-px size; not listed one by one): ${u2unk.length} parents in ${docsOf(u2unk).length} docs:`);
for (const d of docsOf(u2unk)) { const r = u2unk.find((x) => `${x.sec}/${x.stem}` === d); console.log(`      ${d}  web ${r.cells.web} · ios ${r.cells.ios} · android ${r.cells.android}`); }
console.log(`\n(2b) legacy-route bordered components with opacity < 1 (a reorder would move their band INSIDE the group): ${opacityBordered.length} in ${[...new Set(opacityBordered.map((r) => r.sec + '/' + r.stem))].length} docs`);
for (const r of opacityBordered) console.log(`  ${r.sec}/${r.stem}  ${r.id}  opacity ${r.opacity}  kids ${r.kids}  web ${r.cells.web} · ios ${r.cells.ios} · android ${r.cells.android}`);
console.log(`\n(3) filter: drop-shadow() carriers: ${dropShadow.length} components in ${[...new Set(dropShadow.map((r) => r.stem))].length} docs`);
for (const r of dropShadow) console.log(`  ${r.sec}/${r.stem}  ${r.id}  fns ${r.fns.join(' ')}  clip-path ${r.clipPath}  bg ${r.bg}  text ${r.text}  kids ${r.kids}  web ${r.cells.web} · ios ${r.cells.ios} · android ${r.cells.android}`);
console.log(`\n(4) NOT this lane: {text, <br>}*-only runs hosts whose run after a <br> keeps a leading collapsible space: ${leadingSpace.length} hosts in ${[...new Set(leadingSpace.map((r) => r.stem))].length} docs`);
for (const r of leadingSpace) console.log(`  ${r.sec}/${r.stem}  web ${r.cells.web} · ios ${r.cells.ios} · android ${r.cells.android}`);
if (jsonOut) fs.writeFileSync(jsonOut, JSON.stringify({ runId, docs, selfPaintBordered, legacyBordered, u1, u2, opacityBordered, dropShadow, leadingSpace }, null, 1) + '\n');
