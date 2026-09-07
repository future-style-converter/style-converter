//
//  BackfaceCulling.swift
//  StyleEngine/transforms — wave 19 (lane BACKFACE, RC-B4a).
//
//  css-transforms-2 §5.1: `backface-visibility: hidden` hides an element
//  when the z component of the transformed normal (0,0,1) of its plane —
//  under the ACCUMULATED 3D transformation matrix of the element and its
//  ancestors up through the containing 3D rendering context — is
//  negative. The old applier heuristic summed only the element's OWN
//  X/Y rotation degrees, so ancestor transforms never participated:
//  backface-visibility-hidden-001's container `perspective: 1000px;
//  transform: rotateY(45deg)` over a preserve-3d card must leave the
//  unrotated green face VISIBLE (accumulated 45°, front-facing) and hide
//  the rotateY(180deg) red face (accumulated 225°, back-facing).
//
//  Mechanism: an ambient CATransform3D environment channel (the
//  wptCaptureMode / styleViewport pattern). An element that ESTABLISHES
//  or EXTENDS a 3D rendering context (css-transforms-2 §4.1: a `perspective`
//  value other than none establishes one; `transform-style: preserve-3d`
//  extends the parent's) publishes `inherited · own-rotation` to its
//  children; a TOUCHED flat element resets the channel to identity
//  (its subtree is flattened into its plane, ending the context).
//  Untransformed wrappers (nil/untouched aggregate — the applier's
//  short-circuit) pass the channel through unchanged: a documented
//  approximation (a plain flat DIV strictly ends the context too), safe
//  because a wrapper with NO transform properties contributes only
//  identity to the accumulation anyway.
//
//  Compose CANNOT mirror this wave: propagating an ancestor 3D matrix
//  through graphicsLayer is the documented Compose WALL for wave 19 —
//  iOS-only fidelity, stated in the lane report.
//

import SwiftUI
// CATransform3D — the same 4×4 carrier Rotate3DEffect builds from.
import QuartzCore

/// Ambient accumulated 3D matrix of the containing 3D rendering context.
/// Default identity = no 3D context above (every pre-existing render is
/// byte-identical: identity accumulation reduces the culling test to the
/// element's own rotation).
private struct Transforms3DContextKey: EnvironmentKey {
    static let defaultValue: CATransform3D = CATransform3DIdentity
}

extension EnvironmentValues {
    /// The accumulated ancestor 3D matrix backface culling tests against.
    /// Internal — only TransformsApplier reads/writes it.
    var transforms3DAccumulated: CATransform3D {
        get { self[Transforms3DContextKey.self] }
        set { self[Transforms3DContextKey.self] = newValue }
    }
}

/// Pure backface arithmetic — statics so XCTest pins every decision
/// without a view hierarchy (the TransformsMath pattern).
enum BackfaceCulling {

    /// The element's OWN rotation matrix: every `.rotate` in the declared
    /// function list (CSS order), then the `rotate` longhand (css-transforms-2
    /// §8: the longhands stack after `transform`). Translate/scale/skew/
    /// matrix entries are skipped deliberately: translations never move a
    /// plane normal, and the corpus carries no negative scales or 3D
    /// matrix() values that could flip facing — documented approximation,
    /// not a silent drop (the culling test below only needs the rotation
    /// part of the §5.1 accumulated matrix).
    static func rotationMatrix(of agg: TransformsAggregate) -> CATransform3D {
        var m = CATransform3DIdentity
        // Function list first, longhand rotate last — declaration order.
        for fn in agg.functions + [agg.rotate].compactMap({ $0 }) {
            if case .rotate(let x, let y, let z, let d) = fn,
               d != 0, (x != 0 || y != 0 || z != 0) {
                // Fold onto the running product (CATransform3DRotate is the
                // same primitive Rotate3DEffect renders with, so the culling
                // matrix and the painted matrix cannot disagree on angles).
                m = CATransform3DRotate(m, d * .pi / 180, x, y, z)
            }
        }
        return m
    }

    /// `inherited · own` — the §5.1 accumulation step. CATransform3DConcat
    /// applies its first operand first, so the element's own rotation
    /// composes inside the ancestor context exactly like a nested
    /// CALayer's transform composes inside its superlayer's.
    static func accumulate(own: CATransform3D,
                           inherited: CATransform3D) -> CATransform3D {
        CATransform3DConcat(own, inherited)
    }

    /// z component (up to a positive determinant factor) of the plane
    /// normal (0,0,1) transformed by [m]: normals map through the inverse
    /// transpose, whose (3,3) entry is cofactor33/det = (m11·m22 −
    /// m12·m21)/det — WebKit's TransformationMatrix::isBackFaceVisible
    /// tests this same cofactor. Rotation products keep det = +1, so the
    /// cofactor's SIGN is the §5.1 facing test verbatim: rotateY(θ) gives
    /// cosθ (45° → +0.707 front, 225° → −0.707 back). The cofactor is
    /// transpose-invariant, so the row/column convention drops out.
    static func backfaceZ(_ m: CATransform3D) -> CGFloat {
        m.m11 * m.m22 - m.m12 * m.m21
    }

    /// The full culling decision: hidden iff the element asked for it AND
    /// the accumulated normal points away (§5.1 "negative" — an exactly
    /// edge-on 90° plane, z == 0, stays visible per the strict inequality).
    static func isCulled(agg: TransformsAggregate,
                         inherited: CATransform3D) -> Bool {
        agg.backfaceHidden &&
            backfaceZ(accumulate(own: rotationMatrix(of: agg),
                                 inherited: inherited)) < 0
    }

    /// True when the element ESTABLISHES or EXTENDS a 3D rendering context
    /// for its children (css-transforms-2 §4.1): a used `perspective` other
    /// than none establishes one; `preserve-3d` extends the parent's. Such
    /// elements publish the accumulated matrix; touched FLAT elements
    /// reset it (their subtree flattens into their plane).
    static func publishes3DContext(_ agg: TransformsAggregate) -> Bool {
        agg.preserve3D || agg.perspective?.distancePx != nil
    }
}
