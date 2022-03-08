package eu.h2020.helios_social.core.messaging.crypto

import android.util.Base64
import android.util.Log
import com.google.protobuf.ByteString
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * RSA public key is written in a special way witn libp2p
 *
 * The binary format contains
 */
class RsaPublicKeyWriter {
    val TAG = "RsaPublicKeyWriter"

    fun createPublicKeyString(modulus: BigInteger, publicExponent: BigInteger): String? {
        // Generate first ASN.1 DER encoded public key data
        val publicKeySpec = RSAPublicKeySpec(modulus, publicExponent)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey: PublicKey = keyFactory.generatePublic(publicKeySpec)
        val rawdata: ByteArray = publicKey.getEncoded()

        // Create then PKIX X.509 data structures
        var x509EncodedKeySpec: X509EncodedKeySpec = X509EncodedKeySpec(rawdata)
        val pkixPublicKey: PublicKey = keyFactory.generatePublic(x509EncodedKeySpec)
        val data: ByteArray = pkixPublicKey.getEncoded()

        // Add go-libp2p protobuf header
        var publicKeyBuilder = HeliosLibp2pProtobuf.PublicKey.newBuilder()
        publicKeyBuilder.setType(HeliosLibp2pProtobuf.KeyType.RSA)
        publicKeyBuilder.setData(ByteString.copyFrom(data))
        val fulldata: ByteArray = publicKeyBuilder.build().toByteArray()

        // Convert to Base64 and then to a string
        val b64data: ByteArray = Base64.encode(fulldata, Base64.NO_WRAP)
        val result: String = String(b64data, StandardCharsets.UTF_8)
        Log.d(TAG, "PubKeyProbuf: " + result)
        return result
    }
}
