package com.styleconverter.test.style.speech

/**
 * Speak value options.
 */
enum class SpeakValue {
    NORMAL,
    NONE,
    SPELL_OUT
}

/**
 * Speak-as value options.
 */
enum class SpeakAsValue {
    NORMAL,
    SPELL_OUT,
    DIGITS,
    LITERAL_PUNCTUATION,
    NO_PUNCTUATION
}

/**
 * Voice family value.
 */
sealed interface VoiceFamilyValue {
    data object Auto : VoiceFamilyValue
    data class Named(val name: String) : VoiceFamilyValue
    data class Generic(val type: GenericVoice) : VoiceFamilyValue
}

enum class GenericVoice {
    MALE,
    FEMALE,
    CHILD,
    NEUTRAL
}

/**
 * Configuration for CSS speech synthesis properties.
 */
data class SpeechConfig(
    val speak: SpeakValue = SpeakValue.NORMAL,
    val speakAs: SpeakAsValue = SpeakAsValue.NORMAL,
    val voiceFamily: VoiceFamilyValue = VoiceFamilyValue.Auto,
    val voiceRate: Float = 1.0f,
    val voicePitch: Float = 1.0f,
    val voiceVolume: Float = 1.0f,
    val pauseBefore: Float = 0f,
    val pauseAfter: Float = 0f
) {
    val hasSpeech: Boolean
        get() = speak != SpeakValue.NORMAL ||
                speakAs != SpeakAsValue.NORMAL ||
                voiceFamily != VoiceFamilyValue.Auto ||
                voiceRate != 1.0f ||
                voicePitch != 1.0f ||
                voiceVolume != 1.0f ||
                pauseBefore != 0f ||
                pauseAfter != 0f
}
