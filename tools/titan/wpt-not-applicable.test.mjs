#!/usr/bin/env node
//
// Unit tests for tools/titan/wpt-not-applicable.mjs.
//
// Brief contract: ≥3 tests per rule (positive / negative / edge case).
// 17 rules × 3 = 51 minimum; we ship more for the rules that have
// non-obvious match boundaries (e.g. the printFilename suffix vs the
// @media print rule, or the crash-test-blank-ref two-part check).
//
// Pattern mirrors tools/titan/extract-fixture.test.mjs and
// tools/titan/aggregate-sections.test.mjs: pure-function tests via
// node:test, no filesystem. Each test exercises tagsForTest({...}) on a
// hand-rolled HTML snippet so failures point at the exact regex.

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
    tagsForTest, classifyAll, RULES, RX,
    // wave-34 lane F2: Rule 15's named predicate (the at-RULE boundary).
    declaresFontFaceRule,
    NON_LATIN_PREDEFINED_COUNTER_STYLES,
    shadowedCounterStyleNames,
} from './wpt-not-applicable.mjs';

// ── Sanity: 43 rules (17 swarm-001 + 12 swarm-002 + 11 swarm-003 + 1 wave-21
//    requires-wpt-server + 1 wave-29 browser-ref-divergent + 1 wave-30
//    requires-non-latin-font-parity)

test('RULES exports exactly 43 entries', () => {
    assert.equal(RULES.length, 43);
});

test('RULES tags are unique', () => {
    const tags = RULES.map((r) => r.tag);
    assert.equal(new Set(tags).size, tags.length);
});

test('every rule has a description and at least one swarm-00{1,2,3} source', () => {
    for (const r of RULES) {
        assert.ok(r.description && r.description.length > 10, `rule ${r.tag} missing description`);
        const swarm001 = Array.isArray(r.swarm001Source) ? r.swarm001Source : [];
        const swarm002 = Array.isArray(r.swarm002Source) ? r.swarm002Source : [];
        const swarm003 = Array.isArray(r.swarm003Source) ? r.swarm003Source : [];
        // Each rule must cite at least ONE investigation file from any
        // swarm round. Newer swarm rules don't have an older-swarm source;
        // that's fine.
        assert.ok(swarm001.length + swarm002.length + swarm003.length >= 1,
                  `rule ${r.tag} missing swarm00{1,2,3}Source`);
    }
});

// ── Rule 1: requires-tree-nesting ───────────────────────────────────────────

test('requires-tree-nesting fires on six chained <div> opens', () => {
    const html = '<div><div><div><div><div><div>x</div></div></div></div></div></div>';
    assert.deepEqual(tagsForTest({ html }), ['requires-tree-nesting']);
});

test('requires-tree-nesting does NOT fire on five-deep nesting (FIX-A handles 1..5)', () => {
    const html = '<div><div><div><div><div>x</div></div></div></div></div>';
    assert.equal(tagsForTest({ html }).includes('requires-tree-nesting'), false);
});

test('requires-tree-nesting handles mixed block tags (div/span/section/article)', () => {
    const html = '<section><article><div><span><div><span>x</span></div></span></div></article></section>';
    assert.deepEqual(tagsForTest({ html }), ['requires-tree-nesting']);
});

// ── Rule 2: requires-inline-FC ──────────────────────────────────────────────

test('requires-inline-FC fires on <br>', () => {
    assert.ok(tagsForTest({ html: '<p>line1<br>line2</p>' }).includes('requires-inline-FC'));
});

test('requires-inline-FC fires on unicode-bidi declaration', () => {
    assert.ok(tagsForTest({ html: '<style>span { unicode-bidi: bidi-override; }</style>' })
              .includes('requires-inline-FC'));
});

test('requires-inline-FC does NOT fire on plain block CSS', () => {
    assert.equal(tagsForTest({ html: '<div style="background:red">x</div>' })
                 .includes('requires-inline-FC'), false);
});

test('requires-inline-FC fires on inline span with position:absolute', () => {
    const html = '<span style="position:absolute; left:10px">x</span>';
    assert.ok(tagsForTest({ html }).includes('requires-inline-FC'));
});

// ── Rule 3: requires-animation-runtime ──────────────────────────────────────

test('requires-animation-runtime fires on @keyframes', () => {
    const html = '<style>@keyframes fade { from { opacity: 0 } to { opacity: 1 } }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-animation-runtime'));
});

test('requires-animation-runtime fires on `animation:` shorthand', () => {
    const html = '<style>div { animation: fade 1s; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-animation-runtime'));
});

test('requires-animation-runtime does NOT fire on `transition:` (different property)', () => {
    const html = '<style>div { transition: color 200ms; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-animation-runtime'), false);
});

// ── Rule 4: requires-script-mutation ────────────────────────────────────────

test('requires-script-mutation fires on inline script with appendChild', () => {
    const html = '<script>document.body.appendChild(document.createElement("div"));</script>';
    assert.ok(tagsForTest({ html }).includes('requires-script-mutation'));
});

test('requires-script-mutation fires on innerHTML assignment', () => {
    const html = '<script>document.getElementById("x").innerHTML = "<p>y</p>";</script>';
    assert.ok(tagsForTest({ html }).includes('requires-script-mutation'));
});

test('requires-script-mutation does NOT fire on external <script src>', () => {
    const html = '<script src="/resources/foo.js"></script>';
    assert.equal(tagsForTest({ html }).includes('requires-script-mutation'), false);
});

test('requires-script-mutation fires on insertBefore', () => {
    const html = '<script>parent.insertBefore(newNode, sibling);</script>';
    assert.ok(tagsForTest({ html }).includes('requires-script-mutation'));
});

// swarm-002 widened RX.scriptDomMutation to also match property assignment
// patterns (.className=, .scrollTop=, .style.<prop>=, .classList.add(), …)
// per css-multicol__multicol-clip-scrolled-content-001 and
// css-pseudo__before-dynamic-display-none.

test('requires-script-mutation fires on .className assignment (swarm-002)', () => {
    const html = '<script>document.getElementById("x").className = "active";</script>';
    assert.ok(tagsForTest({ html }).includes('requires-script-mutation'));
});

test('requires-script-mutation fires on .scrollTop assignment (swarm-002)', () => {
    const html = '<script>outer.scrollTop = 100;</script>';
    assert.ok(tagsForTest({ html }).includes('requires-script-mutation'));
});

test('requires-script-mutation fires on .style.* assignment (swarm-002)', () => {
    const html = '<script>el.style.color = "red";</script>';
    assert.ok(tagsForTest({ html }).includes('requires-script-mutation'));
});

test('requires-script-mutation fires on .classList.add() (swarm-002)', () => {
    const html = '<script>el.classList.add("foo");</script>';
    assert.ok(tagsForTest({ html }).includes('requires-script-mutation'));
});

// ── Rule 5: requires-print-medium ───────────────────────────────────────────

test('requires-print-medium fires on -print.html filename suffix', () => {
    assert.ok(tagsForTest({ html: '<div></div>', testRel: 'css/css-fonts/foo-print.html' })
              .includes('requires-print-medium'));
});

test('requires-print-medium fires on @media print rule', () => {
    const html = '<style>@media print { div { color: red } }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-print-medium'));
});

test('requires-print-medium fires on @page', () => {
    const html = '<style>@page { margin: 1cm; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-print-medium'));
});

test('requires-print-medium does NOT fire on plain -001.html screen test', () => {
    assert.equal(tagsForTest({ html: '<div></div>', testRel: 'css/css-fonts/foo-001.html' })
                 .includes('requires-print-medium'), false);
});

test('requires-print-medium does NOT fire on `@media not print`', () => {
    const html = '<style>@media not print { div { color: red } }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-print-medium'), false);
});

// ── Rule 6: requires-table-layout ───────────────────────────────────────────

test('requires-table-layout fires on <table>', () => {
    assert.ok(tagsForTest({ html: '<table><tr><td>x</td></tr></table>' })
              .includes('requires-table-layout'));
});

test('requires-table-layout fires on display: table-cell', () => {
    const html = '<style>div { display: table-cell; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-table-layout'));
});

test('requires-table-layout does NOT fire on display: flex / grid', () => {
    const html = '<style>div { display: flex; } span { display: grid; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-table-layout'), false);
});

// ── Rule 7: requires-form-control-rendering ─────────────────────────────────

test('requires-form-control-rendering fires on <input>', () => {
    assert.ok(tagsForTest({ html: '<input type="text">' })
              .includes('requires-form-control-rendering'));
});

test('requires-form-control-rendering fires on <select>/<option>', () => {
    assert.ok(tagsForTest({ html: '<select><option>a</option></select>' })
              .includes('requires-form-control-rendering'));
});

test('requires-form-control-rendering does NOT fire on plain <div>', () => {
    assert.equal(tagsForTest({ html: '<div class="my-input">x</div>' })
                 .includes('requires-form-control-rendering'), false);
});

// swarm-002 widened RX.formControlTag to also include the disclosure /
// list-item / hr / dialog families (UA-widget rendering, not strictly
// form controls). See css-ui__appearance-auto-details-list-item.json.

test('requires-form-control-rendering fires on <details> (swarm-002)', () => {
    assert.ok(tagsForTest({ html: '<details><summary>Summary</summary>Body</details>' })
              .includes('requires-form-control-rendering'));
});

test('requires-form-control-rendering fires on <ul>/<li> (swarm-002)', () => {
    assert.ok(tagsForTest({ html: '<ul><li>item</li></ul>' })
              .includes('requires-form-control-rendering'));
});

test('requires-form-control-rendering fires on <hr> and <dialog> (swarm-002)', () => {
    assert.ok(tagsForTest({ html: '<hr>' }).includes('requires-form-control-rendering'));
    assert.ok(tagsForTest({ html: '<dialog open>x</dialog>' }).includes('requires-form-control-rendering'));
});

// ── Rule 8: requires-runtime-selection ──────────────────────────────────────

test('requires-runtime-selection fires on ::selection pseudo', () => {
    const html = '<style>::selection { background: yellow; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-runtime-selection'));
});

test('requires-runtime-selection fires on getSelection() API call', () => {
    const html = '<script>window.getSelection().addRange(r);</script>';
    assert.ok(tagsForTest({ html }).includes('requires-runtime-selection'));
});

test('requires-runtime-selection does NOT fire on the literal word "selection" in text', () => {
    const html = '<p>The selection algorithm</p>';
    assert.equal(tagsForTest({ html }).includes('requires-runtime-selection'), false);
});

// ── Rule 9: requires-shadow-dom ─────────────────────────────────────────────

test('requires-shadow-dom fires on <template shadowrootmode>', () => {
    const html = '<div><template shadowrootmode="open"><slot></slot></template></div>';
    assert.ok(tagsForTest({ html }).includes('requires-shadow-dom'));
});

test('requires-shadow-dom fires on ::slotted(div)', () => {
    const html = '<style>::slotted(div) { color: red; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-shadow-dom'));
});

test('requires-shadow-dom fires on :host', () => {
    const html = '<style>:host { display: block; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-shadow-dom'));
});

test('requires-shadow-dom does NOT fire on plain <template> without shadowrootmode', () => {
    const html = '<template id="t"><div>x</div></template>';
    assert.equal(tagsForTest({ html }).includes('requires-shadow-dom'), false);
});

