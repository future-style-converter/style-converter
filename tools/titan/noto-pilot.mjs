// tools/titan/noto-pilot.mjs
//
// wave-45 lane X6 — the Rule-43 NOTO BUNDLING PILOT (measurement before
// commitment). One module owns every pilot knob so the four consumers
// (capture-browser-ref.mjs, apps/web-harness/capture-screenshots.mjs,
// feed-android.mjs, feed-ios.mjs) cannot drift on family names, file names,
// the canvas-rev suffix, or the env-flag spelling.
//
// ## What the pilot is
// inject-wpt-block.mjs's Rule 43 (native-font-parity) excludes 25 of 48
// css-counter-styles tests per NATIVE platform because the four surfaces
// resolve non-Latin glyphs through DIFFERENT font stacks: ref+web land on the
// macOS CoreText cascade, Compose substitutes its bundled metric-normalised
// Noto faces (wave-34 lane F1, 5 scripts) or the emulator's system Noto CJK,
// and SwiftUI keeps the Apple system cascade. The documented closing move
// (inject-wpt-block.mjs Rule-43 banner) is to bundle ONE Noto subset in all
// four pipelines and bump CANVAS_REV. That is a heavy corpus-wide change;
// this pilot delivers the same faces to the REF and WEB surfaces for
// css-counter-styles ONLY, behind an env flag, so the payoff can be MEASURED
// (how many of the 25 would pass?) before wave 46 commits to it.
//
// ## The flag contract (byte-discipline)
// Every export is a FUNCTION of an env object defaulting to process.env, and
// every consumer's call site collapses to its pre-pilot behaviour when
// TITAN_NOTO_PILOT !== '1'. The default pipeline is therefore byte-identical
// with the flag unset — pinned by noto-pilot.test.mjs and by each consumer's
// own suite.
//
// ## The faces
// The five script faces are the wave-34 lane-F1 bundled files
// (runtimes/compose/src/main/res/font/noto_sans_<script>_regular.ttf,
// byte-identical to the SwiftPM resources) — vertical metrics normalised to
// Inter's, outlines untouched. Using EXACTLY those bytes is the point: they
// are what Compose already paints in WPT capture mode, so giving the ref the
// same file measures the closing move rather than a lookalike. The CJK face
// is the ideograph/Hangul coverage the wave-34 bundle deliberately skipped
// (size): the pilot stages a subset of the EMULATOR's own NotoSansCJK
// (what Android's per-glyph system fallback shapes with — again, parity with
// what the native actually paints, not with upstream). Files are staged in a
// host directory named by TITAN_NOTO_PILOT_FONTS — deliberately NOT repo
// binaries: whether these bytes ever land in-repo is exactly the wave-46
// decision this pilot informs.

import path from 'node:path';

/** The one spelling of the pilot flag. '1' and nothing else — mirrors the
 *  WPT_MODE/WPT_COMPOSED convention in the capture drivers. */
export function notoPilotEnabled(env = process.env) {
  return env.TITAN_NOTO_PILOT === '1';
}

/** Host directory holding the staged pilot font files, or null when unset.
 *  Separate from the enabled flag so a mis-set run fails LOUDLY (enabled
 *  with no dir ⇒ every face is reported missing) instead of silently
 *  rendering the pre-pilot stack under a pilot-suffixed canvas rev. */
export function notoPilotFontsDir(env = process.env) {
  return env.TITAN_NOTO_PILOT_FONTS || null;
}

/** Canvas-rev suffix — the pilot's refs live in their OWN tree
 *  (…-notopilot/) so the frozen white-black-ink-font-lh-imgpad-htmlpins refs
 *  are never touched, and a pilot ref can never be adopted by (or leak into)
 *  the production scorer view. */
export const NOTO_PILOT_REV_SUFFIX = '-notopilot';

/** '' with the flag off — string-concatenated into CANVAS_REV by
 *  capture-browser-ref.mjs, so the default rev is byte-identical. */
export function notoPilotRevSuffix(env = process.env) {
  return notoPilotEnabled(env) ? NOTO_PILOT_REV_SUFFIX : '';
}

/** The pilot faces, in the order they join the font-family stack.
 *
 *  Family names are pilot-scoped ('Noto Pilot …') rather than the fonts'
 *  own names so a machine-installed 'Noto Sans Armenian' can never shadow
 *  the staged file in the ref browser — the measurement must run against
 *  OUR bytes or it measures the host machine.
 *
 *  Order note: the five script faces have disjoint coverage, so their
 *  relative order is immaterial; CJK goes LAST because it also carries
 *  Latin/punctuation glyphs and must never win a codepoint an earlier face
 *  (or Inter itself) can serve. Coverage the pilot needs (measured over the
 *  25 excluded tests' sources + refs + baked markers, 92 codepoints):
 *  Armenian ×25, Hebrew ×2, Arabic digits ×10, Bengali digits ×10, Khmer
 *  digits ×10, Han ×32 + 〇 + 、, Hangul ×2. */
export const NOTO_PILOT_FACES = Object.freeze([
  { family: 'Noto Pilot Arabic',   file: 'noto_sans_arabic_regular.ttf' },
  { family: 'Noto Pilot Armenian', file: 'noto_sans_armenian_regular.ttf' },
  { family: 'Noto Pilot Bengali',  file: 'noto_sans_bengali_regular.ttf' },
  { family: 'Noto Pilot Hebrew',   file: 'noto_sans_hebrew_regular.ttf' },
  { family: 'Noto Pilot Khmer',    file: 'noto_sans_khmer_regular.ttf' },
  { family: 'Noto Pilot CJK',      file: 'noto_pilot_cjk_subset.otf' },
]);

