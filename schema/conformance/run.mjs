#!/usr/bin/env node
// schema/conformance/run.mjs — IR v1 conformance runner.
//
// Validates IR documents against schema/ir-v1.schema.json (JSON Schema
// draft 2020-12 via ajv, a tools-workspace dependency hoisted to the root
// node_modules):
//
//   node schema/conformance/run.mjs           # golden fixtures + out/tmpOutput.json (if present)
//   node schema/conformance/run.mjs --emit    # run the converter on fixtures/visual-test.json first,
//                                             # then validate the fresh artifact + the goldens
//
// Exit code is non-zero on ANY violation, with a precise per-error path
// report (file → JSON Pointer → message) so CI logs point at the byte.

import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath, pathToFileURL } from 'node:url';
// ajv's dedicated 2020-12 entry point — the default `ajv` export only
// speaks draft-07 and would reject the schema's $schema declaration.
import Ajv2020 from 'ajv/dist/2020.js';

// Resolve everything relative to this file so the runner works from any cwd.
const HERE = path.dirname(fileURLToPath(import.meta.url));
export const REPO_ROOT = path.resolve(HERE, '..', '..');
export const SCHEMA_PATH = path.join(REPO_ROOT, 'schema', 'ir-v1.schema.json');
export const FIXTURES_DIR = path.join(HERE, 'fixtures');
const ARTIFACT = path.join(REPO_ROOT, 'out', 'tmpOutput.json');

/** Compile the IR v1 schema into a reusable ajv validate function. */
export function makeValidator() {
  // allErrors so a single run reports every violation, not just the first;
  // strict mode keeps the schema itself honest (typos in keywords fail loudly).
  const ajv = new Ajv2020({ allErrors: true, strict: true });
  const schema = JSON.parse(readFileSync(SCHEMA_PATH, 'utf8'));
  return ajv.compile(schema);
}

/**
 * Validate one parsed document. Returns { ok, errors } where each error is
 * { path, message } — path is the ajv instancePath (JSON Pointer into the doc).
 */
export function validateDocument(validate, doc) {
  const ok = validate(doc);
  if (ok) return { ok: true, errors: [] };
  const errors = (validate.errors ?? []).map((e) => ({
    path: e.instancePath || '/',
    // Include the offending keyword + params for actionable reports
    // (e.g. additionalProperties tells you WHICH extra key was found).
    message: `${e.message}${e.params && e.params.additionalProperty ? ` (key: ${e.params.additionalProperty})` : ''} [${e.keyword}]`,
  }));
  return { ok: false, errors };
}

/** Validate a JSON file on disk; prints a per-error report. Returns boolean. */
function validateFile(validate, filePath) {
  const rel = path.relative(REPO_ROOT, filePath);
  let doc;
  try {
    doc = JSON.parse(readFileSync(filePath, 'utf8'));
  } catch (err) {
    console.error(`FAIL ${rel} — unparseable JSON: ${err.message}`);
    return false;
  }
  const { ok, errors } = validateDocument(validate, doc);
  if (ok) {
    console.log(`ok   ${rel}`);
    return true;
  }
  console.error(`FAIL ${rel} — ${errors.length} violation(s):`);
  for (const e of errors) console.error(`     ${rel}#${e.path}: ${e.message}`);
  return false;
}

/** --emit: run the converter on fixtures/visual-test.json → out/tmpOutput.json. */
function emitArtifact() {
  const env = { ...process.env };
  // Pin Java 21 on macOS dev machines (converter toolchain requirement);
  // CI runners already export a Java 21 JAVA_HOME via setup-java.
  if (process.platform === 'darwin') {
    try {
      env.JAVA_HOME = execFileSync('/usr/libexec/java_home', ['-v', '21'], { encoding: 'utf8' }).trim();
    } catch {
      // Fall through — whatever JAVA_HOME is already set will be used.
    }
  }
  const gradlew = path.join(REPO_ROOT, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
  console.log('emit: ./gradlew :converter:run — converting fixtures/visual-test.json → out/tmpOutput.json');
  execFileSync(
    gradlew,
    [':converter:run', '-q', '--args=convert --from css --to ir -i fixtures/visual-test.json -o out'],
    { cwd: REPO_ROOT, env, stdio: 'inherit' },
  );
}

function main() {
  const emit = process.argv.includes('--emit');
  if (emit) emitArtifact();

  const validate = makeValidator();
  let allOk = true;

  // (a) every golden fixture MUST validate — they define the contract.
  const fixtureFiles = readdirSync(FIXTURES_DIR)
    .filter((f) => f.endsWith('.json'))
    .sort()
    .map((f) => path.join(FIXTURES_DIR, f));
  if (fixtureFiles.length === 0) {
    console.error(`FAIL — no golden fixtures found in ${FIXTURES_DIR}`);
    process.exit(1);
  }
  for (const f of fixtureFiles) allOk = validateFile(validate, f) && allOk;

  // (b) the real converter artifact, when present (always present in --emit mode).
  if (existsSync(ARTIFACT)) {
    allOk = validateFile(validate, ARTIFACT) && allOk;
  } else if (emit) {
    console.error('FAIL — --emit ran but out/tmpOutput.json was not produced');
    allOk = false;
  } else {
    console.log('skip out/tmpOutput.json (not present — run with --emit to generate it)');
  }

  if (!allOk) {
    console.error('\nIR v1 conformance: FAILED');
    process.exit(1);
  }
  console.log('\nIR v1 conformance: all documents valid');
}

// Only run main() when invoked as a CLI — run.test.mjs imports the exported
// helpers without triggering validation side effects.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