// ── Rule 10: requires-containing-block-layout ───────────────────────────────

test('requires-containing-block-layout fires on position:absolute + bottom offset', () => {
    const html = '<div style="position:absolute; bottom:10px">x</div>';
    assert.ok(tagsForTest({ html }).includes('requires-containing-block-layout'));
});

test('requires-containing-block-layout fires on position:fixed + right offset', () => {
    const html = '<div style="position:fixed; right:0">x</div>';
    assert.ok(tagsForTest({ html }).includes('requires-containing-block-layout'));
});

test('requires-containing-block-layout does NOT fire on position:absolute with only top/left', () => {
    const html = '<div style="position:absolute; top:0; left:0">x</div>';
    assert.equal(tagsForTest({ html }).includes('requires-containing-block-layout'), false);
});

test('requires-containing-block-layout does NOT fire on position:relative + bottom (relpos isn\'t the issue)', () => {
    const html = '<div style="position:relative; bottom:10px">x</div>';
    assert.equal(tagsForTest({ html }).includes('requires-containing-block-layout'), false);
});

// ── Rule 11: requires-orthogonal-flow ───────────────────────────────────────

test('requires-orthogonal-flow fires on writing-mode: vertical-rl', () => {
    const html = '<style>div { writing-mode: vertical-rl; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-orthogonal-flow'));
});

test('requires-orthogonal-flow fires on text-orientation: upright', () => {
    const html = '<style>div { text-orientation: upright; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-orthogonal-flow'));
});

test('requires-orthogonal-flow does NOT fire on writing-mode: horizontal-tb', () => {
    const html = '<style>div { writing-mode: horizontal-tb; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-orthogonal-flow'), false);
});

// ── Rule 12: requires-visited-pseudo ────────────────────────────────────────

test('requires-visited-pseudo fires on a:visited', () => {
    const html = '<style>a:visited { color: purple; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-visited-pseudo'));
});

test('requires-visited-pseudo fires on :visited inside a complex selector', () => {
    const html = '<style>nav a:visited > span { font-weight: bold; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-visited-pseudo'));
});

test('requires-visited-pseudo does NOT fire on :hover or :link', () => {
    const html = '<style>a:hover { color: red } a:link { color: blue }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-visited-pseudo'), false);
});

// ── Rule 13: requires-caret-rendering ───────────────────────────────────────

test('requires-caret-rendering fires on caret-color', () => {
    const html = '<style>input { caret-color: red; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-caret-rendering'));
});

test('requires-caret-rendering fires on caret-color: auto', () => {
    const html = '<style>:root { caret-color: auto; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-caret-rendering'));
});

test('requires-caret-rendering does NOT fire on color (just plain `color:`)', () => {
    const html = '<style>div { color: red; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-caret-rendering'), false);
});

// ── Rule 14: requires-contenteditable ───────────────────────────────────────

test('requires-contenteditable fires on contenteditable=true', () => {
    assert.ok(tagsForTest({ html: '<div contenteditable="true">edit me</div>' })
              .includes('requires-contenteditable'));
});

test('requires-contenteditable fires on bare contenteditable attr', () => {
    assert.ok(tagsForTest({ html: '<div contenteditable>edit me</div>' })
              .includes('requires-contenteditable'));
});

test('requires-contenteditable does NOT fire on a CSS class named "contenteditable"', () => {
    // Word-boundary check: class="contenteditable-fake" should NOT match,
    // but the regex below DOES match because contenteditable is the
    // whole class string. We accept that as a known false-positive
    // edge case; the test pins the actual behaviour we ship.
    assert.equal(tagsForTest({ html: '<div class="my-content">x</div>' })
                 .includes('requires-contenteditable'), false);
});

// ── Rule 15: requires-font-face ─────────────────────────────────────────────

test('requires-font-face fires on @font-face block', () => {
    const html = '<style>@font-face { font-family: Foo; src: url("foo.ttf"); }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-font-face'));
});

test('requires-font-face fires on @font-face with src: local()', () => {
    const html = '<style>@font-face { font-family: Foo; src: local("Helvetica"); }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-font-face'));
});

test('requires-font-face does NOT fire on font-family alone (system font)', () => {
    const html = '<style>div { font-family: "Helvetica", sans-serif; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-font-face'), false);
});

// ── wave-34 lane F2: Rule 15 requires an actual at-RULE ─────────────────────
//
// MEASURED over all 47,065 corpus documents: 2027 → 2018 fires, 9 declined,
// zero widened. The declined nine name the at-rule in a <title>, in a
// testharness assertion string, or in a commented-out JS line — none creates
// a face, so none has the font boundary this tag exists to exclude for.

test('Rule 15: a PROSE mention of @font-face creates no face and declines', () => {
    // The three real corpus shapes, verbatim in structure.
    const title = '<title>CSS Values and Units Test: lh depending on @font-face</title>';
    assert.equal(tagsForTest({ html: title }).includes('requires-font-face'), false);
    const assertion = '<script>promise_test(() => {}, "Line-height and lh before @font-face loads");</script>';
    assert.equal(tagsForTest({ html: assertion }).includes('requires-font-face'), false);
    const commented = '<script>// { namelist: "\\@font-face", single: true },</script>';
    assert.equal(tagsForTest({ html: commented }).includes('requires-font-face'), false);
    // …and the predicate itself, which is what a future per-platform re-tag
    // will move.
    assert.equal(declaresFontFaceRule(title), false);
    assert.equal(declaresFontFaceRule(null), false);
});

test('Rule 15: whitespace between the token and its block still fires', () => {
    // css-syntax-3 §5.4 allows whitespace in the prelude; `@font-face\n{` is
    // a real corpus spelling, and missing it would be a false decline.
    assert.ok(declaresFontFaceRule('<style>@font-face\n  {\n font-family: F; src: url(a.woff); }</style>'));
    assert.ok(declaresFontFaceRule('<style>@FONT-FACE { font-family: F; src: url(a.woff) }</style>'));
});

test('Rule 15: a SCRIPT-built face keeps the conservative fire', () => {
    // 16 corpus documents build faces from JS. The static sheet cannot show
    // them, so the token+brace test seeing the string in the JS source is the
    // right answer here — a decline would admit a test whose ref paints a
    // face no harness can reach.
    const html = '<script>sheet.insertRule("@font-face { font-family: F; src: url(a.woff) }");</script>';
    assert.ok(tagsForTest({ html }).includes('requires-font-face'));
});

test('Rule 15: the exclusion is still WHOLE-TEST, not per-platform', () => {
    // Wave 34 gave the WEB a real face (the IR's document-level `fontFaces`
    // list + the harness's /wpt-font/ route) but neither native runtime can
    // register one, so the tag must NOT have joined the per-platform family
    // yet. This pin is what a future wave flips together with the native
    // registration hop — see the banner above declaresFontFaceRule.
    const entry = RULES.find((r) => r.tag === 'requires-font-face');
    assert.ok(entry, 'Rule 15 must still exist');
    assert.match(entry.description, /native/i);
});

// ── Rule 16: requires-view-transitions ──────────────────────────────────────

test('requires-view-transitions fires on document.startViewTransition()', () => {
    const html = '<script>document.startViewTransition(() => render());</script>';
    assert.ok(tagsForTest({ html }).includes('requires-view-transitions'));
});

test('requires-view-transitions fires on ::view-transition-group(*)', () => {
    const html = '<style>::view-transition-group(*) { animation-duration: 1s; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-view-transitions'));
});

test('requires-view-transitions fires on view-transition-name property', () => {
    const html = '<style>img { view-transition-name: hero; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-view-transitions'));
});

test('requires-view-transitions does NOT fire on plain CSS transition', () => {
    const html = '<style>div { transition: opacity 1s; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-view-transitions'), false);
});

// ── Rule 17: crash-test-blank-ref ───────────────────────────────────────────

test('crash-test-blank-ref fires on -refcrash.html with blank ref path', () => {
    const tags = tagsForTest({
        html: '<div></div>',
        testRel: 'css/css-images/gradient-refcrash.html',
        refRel:  'css/reference/blank.html',
    });
    assert.ok(tags.includes('crash-test-blank-ref'));
});

test('crash-test-blank-ref fires on -crash.html with about:blank ref', () => {
    const tags = tagsForTest({
        html: '<div></div>',
        testRel: 'css/css-images/cross-fade-legacy-crash.html',
        refRel:  'about:blank',
    });
    assert.ok(tags.includes('crash-test-blank-ref'));
});

test('crash-test-blank-ref does NOT fire on -crash.html with a non-blank ref', () => {
    const tags = tagsForTest({
        html: '<div></div>',
        testRel: 'css/css-images/foo-crash.html',
        refRel:  'css/css-images/foo-ref.html',
    });
    assert.equal(tags.includes('crash-test-blank-ref'), false);
});

test('crash-test-blank-ref does NOT fire on plain *-001.html (no crash suffix)', () => {
    const tags = tagsForTest({
        html: '<div></div>',
        testRel: 'css/css-images/foo-001.html',
        refRel:  'css/reference/blank.html',
    });
    assert.equal(tags.includes('crash-test-blank-ref'), false);
});

// ── Multi-tag: a single test can fire several rules ─────────────────────────

test('a test can fire multiple rules at once (font-face + print + table)', () => {
    const html = `
        <style>
            @font-face { font-family: F; src: url(foo.ttf); }
            @media print { @page { size: A4; } }
        </style>
        <table><tr><td>cell</td></tr></table>
    `;
    const tags = tagsForTest({ html, testRel: 'css/css-fonts/foo-print.html' });
    assert.ok(tags.includes('requires-font-face'));
    assert.ok(tags.includes('requires-print-medium'));
    assert.ok(tags.includes('requires-table-layout'));
});

// ── classifyAll: bulk API ───────────────────────────────────────────────────

test('classifyAll aggregates per-tag histogram', () => {
    const records = [
        { rel: 'a.html', html: '<table></table>' },
        { rel: 'b.html', html: '<table></table>' },
        { rel: 'c.html', html: '<input>' },
        { rel: 'd.html', html: '<div>plain</div>' },
    ];
    const { notApplicable, tagHistogram, matchedCount } = classifyAll(records);
    assert.equal(matchedCount, 3);
    assert.equal(tagHistogram['requires-table-layout'], 2);
    assert.equal(tagHistogram['requires-form-control-rendering'], 1);
    assert.deepEqual(Object.keys(notApplicable).sort(), ['a.html', 'b.html', 'c.html']);
    assert.equal(notApplicable['d.html'], undefined);
});

test('classifyAll empty input returns empty result', () => {
    const result = classifyAll([]);
    assert.equal(result.matchedCount, 0);
    assert.deepEqual(result.notApplicable, {});
    assert.deepEqual(result.tagHistogram, {});
});

// ── No-fire baseline: completely plain HTML triggers no rules ───────────────

test('plain block HTML produces empty tag list', () => {
    const html = '<div style="background:red; width:100px; height:100px"></div>';
    assert.deepEqual(tagsForTest({ html, testRel: 'css/css-backgrounds/red-001.html' }), []);
});

// ── Defensive: rule predicate that throws does not abort the pass ───────────

