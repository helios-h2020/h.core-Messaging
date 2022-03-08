package eu.h2020.helios_social.core.messaging.crypto

import android.util.Base64
import android.util.Log
import com.google.protobuf.ByteString
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.RSAPrivateCrtKeySpec

class RsaPrivateKeyWriter {
    val TAG = "RsaPrivateKeyWriter"
    var components: MutableList<ByteArray> = mutableListOf()

    constructor(privateKey: RSAPrivateCrtKey) {
        val version: BigInteger = BigInteger("0")
        val versionAsn1: ByteArray = writeAsn1Integer(version)
        components.add(versionAsn1)

        val modulusAsn1 = writeAsn1Integer(privateKey.modulus)
        components.add(modulusAsn1)

        val publicExponentAsn1 = writeAsn1Integer(privateKey.publicExponent)
        components.add(publicExponentAsn1)

        val privateExponentAsn1 = writeAsn1Integer(privateKey.privateExponent)
        components.add(privateExponentAsn1)

        val prime1Asn1 = writeAsn1Integer(privateKey.primeP)
        components.add(prime1Asn1)

        val prime2Asn1 = writeAsn1Integer(privateKey.primeQ)
        components.add(prime2Asn1)

        val exponent1Asn1 = writeAsn1Integer(privateKey.primeExponentP)
        components.add(exponent1Asn1)

        val exponent2Asn1 = writeAsn1Integer(privateKey.primeExponentQ)
        components.add(exponent2Asn1)

        val coefficientAsn1 = writeAsn1Integer(privateKey.crtCoefficient)
        components.add(coefficientAsn1)
    }

    constructor(keyData: List<BigInteger>) {
        val version: BigInteger = BigInteger("0")
        val versionAsn1: ByteArray = writeAsn1Integer(version)
        components.add(versionAsn1)
        for (component in keyData) {
            val componentAsn1 = writeAsn1Integer(component)
            components.add(componentAsn1)
        }
    }

    fun createPrivateKeyString(): String {
        val data: ByteArray = writeAsn1Sequence(components)

        // Add go-libp2p protobuf header
        var privateKeyBuilder = HeliosLibp2pProtobuf.PrivateKey.newBuilder()
        privateKeyBuilder.setType(HeliosLibp2pProtobuf.KeyType.RSA)
        privateKeyBuilder.setData(ByteString.copyFrom(data))
        val fulldata: ByteArray = privateKeyBuilder.build().toByteArray()

        // Convert to Base64 and then to a string
        val b64data: ByteArray = Base64.encode(fulldata, Base64.NO_WRAP)
        val result = String(b64data, StandardCharsets.UTF_8)
        Log.d(TAG, "PrivateKeyProbuf: " + result)
        return result
    }

    /**
     * Encode length to ByteArray bytes
     *
     * Lengths are either a single byte (for small numbers, under 128), multiple bytes
     * (for larger numbers over 128), or "indefinite". This description is from
     *
     * https://coolaj86.com/articles/asn1-for-dummies/
     *
     * If the number is above 128, the first byte is the number of bytes of the length
     * (minus the first bit).
     *
     * 0x00 to 0x7F (0 - 127): this is the byte length of the value
     * 0x02 would mean the length is 2 bytes
     *
     * 0x80 to 0xFF: this is the length of the length
     * Example 0x8180 means the 1-byte length describes a 128-byte value
     * 0x81 & 0x7F = 0x01
     * 0x80 = 128
     * 0x820101 would mean the length is 257 bytes
     * 0x80 & 0x7F = 0x02
     * 0x0101 = 257 bytes
     *
     * 0x80 exactly: the length is "indefinite" (e.g., end-of-file)
     */
    private fun writeAsn1Length(length: Int): ByteArray {
        if (length < 128) {
            var lengthCode = ByteArray(1)
            lengthCode[0] = length.toByte()
            return lengthCode
        } else  if (length < 256) {
            var lengthCode = ByteArray(2)
            lengthCode[0] = (0x80 + 1).toByte()
            lengthCode[1] = length.toByte()
            return lengthCode
        } else if (length < 4096) {
            var lengthCode = ByteArray(3)
            lengthCode[0] = (0x80 + 2).toByte()
            lengthCode[1] = ((length shr 8) and 0xFF).toByte()
            lengthCode[2] = (length and 0xFF).toByte()
            return lengthCode
        } else {
            throw Exception("Long fields not supported")
        }
    }

    private fun writeAsn1Integer(value: BigInteger): ByteArray {
        val data: ByteArray = value.toByteArray()
        val lengthCode: ByteArray = writeAsn1Length(data.size)
        val tagCode: ByteArray = ByteArray(1)
        tagCode[0] = 0x02.toByte() // ASN.1 INTEGER tag
        var result: ByteArray = ByteArray(tagCode.size + lengthCode.size + data.size)
        var pos: Int  = 0
        System.arraycopy(tagCode, 0, result, pos, tagCode.size)
        pos += tagCode.size
        System.arraycopy(lengthCode, 0, result, pos, lengthCode.size)
        pos += lengthCode.size
        System.arraycopy(data, 0, result, pos, data.size)
        return result
    }

    private fun writeAsn1Sequence(values: List<ByteArray>): ByteArray {
        var length: Int = 0
        for (value in values) {
            length += value.size
        }
        val lengthCode: ByteArray = writeAsn1Length(length)
        val tagCode = ByteArray(1)
        tagCode[0] = 0x30.toByte() // ASN.1 SEQUENCE tag
        var result = ByteArray(tagCode.size + lengthCode.size + length)
        var pos: Int  = 0
        System.arraycopy(tagCode, 0, result, pos, tagCode.size)
        pos += tagCode.size
        System.arraycopy(lengthCode, 0, result, pos, lengthCode.size)
        pos += lengthCode.size
        for (value in values) {
            System.arraycopy(value, 0, result, pos, value.size)
            pos += value.size
        }
        return result
    }

}
