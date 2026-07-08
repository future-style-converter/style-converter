package app.irmodels

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

/**
 * Common value types used across CSS property representations in the IR model.
 *
 * ## Purpose
 * These data classes provide type-safe representations of CSS value types.
 * They serve as building blocks for property-specific implementations.
 *
 * ## Design Philosophy
 * - **Precise Type Constraints**: Each type accepts only valid CSS values
 * - **Platform Agnostic**: Represents values in a neutral format
 * - **Fully Serializable**: Can be serialized to/from JSON for IR persistence
 * - **No Validation Logic**: Parsers handle validation before creating these types
 */

/**
 * Represents a length value with unit.
 *
 * CSS lengths can be absolute (px, pt, cm, etc.) or relative (em, rem, %, vw, vh, etc.).
 *
 * ## Examples
 * - `IRLength(16.0, LengthUnit.PX)` represents "16px"
 * - `IRLength(1.5, LengthUnit.EM)` represents "1.5em"
 * - `IRLength(100.0, LengthUnit.PERCENT)` represents "100%"
 *
 * ## Supported Units
 * - **Absolute**: PX, PT, CM, MM, IN, PC
 * - **Relative to font**: EM, REM, EX, CH
 * - **Viewport relative**: VW, VH, VMIN, VMAX
 * - **Percentage**: PERCENT (relative to parent/context)
 * - **Grid**: FR (fraction of available space in CSS Grid)
 * - **Platform**: DP, SP (Android density-independent pixels)
 *
 * @property value The numeric value of the length
 * @property unit The unit of measurement
 */
/**
 * CSS Length value with optional pixel normalization.
 *
 * ## Dual Storage
 * - `pixels`: Normalized value in CSS pixels (null for relative/contextual units)
 * - `originalValue` + `originalUnit`: Original format for CSS regeneration
 *
 * ## Normalization
 * - Absolute units (px, pt, cm, mm, in, pc, Q) → normalized to pixels
 * - Relative units (em, rem, %, vw, vh, etc.) → pixels is null (needs context)
 *
 * ## Conversion Factors (CSS spec)
 * - 1in = 96px
 * - 1cm = 37.795px (96/2.54)
 * - 1mm = 3.7795px
 * - 1pt = 1.333px (96/72)
 * - 1pc = 16px (96/6)
 * - 1Q = 0.945px (96/101.6, quarter-millimeter)
 */
@Serializable(with = IRLengthSerializer::class)
data class IRLength(
    val pixels: Double?,
    val originalValue: Double,
    val originalUnit: LengthUnit
) {
    // Legacy compatibility - maps to originalValue
    val value: Double get() = originalValue
    // Legacy compatibility - maps to originalUnit
    val unit: LengthUnit get() = originalUnit

    companion object {
        // CSS spec conversion factors to pixels
        private const val PX_PER_IN = 96.0
        private const val PX_PER_CM = 96.0 / 2.54
        private const val PX_PER_MM = 96.0 / 25.4
        private const val PX_PER_PT = 96.0 / 72.0
        private const val PX_PER_PC = 96.0 / 6.0
        private const val PX_PER_Q = 96.0 / 101.6  // Quarter-millimeter

        /** Create from pixels - already normalized */
        fun fromPx(value: Double) = IRLength(pixels = value, originalValue = value, originalUnit = LengthUnit.PX)

        /** Create from points (1pt = 1/72 inch) */
        fun fromPt(value: Double) = IRLength(pixels = value * PX_PER_PT, originalValue = value, originalUnit = LengthUnit.PT)

        /** Create from centimeters */
        fun fromCm(value: Double) = IRLength(pixels = value * PX_PER_CM, originalValue = value, originalUnit = LengthUnit.CM)

        /** Create from millimeters */
        fun fromMm(value: Double) = IRLength(pixels = value * PX_PER_MM, originalValue = value, originalUnit = LengthUnit.MM)

        /** Create from inches */
        fun fromIn(value: Double) = IRLength(pixels = value * PX_PER_IN, originalValue = value, originalUnit = LengthUnit.IN)

        /** Create from picas (1pc = 12pt) */
        fun fromPc(value: Double) = IRLength(pixels = value * PX_PER_PC, originalValue = value, originalUnit = LengthUnit.PC)

        /** Create from quarter-millimeters */
        fun fromQ(value: Double) = IRLength(pixels = value * PX_PER_Q, originalValue = value, originalUnit = LengthUnit.Q)

        /** Create from relative unit (no pixel normalization possible) */
        fun fromRelative(value: Double, unit: LengthUnit) = IRLength(pixels = null, originalValue = value, originalUnit = unit)

        /** Create from value and unit - automatically normalizes absolute units */
        fun from(value: Double, unit: LengthUnit): IRLength = when (unit) {
            LengthUnit.PX -> fromPx(value)
            LengthUnit.PT -> fromPt(value)
            LengthUnit.CM -> fromCm(value)
            LengthUnit.MM -> fromMm(value)
            LengthUnit.IN -> fromIn(value)
            LengthUnit.PC -> fromPc(value)
            LengthUnit.Q -> fromQ(value)
            // Android units - treat as pixels for now
            LengthUnit.DP, LengthUnit.SP -> IRLength(pixels = value, originalValue = value, originalUnit = unit)
            // All relative/contextual units - can't normalize
            else -> fromRelative(value, unit)
        }

        /** Check if a unit is absolute (can be normalized to pixels) */
        fun isAbsoluteUnit(unit: LengthUnit): Boolean = unit in setOf(
            LengthUnit.PX, LengthUnit.PT, LengthUnit.CM, LengthUnit.MM,
            LengthUnit.IN, LengthUnit.PC, LengthUnit.Q,
            LengthUnit.DP, LengthUnit.SP  // Android units, treat as absolute
        )
    }

    enum class LengthUnit {
        /** Pixels - absolute unit (web: 1px = 1/96th inch, mobile: varies by density) */
        PX,

        /** Density-independent Pixels - Android absolute unit (1dp = 1px at 160dpi) */
        DP,

        /** Scale-independent Pixels - Android font unit (like dp but scales with user font preference) */
        SP,

        /** Relative to element's font-size (e.g., 2em = 2x current font size) */
        EM,

        /** Relative to root element's font-size (typically <html>) */
        REM,

        /** Percentage relative to parent or containing context */
        PERCENT,

        /** 1% of viewport width */
        VW,

        /** 1% of viewport height */
        VH,

        /** Minimum of VW and VH */
        VMIN,

        /** Maximum of VW and VH */
        VMAX,

        /** Points - absolute unit (1pt = 1/72nd inch) */
        PT,

        /** Centimeters - absolute unit */
        CM,

        /** Millimeters - absolute unit */
        MM,

        /** Inches - absolute unit */
        IN,

        /** Picas - absolute unit (1pc = 12pt) */
        PC,

        /** Relative to font's x-height (height of lowercase 'x') */
        EX,

        /** Relative to width of '0' glyph in element's font */
        CH,

        /** Fraction of available space in CSS Grid layout */
        FR,

        // === Modern Viewport Units (CSS Values Level 4) ===

        /** Small viewport width - 1% of small viewport's width */
        SVW,
        /** Small viewport height - 1% of small viewport's height */
        SVH,
        /** Small viewport block - 1% of small viewport in block axis */
        SVB,
        /** Small viewport inline - 1% of small viewport in inline axis */
        SVI,
        /** Minimum of SVW and SVH */
        SVMIN,
        /** Maximum of SVW and SVH */
        SVMAX,

        /** Large viewport width - 1% of large viewport's width */
        LVW,
        /** Large viewport height - 1% of large viewport's height */
        LVH,
        /** Large viewport block - 1% of large viewport in block axis */
        LVB,
        /** Large viewport inline - 1% of large viewport in inline axis */
        LVI,
        /** Minimum of LVW and LVH */
        LVMIN,
        /** Maximum of LVW and LVH */
        LVMAX,

        /** Dynamic viewport width - 1% of dynamic viewport's width */
        DVW,
        /** Dynamic viewport height - 1% of dynamic viewport's height */
        DVH,
        /** Dynamic viewport block - 1% of dynamic viewport in block axis */
        DVB,
        /** Dynamic viewport inline - 1% of dynamic viewport in inline axis */
        DVI,
        /** Minimum of DVW and DVH */
        DVMIN,
        /** Maximum of DVW and DVH */
        DVMAX,

        /** Viewport inline - 1% of viewport in inline axis */
        VI,
        /** Viewport block - 1% of viewport in block axis */
        VB,

        // === Container Query Units (CSS Containment Level 3) ===

        /** 1% of query container's width */
        CQW,
        /** 1% of query container's height */
        CQH,
        /** 1% of query container's inline size */
        CQI,
        /** 1% of query container's block size */
        CQB,
        /** Minimum of CQW and CQH */
        CQMIN,
        /** Maximum of CQW and CQH */
        CQMAX,

        // === Font-relative Units ===

        /** Relative to line-height of the element */
        LH,
        /** Relative to line-height of the root element */
        RLH,
        /** Relative to width of ideographic character (CJK) */
        IC,
        /** Relative to cap-height of the font (height of capital letters) */
        CAP,

        /** Quarter-millimeter - absolute unit (1Q = 0.25mm) */
        Q
    }
}

