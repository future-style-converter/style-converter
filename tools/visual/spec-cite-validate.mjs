#!/usr/bin/env node
//
// spec-cite-validate.mjs — the spec-citation gate (retro 2026-09-05, skeptic
// S6 → lane F4).
//
// WHY THIS EXISTS. Every extractor/applier in the four trees names the CSS
// spec section it mirrors ("css-backgrounds-3 §2.6"). Retro lane P2d
// standardised ~900 of those on the drafts.csswg.org Editor's-Draft (ED)
// numbering, and skeptic S6 then found 27 (spec, §) pairs still naming
// sections that do not exist in those EDs, plus 7 NEW wrong cites that a
// comment-only lane introduced the same day — nothing compiled or tested a
// citation, so the sweep could regress silently. This script makes the
// citations machine-checked: every "<spec> §N(.N)*" (or "<spec> section N")
// in runtime / tooling / doc text must name a section that exists in the ED
// table of contents vendored in tools/visual/spec-sections.json.
//
// USAGE
//   node tools/visual/spec-cite-validate.mjs                 scan the repo; exit 1 on any unknown section
//   node tools/visual/spec-cite-validate.mjs --include-docs  also scan docs/*.md + README/CLAUDE/CONTRIBUTING/SECURITY
//   node tools/visual/spec-cite-validate.mjs --include-data  also scan fixtures/**.json and tools/titan/results/
//   node tools/visual/spec-cite-validate.mjs --json          machine-readable report on stdout
//   node tools/visual/spec-cite-validate.mjs --strict-unknown-spec
//                                                            a citation of a spec with no vendored ToC is a failure too
//   node tools/visual/spec-cite-validate.mjs --rebuild <dir> [--fetched YYYY-MM-DD]
//                                                            regenerate spec-sections.json from a directory of cached
//                                                            ED HTML files named <spec>-ed.html (bikeshed markup)
//
// SCOPE AND LIMITS — stated, not hidden:
//   * Only citations that carry the spec name ON THE SAME LINE are checked; a
//     bare "§3.6" whose spec name sits on the previous line cannot be
//     attributed to a spec and is not checked. Same-line continuations
//     ("css-backgrounds-3 §3.2 / §3.3 / §6.1") ARE attributed to the spec
//     that opened the run.
//   * Specs without a vendored ToC (css-logical-1, css-scroll-snap-1, …) are
//     tallied as "not validated" — a warning by default, never a silent skip —
//     so the gap stays visible; --strict-unknown-spec turns it into exit 1.
//   * The vendored ToC is a dated snapshot (see `_meta.fetched`). An upstream
//     renumbering shows up as a wave of hits: that is the moment to --rebuild
//     and re-sweep, not to widen the check.
//   * A line containing the token `spec-cite-ignore` is skipped, and so is
//     everything between `spec-cite-ignore-begin` and `spec-cite-ignore-end`,
//     for prose that QUOTES a wrong citation on purpose (retro write-ups).
//   * `css-selectors-4` / `css-compositing-1` / `css-gap-decorations-1` are
//     validated under their upstream shortnames (SPEC_ALIASES).
//   * Step numbers are not sections: css-flexbox-1 "§9.2 step 3.E" is written
//     that way in the tree precisely because "§9.2.3" does not exist.
//
// EXIT CODES: 0 clean · 1 unknown section(s) (or unknown spec under
// --strict-unknown-spec) · 2 usage / IO error.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
// The vendored ToC lives beside this script so the gate has no network dependency.
export const SECTIONS_PATH = path.join(HERE, 'spec-sections.json');
// The repo root is two levels up (tools/visual/ → repo); callers may override for tests.
export const REPO_ROOT = path.resolve(HERE, '..', '..');

