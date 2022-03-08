package eu.h2020.helios_social.core.messaging.crypto

import android.util.Base64
import android.util.Log
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.RSAKeyGenParameterSpec

class RsaKeyPairGenerator {
    private val TAG = "RsaKeyPairGenerator"
    private val DATA_OFFSET: Int = 26 // Skip the beginning
    private lateinit var keyPair: KeyPair
    private lateinit var privateKeyString: String
    private lateinit var publicKeyString: String

    constructor() {
        generateRsaKeyPair()
        createKeyStrings()
    }

    fun getPublicKey(): String {
        return publicKeyString
    }

    fun getPrivateKey(): String {
        return privateKeyString
    }

    fun getKeyPair(): KeyPair {
        return keyPair
    }

    private fun createKeyStrings() {
        val generatedKey: PrivateKey = keyPair.private
        val generatedKeyBytes: ByteArray = generatedKey.getEncoded()
        val keyData: ByteArray = ByteArray(generatedKeyBytes.size - DATA_OFFSET)
        System.arraycopy(
            generatedKeyBytes,
            DATA_OFFSET,
            keyData,
            0,
            generatedKeyBytes.size - DATA_OFFSET
        )
        val keyReader = RsaPrivateKeyReader(keyData)
        val components: List<BigInteger> = keyReader.getComponents()

        val privateKeyWriter = RsaPrivateKeyWriter(components)
        privateKeyString = privateKeyWriter.createPrivateKeyString()

        val publicKeyWriter = RsaPublicKeyWriter()
        publicKeyString =
            publicKeyWriter.createPublicKeyString(keyReader.modulus!!, keyReader.publicExponent!!)!!
    }

    private fun generateRsaKeyPair() {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        val keyGenSpec = RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4)
        keyGen.initialize(keyGenSpec)
        keyPair = keyGen.generateKeyPair()
    }

}
