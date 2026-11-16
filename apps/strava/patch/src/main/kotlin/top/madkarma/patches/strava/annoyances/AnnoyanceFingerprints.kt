// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.strava.annoyances

import app.reseam.patch.Type
import app.reseam.patch.dex.ref
import app.reseam.patch.fieldTarget
import app.reseam.patch.klass
import app.reseam.patch.method

// The builder of the new-registration step list also decides on phone verification.
private val registrationStepBuilder =
    klass("registrationStepBuilder") {
        strings("Failed to get isInPhoneVerificationAllowListUseCase: ")
    }

// The suspending builder returns the finished step list as a Serializable.
internal val buildRegistrationSteps =
    method("buildRegistrationSteps") {
        inClass(registrationStepBuilder)
        returns("java.io.Serializable")
    }

// The You tab asks this resolver which sheet, if any, to open over it.
internal val youTabOverlay = method("youTabOverlay") { strings("you.overlay.current_tab") }

// Overlay results are only named by toString(), and only the loss-aversion name is unique.
private val lossAversionOverlay = klass("lossAversionOverlay") { strings("LossAversionSheet") }

private val noOverlay =
    klass("noOverlay") {
        strings("None")
        custom { interfaces == lossAversionOverlay.classDef.interfaces }
    }

internal val noOverlayInstance =
    fieldTarget("noOverlayInstance") {
        val overlayClass = noOverlay.classDef
        overlayClass.staticFields.singleOrNull { it.fieldType == overlayClass.descriptor }?.ref
            ?: error("Expected exactly one None overlay singleton")
    }

private val contactSharingSheet =
    klass("com.strava.feed.view.modal.contactSharing.ContactSharingBottomSheetFragment")

// The sheet re-checks the feed's eligibility check when it opens; the feed overlay resolver asks
// the same check before picking the sheet.
internal val contactSharingEligible =
    method("contactSharingEligible") {
        calledBy(contactSharingSheet.method("onCreate"))
        returns(Type.Boolean)
    }

// Onboarding finishes by asking the feed for this record prompt, which it opens from a delayed
// task.
internal val showRecordPrompt =
    method("showRecordPrompt") {
        name("run")
        calls {
            owner("com.strava.onboarding.view.OnboardingConnectDeviceRecordDialogFragment")
            name("<init>")
        }
    }
