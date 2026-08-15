//
// tools/titan/woff-to-ttf.test.mjs — wave-42 lane W9.
//
// Pins for the WOFF1→TTF host transcoder: the byte-level repackager, the
// candidate/naming/rewrite pure halves, and the batch pipeline. Two layers of
// evidence, on purpose:
//   * SYNTHETIC WOFFs built in-test pin every structural rule CI-safe (the
//     corpus under tools/wpt/ is a gitignored mirror CI never fetches);
//   * REAL corpus woffs (both flavors) get a full round-trip proof — a
//     SECOND, independent inflate of every table byte-compared against the
//     rebuilt file — and SKIP themselves when the corpus is absent, exactly
//     like svg-preraster.test.mjs's end-to-end case.
// Run with: node --test tools/titan/woff-to-ttf.test.mjs
//

import test from 'node:test';
import assert from 'node:assert/strict';
import { deflateSync, inflateSync } from 'node:zlib';
import { existsSync } from 'node:fs';
import { promises as fs } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  WOFF1_SIGNATURE, WOFF2_SIGNATURE, SFNT_CHECKSUM_MAGIC, sfntChecksum,
  woffToTtf, isWoffTranscodeCandidate, transcodedSrcFor, isTranscodedSrc,
  applyWoffTranscodeRewrite, collectWoffSources, transcodeWoffSources,
} from './woff-to-ttf.mjs';

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const WPT_DIR = join(REPO_ROOT, 'tools', 'wpt');

// ── Synthetic WOFF builder ──────────────────────────────────────────────────
//
// Builds a small, VALID WOFF1 from scratch so the structural pins never need
// the corpus. The wrapped font is not a renderable typeface — it is three
// tables with spec-correct checksums, which is all the CONTAINER math (the
// thing this module owns) can see.

/** A minimal 54-byte `head` table: sfnt version 1.0, a deliberately STALE
 *  nonzero checkSumAdjustment (the transcoder must recompute it), and the
 *  0x5F0F3CF5 magicNumber at its spec offset 12. */
function syntheticHead() {
  const head = Buffer.alloc(54);
  head.writeUInt32BE(0x00010000, 0);  // table version
  head.writeUInt32BE(0xdeadbeef, 8);  // checkSumAdjustment — STALE on purpose
  head.writeUInt32BE(0x5f0f3cf5, 12); // head magicNumber
  return head;
}

/** Assemble a WOFF1 from `[tag, data, { compress, badChecksum }]` triples.
 *  Checksums are computed by the spec rule (head over a zeroed adjustment)
 *  unless `badChecksum` asks for a wrong one; `compress` zlib-deflates the
 *  payload (only honoured when it actually shrinks, per WOFF §5). */
function buildWoff(tables, { flavor = 0x00010000, reserved = 0, totalSfntSize = null, mutate = null } = {}) {
  const entries = tables.map(([tag, data, o = {}]) => {
    const comp = o.compress ? deflateSync(data) : data;
    const stored = comp.length < data.length ? comp : data; // §5: compression must help
    let checkable = data;
    if (tag === 'head') { // head's directory checksum is over a ZEROED adjustment
      checkable = Buffer.from(data);
      checkable.writeUInt32BE(0, 8);
    }
    const checksum = o.badChecksum ? 0x12345678 : sfntChecksum(checkable);
    return { tag, data, stored, checksum };
  });
  // WOFF payloads start after the 44-byte header + 20-byte-per-table directory.
  let offset = 44 + entries.length * 20;
  for (const e of entries) {
    e.offset = offset;
    offset += (e.stored.length + 3) & ~3; // WOFF §4: table data is long-aligned
  }
  const woff = Buffer.alloc(offset);
  woff.writeUInt32BE(WOFF1_SIGNATURE, 0);
  woff.writeUInt32BE(flavor, 4);
  woff.writeUInt32BE(offset, 8);            // WOFF file length
  woff.writeUInt16BE(entries.length, 12);   // numTables
  woff.writeUInt16BE(reserved, 14);
  // The declared rebuilt size: sfnt header + 16-byte records + padded tables.
  const sfntSize = totalSfntSize ?? 12 + entries.length * 16 +
    entries.reduce((a, e) => a + ((e.data.length + 3) & ~3), 0);
  woff.writeUInt32BE(sfntSize, 16);
  entries.forEach((e, i) => {
    const at = 44 + i * 20;
    woff.write(e.tag, at, 4, 'latin1');
    woff.writeUInt32BE(e.offset, at + 4);
    woff.writeUInt32BE(e.stored.length, at + 8);  // compLength
    woff.writeUInt32BE(e.data.length, at + 12);   // origLength
    woff.writeUInt32BE(e.checksum, at + 16);      // origChecksum
    e.stored.copy(woff, e.offset);
  });
  if (mutate) mutate(woff); // corruption hook for the malformed-input pins
  return woff;
}

