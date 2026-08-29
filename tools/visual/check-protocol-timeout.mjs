#!/usr/bin/env node
// check-protocol-timeout.mjs — verify the round-75 puppeteer fix is REAL.
//
// doc-staleness-check.sh Pattern 10 used to pin the fix with
// `grep -q protocolTimeout <file>` — which is satisfied by the fix's own
// explanatory COMMENT ("Round 75: bump protocolTimeout …"). Delete the
// actual `protocolTimeout:` option from puppeteer.launch and the check
// stays green: a regression guard satisfied by documentation of the thing
// it guards (LANE E finding 2, 2026-08-29). This checker asserts the
// CONFIGURATION instead: every `launch(...)` call in the file must pass a
// `protocolTimeout` property whose statically-evaluated value is at least
// the round-75 floor (5*60*1000 ms) — measured on the source with comments
// and string literals blanked, so prose can never vouch for code.
//
// CLI:  node tools/visual/check-protocol-timeout.mjs [--min-ms N]
//         [--expect-launch] file.mjs [more files…]
//   --min-ms N        floor in milliseconds (default 300000 = 5 min)
//   --expect-launch   fail a file containing NO launch() call — used by
//                     Pattern 10 because these files' whole job is to
//                     launch puppeteer; a refactor that moves the call
//                     away must be re-reviewed, not silently passed.
// Exit 0 = every file verified; exit 1 = any problem (listed on stderr).
//
// Tested by check-protocol-timeout.test.mjs (node --test), including the
// exact comment-only mutation that fooled the grep.

import { readFileSync } from 'node:fs';

// ── Source scrubber ─────────────────────────────────────────────────────────
// Blank the INTERIOR of comments and string literals with spaces, keeping
// every other byte (and all newlines) in place so downstream offsets are
// meaningful. A tiny state machine, not a real JS lexer — it handles the
// constructs these tool files actually use: // line comments, /* block */
// comments, '…' "…" strings with \-escapes, and `…` templates including
// nested `${ … }` interpolations (tracked with a depth counter so a quote
// inside an interpolation doesn't desync the scan). Regex literals are NOT
// modelled; a `/…'…/` regex could desync it, which the tests accept as out
// of scope for these two capture harness files.
export function stripCommentsAndStrings(source) {
  const out = source.split('');                      // mutable char buffer
  let i = 0;                                         // scan cursor
  // template-interpolation stack: each entry is the brace depth inside one
  // `${ … }` so nested templates unwind correctly.
  const tmpl = [];
  while (i < source.length) {
    const c = source[i], d = source[i + 1];
    if (c === '/' && d === '/') {                    // line comment → blank to EOL
      while (i < source.length && source[i] !== '\n') out[i++] = ' ';
    } else if (c === '/' && d === '*') {             // block comment → blank to */
      out[i++] = ' '; out[i++] = ' ';
      while (i < source.length && !(source[i] === '*' && source[i + 1] === '/')) {
        if (source[i] !== '\n') out[i] = ' ';        // keep newlines for line math
        i++;
      }
      if (i < source.length) { out[i++] = ' '; out[i++] = ' '; } // the closing */
    } else if (c === "'" || c === '"') {             // plain string → blank interior
      i++;                                           // keep the opening quote
      while (i < source.length && source[i] !== c && source[i] !== '\n') {
        if (source[i] === '\\') { out[i++] = ' '; }  // blank escape lead-in
        if (i < source.length) out[i++] = ' ';       // blank escaped/plain char
      }
      if (i < source.length) i++;                    // keep the closing quote
    } else if (c === '`') {                          // template → blank until ` or ${
      i++;                                           // keep the opening backtick
      while (i < source.length) {
        if (source[i] === '\\') { out[i] = ' '; out[i + 1] = ' '; i += 2; continue; }
        if (source[i] === '`') { i++; break; }       // template closed
        if (source[i] === '$' && source[i + 1] === '{') {
          tmpl.push(0);                              // enter interpolation: code again
          i += 2; break;                             // outer loop scans the code
        }
        if (source[i] !== '\n') out[i] = ' ';        // blank literal template text
        i++;
      }
    } else if (tmpl.length && c === '{') {           // brace inside interpolation code
      tmpl[tmpl.length - 1]++; i++;
    } else if (tmpl.length && c === '}') {           // maybe the interpolation's end
      if (tmpl[tmpl.length - 1] === 0) {
        tmpl.pop(); i++;                             // back inside template text
        while (i < source.length) {                  // resume blanking template text
          if (source[i] === '\\') { out[i] = ' '; out[i + 1] = ' '; i += 2; continue; }
          if (source[i] === '`') { i++; break; }
          if (source[i] === '$' && source[i + 1] === '{') { tmpl.push(0); i += 2; break; }
          if (source[i] !== '\n') out[i] = ' ';
          i++;
        }
      } else { tmpl[tmpl.length - 1]--; i++; }
    } else i++;                                      // ordinary code byte
  }
  return out.join('');
}