/**
 * Normalized sRGB color with components in 0.0-1.0 range.
 *
 * This is the universal color format that all platforms can use:
 * - CSS: rgb(r*255, g*255, b*255, a)
 * - Compose: Color(r, g, b, a)
 * - SwiftUI: Color(.sRGB, red: r, green: g, blue: b, opacity: a)
 */
@Serializable(with = SRGBSerializer::class)
data class SRGB(
    val r: Double,  // 0.0-1.0
    val g: Double,  // 0.0-1.0
    val b: Double,  // 0.0-1.0
    val a: Double = 1.0  // 0.0-1.0
) {
    /** Clamp values to valid range */
    fun clamped() = SRGB(
        r.coerceIn(0.0, 1.0),
        g.coerceIn(0.0, 1.0),
        b.coerceIn(0.0, 1.0),
        a.coerceIn(0.0, 1.0)
    )

    /** Check if color is within sRGB gamut */
    fun isInGamut() = r in 0.0..1.0 && g in 0.0..1.0 && b in 0.0..1.0
}

/**
 * Represents a color value in various formats.
 *
 * CSS supports multiple color formats: hex, rgb/rgba, hsl/hsla, named colors, and special keywords.
 *
 * ## Dual Storage
 * - `srgb`: Normalized sRGB (0-1 floats) for cross-platform generators (Compose, SwiftUI)
 * - `representation`: Original format preserved for CSS regeneration
 *
 * Dynamic colors (color-mix, light-dark, currentColor, var()) have srgb=null since
 * they cannot be resolved at parse time.
 *
 * ## Examples
 * - `IRColor(ColorRepresentation.Hex("#FF0000"))` represents red in hex
 * - `IRColor(ColorRepresentation.RGB(255, 0, 0, 1.0))` represents red in RGB
 * - `IRColor(ColorRepresentation.HSL(0.0, 100.0, 50.0, 1.0))` represents red in HSL
 * - `IRColor(ColorRepresentation.Named("red"))` represents the named color "red"
 * - `IRColor(ColorRepresentation.CurrentColor())` represents the special "currentColor" keyword
 * - `IRColor(ColorRepresentation.Transparent())` represents transparent
 *
 * @property representation The specific color format and value (original)
 * @property srgb Normalized sRGB value for cross-platform use (null if dynamic)
 */
@Serializable(with = IRColorSerializer::class)
data class IRColor(
    val representation: ColorRepresentation,
    val srgb: SRGB? = null
) {
    @Serializable(with = ColorRepresentationSerializer::class)
    sealed interface ColorRepresentation {
        /**
         * Hexadecimal color notation.
         * @property value Hex string including '#' (e.g., "#FF0000" or "#F00")
         */
        @Serializable
        data class Hex(val value: String) : ColorRepresentation

        /**
         * RGB/RGBA color notation.
         * @property r Red channel (0-255)
         * @property g Green channel (0-255)
         * @property b Blue channel (0-255)
         * @property a Alpha/opacity channel (0.0-1.0)
         */
        @Serializable
        data class RGB(val r: Int, val g: Int, val b: Int, val a: Double = 1.0) : ColorRepresentation

        /**
         * HSL/HSLA color notation.
         * @property h Hue in degrees (0-360)
         * @property s Saturation percentage (0-100)
         * @property l Lightness percentage (0-100)
         * @property a Alpha/opacity channel (0.0-1.0)
         */
        @Serializable
        data class HSL(val h: Double, val s: Double, val l: Double, val a: Double = 1.0) : ColorRepresentation

        /**
         * Named color (e.g., "red", "blue", "transparent").
         * @property name CSS color name
         */
        @Serializable
        data class Named(val name: String) : ColorRepresentation

        /**
         * Special keyword "currentColor" - inherits the text color.
         */
        @Serializable
        data class CurrentColor(val unit: Unit = Unit) : ColorRepresentation

        /**
         * Special keyword "transparent" - fully transparent color.
         */
        @Serializable
        data class Transparent(val unit: Unit = Unit) : ColorRepresentation

        /**
         * Lab color notation (CIE Lab color space).
         * @property l Lightness (0-100 or percentage)
         * @property a Green-red axis (-125 to 125)
         * @property b Blue-yellow axis (-125 to 125)
         * @property alpha Alpha/opacity channel (0.0-1.0)
         */
        @Serializable
        data class Lab(val l: Double, val a: Double, val b: Double, val alpha: Double = 1.0) : ColorRepresentation

        /**
         * LCH color notation (CIE LCH color space).
         * @property l Lightness (0-100 or percentage)
         * @property c Chroma (0-150+)
         * @property h Hue in degrees (0-360)
         * @property alpha Alpha/opacity channel (0.0-1.0)
         */
        @Serializable
        data class LCH(val l: Double, val c: Double, val h: Double, val alpha: Double = 1.0) : ColorRepresentation

        /**
         * OKLab color notation (Oklab perceptually uniform color space).
         */
        @Serializable
        data class OKLab(val l: Double, val a: Double, val b: Double, val alpha: Double = 1.0) : ColorRepresentation

        /**
         * OKLCH color notation (Oklch perceptually uniform color space).
         */
        @Serializable
        data class OKLCH(val l: Double, val c: Double, val h: Double, val alpha: Double = 1.0) : ColorRepresentation

        /**
         * HWB color notation (Hue-Whiteness-Blackness).
         */
        @Serializable
        data class HWB(val h: Double, val w: Double, val b: Double, val alpha: Double = 1.0) : ColorRepresentation

        /**
         * CSS color() function for specific color spaces.
         * @property colorSpace The color space (srgb, display-p3, a98-rgb, etc.)
         * @property values The color component values
         * @property alpha Alpha/opacity channel (0.0-1.0)
         */
        @Serializable
        data class ColorFunction(val colorSpace: String, val values: List<Double>, val alpha: Double = 1.0) : ColorRepresentation

        /**
         * CSS color-mix() function for mixing two colors.
         * @property colorSpace The interpolation color space (srgb, oklch, etc.)
         * @property hueMethod Optional hue interpolation method (shorter, longer, increasing, decreasing)
         * @property color1 First color (as raw string, may be nested)
         * @property percent1 Optional percentage for first color
         * @property color2 Second color (as raw string, may be nested)
         * @property percent2 Optional percentage for second color
         */
        @Serializable
        data class ColorMix(
            val colorSpace: String,
            val hueMethod: String? = null,
            val color1: String,
            val percent1: Double? = null,
            val color2: String,
            val percent2: Double? = null
        ) : ColorRepresentation

        /**
         * CSS light-dark() function for automatic light/dark mode.
         * @property lightColor Color value for light mode
         * @property darkColor Color value for dark mode
         */
        @Serializable
        data class LightDark(
            val lightColor: String,
            val darkColor: String
        ) : ColorRepresentation

        /**
         * CSS relative color syntax (e.g., rgb(from red calc(r - 50) g b)).
         * @property function The color function (rgb, hsl, oklch, etc.)
         * @property baseColor The base color to derive from
         * @property components The component expressions (may include calc())
         */
        @Serializable
        data class RelativeColor(
            val function: String,
            val baseColor: String,
            val components: List<String>
        ) : ColorRepresentation
    }
}