// ── citation grammar ────────────────────────────────────────────────────────
// Spec short names as the tree writes them: css-<name>-<level> (css-color-4,
// css-counter-styles-3, css-transforms-1 …) plus the non-"css-" modules the
// trees cite (filter-effects-1, compositing-1, selectors-4, mediaqueries-5,
// web-animations-1). Level digits are required so plain words never match.
const SPEC_NAME = String.raw`((?:css|css3)-[a-z0-9]+(?:-[a-z0-9]+)*-\d+|filter-effects-\d+|compositing-\d+|selectors-\d+|mediaqueries-\d+|web-animations-\d+)`;
// "<spec> §N.N", "<spec> §§N", "<spec> section N", "<spec> Sec. N" — the section
// number is digits joined by dots; a trailing letter/step ("§9.2 step 3.E",
// "§3.5.3's") is left to the following text.
const CITE_RE = new RegExp(String.raw`\b${SPEC_NAME}(?:\s*§+\s*|\s+[Ss]ection\s+|\s+[Ss]ec\.\s*)(\d+(?:\.\d+)*)`, 'gi'); // `i`: prose writes "CSS-Color-4" too
// Same-line continuation after a citation: a separator then another "§N.N"
// with NO spec name — it belongs to the spec that opened the run.
const CONT_RE = /^(\s*(?:[\/,;&+]|and|or|–|—|-|→)\s*§+\s*)(\d+(?:\.\d+)*)/;
// Short names the tree writes that differ from the drafts.csswg.org shortname
// the ToC is filed under: the selectors and compositing modules never carried
// a "css-" prefix upstream, and CSS Gap Decorations Level 1 is published as
// `css-gaps-1`. The alias resolves BEFORE lookup, so the citation text in the
// tree may keep its spelling while the check still applies.
export const SPEC_ALIASES = { 'css-selectors-4': 'selectors-4', 'css-compositing-1': 'compositing-1', 'css-gap-decorations-1': 'css-gaps-1' };
// Escape hatches for prose that quotes a wrong citation deliberately (retro
// write-ups that list the corrections): one line, or a whole block bounded by
// `spec-cite-ignore-begin` … `spec-cite-ignore-end`.
export const IGNORE_TOKEN = 'spec-cite-ignore';
export const IGNORE_BEGIN = 'spec-cite-ignore-begin';
export const IGNORE_END = 'spec-cite-ignore-end';

/**
 * Every (spec, section) citation on one line, continuations attributed to
 * the spec that opened them. `col` is the 0-based column of the citation.
 */
export function parseCitations(line) {
  const out = [];
  CITE_RE.lastIndex = 0;
  let m;
  while ((m = CITE_RE.exec(line)) !== null) {
    const written = m[1].toLowerCase();
    const spec = SPEC_ALIASES[written] ?? written;  // validate under the upstream shortname
    out.push({ spec, section: m[2], col: m.index });
    let pos = CITE_RE.lastIndex;               // walk the continuations from where the match ended
    let c;
    while ((c = CONT_RE.exec(line.slice(pos))) !== null) {
      out.push({ spec, section: c[2], col: pos + c[1].length });
      pos += c[0].length;
    }
    CITE_RE.lastIndex = pos;                   // resume the main scan after the continuations
  }
  return out;
}

// ── vendored ToC ────────────────────────────────────────────────────────────
/** Numeric section sort: "2.10" after "2.9", "10" after "9". */
export function compareSections(a, b) {
  const A = a.split('.').map(Number), B = b.split('.').map(Number);
  for (let i = 0; i < Math.max(A.length, B.length); i++) {
    const d = (A[i] ?? -1) - (B[i] ?? -1);   // a missing component sorts first ("2" before "2.1")
    if (d !== 0) return d;
  }
  return 0;
}

/** Load spec-sections.json → { meta, specs: Map<spec, Set<section>>, urls }. */
export function loadSections(file = SECTIONS_PATH) {
  const json = JSON.parse(fs.readFileSync(file, 'utf8'));
  const specs = new Map();
  for (const [spec, entry] of Object.entries(json.specs)) specs.set(spec, new Set(entry.sections));
  return { meta: json._meta, specs, raw: json };
}

/**
 * Existing sections that share the cited section's parent — the hint printed
 * beside a miss ("§3 has: 3.1, 3.2, 3.3, 3.4"), so the fix is a lookup, not a
 * re-fetch. A missing TOP-level number lists the top-level sections instead.
 */
