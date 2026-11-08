package com.example.fieldtrack.designsystem.qr

/**
 * A QR code, made here rather than by a library.
 *
 * Byte mode only (a UPI link is plain text), every version from 1 to 40, any of the four
 * error-correction levels, and the mask chosen by the standard's penalty rules. Follows ISO/IEC
 * 18004 the way the well-known reference implementations do; the tests check the error
 * correction against the standard's worked example and read every code back.
 *
 * [modules] is row by row: `modules[y][x]` true for a dark square.
 */
class QrCode private constructor(
    val version: Int,
    val level: Level,
    val mask: Int,
    val modules: Array<BooleanArray>,
    /** Which squares are fixed by the format rather than carrying data. For the tests. */
    internal val isFunction: Array<BooleanArray>,
) {
    val size: Int get() = modules.size

    /** How much damage a code can take: a quarter of a Medium code may be covered and it still reads. */
    enum class Level(internal val formatBits: Int, internal val ordinal_: Int) {
        Low(1, 0), Medium(0, 1), Quartile(3, 2), High(2, 3)
    }

    companion object {
        /** Encodes [text] as UTF-8 in the smallest version that holds it at [level]. */
        fun encode(text: String, level: Level = Level.Medium): QrCode = encode(text.toByteArray(Charsets.UTF_8), level)

        fun encode(data: ByteArray, level: Level = Level.Medium): QrCode {
            var version = 1
            while (true) {
                val capacityBits = dataCodewords(version, level) * 8
                val usedBits = 4 + countBits(version) + data.size * 8
                if (usedBits <= capacityBits) break
                version++
                require(version <= 40) { "Too long for a QR code" }
            }
            // The bit stream: mode, length, the bytes, a terminator, then padding to fill.
            val bits = BitBuffer()
            bits.append(0b0100, 4)
            bits.append(data.size, countBits(version))
            data.forEach { bits.append(it.toInt() and 0xFF, 8) }
            val capacity = dataCodewords(version, level) * 8
            bits.append(0, minOf(4, capacity - bits.size))
            bits.append(0, (8 - bits.size % 8) % 8)
            var pad = 0xEC
            while (bits.size < capacity) {
                bits.append(pad, 8)
                pad = pad xor 0xEC xor 0x11
            }
            val codewords = ByteArray(bits.size / 8) { i ->
                var b = 0
                for (j in 0 until 8) b = (b shl 1) or (if (bits[i * 8 + j]) 1 else 0)
                b.toByte()
            }
            return build(version, level, codewords)
        }

        private fun countBits(version: Int) = if (version <= 9) 8 else 16

        private fun build(version: Int, level: Level, dataCodewords: ByteArray): QrCode {
            val size = version * 4 + 17
            val modules = Array(size) { BooleanArray(size) }
            val isFunction = Array(size) { BooleanArray(size) }
            val grid = Grid(size, modules, isFunction)
            grid.drawFunctionPatterns(version, level)
            val all = addEccAndInterleave(version, level, dataCodewords)
            grid.drawCodewords(all)

            // Every mask, scored by the standard's penalty rules; the lowest wins.
            var best = 0
            var bestScore = Int.MAX_VALUE
            for (m in 0 until 8) {
                grid.applyMask(m)
                grid.drawFormatBits(level, m)
                val score = grid.penalty()
                if (score < bestScore) { bestScore = score; best = m }
                grid.applyMask(m) // undo: a mask is its own inverse
            }
            grid.applyMask(best)
            grid.drawFormatBits(level, best)
            return QrCode(version, level, best, modules, isFunction)
        }

        // ------------------------------------------------------------ the standard's tables

        private val ECC_PER_BLOCK = arrayOf(
            intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),
            intArrayOf(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            intArrayOf(-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
        )

        private val ECC_BLOCKS = arrayOf(
            intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),
            intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),
            intArrayOf(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68),
            intArrayOf(-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81),
        )

        internal fun eccPerBlock(version: Int, level: Level) = ECC_PER_BLOCK[level.ordinal_][version]
        internal fun eccBlocks(version: Int, level: Level) = ECC_BLOCKS[level.ordinal_][version]

        /** Squares left for data and error correction once the fixed patterns are drawn. */
        internal fun rawDataModules(version: Int): Int {
            var result = (16 * version + 128) * version + 64
            if (version >= 2) {
                val numAlign = version / 7 + 2
                result -= (25 * numAlign - 10) * numAlign - 55
                if (version >= 7) result -= 36
            }
            return result
        }

        internal fun dataCodewords(version: Int, level: Level): Int =
            rawDataModules(version) / 8 - eccPerBlock(version, level) * eccBlocks(version, level)

        internal fun alignmentPositions(version: Int): IntArray {
            if (version == 1) return IntArray(0)
            val numAlign = version / 7 + 2
            val step = if (version == 32) 26 else (version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2
            val result = IntArray(numAlign)
            result[0] = 6
            var pos = version * 4 + 17 - 7
            for (i in numAlign - 1 downTo 1) { result[i] = pos; pos -= step }
            return result
        }

        // ------------------------------------------------------------ error correction

        /** Splits the data into blocks, adds each block's correction bytes, and interleaves them. */
        internal fun addEccAndInterleave(version: Int, level: Level, data: ByteArray): ByteArray {
            val numBlocks = eccBlocks(version, level)
            val blockEccLen = eccPerBlock(version, level)
            val rawCodewords = rawDataModules(version) / 8
            val numShortBlocks = numBlocks - rawCodewords % numBlocks
            val shortBlockLen = rawCodewords / numBlocks
            val divisor = reedSolomonDivisor(blockEccLen)
            val blocks = ArrayList<ByteArray>()
            var k = 0
            for (i in 0 until numBlocks) {
                val datLen = shortBlockLen - blockEccLen + if (i < numShortBlocks) 0 else 1
                val dat = data.copyOfRange(k, k + datLen)
                k += datLen
                val ecc = reedSolomonRemainder(dat, divisor)
                // Short blocks get a placeholder byte so every block lines up; it is skipped below.
                val block = if (i < numShortBlocks) dat + byteArrayOf(0) + ecc else dat + ecc
                blocks += block
            }
            val result = ArrayList<Byte>(rawCodewords)
            for (i in 0 until blocks[0].size) {
                for (j in blocks.indices) {
                    if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) result += blocks[j][i]
                }
            }
            return result.toByteArray()
        }

        internal fun reedSolomonDivisor(degree: Int): ByteArray {
            val result = ByteArray(degree)
            result[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in 0 until degree) {
                    result[j] = gfMultiply(result[j].toInt() and 0xFF, root).toByte()
                    if (j + 1 < degree) result[j] = (result[j].toInt() xor result[j + 1].toInt()).toByte()
                }
                root = gfMultiply(root, 0x02)
            }
            return result
        }

        internal fun reedSolomonRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
            val result = ByteArray(divisor.size)
            for (b in data) {
                val factor = (b.toInt() xor result[0].toInt()) and 0xFF
                System.arraycopy(result, 1, result, 0, result.size - 1)
                result[result.size - 1] = 0
                for (i in result.indices) {
                    result[i] = (result[i].toInt() xor gfMultiply(divisor[i].toInt() and 0xFF, factor)).toByte()
                }
            }
            return result
        }

        /** Multiplication in GF(2^8) over the QR polynomial 0x11D. */
        internal fun gfMultiply(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }

        /** The 15 format bits for a level and mask, error-corrected and masked as the standard says. */
        internal fun formatBits(level: Level, mask: Int): Int {
            val data = (level.formatBits shl 3) or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            return ((data shl 10) or rem) xor 0x5412
        }

        internal fun versionBits(version: Int): Int {
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            return (version shl 12) or rem
        }

        internal fun maskHit(mask: Int, x: Int, y: Int): Boolean = when (mask) {
            0 -> (x + y) % 2 == 0
            1 -> y % 2 == 0
            2 -> x % 3 == 0
            3 -> (x + y) % 3 == 0
            4 -> (x / 3 + y / 2) % 2 == 0
            5 -> x * y % 2 + x * y % 3 == 0
            6 -> (x * y % 2 + x * y % 3) % 2 == 0
            7 -> ((x + y) % 2 + x * y % 3) % 2 == 0
            else -> error("No such mask")
        }
    }

    /** Draws onto the module grid. */
    private class Grid(val size: Int, val modules: Array<BooleanArray>, val isFunction: Array<BooleanArray>) {

        fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            isFunction[y][x] = true
        }

        fun drawFunctionPatterns(version: Int, level: Level) {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
            val align = alignmentPositions(version)
            val last = align.size - 1
            for (i in align.indices) for (j in align.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == last) || (i == last && j == 0)) continue
                alignment(align[i], align[j])
            }
            drawFormatBits(level, 0) // reserved now, written for real once the mask is chosen
            if (version >= 7) {
                val bits = versionBits(version)
                for (i in 0 until 18) {
                    val bit = (bits ushr i) and 1 != 0
                    val a = size - 11 + i % 3
                    val b = i / 3
                    set(a, b, bit)
                    set(b, a, bit)
                }
            }
        }

        private fun finder(x: Int, y: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val xx = x + dx
                val yy = y + dy
                if (xx !in 0 until size || yy !in 0 until size) continue
                val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                set(xx, yy, dist != 2 && dist != 4)
            }
        }

        private fun alignment(x: Int, y: Int) {
            for (dy in -2..2) for (dx in -2..2) {
                set(x + dx, y + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
            }
        }

        fun drawFormatBits(level: Level, mask: Int) {
            val bits = formatBits(level, mask)
            fun bit(i: Int) = (bits ushr i) and 1 != 0
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6))
            set(8, 8, bit(7))
            set(7, 8, bit(8))
            for (i in 9 until 15) set(14 - i, 8, bit(i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true) // the dark module, always dark
        }

        /** Lays the codewords in the zigzag the standard reads them in, skipping the fixed squares. */
        fun drawCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!isFunction[y][x] && i < data.size * 8) {
                            modules[y][x] = ((data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) != 0
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                if (!isFunction[y][x] && maskHit(mask, x, y)) modules[y][x] = !modules[y][x]
            }
        }

        /** The standard's four penalties: long runs, 2x2 blocks, finder look-alikes, and balance. */
        fun penalty(): Int {
            var result = 0
            fun runs(get: (Int, Int) -> Boolean) {
                for (a in 0 until size) {
                    var runColor = false
                    var run = 0
                    for (b in 0 until size) {
                        val c = get(a, b)
                        if (b > 0 && c == runColor) {
                            run++
                            if (run == 5) result += 3 else if (run > 5) result += 1
                        } else {
                            runColor = c
                            run = 1
                        }
                    }
                }
            }
            runs { y, x -> modules[y][x] }
            runs { x, y -> modules[y][x] }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += 3
            }
            val pattern = booleanArrayOf(true, false, true, true, true, false, true)
            fun finderLike(get: (Int, Int) -> Boolean) {
                for (a in 0 until size) for (b in 0..size - 7) {
                    if ((0 until 7).all { get(a, b + it) == pattern[it] }) {
                        val lightBefore = (1..4).all { b - it < 0 || !get(a, b - it) }
                        val lightAfter = (1..4).all { b + 6 + it >= size || !get(a, b + 6 + it) }
                        if (lightBefore || lightAfter) result += 40
                    }
                }
            }
            finderLike { y, x -> modules[y][x] }
            finderLike { x, y -> modules[y][x] }
            val dark = modules.sumOf { row -> row.count { it } }
            val total = size * size
            val k = (kotlin.math.abs(dark * 20 - total * 10) + total - 1) / total - 1
            result += k.coerceAtLeast(0) * 10
            return result
        }
    }

    private class BitBuffer {
        private val bits = ArrayList<Boolean>()
        val size: Int get() = bits.size
        operator fun get(i: Int) = bits[i]
        fun append(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits += ((value ushr i) and 1) != 0
        }
    }
}