/** The default synthetic font: a compressible `name`, a stored `maxp`, and a
 *  `head` — tags in ascending order, as WOFF §4 requires of its directory. */
function defaultTables() {
  return [
    ['head', syntheticHead()],
    ['maxp', Buffer.from([0, 1, 0, 0, 0, 3])],
    // 200 repeated bytes deflate far below 200, exercising the inflate path.
    ['name', Buffer.alloc(200, 0x41), { compress: true }],
  ];
}

// ── The repackager: happy path ──────────────────────────────────────────────

test('woffToTtf rebuilds a valid sfnt: header fields, table bytes, checksums', () => {
  const tables = defaultTables();
  const r = woffToTtf(buildWoff(tables));
  assert.equal(r.extension, 'ttf');
  assert.equal(r.numTables, 3);
  assert.deepEqual(r.checksumMismatches, []);
  assert.equal(r.totalSfntSizeMatches, true);
  const f = r.font;
  // sfnt header: version at 0, numTables at 4, then the three binary-search
  // fields the OT spec derives from numTables (for 3 tables: floor(log2 3)=1,
  // searchRange 32, rangeShift 48-32=16).
  assert.equal(f.readUInt32BE(0), 0x00010000, 'sfntVersion');
  assert.equal(f.readUInt16BE(4), 3, 'numTables');
  assert.equal(f.readUInt16BE(6), 32, 'searchRange = 16·2^entrySelector');
  assert.equal(f.readUInt16BE(8), 1, 'entrySelector = floor(log2(numTables))');
  assert.equal(f.readUInt16BE(10), 16, 'rangeShift = 16·numTables − searchRange');
  // Directory: tag order preserved, offsets long-aligned and in-bounds, and
  // every table's bytes byte-identical to the pre-wrap originals (head's
  // adjustment field aside — pinned separately below).
  for (let i = 0; i < 3; i++) {
    const at = 12 + i * 16;
    const tag = f.toString('latin1', at, at + 4);
    const off = f.readUInt32BE(at + 8);
    const len = f.readUInt32BE(at + 12);
    assert.equal(tag, tables[i][0], 'directory keeps the WOFF tag order');
    assert.equal(off % 4, 0, `'${tag}' offset is long-aligned`);
    assert.ok(off >= 12 + 3 * 16 && off + len <= f.length, `'${tag}' data is in-bounds`);
    const original = tables[i][1];
    assert.equal(len, original.length, `'${tag}' length is the original length`);
    if (tag !== 'head') {
      assert.deepEqual(f.subarray(off, off + len), original, `'${tag}' bytes round-trip`);
    }
  }
});

test('head.checkSumAdjustment is RECOMPUTED so the whole file sums to B1B0AFBA', () => {
  const r = woffToTtf(buildWoff(defaultTables()));
  // The OT-spec identity: after adjustment, the file checksum equals the
  // magic exactly. The synthetic head carried a STALE 0xdeadbeef adjustment,
  // so this passing proves the field was recomputed, not copied.
  assert.equal(sfntChecksum(r.font), SFNT_CHECKSUM_MAGIC);
  const headOff = r.font.readUInt32BE(12 + 8); // first directory record is 'head'
  assert.notEqual(r.font.readUInt32BE(headOff + 8), 0xdeadbeef,
    'the stale adjustment must not survive the relayout');
});

test('flavor picks the honest extension: TrueType→ttf, OTTO→otf, unknown→throw', () => {
  assert.equal(woffToTtf(buildWoff(defaultTables(), { flavor: 0x00010000 })).extension, 'ttf');
  assert.equal(woffToTtf(buildWoff(defaultTables(), { flavor: 0x74727565 })).extension, 'ttf', "'true' is Apple TrueType");
  assert.equal(woffToTtf(buildWoff(defaultTables(), { flavor: 0x4f54544f })).extension, 'otf', "'OTTO' is CFF");
  // A closed table: an unknown flavor is not a font Android could load, so it
  // is a NAMED decline, never a guessed extension.
  assert.throws(() => woffToTtf(buildWoff(defaultTables(), { flavor: 0x12345678 })), /unsupported sfnt flavor/);
});

