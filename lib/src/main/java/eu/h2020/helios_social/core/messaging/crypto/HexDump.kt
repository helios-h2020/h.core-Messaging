package eu.h2020.helios_social.core.messaging.crypto

import java.lang.RuntimeException
import java.lang.StringBuilder

/**
 * HexDump utility to debug print binary data.
 *
 * This is derived from the Apache 2.0 licensed Java code that is available in:
 *     https://github.com/mik3y/usb-serial-for-android/blob/v2.1.0/usbSerialExamples/src/main/java/com/hoho/android/usbserial/util/HexDump.java
 */
object HexDump {

    fun dumpHexString(array: ByteArray): String? {
        return dumpHexString(array, 0, array.size)
    }

    fun dumpHexString(array: ByteArray, offset: Int, length: Int): String? {
        val result = StringBuilder()
        val line = ByteArray(16)
        var lineIndex = 0
        result.append("\n0x")
        result.append(toHexString(offset))
        for (i in offset until offset + length) {
            if (lineIndex == 16) {
                result.append(" ")
                for (j in 0..15) {
                    if (line[j] > ' '.code && line[j] < '~'.code) {
                        result.append(String(line, j, 1))
                    } else {
                        result.append(".")
                    }
                }
                result.append("\n0x")
                result.append(toHexString(i))
                lineIndex = 0
            }
            val b = array[i]
            result.append(" ")
            result.append(HEX_DIGITS[b.toInt() ushr 4 and 0x0F])
            result.append(HEX_DIGITS[b.toInt() and 0x0F])
            line[lineIndex++] = b
        }
        if (lineIndex != 16) {
            var count = (16 - lineIndex) * 3
            count++
            for (i in 0 until count) {
                result.append(" ")
            }
            for (i in 0 until lineIndex) {
                if (line[i] > ' '.code && line[i] < '~'.code) {
                    result.append(String(line, i, 1))
                } else {
                    result.append(".")
                }
            }
        }
        return result.toString()
    }

    fun toHexString(b: Byte): String? {
        return toHexString(toByteArray(b))
    }

    fun toHexString(array: ByteArray): String? {
        return toHexString(array, 0, array.size)
    }

    fun toHexString(array: ByteArray, offset: Int, length: Int): String? {
        val buf = CharArray(length * 2)
        var bufIndex = 0
        for (i in offset until offset + length) {
            val b = array[i]
            buf[bufIndex++] = HEX_DIGITS[b.toInt() ushr 4 and 0x0F]
            buf[bufIndex++] = HEX_DIGITS[b.toInt() and 0x0F]
        }
        return String(buf)
    }

    fun toHexString(i: Int): String? {
        return toHexString(toByteArray(i))
    }

    fun toHexString(i: Short): String? {
        return toHexString(toByteArray(i))
    }

    fun toByteArray(b: Byte): ByteArray {
        val array = ByteArray(1)
        array[0] = b
        return array
    }

    fun toByteArray(i: Int): ByteArray {
        val array = ByteArray(4)
        array[3] = (i and 0xFF).toByte()
        array[2] = (i shr 8 and 0xFF).toByte()
        array[1] = (i shr 16 and 0xFF).toByte()
        array[0] = (i shr 24 and 0xFF).toByte()
        return array
    }

    fun toByteArray(i: Short): ByteArray {
        val array = ByteArray(2)
        array[1] = (i.toInt() and 0xFF).toByte()
        array[0] = (i.toInt() shr 8 and 0xFF).toByte()
        return array
    }

    fun hexStringToByteArray(hexString: String): ByteArray? {
        val length = hexString.length
        val buffer = ByteArray(length / 2)
        var i = 0
        while (i < length) {
            buffer[i / 2] = (toByte(hexString[i]) shl 4 or toByte(hexString[i + 1])).toByte()
            i += 2
        }
        return buffer
    }

    private fun toByte(c: Char): Int {
        if (c >= '0' && c <= '9') return c - '0'
        if (c >= 'A' && c <= 'F') return c - 'A' + 10
        if (c >= 'a' && c <= 'f') return c - 'a' + 10
        throw RuntimeException("Invalid hex char '$c'")
    }

    private val HEX_DIGITS = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    )
}