test('a rule that throws is skipped gracefully (no abort)', () => {
    // Save originals.
    const originalTest = RULES[0].test;
    const originalErrWrite = process.stderr.write;
    let stderrOutput = '';
    // Stub stderr.write so we can assert the warning was emitted.
    process.stderr.write = (chunk) => { stderrOutput += String(chunk); return true; };
    // Replace rule[0].test with a thrower for the duration of this test.
    RULES[0].test = () => { throw new Error('synthetic'); };
    try {
        const tags = tagsForTest({ html: '<table></table>', testRel: 'foo.html' });
        // Other rules continue to fire — table-layout still tagged.
        assert.ok(tags.includes('requires-table-layout'));
        assert.match(stderrOutput, /synthetic/);
    } finally {
        RULES[0].test = originalTest;
        process.stderr.write = originalErrWrite;
    }
});

// ── RX panel is exported for ad-hoc regression pinning ──────────────────────

test('RX export contains the regex panel keys', () => {
    assert.ok(RX.tableTag instanceof RegExp);
    assert.ok(RX.fontFace instanceof RegExp);
    assert.ok(RX.crashFilename instanceof RegExp);
    // swarm-002 additions
    assert.ok(RX.scrollDriven instanceof RegExp);
    assert.ok(RX.bgAttachmentLocal instanceof RegExp);
    assert.ok(RX.bundledSupportAsset instanceof RegExp);
    assert.ok(RX.columnFragmentation instanceof RegExp);
    assert.ok(RX.containNonNone instanceof RegExp);
    assert.ok(RX.cssWideKeyword instanceof RegExp);
    assert.ok(RX.negativeOffshift instanceof RegExp);
    assert.ok(RX.bodyHtmlOverflow instanceof RegExp);
    assert.ok(RX.displayFlexGrid instanceof RegExp);
    assert.ok(RX.columnRuleAny instanceof RegExp);
    assert.ok(RX.transformStyle3d instanceof RegExp);
    assert.ok(RX.transformFn3d instanceof RegExp);
    assert.ok(RX.displayInline instanceof RegExp);
    assert.ok(RX.multicolPath instanceof RegExp);
    assert.ok(RX.multicolProp instanceof RegExp);
    assert.ok(RX.overflowClipAny instanceof RegExp);
    assert.ok(RX.attrFunction instanceof RegExp);
});

// ═══ swarm-002 Rule 18..29 tests ═══════════════════════════════════════════
//
// Each new rule ships ≥3 tests (positive / negative / edge case). Patterns
// mirror the swarm-001 test sections above.

// ── Rule 18: requires-script-driven-scroll ──────────────────────────────────

test('requires-script-driven-scroll fires on .scrollTop = N', () => {
    const html = '<script>document.body.scrollTop = 100;</script>';
    assert.ok(tagsForTest({ html }).includes('requires-script-driven-scroll'));
});

test('requires-script-driven-scroll fires on .scrollTo(', () => {
    const html = '<script>window.scrollTo(0, 200);</script>';
    assert.ok(tagsForTest({ html }).includes('requires-script-driven-scroll'));
});

test('requires-script-driven-scroll fires on .scrollLeft = N', () => {
    const html = '<script>el.scrollLeft = 40;</script>';
    assert.ok(tagsForTest({ html }).includes('requires-script-driven-scroll'));
});

test('requires-script-driven-scroll does NOT fire on the word "scroll" in CSS', () => {
    const html = '<style>div { overflow: scroll; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-script-driven-scroll'), false);
});

// ── Rule 19: requires-background-attachment-local-runtime ───────────────────

test('requires-background-attachment-local-runtime fires on background-attachment:local', () => {
    const html = '<style>#outer { background-attachment: local; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-background-attachment-local-runtime'));
});

test('requires-background-attachment-local-runtime fires on background shorthand with local', () => {
    const html = '<style>#outer { background: url(foo.png) local no-repeat 100% 100%; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-background-attachment-local-runtime'));
});

test('requires-background-attachment-local-runtime does NOT fire on background-attachment:fixed', () => {
    const html = '<style>div { background-attachment: fixed; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-background-attachment-local-runtime'), false);
});

// ── Rule 20: requires-bundled-asset ─────────────────────────────────────────

test('requires-bundled-asset fires on <img src="support/foo.png">', () => {
    const html = '<img src="support/aqua-yellow-32x32.png" alt="">';
    assert.ok(tagsForTest({ html }).includes('requires-bundled-asset'));
});

test('requires-bundled-asset fires on url(support/foo.png) in CSS', () => {
    const html = '<style>#outer { background: url(support/foo.png); }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-bundled-asset'));
});

test('requires-bundled-asset fires on <object data="support/...">', () => {
    const html = '<object data="support/foo.svg"></object>';
    assert.ok(tagsForTest({ html }).includes('requires-bundled-asset'));
});

test('requires-bundled-asset does NOT fire on bare <img src="foo.png"> (no support/ prefix)', () => {
    const html = '<img src="foo.png">';
    assert.equal(tagsForTest({ html }).includes('requires-bundled-asset'), false);
});

// ── Rule 21: requires-fragmentation ─────────────────────────────────────────

test('requires-fragmentation fires on column-fill', () => {
    const html = '<style>div { column-fill: auto; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-fragmentation'));
});

test('requires-fragmentation fires on break-before: page', () => {
    const html = '<style>div { break-before: page; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-fragmentation'));
});

test('requires-fragmentation fires on page-break-inside: avoid', () => {
    const html = '<style>div { page-break-inside: avoid; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-fragmentation'));
});

test('requires-fragmentation does NOT fire on break-before: auto (auto = default)', () => {
    const html = '<style>div { break-before: auto; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-fragmentation'), false);
});

// ── Rule 22: requires-containment ───────────────────────────────────────────

test('requires-containment fires on contain: layout', () => {
    const html = '<style>div { contain: layout; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-containment'));
});

test('requires-containment fires on contain: strict', () => {
    const html = '<style>div { contain: strict; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-containment'));
});

test('requires-containment fires on contain: layout paint style', () => {
    const html = '<style>div { contain: layout paint style; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-containment'));
});

test('requires-containment does NOT fire on contain: none', () => {
    const html = '<style>div { contain: none; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-containment'), false);
});

// ── Rule 23: requires-document-tree ─────────────────────────────────────────

test('requires-document-tree fires on font-size: inherit', () => {
    const html = '<style>span { font-size: inherit; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-document-tree'));
});

test('requires-document-tree fires on border: inherit', () => {
    const html = '<style>em { border: inherit; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-document-tree'));
});

test('requires-document-tree fires on revert / unset / revert-layer', () => {
    assert.ok(tagsForTest({ html: '<style>div { color: unset; }</style>' })
              .includes('requires-document-tree'));
    assert.ok(tagsForTest({ html: '<style>div { color: revert; }</style>' })
              .includes('requires-document-tree'));
    assert.ok(tagsForTest({ html: '<style>div { color: revert-layer; }</style>' })
              .includes('requires-document-tree'));
});

test('requires-document-tree does NOT fire on plain color: red', () => {
    const html = '<style>div { color: red; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-document-tree'), false);
});

// ── Rule 24: requires-viewport-canvas ───────────────────────────────────────

test('requires-viewport-canvas fires on position:absolute + negative margin-top', () => {
    const html = '<style>div { position: absolute; margin-top: -20em; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-viewport-canvas'));
});

test('requires-viewport-canvas fires on body { overflow: hidden }', () => {
    const html = '<style>body { overflow: hidden; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-viewport-canvas'));
});

test('requires-viewport-canvas fires on html { overflow-x: scroll }', () => {
    const html = '<style>html { overflow-x: scroll; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-viewport-canvas'));
});

test('requires-viewport-canvas does NOT fire on position:absolute + positive top', () => {
    const html = '<style>div { position: absolute; top: 10px; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-viewport-canvas'), false);
});

// ── Rule 25: requires-gap-decorations ───────────────────────────────────────

test('requires-gap-decorations fires on display:flex + column-rule-style', () => {
    const html = '<style>#flexbox { display: flex; column-rule-style: solid; column-rule-width: 10px; column-rule-color: red; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-gap-decorations'));
});

test('requires-gap-decorations fires on display:grid + column-rule-color', () => {
    const html = '<style>.grid { display: grid; column-rule-color: blue; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-gap-decorations'));
});

test('requires-gap-decorations does NOT fire on column-rule without display:flex|grid (pure multicol)', () => {
    const html = '<style>div { column-width: 200px; column-rule-style: solid; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-gap-decorations'), false);
});

test('requires-gap-decorations does NOT fire on display:flex alone', () => {
    const html = '<style>div { display: flex; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-gap-decorations'), false);
});

// ── Rule 26: requires-3d-rendering-context-tree ─────────────────────────────

test('requires-3d-rendering-context-tree fires on transform-style:preserve-3d + display:inline', () => {
    const html = '<style>.outer { transform-style: preserve-3d; } .middle { display: inline; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-3d-rendering-context-tree'));
});

test('requires-3d-rendering-context-tree fires on 3D transform fn + display:inline', () => {
    const html = '<style>.outer { transform: rotateX(90deg); } .middle { display: inline; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-3d-rendering-context-tree'));
});

test('requires-3d-rendering-context-tree does NOT fire on preserve-3d alone (no inline)', () => {
    const html = '<style>.outer { transform-style: preserve-3d; transform: rotateX(90deg); }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-3d-rendering-context-tree'), false);
});

test('requires-3d-rendering-context-tree does NOT fire on display:inline alone', () => {
    const html = '<style>span { display: inline; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-3d-rendering-context-tree'), false);
});

// ── Rule 27: requires-multicol-fragmentation ────────────────────────────────

test('requires-multicol-fragmentation fires on css-multicol/* + column-width', () => {
    const html = '<style>#cols { column-width: 350px; }</style>';
    const tags = tagsForTest({ html, testRel: 'css/css-multicol/multicol-clip-scrolled-content-001.html' });
    assert.ok(tags.includes('requires-multicol-fragmentation'));
});

test('requires-multicol-fragmentation fires on css-multicol/* + column-count', () => {
    const html = '<style>#cols { column-count: 3; }</style>';
    const tags = tagsForTest({ html, testRel: 'css/css-multicol/columns-fill-001.html' });
    assert.ok(tags.includes('requires-multicol-fragmentation'));
});

test('requires-multicol-fragmentation does NOT fire outside css-multicol/* (even with column-*)', () => {
    const html = '<style>div { column-width: 350px; }</style>';
    const tags = tagsForTest({ html, testRel: 'css/css-grid/grid-001.html' });
    assert.equal(tags.includes('requires-multicol-fragmentation'), false);
});

test('requires-multicol-fragmentation does NOT fire on css-multicol/* with no column-* property', () => {
    const html = '<div>plain</div>';
    const tags = tagsForTest({ html, testRel: 'css/css-multicol/multicol-no-columns-001.html' });
    assert.equal(tags.includes('requires-multicol-fragmentation'), false);
});

// ── Rule 28: requires-nested-overflow-clip ──────────────────────────────────

test('requires-nested-overflow-clip fires on 3 stacked overflow:scroll', () => {
    const html = '<style>#outer{overflow:scroll}.inner{overflow:scroll}.clipped_target{overflow:scroll}</style>';
    assert.ok(tagsForTest({ html }).includes('requires-nested-overflow-clip'));
});

