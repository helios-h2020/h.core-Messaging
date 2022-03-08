package eu.h2020.helios_social.core.messaging.crypto

import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

class RsaKeyPairGeneratorTest {

    @Before
    fun setUp() {
    }

    @Test
    fun testSignAndVerify() {
        val keyGen = RsaKeyPairGenerator()
        val keyPair = keyGen.getKeyPair()
        val privateKey: RSAPrivateKey = keyPair.private as RSAPrivateKey
        val publicKey: RSAPublicKey = keyPair.public as RSAPublicKey

        val signer: Signature = Signature.getInstance("SHA1withRSA")
        signer.initSign(privateKey)
        signer.update(testData.toByteArray())
        val signature: ByteArray = signer.sign()

        val verifier: Signature = Signature.getInstance("SHA1withRSA")
        verifier.initVerify(publicKey)
        verifier.update(testData.toByteArray())
        val result: Boolean = verifier.verify(signature)

        Assert.assertEquals(true, result)
    }

    @After
    fun tearDown() {
    }

    // *******************************************************************************************
    // Test data
    // *******************************************************************************************

    private val testData: String = "The quick brown fox jumps over the lazy dog"
}
