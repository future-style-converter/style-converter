package app.parsing.css.properties.primitiveParsers

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Mechanical guard for finding A6#14: no converter source may re-declare a
 * function whose body is a copy of one of [TokenizationUtils]' tokenizers.
 *
 * WHY a source-scanning test and not a compiler rule: the prescription asked
 * for `internal` members "so a private re-declaration cannot shadow them".
 * That is not how Kotlin works — an object member cannot conflict with a
 * private member of some other class, so `internal` narrows the API surface
 * but enforces nothing. Wave 47 fixed ONE copy of the border tokenizer and
 * left ~40 siblings standing precisely because nothing mechanical was
 * watching (39 tokenizer copies plus 9 more in the grid/timing/anchor/
 * drop-shadow parsers were still standing when the retro opened); this test
 * is that watcher.
 *
 * WHAT it catches: a body that is identical to a shared tokenizer after
 * comments, string literals, formatting and LOCAL VARIABLE NAMES are
 * normalised away — i.e. the copy-paste-and-rename that produced the 39
 * tokenizer duplicates A6#14 counted. It does NOT claim to catch a
 * re-implementation that changes the algorithm's shape; that is a different
 * (and much rarer) failure, and the divergent same-name families that
 * survive on purpose (BackgroundExpander's `/`-aware tokenize, MaskExpander's
 * splitters, …) are exactly why the guard compares BODIES, not names.
 */
class TokenizationCloneGuardTest {

    @Test
    fun `no converter source re-declares a shared tokenizer body`() {
        val mainRoot = File(repoRoot(), "converter/src/main/kotlin")
        val utilsFile = File(mainRoot, "app/parsing/css/properties/primitiveParsers/TokenizationUtils.kt")
        assertTrue(utilsFile.isFile, "TokenizationUtils.kt must exist at ${utilsFile.path}")

        // Canonical bodies, keyed by the shared function they belong to.
        val canonical = mutableMapOf<String, String>()
        val utilsSrc = scrub(utilsFile.readText())
        for (name in listOf(
            "splitByComma", "splitByCommaRaw", "tokenizeByWhitespace",
            "tokenizeBySpace", "splitByTopLevelComma",
        )) {
            val body = bodyOf(utilsSrc, name)
                ?: fail("TokenizationUtils.$name not found — the guard has lost its baseline")
            // A desynced brace match would yield a stub; a real tokenizer body
            // is well over 100 normalised characters.
            assertTrue(body.length > 100, "$name body looks truncated (${body.length} chars)")
            canonical[name] = body
        }

        val offenders = mutableListOf<String>()
        for (file in mainRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            if (file.canonicalPath == utilsFile.canonicalPath) continue // the originals
            val src = scrub(file.readText())
            // Every declaration, not one per name: LogicalBorderExpander held
            // FOUR same-named `tokenize` copies, so a name-keyed scan would
            // have seen only the first.
            for ((fn, at) in declaredFunctions(src)) {
                val body = bodyOf(src, fn, at) ?: continue
                for ((name, want) in canonical) {
                    if (body == want) {
                        offenders += "${file.relativeTo(repoRoot()).path}: `$fn` duplicates " +
                            "TokenizationUtils.$name"
                    }
                }
            }
        }
        if (offenders.isNotEmpty()) {
            fail(
                "Shared tokenizer re-declared (A6#14 regression) — call " +
                    "TokenizationUtils instead:\n" + offenders.joinToString("\n") { "  - $it" },
            )
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /** Walk up from the test's working directory to the repo root. */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "converter/src/main/kotlin").isDirectory) dir = dir.parentFile
        return dir ?: error("repo root not found from ${System.getProperty("user.dir")}")
    }

    /**
     * Remove everything that must not influence the comparison and that could
     * desync the brace matcher: block comments, line comments, string literals
     * (raw first, so `"""…"""` is not eaten char by char) and the two brace
     * CHAR literals. Other char literals stay — `'('` vs `' '` is exactly the
     * difference between tokenizeByWhitespace and tokenizeBySpace, so erasing
     * them would make unrelated tokenizers compare equal.
     */
    private fun scrub(text: String): String = text
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("//[^\n]*"), " ")
        .replace(Regex("\"\"\".*?\"\"\"", RegexOption.DOT_MATCHES_ALL), "\"S\"")
        .replace(Regex("\"(\\\\.|[^\"\\\\\n])*\""), "\"S\"")
        .replace("'{'", "'C'")
        .replace("'}'", "'C'")

    /** Every `fun <name>(` in an already-scrubbed source, as (name, offset). */
    private fun declaredFunctions(src: String): List<Pair<String, Int>> =
        Regex("\\bfun\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(").findAll(src)
            .map { it.groupValues[1] to it.range.first }.toList()

    /**
     * The normalised body of the first `fun <name>` at or after [from] in a
     * scrubbed source:
     * brace-matched, with every name the author was free to choose — the
     * parameters, the `for` binders and the local `val`/`var`s — replaced by
     * positional placeholders, and all whitespace collapsed. Without that
     * renaming step the guard would only catch a verbatim paste; with it,
     * `for (ch in input) { … buf … }` and `for (char in value) { … current … }`
     * compare equal, which is the form the copies actually took (the byte-level
     * dup scan missed OutlineExpander.splitByWhitespace for exactly this
     * reason, and this guard caught it on its first run).
     */
    private fun bodyOf(src: String, name: String, from: Int = 0): String? {
        val m = Regex("\\bfun\\s+" + Regex.escape(name) + "\\s*\\(").find(src, from) ?: return null
        val open = src.indexOf('{', m.range.last)
        if (open < 0) return null
        // Parameter names come from the signature between `(` and the `{`.
        val params = Regex("([A-Za-z_][A-Za-z0-9_]*)\\s*:")
            .findAll(src.substring(m.range.last, open))
            .map { it.groupValues[1] }.toList()
        var depth = 0
        var close = -1
        for (i in open until src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) { close = i; break } }
            }
        }
        if (close < 0) return null
        var body = src.substring(open, close + 1)
        // Canonicalise every author-chosen name, in order of appearance:
        // parameters first (they are in scope from the top), then the `for`
        // binders and `val`/`var` locals as the body introduces them.
        val bound = Regex("(?:\\b(?:val|var)\\s+|\\bfor\\s*\\(\\s*)([A-Za-z_][A-Za-z0-9_]*)")
            .findAll(body).map { it.groupValues[1] }.toList()
        (params + bound).distinct().forEachIndexed { i, local ->
            // `(?<![.\\w])` keeps a member access like `obj.value` intact while
            // renaming the standalone identifier `value`.
            body = body.replace(Regex("(?<![.\\w])" + Regex.escape(local) + "\\b"), "v${i + 1}")
        }
        // `;` is an OPTIONAL statement separator in Kotlin, so a one-line
        // `{ d++; buf.append(c) }` and the same two statements on two lines are
        // the same code — collapse it with whitespace or the guard would miss
        // every reformatted paste (all 19 splitPreservingFunctions copies were
        // written in the compact form).
        return body.replace(Regex("[;\\s]+"), " ").trim()
    }
}