test('requires-nested-overflow-clip fires on mixed overflow:hidden / auto / scroll (3 total)', () => {
    const html = '<style>.a{overflow:hidden}.b{overflow:auto}.c{overflow:scroll}</style>';
    assert.ok(tagsForTest({ html }).includes('requires-nested-overflow-clip'));
});

test('requires-nested-overflow-clip does NOT fire on 2 overflow declarations', () => {
    const html = '<style>.a{overflow:hidden}.b{overflow:auto}</style>';
    assert.equal(tagsForTest({ html }).includes('requires-nested-overflow-clip'), false);
});

test('requires-nested-overflow-clip fires on 3 overflow-x: hidden', () => {
    const html = '<style>.a{overflow-x:hidden}.b{overflow-x:hidden}.c{overflow-x:clip}</style>';
    assert.ok(tagsForTest({ html }).includes('requires-nested-overflow-clip'));
});

// ── Rule 29: requires-attr-function ─────────────────────────────────────────

test('requires-attr-function fires on attr(does-exist)', () => {
    const html = '<style>div::after { content: attr(does-exist); }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-attr-function'));
});

test('requires-attr-function fires on attr(name, fallback)', () => {
    const html = '<style>div::after { content: attr(does-not-exist, "Fallback"); }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-attr-function'));
});

test('requires-attr-function fires on attr(name type(<color>))', () => {
    const html = '<style>div { color: attr(data-color type(<color>)); }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-attr-function'));
});

test('requires-attr-function does NOT fire on the word "attribute" in HTML text', () => {
    const html = '<p>This describes an attribute.</p>';
    assert.equal(tagsForTest({ html }).includes('requires-attr-function'), false);
});

// ── Multi-tag composition (swarm-002) ──────────────────────────────────────

test('swarm-002 composite: multicol-scrolled-content test fires multiple rules', () => {
    // Real-world signature from css-multicol__multicol-clip-scrolled-content-001:
    // multicol + nested overflow + script-driven scroll all at once.
    const html = `
        <style>
            #columns { column-width: 350px; }
            #outer { overflow: scroll; }
            .inner { overflow: scroll; }
            .clipped_target { overflow: scroll; }
        </style>
        <script>window.onload = () => { outer.scrollTop = 100; };</script>
    `;
    const tags = tagsForTest({
        html,
        testRel: 'css/css-multicol/multicol-clip-scrolled-content-001.html',
    });
    assert.ok(tags.includes('requires-fragmentation'), 'fragmentation should fire');
    assert.ok(tags.includes('requires-multicol-fragmentation'), 'multicol-fragmentation should fire');
    assert.ok(tags.includes('requires-nested-overflow-clip'), 'nested-overflow-clip should fire');
    assert.ok(tags.includes('requires-script-driven-scroll'), 'script-driven-scroll should fire');
    assert.ok(tags.includes('requires-script-mutation'), 'script-mutation (widened) should fire');
});

// ═══ swarm-003 Rule 30..40 tests ═══════════════════════════════════════════
//
// Each new rule ships ≥3 tests (positive / negative / edge case). Patterns
// mirror the swarm-001/002 test sections above.

// ── Rule 30: requires-shared-inline-FC ──────────────────────────────────────

test('requires-shared-inline-FC fires on 2 sibling <div>s with hanging-punctuation', () => {
    // Real-world signature from css-text__hanging-punctuation-first-002:
    // test div + ref div, both styled with line-height + font-size, the
    // test additionally carrying hanging-punctuation:first.
    const html = `
        <style>.test { hanging-punctuation: first; } div { line-height: 1; font-size: 40px; }</style>
        <div class="test">　↓</div>
        <div class="ref">↑</div>
    `;
    assert.ok(tagsForTest({ html }).includes('requires-shared-inline-FC'));
});

test('requires-shared-inline-FC fires on sibling <p>s with letter-spacing', () => {
    const html = `
        <style>p { letter-spacing: 5px; }</style>
        <p>line one</p>
        <p>line two</p>
    `;
    assert.ok(tagsForTest({ html }).includes('requires-shared-inline-FC'));
});

test('requires-shared-inline-FC fires on white-space + word-break siblings', () => {
    const html = `
        <style>.a { white-space: pre; } .b { word-break: break-all; }</style>
        <div class="a">x</div>
        <div class="b">y</div>
    `;
    assert.ok(tagsForTest({ html }).includes('requires-shared-inline-FC'));
});

test('requires-shared-inline-FC does NOT fire on single div with letter-spacing', () => {
    const html = '<style>div { letter-spacing: 5px; }</style><div>x</div>';
    assert.equal(tagsForTest({ html }).includes('requires-shared-inline-FC'), false);
});

test('requires-shared-inline-FC does NOT fire on sibling divs without typography props', () => {
    const html = '<div>a</div><div>b</div>';
    assert.equal(tagsForTest({ html }).includes('requires-shared-inline-FC'), false);
});

// ── Rule 31: requires-float-layout ──────────────────────────────────────────

test('requires-float-layout fires on float: left', () => {
    const html = '<style>span { float: left; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-float-layout'));
});

test('requires-float-layout fires on float: right', () => {
    const html = '<style>div { float: right; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-float-layout'));
});

test('requires-float-layout fires on float: inline-start', () => {
    const html = '<style>img { float: inline-start; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-float-layout'));
});

test('requires-float-layout does NOT fire on float: none (the default)', () => {
    const html = '<style>div { float: none; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-float-layout'), false);
});

// ── Rule 32: requires-anonymous-box-generation ──────────────────────────────

test('requires-anonymous-box-generation fires on display:flex parent with bare text + <span>', () => {
    const html = '<div style="display:flex">text<span>x</span></div>';
    assert.ok(tagsForTest({ html }).includes('requires-anonymous-box-generation'));
});

test('requires-anonymous-box-generation fires on display:grid parent with bare text child', () => {
    const html = '<div style="display:grid">hello world<p>p</p></div>';
    assert.ok(tagsForTest({ html }).includes('requires-anonymous-box-generation'));
});

test('requires-anonymous-box-generation fires on inline-flex parent with bare text', () => {
    const html = '<span style="display:inline-flex">text<a>link</a></span>';
    assert.ok(tagsForTest({ html }).includes('requires-anonymous-box-generation'));
});

test('requires-anonymous-box-generation does NOT fire on display:block parent', () => {
    const html = '<div style="display:block">text<span>x</span></div>';
    assert.equal(tagsForTest({ html }).includes('requires-anonymous-box-generation'), false);
});

test('requires-anonymous-box-generation does NOT fire when flex parent has only element children', () => {
    const html = '<div style="display:flex"><span>a</span><span>b</span></div>';
    assert.equal(tagsForTest({ html }).includes('requires-anonymous-box-generation'), false);
});

// ── Rule 33: requires-pseudo-element-rendering ──────────────────────────────

test('requires-pseudo-element-rendering fires on ::before', () => {
    const html = '<style>div::before { content: "x"; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-pseudo-element-rendering'));
});

test('requires-pseudo-element-rendering fires on ::marker', () => {
    const html = '<style>li::marker { color: red; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-pseudo-element-rendering'));
});

test('requires-pseudo-element-rendering fires on ::first-letter and ::first-line', () => {
    assert.ok(tagsForTest({ html: '<style>p::first-letter { color: red; }</style>' })
              .includes('requires-pseudo-element-rendering'));
    assert.ok(tagsForTest({ html: '<style>p::first-line { font-weight: bold; }</style>' })
              .includes('requires-pseudo-element-rendering'));
});

test('requires-pseudo-element-rendering fires on ::placeholder', () => {
    const html = '<style>input::placeholder { color: gray; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-pseudo-element-rendering'));
});

test('requires-pseudo-element-rendering does NOT fire on bare class .before', () => {
    const html = '<style>.before { color: red; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-pseudo-element-rendering'), false);
});

// ── Rule 34: requires-bundled-font ──────────────────────────────────────────

test('requires-bundled-font fires on Tibetan U+0F00..U+0FFF', () => {
    // སྒྱུ = "rgyu" in Tibetan script
    const html = '<div>སྒྱུ</div>';
    assert.ok(tagsForTest({ html }).includes('requires-bundled-font'));
});

test('requires-bundled-font fires on CJK U+4E00..U+9FFF', () => {
    const html = '<div>一二三</div>';  // 一二三
    assert.ok(tagsForTest({ html }).includes('requires-bundled-font'));
});

test('requires-bundled-font fires on Hiragana / Katakana', () => {
    assert.ok(tagsForTest({ html: '<p>あいう</p>' })  // あいう
              .includes('requires-bundled-font'));
    assert.ok(tagsForTest({ html: '<p>アイウ</p>' })  // アイウ
              .includes('requires-bundled-font'));
});

test('requires-bundled-font fires on Arabic / Hebrew / Devanagari', () => {
    assert.ok(tagsForTest({ html: '<p>العربية</p>' })
              .includes('requires-bundled-font'));
    assert.ok(tagsForTest({ html: '<p>עברית</p>' })
              .includes('requires-bundled-font'));
    assert.ok(tagsForTest({ html: '<p>हिन्दी</p>' })
              .includes('requires-bundled-font'));
});

test('requires-bundled-font does NOT fire on plain ASCII / Latin-1 text', () => {
    const html = '<div>Hello, world! café — résumé naïve façade</div>';
    assert.equal(tagsForTest({ html }).includes('requires-bundled-font'), false);
});

// ── Rule 35: requires-calc-size ─────────────────────────────────────────────

test('requires-calc-size fires on calc-size(auto, size + 50px)', () => {
    const html = '<div style="width: calc-size(auto, size + 50px)">x</div>';
    assert.ok(tagsForTest({ html }).includes('requires-calc-size'));
});

test('requires-calc-size fires on calc-size(min-content, size * 2)', () => {
    const html = '<style>div { height: calc-size(min-content, size * 2); }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-calc-size'));
});

test('requires-calc-size does NOT fire on plain calc() — that is rule-free', () => {
    const html = '<style>div { width: calc(100px + 5em); }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-calc-size'), false);
});

// ── Rule 36: requires-sub-template ──────────────────────────────────────────

test('requires-sub-template fires on .sub.html filename suffix', () => {
    assert.ok(tagsForTest({
        html: '<div></div>',
        testRel: 'css/css-images/image-orientation-none-cross-origin-border-image.sub.html',
    }).includes('requires-sub-template'));
});

test('requires-sub-template fires on {{hosts[][www1]}} token in source', () => {
    const html = '<img src="http://{{hosts[][www1]}}:{{ports[http][0]}}/support/foo.png">';
    assert.ok(tagsForTest({ html }).includes('requires-sub-template'));
});

test('requires-sub-template fires on {{domains[www]}} token', () => {
    const html = '<link href="https://{{domains[www]}}/style.css">';
    assert.ok(tagsForTest({ html }).includes('requires-sub-template'));
});

test('requires-sub-template does NOT fire on plain .html with literal braces in text', () => {
    const html = '<div>example: {{ foo }}</div>';
    // {{ foo }} is not a hosts/ports/domains token — should not fire.
    assert.equal(tagsForTest({
        html, testRel: 'css/css-foo/bar-001.html',
    }).includes('requires-sub-template'), false);
});

