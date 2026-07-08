package com.styleconverter.test.style.core.media

import android.content.Context
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.core.ir.IRMedia
import com.styleconverter.test.style.core.ir.IRProperty

/**
 * Evaluates CSS media queries at runtime and merges matching properties.
 *
 * Uses Compose's [LocalConfiguration] to detect current screen dimensions
 * and [isSystemInDarkTheme] for color scheme detection.
 *
 * ## Usage
 * ```kotlin
 * val effectiveProperties = MediaQueryApplier.applyMediaQueries(
 *     baseProperties = component.properties,
 *     mediaQueries = component.media
 * )
 * ```
 *
 * ## Supported Media Features
 * - Width constraints: min-width, max-width, width
 * - Height constraints: min-height, max-height, height
 * - Orientation: portrait, landscape
 * - Color scheme: prefers-color-scheme (light/dark)
 * - Aspect ratio: min-aspect-ratio, max-aspect-ratio
 */
object MediaQueryApplier {

    /**
     * Apply media queries to base properties based on current screen configuration.
     *
     * @param baseProperties The base component properties.
     * @param mediaQueries List of media query conditions with their properties.
     * @return Combined properties with matching media query overrides.
     */
    @Composable
    fun applyMediaQueries(
        baseProperties: List<IRProperty>,
        mediaQueries: List<IRMedia>
    ): List<IRProperty> {
        if (mediaQueries.isEmpty()) return baseProperties

        val screenInfo = rememberScreenInfo()

        return remember(baseProperties, mediaQueries, screenInfo) {
            mergeMatchingQueries(baseProperties, mediaQueries, screenInfo)
        }
    }

    /**
     * Get current screen information from Compose configuration.
     */
    @Composable
    fun rememberScreenInfo(): ScreenInfo {
        val configuration = LocalConfiguration.current
        val isDark = isSystemInDarkTheme()
        val context = LocalContext.current
        val view = LocalView.current

        return remember(configuration.screenWidthDp, configuration.screenHeightDp, isDark) {
            ScreenInfo(
                width = configuration.screenWidthDp.dp,
                height = configuration.screenHeightDp.dp,
                isDarkTheme = isDark,
                isStandalone = false,
                reducedMotionEnabled = isReducedMotionEnabled(context),
                highContrastEnabled = isHighContrastEnabled(context),
                hasHoverCapability = hasHoverCapability(view),
                hasTouchInput = hasTouchInput(context)
            )
        }
    }

