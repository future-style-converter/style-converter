package app.irmodels.properties.transforms

import app.irmodels.*
import kotlinx.serialization.Serializable

/** Transform functions for CSS transform property */
@Serializable(with = TransformFunctionSerializer::class)
sealed interface TransformFunction {
    @Serializable data class Translate(val x: IRLength, val y: IRLength) : TransformFunction
    @Serializable data class TranslateX(val x: IRLength) : TransformFunction
    @Serializable data class TranslateY(val y: IRLength) : TransformFunction
    @Serializable data class TranslateZ(val z: IRLength) : TransformFunction
    @Serializable data class Translate3d(val x: IRLength, val y: IRLength, val z: IRLength) : TransformFunction
    @Serializable data class Scale(val x: IRNumber, val y: IRNumber) : TransformFunction
    @Serializable data class ScaleX(val x: IRNumber) : TransformFunction
    @Serializable data class ScaleY(val y: IRNumber) : TransformFunction
    @Serializable data class ScaleZ(val z: IRNumber) : TransformFunction
    @Serializable data class Scale3d(val x: IRNumber, val y: IRNumber, val z: IRNumber) : TransformFunction
    @Serializable data class Rotate(val angle: IRAngle) : TransformFunction
    @Serializable data class RotateX(val angle: IRAngle) : TransformFunction
    @Serializable data class RotateY(val angle: IRAngle) : TransformFunction
    @Serializable data class RotateZ(val angle: IRAngle) : TransformFunction
    @Serializable data class Rotate3d(val x: IRNumber, val y: IRNumber, val z: IRNumber, val angle: IRAngle) : TransformFunction
    @Serializable data class Skew(val x: IRAngle, val y: IRAngle) : TransformFunction
    @Serializable data class SkewX(val angle: IRAngle) : TransformFunction
    @Serializable data class SkewY(val angle: IRAngle) : TransformFunction
    @Serializable data class Perspective(val length: IRLength) : TransformFunction
    @Serializable data class Matrix(
        val a: IRNumber, val b: IRNumber, val c: IRNumber,
        val d: IRNumber, val e: IRNumber, val f: IRNumber
    ) : TransformFunction
    @Serializable data class Matrix3d(
        val a1: IRNumber, val b1: IRNumber, val c1: IRNumber, val d1: IRNumber,
        val a2: IRNumber, val b2: IRNumber, val c2: IRNumber, val d2: IRNumber,
        val a3: IRNumber, val b3: IRNumber, val c3: IRNumber, val d3: IRNumber,
        val a4: IRNumber, val b4: IRNumber, val c4: IRNumber, val d4: IRNumber
    ) : TransformFunction

    companion object {
        // Translation factories
        fun translate(xPx: Double, yPx: Double) = Translate(IRLength.fromPx(xPx), IRLength.fromPx(yPx))
        fun translateX(px: Double) = TranslateX(IRLength.fromPx(px))
        fun translateY(px: Double) = TranslateY(IRLength.fromPx(px))
        fun translateZ(px: Double) = TranslateZ(IRLength.fromPx(px))
        fun translate3d(xPx: Double, yPx: Double, zPx: Double) = Translate3d(IRLength.fromPx(xPx), IRLength.fromPx(yPx), IRLength.fromPx(zPx))

        // Scale factories
        fun scale(xy: Double) = Scale(IRNumber(xy), IRNumber(xy))
        fun scale(x: Double, y: Double) = Scale(IRNumber(x), IRNumber(y))
        fun scaleX(x: Double) = ScaleX(IRNumber(x))
        fun scaleY(y: Double) = ScaleY(IRNumber(y))
        fun scaleZ(z: Double) = ScaleZ(IRNumber(z))
        fun scale3d(x: Double, y: Double, z: Double) = Scale3d(IRNumber(x), IRNumber(y), IRNumber(z))

        // Rotation factories (in degrees)
        fun rotate(degrees: Double) = Rotate(IRAngle.fromDegrees(degrees))
        fun rotateX(degrees: Double) = RotateX(IRAngle.fromDegrees(degrees))
        fun rotateY(degrees: Double) = RotateY(IRAngle.fromDegrees(degrees))
        fun rotateZ(degrees: Double) = RotateZ(IRAngle.fromDegrees(degrees))
        fun rotate3d(x: Double, y: Double, z: Double, degrees: Double) =
            Rotate3d(IRNumber(x), IRNumber(y), IRNumber(z), IRAngle.fromDegrees(degrees))

        // Skew factories (in degrees)
        fun skew(xDeg: Double, yDeg: Double) = Skew(IRAngle.fromDegrees(xDeg), IRAngle.fromDegrees(yDeg))
        fun skewX(degrees: Double) = SkewX(IRAngle.fromDegrees(degrees))
        fun skewY(degrees: Double) = SkewY(IRAngle.fromDegrees(degrees))

        // Perspective factory
        fun perspective(px: Double) = Perspective(IRLength.fromPx(px))

        // Matrix factory
        fun matrix(a: Double, b: Double, c: Double, d: Double, e: Double, f: Double) =
            Matrix(IRNumber(a), IRNumber(b), IRNumber(c), IRNumber(d), IRNumber(e), IRNumber(f))

        // Identity transforms
        fun identity() = scale(1.0)
        fun noTranslate() = translate(0.0, 0.0)
        fun noRotate() = rotate(0.0)
    }
}