/**
 * Represents a CSS keyword value.
 *
 * Generic keyword type for properties with simple string values.
 * Prefer using enums in specific property classes for type safety.
 *
 * @property value The keyword string (e.g., "auto", "inherit", "initial")
 */
@Serializable(with = IRKeywordSerializer::class)
data class IRKeyword(
    val value: String
)

/**
 * Represents a URL reference.
 *
 * Used for properties like background-image, border-image-source, etc.
 *
 * ## Examples
 * - `IRUrl("https://example.com/image.png")` for external resources
 * - `IRUrl("data:image/png;base64,...", isDataUrl = true)` for data URLs
 *
 * @property url The URL string
 * @property isDataUrl True if this is a data URL (base64 encoded data)
 */
@Serializable(with = IRUrlSerializer::class)
data class IRUrl(
    val url: String,
    val isDataUrl: Boolean = false
)

/**
 * Represents a percentage value.
 *
 * Percentages are relative to a reference value (usually parent element's dimension).
 *
 * ## Examples
 * - `IRPercentage(50.0)` represents "50%"
 * - `IRPercentage(100.0)` represents "100%"
 *
 * @property value The percentage value (0-100, though can exceed 100)
 */
@Serializable(with = IRPercentageSerializer::class)
data class IRPercentage(
    val value: Double
)

/**
 * Union of `<length>` and `<percentage>` CSS values, matching the
 * `<length-percentage>` production used throughout the CSS specs
 * (e.g. CSS Shapes 2 §3.1.4 polygon points, CSS Values 4 §5.4).
 *
 * ## Why a sealed union (and not just `IRLength` with PERCENT)?
 * `IRLength` already has a `PERCENT` unit — but the old clip-path
 * polygon serializer emitted points as raw doubles (`{x:50.0,y:50.0}`)
 * interpreted as percentages, and a flurry of platform extractors
 * (web/iOS/Android) read them in that flat form. To preserve byte-for-
 * byte backward compat with that wire shape while *also* allowing
 * `px`/`em`/etc length coordinates, we tag each value with its kind
 * and serialise asymmetrically:
 *
 *   - `Percentage(50.0)` → JSON `50.0`   (raw number, matches legacy)
 *   - `Length(IRLength.fromPx(100.0))` → JSON `{"px":100.0}` (IRLength shape)
 *
 * Deserialise by inspecting the JSON node kind: a primitive number is a
 * percentage (legacy compat), an object is an IRLength.
 *
 * ## Used by
 *  - `ClipPathProperty.Point` (CSS Shapes 2 polygon coordinates)
 */
@Serializable(with = IRLengthPercentageSerializer::class)
sealed interface IRLengthPercentage {
    /** A concrete CSS `<length>` such as `100px` / `1em` / `2rem`. */
    data class Length(val length: IRLength) : IRLengthPercentage
    /** A CSS `<percentage>` such as `50%`. Wire form is a raw number. */
    data class Percentage(val percentage: IRPercentage) : IRLengthPercentage
}

/**
 * Represents a unitless number.
 *
 * Used for properties like opacity, flex-grow, flex-shrink, z-index, line-height, etc.
 *
 * ## Examples
 * - `IRNumber(0.5)` for opacity: 0.5
 * - `IRNumber(1.0)` for flex-grow: 1
 * - `IRNumber(1.5)` for line-height: 1.5
 *
 * @property value The numeric value
 */
@Serializable(with = IRNumberSerializer::class)
data class IRNumber(
    val value: Double
)

/**
 * Represents an angle value with unit.
 *
 * Used for transforms (rotate), gradients, etc.
 *
 * ## Examples
 * - `IRAngle(45.0, AngleUnit.DEG)` represents "45deg"
 * - `IRAngle(3.14159, AngleUnit.RAD)` represents "3.14159rad"
 * - `IRAngle(0.5, AngleUnit.TURN)` represents "0.5turn" (180 degrees)
 *
 * ## Supported Units
 * - **DEG**: Degrees (0-360)
 * - **RAD**: Radians (0-2π)
 * - **GRAD**: Gradians (0-400)
 * - **TURN**: Turns/rotations (0-1 = full circle)
 *
 * @property value The numeric angle value
 * @property unit The angle unit
 */
/**
 * Represents an angle value, normalized to degrees for cross-platform use.
 *
 * ## Dual Storage
 * - `degrees`: Normalized value in degrees for Compose/SwiftUI
 * - `originalValue` + `originalUnit`: Original format for CSS regeneration
 *
 * ## Examples
 * - `IRAngle.fromDegrees(45.0)` → degrees=45, original=45deg
 * - `IRAngle.fromRadians(Math.PI)` → degrees=180, original=πrad
 * - `IRAngle.fromTurns(0.5)` → degrees=180, original=0.5turn
 *
 * @property degrees Normalized angle in degrees (always)
 * @property originalValue Original numeric value for CSS output
 * @property originalUnit Original unit for CSS output
 */
@Serializable(with = IRAngleSerializer::class)
data class IRAngle(
    val degrees: Double,
    val originalValue: Double,
    val originalUnit: AngleUnit
) {
    enum class AngleUnit {
        /** Degrees (360deg = full circle) */
        DEG,

        /** Radians (2π rad = full circle) */
        RAD,

        /** Gradians (400grad = full circle) */
        GRAD,

        /** Turns (1turn = full circle) */
        TURN
    }

    companion object {
        private const val PI = 3.141592653589793

        /** Create from degrees (no conversion needed) */
        fun fromDegrees(value: Double) = IRAngle(
            degrees = value,
            originalValue = value,
            originalUnit = AngleUnit.DEG
        )

        /** Create from radians → degrees */
        fun fromRadians(value: Double) = IRAngle(
            degrees = value * (180.0 / PI),
            originalValue = value,
            originalUnit = AngleUnit.RAD
        )

        /** Create from gradians → degrees */
        fun fromGradians(value: Double) = IRAngle(
            degrees = value * 0.9,
            originalValue = value,
            originalUnit = AngleUnit.GRAD
        )

        /** Create from turns → degrees */
        fun fromTurns(value: Double) = IRAngle(
            degrees = value * 360.0,
            originalValue = value,
            originalUnit = AngleUnit.TURN
        )

        /** Create from value and unit (auto-normalizes) */
        fun from(value: Double, unit: AngleUnit) = when (unit) {
            AngleUnit.DEG -> fromDegrees(value)
            AngleUnit.RAD -> fromRadians(value)
            AngleUnit.GRAD -> fromGradians(value)
            AngleUnit.TURN -> fromTurns(value)
        }
    }
}

/**
 * Represents a time duration with unit.
 *
 * Used for animations, transitions, and delays.
 *
 * ## Examples
 * - `IRTime.fromSeconds(0.3)` represents "0.3s" → 300 milliseconds
 * - `IRTime.fromMilliseconds(300.0)` represents "300ms"
 *
 * ## Supported Units
 * - **S**: Seconds
 * - **MS**: Milliseconds
 *
 * ## Dual Storage
 * - `milliseconds`: Normalized value in ms (for generators)
 * - `originalValue` + `originalUnit`: Original format (for CSS regeneration)
 *
 * @property milliseconds The time value normalized to milliseconds
 * @property originalValue The original numeric value
 * @property originalUnit The original time unit
 */
@Serializable(with = IRTimeSerializer::class)
data class IRTime(
    val milliseconds: Double,
    val originalValue: Double,
    val originalUnit: TimeUnit
) {
    enum class TimeUnit {
        /** Seconds */
        S,

        /** Milliseconds */
        MS
    }

    companion object {
        /** Create from seconds - normalizes to milliseconds */
        fun fromSeconds(value: Double) = IRTime(
            milliseconds = value * 1000.0,
            originalValue = value,
            originalUnit = TimeUnit.S
        )

        /** Create from milliseconds - already normalized */
        fun fromMilliseconds(value: Double) = IRTime(
            milliseconds = value,
            originalValue = value,
            originalUnit = TimeUnit.MS
        )

        /** Create from value and unit */
        fun from(value: Double, unit: TimeUnit) = when (unit) {
            TimeUnit.S -> fromSeconds(value)
            TimeUnit.MS -> fromMilliseconds(value)
        }
    }
}