test('a stored (uncompressed) table and a compressed one both round-trip', () => {
  // defaultTables has one of each; this pins the equal-length STORED branch
  // explicitly: compLength == origLength must mean verbatim copy, no inflate.
  const stored = Buffer.from([9, 8, 7, 6, 5, 4, 3, 2]);
  const r = woffToTtf(buildWoff([['glyf', stored], ['head', syntheticHead()]].sort((a, b) => a[0] < b[0] ? -1 : 1)));
  const f = r.font;
  for (let i = 0; i < 2; i++) {
    const at = 12 + i * 16;
    if (f.toString('latin1', at, at + 4) === 'glyf') {
      const off = f.readUInt32BE(at + 8);
      assert.deepEqual(f.subarray(off, off + stored.length), stored);
    }
  }
});

// ── The repackager: declines (each a named, not generic, failure) ───────────

test('WOFF2 is rejected LOUDLY by name, never as generic corruption', () => {
  const woff2 = Buffer.alloc(48);
  woff2.writeUInt32BE(WOFF2_SIGNATURE, 0);
  assert.throws(() => woffToTtf(woff2), /WOFF2.*Brotli/s,
    'the message must name the format and why it is out of scope');
});

test('non-WOFF bytes are rejected as such', () => {
  assert.throws(() => woffToTtf(Buffer.alloc(100, 0x42)), /not a WOFF container/);
  assert.throws(() => woffToTtf(Buffer.from('short')), /shorter than the 44-byte/);
  assert.throws(() => woffToTtf(null), /shorter than the 44-byte/);
});

test('spec MUSTs are enforced: reserved≠0, compLength>origLength, zero tables', () => {
  // WOFF §3: reserved MUST be 0 and a conforming UA MUST reject otherwise —
  // matching the browsers keeps this hop from "fixing" what the ref refuses.
  assert.throws(() => woffToTtf(buildWoff(defaultTables(), { reserved: 7 })), /reserved/);
  // WOFF §5: compression MUST be omitted when it does not shrink the table.
  // 56 > head's origLength 54 while staying inside the file, so the §5 rule
  // (not the bounds check) is what must fire.
  assert.throws(() => woffToTtf(buildWoff(defaultTables(), {
    mutate: (w) => w.writeUInt32BE(56, 44 + 8), // first entry's compLength > origLength, in-bounds
  })), /exceeds original length/);
  const empty = Buffer.alloc(44);
  empty.writeUInt32BE(WOFF1_SIGNATURE, 0);
  empty.writeUInt32BE(0x00010000, 4);
  assert.throws(() => woffToTtf(empty), /zero font tables/);
});

test('a directory or payload that overruns the file is corruption, not a crash', () => {
  // Directory truncated: header says 3 tables but the file ends first.
  const truncated = buildWoff(defaultTables()).subarray(0, 50);
  assert.throws(() => woffToTtf(Buffer.from(truncated)), /overruns/);
  // Payload out of bounds: point the first table past EOF.
  assert.throws(() => woffToTtf(buildWoff(defaultTables(), {
    mutate: (w) => w.writeUInt32BE(w.length, 44 + 4), // first entry's offset = EOF
  })), /overruns/);
});

test('a corrupt zlib stream and a short inflate are named per-table', () => {
  // Flip bytes inside the compressed `name` payload (the third directory
  // entry) so inflate fails mid-stream.
  const woff = buildWoff(defaultTables());
  const nameOff = woff.readUInt32BE(44 + 2 * 20 + 4);
  woff.fill(0xff, nameOff, nameOff + 4);
  assert.throws(() => woffToTtf(woff), /'name' failed zlib inflate/);
  // Declared origLength ≠ what the stream inflates to.
  assert.throws(() => woffToTtf(buildWoff(defaultTables(), {
    mutate: (w) => w.writeUInt32BE(150, 44 + 2 * 20 + 12), // name's origLength 200→150…
  })), /'name' compressed length|'name' inflated to/,
  'either the §5 length rule or the inflate-length check must catch it');
});

test('a wrong DECLARED checksum is reported, not fatal (faithful rebuild wins)', () => {
  const r = woffToTtf(buildWoff([
    ['head', syntheticHead()],
    ['maxp', Buffer.from([0, 1, 0, 0, 0, 3]), { badChecksum: true }],
  ]));
  // The rebuild carries the DECLARED value verbatim (that is what the
  // original font said) and the mismatch is surfaced for the feeder's log.
  assert.deepEqual(r.checksumMismatches, ['maxp']);
  assert.equal(r.font.readUInt32BE(12 + 16 + 4), 0x12345678, 'directory keeps the declared value');
});

