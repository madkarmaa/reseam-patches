// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.universal.pairip

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32
import org.jf.dexlib2.AccessFlags
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.dexbacked.raw.HeaderItem

internal data class LiftedMethod(
    val owner: String,
    val name: String,
    val parameters: List<String>,
    val returnType: String,
) {
    val prototype: String
        get() = "(${parameters.joinToString("")})$returnType"

    // Pairip's lifted static bodies take the original instance before its parameters.
    val receiver: String
        get() = parameters.first()
}

internal data class EmbeddedDex(
    val bytes: ByteArray,
    val classes: Set<String>,
    val methods: List<LiftedMethod>,
)

internal fun extractEmbeddedDex(program: ByteArray): EmbeddedDex {
    require(
        program.size >= 8 && program.copyOfRange(0, 4).contentEquals(byteArrayOf(0, 73, 65, 80)),
    ) {
        "Pairip DEX: startup asset is not an IAP program"
    }

    require(ByteBuffer.wrap(program).order(ByteOrder.LITTLE_ENDIAN).getInt(4) == 2) {
        "Pairip DEX: unsupported IAP format"
    }

    val payloads = mutableListOf<ByteArray>()

    var offset = 8
    while (offset <= program.size - HeaderItem.ITEM_SIZE) {
        if (!HeaderItem.verifyMagic(program, offset)) {
            offset++
            continue
        }

        val candidate = DexBackedDexFile(null, program, offset)

        val size = candidate.buffer.readSmallUint(HeaderItem.FILE_SIZE_OFFSET)
        require(size >= HeaderItem.ITEM_SIZE && size <= program.size - offset) {
            "Pairip DEX: embedded DEX extends beyond the startup asset"
        }

        val bytes = program.copyOfRange(offset, offset + size)
        validateDex(bytes)

        payloads += bytes
        offset += size
    }

    val bytes =
        payloads.singleOrNull()
            ?: error(
                "Pairip DEX: expected one embedded DEX, found ${payloads.size}; runtime-only payloads are unsupported",
            )

    val dex = DexBackedDexFile(null, bytes)
    val methods =
        dex.classes.flatMap { definition ->
            definition.methods.map { method ->
                require(
                    AccessFlags.STATIC.isSet(method.accessFlags) && method.implementation != null,
                ) {
                    "Pairip DEX: unsupported lifted method ${definition.type}->${method.name}"
                }

                val parameters = method.parameterTypes.map(CharSequence::toString)
                require(parameters.firstOrNull()?.startsWith("L") == true) {
                    "Pairip DEX: lifted method ${definition.type}->${method.name} has no receiver"
                }

                LiftedMethod(definition.type, method.name, parameters, method.returnType)
            }
        }

    require(methods.isNotEmpty()) { "Pairip DEX: embedded DEX has no lifted methods" }
    return EmbeddedDex(bytes, dex.classes.map { it.type }.toSet(), methods)
}

private fun validateDex(bytes: ByteArray) {
    val dex = DexBackedDexFile(null, bytes)

    val header = HeaderItem(dex)
    require(header.headerSize == HeaderItem.ITEM_SIZE) { "Pairip DEX: unsupported DEX header" }

    val checksum =
        Adler32()
            .apply {
                update(
                    bytes,
                    HeaderItem.CHECKSUM_DATA_START_OFFSET,
                    bytes.size - HeaderItem.CHECKSUM_DATA_START_OFFSET,
                )
            }
            .value
    require(
        checksum == dex.buffer.readInt(HeaderItem.CHECKSUM_OFFSET).toUInt().toLong(),
    ) {
        "Pairip DEX: invalid DEX checksum"
    }

    val digest =
        MessageDigest.getInstance("SHA-1")
            .digest(bytes.copyOfRange(HeaderItem.SIGNATURE_DATA_START_OFFSET, bytes.size))
    require(digest.contentEquals(header.signature)) { "Pairip DEX: invalid DEX signature" }
}