// ── Launch-call extraction ─────────────────────────────────────────────────
// On the SCRUBBED source, find every `launch(` (bare or `.launch(`) and
// capture its balanced-paren argument text. Balancing is safe post-scrub:
// no paren can hide inside a comment or string any more.
export function findLaunchCalls(stripped) {
  const calls = [];
  const re = /\blaunch\s*\(/g;                       // word-boundary: skips relaunch()
  let m;
  while ((m = re.exec(stripped)) !== null) {
    let depth = 1;                                   // we are past the opening (
    let j = m.index + m[0].length;
    while (j < stripped.length && depth > 0) {       // walk to the matching )
      if (stripped[j] === '(') depth++;
      else if (stripped[j] === ')') depth--;
      j++;
    }
    // Unbalanced parens mean the scrub desynced — surface it, don't guess.
    if (depth !== 0) throw new Error(`unbalanced parens in launch() at offset ${m.index}`);
    calls.push({
      offset: m.index,                               // for error messages
      line: stripped.slice(0, m.index).split('\n').length, // 1-based line
      args: stripped.slice(m.index + m[0].length, j - 1),  // inside the parens
    });
  }
  return calls;
}

// ── Value evaluation ───────────────────────────────────────────────────────
// Extract the expression assigned to `protocolTimeout:` inside one call's
// args (up to the next top-level `,` or `}`) and evaluate it IF it is pure
// numeric arithmetic (digits, `_` separators, + - * / ( ) . whitespace).
// Anything else — an identifier, a call — returns null: "cannot verify" is
// a FAILURE at the call site, never a silent pass (repo hard rule).
export function evalTimeoutExpr(args) {
  const m = /\bprotocolTimeout\s*:/.exec(args);
  if (!m) return { present: false, value: null };    // option not passed at all
  let j = m.index + m[0].length, depth = 0, expr = '';
  while (j < args.length) {                          // slice one object-literal value
    const c = args[j];
    if (c === '(' || c === '[' || c === '{') depth++;
    else if (c === ')' || c === ']') depth--;
    else if (c === '}') { if (depth === 0) break; depth--; }
    else if (c === ',' && depth === 0) break;        // next property begins
    expr += c; j++;
  }
  // Charset gate before evaluating — no identifiers, no side effects. The
  // Function() below then only ever sees literal arithmetic.
  if (!/^[\d_\s+*/().-]+$/.test(expr.trim()) || expr.trim() === '') {
    return { present: true, value: null, expr: expr.trim() };
  }
  try {
    // Safe by the charset gate: evaluates e.g. `5 * 60 * 1000` or `300_000`.
    const v = new Function(`"use strict"; return (${expr});`)();
    return { present: true, value: Number.isFinite(v) ? v : null, expr: expr.trim() };
  } catch {                                          // e.g. `--` parse garbage
    return { present: true, value: null, expr: expr.trim() };
  }
}

// ── Per-file verdict ───────────────────────────────────────────────────────
// Pure so tests can feed synthetic sources. Returns { launches, problems[] };
// ok ⇔ problems is empty (and launches>0 when expectLaunch).
export function checkSource(source, { minMs = 300000, expectLaunch = false } = {}) {
  const calls = findLaunchCalls(stripCommentsAndStrings(source));
  const problems = [];
  if (expectLaunch && calls.length === 0) {
    // The file's job is to launch puppeteer — a vanished call means the
    // fix moved or died; either way a human must look, not the grep.
    problems.push('no launch() call found but --expect-launch was set');
  }
  for (const c of calls) {
    const { present, value, expr } = evalTimeoutExpr(c.args);
    if (!present) {
      // The round-75 regression itself: option missing → puppeteer default.
      problems.push(`launch() at line ${c.line} does not pass protocolTimeout`);
    } else if (value === null) {
      // Present but not statically checkable — fail loudly per repo rule.
      problems.push(`launch() at line ${c.line}: protocolTimeout value "${expr}" is not statically verifiable arithmetic`);
    } else if (value < minMs) {
      // Explicit but below the round-75 floor — the same hang, hand-rolled.
      problems.push(`launch() at line ${c.line}: protocolTimeout ${value}ms is below the required ${minMs}ms floor`);
    }
  }
  return { launches: calls.length, problems };
}

// ── CLI ────────────────────────────────────────────────────────────────────
// Invoked by doc-staleness-check.sh Pattern 10. Import-safe: only runs when
// executed directly (node --test imports this module without side effects).
if (import.meta.url === `file://${process.argv[1]}`) {
  const argv = process.argv.slice(2);
  const expectLaunch = argv.includes('--expect-launch'); // flag, see header
  const minIdx = argv.indexOf('--min-ms');               // optional floor override
  const minMs = minIdx >= 0 ? Number(argv[minIdx + 1]) : 300000;
  // Positional args = files; drop the flag tokens (and --min-ms's value).
  // The minIdx guard matters: with --min-ms ABSENT, minIdx is -1 and a bare
  // `i !== minIdx + 1` becomes `i !== 0` — silently dropping the FIRST file.
  // Found by the adversarial review; with one file passed and no flag, the
  // checker verified nothing and exited 0.
  const files = argv.filter((a, i) => !a.startsWith('--') && (minIdx < 0 || i !== minIdx + 1));
  // Zero files is a broken invocation, not a clean pass — the same
  // check-that-cannot-fail doctrine as everywhere else in this pipeline.
  if (files.length === 0) {
    console.error('✗ check-protocol-timeout: no input files given — nothing was checked');
    process.exit(2);
  }
  let failed = 0;                                        // process-wide verdict
  for (const f of files) {
    const { launches, problems } = checkSource(readFileSync(f, 'utf8'), { minMs, expectLaunch });
    if (problems.length) {
      failed = 1;                                        // any problem fails the run
      for (const p of problems) console.error(`✗ ${f}: ${p}`);
    } else {
      console.log(`✓ ${f}: ${launches} launch() call(s) verified (protocolTimeout ≥ ${minMs}ms)`);
    }
  }
  process.exit(failed);                                  // 0 ok · 1 regression
}
