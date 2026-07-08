package com.styleconverter.test.style.rhythm

// Phase 10 facade — CSS Rhythmic Sizing module (block-step-*). Parse-only
// on every mobile target; no Compose analogue for vertical-rhythm-
// snapped box sizing. LineGrid / LineSnap / LineHeightStep (the rhythm-
// adjacent typography properties) are already claimed by typography/
// TypographyExtractor.
//
// Parser-gap notes: BlockStep is a shorthand combining size + insert +
// align + round. All five are strict enums or a length (size).

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers 5 CSS Rhythmic Sizing IR properties under the `rhythm` owner.
 * All parse-only on Compose.
 */
object RhythmRegistration {

    init {
        PropertyRegistry.migrated(
            "BlockStep",
            "BlockStepAlign", "BlockStepInsert",
            "BlockStepRound", "BlockStepSize",
            owner = "rhythm"
        )
    }
}
