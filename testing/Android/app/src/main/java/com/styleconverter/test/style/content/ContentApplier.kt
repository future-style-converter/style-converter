package com.styleconverter.test.style.content

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.styleconverter.test.style.core.ir.IRProperty
import com.styleconverter.test.style.core.ir.IRSelector
import com.styleconverter.test.style.StyleApplier
import com.styleconverter.test.style.typography.TextStyleApplier

/**
 * Applies CSS ::before/::after pseudo-elements as Compose content.
 *
 * ## CSS Features
 * ```css
 * .element::before {
 *     content: "→ ";
 *     color: blue;
 * }
 * .element::after {
 *     content: " ←";
 *     color: red;
 * }
 * ```
 *
 * ## Compose Implementation
 * Wraps the main content in a layout with before/after elements:
 * - Block display → Column with before, content, after
 * - Inline display → Row with before, content, after
 *
 * ## Limitations
 * - attr() requires runtime attribute lookup (not fully supported)
 * - Complex counters require counter state management
 * - URL content requires image loading
 */
object ContentApplier {

    /**
     * Configuration for a pseudo-element (::before or ::after).
     */
    data class PseudoElementConfig(
        val content: List<ContentValue>,
        val properties: List<IRProperty>,
        val isInline: Boolean = true
    ) {
        val hasContent: Boolean get() = content.isNotEmpty() &&
                content.any { it !is ContentValue.None && it !is ContentValue.Normal }
    }

    /**
     * Combined before/after configuration.
     */
    data class BeforeAfterConfig(
        val before: PseudoElementConfig? = null,
        val after: PseudoElementConfig? = null,
        val isInline: Boolean = true
    ) {
        val hasBeforeAfter: Boolean get() = before?.hasContent == true || after?.hasContent == true
    }

    /**
     * Extract before/after configuration from component selectors.
     */
    fun extractBeforeAfterConfig(selectors: List<IRSelector>): BeforeAfterConfig {
        var before: PseudoElementConfig? = null
        var after: PseudoElementConfig? = null
        var isInline = true

        for (selector in selectors) {
            val condition = selector.condition.lowercase()

            when {
                condition == "::before" || condition == ":before" -> {
                    val propertyPairs = selector.properties.map { it.type to it.data }
                    val content = ContentExtractor.extractContentValues(propertyPairs)
                    val displayType = extractDisplayType(propertyPairs)
                    isInline = isInline && (displayType == "inline" || displayType == null)
                    before = PseudoElementConfig(
                        content = content,
                        properties = selector.properties,
                        isInline = displayType == "inline" || displayType == null
                    )
                }
                condition == "::after" || condition == ":after" -> {
                    val propertyPairs = selector.properties.map { it.type to it.data }
                    val content = ContentExtractor.extractContentValues(propertyPairs)
                    val displayType = extractDisplayType(propertyPairs)
                    isInline = isInline && (displayType == "inline" || displayType == null)
                    after = PseudoElementConfig(
                        content = content,
                        properties = selector.properties,
                        isInline = displayType == "inline" || displayType == null
                    )
                }
            }
        }

        return BeforeAfterConfig(before = before, after = after, isInline = isInline)
    }

    /**
     * Extract display type from properties.
     */
    private fun extractDisplayType(properties: List<Pair<String, kotlinx.serialization.json.JsonElement?>>): String? {
        for ((type, data) in properties) {
            if (type == "Display") {
                val keyword = com.styleconverter.test.style.core.types.ValueExtractors.extractKeyword(data)
                return keyword?.lowercase()
            }
        }
        return null
    }

