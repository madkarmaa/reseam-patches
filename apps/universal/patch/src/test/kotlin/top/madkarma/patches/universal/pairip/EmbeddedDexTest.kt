// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.universal.pairip

import org.jf.dexlib2.AccessFlags
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.raw.HeaderItem
import org.jf.dexlib2.immutable.*
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x
import org.jf.dexlib2.writer.io.MemoryDataStore
import org.jf.dexlib2.writer.pool.DexPool
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Adler32

class EmbeddedDexTest {
    @Test
    fun extractsOriginalDexWithoutRewritingItsInstructions() {
        val bytes = dex()
        val recovered = extractEmbeddedDex(program(bytes))
        assertArrayEquals(bytes, recovered.bytes)
        assertEquals(setOf("Lexample/Recovered;"), recovered.classes)
        assertEquals("Lexample/Original;", recovered.methods.single().receiver)
        assertEquals("(Lexample/Original;)V", recovered.methods.single().prototype)
    }

    @Test
    fun rejectsAmbiguousPayloads() {
        assertThrows(IllegalStateException::class.java) { extractEmbeddedDex(program(dex() + dex())) }
    }

    @Test
    fun rejectsVmOnlyPayloads() {
        assertThrows(IllegalStateException::class.java) { extractEmbeddedDex(program(ByteArray(128))) }
    }

    @Test
    fun rejectsUnsupportedIapVersion() {
        val input = program(dex())
        littleEndian(input).putInt(4, 3)
        assertThrows(IllegalArgumentException::class.java) { extractEmbeddedDex(input) }
    }

    @Test
    fun rejectsTruncatedDex() {
        val input = dex().dropLast(1).toByteArray()
        assertThrows(IllegalArgumentException::class.java) { extractEmbeddedDex(program(input)) }
    }

    @Test
    fun rejectsDamagedChecksum() {
        val input = dex()
        input[HeaderItem.CHECKSUM_OFFSET] =
            (input[HeaderItem.CHECKSUM_OFFSET].toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) { extractEmbeddedDex(program(input)) }
    }

    @Test
    fun rejectsDamagedSignatureEvenWithAValidChecksum() {
        val input = dex()
        input[HeaderItem.SIGNATURE_OFFSET] =
            (input[HeaderItem.SIGNATURE_OFFSET].toInt() xor 1).toByte()
        val checksum = Adler32().apply {
            update(
                input,
                HeaderItem.CHECKSUM_DATA_START_OFFSET,
                input.size - HeaderItem.CHECKSUM_DATA_START_OFFSET
            )
        }.value
        littleEndian(input).putInt(HeaderItem.CHECKSUM_OFFSET, checksum.toInt())
        assertThrows(IllegalArgumentException::class.java) { extractEmbeddedDex(program(input)) }
    }

    private fun program(payload: ByteArray): ByteArray =
        byteArrayOf(0, 73, 65, 80, 2, 0, 0, 0) + ByteArray(19) + payload

    private fun littleEndian(bytes: ByteArray): ByteBuffer =
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    private fun dex(): ByteArray {
        val implementation = ImmutableMethodImplementation(
            1, listOf(ImmutableInstruction10x(Opcode.RETURN_VOID)), emptyList(), emptyList()
        )
        val method = ImmutableMethod(
            "Lexample/Recovered;",
            "run",
            listOf(ImmutableMethodParameter("Lexample/Original;", emptySet(), null)),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            implementation
        )
        val definition = ImmutableClassDef(
            "Lexample/Recovered;",
            AccessFlags.PUBLIC.value,
            "Ljava/lang/Object;",
            emptyList(),
            null,
            emptySet(),
            emptyList(),
            listOf(method)
        )
        val store = MemoryDataStore()
        try {
            DexPool.writeTo(store, ImmutableDexFile(Opcodes.forApi(26), listOf(definition)))
            return store.data
        } finally {
            store.close()
        }
    }
}