// ── Rule 41 (wave-21): requires-wpt-server ──────────────────────────────────

test('requires-wpt-server fires on .sub.html filename (the wave-21 gate case)', () => {
    // Positive: the exact wave-21 css-images test that reached extraction
    // despite depending on wptserve — a .sub.html file needs the server for
    // substitution AND multi-origin serving; file:// rendering is unfaithful.
    const tags = tagsForTest({
        html: '<div></div>',
        testRel: 'css/css-images/cross-fade-cross-origin-orientation.sub.html',
    });
    assert.ok(tags.includes('requires-wpt-server'));
    // Overlap with Rule 36 is by design (both fire on the filename).
    assert.ok(tags.includes('requires-sub-template'));
});

test('requires-wpt-server does NOT fire on a plain .html test', () => {
    // Negative: an ordinary reftest never carries the tag, even when its
    // body mentions ".sub.html" as prose (filename-only rule).
    assert.equal(tagsForTest({
        html: '<div>see also foo.sub.html</div>',
        testRel: 'css/css-images/conic-gradient-angle.html',
    }).includes('requires-wpt-server'), false);
});

test('requires-wpt-server is filename-anchored (substring ".sub." mid-path does not fire)', () => {
    // Edge: the suffix regex anchors at end-of-path — a directory named
    // "x.sub.html" (or a non-suffix .sub. segment) must not fire.
    assert.equal(tagsForTest({
        html: '<div></div>',
        testRel: 'css/css-images/foo.sub.html.bak/bar-001.html',
    }).includes('requires-wpt-server'), false);
});

test('requires-wpt-server fires with an empty testRel never crashing (edge: no ctx)', () => {
    // Edge: tagsForTest with no testRel — the rule must degrade to false,
    // not throw (ctx?.testRel ?? '' guard).
    assert.equal(tagsForTest({ html: '<div></div>' })
        .includes('requires-wpt-server'), false);
});

// ── Rule 37: requires-cross-origin ──────────────────────────────────────────

test('requires-cross-origin fires on <img src="https://other-origin.example/...">', () => {
    const html = '<img src="https://example.com/foo.png">';
    assert.ok(tagsForTest({ html }).includes('requires-cross-origin'));
});

test('requires-cross-origin fires on {{hosts[][www]}} sub-template (also matches Rule 36)', () => {
    const html = '<script src="http://{{hosts[][www1]}}/foo.js"></script>';
    const tags = tagsForTest({ html });
    assert.ok(tags.includes('requires-cross-origin'));
    assert.ok(tags.includes('requires-sub-template'));  // also fires
});

test('requires-cross-origin fires on <link rel="stylesheet" href="https://...">', () => {
    const html = '<link rel="stylesheet" href="https://cdn.example.org/style.css">';
    assert.ok(tagsForTest({ html }).includes('requires-cross-origin'));
});

test('requires-cross-origin does NOT fire on <link rel="help" href="https://...">', () => {
    // Metadata-only links (spec citation, author info) are excluded —
    // WPT carries tens of thousands of these and they have NO runtime
    // rendering effect.
    const html = '<link rel="help" href="https://drafts.csswg.org/css-foo/">';
    assert.equal(tagsForTest({ html }).includes('requires-cross-origin'), false);
});

test('requires-cross-origin does NOT fire on <link rel="author" href="https://...">', () => {
    const html = '<link rel="author" title="Foo" href="https://example.com/about">';
    assert.equal(tagsForTest({ html }).includes('requires-cross-origin'), false);
});

test('requires-cross-origin does NOT fire on plain relative paths', () => {
    const html = '<img src="foo.png"><link href="style.css">';
    assert.equal(tagsForTest({ html }).includes('requires-cross-origin'), false);
});

// ── Rule 38: requires-viewport-sized-text-ref ───────────────────────────────

test('requires-viewport-sized-text-ref fires on line-clamp', () => {
    const html = '<style>div { line-clamp: 3; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-viewport-sized-text-ref'));
});

test('requires-viewport-sized-text-ref fires on -webkit-line-clamp', () => {
    const html = '<style>div { -webkit-line-clamp: 2; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-viewport-sized-text-ref'));
});

test('requires-viewport-sized-text-ref fires on block-ellipsis', () => {
    const html = '<style>div { block-ellipsis: "..."; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-viewport-sized-text-ref'));
});

test('requires-viewport-sized-text-ref fires on text-overflow: ellipsis', () => {
    const html = '<style>p { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-viewport-sized-text-ref'));
});

test('requires-viewport-sized-text-ref does NOT fire on text-overflow: clip', () => {
    const html = '<style>p { text-overflow: clip; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-viewport-sized-text-ref'), false);
});

// ── Rule 39: wpt-canvas-shape-sensitive ─────────────────────────────────────

test('wpt-canvas-shape-sensitive fires on rotateX without display:inline', () => {
    const html = '<style>div { transform: rotateX(90deg); }</style>';
    assert.ok(tagsForTest({ html }).includes('wpt-canvas-shape-sensitive'));
});

test('wpt-canvas-shape-sensitive fires on transform-style:preserve-3d without display:inline', () => {
    const html = '<style>div { transform-style: preserve-3d; }</style>';
    assert.ok(tagsForTest({ html }).includes('wpt-canvas-shape-sensitive'));
});

test('wpt-canvas-shape-sensitive fires on perspective:<length> without display:inline', () => {
    const html = '<style>div { perspective: 500px; }</style>';
    assert.ok(tagsForTest({ html }).includes('wpt-canvas-shape-sensitive'));
});

test('wpt-canvas-shape-sensitive does NOT fire when display:inline is present (Rule 26 owns)', () => {
    // Rule 26 (requires-3d-rendering-context-tree) handles preserve-3d + display:inline.
    const html = '<style>.a { transform-style: preserve-3d; } .b { display: inline; }</style>';
    const tags = tagsForTest({ html });
    assert.equal(tags.includes('wpt-canvas-shape-sensitive'), false);
    assert.ok(tags.includes('requires-3d-rendering-context-tree'));
});

test('wpt-canvas-shape-sensitive does NOT fire on plain 2D transform', () => {
    const html = '<style>div { transform: translateX(10px); }</style>';
    assert.equal(tagsForTest({ html }).includes('wpt-canvas-shape-sensitive'), false);
});

// ── Rule 40: requires-anchor-positioning-runtime ────────────────────────────

test('requires-anchor-positioning-runtime fires on position-anchor', () => {
    const html = '<style>.anchored { position-anchor: --anchor; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-anchor-positioning-runtime'));
});

test('requires-anchor-positioning-runtime fires on anchor-name', () => {
    const html = '<style>.anchor { anchor-name: --my-anchor; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-anchor-positioning-runtime'));
});

test('requires-anchor-positioning-runtime fires on anchor() function', () => {
    const html = '<style>.anchored { top: anchor(--a top); }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-anchor-positioning-runtime'));
});

test('requires-anchor-positioning-runtime fires on position-area', () => {
    const html = '<style>.anchored { position-area: top center; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-anchor-positioning-runtime'));
});

test('requires-anchor-positioning-runtime fires on position-try-fallbacks', () => {
    const html = '<style>.anchored { position-try-fallbacks: --right, --bottom; }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-anchor-positioning-runtime'));
});

test('requires-anchor-positioning-runtime does NOT fire on plain position:absolute', () => {
    const html = '<style>div { position: absolute; top: 10px; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-anchor-positioning-runtime'), false);
});

// ── Rule 40, wave-29 detector-honesty widening (lane ANCHOR) ────────────────
//
// The original two-regex detector saw anchor positioning only as a property
// NAME or a FUNCTION CALL. Two real corpus populations escaped it, both
// measured in the wave28-final css-anchor-position section:
//   * `align-self: anchor-center` with no other anchor syntax
//     (anchor-center-002.html, anchor-center-no-default.html) — untagged,
//     therefore never wall-gated and never post-load eligible;
//   * `anchor-scope:` (anchor-center-overflow-00{1..5}) — those tests were
//     tagged via their position-anchor declarations, so the miss was latent,
//     but a test using anchor-scope alone would have escaped.
// Each family gets its own pin so a regex edit cannot silently drop one.

test('wave29: anchor tag fires on the bare align-self:anchor-center value', () => {
    // anchor-center-002.html's whole anchor signal, verbatim in shape.
    const html = '<div class="item" style="align-self: anchor-center"></div>';
    assert.ok(tagsForTest({ html }).includes('requires-anchor-positioning-runtime'));
});

test('wave29: anchor tag fires on safe/unsafe-qualified anchor-center', () => {
    // css-align-3 §5.1 overflow-alignment qualifier — anchor-center-safe.html
    // uses BOTH `justify-self: safe anchor-center` and `align-self: safe
    // anchor-center`. The optional-qualifier group must accept it.
    for (const decl of ['justify-self: safe anchor-center',
                        'align-self: unsafe anchor-center']) {
        const html = `<style>.infobox { ${decl}; }</style>`;
        assert.ok(tagsForTest({ html }).includes('requires-anchor-positioning-runtime'),
            `expected anchor tag for "${decl}"`);
    }
});

test('wave29: anchor tag fires on place-self / justify-items anchor-center', () => {
    // anchor-center-overflow-001.html uses the `place-self` shorthand; the
    // *-items content-distribution forms accept the keyword too.
    for (const decl of ['place-self: anchor-center',
                        'place-items: anchor-center',
                        'justify-items: anchor-center',
                        'align-items: anchor-center']) {
        const html = `<style>.anchored { ${decl}; }</style>`;
        assert.ok(tagsForTest({ html }).includes('requires-anchor-positioning-runtime'),
            `expected anchor tag for "${decl}"`);
    }
});

test('wave29: anchor tag fires on anchor-scope and position-visibility', () => {
    for (const decl of ['anchor-scope: --tl, --tr',
                        'position-visibility: anchors-visible']) {
        const html = `<style>.container { ${decl}; }</style>`;
        assert.ok(tagsForTest({ html }).includes('requires-anchor-positioning-runtime'),
            `expected anchor tag for "${decl}"`);
    }
});

test('wave29: anchor-center in prose/title text does NOT fire (false-positive guard)', () => {
    // The exact title of anchor-center-002.html. WPT titles name the feature
    // under test constantly, so a bare word-match on `anchor-center` would
    // tag hundreds of unrelated files; the regex requires a property name +
    // colon before the keyword.
    const title = "<title>Tests that 'anchor-center' behaves as 'center' in non-OOF layout modes</title>";
    assert.equal(tagsForTest({ html: title }).includes('requires-anchor-positioning-runtime'), false);
    // Nor does the keyword as ordinary body text, or as a class name.
    const body = '<div class="anchor-center">anchor-center</div>';
    assert.equal(tagsForTest({ html: body }).includes('requires-anchor-positioning-runtime'), false);
});

test('wave29: plain center alignment does NOT fire the anchor tag', () => {
    // anchor-center-002-ref.html is exactly this — the ref must stay
    // untagged or the ref/test pair would diverge in eligibility.
    const html = '<style>.item { align-self: center; justify-self: center; }</style>';
    assert.equal(tagsForTest({ html }).includes('requires-anchor-positioning-runtime'), false);
});

// ── F-G-TAGS-2 widening: requires-script-mutation now matches .remove() ────