/**
 * Value type for padding properties.
 * Padding accepts length values or percentage.
 */
@Serializable(with = PaddingValueSerializer::class)
sealed interface PaddingValue {
    @Serializable
    data class Length(val value: IRLength) : PaddingValue

    @Serializable
    data class Percentage(val value: IRPercentage) : PaddingValue

    /** CSS expression: calc(), clamp(), min(), max(), var() */
    @Serializable
    data class Expression(val raw: String) : PaddingValue

    /** Global CSS keywords: inherit, initial, unset, revert, revert-layer */
    @Serializable
    data class Keyword(val keyword: String) : PaddingValue
}

/**
 * Value type for margin properties.
 * Margin accepts length, percentage, or "auto" keyword.
 */
@Serializable(with = MarginValueSerializer::class)
sealed interface MarginValue {
    @Serializable
    data class Length(val value: IRLength) : MarginValue

    @Serializable
    data class Percentage(val value: IRPercentage) : MarginValue

    @Serializable
    data class Auto(val unit: Unit = Unit) : MarginValue

    /** CSS expression: calc(), clamp(), min(), max(), var() */
    @Serializable
    data class Expression(val raw: String) : MarginValue
}

/**
 * Value type for scroll-padding properties.
 * Accepts length, percentage, "auto", global keywords, or CSS functions.
 */
@Serializable(with = ScrollPaddingValueSerializer::class)
sealed interface ScrollPaddingValue {
    @Serializable
    data class Length(val value: IRLength) : ScrollPaddingValue

    @Serializable
    data class Percentage(val value: IRPercentage) : ScrollPaddingValue

    @Serializable
    data class Auto(val unit: Unit = Unit) : ScrollPaddingValue

    @Serializable
    data class Keyword(val value: String) : ScrollPaddingValue

    @Serializable
    data class Raw(val value: String) : ScrollPaddingValue
}

/**
 * Value type for border-radius properties.
 * Accepts length or percentage values.
 */
@Serializable(with = BorderRadiusValueSerializer::class)
sealed interface BorderRadiusValue {
    @Serializable
    data class Length(val value: IRLength) : BorderRadiusValue

    @Serializable
    data class Percentage(val value: IRPercentage) : BorderRadiusValue

    @Serializable
    data class Keyword(val keyword: String) : BorderRadiusValue

    @Serializable
    data class Raw(val value: String) : BorderRadiusValue
}

/**
 * Value type for overflow properties.
 */
@Serializable
enum class OverflowValue {
    VISIBLE,
    HIDDEN,
    CLIP,
    SCROLL,
    AUTO
}

/**
 * Value type for animation-range properties.
 */
@Serializable(with = AnimationRangeValueSerializer::class)
sealed interface AnimationRangeValue {
    @Serializable
    data class Length(val value: IRLength) : AnimationRangeValue

    @Serializable
    data class Percentage(val value: IRPercentage) : AnimationRangeValue

    @Serializable
    data class Keyword(val value: String) : AnimationRangeValue  // normal, cover, contain, etc.

    @Serializable
    data class NamedRange(val name: TimelineRangeName, val offset: IRPercentage) : AnimationRangeValue

    @Serializable
    data class Raw(val value: String) : AnimationRangeValue
}

@Serializable
enum class TimelineRangeName {
    @SerialName("cover") COVER,
    @SerialName("contain") CONTAIN,
    @SerialName("entry") ENTRY,
    @SerialName("exit") EXIT,
    @SerialName("entry-crossing") ENTRY_CROSSING,
    @SerialName("exit-crossing") EXIT_CROSSING
}

/**
 * Value type for mask-border-slice.
 * Supports 1-4 values (number or percentage) plus optional fill keyword.
 */
@Serializable(with = MaskBorderSliceValueSerializer::class)
sealed interface MaskBorderSliceValue {
    @Serializable
    data class Number(val value: IRNumber) : MaskBorderSliceValue

    @Serializable
    data class Percentage(val value: IRPercentage) : MaskBorderSliceValue

    @Serializable
    data class Values(
        val top: SliceComponent,
        val right: SliceComponent,
        val bottom: SliceComponent,
        val left: SliceComponent,
        val fill: Boolean = false
    ) : MaskBorderSliceValue

    @Serializable
    data class Keyword(val keyword: String) : MaskBorderSliceValue

    @Serializable
    data class Raw(val value: String) : MaskBorderSliceValue
}

/**
 * Component value for mask-border-slice (can be number or percentage).
 */
@Serializable
sealed interface SliceComponent {
    @Serializable data class Num(val value: Double) : SliceComponent
    @Serializable data class Pct(val value: Double) : SliceComponent
}

/**
 * Value type for mask-border-repeat.
 */
@Serializable
sealed interface MaskBorderRepeatValue {
    @Serializable @SerialName("stretch") data object Stretch : MaskBorderRepeatValue
    @Serializable @SerialName("repeat") data object Repeat : MaskBorderRepeatValue
    @Serializable @SerialName("round") data object Round : MaskBorderRepeatValue
    @Serializable @SerialName("space") data object Space : MaskBorderRepeatValue
    @Serializable @SerialName("two-value") data class TwoValue(val horizontal: String, val vertical: String) : MaskBorderRepeatValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : MaskBorderRepeatValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : MaskBorderRepeatValue
}

/**
 * Value type for mask-border-mode.
 */
@Serializable
enum class MaskBorderModeValue {
    LUMINANCE,
    ALPHA
}

/**
 * Value type for background position.
 */
@Serializable(with = PositionValueSerializer::class)
sealed interface PositionValue {
    @Serializable
    data class Length(val value: IRLength) : PositionValue

    @Serializable
    data class Percentage(val value: IRPercentage) : PositionValue

    @Serializable
    data class Keyword(val value: String) : PositionValue  // left, center, right, top, bottom
}

// ============================================================================
// SERIALIZERS - Moved from serializers/ folder for better organization
// ============================================================================

/**
 * Serializer for IRLength with dual storage:
 * - Always includes: "px" (normalized pixels) if available, or just original
 * - Conditionally includes: "original" (only when unit != PX)
 *
 * Examples:
 * - 10px input: {"px": 10.0}
 * - 12pt input: {"px": 16.0, "original": {"v": 12.0, "u": "PT"}}
 * - 2em input: {"original": {"v": 2.0, "u": "EM"}} (no px - relative unit)
 */
object IRLengthSerializer : KSerializer<IRLength> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IRLength")
    override fun serialize(encoder: Encoder, value: IRLength) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(buildJsonObject {
            // Include normalized pixels if available
            value.pixels?.let { put("px", it) }
            // Include original if it differs from pixels representation
            if (value.originalUnit != IRLength.LengthUnit.PX || value.pixels == null) {
                put("original", buildJsonObject {
                    put("v", value.originalValue)
                    put("u", value.originalUnit.name)
                })
            }
        })
    }
    override fun deserialize(decoder: Decoder): IRLength {
        require(decoder is JsonDecoder)
        val obj = decoder.decodeJsonElement().jsonObject
        val px = obj["px"]?.jsonPrimitive?.doubleOrNull
        val original = obj["original"]?.jsonObject
        return if (original != null) {
            val v = original["v"]?.jsonPrimitive?.double ?: px ?: 0.0
            val u = original["u"]?.jsonPrimitive?.content?.let { IRLength.LengthUnit.valueOf(it) } ?: IRLength.LengthUnit.PX
            IRLength(px, v, u)
        } else {
            // No original means it was in pixels
            IRLength(px ?: 0.0, px ?: 0.0, IRLength.LengthUnit.PX)
        }
    }
}

/** Serializer for IRNumber - raw number without wrapper */
object IRNumberSerializer : KSerializer<IRNumber> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IRNumber")
    override fun serialize(encoder: Encoder, value: IRNumber) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(JsonPrimitive(value.value))
    }
    override fun deserialize(decoder: Decoder): IRNumber {
        require(decoder is JsonDecoder)
        return IRNumber(decoder.decodeJsonElement().jsonPrimitive.double)
    }
}

