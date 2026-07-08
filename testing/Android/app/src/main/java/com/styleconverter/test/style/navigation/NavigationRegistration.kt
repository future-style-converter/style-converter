package com.styleconverter.test.style.navigation

// Phase 10 facade — CSS Basic User Interface navigation properties were
// dropped from CSS-UI Level 4 but the parser still accepts them for
// round-trip compatibility. SpatialNavigationExtractor (under
// interactions/) already extracts nav-up/down/left/right into a
// SpatialNavigationConfig. This facade claims the four nav-* longhands
// plus `reading-order` under the `navigation` owner so the canonical
// style/navigation/ folder mirrors the irmodels/properties/navigation/
// folder.
//
// Parser-gap note:
//   * Nav{Up,Down,Left,Right} accept `auto` or any other string (strips
//     leading `#`).
//
// Applier is no-op — Android Compose has no spatial-navigation focus
// system comparable to CSS Nav. Focus traversal uses Modifier.focusProperties
// with up/down/left/right getters but is not wired to these IR properties.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers 5 spatial-navigation IR properties under the `navigation`
 * owner. All parse-only on Compose mobile.
 */
object NavigationRegistration {

    init {
        PropertyRegistry.migrated(
            "NavUp", "NavDown", "NavLeft", "NavRight",
            "ReadingOrder",
            owner = "navigation"
        )
    }
}