export function siblingsOf(sectionSet, section) {
  const parts = section.split('.');
  const parent = parts.slice(0, -1).join('.');
  const depth = parts.length;
  const sibs = [...sectionSet].filter((s) => {
    const p = s.split('.');
    return p.length === depth && (parent === '' || s.startsWith(parent + '.'));
  }).sort(compareSections);
  return { parent, siblings: sibs };
}

/**
 * Check one text. Returns the misses (unknown sections), the tally of specs
 * with no vendored ToC, and how many citations were actually validated.
 */
export function checkText(text, specs, file = '<text>') {
  const hits = [];
  const unknownSpecs = new Map();
  let validated = 0;
  const lines = text.split('\n');
  let ignoring = false;                            // inside a spec-cite-ignore-begin … -end block
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line.includes(IGNORE_BEGIN)) { ignoring = true; continue; }
    if (line.includes(IGNORE_END)) { ignoring = false; continue; }
    if (ignoring || line.includes(IGNORE_TOKEN)) continue; // deliberate quote of a wrong cite
    if (!line.includes('§') && !/\b[Ss]ec(?:tion|\.)/.test(line)) continue; // cheap pre-filter
    for (const c of parseCitations(line)) {
      const set = specs.get(c.spec);
      if (!set) { unknownSpecs.set(c.spec, (unknownSpecs.get(c.spec) ?? 0) + 1); continue; }
      validated++;
      if (!set.has(c.section)) {
        hits.push({ file, line: i + 1, col: c.col, spec: c.spec, section: c.section, text: line.trim(), ...siblingsOf(set, c.section) });
      }
    }
  }
  return { hits, unknownSpecs, validated };
}

// ── tree walk ───────────────────────────────────────────────────────────────
// What the gate reads by default: the four runtimes, the converter, the
// harnesses, the tooling and the schema prose — the "runtime/tooling
// comments" the check was asked for. Two opt-in extensions, each stated
// rather than silently skipped (the summary line prints which are on):
//   --include-docs  docs/*.md + the top-level docs. They are prose that
//                   legitimately QUOTES wrong citations (retro write-ups
//                   list the corrections), so they need the ignore markers
//                   above before the gate can hold them; docs/BACKLOG.md
//                   §(f) carried 13 such quotes on 2026-09-05.
//   --include-data  fixtures/**.json `_comment` strings and the TITAN result
//                   snapshots — fixture text, owned by whoever owns the fixture.
export const SCAN_ROOTS = ['runtimes', 'converter', 'apps', 'tools', 'schema'];
export const DOCS_ROOTS = ['docs', 'README.md', 'CLAUDE.md', 'CONTRIBUTING.md', 'SECURITY.md'];
export const DATA_ROOTS = ['fixtures', 'tools/titan/results'];
// Directory basenames never descended into: dependencies, build output, the
// gitignored WPT mirror + run captures, PNG baselines and reports.
const EXCLUDE_DIRS = new Set(['node_modules', 'build', '.build', '.gradle', '.git', 'dist', '.kotlin',
  'screenshots', 'report', 'baseline', 'interaction-snapshots', 'wpt', 'runs', 'investigations', 'results']);
const EXTENSIONS = new Set(['.kt', '.swift', '.ts', '.tsx', '.js', '.mjs', '.cjs', '.sh', '.md', '.json', '.yml', '.yaml']);
// This gate's own files carry deliberately-wrong examples (tests, this header).
const SELF = /^(spec-cite-validate(\.test)?\.mjs|spec-sections\.json|package-lock\.json)$/;

function* walk(dir, includeData) {
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, ent.name);
    if (ent.isSymbolicLink()) continue;          // never follow links (the PR #126 node_modules lesson)
    if (ent.isDirectory()) {
      if (EXCLUDE_DIRS.has(ent.name) && !(includeData && ent.name === 'results')) continue;
      yield* walk(full, includeData);
    } else if (ent.isFile() && EXTENSIONS.has(path.extname(ent.name)) && !SELF.test(ent.name)) {
      yield full;
    }
  }
}