/** Serializer for IRPercentage - raw number without wrapper */
object IRPercentageSerializer : KSerializer<IRPercentage> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IRPercentage")
    override fun serialize(encoder: Encoder, value: IRPercentage) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(JsonPrimitive(value.value))
    }
    override fun deserialize(decoder: Decoder): IRPercentage {
        require(decoder is JsonDecoder)
        return IRPercentage(decoder.decodeJsonElement().jsonPrimitive.double)
    }
}

/**
 * Serializer for [IRLengthPercentage]. Wire format intentionally asymmetric
 * to preserve backward compatibility with the pre-existing clip-path
 * polygon emission (raw number == percentage):
 *
 *  - `Percentage(50.0)` → JSON primitive `50.0`
 *  - `Length(...)` → JSON object via [IRLengthSerializer] (e.g. `{"px":100.0}`)
 *
 * Deserialise dispatches on the JSON node kind:
 *  - primitive number → percentage branch (legacy + new shapes both work)
 *  - object → length branch (new px/em/rem coordinates)
 */
object IRLengthPercentageSerializer : KSerializer<IRLengthPercentage> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IRLengthPercentage")
    override fun serialize(encoder: Encoder, value: IRLengthPercentage) {
        require(encoder is JsonEncoder)
        when (value) {
            // Percentage → raw number primitive; matches the legacy
            // `{x: 50.0, y: 50.0}` polygon-point shape that every
            // platform extractor was already coded against.
            is IRLengthPercentage.Percentage ->
                encoder.encodeJsonElement(JsonPrimitive(value.percentage.value))
            // Length → delegate to IRLengthSerializer, which produces a
            // JSON object like `{"px":100.0}` (absolute) or
            // `{"original":{"v":1.5,"u":"EM"}}` (relative).
            is IRLengthPercentage.Length ->
                encoder.encodeJsonElement(
                    encoder.json.encodeToJsonElement(IRLengthSerializer, value.length)
                )
        }
    }
    override fun deserialize(decoder: Decoder): IRLengthPercentage {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            // Object → IRLength branch (new px/em/rem coordinates).
            element is JsonObject ->
                IRLengthPercentage.Length(decoder.json.decodeFromJsonElement(IRLengthSerializer, element))
            // Primitive number → legacy percentage form. We accept
            // `decoder.decodeJsonElement().jsonPrimitive.double` here
            // rather than `doubleOrNull` to fail loudly on non-numeric
            // primitives — that signals an upstream encoding bug.
            element is JsonPrimitive ->
                IRLengthPercentage.Percentage(IRPercentage(element.double))
            // Anything else (array, null) — defensive default: 0%.
            else -> IRLengthPercentage.Percentage(IRPercentage(0.0))
        }
    }
}

/** Serializer for IRAngle - includes normalized degrees and original value/unit */
object IRAngleSerializer : KSerializer<IRAngle> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IRAngle")
    override fun serialize(encoder: Encoder, value: IRAngle) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(buildJsonObject {
            // Normalized degrees for generators
            put("deg", value.degrees)
            // Original for CSS output (only if different from degrees)
            if (value.originalUnit != IRAngle.AngleUnit.DEG) {
                put("original", buildJsonObject {
                    put("v", value.originalValue)
                    put("u", value.originalUnit.name)
                })
            }
        })
    }
    override fun deserialize(decoder: Decoder): IRAngle {
        require(decoder is JsonDecoder)
        val obj = decoder.decodeJsonElement().jsonObject
        val degrees = obj["deg"]?.jsonPrimitive?.double ?: 0.0
        val original = obj["original"]?.jsonObject
        return if (original != null) {
            val originalValue = original["v"]?.jsonPrimitive?.double ?: degrees
            val originalUnit = original["u"]?.jsonPrimitive?.content?.let {
                IRAngle.AngleUnit.valueOf(it)
            } ?: IRAngle.AngleUnit.DEG
            IRAngle(degrees, originalValue, originalUnit)
        } else {
            IRAngle(degrees, degrees, IRAngle.AngleUnit.DEG)
        }
    }
}

/**
 * Serializer for IRTime with dual storage:
 * - Always includes: "ms" (normalized milliseconds)
 * - Conditionally includes: "original" (only when originalUnit != MS)
 *
 * Examples:
 * - 300ms input: {"ms": 300.0}
 * - 0.3s input: {"ms": 300.0, "original": {"v": 0.3, "u": "S"}}
 */
object IRTimeSerializer : KSerializer<IRTime> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IRTime")
    override fun serialize(encoder: Encoder, value: IRTime) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(buildJsonObject {
            put("ms", value.milliseconds)
            // Only include original if it differs from milliseconds representation
            if (value.originalUnit != IRTime.TimeUnit.MS) {
                put("original", buildJsonObject {
                    put("v", value.originalValue)
                    put("u", value.originalUnit.name)
                })
            }
        })
    }
    override fun deserialize(decoder: Decoder): IRTime {
        require(decoder is JsonDecoder)
        val obj = decoder.decodeJsonElement().jsonObject
        val ms = obj["ms"]?.jsonPrimitive?.double ?: 0.0
        val original = obj["original"]?.jsonObject
        return if (original != null) {
            val v = original["v"]?.jsonPrimitive?.double ?: ms
            val u = original["u"]?.jsonPrimitive?.content?.let { IRTime.TimeUnit.valueOf(it) } ?: IRTime.TimeUnit.MS
            IRTime(ms, v, u)
        } else {
            // No original means it was already in milliseconds
            IRTime(ms, ms, IRTime.TimeUnit.MS)
        }
    }
}

/** Serializer for IRUrl - string for regular, object for data URLs */
object IRUrlSerializer : KSerializer<IRUrl> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IRUrl")
    override fun serialize(encoder: Encoder, value: IRUrl) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(if (value.isDataUrl) {
            buildJsonObject { put("url", value.url); put("data", true) }
        } else JsonPrimitive(value.url))
    }
    override fun deserialize(decoder: Decoder): IRUrl {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive -> IRUrl(element.content, false)
            element is JsonObject -> IRUrl(element["url"]?.jsonPrimitive?.content ?: "", element["data"]?.jsonPrimitive?.boolean ?: false)
            else -> IRUrl("", false)
        }
    }
}

/** Serializer for IRKeyword - raw string without wrapper */
object IRKeywordSerializer : KSerializer<IRKeyword> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IRKeyword")
    override fun serialize(encoder: Encoder, value: IRKeyword) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(JsonPrimitive(value.value))
    }
    override fun deserialize(decoder: Decoder): IRKeyword {
        require(decoder is JsonDecoder)
        return IRKeyword(decoder.decodeJsonElement().jsonPrimitive.content)
    }
}

/** Serializer for SRGB - compact format: {"r":0.5,"g":0.3,"b":0.1} or {"r":0.5,"g":0.3,"b":0.1,"a":0.8} */
object SRGBSerializer : KSerializer<SRGB> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SRGB")
    override fun serialize(encoder: Encoder, value: SRGB) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(buildJsonObject {
            put("r", value.r)
            put("g", value.g)
            put("b", value.b)
            if (value.a != 1.0) put("a", value.a)
        })
    }
    override fun deserialize(decoder: Decoder): SRGB {
        require(decoder is JsonDecoder)
        val obj = decoder.decodeJsonElement().jsonObject
        return SRGB(
            r = obj["r"]?.jsonPrimitive?.double ?: 0.0,
            g = obj["g"]?.jsonPrimitive?.double ?: 0.0,
            b = obj["b"]?.jsonPrimitive?.double ?: 0.0,
            a = obj["a"]?.jsonPrimitive?.double ?: 1.0
        )
    }
}