    /**
     * Wrap content with before/after pseudo-elements.
     *
     * @param config The before/after configuration
     * @param counters Current counter state for counter() content
     * @param quoteLevel Current quote nesting level
     * @param content The main content composable
     */
    @Composable
    fun ContentWithPseudoElements(
        config: BeforeAfterConfig,
        counters: CounterState = LocalCounterState.current,
        quoteLevel: Int = 0,
        content: @Composable () -> Unit
    ) {
        if (!config.hasBeforeAfter) {
            content()
            return
        }

        val quotesConfig = QuotesConfig.Default

        if (config.isInline) {
            // Inline layout - use Row
            Row {
                // ::before element
                config.before?.let { beforeConfig ->
                    if (beforeConfig.hasContent) {
                        PseudoElement(
                            config = beforeConfig,
                            counters = counters,
                            quotesConfig = quotesConfig,
                            quoteLevel = quoteLevel
                        )
                    }
                }

                // Main content
                content()

                // ::after element
                config.after?.let { afterConfig ->
                    if (afterConfig.hasContent) {
                        PseudoElement(
                            config = afterConfig,
                            counters = counters,
                            quotesConfig = quotesConfig,
                            quoteLevel = quoteLevel
                        )
                    }
                }
            }
        } else {
            // Block layout - use Column
            Column {
                // ::before element
                config.before?.let { beforeConfig ->
                    if (beforeConfig.hasContent) {
                        PseudoElement(
                            config = beforeConfig,
                            counters = counters,
                            quotesConfig = quotesConfig,
                            quoteLevel = quoteLevel
                        )
                    }
                }

                // Main content
                content()

                // ::after element
                config.after?.let { afterConfig ->
                    if (afterConfig.hasContent) {
                        PseudoElement(
                            config = afterConfig,
                            counters = counters,
                            quotesConfig = quotesConfig,
                            quoteLevel = quoteLevel
                        )
                    }
                }
            }
        }
    }

    /**
     * Render a single pseudo-element.
     */
    @Composable
    private fun PseudoElement(
        config: PseudoElementConfig,
        counters: CounterState,
        quotesConfig: QuotesConfig,
        quoteLevel: Int
    ) {
        // Apply modifier from pseudo-element properties
        val modifier = try {
            StyleApplier.applyProperties(config.properties)
        } catch (e: Exception) {
            Modifier
        }

        // Extract text style
        val textStyle = try {
            TextStyleApplier.extractTextStyle(config.properties)
        } catch (e: Exception) {
            TextStyle.Default
        }

        // Check for image content first
        val imageUrl = config.content.filterIsInstance<ContentValue.Url>().firstOrNull()

        // Build text content (uses @Composable function for attr() support)
        val textContent = buildContentString(
            content = config.content,
            counters = counters,
            quotesConfig = quotesConfig,
            quoteLevel = quoteLevel
        )

        // Render content
        Box(modifier = modifier) {
            when {
                imageUrl != null -> {
                    // Render image
                    PseudoElementImage(url = imageUrl.url)
                }
                textContent.isNotEmpty() -> {
                    // Render text
                    Text(
                        text = textContent,
                        style = textStyle
                    )
                }
            }
        }
    }