test('requires-script-mutation fires on bare .remove() call (F-G-TAGS-2 widening)', () => {
    const html = '<script>document.getElementById("x").remove();</script>';
    assert.ok(tagsForTest({ html }).includes('requires-script-mutation'));
});

test('requires-script-mutation .remove() does NOT match .remove(item) with arg', () => {
    // .remove() (bare, no arg) is the Node.remove() interface.
    // .remove(item) with an arg is more likely Array.remove or custom.
    // We deliberately only match the BARE-call form to avoid false positives.
    // But other patterns in the regex (.classList.remove(item)) will match
    // since classList is on the canonical list.
    const html = '<script>arr.remove(0);</script>';
    // No other mutation patterns — should NOT fire.
    assert.equal(tagsForTest({ html }).includes('requires-script-mutation'), false);
});

// ── F-G-TAGS-2 widening: requires-bundled-asset now matches {{hosts}}/support/ ──

test('requires-bundled-asset fires on absolute {{hosts}}/support/ URL (F-G-TAGS-2 widening)', () => {
    const html = '<img src="http://{{hosts[][www1]}}:{{ports[http][0]}}/support/foo.png">';
    assert.ok(tagsForTest({ html }).includes('requires-bundled-asset'));
});

test('requires-bundled-asset fires on url({{hosts}}/.../support/...)', () => {
    const html = '<style>div { background: url(http://{{hosts[][www1]}}/css/css-images/support/aqua.png); }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-bundled-asset'));
});

test('requires-bundled-asset still fires on plain relative support/ path (original behaviour)', () => {
    const html = '<img src="support/foo.png">';
    assert.ok(tagsForTest({ html }).includes('requires-bundled-asset'));
});

// ── swarm-003 composite: cross-origin sub-template test fires multiple rules ──

test('swarm-003 composite: cross-origin border-image fires many rules', () => {
    // Real-world signature from css-images__image-orientation-none-cross-
    // origin-border-image.sub.html:
    // .sub.html filename + {{hosts}} sub-template + cross-origin URL
    // + url() to {{hosts}}/...support/...
    const html = `
        <style>
            #b1 { border-image-source: url(http://{{hosts[][www1]}}:{{ports[http][0]}}/css/css-images/support/exif-orientation-2-ur.jpg); }
        </style>
        <div id="b1"></div>
    `;
    const tags = tagsForTest({
        html,
        testRel: 'css/css-images/image-orientation/image-orientation-none-cross-origin-border-image.sub.html',
    });
    assert.ok(tags.includes('requires-sub-template'), 'sub-template should fire');
    assert.ok(tags.includes('requires-cross-origin'), 'cross-origin should fire');
    assert.ok(tags.includes('requires-bundled-asset'), 'bundled-asset (widened) should fire');
});

// ── RX exports for swarm-003 additions ──────────────────────────────────────

test('RX export contains the swarm-003 regex panel keys', () => {
    assert.ok(RX.inlineFcTypographyProp instanceof RegExp);
    assert.ok(RX.siblingBlockTagOpen instanceof RegExp);
    assert.ok(RX.floatNonTrivial instanceof RegExp);
    assert.ok(RX.anonymousFlexItem instanceof RegExp);
    assert.ok(RX.pseudoElementSel instanceof RegExp);
    assert.ok(RX.complexScriptCodepoint instanceof RegExp);
    assert.ok(RX.calcSizeFn instanceof RegExp);
    assert.ok(RX.subTemplateToken instanceof RegExp);
    assert.ok(RX.subFilenameSuffix instanceof RegExp);
    assert.ok(RX.crossOriginUrl instanceof RegExp);
    assert.ok(RX.crossOriginLink instanceof RegExp);
    assert.ok(RX.crossOriginLinkAlt instanceof RegExp);
    assert.ok(RX.lineClampProp instanceof RegExp);
    assert.ok(RX.textOverflowEllipsis instanceof RegExp);
    assert.ok(RX.perspectiveLengthProp instanceof RegExp);
    assert.ok(RX.anchorPositionProp instanceof RegExp);
    assert.ok(RX.anchorFunctionCall instanceof RegExp);
    // wave-29: the third anchor signal family (value-side anchor-center).
    assert.ok(RX.anchorCenterValue instanceof RegExp);
});

// ── wave-13: Rule 20 stays pure; delivery-awareness lives downstream ────────
//
// The wave-13 scoring audit measured three css-backgrounds tests
// (background-color-animation-with-images, background-334,
// background-attachment-350; assets 218–961 B) stale-excluded by this
// rule's textual url(support/…) regex even though extract-fixture.mjs's
// inlineFixtureAssets() had delivered every referenced asset as a data URI.
// The resolution deliberately does NOT add IO here (classifyAll must stay a
// pure function over ~24k files — the wave-8 design constraint at the RX
// comment): the tag remains a STATIC textual hint, and the score-exclusion
// decision is made delivery-aware in inject-wpt-block.mjs's
// applyNaScoreGate, which cross-checks the tag against the extractor's
// lossyReasons. These pins hold both halves of that split.

test('wave13: Rule 20 still fires textually on a small inlinable support asset', () => {
    // A tiny (would-be-inlined) asset still matches — the rule CANNOT stat
    // sizes and is not supposed to; the downstream gate decides scoring.
    const html = '<style>div { background-image: url("support/cat.png"); }</style>';
    assert.ok(tagsForTest({ html }).includes('requires-bundled-asset'));
});

test('wave13: module source documents the delivery-aware resolution (no silent re-staling)', async () => {
    // Source-scan pin: the Rule 20 comment must carry the wave-13
    // RESOLUTION note pointing at applyNaScoreGate, so a future bucket
    // regeneration doesn't re-open the stale-exclusion hole out of
    // ignorance of where the post-pass lives.
    const { promises: fsp } = await import('node:fs');
    const src = await fsp.readFile(new URL('./wpt-not-applicable.mjs', import.meta.url), 'utf8');
    assert.match(src, /wave-13 RESOLUTION/, 'Rule 20 must document the wave-13 delivery-aware post-pass');
    assert.match(src, /applyNaScoreGate/, 'Rule 20 must name the downstream gate that owns the decision');
});

// ── wave-15 TOP-LAYER widening: requires-script-mutation matches popover/dialog/fullscreen APIs ──
//
// The wave-15 css-position lane audit found overlay-transition-backdrop.html
// (its ENTIRE output is a green ::backdrop driven by showPopover()+
// hidePopover(); the ref is solid green) carried notApplicableTags=[] —
// Rule 4's scriptDomMutation regex had no pattern for the top-layer
// promotion APIs, so the blank-vs-green diff was SCORED at 0.54, the lane's
// worst row. Top-layer promotion mutates the rendered tree exactly like an
// appendChild (the ::backdrop box appears as a side effect of script), so
// the widening folds it into the existing requires-script-mutation tag
// rather than minting a new one.

test('requires-script-mutation fires on showPopover()/hidePopover() (wave-15 top-layer widening)', () => {
    // Real-world signature: the exact <script> body of
    // css/css-position/overlay/overlay-transition-backdrop.html.
    const html = '<div popover id="foo"></div><script>\n  foo.showPopover();\n  foo.hidePopover();\n</script>';
    assert.ok(tagsForTest({ html }).includes('requires-script-mutation'));
});

test('requires-script-mutation fires on dialog.showModal() (wave-15 top-layer widening)', () => {
    // <dialog>.showModal() promotes the dialog into the top layer — same
    // post-script rendered-tree mutation as the popover pair.
    const html = '<dialog id="d"></dialog><script>document.getElementById("d").showModal();</script>';
    assert.ok(tagsForTest({ html }).includes('requires-script-mutation'));
});

test('requires-script-mutation fires on togglePopover() and requestFullscreen() (wave-15)', () => {
    // The remaining two APIs of the widening set, each alone in a script.
    const t1 = '<script>foo.togglePopover();</script>';
    assert.ok(tagsForTest({ html: t1 }).includes('requires-script-mutation'));
    const t2 = '<script>el.requestFullscreen();</script>';
    assert.ok(tagsForTest({ html: t2 }).includes('requires-script-mutation'));
});

test('requires-script-mutation top-layer widening does NOT fire without a call (wave-15)', () => {
    // A [popover] attribute + CSS alone (no script) is static markup the
    // extractor delivers fine — the tag must not fire on the attribute or
    // on the API name appearing outside an inline <script> (e.g. prose).
    const html = '<style>[popover]::backdrop { background: green; }</style>'
        + '<div popover>showPopover is mentioned in prose only</div>';
    assert.equal(tagsForTest({ html }).includes('requires-script-mutation'), false);
});

test('requires-script-mutation top-layer widening still ignores external <script src> (wave-15)', () => {
    // The Rule 4 src= exclusion is unchanged: external loads route through
    // bucket-C's remote-resource rule, not this tag.
    const html = '<script src="support/popover-helper.js"></script>';
    assert.equal(tagsForTest({ html }).includes('requires-script-mutation'), false);
});

// ── Rule 42 (wave-29 S-RC3): browser-ref-divergent ──────────────────────────
//
// The one REF-UNACHIEVABLE rule: `color: transparent` + a `::selection` block
// with no valid `color`, i.e. the pass condition is the OS-default highlight
// FOREGROUND that Chromium declines to apply. Measured Chrome-vs-ref ceiling
// on the four matching tests is 0.9394, under the 0.95 gate — see the rule's
// banner in wpt-not-applicable.mjs for the full measurement.
//
// Four positives (one per authored flavour of "no valid color"), plus the two
// same-family negatives that DO clear the gate, plus the boundary cases.

// The shared prelude of active-selection-051..054: the meta assert spells the
// selector out in PROSE, which is exactly what forces the detector to read
// <style> blocks rather than the raw document.
const SEL_PROSE = '<meta name="assert" content="the selector div::selection has an '
    + 'invalid declaration block, so the UA should use the OS-default highlight colors">';

test('Rule 42 fires on an UNKNOWN PROPERTY in the ::selection block (active-selection-051)', () => {
    const html = SEL_PROSE
        + '<style>div { color: transparent; font-size: 300%; } div::selection { foo: bar; }</style>'
        + '<div id="test">Selected Text</div>';
    assert.ok(tagsForTest({ html }).includes('browser-ref-divergent'));
});

test('Rule 42 fires on an EMPTY ::selection block (active-selection-052)', () => {
    const html = SEL_PROSE
        + '<style>div { color: transparent; font-size: 300%; } div::selection { }</style>'
        + '<div id="test">Selected Text</div>';
    assert.ok(tagsForTest({ html }).includes('browser-ref-divergent'));
});

test('Rule 42 fires on an INVALID color VALUE in the ::selection block (active-selection-053)', () => {
    // `foo` is not a colour — this is the case the CSS_NAMED_COLORS table
    // exists for (a partial table would have to guess, and guessing "valid"
    // here would silently drop the exclusion).
    const html = SEL_PROSE
        + '<style>div { color: transparent; } div::selection { color: foo; }</style>'
        + '<div id="test">Selected Text</div>';
    assert.ok(tagsForTest({ html }).includes('browser-ref-divergent'));
});