/** Serializer for IRColor - includes both srgb and original representation */
object IRColorSerializer : KSerializer<IRColor> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IRColor")
    override fun serialize(encoder: Encoder, value: IRColor) {
        require(encoder is JsonEncoder)
        val json = encoder.json

        // Build output with srgb (if available) and original representation
        val representationJson = json.encodeToJsonElement(IRColor.ColorRepresentation.serializer(), value.representation)

        encoder.encodeJsonElement(buildJsonObject {
            // Add normalized sRGB first (main field for generators)
            value.srgb?.let { srgb ->
                put("srgb", json.encodeToJsonElement(SRGB.serializer(), srgb))
            }
            // Add original representation for CSS regeneration
            put("original", representationJson)
        })
    }
    override fun deserialize(decoder: Decoder): IRColor {
        require(decoder is JsonDecoder)
        val json = decoder.json
        val obj = decoder.decodeJsonElement().jsonObject

        val srgb = obj["srgb"]?.let { json.decodeFromJsonElement(SRGB.serializer(), it) }
        val representation = json.decodeFromJsonElement(IRColor.ColorRepresentation.serializer(), obj["original"]!!)

        return IRColor(representation, srgb)
    }
}

/** Serializer for ColorRepresentation - detects variant by structure */
object ColorRepresentationSerializer : KSerializer<IRColor.ColorRepresentation> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ColorRepresentation")
    override fun serialize(encoder: Encoder, value: IRColor.ColorRepresentation) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is IRColor.ColorRepresentation.Hex -> JsonPrimitive(value.value)
            is IRColor.ColorRepresentation.RGB -> buildJsonObject {
                put("r", value.r); put("g", value.g); put("b", value.b)
                if (value.a != 1.0) put("a", value.a)
            }
            is IRColor.ColorRepresentation.HSL -> buildJsonObject {
                put("h", value.h); put("s", value.s); put("l", value.l)
                if (value.a != 1.0) put("a", value.a)
            }
            is IRColor.ColorRepresentation.Lab -> buildJsonObject {
                put("type", "lab"); put("l", value.l); put("a", value.a); put("b", value.b)
                if (value.alpha != 1.0) put("alpha", value.alpha)
            }
            is IRColor.ColorRepresentation.LCH -> buildJsonObject {
                put("type", "lch"); put("l", value.l); put("c", value.c); put("h", value.h)
                if (value.alpha != 1.0) put("alpha", value.alpha)
            }
            is IRColor.ColorRepresentation.OKLab -> buildJsonObject {
                put("type", "oklab"); put("l", value.l); put("a", value.a); put("b", value.b)
                if (value.alpha != 1.0) put("alpha", value.alpha)
            }
            is IRColor.ColorRepresentation.OKLCH -> buildJsonObject {
                put("type", "oklch"); put("l", value.l); put("c", value.c); put("h", value.h)
                if (value.alpha != 1.0) put("alpha", value.alpha)
            }
            is IRColor.ColorRepresentation.HWB -> buildJsonObject {
                put("type", "hwb"); put("h", value.h); put("w", value.w); put("b", value.b)
                if (value.alpha != 1.0) put("alpha", value.alpha)
            }
            is IRColor.ColorRepresentation.ColorFunction -> buildJsonObject {
                put("type", "color"); put("colorSpace", value.colorSpace)
                put("values", JsonArray(value.values.map { JsonPrimitive(it) }))
                if (value.alpha != 1.0) put("alpha", value.alpha)
            }
            is IRColor.ColorRepresentation.ColorMix -> buildJsonObject {
                put("type", "color-mix"); put("colorSpace", value.colorSpace)
                value.hueMethod?.let { put("hueMethod", it) }
                put("color1", value.color1)
                value.percent1?.let { put("percent1", it) }
                put("color2", value.color2)
                value.percent2?.let { put("percent2", it) }
            }
            is IRColor.ColorRepresentation.LightDark -> buildJsonObject {
                put("type", "light-dark")
                put("lightColor", value.lightColor)
                put("darkColor", value.darkColor)
            }
            is IRColor.ColorRepresentation.RelativeColor -> buildJsonObject {
                put("type", "relative")
                put("function", value.function)
                put("baseColor", value.baseColor)
                put("components", JsonArray(value.components.map { JsonPrimitive(it) }))
            }
            is IRColor.ColorRepresentation.Named -> JsonPrimitive(value.name)
            is IRColor.ColorRepresentation.CurrentColor -> JsonPrimitive("currentColor")
            is IRColor.ColorRepresentation.Transparent -> JsonPrimitive("transparent")
        })
    }
    override fun deserialize(decoder: Decoder): IRColor.ColorRepresentation {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonObject && element.containsKey("type") -> {
                val type = element["type"]!!.jsonPrimitive.content
                val alpha = element["alpha"]?.jsonPrimitive?.double ?: 1.0
                when (type) {
                    "lab" -> IRColor.ColorRepresentation.Lab(
                        element["l"]!!.jsonPrimitive.double, element["a"]!!.jsonPrimitive.double,
                        element["b"]!!.jsonPrimitive.double, alpha
                    )
                    "lch" -> IRColor.ColorRepresentation.LCH(
                        element["l"]!!.jsonPrimitive.double, element["c"]!!.jsonPrimitive.double,
                        element["h"]!!.jsonPrimitive.double, alpha
                    )
                    "oklab" -> IRColor.ColorRepresentation.OKLab(
                        element["l"]!!.jsonPrimitive.double, element["a"]!!.jsonPrimitive.double,
                        element["b"]!!.jsonPrimitive.double, alpha
                    )
                    "oklch" -> IRColor.ColorRepresentation.OKLCH(
                        element["l"]!!.jsonPrimitive.double, element["c"]!!.jsonPrimitive.double,
                        element["h"]!!.jsonPrimitive.double, alpha
                    )
                    "hwb" -> IRColor.ColorRepresentation.HWB(
                        element["h"]!!.jsonPrimitive.double, element["w"]!!.jsonPrimitive.double,
                        element["b"]!!.jsonPrimitive.double, alpha
                    )
                    "color" -> IRColor.ColorRepresentation.ColorFunction(
                        element["colorSpace"]!!.jsonPrimitive.content,
                        element["values"]!!.jsonArray.map { it.jsonPrimitive.double },
                        alpha
                    )
                    "color-mix" -> IRColor.ColorRepresentation.ColorMix(
                        colorSpace = element["colorSpace"]!!.jsonPrimitive.content,
                        hueMethod = element["hueMethod"]?.jsonPrimitive?.content,
                        color1 = element["color1"]!!.jsonPrimitive.content,
                        percent1 = element["percent1"]?.jsonPrimitive?.double,
                        color2 = element["color2"]!!.jsonPrimitive.content,
                        percent2 = element["percent2"]?.jsonPrimitive?.double
                    )
                    "light-dark" -> IRColor.ColorRepresentation.LightDark(
                        lightColor = element["lightColor"]!!.jsonPrimitive.content,
                        darkColor = element["darkColor"]!!.jsonPrimitive.content
                    )
                    "relative" -> IRColor.ColorRepresentation.RelativeColor(
                        function = element["function"]!!.jsonPrimitive.content,
                        baseColor = element["baseColor"]!!.jsonPrimitive.content,
                        components = element["components"]!!.jsonArray.map { it.jsonPrimitive.content }
                    )
                    else -> throw IllegalArgumentException("Unknown color type: $type")
                }
            }
            element is JsonObject && element.containsKey("r") -> IRColor.ColorRepresentation.RGB(
                element["r"]!!.jsonPrimitive.int, element["g"]!!.jsonPrimitive.int, element["b"]!!.jsonPrimitive.int,
                element["a"]?.jsonPrimitive?.double ?: 1.0
            )
            element is JsonObject && element.containsKey("h") -> IRColor.ColorRepresentation.HSL(
                element["h"]!!.jsonPrimitive.double, element["s"]!!.jsonPrimitive.double, element["l"]!!.jsonPrimitive.double,
                element["a"]?.jsonPrimitive?.double ?: 1.0
            )
            element is JsonPrimitive && element.content.startsWith("#") -> IRColor.ColorRepresentation.Hex(element.content)
            element is JsonPrimitive && element.content == "currentColor" -> IRColor.ColorRepresentation.CurrentColor()
            element is JsonPrimitive && element.content == "transparent" -> IRColor.ColorRepresentation.Transparent()
            element is JsonPrimitive -> IRColor.ColorRepresentation.Named(element.content)
            else -> throw IllegalArgumentException("Cannot deserialize ColorRepresentation from $element")
        }
    }
}

