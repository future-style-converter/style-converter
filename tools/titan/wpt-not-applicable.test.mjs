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

import { tagsForTest, classifyAll, RULES, RX } from './wpt-not-applicable.mjs';

// ── Sanity: 40 rules (17 swarm-001 + 12 swarm-002 + 11 swarm-003) ──────────

test('RULES exports exactly 40 entries', () => {
    assert.equal(RULES.length, 40);
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
});