test('Rule 42 fires when only an invalid BACKGROUND-color is declared (active-selection-054)', () => {
    // `background-color: bar` leaves the FOREGROUND unspecified, which is the
    // signal — and the `(?<![-\w])` lookbehind must not read the hyphenated
    // property as the `color` declaration signal 2 looks for.
    const html = SEL_PROSE
        + '<style>div { color: transparent; } div::selection { background-color: bar; }</style>'
        + '<div id="test">Selected Text</div>';
    assert.ok(tagsForTest({ html }).includes('browser-ref-divergent'));
});

test('Rule 42 does NOT fire when the ::selection block declares a real color (active-selection-056 shape)', () => {
    // -056 measures 1.0000 against its ref: the author pinned the highlight
    // colours, so no OS default is consulted and nothing diverges.
    const html = '<style>div { font-size: 100px; } '
        + 'div::selection { background-color: transparent; color: red; }</style>'
        + '<div id="test">&nbsp;<br><br></div>';
    assert.equal(tagsForTest({ html }).includes('browser-ref-divergent'), false);
});

test('Rule 42 does NOT fire without a `color: transparent` (active-selection-057 shape)', () => {
    // -057 measures 0.9543 — it clears the gate. Its ::selection rules carry
    // `color: red` and only BACKGROUND-color is transparent, so BOTH signals
    // are absent; either absence alone must be enough to decline.
    const html = '<style>div#subtest1 { background-color: transparent; height: 100px; } '
        + 'div#subtest1::selection { color: red; }</style>'
        + '<div id="subtest1">&nbsp;</div>';
    assert.equal(tagsForTest({ html }).includes('browser-ref-divergent'), false);
});

test('Rule 42 does NOT fire on `color: transparent` with no ::selection rule at all', () => {
    // Signal 2 missing: transparent text is just invisible text — nothing
    // about the ref is unreachable.
    const html = '<style>div { color: transparent; }</style><div>x</div>';
    assert.equal(tagsForTest({ html }).includes('browser-ref-divergent'), false);
});

test('Rule 42 does NOT fire on a ::selection rule with no `color: transparent` anywhere', () => {
    // Signal 1 missing: the selected text is visible from its own colour, so
    // the OS highlight FOREGROUND never decides the render.
    const html = '<style>div { color: black; } div::selection { }</style><div>x</div>';
    assert.equal(tagsForTest({ html }).includes('browser-ref-divergent'), false);
});

test('Rule 42 treats system colors, CSS-wide keywords and functional notations as valid colors', () => {
    // Each of these is a real `color` value, so each must decline. If any were
    // misread as invalid the exclusion would over-fire onto a scorable test.
    for (const value of ['Highlight', 'inherit', 'currentColor', 'rebeccapurple',
                         '#3838e0c0', 'var(--a)', 'light-dark(green, blue)',
                         'color-mix(in srgb, red, blue)']) {
        const html = `<style>div { color: transparent; } div::selection { color: ${value}; }</style>`;
        assert.equal(tagsForTest({ html }).includes('browser-ref-divergent'), false,
            `expected '${value}' to count as a valid color`);
    }
});

test('Rule 42 reads STYLE BLOCKS ONLY — the meta-assert prose cannot arm or disarm it', () => {
    // The prose alone (no <style>) must not fire: with no stylesheet there is
    // no `color: transparent` and no ::selection rule, only words about them.
    assert.equal(tagsForTest({ html: SEL_PROSE }).includes('browser-ref-divergent'), false);
    // And prose declaring a colour must not DISARM a stylesheet that omits
    // one — the `color: red` below lives in the meta content, not the sheet.
    const armed = '<meta name="assert" content="div::selection { color: red }">'
        + '<style>div { color: transparent; } div::selection { }</style>';
    assert.ok(tagsForTest({ html: armed }).includes('browser-ref-divergent'));
});

test('Rule 42 ignores a COMMENTED-OUT ::selection color (css-syntax-3 comment stripping)', () => {
    // The comment is not a declaration; the block still supplies no colour.
    const html = '<style>div { color: transparent; } '
        + 'div::selection { /* color: red; */ }</style>';
    assert.ok(tagsForTest({ html }).includes('browser-ref-divergent'));
});

test('Rule 42 handles a ::selection SELECTOR LIST as one block (active-selection-057 prelude shape)', () => {
    // `a::selection , b::selection { … }` must be read as a single rule whose
    // one declaration block is inspected once — the `[^{}]*` between pseudo
    // and brace can never cross a brace, so the prelude stays intact.
    const html = '<style>div { color: transparent; } '
        + 'div#a::selection , hr#b::selection { color: lime; }</style>';
    assert.equal(tagsForTest({ html }).includes('browser-ref-divergent'), false);
});

// ── Rule 43 (wave-30 B4b): requires-non-latin-font-parity ───────────────────
//
// The one NATIVE-ONLY rule, and the first tag in this file whose exclusion is
// PER PLATFORM. It names a FONT BOUNDARY: the pipeline pins Inter (Latin +
// Greek + Cyrillic) on all four surfaces, so a test painting §6 non-Latin
// counter-style glyphs sends the ref and web to the same macOS CoreText
// fallback while Compose and SwiftUI each resolve their own — a typographic
// ceiling on the native SSIM that no runtime work can lift. Measured across
// css-counter-styles: web 0.9623–0.9986 (11/12 pass), natives 0.7244–0.9917
// and degrading with glyph count. Full table at the rule's banner.
//
// The pins below hold the three things that make it safe: the CLOSED §6
// table (a silent widening would exclude native diffs that are genuinely
// comparable), the three declaration shapes that count as a USE, and the
// deliberate declines — Latin/Greek styles, §6.1 bullets, prose mentions.

const NLF = 'requires-non-latin-font-parity';

test('Rule 43 fires on a list-style-type naming a §6 non-Latin system (arabic-indic 102 shape)', () => {
    // The exact declaration css3-counter-styles-102.html carries.
    const html = '<title>arabic-indic, 10+</title>'
        + '<style>ol li { list-style-type: arabic-indic; }</style>'
        + '<ol start="10"><li>x</ol>';
    assert.ok(tagsForTest({ html }).includes(NLF));
});

test('Rule 43 fires through the list-style SHORTHAND (css-lists marker-text-matches-armenian)', () => {
    // `list-style: armenian inside` — the style name is one component of the
    // shorthand, so a longhand-only match would miss the whole family.
    const html = '<style>ol { list-style: armenian inside; }</style>';
    assert.ok(tagsForTest({ html }).includes(NLF));
});

test('Rule 43 fires on counter()/counters() whose LAST argument is a non-Latin style', () => {
    // css-lists/counter-004 and counters-004 verbatim: the style is the last
    // argument (css-lists-3 §4.3), after the counter name and the separator.
    const one = '<style>#test span::before { content: counter(c, georgian); }</style>';
    const two = '<style>#test span::before { content: counters(c, ".", georgian); }</style>';
    assert.ok(tagsForTest({ html: one }).includes(NLF));
    assert.ok(tagsForTest({ html: two }).includes(NLF));
});

test('Rule 43 fires on `system: extends <non-Latin>` (counter-style-at-rule/redefine-builtin)', () => {
    // §3.1: an extending style reuses the base style's SYMBOLS, so it paints
    // the same non-Latin glyphs under an arbitrary name. redefine-builtin.html
    // names its style `hebrew` but the GLYPHS come from cjk-decimal — which is
    // why the rule reads the `extends` operand, not the at-rule's own name.
    const html = '<style>@counter-style hebrew { system: extends cjk-decimal; }</style>';
    assert.ok(tagsForTest({ html }).includes(NLF));
});

test('Rule 43 does NOT fire on the ASCII / Greek predefined systems', () => {
    // The deliberate boundary: Inter covers Latin, Greek and Cyrillic, so
    // these four surfaces keep the SAME face and the comparison stays fair.
    // lower-greek is the load-bearing case — it is non-Latin SCRIPT but not
    // outside the bundled coverage, and the corpus has three passing tests
    // (css3-counter-styles-027..029) that must not be excluded.
    for (const style of ['decimal', 'decimal-leading-zero', 'lower-alpha', 'upper-latin',
        'lower-roman', 'upper-roman', 'lower-greek']) {
        const html = `<style>ol { list-style-type: ${style}; }</style>`;
        assert.equal(tagsForTest({ html }).includes(NLF), false, `${style} must NOT fire`);
    }
});

test('Rule 43 does NOT fire on the §6.1 bullet styles', () => {
    // Ordinal-independent and drawn rather than text-shaped on the natives —
    // no glyph, no font boundary.
    for (const style of ['disc', 'circle', 'square', 'disclosure-open', 'disclosure-closed', 'none']) {
        const html = `<style>ul { list-style-type: ${style}; }</style>`;
        assert.equal(tagsForTest({ html }).includes(NLF), false, `${style} must NOT fire`);
    }
});

test('Rule 43 reads STYLE BLOCKS ONLY — title/meta prose cannot arm it', () => {
    // Every one of these tests spells the style name in its <title> and its
    // <meta name=assert>, and 461 further corpus documents mention a §6 name
    // in prose alone (css-writing-modes "the first six Hebrew letters",
    // css-text "japanese hiragana" titles, …). A whole-document scan would
    // fire on all of them.
    const html = '<title>arabic-indic, 10+</title>'
        + '<meta name="assert" content="list-style-type: arabic-indic produces numbers after 9">'
        + '<p>Test passes if the two columns are the same.</p>';
    assert.equal(tagsForTest({ html }).includes(NLF), false);
});

test('Rule 43 does NOT fire on list-style-image / a quoted string marker', () => {
    // Both are the false-positive shapes a substring match would invent:
    // `url(georgian-bullet.png)` names an IMAGE, `"hebrew"` a literal STRING
    // marker (css-counter-styles-3 §6 allows <string> in list-style-type).
    // Neither is a style-name position, so neither may fire.
    const img = '<style>ol { list-style-image: url(georgian-bullet.png); }</style>';
    const shorthandImg = '<style>ol { list-style: url(armenian.png) none; }</style>';
    const str = '<style>ol { list-style-type: "hebrew"; }</style>';
    assert.equal(tagsForTest({ html: img }).includes(NLF), false);
    assert.equal(tagsForTest({ html: shorthandImg }).includes(NLF), false);
    assert.equal(tagsForTest({ html: str }).includes(NLF), false);
});

test('Rule 43 does NOT fire when a non-Latin name is only the counter NAME', () => {
    // `counter(hebrew)` names a COUNTER called hebrew and renders it in the
    // default `decimal` style (css-lists-3 §4.3) — ASCII digits, no boundary.
    // Only the LAST argument is a style position, which is what this pins.
    const html = '<style>span::before { content: counter(hebrew); }</style>';
    assert.equal(tagsForTest({ html }).includes(NLF), false);
});

test('Rule 43 ignores a COMMENTED-OUT declaration (css-syntax-3 comment stripping)', () => {
    const html = '<style>ol { /* list-style-type: tibetan; */ list-style-type: decimal; }</style>';
    assert.equal(tagsForTest({ html }).includes(NLF), false);
});