/** Serializer for PaddingValue */
object PaddingValueSerializer : KSerializer<PaddingValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PaddingValue")
    override fun serialize(encoder: Encoder, value: PaddingValue) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is PaddingValue.Length -> encoder.json.encodeToJsonElement(IRLength.serializer(), value.value)
            is PaddingValue.Percentage -> encoder.json.encodeToJsonElement(IRPercentage.serializer(), value.value)
            is PaddingValue.Expression -> buildJsonObject { put("expr", value.raw) }
            is PaddingValue.Keyword -> JsonPrimitive(value.keyword)
        })
    }
    override fun deserialize(decoder: Decoder): PaddingValue {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.content in setOf("inherit", "initial", "unset", "revert", "revert-layer") ->
                PaddingValue.Keyword(element.content)
            element is JsonObject && element.containsKey("expr") -> PaddingValue.Expression(element["expr"]!!.jsonPrimitive.content)
            element is JsonObject && element.containsKey("u") -> PaddingValue.Length(decoder.json.decodeFromJsonElement(IRLength.serializer(), element))
            else -> PaddingValue.Percentage(decoder.json.decodeFromJsonElement(IRPercentage.serializer(), element))
        }
    }
}

/** Serializer for MarginValue */
object MarginValueSerializer : KSerializer<MarginValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MarginValue")
    override fun serialize(encoder: Encoder, value: MarginValue) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is MarginValue.Length -> encoder.json.encodeToJsonElement(IRLength.serializer(), value.value)
            is MarginValue.Percentage -> encoder.json.encodeToJsonElement(IRPercentage.serializer(), value.value)
            is MarginValue.Auto -> JsonPrimitive("auto")
            is MarginValue.Expression -> buildJsonObject { put("expr", value.raw) }
        })
    }
    override fun deserialize(decoder: Decoder): MarginValue {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.content == "auto" -> MarginValue.Auto()
            element is JsonObject && element.containsKey("expr") -> MarginValue.Expression(element["expr"]!!.jsonPrimitive.content)
            element is JsonObject && element.containsKey("u") -> MarginValue.Length(decoder.json.decodeFromJsonElement(IRLength.serializer(), element))
            else -> MarginValue.Percentage(decoder.json.decodeFromJsonElement(IRPercentage.serializer(), element))
        }
    }
}

/** Serializer for ScrollPaddingValue */
object ScrollPaddingValueSerializer : KSerializer<ScrollPaddingValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ScrollPaddingValue")
    override fun serialize(encoder: Encoder, value: ScrollPaddingValue) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is ScrollPaddingValue.Length -> encoder.json.encodeToJsonElement(IRLength.serializer(), value.value)
            is ScrollPaddingValue.Percentage -> encoder.json.encodeToJsonElement(IRPercentage.serializer(), value.value)
            is ScrollPaddingValue.Auto -> JsonPrimitive("auto")
            is ScrollPaddingValue.Keyword -> buildJsonObject { put("kw", JsonPrimitive(value.value)) }
            is ScrollPaddingValue.Raw -> buildJsonObject { put("raw", JsonPrimitive(value.value)) }
        })
    }
    override fun deserialize(decoder: Decoder): ScrollPaddingValue {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.content == "auto" -> ScrollPaddingValue.Auto()
            element is JsonObject && element.containsKey("kw") -> ScrollPaddingValue.Keyword(element["kw"]!!.jsonPrimitive.content)
            element is JsonObject && element.containsKey("raw") -> ScrollPaddingValue.Raw(element["raw"]!!.jsonPrimitive.content)
            element is JsonObject && element.containsKey("u") -> ScrollPaddingValue.Length(decoder.json.decodeFromJsonElement(IRLength.serializer(), element))
            else -> ScrollPaddingValue.Percentage(decoder.json.decodeFromJsonElement(IRPercentage.serializer(), element))
        }
    }
}

/** Serializer for BorderRadiusValue */
object BorderRadiusValueSerializer : KSerializer<BorderRadiusValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BorderRadiusValue")
    override fun serialize(encoder: Encoder, value: BorderRadiusValue) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is BorderRadiusValue.Length -> encoder.json.encodeToJsonElement(IRLength.serializer(), value.value)
            is BorderRadiusValue.Percentage -> encoder.json.encodeToJsonElement(IRPercentage.serializer(), value.value)
            is BorderRadiusValue.Keyword -> JsonPrimitive(value.keyword)
            is BorderRadiusValue.Raw -> buildJsonObject { put("raw", value.value) }
        })
    }
    override fun deserialize(decoder: Decoder): BorderRadiusValue {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.content in setOf("inherit", "initial", "unset", "revert", "revert-layer") ->
                BorderRadiusValue.Keyword(element.content)
            element is JsonObject && element.containsKey("raw") ->
                BorderRadiusValue.Raw(element["raw"]!!.jsonPrimitive.content)
            element is JsonObject && element.containsKey("u") ->
                BorderRadiusValue.Length(decoder.json.decodeFromJsonElement(IRLength.serializer(), element))
            else -> BorderRadiusValue.Percentage(decoder.json.decodeFromJsonElement(IRPercentage.serializer(), element))
        }
    }
}

/** Serializer for AnimationRangeValue */
object AnimationRangeValueSerializer : KSerializer<AnimationRangeValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AnimationRangeValue")
    override fun serialize(encoder: Encoder, value: AnimationRangeValue) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is AnimationRangeValue.Length -> encoder.json.encodeToJsonElement(IRLength.serializer(), value.value)
            is AnimationRangeValue.Percentage -> encoder.json.encodeToJsonElement(IRPercentage.serializer(), value.value)
            is AnimationRangeValue.Keyword -> JsonPrimitive(value.value)
            is AnimationRangeValue.NamedRange -> buildJsonObject {
                put("name", JsonPrimitive(value.name.name.lowercase().replace("_", "-")))
                put("offset", value.offset.value)
            }
            is AnimationRangeValue.Raw -> JsonPrimitive(value.value)
        })
    }
    override fun deserialize(decoder: Decoder): AnimationRangeValue {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonObject && element.containsKey("u") -> AnimationRangeValue.Length(decoder.json.decodeFromJsonElement(IRLength.serializer(), element))
            element is JsonObject && element.containsKey("name") -> {
                val name = TimelineRangeName.valueOf((element["name"] as JsonPrimitive).content.uppercase().replace("-", "_"))
                val offset = IRPercentage((element["offset"] as JsonPrimitive).double)
                AnimationRangeValue.NamedRange(name, offset)
            }
            element is JsonObject -> AnimationRangeValue.Percentage(decoder.json.decodeFromJsonElement(IRPercentage.serializer(), element))
            else -> AnimationRangeValue.Keyword((element as JsonPrimitive).content)
        }
    }
}

/** Serializer for MaskBorderSliceValue */
object MaskBorderSliceValueSerializer : KSerializer<MaskBorderSliceValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MaskBorderSliceValue")
    override fun serialize(encoder: Encoder, value: MaskBorderSliceValue) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is MaskBorderSliceValue.Number -> encoder.json.encodeToJsonElement(IRNumber.serializer(), value.value)
            is MaskBorderSliceValue.Percentage -> encoder.json.encodeToJsonElement(IRPercentage.serializer(), value.value)
            is MaskBorderSliceValue.Values -> buildJsonObject {
                put("top", serializeSliceComponent(encoder, value.top))
                put("right", serializeSliceComponent(encoder, value.right))
                put("bottom", serializeSliceComponent(encoder, value.bottom))
                put("left", serializeSliceComponent(encoder, value.left))
                if (value.fill) put("fill", JsonPrimitive(true))
            }
            is MaskBorderSliceValue.Keyword -> JsonPrimitive(value.keyword)
            is MaskBorderSliceValue.Raw -> JsonPrimitive(value.value)
        })
    }
    private fun serializeSliceComponent(encoder: JsonEncoder, comp: SliceComponent): JsonElement {
        return when (comp) {
            is SliceComponent.Num -> JsonPrimitive(comp.value)
            is SliceComponent.Pct -> buildJsonObject { put("pct", comp.value) }
        }
    }
    override fun deserialize(decoder: Decoder): MaskBorderSliceValue {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> MaskBorderSliceValue.Raw(element.content)
            else -> MaskBorderSliceValue.Number(decoder.json.decodeFromJsonElement(IRNumber.serializer(), element))
        }
    }
}