/** Insert the pilot families into a font-family stack, directly after the
 *  leading 'Inter' entry. AFTER Inter, never before: Latin (and every other
 *  codepoint Inter covers) must keep resolving exactly where it does today —
 *  the pilot may only catch the fall-through the frozen stack hands to the
 *  platform cascade. A stack that does not start with 'Inter' is a contract
 *  violation (REF_FONT_STACK is pinned by wpt-white-canvas.test.mjs), so we
 *  throw rather than guess an insertion point. Flag off ⇒ input returned
 *  UNCHANGED (same string identity — the byte-discipline pin). */
export function notoPilotStack(baseStack, env = process.env) {
  if (!notoPilotEnabled(env)) return baseStack;
  const lead = "'Inter', ";
  if (!baseStack.startsWith(lead)) {
    throw new Error(`noto-pilot: expected the font stack to start with ${lead.trim()} — got: ${baseStack}`);
  }
  const pilots = NOTO_PILOT_FACES.map((f) => `'${f.family}'`).join(', ');
  return `${lead}${pilots}, ${baseStack.slice(lead.length)}`;
}

/** MIME by extension for the data-URI payloads. Closed table on purpose
 *  (same discipline as feed-lib's FONT_EXTENSIONS): the pilot stages exactly
 *  ttf + otf, and an unexpected extension should fail the face loudly
 *  rather than ship with a guessed type. */
const FONT_MIME = Object.freeze({ '.ttf': 'font/ttf', '.otf': 'font/otf' });

/** Absolute host paths of the staged pilot files that actually exist.
 *  `missing` names the rest so every consumer can warn with the same words.
 *  Flag off (or no dir) ⇒ both lists empty / all-missing respectively. */
export function notoPilotFontFiles(env = process.env, { existsSync }) {
  const dir = notoPilotFontsDir(env);
  const present = [];
  const missing = [];
  if (!notoPilotEnabled(env)) return { present, missing };
  for (const face of NOTO_PILOT_FACES) {
    const abs = dir ? path.join(dir, face.file) : null;
    if (abs && existsSync(abs)) present.push({ ...face, abs });
    else missing.push(face);
  }
  return { present, missing };
}

/** @font-face CSS for the staged pilot faces, payloads embedded as base64
 *  data URIs — the same delivery capture-browser-ref.mjs uses for Inter
 *  (base64 is safe on this path; it never crosses the converter's
 *  value-lowercasing IR pipeline). font-weight 400 + font-display: block
 *  mirror the Inter rules: every Rule-43 document paints its non-Latin ink
 *  at the default weight (ScriptFallbackFonts banner), and `block` keeps the
 *  capture from racing a fallback-face first paint.
 *
 *  Missing files are reported through `warn` — LOUD per staged face, never
 *  thrown: one missing face must not take down the other five, and the
 *  delivery gates downstream (document.fonts.check in the web driver) make
 *  the miss measurable. Flag off ⇒ '' (injected nowhere, changes nothing). */
export async function notoPilotFontFaceCss(env = process.env, { readFile, existsSync },
                                           warn = (m) => process.stderr.write(`${m}\n`)) {
  if (!notoPilotEnabled(env)) return '';
  const { present, missing } = notoPilotFontFiles(env, { existsSync });
  for (const face of missing) {
    warn(`[noto-pilot] WARN: staged font missing for '${face.family}' ` +
         `(${notoPilotFontsDir(env) ?? '<TITAN_NOTO_PILOT_FONTS unset>'}/${face.file}) — ` +
         `that script keeps the pre-pilot fallback`);
  }
  const rules = [];
  for (const face of present) {
    const mime = FONT_MIME[path.extname(face.file).toLowerCase()];
    if (!mime) { warn(`[noto-pilot] WARN: unexpected extension on ${face.file} — face skipped`); continue; }
    const bytes = await readFile(face.abs);
    rules.push(
      `@font-face { font-family: '${face.family}'; font-style: normal; ` +
      `font-weight: 400; font-display: block; ` +
      `src: url(data:${mime};base64,${bytes.toString('base64')}) format('${mime === 'font/otf' ? 'opentype' : 'truetype'}'); }`,
    );
  }
  return rules.join('\n');
}

/** The WEB half of the pilot: one <style> payload for the live harness page.
 *  The @font-face rules above PLUS a re-statement of the two WPT-stage
 *  font-family rules from apps/web-harness/index.html with the pilot stack.
 *  Same selectors, same specificity, injected LATER ⇒ the pilot stack wins
 *  by source order (CSS 2.1 §6.4.3) exactly where the index.html rules
 *  apply, and nowhere else — an IR-declared font-family still arrives as an
 *  inline style and still beats both. `baseStack` must be the harness's own
 *  stack (the caller passes REF_FONT_STACK, which wpt-white-canvas.test.mjs
 *  pins byte-for-byte against index.html), so ref and web extend the SAME
 *  base to the SAME result. Flag off ⇒ ''. */
export async function notoPilotWebCss(baseStack, env = process.env, io,
                                      warn = (m) => process.stderr.write(`${m}\n`)) {
  if (!notoPilotEnabled(env)) return '';
  const faces = await notoPilotFontFaceCss(env, io, warn);
  const stack = notoPilotStack(baseStack, env);
  return `${faces}\n` +
    `html, body { font-family: ${stack}; }\n` +
    `body.wpt-mode, body.wpt-mode #root, ` +
    `body.wpt-composed-mode, body.wpt-composed-mode #root { font-family: ${stack}; }`;
}
