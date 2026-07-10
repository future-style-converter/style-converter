#!/usr/bin/env node
// schema/conformance/run.mjs — IR conformance runner (v1 + v2).
//
// Validates IR documents against the two published contracts:
//   - schema/ir-v1.schema.json — the legacy nested wire (deprecation
//     window only; still emitted by `--emit-ir v1`)
//   - schema/ir-v2.schema.json — the flat-list slot/placement wire the
//     converter emits BY DEFAULT since the v2 freeze
//
//   node schema/conformance/run.mjs           # v1 goldens vs v1 schema, v2 goldens vs v2 schema,
//                                             # + out/tmpOutput.json vs v2 (if present)
//   node schema/conformance/run.mjs --emit    # run the converter on fixtures/visual-test.json first
//                                             # (default v2 emission), then validate everything
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
export const SCHEMA_V2_PATH = path.join(REPO_ROOT, 'schema', 'ir-v2.schema.json');
export const FIXTURES_DIR = path.join(HERE, 'fixtures');
export const FIXTURES_V2_DIR = path.join(HERE, 'fixtures', 'v2');
const ARTIFACT = path.join(REPO_ROOT, 'out', 'tmpOutput.json');

/** Compile the IR v1 schema into a reusable ajv validate function. */
export function makeValidator() {
  // allErrors so a single run reports every violation, not just the first;
  // strict mode keeps the schema itself honest (typos in keywords fail loudly).
  const ajv = new Ajv2020({ allErrors: true, strict: true });
  const schema = JSON.parse(readFileSync(SCHEMA_PATH, 'utf8'));
  return ajv.compile(schema);
}

/** Compile the IR v2 schema into a reusable ajv validate function. */
export function makeValidatorV2() {
  // Same ajv configuration as v1 — the two contracts differ in content,
  // not in validation strictness policy.
  const ajv = new Ajv2020({ allErrors: true, strict: true });
  const schema = JSON.parse(readFileSync(SCHEMA_V2_PATH, 'utf8'));
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
function validateFile(validate, filePath, label) {
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
    console.log(`ok   [${label}] ${rel}`);
    return true;
  }
  console.error(`FAIL [${label}] ${rel} — ${errors.length} violation(s):`);
  for (const e of errors) console.error(`     ${rel}#${e.path}: ${e.message}`);
  return false;
}

/** List golden .json files directly inside a directory (subdirs excluded). */
function goldenFiles(dir) {
  return readdirSync(dir, { withFileTypes: true })
    .filter((d) => d.isFile() && d.name.endsWith('.json'))
    .map((d) => d.name)
    .sort()
    .map((f) => path.join(dir, f));
}

/** --emit: run the converter on fixtures/visual-test.json → out/tmpOutput.json (v2 default). */
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
  console.log('emit: ./gradlew :converter:run — converting fixtures/visual-test.json → out/tmpOutput.json (IR v2)');
  execFileSync(
    gradlew,
    [':converter:run', '-q', '--args=convert --from css --to ir -i fixtures/visual-test.json -o out'],
    { cwd: REPO_ROOT, env, stdio: 'inherit' },
  );
}

function main() {
  const emit = process.argv.includes('--emit');
  if (emit) emitArtifact();

  const validateV1 = makeValidator();
  const validateV2 = makeValidatorV2();
  let allOk = true;

  // (a) every v1 golden MUST validate against the v1 schema — the legacy
  // contract stays checkable for the whole deprecation window.
  const v1Fixtures = goldenFiles(FIXTURES_DIR);
  if (v1Fixtures.length === 0) {
    console.error(`FAIL — no v1 golden fixtures found in ${FIXTURES_DIR}`);
    process.exit(1);
  }
  for (const f of v1Fixtures) allOk = validateFile(validateV1, f, 'v1') && allOk;

  // (b) every v2 golden MUST validate against the v2 schema — these
  // define the flat slot/placement contract (12 v1-mirrors + the
  // slot-composition and placement-claims goldens).
  const v2Fixtures = goldenFiles(FIXTURES_V2_DIR);
  if (v2Fixtures.length === 0) {
    console.error(`FAIL — no v2 golden fixtures found in ${FIXTURES_V2_DIR}`);
    process.exit(1);
  }
  for (const f of v2Fixtures) allOk = validateFile(validateV2, f, 'v2') && allOk;

  // (c) the real converter artifact, when present (always present in
  // --emit mode). Default emission is v2 since the freeze, so the fresh
  // artifact is held to the v2 contract.
  if (existsSync(ARTIFACT)) {
    allOk = validateFile(validateV2, ARTIFACT, 'v2') && allOk;
  } else if (emit) {
    console.error('FAIL — --emit ran but out/tmpOutput.json was not produced');
    allOk = false;
  } else {
    console.log('skip out/tmpOutput.json (not present — run with --emit to generate it)');
  }

  if (!allOk) {
    console.error('\nIR conformance: FAILED');
    process.exit(1);
  }
  console.log('\nIR conformance: all documents valid (v1 goldens + v2 goldens)');
}

// Only run main() when invoked as a CLI — run.test.mjs imports the exported
// helpers without triggering validation side effects.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
