// tools/titan/mono-pin.mjs
//
// The MONOSPACE FONT-METRIC PIN — piloted by wave-46 lane Y7 (measurement
// before commitment), DEFAULT-ON since wave-47 lane Z4 earned the flip (the
// flag-contract section below carries the A/B numbers). One module owns
// every pin knob so the two native feeders (feed-android.mjs, feed-ios.mjs)
// cannot drift on the family name, the face files, the sandbox corner or
// the env-flag spelling. Structure mirrors the wave-45 noto-pilot.mjs so
// the pilot-family modules read the same way.
//
// ## The wall this pin closes
// The corpus canvas pins Inter for `sans-serif` (capture-browser-ref.mjs's
// corpus-v4.1 FONT sub-boundary) but NOT `monospace`: each surface resolves
// the generic through its own platform cascade. MEASURED over the wave-45
// gate (tools/titan/runs/wave45-final, 71 monospace-bearing tests across 10
// sections) with fontTools over the actual face files:
//
//   surface   face                 '0' advance   hhea ascent/descent
//   ref+web   Menlo (Chromium/mac)  1233/2048     1901 / 483      ← frozen refs
//   Android   Droid Sans Mono        ~0.600em      (system image)
//   iOS       SF Mono (.monospaced)  1266/2048     1980 / 432
//
// so a `width: 10ch` box at 32px is 197px on the ref, 194px on Android and
// 202px on iOS (hyphens-manual-inline-010: measured border columns), and the
// glyph ink differs on both natives. Eight iOS cells and one Android cell of
// the css-text hyphens family lay out IDENTICALLY to the ref and fail on the
// face alone (Y7's layout-signature audit).
//
// ## The pin face
// Menlo is Apple-proprietary and cannot be bundled. DejaVu Sans Mono — the
// face Menlo was derived from — is free (Bitstream Vera licence) and has
// BYTE-IDENTICAL metrics: unitsPerEm 2048, '0' advance 1233, hhea 1901/-483/0,
// typo 1556/-492/410. So pinning DejaVu Sans Mono on the natives lands every
// `ch` width, wrap point and baseline exactly where the frozen Menlo refs have
// them, and only the outline ink differs. MEASURED (Y7 host probe: the real ref
// canvas with generic-monospace elements redirected to DejaVu, diffed against
// the frozen Menlo refs with the scorer's own diffWebVsRef): worst residual
// 0.9891 (block-ellipsis-023/024), median ≈0.997, 27 of 68 at ≥0.999.
//
// ## Why the ref and web surfaces are NOT touched
// Chromium resolves a GENERIC family through the system font cache only — a
// web font declared as `@font-face { font-family: "monospace" }` stays
// `unloaded` against `font-family: monospace` (Y7 probe via puppeteer:
// document.fonts reports `monospace:unloaded`, widths unchanged). The only
// redirect is CDP Page.setFontFamilies({fixed}) naming a HOST-INSTALLED face,
// which would make the frozen refs depend on the capture host's font book —
// the exact drift the Inter data-URI pin was built to kill. And the ref is
// ALREADY the pin's target (Menlo == DejaVu Sans Mono metrically), so the
// pilot scores natives against the FROZEN refs unchanged: no canvas-rev
// suffix, because the ref contract does not change — a suffix here would only
// force a lazy re-render of byte-identical refs.
//
// ## How the natives take the pin (no resolver change)
// Both runtimes consult the DOCUMENT @font-face database before any generic
// (wave-35 lane B2: Compose CssFontFamilyResolver.resolveEntry →
// DocumentFontRegistry first; SwiftUI TypographyApplier / StyleBuilder walk
// fontFamilyNames through DocumentFontRegistry.resolvedName first). So the
// pilot delivers the pin as a document face NAMED `monospace`: the feeders
// append `fontFaces` entries to the in-memory IR of every document that names
// the generic, and push the two DejaVu files into a `_mono-pin/` corner of
// the fonts sandbox. The UA fixed-default 13px quirk (MonospaceUAFontSize on
// both natives) keys on the declared family NAME, so it still fires.
//
// ## The two measurement seams (measured open by Y7, CLOSED in wave-47 Z4)
//   * Compose spacing/ChUnitMetrics measured `ch` from the GENERIC
//     (Typeface.MONOSPACE) and fell to Typeface.DEFAULT for a document
//     family, so pinned Android `ch` widths resolved against Roboto's '0'
//     (10ch box 184px vs the ref's 197). Closed: DocumentFontRegistry keeps
//     a per-family measuring handle (DocumentFontTypefaces) and ChUnitMetrics
//     measures THE REGISTERED face. 53 of the 71 wall tests use `ch`.
//   * SwiftUI ChUnitMetrics.zeroAdvancePx, ComponentRenderer's
//     measurementUIFont and LineBoxMetrics.contentHeight keyed on the generic
//     DESIGN (.monospaced → SF Mono), so the iOS `ch` basis (box 202 vs 197),
//     the greedy pre-break's measuring font and the line grid stayed SF Mono
//     while the label painted DejaVu (the pilot's one regression,
//     hyphens-auto-inline-010 0.9528→0.9406 — glyph runs ~3px low). Closed:
//     all three resolve the document face first, via the same §5.2 registry
//     walk that fills TextConfig.fontFaceName.
//
// ## The flag contract (byte-discipline, default ON since wave 47)
// Every export is a FUNCTION of an env object defaulting to process.env.
// The pin is the DEFAULT: wave-47 lane Z4's decisive A/B (both seams closed,
// css-text + css-overflow on both natives, private devices, scored with the
// frozen-ref scorer) measured arm B at +12 passes / 0 regressions —
//   css-text      iOS 25→33 (all 8 face-only wall cells flip)  Android 27→28
//   css-overflow  iOS 40→43                                    Android 42→42
// — meeting the svg-preraster flip rule ("costs no passes"). Explicit
// TITAN_MONO_PIN=0 (or any spelling other than '1') restores the platform
// cascade byte-for-byte: documents come back with the SAME identity, the
// face list is empty, nothing is pushed. Pinned by mono-pin.test.mjs.
//
// The faces live IN-REPO at tools/titan/fonts/mono-pin/ (Bitstream Vera
// licence — see the LICENSE there, extracted from the faces' own name
// table): a default cannot depend on an uncommitted host directory. The
// wave-46 pilot staged them host-side precisely because in-repo binaries
// were the decision the pilot informed; the A/B above made that decision.
// TITAN_MONO_PIN_FONTS still overrides the directory for experiments.

