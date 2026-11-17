// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.mtmanager.vip

import app.reseam.patch.*
import app.reseam.patch.dex.AccessFlags
import top.madkarma.mtmanager.vip.bindings.Editor
import top.madkarma.mtmanager.vip.bindings.License
import top.madkarma.mtmanager.vip.bindings.NativeBridge
import top.madkarma.mtmanager.vip.bindings.Network
import top.madkarma.patches.mtmanager.MTManagerPatchStrings
import top.madkarma.patches.mtmanager.shared.integrity

val unlockVip =
    patch(MTManagerPatchStrings.unlockVipName) {
        description(MTManagerPatchStrings.unlockVipDescription)
        compatibleWith("bin.mt.plus")
        dependsOn(integrity)

        execute {
            val completionMask = completionIdentityMask
            vipAccountState.replace { returnTrue() }
            log.info(
                "VIP: ${vipAccountState.descriptor} reports every account state as VIP, keeping network storage open.",
            )

            licenseStartup.after { track(licenseState) }
            accountStartup.after { track(accountState) }

            val expiryGetters =
                licenseValues.all.map { licenseState to it } + (accountState to vipExpiry)
            log.info(
                "VIP: ${expiryGetters.joinToString { it.second.descriptor }} read one shared VIP expiry.",
            )

            val responseBlockers = blockedNetworkResponses.all
            if (responseBlockers.isEmpty()) error("Network VIP response gates are missing")

            for (interceptor in responseBlockers) {
                interceptor.replace {
                    returnValue(
                        Network.proceed(
                                paramOfType(chainProceed.owner),
                                string(chainRequest.name),
                                string(chainProceed.name),
                            )
                            .cast(interceptor.returnType),
                    )
                }
                log.info("VIP: ${interceptor.descriptor} preserves the server's response.")
            }

            val nativeAbis =
                files
                    .list()
                    .filter { it.startsWith("lib/") && it.endsWith("/libmtprotect.so") }
                    .map { it.split('/')[1] }

            if (nativeAbis.isEmpty()) error("MT protection library is missing")

            for (abi in nativeAbis) {
                val path = "lib/$abi/libmtvip.so"
                val library =
                    VipResources.javaClass.classLoader.getResourceAsStream(path)
                        ?: error("Native VIP bridge is unavailable for $abi")
                library.use { files.write(path, it) }
            }

            // Preserve the native declaration for the protector's registration. Rebind its JNI
            // entry after Application initialization has finished.
            val unlockedVip =
                klass(vipEntitlement.owner).addMethod(
                    "reseam_isVip",
                    "()Z",
                    AccessFlags.PUBLIC or AccessFlags.STATIC,
                ) {
                    returnTrue()
                }

            applicationStartup.after {
                setStatic(
                    completionIdentity,
                    Editor.completionIdentity(thisObject, int(completionMask)),
                )

                for ((state, getter) in expiryGetters) {
                    License.coverExpiry(
                        staticField(state),
                        string(className(getter.owner)),
                        string(getter.name),
                    )
                }

                NativeBridge.install(
                    string(className(vipEntitlement.owner)),
                    string(vipEntitlement.name),
                    string(unlockedVip.name),
                )
            }
            log.info(
                "VIP: ${vipEntitlement.descriptor} forwards to ${unlockedVip.descriptor} after native registration.",
            )
            log.info(
                "VIP: ${completionIdentity.owner}->${completionIdentity.name}:${completionIdentity.type} binds text completion to the installed application UID with extracted mask $completionMask.",
            )
        }
    }

private fun CodeScope.track(state: FieldTarget) =
    setStatic(
        state,
        License.track(staticField(state), string(className(state.type))).cast(state.type),
    )

private object VipResources