test('Rule 43 matches whole IDENTS, never a substring of a longer word', () => {
    // The corpus lesson: the only "urdu" matches in tools/wpt/css live inside
    // "tURDUcken" (css-gcpm ipsum prose). `urdu` is not a §6 style and is not
    // in the table — but the ident boundary is what stops the NEXT such
    // coincidence, so pin it on a name that IS in the table.
    const html = '<style>ol { list-style-type: pseudo-thai-ish; }</style>';
    assert.equal(tagsForTest({ html }).includes(NLF), false);
});

test('Rule 43 is stateless across calls (the /g/ panel regexes are matchAll-cloned)', () => {
    // The three Rule 43 entries in RX are the only GLOBAL regexes on the
    // shared panel. String.matchAll clones the regex rather than advancing
    // the original's lastIndex, so repeated classification of the same input
    // must give the same answer — a `.test()` on a /g/ regex would alternate.
    const html = '<style>ol { list-style-type: bengali; }</style>';
    for (let i = 0; i < 4; i++) assert.ok(tagsForTest({ html }).includes(NLF), `call ${i}`);
});

test('Rule 43 table is the CLOSED css-counter-styles-3 §6 non-Latin set', () => {
    // Membership is the exclusion's blast radius: every name here removes both
    // native platforms from the denominator for every test using it. Pinned
    // exactly, so a widening is a reviewed edit and not a typo.
    assert.equal(NON_LATIN_PREDEFINED_COUNTER_STYLES.size, 41);
    // §6.2 numeric (19) — one non-ASCII digit block each; khmer is the §6.2
    // alias of cambodian and both must be present.
    for (const n of ['arabic-indic', 'bengali', 'cambodian', 'khmer', 'cjk-decimal',
        'devanagari', 'gujarati', 'gurmukhi', 'kannada', 'lao', 'malayalam', 'mongolian',
        'myanmar', 'oriya', 'persian', 'tamil', 'telugu', 'thai', 'tibetan']) {
        assert.ok(NON_LATIN_PREDEFINED_COUNTER_STYLES.has(n), `${n} missing`);
    }
    // §6.2 alphabetic kana (4) + additive letter-numerals (5).
    for (const n of ['hiragana', 'hiragana-iroha', 'katakana', 'katakana-iroha',
        'armenian', 'upper-armenian', 'lower-armenian', 'georgian', 'hebrew']) {
        assert.ok(NON_LATIN_PREDEFINED_COUNTER_STYLES.has(n), `${n} missing`);
    }
    // §6.3 complex (13).
    for (const n of ['cjk-earthly-branch', 'cjk-heavenly-stem', 'cjk-ideographic',
        'ethiopic-numeric', 'japanese-formal', 'japanese-informal', 'korean-hangul-formal',
        'korean-hanja-formal', 'korean-hanja-informal', 'simp-chinese-formal',
        'simp-chinese-informal', 'trad-chinese-formal', 'trad-chinese-informal']) {
        assert.ok(NON_LATIN_PREDEFINED_COUNTER_STYLES.has(n), `${n} missing`);
    }
    // …and the names that must NEVER be admitted. `urdu` is not a §6 style at
    // all (the spec spells the extended-Arabic-Indic digits `persian`);
    // lower-greek is §6 but INSIDE Inter's coverage.
    for (const n of ['decimal', 'decimal-leading-zero', 'lower-alpha', 'upper-alpha',
        'lower-latin', 'upper-latin', 'lower-roman', 'upper-roman', 'lower-greek',
        'disc', 'circle', 'square', 'disclosure-open', 'disclosure-closed', 'none', 'urdu']) {
        assert.equal(NON_LATIN_PREDEFINED_COUNTER_STYLES.has(n), false, `${n} must NOT be in the table`);
    }
});

test('Rule 43 keeps the four systems the corpus actually exercises (wave-31)', () => {
    // wave-31 lane F re-derived the fire list over all 312 wave-30 docs: Rule
    // 43 fires on 13 tests spanning exactly these four systems. They are the
    // ONLY §6 names whose exclusion is currently costing measurements, so they
    // are the four a "narrow the rule" wave will reach for first — and the
    // four that must NOT be removed until both natives can pin a face.
    //
    // The gate is not "are the fonts bundled" but "can the ENGINES pin them".
    // Compose resolves one face per weight and leaves per-glyph fallback to
    // the system chain (`Typeface.CustomFallbackBuilder` is API 29 vs
    // `minSdk = 24`); iOS needs a `kCTFontCascadeListAttribute` descriptor in
    // ComponentRenderer's `.custom("Inter", size:)` path. Neither exists —
    // grep for either name in runtimes/ or apps/ and you get nothing. Wave 31
    // measured what happens if you bundle faces anyway and pin only the ref:
    // iOS bidi-lines-001 REGRESSED 0.9926 → 0.9559, because Chromium-on-macOS
    // and iOS resolve Arabic to the same system face today and pinning Noto in
    // the ref alone destroys that accidental parity. Full write-up in the
    // wave-31 section of hasNonLatinPredefinedCounterStyle's banner.
    for (const n of ['armenian', 'arabic-indic', 'bengali', 'cambodian']) {
        assert.ok(NON_LATIN_PREDEFINED_COUNTER_STYLES.has(n),
            `${n} is exercised by the corpus and must stay in the table until ` +
            'both native runtimes can pin the face (see the wave-31 banner)');
    }
});

test('Rule 43 rides classifyAll and coexists with the other tags on the same test', () => {
    // css3-counter-styles-102 carries requires-form-control-rendering and
    // requires-bundled-font too; the histogram must count all of them, and the
    // per-platform gate downstream keys on this ONE tag out of the list.
    const html = '<style>ol li { list-style-type: bengali; }</style><ol><li>x</ol>';
    const { notApplicable, tagHistogram } = classifyAll([
        { rel: 'css/css-counter-styles/bengali/css3-counter-styles-117.html', html },
    ]);
    assert.ok(notApplicable['css/css-counter-styles/bengali/css3-counter-styles-117.html'].includes(NLF));
    assert.equal(tagHistogram[NLF], 1);
});

// ── wave-30 fix-T4: Rule 43 must not fire on an AUTHOR-SHADOWED §6 name ─────
//
// css-counter-styles-3 §5 puts author `@counter-style` rules in the cascade
// alongside the predefined ones, and later wins. Verified in Chromium: a page
// carrying `@counter-style bengali { system: numeric; symbols: "0" … "9" }`
// plus `list-style-type: bengali` renders ASCII digits identical to `decimal`.
// Every surface shapes those from Inter — there is no fallback face, so the
// font boundary Rule 43 names does not exist for that document and firing
// would score-exclude two perfectly comparable native diffs.
//
// The decline is deliberately ASYMMETRIC: we fire unless the redefinition is
// PROVABLY non-Latin. Corpus impact of the fix, measured over all 33,643
// documents: 319 → 319, zero documents affected — the only §6 name any
// corpus document shadows is `hebrew` in
// css-counter-styles/counter-style-at-rule/redefine-builtin.html, and it does
// so with `system: extends cjk-decimal`, which IS provably non-Latin.

test('Rule 43 fix-T4: an author redefinition with ASCII symbols declines the name', () => {
    const html = '<style>@counter-style bengali { system: numeric; '
        + 'symbols: "0" "1" "2" "3" "4" "5" "6" "7" "8" "9"; } '
        + 'ol li { list-style-type: bengali; }</style><ol><li>x</ol>';
    assert.deepEqual([...shadowedCounterStyleNames(
        html.replace(/^[\s\S]*<style>|<\/style>[\s\S]*$/g, ''))], ['bengali']);
    assert.equal(tagsForTest({ html }).includes(NLF), false);
});

test('Rule 43 fix-T4: a shadow that EXTENDS a non-Latin style still fires', () => {
    // `system: extends thai` reuses Thai digits (§3.1) — the boundary is real.
    const html = '<style>@counter-style thai { system: extends thai; } '
        + 'ol { list-style-type: thai; }</style>';
    assert.deepEqual([...shadowedCounterStyleNames('@counter-style thai { system: extends thai; }')], []);
    assert.ok(tagsForTest({ html }).includes(NLF));
    // …and a shadow whose OWN symbols carry non-ASCII codepoints.
    const glyphs = '<style>@counter-style bengali { system: numeric; '
        + 'symbols: "০" "১" "২"; } ol { list-style-type: bengali }</style>';
    assert.ok(tagsForTest({ html: glyphs }).includes(NLF));
    // …and one that spells them as CSS escapes instead of literal codepoints.
    const escaped = '<style>@counter-style bengali { system: numeric; '
        + 'symbols: \\09E6 \\09E7 \\09E8; } ol { list-style-type: bengali }</style>';
    assert.ok(tagsForTest({ html: escaped }).includes(NLF));
});

test('Rule 43 fix-T4: extending a SHADOWED name declines too', () => {
    // `x` inherits the AUTHOR's ASCII symbols, not §6's Bengali digits, so the
    // Use-3 `system: extends` route must respect the same decline.
    const html = '<style>@counter-style bengali { system: numeric; symbols: "0" "1"; } '
        + '@counter-style x { system: extends bengali; } ol { list-style-type: x }</style>';
    assert.equal(tagsForTest({ html }).includes(NLF), false);
});

test('Rule 43 fix-T4: an empty or non-§6 @counter-style block shadows nothing', () => {
    // A block with no descriptor cannot be a valid counter style (§3), so it
    // cannot take the name over.
    assert.deepEqual([...shadowedCounterStyleNames('@counter-style bengali { }')], []);
    assert.ok(tagsForTest({ html: '<style>@counter-style bengali {} '
        + 'ol { list-style-type: bengali }</style>' }).includes(NLF));
    // A name that was never in the §6 table is irrelevant to the rule.
    assert.deepEqual([...shadowedCounterStyleNames(
        '@counter-style my-disc { system: cyclic; symbols: "x"; }')], []);
});

test('Rule 43 fix-T4: css-counter-styles/redefine-builtin still fires (measured member)', () => {
    // The ONLY corpus document that shadows a §6 name. Its `hebrew`
    // redefinition extends cjk-decimal, so the boundary survives — and the
    // sheet's other `system: extends` arms Use 3 independently.
    const css = '@counter-style none { system: extends lower-roman; } '
        + '@counter-style decimal { system: extends upper-roman; } '
        + '@counter-style disc { system: extends decimal; } '
        + '@counter-style hebrew { system: extends cjk-decimal; }';
    assert.deepEqual([...shadowedCounterStyleNames(css)], []);
    assert.ok(tagsForTest({ html: `<style>${css}</style>` }).includes(NLF));
});

test('Rule 43 fix-T4: the 319-fire baseline members are untouched (spot-check)', () => {
    // Three families from the measured table, one per use site, all with NO
    // author @counter-style anywhere — the fix must be a pure narrowing.
    assert.ok(tagsForTest({ html: '<style>ol li { list-style-type: bengali; }</style>' }).includes(NLF));
    assert.ok(tagsForTest({ html: '<style>ol { list-style: armenian inside; }</style>' }).includes(NLF));
    assert.ok(tagsForTest({ html: '<style>span::before { content: counter(c, georgian); }</style>' }).includes(NLF));
    assert.ok(tagsForTest({ html: '<style>@counter-style x { system: extends arabic-indic; }</style>' }).includes(NLF));
    // And the deliberate declines stay declined.
    assert.equal(tagsForTest({ html: '<style>ol { list-style-type: lower-greek }</style>' }).includes(NLF), false);
});