import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** ON unless explicitly disabled. Unset ⇒ ON (the wave-47 default); '1' ⇒ ON
 *  (the historical explicit-on spelling keeps working); ANY other value ⇒
 *  OFF — one loud opt-out spelling ('0' by convention) rather than a junk
 *  value silently half-enabling a metric pin. */
export function monoPinEnabled(env = process.env) {
  const v = env.TITAN_MONO_PIN;
  return v === undefined || v === '1';
}

/** The committed default faces directory — resolved relative to THIS module
 *  so every consumer (feeders run from any cwd) lands on the same bytes. */
export const MONO_PIN_DEFAULT_FONTS_DIR =
  path.join(path.dirname(fileURLToPath(import.meta.url)), 'fonts', 'mono-pin');

/** Directory holding the staged DejaVu files: TITAN_MONO_PIN_FONTS when set
 *  (experiments keep their override), else the committed default. Still a
 *  separate function from the enabled flag so a bad override fails LOUDLY
 *  (enabled with a wrong dir ⇒ every face reported missing) instead of
 *  silently rendering the platform cascade under a pinned-labelled run. */
export function monoPinFontsDir(env = process.env) {
  return env.TITAN_MONO_PIN_FONTS || MONO_PIN_DEFAULT_FONTS_DIR;
}

/** The CSS family the pin registers under. It is the GENERIC's own name on
 *  purpose: the natives' document-font-first rule (css-fonts-4 §5) then routes
 *  `font-family: monospace` to the pinned file with no resolver change, while
 *  the 13px UA quirk — keyed on the declared name — keeps firing. */
export const MONO_PIN_FAMILY = 'monospace';

/** The generic spellings that take the pin — exactly the two
 *  MonospaceUAFontSize arms the 13px quirk on, so "pinned face" and "fixed
 *  default size" always travel together. `ui-monospace` is a separate
 *  css-fonts-4 §12.2 keyword that Chromium ALSO maps to the fixed font. */
export const MONO_PIN_GENERICS = Object.freeze(['monospace', 'ui-monospace']);

/** Corner of each native's fonts sandbox the faces are pushed into. Leading
 *  underscore: no corpus-relative `fontFaces[].src` starts with one, so a
 *  real document face can never collide with — or be shadowed by — the pin. */
export const MONO_PIN_SANDBOX_DIR = '_mono-pin';

/** The pin faces, in IR `fontFaces` order.
 *
 *  Bold FIRST, Regular LAST — deliberately. SwiftUI's DocumentFontRegistry
 *  keeps ONE platform name per CSS family (`mapping[family] = name`, later
 *  faces overwrite), so the Regular face must be the last one registered or
 *  every plain monospace run would paint Bold. Compose groups both into one
 *  FontFamily and matches by weight, so the order is immaterial there. The
 *  three bold-bearing monospace tests in the wall are the only ones the 700
 *  face serves. */
export const MONO_PIN_FACES = Object.freeze([
  { file: 'DejaVuSansMono-Bold.ttf', weight: '700', style: 'normal' },
  { file: 'DejaVuSansMono.ttf',      weight: '400', style: 'normal' },
]);

/** Absolute host paths of the staged pin files that exist; `missing` names
 *  the rest so both feeders warn with the same words. Flag off ⇒ both empty. */