/** All files the gate scans under `root`, relative paths, sorted. */
export function listFiles(root = REPO_ROOT, { includeData = false, includeDocs = false } = {}) {
  const roots = [...SCAN_ROOTS, ...(includeDocs ? DOCS_ROOTS : []), ...(includeData ? DATA_ROOTS : [])];
  const files = [];
  for (const r of roots) {
    const full = path.join(root, r);
    if (!fs.existsSync(full)) continue;
    if (fs.statSync(full).isDirectory()) { for (const f of walk(full, includeData)) files.push(f); }
    else files.push(full);
  }
  return [...new Set(files)].map((f) => path.relative(root, f)).sort();
}

/** Scan a tree: aggregate hits, unknown-spec tally and per-spec validated counts. */
export function scanTree(root = REPO_ROOT, { includeData = false, includeDocs = false, sectionsFile = SECTIONS_PATH } = {}) {
  const { meta, specs } = loadSections(sectionsFile);
  const files = listFiles(root, { includeData, includeDocs });
  const hits = [];
  const unknownSpecs = new Map();
  let validated = 0;
  for (const rel of files) {
    let text;
    try { text = fs.readFileSync(path.join(root, rel), 'utf8'); } catch { continue; } // unreadable → skip, counted below
    const r = checkText(text, specs, rel);
    hits.push(...r.hits);
    validated += r.validated;
    for (const [s, n] of r.unknownSpecs) unknownSpecs.set(s, (unknownSpecs.get(s) ?? 0) + n);
  }
  return { meta, scope: { includeDocs, includeData }, files: files.length, validated, hits, unknownSpecs: Object.fromEntries([...unknownSpecs].sort()) };
}

// ── --rebuild: cached ED HTML → spec-sections.json ─────────────────────────
/**
 * Section numbers from a bikeshed-rendered spec: every `<span class="secno">`
 * (the ToC and the headings both carry one; the Set de-duplicates). Appendix
 * letters ("A") are not numbers and are not citations this gate checks.
 */
export function parseTocHtml(html) {
  const secs = new Set();
  for (const m of html.matchAll(/<span class="secno">\s*(\d+(?:\.\d+)*)\.?\s*<\/span>/g)) secs.add(m[1]);
  return [...secs].sort(compareSections);
}

// Cached-file basename → spec short name where they differ (the P2d cache
// named the transforms-1 ED `transforms1-ed.html`).
const FILE_TO_SPEC = { transforms1: 'css-transforms-1' };

export function rebuild(dir, fetched) {
  const specs = {};
  for (const f of fs.readdirSync(dir).sort()) {
    const m = /^(.*)-ed\.html$/.exec(f);
    if (!m) continue;                              // TR mirrors and non-spec pages are not the reference
    const spec = FILE_TO_SPEC[m[1]] ?? m[1];
    const sections = parseTocHtml(fs.readFileSync(path.join(dir, f), 'utf8'));
    if (sections.length < 5) { console.error(`[rebuild] ${f}: only ${sections.length} numbered headings — skipped (not bikeshed markup?)`); continue; }
    specs[spec] = { url: `https://drafts.csswg.org/${spec}/`, sections };
  }
  const meta = {
    description: 'Section numbers of the drafts.csswg.org Editor\'s Drafts the trees cite — the reference for tools/visual/spec-cite-validate.mjs. Numbers only (titles are one click away at `url`).',
    numbering: 'ED (drafts.csswg.org), the scheme retro P2d standardised the tree on; the W3C TR numbering differs for several of these specs (css-transforms-1 by two sections, css-backgrounds-3 border-image §5 vs §6, css-color-4 opacity §3.3 vs §2.x), so TR numbers are hits here by design.',
    fetched,
    source: 'drafts.csswg.org ED HTML fetched ' + fetched + ' — 26 files from the retro P2d/S6 spec cache (17:07) plus 42 fetched live by lane F4 the same day; css-color-3 and css-contain-3 pages are not bikeshed-numbered and are therefore absent (cited ×1 / ×4, reported as unvalidated)',
    regenerate: 'node tools/visual/spec-cite-validate.mjs --rebuild <dir of <spec>-ed.html> --fetched YYYY-MM-DD',
    specCount: Object.keys(specs).length,
  };
  // Compact, diff-friendly layout: one spec per line.
  const body = Object.entries(specs).map(([s, e]) => `    ${JSON.stringify(s)}: ${JSON.stringify(e)}`).join(',\n');
  return `{\n  "_meta": ${JSON.stringify(meta, null, 2).replace(/\n/g, '\n  ')},\n  "specs": {\n${body}\n  }\n}\n`;
}