// ── The candidate gate + naming contract ────────────────────────────────────

test('isWoffTranscodeCandidate admits corpus-relative .woff and nothing else', () => {
  assert.equal(isWoffTranscodeCandidate('css/css-text/r/LinLibertine.woff'), true);
  assert.equal(isWoffTranscodeCandidate('r/A.WOFF'), true, 'extension is case-insensitive');
  assert.equal(isWoffTranscodeCandidate('  r/a.woff  '), true, 'the wire value is trimmed');
  // woff2 is declined BY NAME so a woff2 corpus never even reads the file —
  // the transcoder would reject the content anyway, but later and slower.
  assert.equal(isWoffTranscodeCandidate('r/a.woff2'), false);
  // Already-loadable containers need no stand-in.
  assert.equal(isWoffTranscodeCandidate('r/a.ttf'), false);
  assert.equal(isWoffTranscodeCandidate('r/a.otf'), false);
  // Containment, mirroring resolveFontFile: absolute/protocol paths name
  // files outside the corpus, which this hop must never read or write.
  assert.equal(isWoffTranscodeCandidate('/abs/a.woff'), false);
  assert.equal(isWoffTranscodeCandidate('https://x.test/a.woff'), false);
  for (const bad of [null, undefined, 42, '', '   ']) {
    assert.equal(isWoffTranscodeCandidate(bad), false, `bad input ${JSON.stringify(bad)}`);
  }
});

test('the transcode suffix is APPENDED, so the woff stem survives verbatim', () => {
  assert.equal(transcodedSrcFor('r/LinLibertine.woff', 'otf'), 'r/LinLibertine.woff.otf');
  assert.equal(transcodedSrcFor('r/DejaVu.woff', 'ttf'), 'r/DejaVu.woff.ttf');
  // The load-bearing consequence, same as svg-preraster's: substitution
  // (`DejaVu.ttf`) could collide with an authored sibling the corpus already
  // ships and silently deliver the wrong font.
  assert.notEqual(transcodedSrcFor('r/DejaVu.woff', 'ttf'), 'r/DejaVu.ttf');
});

test('isTranscodedSrc marks exactly the host-transcode double extension', () => {
  assert.equal(isTranscodedSrc('r/a.woff.ttf'), true);
  assert.equal(isTranscodedSrc('r/a.WOFF.OTF'), true);
  assert.equal(isTranscodedSrc('r/a.ttf'), false, 'an authored font is not a stand-in');
  assert.equal(isTranscodedSrc('r/a.woff'), false, 'the woff itself is not a stand-in');
  assert.equal(isTranscodedSrc(null), false);
});

// ── The wire rewrite ────────────────────────────────────────────────────────

test('applyWoffTranscodeRewrite rewrites ONLY faces whose transcode succeeded', () => {
  const doc = {
    irVersion: 2,
    fontFaces: [
      { family: 'test', src: 'r/lin.woff' },
      { family: 'test', src: 'r/lin.woff' },      // css-fonts-4 §4.1: many faces, one file
      { family: 'other', src: 'r/failed.woff' },  // NOT in the map — transcode failed
      { family: 'plain', src: 'r/authored.ttf' }, // already loadable, untouched
    ],
    components: [],
  };
  const applied = applyWoffTranscodeRewrite(doc, new Map([['r/lin.woff', 'r/lin.woff.otf']]));
  assert.deepEqual(applied, [{ src: 'r/lin.woff', fontSrc: 'r/lin.woff.otf' }],
    'reported once per distinct file, not once per face');
  assert.equal(doc.fontFaces[0].src, 'r/lin.woff.otf');
  assert.equal(doc.fontFaces[1].src, 'r/lin.woff.otf');
  // A failed transcode KEEPS its .woff so DocumentFontRegistry's extension
  // decline still fires — rewriting to a missing file would swap the precise
  // "Typeface cannot parse woff" for a misleading "hop did not deliver".
  assert.equal(doc.fontFaces[2].src, 'r/failed.woff');
  assert.equal(doc.fontFaces[3].src, 'r/authored.ttf');
});