    /**
     * Render image content for pseudo-element using Coil for async loading.
     *
     * @param url The URL of the image to load
     * @param modifier Optional modifier for the image
     * @param defaultSize Default size when no size is specified (24.dp)
     * @param contentScale How to scale the image content
     */
    @Composable
    private fun PseudoElementImage(
        url: String,
        modifier: Modifier = Modifier,
        defaultSize: Dp = 24.dp,
        contentScale: ContentScale = ContentScale.Fit
    ) {
        val context = LocalContext.current

        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = null, // Decorative content
            modifier = modifier.then(
                if (modifier == Modifier) Modifier.size(defaultSize) else Modifier
            ),
            contentScale = contentScale
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading -> {
                    Box(
                        modifier = Modifier.size(defaultSize),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    // Show placeholder on error
                    Box(
                        modifier = Modifier
                            .size(defaultSize)
                            .background(Color.LightGray.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⚠",
                            style = TextStyle.Default
                        )
                    }
                }
                else -> {
                    SubcomposeAsyncImageContent()
                }
            }
        }
    }

    /**
     * Render image content with sizing from CSS properties.
     */
    @Composable
    fun PseudoElementImageWithSizing(
        url: String,
        width: Dp? = null,
        height: Dp? = null,
        contentScale: ContentScale = ContentScale.Fit
    ) {
        val sizeModifier = when {
            width != null && height != null -> Modifier.size(width, height)
            width != null -> Modifier.size(width)
            height != null -> Modifier.size(height)
            else -> Modifier
        }

        PseudoElementImage(
            url = url,
            modifier = sizeModifier,
            contentScale = contentScale
        )
    }

    /**
     * Build the content string from ContentValue list.
     */
    @Composable
    private fun buildContentString(
        content: List<ContentValue>,
        counters: CounterState,
        quotesConfig: QuotesConfig,
        quoteLevel: Int
    ): String {
        val attributeProvider = LocalAttributeProvider.current
        return buildContentStringInternal(content, counters, quotesConfig, quoteLevel, attributeProvider)
    }

    /**
     * Internal function to build content string with attribute provider.
     */
    private fun buildContentStringInternal(
        content: List<ContentValue>,
        counters: CounterState,
        quotesConfig: QuotesConfig,
        quoteLevel: Int,
        attributeProvider: AttributeProvider
    ): String {
        val builder = StringBuilder()

        for (value in content) {
            when (value) {
                is ContentValue.Text -> builder.append(value.value)
                is ContentValue.Counter -> {
                    val counterValue = counters.get(value.name)
                    builder.append(counters.formatCounterValue(counterValue, value.style))
                }
                is ContentValue.Counters -> {
                    // Get all nested counter values with separator
                    val formatted = counters.getFormatted(value.name, value.separator, value.style)
                    builder.append(formatted)
                }
                is ContentValue.OpenQuote -> {
                    val pair = quotesConfig.getQuotePair(quoteLevel)
                    builder.append(pair.open)
                }
                is ContentValue.CloseQuote -> {
                    val pair = quotesConfig.getQuotePair(quoteLevel)
                    builder.append(pair.close)
                }
                is ContentValue.NoOpenQuote -> {
                    // Increases nesting but no visible quote
                }
                is ContentValue.NoCloseQuote -> {
                    // Decreases nesting but no visible quote
                }
                is ContentValue.Attr -> {
                    // Get attribute value from provider
                    val attrValue = attributeProvider.getAttribute(value.attributeName)
                    if (attrValue != null) {
                        builder.append(attrValue)
                    } else {
                        // Fallback: show placeholder if attribute not found
                        builder.append("[${value.attributeName}]")
                    }
                }
                is ContentValue.Url -> {
                    // URL content handled separately as image
                }
                is ContentValue.Normal, is ContentValue.None -> {
                    // No content
                }
            }
        }

        return builder.toString()
    }
}

/**
 * Counter state for CSS counters with hierarchy support.
 *
 * CSS counters have hierarchical behavior:
 * - counter-reset creates a new counter scope
 * - counter-increment increments the counter in the current scope
 * - counter() returns the current scope's value
 * - counters() returns all ancestor values with a separator
 *
 * ## Example
 * ```html
 * <ol style="counter-reset: section">
 *   <li style="counter-increment: section">Section <!-- counter: 1 -->
 *     <ol style="counter-reset: section">
 *       <li style="counter-increment: section">Subsection <!-- counters: 1.1 -->
 *       <li style="counter-increment: section">Subsection <!-- counters: 1.2 -->
 *     </ol>
 *   </li>
 *   <li style="counter-increment: section">Section <!-- counter: 2 -->
 * </ol>
 * ```
 */
