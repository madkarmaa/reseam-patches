// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.strava.analytics

import app.reseam.patch.Type
import app.reseam.patch.dex.ref
import app.reseam.patch.fieldTarget
import app.reseam.patch.klass
import app.reseam.patch.method

// Snowplow stores each tracked event from this background task.
private val storeEventPayload =
    method("storeEventPayload") { strings("Adding new payload to event storage: %s") }

// Snowplow's track(event) is the only method that schedules that task.
internal val trackEvent =
    method("trackEvent") {
        calls {
            owner(storeEventPayload.owner)
            name("<init>")
        }
    }

private val updateDataSharing =
    method("updateDataSharing") {
        calls {
            owner("com.strava.settings.gateway.SettingsApi")
            name("updateDataShareSetting")
        }
    }

// The consent handler sends the choice through the settings gateway, then applies it to
// Firebase.
internal val applyDataSharing =
    method("applyDataSharing") {
        calls {
            owner(klass(updateDataSharing.owner).classDef.interfaces.single())
            name(updateDataSharing.name)
        }
    }

// Branch resets its session after the last activity stops, and reloads its tracking-disabled flag
// through this preference getter, as it does at startup.
private val resetBranchSession =
    method("resetBranchSession") { strings("activityCnt_: reset to 0") }

internal val branchPreferenceFlag =
    method("branchPreferenceFlag") {
        calledBy(resetBranchSession)
        params(Type.String)
        returns(Type.Boolean)
    }

private val consentRejected =
    klass("consentRejected") { strings("DataSharingConsentRejectClicked") }

internal val rejectConsent =
    fieldTarget("rejectConsent") {
        val eventClass = consentRejected.classDef
        eventClass.staticFields.singleOrNull { it.fieldType == eventClass.descriptor }?.ref
            ?: error("Expected exactly one consent rejection event")
    }

private val registrationFlow = klass("registrationFlow") { strings("age_conflict_inserted") }

// The flow's event handler records both consent answers.
internal val handleRegistrationEvent =
    method("handleRegistrationEvent") {
        inClass(registrationFlow)
        strings("granted", "denied")
    }

internal val registrationFlowConstructor = registrationFlow.method("<init>")
