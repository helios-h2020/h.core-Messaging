package eu.h2020.helios_social.core.messaging.crypto

import android.util.Base64
import java.lang.IllegalArgumentException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec

/**
 * Parse libp2p format RSA public key string
 */
class RsaPublicKeyReader {
    var publicExponent: BigInteger? = null
    var modulus: BigInteger? = null
    var publicKey: RSAPublicKey? = null

    constructor(key: String) {
        parse(key)
    }

    private fun parse(key: String) {
        val keydata: ByteArray = getKeyBytes(key)
        val x509EncodedKeySpec = X509EncodedKeySpec(keydata)
        val keyFactory = KeyFactory.getInstance("RSA")
        publicKey = keyFactory.generatePublic(x509EncodedKeySpec) as RSAPublicKey
        publicExponent = publicKey?.publicExponent
        modulus = publicKey?.modulus
    }

    @Throws(IllegalArgumentException::class)
    private fun getKeyBytes(key: String): ByteArray {
        val buffer = Base64.decode(key, Base64.DEFAULT)
        val offset = 5 // The first bytes in the buffer should be skipped
        val buflen = buffer.size - offset
        var keydata: ByteArray = ByteArray(buflen)
        System.arraycopy(buffer, offset, keydata, 0, buflen)
        return keydata
    }
}
