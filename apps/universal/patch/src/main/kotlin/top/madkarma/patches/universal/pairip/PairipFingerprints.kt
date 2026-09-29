package top.madkarma.patches.universal.pairip

import app.reseam.patch.BytecodeScope
import app.reseam.patch.dex.AccessFlags
import app.reseam.patch.dex.DexClass
import app.reseam.patch.dex.isSet

const val PAIRIP_DESCRIPTOR_PREFIX = "Lcom/pairip/"
const val SIGNATURE_CHECK = "com.pairip.SignatureCheck"
const val VM_RUNNER = "com.pairip.VMRunner"
const val STARTUP_LAUNCHER = "com.pairip.StartupLauncher"
const val PAIRIP_APPLICATION = "com.pairip.application.Application"
const val LICENSE_CLIENT = "com.pairip.licensecheck.LicenseClient"
const val LICENSE_CLIENT_V3 = "com.pairip.licensecheck3.LicenseClientV3"
const val LICENSE_CONTENT_PROVIDER = "com.pairip.licensecheck.LicenseContentProvider"
const val LICENSE_ACTIVITY = "com.pairip.licensecheck.LicenseActivity"
const val PLAY_STORE = "com.android.vending"

val LICENSE_CLIENT_VOID_METHODS = arrayOf(
    "checkLicense",
    "initializeLicenseCheck",
    "connectToLicensingService",
    "retryOrThrow",
    "lambda\$retryOrThrow\$0",
    "startPaywallActivity",
    "startErrorDialogActivity",
    "scheduleAppShutdown",
)

val LICENSE_ACTIVITY_VOID_METHODS = arrayOf(
    "closeApp",
    "exitApp",
    "closeAllTasks",
    "showPaywallAndCloseApp",
    "showErrorDialog",
    "logAndShowErrorDialog",
)

fun noOp(
    classDef: DexClass,
    vararg methodNames: String,
): Int {
    var patched = 0
    for (methodName in methodNames) {
        classDef.methods.filter { it.name == methodName }.forEach {
            it.alwaysReturn()
            patched++
        }
    }
    return patched
}

fun returnTrue(
    classDef: DexClass,
    vararg methodNames: String,
): Int {
    var patched = 0
    for (methodName in methodNames) {
        classDef.methods.filter { it.name == methodName }.forEach {
            it.alwaysReturn(true)
            patched++
        }
    }
    return patched
}

fun forceLicenseResponseOk(classDef: DexClass): Int {
    val method = classDef.methods.firstOrNull { it.name == "processResponse" } ?: return 0
    val paramBase = method.registersSize - method.insSize
    val responseCode = if (method.isStatic) paramBase else paramBase + 1

    method.addInstructions(0) { const4(responseCode, 0) }
    return 1
}

fun disableRepeatedCheck(classDef: DexClass): Int {
    val flag = classDef.field("repeatedCheckEnabled") ?: return 0
    val clinit = classDef.methods.firstOrNull { it.name == "<clinit>" } ?: return 0
    val scratch = clinit.registersSize

    if (!clinit.growLocalRegisters(1)) return 0

    clinit.addInstructions(0) {
        const4(scratch, 0)
        sputBoolean(scratch, flag)
    }
    return 1
}

fun spoofInstallerChecks(scope: BytecodeScope): Int {
    var booleanDone = false
    var stringDone = false

    for (classDef in scope.classes) {
        if (classDef.descriptor.startsWith(PAIRIP_DESCRIPTOR_PREFIX)) continue

        for (method in classDef.methods) {
            if (method.parameterTypes.isNotEmpty()) continue
            if (method.indexOfFirstString(PLAY_STORE) == null) continue
            if (!AccessFlags.PRIVATE.isSet(method.info.accessFlags)) continue

            when (method.returnType) {
                "Z" -> {
                    if (!booleanDone) {
                        method.alwaysReturn(true)
                        booleanDone = true
                    }
                }

                "Ljava/lang/String;" -> {
                    if (!stringDone) {
                        method.alwaysReturn(PLAY_STORE)
                        stringDone = true
                    }
                }
            }
        }

        if (booleanDone && stringDone) break
    }

    return (if (booleanDone) 1 else 0) + (if (stringDone) 1 else 0)
}
