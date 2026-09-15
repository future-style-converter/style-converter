
// ── wave-50 lane B4: HTML optional end tags must not double-count text ──────
//
// scanOwnText (reached through extractOwnTextMerged) never mirrored
// walkChildren's AUTO_CLOSE_TRIGGERS rule, so an omitted `</li>` / `</p>` /
// `</td>` left the scan resuming INSIDE the child and the child's text was
// counted as the PARENT's own text as well. The wire then carried it twice —
// once on the parent's `_text`/`_runs`, once on the child — and every renderer
// painted each item twice. MEASURED on css-counter-styles/counter-suffix
// (wave49-final web f 0.8867 · iOS x 0.8883 · android x 0.8901): its six
// `<ol><li>foo<li>bar</ol>` lists each rendered "1. foo" then a bare "foo".
//
// MUTATION PROOF (run at authoring time, lane B4): deleting the
// `autoCloseTriggers`/`implicitCloseAt` branch in scanOwnText — i.e. restoring
// the pre-lane behaviour — turns the first three assertions below red
// (text "foobar"/"1 2"/"ab" and a non-null runProto reappear) while the
// closed-tag control stays green, so the pin cannot pass on the old code.
test('wave50-B4: an omitted end tag does not double-count the child text', () => {
  const ctx = { styledTags: new Set() };
  // counter-suffix's own shape, verbatim: two `<li>`s with no `</li>`.
  const li = extractOwnTextMerged('<li>foo<li>bar', ctx);
  assert.equal(li.text, '');           // the `<ol>` owns NO text of its own
  assert.equal(li.runProto, null);     // …so there is no run list either
  assert.equal(li.reordered, false);   // and nothing was reordered
  // `<p>` is the other high-frequency omittable end tag (selectors/
  // child-indexed-no-parent.html writes nine of them in a row).
  assert.equal(extractOwnTextMerged('<p>a<p>b', ctx).text, '');
  // Table row/cell chain — background-color-animation-with-table1's shape.
  assert.equal(extractOwnTextMerged('<tr><td>a<tr><td>b', ctx).text, '');
  // Control: the SAME content with explicit closers already behaved
  // correctly, and must still.
  assert.equal(extractOwnTextMerged('<li>foo</li><li>bar</li>', ctx).text, '');
});

test('wave50-B4: the auto-close mirror leaves genuine mixed content alone', () => {
  const ctx = { styledTags: new Set() };
  // The canonical wave-21 reorder shape: own text on BOTH sides of a kept
  // child. `u` has no AUTO_CLOSE_TRIGGERS entry, so the new branch cannot
  // fire and the run list must be byte-identical to its pre-lane value.
  const mixed = extractOwnTextMerged('the quick <u>brown</u> fox', ctx);
  assert.equal(mixed.text, 'the quick fox');
  assert.equal(mixed.reordered, true);
  assert.deepEqual(mixed.runProto, [
    { text: 'the quick ' }, { el: 0, tag: 'u' }, { text: ' fox' },
  ]);
  // An auto-closing tag that DOES carry its closer keeps the real close as
  // the element end — the implicit trigger sits after it, so the parent's
  // trailing text is still its own.
  const closed = extractOwnTextMerged('<li>foo</li>tail<li>bar', ctx);
  assert.equal(closed.text, 'tail');
  // ── the branch-1 discriminator (implicit trigger BEFORE a real closer) ──
  // `<li>foo<li>bar</li>tail`: the first item's `</li>` is the SECOND item's,
  // so only the implicit-trigger branch can end item one at the right place.
  // With that branch alone disabled the scan runs past the real closer and
  // item one SWALLOWS item two — one element in the proto instead of two —
  // which is what this assertion catches (mutation-verified, lane B4).
  const early = extractOwnTextMerged('<li>foo<li>bar</li>tail', ctx);
  assert.equal(early.text, 'tail');
  assert.deepEqual(early.runProto, [
    { el: 0, tag: 'li' }, { el: 1, tag: 'li' }, { text: 'tail' },
  ]);
});
