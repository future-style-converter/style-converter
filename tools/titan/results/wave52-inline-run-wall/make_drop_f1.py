#!/usr/bin/env python3
# Wave 52 · lane L9 — build drop-F1.patch: the exact removal of F1 (the
# `hanging-punctuation` inert arm, both rings, its helpers and its pins) from
# the lane's files as they stand, leaving F2–F5 untouched.
#
# Why it exists: the lane's PNG replay (replay.json) REFUTES F1's predicted
# flip — the folded, non-hanging shape scores LOWER than the stacked wrong
# render on both natives against a ref that HANGS the bracket. F1 is kept in
# the tree as a correctness change for the device A/B (PLAN §4 step 5); if
# the A/B confirms the drop, the orchestrator applies this patch
# (`git apply tools/titan/results/wave52-inline-run-wall/drop-F1.patch`)
# instead of hand-editing two rings and three test files.
#
# Usage: python3 make_drop_f1.py  (reads the working tree, writes the patch)
import difflib, os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "drop-F1.patch")


def cut(s, start, end_incl):
    # Delete from the first `start` through the end of `end_incl` (each
    # must occur exactly once — a drifted file fails loudly).
    assert s.count(start) == 1, start[:70]
    i = s.index(start)
    assert s.count(end_incl, i) >= 1, end_incl[:70]
    j = s.index(end_incl, i) + len(end_incl)
    return s[:i] + s[j:]


def swap(s, old, new):
    # One exact replacement.
    assert s.count(old) == 1, old[:70]
    return s.replace(old, new)


K = "runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InlineSpanRing.kt"
W = "runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/inline/InlineSpanRing.swift"
KT = "runtimes/compose/src/test/java/com/styleconverter/runtime/typography/inline/InlineRunFoldTest.kt"
WT1 = "runtimes/swiftui/Tests/StyleConverterRuntimeTests/InlineSpanRingTests.swift"
WT2 = "runtimes/swiftui/Tests/StyleConverterRuntimeTests/InlineRunFlowTests.swift"


def drop_kotlin_ring(s):
    s = cut(s, "        // Wave 52 (lane L9) — the member's `hanging-punctuation` keywords,\n",
            "        var hangingKeywords: String? = null\n")
    s = cut(s, "            // ── Wave 52 (lane L9) — `hanging-punctuation` is INERT",
            "                hangingKeywords = extractHangingKeywords(prop.data)\n            }\n")
    s = swap(s, """        val stated = buildList {
            if (borderStyleSeen) add("border-box-ink(${losses.joinToString(",")})")
            // Wave 52 (lane L9): the un-hung punctuation, named per keyword
            // so logcat can tell `first` from `last` (css-text-3 §8.3).
            hangingKeywords?.let { add("hanging-punctuation($it)") }
        }
""", """        val stated = if (borderStyleSeen) listOf("border-box-ink(${losses.joinToString(",")})") else emptyList()
""")
    return cut(s, "    /** Wave 52 (lane L9) — the HangingPunctuation wire: a JsonArray of\n",
               "            else -> ValueExtractors.extractKeyword(data)?.lowercase() ?: \"\"\n        }\n\n")


def drop_swift_ring(s):
    s = cut(s, "        // Wave 52 (lane L9) — the member's `hanging-punctuation` keywords,\n",
            "        var hangingKeywords: String? = nil\n")
    s = cut(s, "            // ── Wave 52 (lane L9) — `hanging-punctuation` is INERT",
            "                hangingKeywords = extractHangingKeywords(prop.data)\n")
    s = swap(s, """        var stated: [String] = borderStyleSeen ? ["border-box-ink(\\(losses.joined(separator: ",")))"] : []
        // Wave 52 (lane L9): the un-hung punctuation, named per keyword so
        // the breadcrumb can tell `first` from `last` (css-text-3 §8.3).
        if let hangingKeywords { stated.append("hanging-punctuation(\\(hangingKeywords))") }
""", """        let stated = borderStyleSeen ? ["border-box-ink(\\(losses.joined(separator: ",")))"] : []
""")
    return cut(s, "    /// Wave 52 (lane L9) — the HangingPunctuation wire: an array of\n",
               "        return ValueExtractors.extractKeyword(data)?.lowercased() ?? \"\"\n    }\n\n")


def drop_kotlin_tests(s):
    s = cut(s, "    // ── Wave 52 (lane L9) — `hanging-punctuation` is an INERT member prop ──\n",
            "            admitted.statedLossTypes,\n        )\n    }\n\n")
    return cut(s, "    /** hanging-punctuation-inline-001, the whole document, verbatim (minified). */\n",
               "\"text\":\"」\",\"meta\":{\"sourceTag\":\"span\",\"lang\":\"en\"}}]}\"\"\"\n\n")


def drop_swift_ring_tests(s):
    return cut(s, "    // MARK: - Wave 52 (lane L9): `hanging-punctuation` is an inert member prop\n",
               "XCTAssertEqual(admitted(none)?.1, [\"hanging-punctuation(none)\"])\n    }\n\n")


def drop_swift_flow_tests(s):
    return cut(s, "\n    func testHangingPunctuationInline001FoldsTheClosingBracketOntoTheLine() {\n",
               "        XCTAssertEqual(folded?.droppedEmptyMembers, 0)\n    }\n")


patch = []
for rel, fn in [(K, drop_kotlin_ring), (W, drop_swift_ring), (KT, drop_kotlin_tests),
                (WT1, drop_swift_ring_tests), (WT2, drop_swift_flow_tests)]:
    before = open(os.path.join(ROOT, rel), encoding="utf-8").read()
    after = fn(before)
    patch.append(f"diff --git a/{rel} b/{rel}\n")
    patch.extend(difflib.unified_diff(before.splitlines(True), after.splitlines(True),
                                      f"a/{rel}", f"b/{rel}", n=3))
open(OUT, "w", encoding="utf-8").write(
    "Wave 52 · lane L9 — drop-F1.patch: removes F1 (hanging-punctuation inert arm + pins) only.\n"
    "Apply on the lane's files as committed (`git apply` from the repo root); see _note.md.\n\n"
    + "".join(patch))
print(OUT)