    /**
     * Check if reduced motion is enabled in system settings.
     */
    private fun isReducedMotionEnabled(context: Context): Boolean {
        return try {
            val animatorDuration = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            animatorDuration == 0f
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if high contrast mode is enabled.
     */
    private fun isHighContrastEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(
                context.contentResolver,
                "high_text_contrast_enabled",
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if device has hover capability (mouse/trackpad connected).
     */
    private fun hasHoverCapability(view: View): Boolean {
        return view.isInTouchMode.not() || android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
    }

    /**
     * Check if device has touch input.
     */
    private fun hasTouchInput(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)
    }

    /**
     * Merge base properties with matching media query properties.
     */
    private fun mergeMatchingQueries(
        baseProperties: List<IRProperty>,
        mediaQueries: List<IRMedia>,
        screenInfo: ScreenInfo
    ): List<IRProperty> {
        val effectiveProperties = baseProperties.toMutableList()

        for (mediaQuery in mediaQueries) {
            if (evaluateQuery(mediaQuery.query, screenInfo)) {
                // Override/add properties from matching media query
                effectiveProperties.addAll(mediaQuery.properties)
            }
        }

        return effectiveProperties
    }

    /**
     * Evaluate a media query string against current screen info.
     */
    fun evaluateQuery(query: String, screenInfo: ScreenInfo): Boolean {
        val conditions = MediaQueryExtractor.parse(query)
        if (conditions.isEmpty()) return false

        var result = true
        var isFirstCondition = true

        for (condition in conditions) {
            val conditionResult = evaluateCondition(condition, screenInfo)
            val negatedResult = if (condition.negate) !conditionResult else conditionResult

            result = when {
                isFirstCondition -> negatedResult
                condition.operator == LogicalOperator.AND -> result && negatedResult
                condition.operator == LogicalOperator.OR -> result || negatedResult
                else -> result && negatedResult
            }

            isFirstCondition = false
        }

        return result
    }

    /**
     * Evaluate a single media query condition.
     */
    private fun evaluateCondition(config: MediaQueryConfig, screenInfo: ScreenInfo): Boolean {
        // Check width constraints
        config.minWidth?.let { minWidth ->
            if (screenInfo.width < minWidth) return false
        }
        config.maxWidth?.let { maxWidth ->
            if (screenInfo.width > maxWidth) return false
        }
        config.width?.let { width ->
            if (screenInfo.width != width) return false
        }

        // Check height constraints
        config.minHeight?.let { minHeight ->
            if (screenInfo.height < minHeight) return false
        }
        config.maxHeight?.let { maxHeight ->
            if (screenInfo.height > maxHeight) return false
        }
        config.height?.let { height ->
            if (screenInfo.height != height) return false
        }

        // Check orientation
        config.orientation?.let { orientation ->
            if (screenInfo.orientation != orientation) return false
        }

        // Check color scheme
        config.colorScheme?.let { colorScheme ->
            val matchesDark = colorScheme == ColorScheme.DARK
            if (screenInfo.isDarkTheme != matchesDark) return false
        }

        // Check aspect ratio
        config.minAspectRatio?.let { minRatio ->
            if (screenInfo.aspectRatio < minRatio) return false
        }
        config.maxAspectRatio?.let { maxRatio ->
            if (screenInfo.aspectRatio > maxRatio) return false
        }

        // Check display mode
        config.displayMode?.let { displayMode ->
            when (displayMode) {
                DisplayMode.STANDALONE -> if (!screenInfo.isStandalone) return false
                DisplayMode.BROWSER -> if (screenInfo.isStandalone) return false
                else -> { /* Other modes not directly supported on Android */ }
            }
        }

        // Check prefers-reduced-motion
        config.prefersReducedMotion?.let { pref ->
            if (screenInfo.reducedMotion != pref) return false
        }

        // Check prefers-contrast
        config.prefersContrast?.let { pref ->
            if (screenInfo.contrastPreference != pref) return false
        }

        // Check hover capability
        config.hoverCapability?.let { hover ->
            if (screenInfo.hoverCapability != hover) return false
        }

        // Check pointer type
        config.pointerType?.let { pointer ->
            if (screenInfo.pointerType != pointer) return false
        }

        return true
    }

    /**
     * Check if a specific media query matches current screen.
     *
     * Convenience function for simple query checks without property merging.
     */
    @Composable
    fun matches(query: String): Boolean {
        val screenInfo = rememberScreenInfo()
        return remember(query, screenInfo) {
            evaluateQuery(query, screenInfo)
        }
    }

    /**
     * Get the current screen width breakpoint.
     */
    @Composable
    fun currentBreakpoint(): Breakpoint {
        val screenInfo = rememberScreenInfo()
        return remember(screenInfo.width) {
            when {
                screenInfo.width >= Breakpoints.XXL -> Breakpoint.XXL
                screenInfo.width >= Breakpoints.XL -> Breakpoint.XL
                screenInfo.width >= Breakpoints.LG -> Breakpoint.LG
                screenInfo.width >= Breakpoints.MD -> Breakpoint.MD
                screenInfo.width >= Breakpoints.SM -> Breakpoint.SM
                else -> Breakpoint.XS
            }
        }
    }

    /**
     * Named breakpoint levels.
     */
    enum class Breakpoint {
        XS,  // < 576dp
        SM,  // >= 576dp
        MD,  // >= 768dp
        LG,  // >= 992dp
        XL,  // >= 1200dp
        XXL  // >= 1400dp
    }
}

/**
 * Compose utility functions for responsive design.
 */
object ResponsiveUtils {

    /**
     * Returns a value based on current breakpoint.
     *
     * Example:
     * ```kotlin
     * val columns = breakpoint(
     *     xs = 1,
     *     sm = 2,
     *     md = 3,
     *     lg = 4
     * )
     * ```
     */
    @Composable
    fun <T> breakpoint(
        xs: T,
        sm: T = xs,
        md: T = sm,
        lg: T = md,
        xl: T = lg,
        xxl: T = xl
    ): T {
        return when (MediaQueryApplier.currentBreakpoint()) {
            MediaQueryApplier.Breakpoint.XS -> xs
            MediaQueryApplier.Breakpoint.SM -> sm
            MediaQueryApplier.Breakpoint.MD -> md
            MediaQueryApplier.Breakpoint.LG -> lg
            MediaQueryApplier.Breakpoint.XL -> xl
            MediaQueryApplier.Breakpoint.XXL -> xxl
        }
    }

    /**
     * Check if current screen is mobile sized.
     */
    @Composable
    fun isMobile(): Boolean = MediaQueryApplier.matches("(max-width: 767px)")

    /**
     * Check if current screen is tablet sized.
     */
    @Composable
    fun isTablet(): Boolean = MediaQueryApplier.matches("(min-width: 768px) and (max-width: 991px)")

    /**
     * Check if current screen is desktop sized.
     */
    @Composable
    fun isDesktop(): Boolean = MediaQueryApplier.matches("(min-width: 992px)")

    /**
     * Check if device is in landscape orientation.
     */
    @Composable
    fun isLandscape(): Boolean = MediaQueryApplier.matches("(orientation: landscape)")

    /**
     * Check if device is in dark mode.
     */
    @Composable
    fun isDarkMode(): Boolean = MediaQueryApplier.matches("(prefers-color-scheme: dark)")
}
