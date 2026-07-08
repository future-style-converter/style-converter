package com.styleconverter.test.style.speech

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts speech configuration from IR properties.
 */
object SpeechExtractor {

    fun extractSpeechConfig(properties: List<Pair<String, JsonElement?>>): SpeechConfig {
        var speak = SpeakValue.NORMAL
        var speakAs = SpeakAsValue.NORMAL
        var voiceFamily: VoiceFamilyValue = VoiceFamilyValue.Auto
        var voiceRate = 1.0f
        var voicePitch = 1.0f
        var voiceVolume = 1.0f
        var pauseBefore = 0f
        var pauseAfter = 0f

        for ((type, data) in properties) {
            when (type) {
                "Speak" -> speak = extractSpeak(data)
                "SpeakAs" -> speakAs = extractSpeakAs(data)
                "VoiceFamily" -> voiceFamily = extractVoiceFamily(data)
                "VoiceRate" -> voiceRate = extractFloat(data, 1.0f)
                "VoicePitch" -> voicePitch = extractFloat(data, 1.0f)
                "VoiceVolume" -> voiceVolume = extractFloat(data, 1.0f)
                "PauseBefore" -> pauseBefore = extractFloat(data, 0f)
                "PauseAfter" -> pauseAfter = extractFloat(data, 0f)
            }
        }

        return SpeechConfig(
            speak = speak,
            speakAs = speakAs,
            voiceFamily = voiceFamily,
            voiceRate = voiceRate,
            voicePitch = voicePitch,
            voiceVolume = voiceVolume,
            pauseBefore = pauseBefore,
            pauseAfter = pauseAfter
        )
    }

    private fun extractSpeak(data: JsonElement?): SpeakValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return SpeakValue.NORMAL

        return when (keyword) {
            "NORMAL" -> SpeakValue.NORMAL
            "NONE" -> SpeakValue.NONE
            "SPELL_OUT" -> SpeakValue.SPELL_OUT
            else -> SpeakValue.NORMAL
        }
    }

    private fun extractSpeakAs(data: JsonElement?): SpeakAsValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return SpeakAsValue.NORMAL

        return when (keyword) {
            "NORMAL" -> SpeakAsValue.NORMAL
            "SPELL_OUT" -> SpeakAsValue.SPELL_OUT
            "DIGITS" -> SpeakAsValue.DIGITS
            "LITERAL_PUNCTUATION" -> SpeakAsValue.LITERAL_PUNCTUATION
            "NO_PUNCTUATION" -> SpeakAsValue.NO_PUNCTUATION
            else -> SpeakAsValue.NORMAL
        }
    }

    private fun extractVoiceFamily(data: JsonElement?): VoiceFamilyValue {
        if (data == null) return VoiceFamilyValue.Auto

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase()
                return when (content) {
                    "auto" -> VoiceFamilyValue.Auto
                    "male" -> VoiceFamilyValue.Generic(GenericVoice.MALE)
                    "female" -> VoiceFamilyValue.Generic(GenericVoice.FEMALE)
                    "child" -> VoiceFamilyValue.Generic(GenericVoice.CHILD)
                    "neutral" -> VoiceFamilyValue.Generic(GenericVoice.NEUTRAL)
                    else -> VoiceFamilyValue.Named(content ?: "auto")
                }
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
                return when (type) {
                    "auto" -> VoiceFamilyValue.Auto
                    "generic" -> {
                        val voice = data["voice"]?.jsonPrimitive?.contentOrNull?.uppercase()
                        val genericVoice = when (voice) {
                            "MALE" -> GenericVoice.MALE
                            "FEMALE" -> GenericVoice.FEMALE
                            "CHILD" -> GenericVoice.CHILD
                            else -> GenericVoice.NEUTRAL
                        }
                        VoiceFamilyValue.Generic(genericVoice)
                    }
                    "named" -> {
                        val name = data["name"]?.jsonPrimitive?.contentOrNull ?: ""
                        VoiceFamilyValue.Named(name)
                    }
                    else -> VoiceFamilyValue.Auto
                }
            }
            else -> return VoiceFamilyValue.Auto
        }
    }

    private fun extractFloat(data: JsonElement?, default: Float): Float {
        if (data == null) return default
        return when (data) {
            is JsonPrimitive -> data.floatOrNull ?: default
            is JsonObject -> data["value"]?.jsonPrimitive?.floatOrNull ?: default
            else -> default
        }
    }

    fun isSpeechProperty(type: String): Boolean {
        return type in setOf(
            "Speak", "SpeakAs", "VoiceFamily", "VoiceRate",
            "VoicePitch", "VoiceVolume", "PauseBefore", "PauseAfter"
        )
    }
}