export function monoPinFontFiles(env = process.env, { existsSync }) {
  const dir = monoPinFontsDir(env);
  const present = [];
  const missing = [];
  if (!monoPinEnabled(env)) return { present, missing };
  for (const face of MONO_PIN_FACES) {
    const abs = dir ? path.join(dir, face.file) : null;
    if (abs && existsSync(abs)) present.push({ ...face, abs });
    else missing.push(face);
  }
  return { present, missing };
}

/** The family names one `FontFamily` property payload carries, in declared
 *  order — the SAME three wire shapes the runtimes' resolvers accept (bare
 *  array, `{ families: [...] }`, legacy single string), so a document the
 *  natives would pin is exactly a document this predicate says to pin. */
function familyNames(data) {
  if (Array.isArray(data)) return data.filter((x) => typeof x === 'string');
  if (data && typeof data === 'object' && Array.isArray(data.families)) {
    return data.families.filter((x) => typeof x === 'string');
  }
  if (typeof data === 'string') return [data];
  return [];
}

/** Normalise a CSS family name the way both DocumentFontRegistry twins key
 *  their maps: trimmed, outer quotes stripped, lower-cased. */
function familyKey(raw) {
  return raw.trim().replace(/^["']|["']$/g, '').toLowerCase();
}

/** True iff some component of this (flat, v2) document declares a
 *  `FontFamily` that names the monospace generic ANYWHERE in its list. Any
 *  position, not just first: the document-font rule matches by NAME wherever
 *  it sits, and the css-fonts-4 §5.2 walk reaches a trailing `monospace` only
 *  after every earlier entry misses (on Compose `"Courier New", Courier,
 *  monospace` walks to the generic; on iOS the installed Courier New wins
 *  first — both exactly as they do without the pin). A document that names
 *  no monospace family gets NO fontFaces entry, so its wire is byte-identical
 *  and its registry stays empty (the universal case). */
export function documentNamesMonospace(doc) {
  const comps = Array.isArray(doc?.components) ? doc.components : [];
  for (const c of comps) {
    for (const p of Array.isArray(c?.properties) ? c.properties : []) {
      if (p?.type !== 'FontFamily') continue;
      if (familyNames(p.data).some((n) => MONO_PIN_GENERICS.includes(familyKey(n)))) return true;
    }
  }
  return false;
}

/** The IR `fontFaces` entries the pin appends — `src` is the sandbox-relative
 *  path both DocumentFontRegistry twins join onto their fonts root verbatim
 *  (feed-lib's "relative path preserved verbatim" contract), which is why the
 *  feeders push the files to exactly `<fontsRoot>/_mono-pin/<file>`. Flag off
 *  ⇒ []. */
export function monoPinFontFaces(env = process.env) {
  if (!monoPinEnabled(env)) return [];
  return MONO_PIN_FACES.map((f) => ({
    family: MONO_PIN_FAMILY,
    src: `${MONO_PIN_SANDBOX_DIR}/${f.file}`,
    weight: f.weight,
    style: f.style,
  }));
}

/** Apply the pin to one decoded IR document.
 *
 *  Returns the INPUT OBJECT (same identity — the byte-discipline pin) when the
 *  flag is off, when the document names no monospace family, or when the
 *  document ALREADY declares a face named `monospace` (a real @font-face of
 *  that name must keep winning — css-fonts-4 §4.1 gives the document's own
 *  declaration precedence, and pinning over it would be the pilot measuring
 *  itself). Otherwise a SHALLOW copy whose `fontFaces` is the original list
 *  (if any, document order kept) followed by the pin faces; every other key
 *  is shared, so nothing else on the wire can drift. */
export function monoPinDocument(doc, env = process.env) {
  if (!monoPinEnabled(env) || !doc || typeof doc !== 'object') return doc;
  if (!documentNamesMonospace(doc)) return doc;
  const existing = Array.isArray(doc.fontFaces) ? doc.fontFaces : [];
  if (existing.some((f) => typeof f?.family === 'string' && familyKey(f.family) === MONO_PIN_FAMILY)) {
    return doc;
  }
  return { ...doc, fontFaces: [...existing, ...monoPinFontFaces(env)] };
}

/** One warning line per missing staged file — LOUD per face, never thrown:
 *  one missing face must not take down the other, and the runtimes'
 *  DocumentFontRegistry decline stamp makes the miss measurable on-device. */
export function monoPinMissingWarnings(env = process.env, { existsSync }, tag = 'mono-pin') {
  const { missing } = monoPinFontFiles(env, { existsSync });
  // monoPinFontsDir never answers null since the wave-47 default-ON flip
  // (unset ⇒ the committed tools/titan/fonts/mono-pin), so the old
  // '<TITAN_MONO_PIN_FONTS unset>' arm is gone with the reason it existed.
  return missing.map((face) =>
    `[${tag}] WARN: staged pin font MISSING ` +
    `(${monoPinFontsDir(env)}/${face.file}) — ` +
    `weight ${face.weight} keeps the platform monospace cascade`);
}