// ── CLI ─────────────────────────────────────────────────────────────────────
function main(argv) {
  const args = [...argv];
  const has = (flag) => { const i = args.indexOf(flag); if (i < 0) return false; args.splice(i, 1); return true; };
  const opt = (flag) => { const i = args.indexOf(flag); if (i < 0) return undefined; const v = args[i + 1]; args.splice(i, 2); return v; };
  const rebuildDir = opt('--rebuild');
  const fetched = opt('--fetched');
  const json = has('--json');
  const includeData = has('--include-data');
  const includeDocs = has('--include-docs');
  const strictUnknown = has('--strict-unknown-spec');
  const root = opt('--root') ?? REPO_ROOT;
  if (args.length) { console.error(`unknown argument(s): ${args.join(' ')}`); return 2; }

  if (rebuildDir !== undefined) {
    if (!fetched || !/^\d{4}-\d{2}-\d{2}$/.test(fetched)) { console.error('--rebuild needs --fetched YYYY-MM-DD (the date the ED HTML was fetched)'); return 2; }
    if (!fs.existsSync(rebuildDir)) { console.error(`--rebuild: no such directory ${rebuildDir}`); return 2; }
    const out = rebuild(rebuildDir, fetched);
    fs.writeFileSync(SECTIONS_PATH, out);
    const n = Object.keys(JSON.parse(out).specs).length;
    console.log(`wrote ${path.relative(process.cwd(), SECTIONS_PATH)} — ${n} specs, fetched ${fetched}`);
    return 0;
  }

  if (!fs.existsSync(SECTIONS_PATH)) { console.error(`missing ${SECTIONS_PATH} — run --rebuild`); return 2; }
  const report = scanTree(root, { includeData, includeDocs });
  const unknownTotal = Object.values(report.unknownSpecs).reduce((a, b) => a + b, 0);
  const fail = report.hits.length > 0 || (strictUnknown && unknownTotal > 0);
  if (json) { console.log(JSON.stringify({ ...report, exit: fail ? 1 : 0 }, null, 2)); return fail ? 1 : 0; }

  for (const h of report.hits) {
    const hint = h.siblings.length ? `${h.parent ? '§' + h.parent : 'top level'} has: ${h.siblings.join(', ')}` : `nothing under ${h.parent ? '§' + h.parent : 'the top level'}`;
    console.log(`NO-SUCH-SECTION ${h.file}:${h.line}  ${h.spec} §${h.section} — not in the ED ToC (${hint})\n    ${h.text}`);
  }
  const scope = `runtimes+converter+apps+tools+schema${includeDocs ? '+docs' : ''}${includeData ? '+fixtures/results' : ''}`;
  console.log(`[spec-cite-validate] ${report.files} files scanned (${scope}) · ${report.validated} citations validated against ${report.meta.specCount} ED ToCs (fetched ${report.meta.fetched}) · ${report.hits.length} unknown section(s)`);
  if (unknownTotal) {
    const list = Object.entries(report.unknownSpecs).map(([s, n]) => `${s} ×${n}`).join(', ');
    console.log(`[spec-cite-validate] not validated (no vendored ToC${strictUnknown ? ' — FAILING under --strict-unknown-spec' : ''}): ${list}`);
  }
  return fail ? 1 : 0;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exitCode = main(process.argv.slice(2));
}
