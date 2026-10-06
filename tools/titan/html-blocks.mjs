// tools/titan/html-blocks.mjs — index-based block scanning for WPT sources.
//
// WHY THIS EXISTS. The harness reads WPT test sources as text and needs to
// skip `<script>…</script>` blocks and `<!-- … -->` comments before it looks
// for `<style>` rules or `style=` attributes. Doing that with regexes such as
// `/<script\b[^>]*>[\s\S]*?<\/script>/gi` is what CodeQL flags as
// "bad tag filter" / "incomplete multi-character sanitization" (js/bad-tag-filter,
// js/incomplete-multi-character-sanitization): a regex that recognises a tag
// is not a parser, and on a hostile document it can be made to miss or to
// backtrack. Our inputs are the vendored WPT corpus, not user input — but the
// 11 alerts the wave-52 PR raised were real findings about our own code, and
// a plain scanner is both clearer and provably equivalent on the corpus
// (`tools/titan/html-blocks.test.mjs` pins both: behaviour, and byte-equality
// with the regexes it replaced over every source the gate reads).
//
// Semantics are those of the regexes they replace, deliberately:
//   • a block opens at `<script` (any case) followed by a non-word character
//     (the regex `\b`), its attributes run to the next `>`, its body to the
//     next `</script>` (any case); a block without a closing tag is NOT a
//     block (the lazy regex would not have matched it either) — scanning stops;
//   • a comment opens at `<!--` and closes at the next `-->`; an unclosed
//     comment is left in place.

/** indexOf for an ASCII token, ASCII-case-insensitively, with offsets in `text`
 *  itself. (Lower-casing the whole document first is NOT length-preserving —
 *  `İ` becomes two code units — so offsets taken in a lower-cased copy drift;
 *  css-fonts/test_font_family_parsing.html caught exactly that.) */
function indexOfAsciiCI(text, token, from) {
  const t = token.toLowerCase();
  outer: for (let i = Math.max(0, from); i <= text.length - t.length; i++) {
    for (let j = 0; j < t.length; j++) {
      const c = text.charCodeAt(i + j);
      // ASCII letters fold by the 0x20 bit; everything else must match exactly.
      const folded = c >= 65 && c <= 90 ? c + 32 : c;
      if (folded !== t.charCodeAt(j)) continue outer;
    }
    return i;
  }
  return -1;
}

/** Every `<script …>body</script>` block of `html`, in document order:
 *  `{ start, end, attrs, body }` with `start`/`end` the block's offsets. */
export function scriptBlocks(html) {
  const text = String(html ?? '');
  const blocks = [];
  let from = 0;
  for (;;) {
    const open = indexOfAsciiCI(text, '<script', from);   // case-insensitive tag names, as the /i regexes were
    if (open < 0) break;
    const after = text[open + 7];                    // the character after `<script`
    // `\b`: `<scripts>` or `<scriptx` is not a script tag — keep scanning past it.
    if (after !== undefined && /\w/.test(after)) { from = open + 7; continue; }
    const gt = text.indexOf('>', open + 7);          // end of the opening tag
    if (gt < 0) break;                               // no `>`: the regex would not match — stop
    const close = indexOfAsciiCI(text, '</script>', gt + 1);
    if (close < 0) break;                            // unclosed: not a block — stop (regex parity)
    blocks.push({ start: open, end: close + '</script>'.length, attrs: text.slice(open + 7, gt), body: text.slice(gt + 1, close) });
    from = close + '</script>'.length;
  }
  return blocks;
}

/** `html` with every script block removed (the `withoutScripts` of counter-style-author.mjs). */
export function stripScripts(html) {
  const text = String(html ?? '');
  let out = '', from = 0;
  // Copy the gaps between blocks; the blocks themselves are dropped.
  for (const b of scriptBlocks(text)) { out += text.slice(from, b.start); from = b.end; }
  return out + text.slice(from);
}

/** `text` with every `open…close` span removed (e.g. `<!--` … `-->`); an unclosed
 *  span stays, as with the lazy regex `/<!--[\s\S]*?-->/g`. */
export function stripBetween(text, open, close) {
  const s = String(text ?? '');
  let out = '', from = 0;
  for (;;) {
    const a = s.indexOf(open, from);
    if (a < 0) break;
    const b = s.indexOf(close, a + open.length);
    if (b < 0) break;                                // unclosed: keep the rest verbatim
    out += s.slice(from, a);
    from = b + close.length;
  }
  return out + s.slice(from);
}