test('applyWoffTranscodeRewrite is a no-op on face-free and malformed documents', () => {
  const m = new Map([['r/a.woff', 'r/a.woff.ttf']]);
  assert.deepEqual(applyWoffTranscodeRewrite({}, m), []);
  assert.deepEqual(applyWoffTranscodeRewrite({ fontFaces: 'nonsense' }, m), []);
  assert.deepEqual(applyWoffTranscodeRewrite(null, m), []);
});

test('collectWoffSources dedupes across documents and filters by the gate', () => {
  const srcsOf = (d) => d.srcs; // the injected walker, stubbed
  const docs = [
    { srcs: ['r/a.woff', 'r/b.ttf'] },
    { srcs: ['r/a.woff', 'r/c.woff2', '/abs/d.woff'] },
    { srcs: ['r/e.woff'] },
  ];
  assert.deepEqual(collectWoffSources(docs, srcsOf), ['r/a.woff', 'r/e.woff']);
});

// ── The batch pipeline (scratch corpus in a tmpdir) ─────────────────────────

test('transcodeWoffSources writes the sibling, caches by mtime, and stamps', async (t) => {
  const root = await fs.mkdtemp(join(tmpdir(), 'w9-corpus-'));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  await fs.mkdir(join(root, 'r'), { recursive: true });
  await fs.writeFile(join(root, 'r', 'syn.woff'), buildWoff(defaultTables()));
  const lines = [];
  const log = (m) => lines.push(m);
  const map = await transcodeWoffSources(['r/syn.woff'], { wptDir: root, log });
  assert.deepEqual([...map.entries()], [['r/syn.woff', 'r/syn.woff.ttf']]);
  const sibling = join(root, 'r', 'syn.woff.ttf');
  assert.ok(existsSync(sibling), 'the sfnt sibling must exist next to the woff');
  // The written bytes are a valid sfnt whose adjusted file checksum balances.
  assert.equal(sfntChecksum(await fs.readFile(sibling)), SFNT_CHECKSUM_MAGIC);
  assert.ok(lines.some((l) => /woff transcode r\/syn\.woff → r\/syn\.woff\.ttf/.test(l)),
    'the loud per-file stamp must name source and stand-in');
  // Second run: fresh sibling ⇒ CACHED, still in the map, still stamped.
  const lines2 = [];
  const map2 = await transcodeWoffSources(['r/syn.woff'], { wptDir: root, log: (m) => lines2.push(m) });
  assert.equal(map2.get('r/syn.woff'), 'r/syn.woff.ttf');
  assert.ok(lines2.some((l) => /CACHED/.test(l)), 'a cache hit is stamped too — the hop is ENGAGED');
});

test('transcodeWoffSources declines traversal, missing files and bad content', async (t) => {
  const root = await fs.mkdtemp(join(tmpdir(), 'w9-corpus-'));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  await fs.mkdir(join(root, 'r'), { recursive: true });
  // A woff2 payload AT a .woff name: the gate passed on the name, so the
  // CONTENT check must decline it with the named WOFF2 message.
  const woff2 = Buffer.alloc(48);
  woff2.writeUInt32BE(WOFF2_SIGNATURE, 0);
  await fs.writeFile(join(root, 'r', 'sneaky.woff'), woff2);
  const lines = [];
  const map = await transcodeWoffSources(
    ['../outside.woff', 'r/missing.woff', 'r/sneaky.woff'],
    { wptDir: root, log: (m) => lines.push(m) },
  );
  assert.equal(map.size, 0, 'nothing may be transcoded');
  assert.ok(lines.some((l) => /DECLINED \.\.\/outside\.woff: resolves outside/.test(l)),
    'containment decline is named');
  assert.ok(lines.some((l) => /DECLINED r\/missing\.woff: no such file/.test(l)));
  assert.ok(lines.some((l) => /DECLINED r\/sneaky\.woff: WOFF2/.test(l)),
    'the WOFF2 scope limit must survive into the batch log');
  assert.ok(!existsSync(join(root, 'r', 'sneaky.woff.ttf')), 'no sibling for a decline');
});

// ── The cache is VALIDATED, not trusted (wave-42 lane F4) ───────────────────
//
// The defect these three pin: the mtime cache used to trust the sibling's
// CONTENT. An interrupted feeder run (SIGINT, ENOSPC, an OOM kill mid-write)
// left a PARTIAL font at the sibling path with a newer-than-the-woff mtime, so
// every later run logged `CACHED` and shipped those bytes to the device — a
// broken typeface with nothing in any log saying so.

/** Build a one-woff scratch corpus and transcode it once, returning the paths
 *  and the complete reference bytes every pin below compares against. */
