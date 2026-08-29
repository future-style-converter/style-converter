package app.parsing.shorthands

// Pins the flex shorthand against css-flexbox-1 §7.1.1, whose omitted-
// component rules are NOT the longhand initials:
//   flex: <number>       ≡ <number> 1 0%     (basis 0%, not auto!)
//   flex: <number> <n2>  ≡ <number> <n2> 0%
//   flex: <width>        ≡ 1 1 <width>
//   flex: <n> <width>    ≡ <n> 1 <width>
//
// The old expander emitted only what appeared, so `flex: 1` left basis at
// the cascade's `auto` — content-sized layout where the spec demands
// zero-based distribution. And its number pattern required a leading
// digit, so `flex: .5` was misrouted onto flex-basis and dropped.

import app.parsing.css.properties.shorthands.FlexExpander
import kotlin.test.Test
import kotlin.test.assertEquals

class FlexExpanderTest {

    private fun f(v: String) = FlexExpander.expand(v)

    @Test
    fun `single number resets basis to zero percent`() {
        // THE bug: this must be 1 / 1 / 0%, never 1 / - / -.
        val out = f("1")
        assertEquals("1", out["flex-grow"])
        assertEquals("1", out["flex-shrink"])
        assertEquals("0%", out["flex-basis"])
    }

    @Test
    fun `leading-dot number is a grow factor, not a basis`() {
        // `.5` is a valid <number> (css-values-4 §5). The old pattern
        // required a leading digit, so this was filed as flex-basis.
        val out = f(".5")
        assertEquals(".5", out["flex-grow"])
        assertEquals("1", out["flex-shrink"])
        assertEquals("0%", out["flex-basis"])
    }

    @Test
    fun `two numbers are grow shrink with zero basis`() {
        val out = f("2 3")
        assertEquals("2", out["flex-grow"])
        assertEquals("3", out["flex-shrink"])
        assertEquals("0%", out["flex-basis"])
    }

    @Test
    fun `number plus width is grow basis with shrink one`() {
        val out = f("2 200px")
        assertEquals("2", out["flex-grow"])
        assertEquals("1", out["flex-shrink"])
        assertEquals("200px", out["flex-basis"])
    }

    @Test
    fun `bare width implies grow and shrink of one`() {
        val out = f("200px")
        assertEquals("1", out["flex-grow"])
        assertEquals("1", out["flex-shrink"])
        assertEquals("200px", out["flex-basis"])
    }

    @Test
    fun `keywords keep their spec expansions`() {
        // §7.1.1's named values — pinned so the rewrite above cannot have
        // disturbed them.
        assertEquals(mapOf("flex-grow" to "1", "flex-shrink" to "1", "flex-basis" to "auto"), f("auto"))
        assertEquals(mapOf("flex-grow" to "0", "flex-shrink" to "0", "flex-basis" to "auto"), f("none"))
        assertEquals(mapOf("flex-grow" to "0", "flex-shrink" to "1", "flex-basis" to "auto"), f("initial"))
    }

    @Test
    fun `three explicit values pass through`() {
        val out = f("2 3 10%")
        assertEquals("2", out["flex-grow"])
        assertEquals("3", out["flex-shrink"])
        assertEquals("10%", out["flex-basis"])
    }

    @Test
    fun `calc basis survives the tokenizer`() {
        val out = f("1 1 calc(100% - 20px)")
        assertEquals("calc(100% - 20px)", out["flex-basis"])
    }
}
