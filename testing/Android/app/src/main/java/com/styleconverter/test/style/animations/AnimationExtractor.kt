package com.styleconverter.test.style.animations

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.*

/**
 * Extracts animation and transition configuration from IR properties.
 */
object AnimationExtractor {

    /**
     * Extract animation configuration from property pairs.
     */
    fun extractAnimationConfig(properties: List<Pair<String, JsonElement?>>): AnimationConfig {
        val names = mutableListOf<String>()
        val durations = mutableListOf<Long>()
        val timingFunctions = mutableListOf<TimingFunctionConfig>()
        val delays = mutableListOf<Long>()
        val iterationCounts = mutableListOf<AnimationIterationCount>()
        val directions = mutableListOf<AnimationDirection>()
        val fillModes = mutableListOf<AnimationFillMode>()
        val playStates = mutableListOf<AnimationPlayState>()
        var hasAny = false

        for ((type, data) in properties) {
            try {
                when (type) {
                    "AnimationName" -> {
                        hasAny = true
                        extractAnimationNames(data, names)
                    }
                    "AnimationDuration" -> {
                        hasAny = true
                        extractAnimationDurations(data, durations)
                    }
                    "AnimationTimingFunction" -> {
                        hasAny = true
                        extractTimingFunctions(data, timingFunctions)
                    }
                    "AnimationDelay" -> {
                        hasAny = true
                        extractAnimationDelays(data, delays)
                    }
                    "AnimationIterationCount" -> {
                        hasAny = true
                        extractIterationCounts(data, iterationCounts)
                    }
                    "AnimationDirection" -> {
                        hasAny = true
                        extractDirections(data, directions)
                    }
                    "AnimationFillMode" -> {
                        hasAny = true
                        extractFillModes(data, fillModes)
                    }
                    "AnimationPlayState" -> {
                        hasAny = true
                        extractPlayStates(data, playStates)
                    }
                }
            } catch (e: Exception) {
                // Skip malformed properties
            }
        }

        return AnimationConfig(
            names = names,
            durations = durations,
            timingFunctions = timingFunctions,
            delays = delays,
            iterationCounts = iterationCounts,
            directions = directions,
            fillModes = fillModes,
            playStates = playStates,
            hasAnimations = hasAny
        )
    }

    /**
     * Extract transition configuration from property pairs.
     */
    fun extractTransitionConfig(properties: List<Pair<String, JsonElement?>>): TransitionConfig {
        val transitionProperties = mutableListOf<String>()
        val durations = mutableListOf<Long>()
        val timingFunctions = mutableListOf<TimingFunctionConfig>()
        val delays = mutableListOf<Long>()
        val behaviors = mutableListOf<TransitionBehavior>()
        var hasAny = false

        for ((type, data) in properties) {
            try {
                when (type) {
                    "TransitionProperty" -> {
                        hasAny = true
                        extractTransitionProperties(data, transitionProperties)
                    }
                    "TransitionDuration" -> {
                        hasAny = true
                        extractTransitionDurations(data, durations)
                    }
                    "TransitionTimingFunction" -> {
                        hasAny = true
                        extractTimingFunctions(data, timingFunctions)
                    }
                    "TransitionDelay" -> {
                        hasAny = true
                        extractTransitionDelays(data, delays)
                    }
                    "TransitionBehavior" -> {
                        hasAny = true
                        extractTransitionBehaviors(data, behaviors)
                    }
                }
            } catch (e: Exception) {
                // Skip malformed properties
            }
        }

        return TransitionConfig(
            properties = transitionProperties,
            durations = durations,
            timingFunctions = timingFunctions,
            delays = delays,
            behaviors = behaviors,
            hasTransitions = hasAny
        )
    }

    // Animation name extraction
    private fun extractAnimationNames(data: JsonElement?, result: MutableList<String>) {
        if (data == null) return

        when (data) {
            is JsonArray -> {
                data.forEach { item ->
                    when (item) {
                        is JsonPrimitive -> {
                            val name = item.contentOrNull
                            if (name != null && name.lowercase() != "none") {
                                result.add(name)
                            }
                        }
                        is JsonObject -> {
                            val type = item["type"]?.jsonPrimitive?.contentOrNull
                            if (type == "identifier") {
                                item["name"]?.jsonPrimitive?.contentOrNull?.let { result.add(it) }
                            }
                        }
                        else -> {}
                    }
                }
            }
            is JsonObject -> {
                val namesArray = data["names"] as? JsonArray
                namesArray?.forEach { item ->
                    when (item) {
                        is JsonPrimitive -> {
                            val name = item.contentOrNull
                            if (name != null && name.lowercase() != "none") {
                                result.add(name)
                            }
                        }
                        is JsonObject -> {
                            val type = item["type"]?.jsonPrimitive?.contentOrNull
                            if (type == "identifier") {
                                item["name"]?.jsonPrimitive?.contentOrNull?.let { result.add(it) }
                            }
                        }
                        else -> {}
                    }
                }
            }
            else -> {}
        }
    }

