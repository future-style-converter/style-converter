package app.parsing.css.properties.longhands.transforms

// retro R5 (A11#14) — css-transforms-1 §5's one-value form: "If only one value
// is specified, the second value is assumed to be center." The parser used to
// duplicate a lone VERTICAL keyword into both slots (`top` → x:TOP, y:TOP):
// the web runtime replayed that as `transform-origin: top top`, which is not
// in the grammar (Chromium dropped it → 50% 50%), and the positional natives
// read the top-LEFT corner. `top` is `center top`; `left` was already
// `left center`. No fixture and no wave49-final corpus test carries a lone
// vertical keyword, so these declarations are synthetic.

import app.irmodels.properties.transforms.TransformOriginProperty
import app.irmodels.properties.transforms.TransformOriginProperty.OriginValue
import app.irmodels.properties.transforms.TransformOriginProperty.OriginValue.OriginKeyword
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TransformOriginLoneKeywordTest {

    private fun kw(k: OriginKeyword) = OriginValue.Keyword(k)
    private fun values(css: String): TransformOriginProperty.Values =
        assertIs<TransformOriginProperty.Values>(TransformOriginPropertyParser.parse(css))

    @Test
    fun `a lone vertical keyword puts the implied center in the x slot`() {
        assertEquals(TransformOriginProperty.Values(kw(OriginKeyword.CENTER), kw(OriginKeyword.TOP)), values("top"))
        assertEquals(TransformOriginProperty.Values(kw(OriginKeyword.CENTER), kw(OriginKeyword.BOTTOM)), values("bottom"))
    }

    @Test
    fun `lone horizontal keywords and center keep the y slot centered`() {
        // Origin_KeywordSingle (fixtures/properties/transforms/transform-origin.json) — unchanged.
        assertEquals(TransformOriginProperty.Values(kw(OriginKeyword.LEFT), kw(OriginKeyword.CENTER)), values("left"))
        assertEquals(TransformOriginProperty.Values(kw(OriginKeyword.CENTER), kw(OriginKeyword.CENTER)), values("center"))
    }

    @Test
    fun `two values stay positional for the runtimes to reorder`() {
        // Origin_TopRight is stored as (TOP, RIGHT); the §5 axis binding is the
        // runtimes' job (retro R1 on Compose, R5 on iOS) — the wire is frozen.
        assertEquals(TransformOriginProperty.Values(kw(OriginKeyword.TOP), kw(OriginKeyword.RIGHT)), values("top right"))
    }
}