async function seededCorpus(t) {
  const root = await fs.mkdtemp(join(tmpdir(), 'w9-corpus-'));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  await fs.mkdir(join(root, 'r'), { recursive: true });
  const woff = join(root, 'r', 'syn.woff');
  await fs.writeFile(woff, buildWoff(defaultTables()));
  await transcodeWoffSources(['r/syn.woff'], { wptDir: root, log: () => {} });
  const sibling = join(root, 'r', 'syn.woff.ttf');
  return { root, woff, sibling, good: await fs.readFile(sibling) };
}

/** Stamp `file`'s mtime 10s AFTER the woff's, so the mtime half of the cache
 *  check says "fresh" and only the content checks can reject it. */
async function stampNewerThan(file, woff) {
  const { mtime } = await fs.stat(woff);
  const newer = new Date(mtime.getTime() + 10_000);
  await fs.utimes(file, newer, newer);
}

test('a TRUNCATED sibling newer than the woff is re-transcoded, never reused', async (t) => {
  const { root, woff, sibling, good } = await seededCorpus(t);
  // THE OBSERVED FAILURE, reproduced: a partial write keeps the head bytes and
  // loses the tail, so the sfnt magic still reads correctly — only the SIZE
  // betrays it. That is why size equality (not just the magic word) is the
  // check that catches this one.
  await fs.truncate(sibling, Math.floor(good.length / 2));
  await stampNewerThan(sibling, woff);
  const lines = [];
  const map = await transcodeWoffSources(['r/syn.woff'], { wptDir: root, log: (m) => lines.push(m) });
  assert.equal(map.get('r/syn.woff'), 'r/syn.woff.ttf', 'the stand-in is still delivered');
  assert.ok(!lines.some((l) => /CACHED/.test(l)), 'a partial file must NEVER be reported as cached');
  assert.ok(lines.some((l) => /REJECTED cached r\/syn\.woff\.ttf: .*bytes but the transcode is/.test(l)),
    'the rejection is stamped loudly and names the size disagreement');
  // And the repair is real: the bytes on disk are the complete transcode again.
  assert.deepEqual(await fs.readFile(sibling), good, 'the sibling is rewritten in full');
  assert.equal(sfntChecksum(await fs.readFile(sibling)), SFNT_CHECKSUM_MAGIC);
});

test('a right-SIZED sibling with a non-sfnt magic word is re-transcoded too', async (t) => {
  const { root, woff, sibling, good } = await seededCorpus(t);
  // The other half of the validation: a file of exactly the right length whose
  // content is not a font at all (a half-copied mirror, a truncating fs that
  // pads, a stray write). Size alone would pass it, so the 4-byte sfntVersion
  // tag is what declines it.
  await fs.writeFile(sibling, Buffer.alloc(good.length, 0x42)); // 0x42424242 is no sfnt version
  await stampNewerThan(sibling, woff);
  const lines = [];
  const map = await transcodeWoffSources(['r/syn.woff'], { wptDir: root, log: (m) => lines.push(m) });
  assert.equal(map.get('r/syn.woff'), 'r/syn.woff.ttf');
  assert.ok(!lines.some((l) => /CACHED/.test(l)));
  assert.ok(lines.some((l) => /REJECTED cached .*opens with 0x42424242.*not an sfnt version tag/.test(l)),
    'the rejection names the actual magic word it found');
  assert.deepEqual(await fs.readFile(sibling), good, 'the sibling is rewritten in full');
});

test('force ignores an otherwise-valid cache — the transcoder-fix rollout knob', async (t) => {
  const { root, woff, sibling, good } = await seededCorpus(t);
  // A sibling that PASSES both content checks (right size, right magic) but
  // whose interior bytes are wrong — the honest limit of a size+magic check,
  // and precisely the state a transcoder BUG leaves behind: every input file
  // untouched, so the mtime cache would pin the bad output forever.
  const tampered = Buffer.from(good);
  tampered[tampered.length - 1] ^= 0xff;
  await fs.writeFile(sibling, tampered);
  await stampNewerThan(sibling, woff);
  const cachedLines = [];
  await transcodeWoffSources(['r/syn.woff'], { wptDir: root, log: (m) => cachedLines.push(m) });
  assert.ok(cachedLines.some((l) => /CACHED/.test(l)), 'without force this sibling is reused (the gap force closes)');
  assert.deepEqual(await fs.readFile(sibling), tampered, 'and its bad bytes survive');
  // With force the sibling is rewritten unconditionally, restoring the byte.
  const forcedLines = [];
  const map = await transcodeWoffSources(['r/syn.woff'], { wptDir: root, log: (m) => forcedLines.push(m), force: true });
  assert.equal(map.get('r/syn.woff'), 'r/syn.woff.ttf');
  assert.ok(!forcedLines.some((l) => /CACHED/.test(l)), 'force must not take the cache branch');
  assert.deepEqual(await fs.readFile(sibling), good, 'force rewrote the sibling from the woff');
});

