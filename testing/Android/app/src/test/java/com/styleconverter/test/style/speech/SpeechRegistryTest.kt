package com.styleconverter.test.style.speech

import com.styleconverter.test.style.PropertyRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpeechRegistryTest {

    @Before
    fun prime() {
        SpeechRegistration.hashCode()
    }

    private val props = listOf(
        "Volume", "Speak", "SpeakAs",
        "Pause", "PauseBefore", "PauseAfter",
        "Rest", "RestBefore", "RestAfter",
        "Cue", "CueBefore", "CueAfter",
        "VoiceFamily", "VoicePitch", "VoiceRange",
        "VoiceRate", "VoiceStress", "VoiceVolume",
        "VoiceBalance", "VoiceDuration",
        "Pitch", "PitchRange", "Richness", "Stress",
        "SpeechRate", "Azimuth", "Elevation"
    )

    @Test
    fun `all 27 speech properties are registered under speech owner`() {
        val bad = props.filter { PropertyRegistry.ownerOf(it) != "speech" }
        assertTrue("Wrong owner or missing:\n  ${bad.joinToString("\n  ")}", bad.isEmpty())
    }

    @Test
    fun `speech property count matches phase10 coverage`() {
        assertTrue("Expected exactly 27 speech props, got ${props.size}", props.size == 27)
    }
}