class CounterState(
    private val values: MutableMap<String, Int> = mutableMapOf(),
    private val parent: CounterState? = null,
    /** Track which counters were reset at this level (creating new scopes) */
    private val localCounters: MutableSet<String> = mutableSetOf()
) {
    /**
     * Get the current counter value (just the innermost scope).
     */
    fun get(name: String): Int {
        return if (name in localCounters) {
            values[name] ?: 0
        } else {
            parent?.get(name) ?: (values[name] ?: 0)
        }
    }

    /**
     * Get all counter values for counters() function (all scopes from root to current).
     * Returns a list from outermost to innermost scope.
     */
    fun getAll(name: String): List<Int> {
        val result = mutableListOf<Int>()
        collectAllValues(name, result)
        return result.reversed()
    }

    private fun collectAllValues(name: String, result: MutableList<Int>) {
        // First collect from parent (if any)
        parent?.collectAllValues(name, result)

        // Then add our value if we have this counter locally
        if (name in localCounters || (parent == null && name in values)) {
            result.add(values[name] ?: 0)
        }
    }

    /**
     * Get formatted counters() string with separator.
     */
    fun getFormatted(name: String, separator: String, style: ListStyleType): String {
        val allValues = getAll(name)
        if (allValues.isEmpty()) return formatCounterValue(0, style)
        return allValues.joinToString(separator) { formatCounterValue(it, style) }
    }

    /**
     * Format a single counter value according to list style type.
     */
    fun formatCounterValue(value: Int, style: ListStyleType): String {
        return when (style) {
            ListStyleType.DECIMAL -> value.toString()
            ListStyleType.DECIMAL_LEADING_ZERO -> value.toString().padStart(2, '0')
            ListStyleType.LOWER_ROMAN -> toRoman(value).lowercase()
            ListStyleType.UPPER_ROMAN -> toRoman(value)
            ListStyleType.LOWER_LATIN, ListStyleType.LOWER_ALPHA ->
                if (value in 1..26) ('a' + value - 1).toString() else value.toString()
            ListStyleType.UPPER_LATIN, ListStyleType.UPPER_ALPHA ->
                if (value in 1..26) ('A' + value - 1).toString() else value.toString()
            ListStyleType.LOWER_GREEK ->
                if (value in 1..24) ('α'.code + value - 1).toChar().toString() else value.toString()
            ListStyleType.DISC -> "•"
            ListStyleType.CIRCLE -> "○"
            ListStyleType.SQUARE -> "■"
            ListStyleType.ARMENIAN -> value.toString() // Simplified
            ListStyleType.GEORGIAN -> value.toString() // Simplified
            ListStyleType.NONE -> ""
        }
    }

    private fun toRoman(num: Int): String {
        if (num <= 0 || num > 3999) return num.toString()
        val romanPairs = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
            100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
            10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
        )
        var n = num
        val result = StringBuilder()
        for ((value, symbol) in romanPairs) {
            while (n >= value) {
                result.append(symbol)
                n -= value
            }
        }
        return result.toString()
    }

    /**
     * Set a counter value directly.
     */
    fun set(name: String, value: Int) {
        values[name] = value
        localCounters.add(name)
    }

    /**
     * Increment a counter.
     */
    fun increment(name: String, by: Int = 1) {
        values[name] = get(name) + by
        // If this is a new counter at this level, mark it as local
        if (name !in values) {
            localCounters.add(name)
        }
    }

    /**
     * Reset a counter, creating a new scope at this level.
     */
    fun reset(name: String, value: Int = 0) {
        values[name] = value
        localCounters.add(name) // Mark as locally scoped
    }

    /**
     * Apply counter configuration.
     */
    fun applyConfig(config: CounterConfig) {
        config.reset.forEach { (name, value) -> reset(name, value) }
        config.set.forEach { (name, value) -> set(name, value) }
        config.increment.forEach { (name, value) -> increment(name, value) }
    }

    /**
     * Create a child scope with this state as parent.
     */
    fun createChildScope(): CounterState {
        return CounterState(
            values = mutableMapOf(),
            parent = this,
            localCounters = mutableSetOf()
        )
    }

    /**
     * Check if a counter exists in this scope or any parent.
     */
    fun hasCounter(name: String): Boolean {
        return name in values || (parent?.hasCounter(name) ?: false)
    }
}