test('feed-android wires TITAN_WOFF_TRANSCODE=force into the transcoder opts', async () => {
  // The env spelling lives in the FEEDER (the module takes a plain boolean, so
  // it stays testable without touching process.env). Pinned as source text for
  // the same reason the other feed-android wiring pins are: the alternative is
  // a device run. `=0` (off) is pinned behaviourally in the module suite below.
  const src = await fs.readFile(new URL('./feed-android.mjs', import.meta.url), 'utf8');
  assert.match(src, /force: process\.env\.TITAN_WOFF_TRANSCODE === 'force'/,
    'the force knob must reach transcodeWoffFixtures');
});

test('a failed write leaves NO partial sibling and NO scratch file behind', async (t) => {
  const root = await fs.mkdtemp(join(tmpdir(), 'w9-corpus-'));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  await fs.mkdir(join(root, 'r'), { recursive: true });
  await fs.writeFile(join(root, 'r', 'syn.woff'), buildWoff(defaultTables()));
  // Block the sibling path with a NON-EMPTY DIRECTORY so the rename cannot
  // succeed — the cheapest way to exercise the write-failure path for real.
  // This is what makes the temp-then-rename discipline visible: the failure
  // happens at the rename, so nothing partial can ever occupy the sibling path.
  await fs.mkdir(join(root, 'r', 'syn.woff.ttf'), { recursive: true });
  await fs.writeFile(join(root, 'r', 'syn.woff.ttf', 'blocker'), 'x');
  const lines = [];
  const map = await transcodeWoffSources(['r/syn.woff'], { wptDir: root, log: (m) => lines.push(m) });
  assert.equal(map.size, 0, 'a write that failed is a DECLINE, so the woff keeps riding the wire');
  assert.ok(lines.some((l) => /DECLINED r\/syn\.woff: could not write/.test(l)));
  // The scratch file is cleaned up: a failed run must not litter the corpus
  // mirror with `.tmp-…` files that no later run would ever collect.
  const left = (await fs.readdir(join(root, 'r'))).filter((n) => n.includes('.tmp-'));
  assert.deepEqual(left, [], 'no temp scratch may survive a failed write');
});

test('a successful write leaves no scratch file either', async (t) => {
  const { root } = await seededCorpus(t);
  const left = (await fs.readdir(join(root, 'r'))).filter((n) => n.includes('.tmp-'));
  assert.deepEqual(left, [], 'the temp file is renamed into place, not copied and left');
  // And the scratch name could never be mistaken for a deliverable stand-in.
  assert.equal(isTranscodedSrc('r/syn.woff.ttf.tmp-123-456'), false);
});

test('TITAN_WOFF_TRANSCODE=0 disables the hop; no wptDir skips it — both loudly', async (t) => {
  const root = await fs.mkdtemp(join(tmpdir(), 'w9-corpus-'));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  await fs.writeFile(join(root, 'syn.woff'), buildWoff(defaultTables()));
  // The off-switch: default ON (the transcode is lossless — module banner),
  // '0' reverts both feeder legs to the exact wave-41 decline behaviour.
  process.env.TITAN_WOFF_TRANSCODE = '0';
  t.after(() => { delete process.env.TITAN_WOFF_TRANSCODE; });
  const offLines = [];
  const offMap = await transcodeWoffSources(['syn.woff'], { wptDir: root, log: (m) => offLines.push(m) });
  assert.equal(offMap.size, 0);
  assert.ok(offLines.some((l) => /woff transcode OFF/.test(l)));
  delete process.env.TITAN_WOFF_TRANSCODE;
  const skipLines = [];
  const skipMap = await transcodeWoffSources(['syn.woff'], { log: (m) => skipLines.push(m) });
  assert.equal(skipMap.size, 0);
  assert.ok(skipLines.some((l) => /SKIPPED.*no --wpt-dir/.test(l)));
});

// ── Real corpus round-trip (skips when tools/wpt is not materialised) ───────