    // Duration extraction
    private fun extractAnimationDurations(data: JsonElement?, result: MutableList<Long>) {
        if (data == null) return

        val durationsArray = when (data) {
            is JsonArray -> data
            is JsonObject -> {
                data["value"]?.let { value ->
                    when (value) {
                        is JsonObject -> value["durations"] as? JsonArray
                        is JsonArray -> value
                        else -> null
                    }
                } ?: data["durations"] as? JsonArray
            }
            else -> null
        }

        durationsArray?.forEach { item ->
            extractTimeMs(item)?.let { result.add(it) }
        }
    }

    private fun extractTransitionDurations(data: JsonElement?, result: MutableList<Long>) {
        if (data == null) return

        val durationsArray = when (data) {
            is JsonArray -> data
            is JsonObject -> data["durations"] as? JsonArray ?: data["value"] as? JsonArray
            else -> null
        }

        durationsArray?.forEach { item ->
            extractTimeMs(item)?.let { result.add(it) }
        }
    }

    private fun extractAnimationDelays(data: JsonElement?, result: MutableList<Long>) {
        if (data == null) return

        val delaysArray = when (data) {
            is JsonArray -> data
            is JsonObject -> data["delays"] as? JsonArray ?: data["value"] as? JsonArray
            else -> null
        }

        delaysArray?.forEach { item ->
            extractTimeMs(item)?.let { result.add(it) }
        }
    }

    private fun extractTransitionDelays(data: JsonElement?, result: MutableList<Long>) {
        if (data == null) return

        val delaysArray = when (data) {
            is JsonArray -> data
            is JsonObject -> data["delays"] as? JsonArray ?: data["value"] as? JsonArray
            else -> null
        }

        delaysArray?.forEach { item ->
            extractTimeMs(item)?.let { result.add(it) }
        }
    }

    private fun extractTimeMs(data: JsonElement): Long? {
        return when (data) {
            is JsonPrimitive -> data.longOrNull ?: data.doubleOrNull?.toLong()
            is JsonObject -> {
                data["ms"]?.jsonPrimitive?.let { ms ->
                    ms.longOrNull ?: ms.doubleOrNull?.toLong()
                }
            }
            else -> null
        }
    }

    // Timing function extraction
    private fun extractTimingFunctions(data: JsonElement?, result: MutableList<TimingFunctionConfig>) {
        if (data == null) return

        val functionsArray = when (data) {
            is JsonArray -> data
            is JsonObject -> data["functions"] as? JsonArray
            else -> null
        }

        functionsArray?.forEach { item ->
            extractTimingFunction(item)?.let { result.add(it) }
        }
    }

    private fun extractTimingFunction(data: JsonElement): TimingFunctionConfig? {
        if (data !is JsonObject) return null

        // Check for cubic-bezier
        val cb = data["cb"]?.let { cbEl ->
            when (cbEl) {
                is JsonArray -> cbEl.mapNotNull { it.jsonPrimitive.doubleOrNull }
                else -> null
            }
        }

        // Check for steps
        val stepsData = data["steps"] as? JsonObject
        val stepsCount = stepsData?.get("n")?.jsonPrimitive?.intOrNull
        val stepsPosition = stepsData?.get("pos")?.jsonPrimitive?.contentOrNull

        val original = data["original"]?.jsonPrimitive?.contentOrNull

        // Map keywords to bezier if no explicit cb
        if (cb == null && stepsCount == null && original != null) {
            return when (original.lowercase()) {
                "linear" -> TimingFunctionConfig.LINEAR
                "ease" -> TimingFunctionConfig.EASE
                "ease-in" -> TimingFunctionConfig.EASE_IN
                "ease-out" -> TimingFunctionConfig.EASE_OUT
                "ease-in-out" -> TimingFunctionConfig.EASE_IN_OUT
                else -> TimingFunctionConfig(null, null, null, original)
            }
        }

        return TimingFunctionConfig(
            cubicBezier = if (cb?.size == 4) cb else null,
            stepsCount = stepsCount,
            stepsPosition = stepsPosition,
            original = original
        )
    }

