package com.example.fieldtrack.designsystem

import com.example.fieldtrack.designsystem.qr.QrCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The QR code is our own, so it is checked against the standard's own numbers, and every code
 * made here is read back square by square: format, mask, error correction, and the text.
 */
class QrCodeTest {

    @Test fun `error correction matches the standard's worked example`() {
        // "HELLO WORLD" at version 1-M, as the data codewords the standard's example builds.
        val data = intArrayOf(32, 91, 11, 120, 209, 114, 220, 77, 67, 64, 236, 17, 236, 17, 236, 17)
            .map { it.toByte() }.toByteArray()
        val ecc = QrCode.reedSolomonRemainder(data, QrCode.reedSolomonDivisor(10))
        assertArrayEquals(
            intArrayOf(196, 35, 39, 119, 235, 215, 231, 226, 93, 23).map { it.toByte() }.toByteArray(),
            ecc,
        )
    }

    @Test fun `format and version bits are the standard's`() {
        assertEquals(0b111011111000100, QrCode.formatBits(QrCode.Level.Low, 0))
        assertEquals(0b101010000010010, QrCode.formatBits(QrCode.Level.Medium, 0))
        assertEquals(0b011010101011111, QrCode.formatBits(QrCode.Level.Quartile, 0))
        assertEquals(0b001011010001001, QrCode.formatBits(QrCode.Level.High, 0))
        assertEquals(0b000111110010010100, QrCode.versionBits(7))
    }

    @Test fun `capacities are the standard's`() {
        assertEquals(
            listOf(16, 28, 44, 64, 86, 108, 124, 154, 182, 216),
            (1..10).map { QrCode.dataCodewords(it, QrCode.Level.Medium) },
        )
        assertEquals(listOf(6, 22, 38), QrCode.alignmentPositions(7).toList())
    }

    @Test fun `a UPI link reads back exactly`() {
        val text = "upi://pay?pa=harpalagro@okaxis&pn=Harpal%20Agro%20Works&am=4000.00&cu=INR&tn=Ramesh%20Singh"
        readBack(QrCode.encode(text), text)
    }

    @Test fun `a long text in a big version with several blocks reads back exactly`() {
        val text = "upi://pay?pa=a-very-long-business-name@bank&pn=" + "Gurmeet Farm Works ".repeat(12) + "&am=1.00&cu=INR"
        val code = QrCode.encode(text)
        assert(code.version >= 7) { "meant to exercise version bits, got ${code.version}" }
        readBack(code, text)
        for (level in QrCode.Level.entries) readBack(QrCode.encode(text, level), text)
    }

    /** Reads a code the way a scanner would, given only the grid, and checks everything on the way. */
    private fun readBack(code: QrCode, expected: String) {
        val size = code.size
        val m = code.modules
        assertEquals(code.version * 4 + 17, size)

        // Format bits, both copies, against the 32 valid words.
        var first = 0
        for (i in 0..5) if (m[i][8]) first = first or (1 shl i)
        if (m[7][8]) first = first or (1 shl 6)
        if (m[8][8]) first = first or (1 shl 7)
        if (m[8][7]) first = first or (1 shl 8)
        for (i in 9 until 15) if (m[8][14 - i]) first = first or (1 shl i)
        var second = 0
        for (i in 0 until 8) if (m[8][size - 1 - i]) second = second or (1 shl i)
        for (i in 8 until 15) if (m[size - 15 + i][8]) second = second or (1 shl i)
        assertEquals("the two format copies agree", first, second)
        val (level, mask) = QrCode.Level.entries.flatMap { l -> (0 until 8).map { l to it } }
            .single { (l, mk) -> QrCode.formatBits(l, mk) == first }
        assertEquals(code.level, level)
        assertEquals(code.mask, mask)
        assertEquals("the dark module", true, m[size - 8][8])

        // The codewords, in the zigzag, unmasked.
        val bits = ArrayList<Boolean>()
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) for (j in 0..1) {
                val x = right - j
                val upward = ((right + 1) and 2) == 0
                val y = if (upward) size - 1 - vert else vert
                if (!code.isFunction[y][x]) bits += m[y][x] xor QrCode.maskHit(mask, x, y)
            }
            right -= 2
        }
        val raw = QrCode.rawDataModules(code.version) / 8
        val codewords = IntArray(raw) { i -> (0 until 8).fold(0) { acc, j -> (acc shl 1) or (if (bits[i * 8 + j]) 1 else 0) } }

        // Back into blocks; every block's correction bytes must be right for its data.
        val numBlocks = QrCode.eccBlocks(code.version, level)
        val eccLen = QrCode.eccPerBlock(code.version, level)
        val numShort = numBlocks - raw % numBlocks
        val shortLen = raw / numBlocks
        val blocks = List(numBlocks) { ArrayList<Int>() }
        var k = 0
        for (i in 0 until shortLen + 1) for (j in 0 until numBlocks) {
            // The one place a short block has no byte: where the long blocks carry an extra data byte.
            if (i == shortLen - eccLen && j < numShort) continue
            blocks[j] += codewords[k++]
        }
        val data = ArrayList<Int>()
        val divisor = QrCode.reedSolomonDivisor(eccLen)
        for (b in blocks) {
            val dat = b.dropLast(eccLen)
            val ecc = b.takeLast(eccLen)
            val expectedEcc = QrCode.reedSolomonRemainder(dat.map { it.toByte() }.toByteArray(), divisor).map { it.toInt() and 0xFF }
            assertEquals("a block's correction bytes", expectedEcc, ecc)
            data += dat
        }

        // Mode, length, bytes.
        val stream = data.flatMap { v -> (7 downTo 0).map { (v ushr it) and 1 } }
        fun read(from: Int, count: Int) = (from until from + count).fold(0) { acc, i -> (acc shl 1) or stream[i] }
        assertEquals("byte mode", 0b0100, read(0, 4))
        val countBits = if (code.version <= 9) 8 else 16
        val length = read(4, countBits)
        val bytes = ByteArray(length) { read(4 + countBits + it * 8, 8).toByte() }
        assertEquals(expected, String(bytes, Charsets.UTF_8))
    }
}
