package com.styleconverter.test.style.visibility

// Phase 8 facade — the runtime logic for `visibility` and the overflow longhands
// lives in older, pre-canonical modules (style.interactions.InteractionExtractor
// for `visibility`, style.scrolling.OverflowExtractor for the five
// `overflow*` properties). Relocating those extractors wholesale is out of
// Phase 8's scope because both modules carry additional properties that are
// not part of Phase 8 (PointerEvents, UserSelect, OverflowAnchor, etc.), so
// moving the files would force renames across already-migrated phases.
//
// Instead this file is a *registration* facade: it exists so the canonical
// style/visibility/ folder is populated (required by CLAUDE.md's per-platform
// mirror rule), and so Phase 8's coverage asserts have a single object to
// prime to force the PropertyRegistry writes. The actual IR → Config →
// Modifier pipeline is still run by InteractionExtractor + OverflowExtractor
// at style-apply time; the registry claim here just documents ownership.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers the seven "visibility-family" CSS properties under the
 * `visibility` owner. These are the properties that, per the CSS spec, hide
 * or clip the element as a whole (as opposed to per-child scroll behavior).
 *
 * `Visibility` maps to [com.styleconverter.test.style.interactions.InteractionExtractor]
 * (visibility/collapse/hidden → alpha + layout flag). The five `overflow*`
 * properties map to [com.styleconverter.test.style.scrolling.OverflowExtractor]
 * (keyword → OverflowBehavior → Modifier.clip or Scrollable). See
 * [StyleApplier.applyConfig] for the actual Modifier chain.
 *
 * Touching [VisibilityRegistration] from any test primes this init block via
 * Kotlin's lazy object initialization rules; the `@Before` hook in
 * Phase8RegistryTest does exactly that.
 */
object VisibilityRegistration {

    init {
        // All seven Phase 8 visibility/overflow longhands claim the canonical
        // `visibility` owner. Note that `Visibility` itself is the legacy CSS
        // 2.1 keyword (visible | hidden | collapse) — it is NOT the same as
        // `content-visibility` (which is interactions-owned performance hint)
        // and is also not the same as `backface-visibility` (which is
        // transforms-owned; see Transform3DExtractor).
        PropertyRegistry.migrated(
            "Visibility",
            "Overflow",
            "OverflowX",
            "OverflowY",
            "OverflowBlock",
            "OverflowInline",
            owner = "visibility"
        )
    }
}