    // Iteration count extraction
    private fun extractIterationCounts(data: JsonElement?, result: MutableList<AnimationIterationCount>) {
        if (data == null) return

        val countsArray = when (data) {
            is JsonArray -> data
            is JsonObject -> data["counts"] as? JsonArray
            else -> null
        }

        countsArray?.forEach { item ->
            when (item) {
                is JsonPrimitive -> {
                    val content = item.contentOrNull
                    if (content?.lowercase() == "infinite") {
                        result.add(AnimationIterationCount.Infinite)
                    } else {
                        item.doubleOrNull?.let {
                            result.add(AnimationIterationCount.Count(it))
                        }
                    }
                }
                is JsonObject -> {
                    if (item["infinite"]?.jsonPrimitive?.booleanOrNull == true) {
                        result.add(AnimationIterationCount.Infinite)
                    } else {
                        item["number"]?.jsonPrimitive?.doubleOrNull?.let {
                            result.add(AnimationIterationCount.Count(it))
                        }
                    }
                }
                else -> {}
            }
        }
    }

    // Direction extraction
    private fun extractDirections(data: JsonElement?, result: MutableList<AnimationDirection>) {
        if (data == null) return

        val directionsArray = when (data) {
            is JsonArray -> data
            is JsonObject -> data["directions"] as? JsonArray
            else -> null
        }

        directionsArray?.forEach { item ->
            val keyword = item.jsonPrimitive.contentOrNull?.uppercase()?.replace("-", "_")
            val direction = try {
                keyword?.let { AnimationDirection.valueOf(it) }
            } catch (e: Exception) {
                null
            }
            direction?.let { result.add(it) }
        }
    }

    // Fill mode extraction
    private fun extractFillModes(data: JsonElement?, result: MutableList<AnimationFillMode>) {
        if (data == null) return

        val modesArray = when (data) {
            is JsonArray -> data
            is JsonObject -> data["fillModes"] as? JsonArray ?: data["modes"] as? JsonArray
            else -> null
        }

        modesArray?.forEach { item ->
            val keyword = item.jsonPrimitive.contentOrNull?.uppercase()
            val mode = try {
                keyword?.let { AnimationFillMode.valueOf(it) }
            } catch (e: Exception) {
                null
            }
            mode?.let { result.add(it) }
        }
    }

    // Play state extraction
    private fun extractPlayStates(data: JsonElement?, result: MutableList<AnimationPlayState>) {
        if (data == null) return

        val statesArray = when (data) {
            is JsonArray -> data
            is JsonObject -> data["states"] as? JsonArray
            else -> null
        }

        statesArray?.forEach { item ->
            val keyword = item.jsonPrimitive.contentOrNull?.uppercase()
            val state = try {
                keyword?.let { AnimationPlayState.valueOf(it) }
            } catch (e: Exception) {
                null
            }
            state?.let { result.add(it) }
        }
    }

    // Transition properties extraction
    private fun extractTransitionProperties(data: JsonElement?, result: MutableList<String>) {
        if (data == null) return

        val propsArray = when (data) {
            is JsonArray -> data
            is JsonObject -> data["properties"] as? JsonArray
            else -> null
        }

        propsArray?.forEach { item ->
            when (item) {
                is JsonPrimitive -> {
                    item.contentOrNull?.let { result.add(it) }
                }
                is JsonObject -> {
                    item["property"]?.jsonPrimitive?.contentOrNull?.let { result.add(it) }
                }
                else -> {}
            }
        }
    }

    // Transition behavior extraction
    private fun extractTransitionBehaviors(data: JsonElement?, result: MutableList<TransitionBehavior>) {
        if (data == null) return

        val behaviorsArray = when (data) {
            is JsonArray -> data
            is JsonObject -> data["behaviors"] as? JsonArray
            else -> null
        }

        behaviorsArray?.forEach { item ->
            val keyword = item.jsonPrimitive.contentOrNull?.uppercase()?.replace("-", "_")
            val behavior = when (keyword) {
                "ALLOW_DISCRETE" -> TransitionBehavior.ALLOW_DISCRETE
                else -> TransitionBehavior.NORMAL
            }
            result.add(behavior)
        }
    }

    /**
     * Check if a property type is animation-related.
     */
    fun isAnimationProperty(type: String): Boolean {
        return type in ANIMATION_PROPERTIES
    }

    private val ANIMATION_PROPERTIES = setOf(
        "AnimationName", "AnimationDuration", "AnimationTimingFunction",
        "AnimationDelay", "AnimationIterationCount", "AnimationDirection",
        "AnimationFillMode", "AnimationPlayState",
        "TransitionProperty", "TransitionDuration", "TransitionTimingFunction",
        "TransitionDelay", "TransitionBehavior"
    )
}