/**
 * CompositionLocal for counter state.
 */
val LocalCounterState = compositionLocalOf { CounterState() }

/**
 * Provider for counter state with proper hierarchy support.
 *
 * Creates a new counter scope that inherits from the parent.
 * Counters reset at this level create new scopes, allowing
 * counters() to show the full hierarchy.
 */
@Composable
fun CounterStateProvider(
    config: CounterConfig = CounterConfig(),
    content: @Composable () -> Unit
) {
    val parentState = LocalCounterState.current

    // Create a child scope that links to parent for hierarchy
    val state = remember(parentState, config) {
        val childState = if (config.hasCounters) {
            // Create child scope with parent reference for hierarchy
            parentState.createChildScope().also { it.applyConfig(config) }
        } else {
            // No counter changes at this level, reuse parent
            parentState
        }
        childState
    }

    CompositionLocalProvider(LocalCounterState provides state) {
        content()
    }
}

// ==================== ATTRIBUTE PROVIDER ====================

/**
 * Interface for providing attribute values for CSS attr() function.
 *
 * In CSS, attr() retrieves an attribute's value from the element. In SDUI context,
 * this maps to component data/properties that can be referenced by name.
 *
 * ## Usage
 * ```kotlin
 * // Define custom attribute provider
 * val myProvider = AttributeProvider { name ->
 *     when (name) {
 *         "data-price" -> "$19.99"
 *         "data-count" -> "5"
 *         else -> null
 *     }
 * }
 *
 * // Provide to composition
 * AttributeProviderProvider(myProvider) {
 *     // Content can now use attr(data-price) etc.
 * }
 * ```
 */
fun interface AttributeProvider {
    /**
     * Get the value of an attribute by name.
     *
     * @param name The attribute name (e.g., "data-tooltip", "aria-label", "href")
     * @return The attribute value as a string, or null if not found
     */
    fun getAttribute(name: String): String?

    companion object {
        /**
         * Empty provider that returns null for all attributes.
         */
        val Empty = AttributeProvider { null }

        /**
         * Create a provider from a map of attribute name to value.
         */
        fun fromMap(attributes: Map<String, String>): AttributeProvider =
            AttributeProvider { name -> attributes[name] }

        /**
         * Create a provider from a component's data properties.
         */
        fun fromData(data: Map<String, Any?>): AttributeProvider =
            AttributeProvider { name -> data[name]?.toString() }
    }
}

/**
 * CompositionLocal for attribute provider.
 */
val LocalAttributeProvider = compositionLocalOf<AttributeProvider> { AttributeProvider.Empty }

/**
 * Provider for element attributes.
 *
 * @param provider The attribute provider to use
 * @param content The composable content
 */
@Composable
fun AttributeProviderProvider(
    provider: AttributeProvider,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAttributeProvider provides provider) {
        content()
    }
}

/**
 * Provider for element attributes using a map.
 *
 * @param attributes Map of attribute name to value
 * @param content The composable content
 */
@Composable
fun AttributeProviderProvider(
    attributes: Map<String, String>,
    content: @Composable () -> Unit
) {
    val provider = remember(attributes) { AttributeProvider.fromMap(attributes) }
    CompositionLocalProvider(LocalAttributeProvider provides provider) {
        content()
    }
}

/**
 * Combined provider for both counters and attributes.
 */
@Composable
fun ContentContextProvider(
    counterConfig: CounterConfig = CounterConfig(),
    attributes: Map<String, String> = emptyMap(),
    content: @Composable () -> Unit
) {
    CounterStateProvider(counterConfig) {
        AttributeProviderProvider(attributes) {
            content()
        }
    }
}
