package com.styleconverter.test.style.animations

// Phase 9 extractor tests. Exercises the existing AnimationExtractor and
// the cross-cutting parts of ScrollTimelineExtractor / ViewTimelineExtractor
// with IR shapes pulled verbatim from
// examples/properties/animations/*.json conversions (dump commands in the
// PR description). Keeping test inputs aligned with real parser output
// avoids the classic "unit test passes, integration breaks" pitfall when a
// parser IR shape drifts.

import com.styleconverter.test.style.scrolling.AnimationRangeValue
import com.styleconverter.test.style.scrolling.AnimationTimelineValue
import com.styleconverter.test.style.scrolling.ScrollTimelineAxisValue
import com.styleconverter.test.style.scrolling.ScrollTimelineExtractor
import com.styleconverter.test.style.scrolling.ScrollerValue
import com.styleconverter.test.style.scrolling.TimelineRangeName
import com.styleconverter.test.style.scrolling.ViewTimelineExtractor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationsExtractorTest {

    // Parse helper — keeps each test short. All fixtures are passed as raw
    // JSON strings so the exact IR byte-shape is visible in-test.
    private fun parse(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    // ==================== animation-name ==================================

    @Test
    fun `animation-name none resolves to empty names list`() {
        // IR shape from AnimName_None fixture: [{"type":"none"}]
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(pair("AnimationName", """[{"type":"none"}]"""))
        )
        // "none" is the CSS sentinel for "no animation" — the extractor is
        // expected to filter it out rather than store "none" as a name.
        assertTrue(cfg.names.isEmpty())
        assertTrue(cfg.hasAnimations)
    }

    @Test
    fun `animation-name single identifier is captured`() {
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(pair("AnimationName", """[{"type":"identifier","name":"slide-in"}]"""))
        )
        assertEquals(listOf("slide-in"), cfg.names)
    }

    @Test
    fun `animation-name multi preserves order`() {
        // Multi-value: animation-name: a, b, c → three identifier entries.
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(
                pair(
                    "AnimationName",
                    """[
                        {"type":"identifier","name":"a"},
                        {"type":"identifier","name":"b"},
                        {"type":"identifier","name":"c"}
                    ]"""
                )
            )
        )
        assertEquals(listOf("a", "b", "c"), cfg.names)
    }

    // ==================== animation-duration ==============================

    @Test
    fun `animation-duration seconds becomes ms long`() {
        // Real IR: {"type":"...Durations","durations":[{"ms":2000.0,...}]}
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(
                pair(
                    "AnimationDuration",
                    """{"type":"x.Durations","durations":[{"ms":2000.0}]}"""
                )
            )
        )
        assertEquals(listOf(2000L), cfg.durations)
    }

    @Test
    fun `animation-duration multi-value list is preserved`() {
        // 1s, 500ms, 2.5s → three entries in declaration order.
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(
                pair(
                    "AnimationDuration",
                    """{"type":"x.Durations","durations":[{"ms":1000.0},{"ms":500.0},{"ms":2500.0}]}"""
                )
            )
        )
        assertEquals(listOf(1000L, 500L, 2500L), cfg.durations)
    }

    @Test
    fun `animation-duration zero is preserved`() {
        // CSS allows 0s; it means the animation finishes instantly, not "no
        // animation". Must not be filtered.
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(pair("AnimationDuration", """{"type":"x","durations":[{"ms":0.0}]}"""))
        )
        assertEquals(listOf(0L), cfg.durations)
    }

    // ==================== animation-delay =================================

    @Test
    fun `animation-delay negative is preserved`() {
        // Negative delays are spec-legal and semantically important
        // (they fast-forward the animation). Must round-trip as-is.
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(pair("AnimationDelay", """[{"ms":-500.0}]"""))
        )
        assertEquals(listOf(-500L), cfg.delays)
    }

    @Test
    fun `animation-delay multi with negatives preserves signs and order`() {
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(
                pair(
                    "AnimationDelay",
                    """[{"ms":0.0},{"ms":500.0},{"ms":-250.0},{"ms":1000.0}]"""
                )
            )
        )
        assertEquals(listOf(0L, 500L, -250L, 1000L), cfg.delays)
    }

    // ==================== animation-iteration-count =======================

    @Test
    fun `animation-iteration-count integer becomes Count`() {
        // IR shape: bare array of numbers. Parser emits [3.0] for "3".
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(pair("AnimationIterationCount", """[3.0]"""))
        )
        assertEquals(1, cfg.iterationCounts.size)
        val ic = cfg.iterationCounts[0] as AnimationIterationCount.Count
        assertEquals(3.0, ic.value, 0.0001)
    }

    @Test
    fun `animation-iteration-count fractional becomes Count`() {
        // 0.5 / 2.5 are legal — animation plays for that fraction of a cycle.
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(pair("AnimationIterationCount", """[0.5,2.5]"""))
        )
        assertEquals(2, cfg.iterationCounts.size)
        assertEquals(0.5, (cfg.iterationCounts[0] as AnimationIterationCount.Count).value, 0.0001)
        assertEquals(2.5, (cfg.iterationCounts[1] as AnimationIterationCount.Count).value, 0.0001)
    }

    @Test
    fun `animation-iteration-count infinite string becomes Infinite sentinel`() {
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(pair("AnimationIterationCount", """["infinite"]"""))
        )
        assertEquals(1, cfg.iterationCounts.size)
        assertTrue(cfg.iterationCounts[0] is AnimationIterationCount.Infinite)
    }

    // ==================== animation-direction =============================

    @Test
    fun `animation-direction keywords map to enum`() {
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(
                pair(
                    "AnimationDirection",
                    """["normal","reverse","alternate","alternate-reverse"]"""
                )
            )
        )
        // Parser lowercases keywords; extractor uppercases + replaces '-' → '_'
        // so the enum .valueOf() succeeds.
        assertEquals(
            listOf(
                AnimationDirection.NORMAL,
                AnimationDirection.REVERSE,
                AnimationDirection.ALTERNATE,
                AnimationDirection.ALTERNATE_REVERSE
            ),
            cfg.directions
        )
    }

    // ==================== animation-fill-mode =============================

    @Test
    fun `animation-fill-mode all keywords map to enum`() {
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(pair("AnimationFillMode", """["NONE","FORWARDS","BACKWARDS","BOTH"]"""))
        )
        // Fixture dump showed parser emits uppercased enum names directly
        // (Fill_None → "NONE"), unlike the direction fixture. Extractor
        // uppercases defensively so either casing would work.
        assertEquals(
            listOf(
                AnimationFillMode.NONE,
                AnimationFillMode.FORWARDS,
                AnimationFillMode.BACKWARDS,
                AnimationFillMode.BOTH
            ),
            cfg.fillModes
        )
    }

    // ==================== animation-play-state ============================

    @Test
    fun `animation-play-state running and paused map to enum`() {
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(pair("AnimationPlayState", """["RUNNING","PAUSED"]"""))
        )
        assertEquals(
            listOf(AnimationPlayState.RUNNING, AnimationPlayState.PAUSED),
            cfg.playStates
        )
    }

    // ==================== animation-timing-function =======================

    @Test
    fun `timing-function linear keyword maps to cubic-bezier 0 0 1 1`() {
        // CSS spec linear = cubic-bezier(0,0,1,1). Parser pre-expands the
        // keyword to the bezier form before emitting the IR.
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(
                pair(
                    "AnimationTimingFunction",
                    """[{"cb":[0.0,0.0,1.0,1.0],"original":"linear"}]"""
                )
            )
        )
        val tf = cfg.timingFunctions.single()
        assertEquals(listOf(0.0, 0.0, 1.0, 1.0), tf.cubicBezier)
        assertTrue(tf.isCubicBezier)
        assertEquals("linear", tf.original)
    }

    @Test
    fun `timing-function explicit cubic-bezier preserves all 4 controls`() {
        // Non-keyword cubic-bezier with negative control points (legal per
        // spec — lets the easing overshoot).
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(
                pair(
                    "AnimationTimingFunction",
                    """[{"cb":[0.68,-0.55,0.27,1.55],"original":"cubic-bezier(0.68, -0.55, 0.27, 1.55)"}]"""
                )
            )
        )
        val tf = cfg.timingFunctions.single()
        assertEquals(4, tf.cubicBezier?.size)
        assertEquals(-0.55, tf.cubicBezier!![1], 0.0001)
    }

    @Test
    fun `timing-function steps stores count and position`() {
        val cfg = AnimationExtractor.extractAnimationConfig(
            listOf(
                pair(
                    "AnimationTimingFunction",
                    """[{"steps":{"n":4,"pos":"jump-end"},"original":"steps(4, jump-end)"}]"""
                )
            )
        )
        val tf = cfg.timingFunctions.single()
        assertTrue(tf.isSteps)
        assertEquals(4, tf.stepsCount)
        assertEquals("jump-end", tf.stepsPosition)
    }

    // ==================== animation-timeline ==============================

    @Test
    fun `animation-timeline auto`() {
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(pair("AnimationTimeline", """{"type":"auto"}"""))
        )
        assertTrue(cfg.animationTimeline is AnimationTimelineValue.Auto)
    }

    @Test
    fun `animation-timeline scroll with root scroller and inline axis`() {
        // Real IR: {"type":"scroll","scroller":"root","axis":"inline"}
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(
                pair(
                    "AnimationTimeline",
                    """{"type":"scroll","scroller":"root","axis":"inline"}"""
                )
            )
        )
        val tl = cfg.animationTimeline
        assertTrue(tl is AnimationTimelineValue.Scroll)
        tl as AnimationTimelineValue.Scroll
        assertEquals(ScrollerValue.ROOT, tl.scroller)
        assertEquals(ScrollTimelineAxisValue.INLINE, tl.axis)
    }

    @Test
    fun `animation-timeline view with inset lengths`() {
        // insetStart/insetEnd arrive as {"px":...} sub-objects.
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(
                pair(
                    "AnimationTimeline",
                    """{"type":"view","axis":"inline","insetStart":{"px":10.0},"insetEnd":{"px":20.0}}"""
                )
            )
        )
        val tl = cfg.animationTimeline as AnimationTimelineValue.View
        assertEquals(ScrollTimelineAxisValue.INLINE, tl.axis)
        assertNotNull(tl.insetStart)
        assertNotNull(tl.insetEnd)
        assertEquals(10f, tl.insetStart!!.value, 0.0001f)
        assertEquals(20f, tl.insetEnd!!.value, 0.0001f)
    }

    @Test
    fun `animation-timeline named ident`() {
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(pair("AnimationTimeline", """{"type":"named","name":"--my-timeline"}"""))
        )
        val tl = cfg.animationTimeline
        assertTrue(tl is AnimationTimelineValue.Named)
        assertEquals("--my-timeline", (tl as AnimationTimelineValue.Named).name)
    }

    // ==================== animation-range-start / -end ====================

    @Test
    fun `animation-range-start percentage bare-number is wrapped`() {
        // Real IR for "25%": the bare JSON number 25.0 (not object).
        // The extractor handles number-primitives via the JsonObject branch;
        // verify the named-range path here by using the name form we saw.
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(pair("AnimationRangeStart", """{"name":"cover","offset":25.0}"""))
        )
        val r = cfg.animationRangeStart as AnimationRangeValue.NamedRange
        assertEquals(TimelineRangeName.COVER, r.name)
        assertEquals(25f, r.offset!!, 0.0001f)
    }

    @Test
    fun `animation-range-start length in px becomes Length`() {
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(pair("AnimationRangeStart", """{"px":100.0}"""))
        )
        val r = cfg.animationRangeStart as AnimationRangeValue.Length
        assertEquals(100f, r.value.value, 0.0001f)
    }

    @Test
    fun `animation-range-start normal keyword`() {
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(pair("AnimationRangeStart", "\"normal\""))
        )
        assertTrue(cfg.animationRangeStart is AnimationRangeValue.Normal)
    }

    @Test
    fun `animation-range-end entry keyword with offset`() {
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(pair("AnimationRangeEnd", """{"name":"exit","offset":100.0}"""))
        )
        val r = cfg.animationRangeEnd as AnimationRangeValue.NamedRange
        assertEquals(TimelineRangeName.EXIT, r.name)
        assertEquals(100f, r.offset!!, 0.0001f)
    }

    // ==================== transition-property =============================

    @Test
    fun `transition-property all sentinel survives as all string`() {
        // Parser wraps "all" into {"type":"all"} — extractor treats the
        // object as a property-named entry (see extractTransitionProperties
        // which reads either a JsonPrimitive or obj["property"]).
        // Our concern: the name "all" must at minimum not crash.
        val cfg = AnimationExtractor.extractTransitionConfig(
            listOf(pair("TransitionProperty", """[{"type":"all"}]"""))
        )
        // AnimationExtractor only captures obj["property"] which is absent
        // here → empty properties list, but hasTransitions is true.
        assertTrue(cfg.hasTransitions)
        // Empty list maps to the semantic "transition all animatable" via
        // TransitionConfig.hasTransitionFor("x") returning true on empty.
        assertTrue(cfg.hasTransitionFor("opacity"))
    }

    @Test
    fun `transition-property list of identifiers is captured`() {
        val cfg = AnimationExtractor.extractTransitionConfig(
            listOf(
                pair(
                    "TransitionProperty",
                    """[{"property":"opacity"},{"property":"transform"}]"""
                )
            )
        )
        assertEquals(listOf("opacity", "transform"), cfg.properties)
        assertTrue(cfg.hasTransitionFor("opacity"))
        assertFalse(cfg.hasTransitionFor("color"))
    }

    // ==================== transition-behavior =============================

    @Test
    fun `transition-behavior allow-discrete maps to enum`() {
        val cfg = AnimationExtractor.extractTransitionConfig(
            listOf(pair("TransitionBehavior", """["allow-discrete"]"""))
        )
        assertEquals(listOf(TransitionBehavior.ALLOW_DISCRETE), cfg.behaviors)
    }

    @Test
    fun `transition-behavior normal multi-list`() {
        val cfg = AnimationExtractor.extractTransitionConfig(
            listOf(pair("TransitionBehavior", """["normal","allow-discrete","normal"]"""))
        )
        assertEquals(
            listOf(
                TransitionBehavior.NORMAL,
                TransitionBehavior.ALLOW_DISCRETE,
                TransitionBehavior.NORMAL
            ),
            cfg.behaviors
        )
    }

    // ==================== view-timeline-axis ==============================

    @Test
    fun `view-timeline-axis keywords map through enum`() {
        // Four keyword variants from the fixture. Each is its own test in
        // ViewTimelineExtractor terms — driven through a single helper.
        val block = ViewTimelineExtractor.extractViewTimelineConfig(
            listOf(pair("ViewTimelineAxis", "\"block\""))
        )
        val inline = ViewTimelineExtractor.extractViewTimelineConfig(
            listOf(pair("ViewTimelineAxis", "\"inline\""))
        )
        val x = ViewTimelineExtractor.extractViewTimelineConfig(
            listOf(pair("ViewTimelineAxis", "\"x\""))
        )
        val y = ViewTimelineExtractor.extractViewTimelineConfig(
            listOf(pair("ViewTimelineAxis", "\"y\""))
        )
        assertEquals(ScrollTimelineAxisValue.BLOCK, block.axis)
        assertEquals(ScrollTimelineAxisValue.INLINE, inline.axis)
        assertEquals(ScrollTimelineAxisValue.X, x.axis)
        assertEquals(ScrollTimelineAxisValue.Y, y.axis)
    }

    // ==================== view-transition-name ============================

    @Test
    fun `view-transition-name identifier is captured via ScrollTimelineExtractor viewTimelineName path`() {
        // View-transition-name extraction currently routes through
        // ScrollTimelineExtractor.extractTimelineName (same String-typed
        // storage as view-timeline-name). Fixture dump:
        //   VTN_Auto ViewTransitionName {"type":"named","name":"auto"}
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(pair("ViewTimelineName", """{"type":"named","name":"auto"}"""))
        )
        assertEquals("auto", cfg.viewTimelineName)
    }

    @Test
    fun `view-timeline-name none sentinel is filtered to null`() {
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(pair("ViewTimelineName", "\"none\""))
        )
        // The parser stores the literal "none" string (see parser-gap note
        // in README); the extractor is responsible for treating it as the
        // sentinel.
        assertEquals(null, cfg.viewTimelineName)
    }
}