/** Serializer for PositionValue */
object PositionValueSerializer : KSerializer<PositionValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PositionValue")
    override fun serialize(encoder: Encoder, value: PositionValue) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is PositionValue.Length -> encoder.json.encodeToJsonElement(IRLength.serializer(), value.value)
            is PositionValue.Percentage -> encoder.json.encodeToJsonElement(IRPercentage.serializer(), value.value)
            is PositionValue.Keyword -> JsonPrimitive(value.value)
        })
    }
    override fun deserialize(decoder: Decoder): PositionValue {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonObject && element.containsKey("u") -> PositionValue.Length(decoder.json.decodeFromJsonElement(IRLength.serializer(), element))
            element is JsonObject -> PositionValue.Percentage(decoder.json.decodeFromJsonElement(IRPercentage.serializer(), element))
            else -> PositionValue.Keyword((element as JsonPrimitive).content)
        }
    }
}

/**
 * Common value type for border-width properties with dual storage.
 * Used by border-top-width, border-right-width, border-bottom-width, border-left-width.
 *
 * CSS spec keyword → pixel mappings:
 * - thin → 1px
 * - medium → 3px
 * - thick → 5px
 */
@Serializable(with = BorderWidthValueSerializer::class)
data class BorderWidthValue(
    /** Normalized width in pixels */
    val pixels: Double,
    /** Original representation for CSS regeneration */
    val original: BorderWidthOriginal
) {
    @Serializable
    sealed interface BorderWidthOriginal {
        @Serializable data class Length(val length: IRLength) : BorderWidthOriginal
        @Serializable data class Keyword(val keyword: String) : BorderWidthOriginal
    }

    companion object {
        // CSS spec pixel values for keywords
        private const val THIN_PX = 1.0
        private const val MEDIUM_PX = 3.0
        private const val THICK_PX = 5.0

        /** Create from keyword */
        fun fromKeyword(keyword: String): BorderWidthValue {
            val lower = keyword.lowercase()
            val px = when (lower) {
                "thin" -> THIN_PX
                "medium" -> MEDIUM_PX
                "thick" -> THICK_PX
                else -> MEDIUM_PX
            }
            return BorderWidthValue(px, BorderWidthOriginal.Keyword(lower))
        }

        /** Create from length */
        fun fromLength(length: IRLength): BorderWidthValue {
            val px = length.pixels ?: 0.0
            return BorderWidthValue(px, BorderWidthOriginal.Length(length))
        }
    }
}

// Keep enum for backwards compatibility in other files
enum class BorderWidthKeyword { THIN, MEDIUM, THICK }

/** Serializer for BorderWidthValue */
object BorderWidthValueSerializer : KSerializer<BorderWidthValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BorderWidthValue")

    override fun serialize(encoder: Encoder, value: BorderWidthValue) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(buildJsonObject {
            put("px", value.pixels)
            when (val orig = value.original) {
                is BorderWidthValue.BorderWidthOriginal.Length -> {
                    // Only include original if different from pixels or has unit info
                    if (orig.length.originalUnit != IRLength.LengthUnit.PX || orig.length.pixels != value.pixels) {
                        put("original", encoder.json.encodeToJsonElement(IRLength.serializer(), orig.length))
                    }
                }
                is BorderWidthValue.BorderWidthOriginal.Keyword -> {
                    put("original", orig.keyword)
                }
            }
        })
    }

    override fun deserialize(decoder: Decoder): BorderWidthValue {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()

        return when {
            element is JsonObject -> {
                val px = element["px"]?.jsonPrimitive?.double ?: 0.0
                val original = element["original"]
                when {
                    original is JsonPrimitive && original.isString -> {
                        BorderWidthValue(px, BorderWidthValue.BorderWidthOriginal.Keyword(original.content))
                    }
                    original is JsonObject -> {
                        val length = decoder.json.decodeFromJsonElement(IRLength.serializer(), original)
                        BorderWidthValue(px, BorderWidthValue.BorderWidthOriginal.Length(length))
                    }
                    else -> {
                        BorderWidthValue(px, BorderWidthValue.BorderWidthOriginal.Length(IRLength.fromPx(px)))
                    }
                }
            }
            // Legacy format support
            element is JsonPrimitive && element.isString -> {
                BorderWidthValue.fromKeyword(element.content)
            }
            else -> BorderWidthValue.fromKeyword("medium")
        }
    }
}

/**
 * Common value type for sizing properties (block-size, inline-size, min/max variants).
 * Accepts length, percentage, auto, min-content, max-content, fit-content.
 */
@Serializable(with = SizeValueSerializer::class)
sealed interface SizeValue {
    @Serializable data class LengthValue(val length: IRLength) : SizeValue
    @Serializable data class PercentageValue(val percentage: IRPercentage) : SizeValue
    @Serializable data object Auto : SizeValue
    @Serializable data object None : SizeValue
    @Serializable data object MaxContent : SizeValue
    @Serializable data object MinContent : SizeValue
    @Serializable data class FitContent(val length: IRLength?) : SizeValue
    /** CSS expression: calc(), clamp(), min(), max(), var() */
    @Serializable data class Expression(val raw: String) : SizeValue
    /** CSS global keywords: inherit, initial, unset, revert */
    @Serializable data class Keyword(val keyword: String) : SizeValue
}

/** Serializer for SizeValue */
object SizeValueSerializer : KSerializer<SizeValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SizeValue")
    override fun serialize(encoder: Encoder, value: SizeValue) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is SizeValue.LengthValue -> encoder.json.encodeToJsonElement(IRLength.serializer(), value.length)
            is SizeValue.PercentageValue -> encoder.json.encodeToJsonElement(IRPercentage.serializer(), value.percentage)
            is SizeValue.Auto -> JsonPrimitive("auto")
            is SizeValue.None -> JsonPrimitive("none")
            is SizeValue.MaxContent -> JsonPrimitive("max-content")
            is SizeValue.MinContent -> JsonPrimitive("min-content")
            is SizeValue.FitContent -> buildJsonObject {
                put("fit-content", value.length?.let { encoder.json.encodeToJsonElement(IRLength.serializer(), it) } ?: JsonNull)
            }
            is SizeValue.Expression -> buildJsonObject { put("expr", value.raw) }
            is SizeValue.Keyword -> JsonPrimitive(value.keyword)
        })
    }
    override fun deserialize(decoder: Decoder): SizeValue {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonObject && element.containsKey("u") -> SizeValue.LengthValue(decoder.json.decodeFromJsonElement(IRLength.serializer(), element))
            element is JsonObject && element.containsKey("fit-content") -> {
                val fitVal = element["fit-content"]
                SizeValue.FitContent(if (fitVal is JsonNull) null else decoder.json.decodeFromJsonElement(IRLength.serializer(), fitVal!!))
            }
            element is JsonObject && element.containsKey("expr") -> SizeValue.Expression(element["expr"]!!.jsonPrimitive.content)
            element is JsonPrimitive && element.content == "auto" -> SizeValue.Auto
            element is JsonPrimitive && element.content == "none" -> SizeValue.None
            element is JsonPrimitive && element.content == "max-content" -> SizeValue.MaxContent
            element is JsonPrimitive && element.content == "min-content" -> SizeValue.MinContent
            element is JsonPrimitive && element.content in setOf("inherit", "initial", "unset", "revert") -> SizeValue.Keyword(element.content)
            element is JsonPrimitive -> SizeValue.Keyword(element.content)
            else -> SizeValue.Auto
        }
    }
}

/**
 * Common value type for border line styles.
 * Used by border-style, border-top/right/bottom/left-style, outline-style,
 * and logical border style properties.
 */
@Serializable
enum class LineStyle {
    NONE, HIDDEN, DOTTED, DASHED, SOLID, DOUBLE, GROOVE, RIDGE, INSET, OUTSET
}
