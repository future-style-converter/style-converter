#!/usr/bin/env node
// tools/titan/results/wave55-plan/positioned-parent-slot.census.mjs
//
// Wave-55 plan, lane L3-positioned-parent-slot (BACKLOG obligation 0(i)). READ-ONLY census over the per-test IR of a
// gate run (the exact wire every runtime rendered) plus that run's manifests (the scorer's own loadRun) — no images,
// no device, no build. It answers: which Compose render paths does "mount PositionedParentFlowSlot for host-inactive
// documents too" change, and what does each touched document score on Android today?
//
// The predicate it ports (runtimes/compose/…/core/renderer/ComponentRenderer.kt :3650-3657 at 0d0d3a3d):
//   outOfFlowSlotMount = CanvasRootHoist.LocalActive && PositionedParentFlowSlot.applies(child, parent)
//   applies = child Position ∈ {ABSOLUTE, FIXED} && parent Position ∈ {ABSOLUTE, FIXED, STICKY}
// reached only in RenderContent's non-positioned-container branch, i.e. NOT when the parent is RELATIVE or a
// transform/OOF containing block hosting an out-of-flow child (`transformCbHostsOutOfFlowChild` → `isPositionedContainer`, :3504-3512).
// LocalActive is true exactly when CanvasRootHoist.hostActivates(roots) = anyOutOfFlowBox(roots) — ported below from
// CanvasRootHoist.kt :322 shouldHoistToCanvasRoot, :380 rendersInFlowAsStaticPosition, :572 anyOutOfFlowBox, with
// the wave-54 L4 predicates (OutOfFlowContainingBlock.establishes / declaresAnyInset) and the wave-49 multicol
// spanner restart (MulticolSpannerContainingBlock.childPositionedAncestor). Stated approximations (decoders not
// ported byte-for-byte): ClipPath "establishes" = a ClipPath declaration whose data is not the primitive none;
// Contain layout|paint = a token LAYOUT / PAINT / STRICT / CONTENT anywhere in the leaf; multicol container = a
// non-auto ColumnCount or ColumnWidth; spanner = ColumnSpan ALL.
//
// Usage: node positioned-parent-slot.census.mjs [run=wave54-final] [--json out.json]
import { readdirSync, readFileSync, existsSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

// Repo root: four levels above this file.
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../..');
const args = process.argv.slice(2);
const run = args.find((a) => !a.startsWith('--') && !a.endsWith('.json')) ?? 'wave54-final';
const jsonOut = args.includes('--json') ? args[args.indexOf('--json') + 1] : null;
const secDir = path.join(ROOT, 'tools/titan/runs', run, 'sections');

// ── wire readers (last declaration of a type wins — the extractors' fold order) ──
const prop = (c, t) => (c.properties || []).filter((p) => p.type === t).at(-1)?.data;
const has = (c, t) => (c.properties || []).some((p) => p.type === t);
// ValueExtractors.extractKeyword: a primitive, or {keyword}/{value}.
const kw = (d) => (d == null ? null : typeof d === 'object' ? (d.keyword ?? d.value ?? null) : String(d));
// PositionExtractor.parsePositionType.
const posOf = (c) => {
  const k = String(kw(prop(c, 'Position')) ?? '').toLowerCase();
  return ['relative', 'absolute', 'fixed', 'sticky'].includes(k) ? k.toUpperCase() : 'STATIC';
};
// ValueExtractors.extractDp: {px}, {original:{px}}, {original:{original:{px}}}, {original:{v,u:EM|REM}}, number.
const dp = (d) => {
  if (d == null) return null;
  if (typeof d === 'number') return d;
  if (typeof d === 'string') return Number.isFinite(Number(d)) && d.trim() !== '' ? Number(d) : null;
  if (typeof d !== 'object') return null;
  if (typeof d.px === 'number') return d.px;
  const o = d.original;
  if (!o || typeof o !== 'object') return null;
  if (typeof o.px === 'number') return o.px;
  if (o.original && typeof o.original.px === 'number') return o.original.px;
  if (typeof o.v === 'number' && ['EM', 'REM'].includes(String(o.u).toUpperCase())) return o.v * 16;
  return null;
};
const INSETS = ['Top', 'Right', 'Bottom', 'Left', 'InsetBlockStart', 'InsetBlockEnd', 'InsetInlineStart', 'InsetInlineEnd'];
// CanvasRootHoist.hasAnyInset — a RESOLVED inset on any side (PositionConfig.resolved*).
const hasAnyInset = (c) => INSETS.some((t) => dp(prop(c, t)) !== null);
// OutOfFlowContainingBlock.declaresAnyInset — any inset declaration whose leaf is not the primitive "auto".
const declaresAnyInset = (c) => (c.properties || []).some((p) => INSETS.includes(p.type) && p.data !== 'auto');
// TransformContainingBlock.establishes (:112).
const tfCb = (c) => (c.properties || []).some((p) => {
  const d = p.data;
  switch (p.type) {
    case 'Transform': return d && typeof d === 'object' && !Array.isArray(d) ? (d.type === 'functions' ? Array.isArray(d.list) && d.list.length > 0 : true) : false;
    case 'Translate': case 'Rotate': case 'Scale': case 'Perspective':
      return d && typeof d === 'object' && !Array.isArray(d) ? d.type !== 'none' : false;
    case 'TransformStyle': return d === 'PRESERVE_3D';
    case 'WillChange': return Array.isArray(d) && d.some((h) => h && (h.name === 'transform' || h.name === 'perspective'));
    default: return false;
  }
});
// OutOfFlowContainingBlock.establishes (:88) — an out-of-flow box never claims it for itself.
const oofCb = (c) => {
  const fires = (c.properties || []).some((p) => {
    const d = p.data;
    switch (p.type) {
      case 'Contain': return /LAYOUT|PAINT|STRICT|CONTENT/i.test(JSON.stringify(d ?? ''));
      case 'Filter': case 'BackdropFilter': return Array.isArray(d) ? d.length > 0 : (d !== null && typeof d === 'object');
      case 'WillChange': return Array.isArray(d) && d.some((h) => h && ['filter', 'backdrop-filter', 'contain'].includes(h.name));
      default: return false;
    }
  });
  return fires && !['ABSOLUTE', 'FIXED'].includes(posOf(c));
};
// CanvasRootHoist.establishesTransformContainingBlock (:231).
const establishesTf = (c) => tfCb(c) || oofCb(c);
// establishesUnescapableClip — approximation (banner).
const clipCb = (c) => { const d = prop(c, 'ClipPath'); return d != null && !(typeof d === 'string' && /^none$/i.test(d)); };
// MulticolSpannerContainingBlock helpers — approximations (banner).
const isMulticol = (c) => ['ColumnCount', 'ColumnWidth'].some((t) => { const d = prop(c, t); return d != null && !(typeof d === 'string' && /^auto$/i.test(d)); });
const isSpanner = (c) => /^all$/i.test(String(kw(prop(c, 'ColumnSpan')) ?? ''));
// shouldHoistToCanvasRoot (:322).
const hoists = (c, ancPos, ancTf, ancClip) => {
  if (ancClip) return false;
  const p = posOf(c);
  if (p === 'FIXED') return !ancTf && declaresAnyInset(c);
  if (p === 'ABSOLUTE') return !ancPos && !ancTf && hasAnyInset(c);
  return false;
};
// rendersInFlowAsStaticPosition (:380), staticPositionOwned = false.
const staticPos = (c, ancPos, ancTf, ancClip) => {
  const p = posOf(c);
  const rc1 = (p === 'ABSOLUTE' ? !hasAnyInset(c) : p === 'FIXED' ? !declaresAnyInset(c) : false) && !ancPos;
  return rc1 || (ancClip && hoists(c, ancPos, ancTf, false));
};

// ── scored cells of the run (the scorer's own loader: scored = numeric ssim && !scoreExcluded) ──
const loaded = loadRun(resolveRunDir(run));
const cellOf = (sec, stem, platform) => {
  const s = loaded.sections[sec];
  if (!s) return null;
  for (const [k, v] of s.cells) {
    const [test, plat] = k.split('|');
    if (plat !== platform) continue;
    const rest = test.replace(/^css\//, '').replace(new RegExp(`^${sec}/`), '').replace(/\.[a-z]+$/i, '');
    if (`wpt__${sec}__${rest.replace(/\//g, '__')}` === stem) return `${v.pass ? 'P' : 'f'} ${v.ssim}`;
  }
  return null;
};

const out = { run, docs: 0, docsHostInactive: 0, pairs: [], parents: [] };
for (const sec of readdirSync(secDir).sort()) {
  const irDir = path.join(secDir, sec, 'per-test-ir');
  if (!existsSync(irDir)) continue;
  for (const f of readdirSync(irDir).filter((x) => x.endsWith('.json')).sort()) {
    const doc = JSON.parse(readFileSync(path.join(irDir, f), 'utf8'));
    out.docs++;
    const stem = f.replace(/\.json$/, '');
    const comps = Array.isArray(doc.components) ? doc.components : Object.values(doc.components || {});
    const byId = new Map(comps.map((c) => [c.id, c]));
    const kids = new Map();
    for (const c of comps) { const p = c.slot?.parent; if (p && byId.has(p)) (kids.get(p) ?? kids.set(p, []).get(p)).push(c); }
    const roots = comps.filter((c) => !c.slot?.parent || !byId.has(c.slot.parent));
    // anyOutOfFlowBox (:572), one-for-one threading.
    const activates = (function walk(list, ancPos, ancTf, ancClip, atMc) {
      for (const n of list) {
        if (hoists(n, ancPos, ancTf, ancClip) || staticPos(n, ancPos, ancTf, ancClip)) return true;
        const self = posOf(n) !== 'STATIC';
        const childPos = atMc != null && isSpanner(n) ? (atMc || self) : (ancPos || self);
        const childAtMc = isMulticol(n) ? childPos : atMc;
        if (walk(kids.get(n.id) ?? [], childPos, ancTf || establishesTf(n), ancClip || clipCb(n), childAtMc)) return true;
      }
      return false;
    })(roots, false, false, false, null);
    if (!activates) out.docsHostInactive++;
    // Inherited direction (Direction inherits, css-writing-modes-4 §2.1): nearest ancestor-or-self declaration.
    const dirOf = (c) => { for (let n = c; n; n = byId.get(n.slot?.parent)) { const d = kw(prop(n, 'Direction')); if (d) return String(d).toUpperCase(); } return 'LTR'; };
    for (const c of comps) {
      const pp = posOf(c);
      if (!['ABSOLUTE', 'FIXED', 'STICKY'].includes(pp)) continue;
      const ch = kids.get(c.id) ?? [];
      const oof = ch.filter((k) => ['ABSOLUTE', 'FIXED'].includes(posOf(k)));
      if (!oof.length) continue;
      // :3504-3512 — a transform/OOF-CB parent hosting an out-of-flow child takes the Box branch instead (:3504-3512).
      const boxBranch = establishesTf(c);
      const parent = {
        section: sec, stem, id: c.id, hostActivates: activates, position: pp, boxBranch,
        display: kw(prop(c, 'Display')) ?? null, direction: dirOf(c),
        // Compose's USED layout direction: ComponentRenderer.kt :2189-2195 provides LocalLayoutDirection Rtl for a
        // Direction-RTL component and never re-provides Ltr, so a box is laid out RTL iff it or ANY ancestor declares
        // rtl — what the block Column actually places children with (and what the seam reads).
        composeRtl: (() => { for (let n = c; n; n = byId.get(n.slot?.parent)) if (String(kw(prop(n, 'Direction')) ?? '').toUpperCase() === 'RTL') return true; return false; })(),
        width: dp(prop(c, 'Width')), height: dp(prop(c, 'Height')),
        children: ch.length, oofChildren: oof.length,
        // in-flow siblings BEFORE the last out-of-flow child: the flow-cursor caveat of the slot's header.
        inFlowBeforeOof: ch.slice(0, ch.lastIndexOf(oof.at(-1))).filter((k) => !['ABSOLUTE', 'FIXED'].includes(posOf(k))).length,
        oofIds: oof.map((k) => k.id),
        oofInsets: oof.map((k) => INSETS.filter((t) => prop(k, t) !== undefined && prop(k, t) !== 'auto').join('+') || 'none'),
        oofWidthAuto: oof.map((k) => !has(k, 'Width') && !has(k, 'InlineSize')),
        oofTexts: oof.map((k) => (typeof k.text === 'string' ? k.text.slice(0, 24) : null)),
        // Per out-of-flow child: declared size and block offset, and its class under the mount (see classify()).
        oofGeom: oof.map((k) => ({ w: dp(prop(k, 'Width')), h: dp(prop(k, 'Height')), top: dp(prop(k, 'Top')), left: dp(prop(k, 'Left')) })),
        padding: ['PaddingTop', 'PaddingRight', 'PaddingBottom', 'PaddingLeft'].map((t) => dp(prop(c, t)) ?? 0),
        inFlowAfterFirstOof: ch.slice(ch.indexOf(oof[0]) + 1).filter((k) => !['ABSOLUTE', 'FIXED'].includes(posOf(k))).length,
        cells: { web: cellOf(sec, stem, 'web'), ios: cellOf(sec, stem, 'ios'), android: cellOf(sec, stem, 'android') },
      };
      out.parents.push(parent);
    }
  }
}

// classify(): what PositionedParentFlowSlot.mount changes for this parent's out-of-flow children versus the plain
// Column loop (each class is a reason a picture can move; 'identity' = none applies, so the pixels should not move):
//   STACK   — an out-of-flow child after another out-of-flow child of non-zero declared height: today it sits at
//             cursor Σ(previous heights) + top; mounted, at top (CSS 2.1 §10.6.4 / css-position-3 §3.1).
//   CLAMP   — a child declared wider/taller than the parent's declared box: the Column clamps its size today
//             (remaining main-axis space, maxWidth); the mount measures it unbounded (css-position-3 §2.1 overflow).
//   AUTO-W  — a width-auto child: Column stretch/fit today vs §10.3.7 shrink-to-fit mounted.
//   AUTO-H  — an auto-height parent: its height loses the children's footprints (they report 0×0).
//   INFLOW  — an in-flow sibling after the first out-of-flow child moves up by the footprints above it.
//   RTL     — the parent's used direction is rtl: the RTL Column places the 0-wide slot at its END edge.
const classify = (p) => {
  const cls = [];
  const g = p.oofGeom;
  if (g.some((x, i) => i > 0 && g.slice(0, i).some((y) => (y.h ?? 1) > 0))) cls.push('STACK');
  if (g.some((x) => (p.width != null && x.w != null && x.w + (x.left ?? 0) > p.width + 0.5)
    || (p.height != null && x.h != null && x.h > p.height + 0.5))) cls.push('CLAMP');
  if (p.oofWidthAuto.some(Boolean)) cls.push('AUTO-W');
  if (p.height == null) cls.push('AUTO-H');
  if (p.inFlowAfterFirstOof > 0) cls.push('INFLOW');
  if (p.direction === 'RTL' || p.composeRtl) cls.push(p.direction === 'RTL' ? 'RTL' : 'RTL(compose-only)');
  return cls.length ? cls : ['identity'];
};
for (const p of out.parents) p.classes = classify(p);

// ── read-out ──
const fmt = (p) => `[${p.classes.join(',')}] ${p.section}/${p.stem.replace(`wpt__${p.section}__`, '')} ${p.id} pos=${p.position} disp=${p.display ?? '-'} dir=${p.direction} W=${p.width ?? 'auto'} H=${p.height ?? 'auto'} kids=${p.children} oof=${p.oofChildren} inFlowBefore=${p.inFlowBeforeOof} insets=[${p.oofInsets.join(',')}] geom=[${p.oofGeom.map((x) => `${x.w ?? 'auto'}x${x.h ?? 'auto'}@${x.left ?? '-'},${x.top ?? '-'}`).join(' ')}] pad=${p.padding.join('/')} inFlowAfter=${p.inFlowAfterFirstOof} widthAuto=[${p.oofWidthAuto.map((x) => (x ? 'A' : 'd')).join('')}] | android ${p.cells.android ?? '—'} · ios ${p.cells.ios ?? '—'} · web ${p.cells.web ?? '—'}`;
const column = out.parents.filter((p) => !p.boxBranch);
const inactive = column.filter((p) => !p.hostActivates);
const active = column.filter((p) => p.hostActivates);
const docsOf = (list) => [...new Set(list.map((p) => `${p.section}/${p.stem}`))];
console.log(`run ${run}: ${out.docs} per-test IR documents, host INACTIVE on ${out.docsHostInactive}`);
console.log(`Column-loop containing-block parents (ABSOLUTE|FIXED|STICKY, ≥1 out-of-flow child, not the Box branch): ${column.length} in ${docsOf(column).length} docs`);
console.log(`  host ACTIVE (slot mounted today): ${active.length} parents in ${docsOf(active).length} docs; ≥2 oof: ${active.filter((p) => p.oofChildren >= 2).length}`);
console.log(`  host INACTIVE (slot NOT mounted today — the change): ${inactive.length} parents in ${docsOf(inactive).length} docs; ≥2 oof (stack shape): ${inactive.filter((p) => p.oofChildren >= 2).length} parents in ${docsOf(inactive.filter((p) => p.oofChildren >= 2)).length} docs`);
console.log(`Box-branch parents (transform/OOF CB, unaffected): ${out.parents.length - column.length}`);
console.log('\n== (A) host-INACTIVE parents — every pair the change mounts (the blast radius) ==');
for (const p of inactive) console.log('  ' + fmt(p));
console.log('\n== (B) host-ACTIVE parents — already mounted today (the device evidence for what the mount does) ==');
for (const p of active) console.log('  ' + fmt(p));
console.log('\n== (C) RTL Column-loop parents (any host state; CSS direction or Compose used direction) ==');
for (const p of column.filter((q) => q.direction === 'RTL' || q.composeRtl)) console.log('  ' + (p.hostActivates ? 'ACTIVE   ' : 'INACTIVE ') + fmt(p));
console.log('\n== (D) affected documents (host inactive) and their cells at ' + run + ' ==');
for (const d of docsOf(inactive)) {
  const ps = inactive.filter((p) => `${p.section}/${p.stem}` === d);
  console.log(`  ${d}: parents ${ps.length} (≥2 oof ${ps.filter((p) => p.oofChildren >= 2).length}) classes {${[...new Set(ps.flatMap((p) => p.classes))].join(',')}} | android ${ps[0].cells.android ?? '—'} · ios ${ps[0].cells.ios ?? '—'} · web ${ps[0].cells.web ?? '—'}`);
}
// ── (E) carrier sets per unit (capture stems; the wire is unchanged by both units — a runtime-only lane) ──
//   U1 = mount for host-INACTIVE, non-RTL parents: a document is a CARRIER (its android capture is expected to move)
//        when one of those parents is STACK-class; it is a CONTROL (expected byte-identical) when every reached parent is
//        identity / CLAMP / AUTO only — the brief §5 argues each CLAMP / AUTO case.
//   U2 = RTL physical-left origin for every Column-loop parent laid out RTL, any host state: every such document is a
//        carrier. web and iOS: no file of either runtime changes, so no capture carrier there; no wire carrier.
const stemOf = (p) => `wpt__${p.section}__${p.stem.replace(`wpt__${p.section}__`, '')}`;
const u1 = inactive.filter((p) => !(p.direction === 'RTL' || p.composeRtl));
const u1carriers = [...new Set(u1.filter((p) => p.classes.includes('STACK')).map(stemOf))].sort();
const u1controls = [...new Set(u1.map(stemOf))].filter((s) => !u1carriers.includes(s)).sort();
const u2carriers = [...new Set(column.filter((p) => p.direction === 'RTL' || p.composeRtl).map(stemOf))].sort();
console.log('\n== (E) carrier sets (android capture stems; web/ios: none; wire: none) ==');
console.log(`  U1 reach: ${u1.length} parents / ${u1.reduce((n, p) => n + p.oofChildren, 0)} out-of-flow children / ${docsOf(u1).length} docs`);
console.log(`  U1 carriers (STACK): ${u1carriers.length}  ${u1carriers.join(' ')}`);
console.log(`  U1 controls (identity/CLAMP/AUTO only): ${u1controls.length}  ${u1controls.join(' ')}`);
const u2 = column.filter((p) => p.direction === 'RTL' || p.composeRtl);
console.log(`  U2 reach: ${u2.length} parents / ${u2.reduce((n, p) => n + p.oofChildren, 0)} children / ${u2carriers.length} docs (host-inactive ${u2.filter((p) => !p.hostActivates).length} parents)`);
console.log(`  U2 carriers: ${u2carriers.length}  ${u2carriers.join(' ')}`);
console.log(`  union android carriers: ${[...new Set([...u1carriers, ...u2carriers])].length}`);
if (jsonOut) writeFileSync(jsonOut, JSON.stringify(out, null, 1));
