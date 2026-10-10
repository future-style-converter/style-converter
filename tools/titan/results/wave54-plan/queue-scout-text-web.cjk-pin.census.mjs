#!/usr/bin/env node
// tools/titan/results/wave54-plan/queue-scout-text-web.cjk-pin.census.mjs
//
// One-line census behind queue-scout-text-web.md §C row "4(f) hanging punctuation": how many per-test IR documents
// carry a component `text` with NO whitespace that holds a CJK ideograph / kana / CJK punctuation (a run UAX #14 lets
// wrap between ideographs, which iOS's Round-4 `singleLineText` proxy — Renderer/ComponentRenderer.swift
// `!displayText.contains { $0.isWhitespace }` — pins to ONE line box anyway), and their iOS cells.
// READ-ONLY over a gate run. Usage: node queue-scout-text-web.cjk-pin.census.mjs [run-id=wave53-final]
import fs from 'node:fs';
import path from 'node:path';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';
const runId = process.argv[2] ?? 'wave53-final';
const runDir = resolveRunDir(runId);
const run = loadRun(runDir);
const CJK = /[　-〿぀-ヿ㐀-䶿一-鿿豈-﫿＀-￯]/u;
let docs = 0, P = 0, F = 0, U = 0; const rows = [];
for (const sec of Object.keys(run.sections).sort()) {
  const irDir = path.join(runDir, 'sections', sec, 'per-test-ir');
  if (!fs.existsSync(irDir) || !run.sections[sec].hasManifest) continue;
  const m = JSON.parse(fs.readFileSync(path.join(runDir, 'sections', sec, 'manifest.json'), 'utf8'));
  const idx = new Map(Object.keys(m.wpt?.results ?? {}).map((k) => ['wpt__' + k.replace(/^css\//, '').replace(/\.[a-z]+$/, '').split('/').join('__'), k]));
  for (const f of fs.readdirSync(irDir).filter((x) => x.endsWith('.json'))) {
    const doc = JSON.parse(fs.readFileSync(path.join(irDir, f), 'utf8'));
    const hits = (doc.components ?? []).filter((c) => typeof c.text === 'string' && c.text.length > 1 && !/\s/u.test(c.text) && CJK.test(c.text));
    if (!hits.length) continue;
    docs++;
    const key = idx.get(f.replace(/\.json$/, ''));
    const x = key && run.sections[sec].cells.get(`${key}|ios`);
    if (!x) U++; else if (x.pass) P++; else F++;
    rows.push(`${(key ?? f).replace(/^css\//, '').padEnd(72)} ios ${x ? (x.pass ? 'P ' : 'f ') + x.ssim : '—'}  (${hits.length} space-less CJK text(s), e.g. ${JSON.stringify(hits[0].text.slice(0, 12))})`);
  }
}
console.log(`run ${runId}: ${docs} documents carry a space-less CJK text; ios cells ${P} P / ${F} f / ${U} unscored`);
for (const r of rows.sort()) console.log(r);
