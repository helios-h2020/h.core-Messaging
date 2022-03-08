package eu.h2020.helios_social.core.messaging.crypto

import android.util.Base64
import java.lang.IllegalArgumentException
import java.math.BigInteger
import java.security.InvalidKeyException

/**
 * Parse RSA private key that is generated with go-libp2p HeliosAPI to RSA components.
 * The private key is a PKCS1 key using ASN.1 DER as specified in
 *
 * https://github.com/libp2p/specs/blob/master/peer-ids/peer-ids.md
 *
 * The key parser can be used to parse the private key to RSA key components that can
 * be used to create public key.
 */
class RsaPrivateKeyReader {
    // RSA private key components
    var modulus: BigInteger? = null          // n
    var publicExponent: BigInteger? = null   // e
    var privateExponent: BigInteger? = null  // d
    var prime1: BigInteger? = null           // p
    var prime2: BigInteger? = null           // q
    var exponent1: BigInteger? = null        // d mod (p-1)
    var exponent2: BigInteger? = null        // d mod (q-1)
    var coefficient: BigInteger? = null      // (inverse of q) mod p

    constructor(key: String) {
        val encodedPrivateKey: ByteArray = getKeyBytes(key)
        var integers: List<BigInteger> = parse(encodedPrivateKey)
        setComponents(integers)
    }

    constructor(keyBytes: ByteArray) {
        var integers: List<BigInteger> = parse(keyBytes)
        setComponents(integers)
    }

    fun getComponents(): List<BigInteger> {
        var result: MutableList<BigInteger> = mutableListOf()
        result.add(modulus!!)
        result.add(publicExponent!!)
        result.add(privateExponent!!)
        result.add(prime1!!)
        result.add(prime2!!)
        result.add(exponent1!!)
        result.add(exponent2!!)
        result.add(coefficient!!)
        return result
    }

    /**
     * Place parsed components from the list to correct data members.
     *
     * @throws IllegalArgumentException if the list of BigIntegers is too short
     */
    @Throws(IllegalArgumentException::class)
    private fun setComponents(integers: List<BigInteger>) {
        val count = integers.size
        if (count < 9) {
            throw IllegalArgumentException("Too few list items ($count)")
        }
        val version = integers.get(0).toInt()
        if (version != 0) {
            throw IllegalArgumentException("Version should be zero (was $version)")
        }
        modulus = integers.get(1)
        publicExponent = integers.get(2)
        privateExponent = integers.get(3)
        prime1 = integers.get(4)
        prime2 = integers.get(5)
        exponent1 = integers.get(6)
        exponent2 = integers.get(7)
        coefficient = integers.get(8)
    }

    /**
     * Base64 encoded data string is decoded and key data is returned.
     *
     * @param key Private key string from go-libp2p Heliosapi
     * @return Key data buffer
     * @throws IllegalArgumentException if base64 decoding failed
     */
    @Throws(IllegalArgumentException::class)
    private fun getKeyBytes(key: String): ByteArray {
        val buffer = Base64.decode(key, Base64.DEFAULT)
        val offset = 5 // The first bytes in the buffer should be skipped
        val buflen = buffer.size - offset
        var keydata: ByteArray = ByteArray(buflen)
        System.arraycopy(buffer, offset, keydata, 0, buflen)
        return keydata
    }

    /**
     * Bare-bones ASN.1 parser that can only deal with a structure that contains integers.
     * This can be used to parse the RSA private key format given in PKCS #1 and RFC 3447.
     * ASN.1 specification:
     *
     * RSAPrivateKey ::= SEQUENCE {
     *     version           Version,
     *     modulus           INTEGER,  -- n
     *     publicExponent    INTEGER,  -- e
     *     privateExponent   INTEGER,  -- d
     *     prime1            INTEGER,  -- p
     *     prime2            INTEGER,  -- q
     *     exponent1         INTEGER,  -- d mod (p-1)
     *     exponent2         INTEGER,  -- d mod (q-1)
     *     coefficient       INTEGER,  -- (inverse of q) mod p
     *    otherPrimeInfos   OtherPrimeInfos OPTIONAL
     *
     * The code is based on a code example by Joshua Davies
     * http://commandlinefanatic.com/cgi-bin/showarticle.cgi?article=art049
     *
     * @param b the bytes to be parsed as ASN.1 DER
     * @return integers an output array to which all integers encountered during the parse
     * will be appended in the order they're encountered.  It's up to the caller to determine
     * which is which.
     * @throws InvalidKeyException if ASN.1 parsing fails
     */
    @Throws(InvalidKeyException::class)
    private fun parse(b: ByteArray): List<BigInteger> {
        var integers: MutableList<BigInteger> = mutableListOf()
        var pos = 0
        while (pos < b.size) {
            val tag = b[pos++]
            var length = b[pos++].toInt()
            if (length and 0x80 != 0) {
                var extLen = 0
                for (i in 0 until (length and 0x7F)) {
                    extLen = extLen shl 8 or (b[pos++].toInt() and 0xFF)
                }
                length = extLen
            }
            val contents = ByteArray(length)
            System.arraycopy(b, pos, contents, 0, length)
            pos += length
            if (tag.toInt() == 0x30) {  // sequence
                var seqints: List<BigInteger> = parse(contents)
                integers.addAll(seqints)
            } else if (tag.toInt() == 0x02) {  // Integer
                val i = BigInteger(contents)
                integers.add(i)
            } else {
                throw InvalidKeyException("Unsupported ASN.1 tag " + tag)
            }
        }
        return integers
    }
}