/** The independent SECOND PASS the lane brief asks for: re-parse the WOFF with
 *  fresh reads here in the test (not via the module), inflate every table
 *  independently, and byte-compare against the rebuilt sfnt's directory and
 *  payloads. Any drift between the module's math and the spec shows up as a
 *  byte diff, not as a "the module agrees with itself" tautology. */
function verifyRoundTrip(woff, font) {
  const numTables = woff.readUInt16BE(12);
  assert.equal(font.readUInt32BE(0), woff.readUInt32BE(4), 'sfntVersion == WOFF flavor');
  assert.equal(font.readUInt16BE(4), numTables, 'table count survives');
  for (let i = 0; i < numTables; i++) {
    const wAt = 44 + i * 20;
    const tag = woff.toString('latin1', wAt, wAt + 4);
    const orig = (() => { // inflate independently of the module
      const off = woff.readUInt32BE(wAt + 4);
      const comp = woff.readUInt32BE(wAt + 8);
      const len = woff.readUInt32BE(wAt + 12);
      const raw = woff.subarray(off, off + comp);
      return comp === len ? raw : inflateSync(raw);
    })();
    const fAt = 12 + i * 16;
    assert.equal(font.toString('latin1', fAt, fAt + 4), tag, `record ${i} keeps tag '${tag}'`);
    const off = font.readUInt32BE(fAt + 8);
    const len = font.readUInt32BE(fAt + 12);
    assert.equal(off % 4, 0, `'${tag}' is long-aligned`);
    assert.equal(len, orig.length, `'${tag}' keeps its original length`);
    const rebuilt = font.subarray(off, off + len);
    if (tag === 'head') {
      // Everything except the 4 recomputed adjustment bytes must round-trip.
      assert.deepEqual(rebuilt.subarray(0, 8), orig.subarray(0, 8));
      assert.deepEqual(rebuilt.subarray(12), orig.subarray(12));
    } else {
      assert.deepEqual(rebuilt, orig, `'${tag}' bytes round-trip losslessly`);
    }
  }
  // And the head adjustment identity holds over the whole rebuilt file.
  assert.equal(sfntChecksum(font), SFNT_CHECKSUM_MAGIC, 'file sums to B1B0AFBA');
}

test('REAL corpus: the boundary-shaping LinLibertine woff (CFF flavor) round-trips', async (t) => {
  // THE probe font: the face all 48 wave-41 font tests declare, and the one
  // the wave-35/41 device gates measured Android declining.
  const p = join(WPT_DIR, 'css', 'css-text', 'boundary-shaping', 'resources', 'LinLibertine_Re-4.7.5.woff');
  if (!existsSync(p)) { t.skip('tools/wpt corpus not materialised'); return; }
  const woff = await fs.readFile(p);
  const r = woffToTtf(woff);
  assert.equal(r.extension, 'otf', "flavor 'OTTO' (CFF outlines) must name .otf");
  assert.equal(r.numTables, 13);
  assert.deepEqual(r.checksumMismatches, [], 'every declared corpus checksum verifies');
  assert.equal(r.totalSfntSizeMatches, true, "the WOFF's own declared sfnt size confirms the layout");
  verifyRoundTrip(woff, r.font);
});

test('REAL corpus: a TrueType-flavor woff (DejaVuSerif) round-trips to .ttf', async (t) => {
  const p = join(WPT_DIR, 'css', 'css-writing-modes', 'support', 'DejaVuSerif-webfont.woff');
  if (!existsSync(p)) { t.skip('tools/wpt corpus not materialised'); return; }
  const woff = await fs.readFile(p);
  const r = woffToTtf(woff);
  assert.equal(r.extension, 'ttf', 'flavor 0x00010000 (glyf outlines) must name .ttf');
  verifyRoundTrip(woff, r.font);
});

// ── The platform asymmetry pin ──────────────────────────────────────────────

test('feed-ios does NOT import the transcoder — CoreText parses WOFF1 natively', async () => {
  // MEASURED (wave-35 device gate, quoted in DocumentFontRegistry.kt): the
  // same LinLibertine woff registered fine on iOS and moved its ink
  // 0.665% → 0.488%. Transcoding there would be work that can only add risk;
  // this pin makes the asymmetry a decision, not an omission.
  const src = await fs.readFile(new URL('./feed-ios.mjs', import.meta.url), 'utf8');
  assert.ok(!src.includes('woff-to-ttf'), 'the iOS feeder must keep delivering the authored woff');
});
