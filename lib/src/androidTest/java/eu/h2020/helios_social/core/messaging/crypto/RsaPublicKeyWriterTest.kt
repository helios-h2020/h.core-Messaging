package eu.h2020.helios_social.core.messaging.crypto

import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

class RsaPublicKeyWriterTest {
    @Before
    fun setUp() {
    }

    @Test
    fun testPublicKeyWriting() {
        val modulusBigInt = BigInteger(modulus)
        val publicExponentBigInt = publicExponent.toBigInteger()
        val keyWriter = RsaPublicKeyWriter()
        var str: String? = keyWriter.createPublicKeyString(modulusBigInt, publicExponentBigInt)
        Assert.assertNotNull(str)
        Assert.assertEquals(publicKey, str)
    }

    @After
    fun tearDown() {
    }

    // *******************************************************************************************
    // Test data
    // *******************************************************************************************

    private val publicKey: String = "CAASpgIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCRyxN+ZC" +
            "soKqJfMxhtKJ7qy9214hoshuLnB+tnOu6AaRHcDhbrFRKzEc+nlatMYWz9S5AEnJWlKQbkGy3/nGoDYNFc" +
            "8i1Qr0h3lgw0RD6EzL+1SeD6Ch3ujLO9Opv3z4MfUp5mCbEwK7hpinxdY6qIApM/VH9+KCNfhmJseYnR8G" +
            "StwCSQW1yS9FffbyvVfKRrutfz9pr7TcQrOwkSXso4CowcMeL94Xi42qGI9d4WgOQPRgIluCQNTpZLF/wK" +
            "GytG4Heu0lPImYKlif8U5u+Ee2+dVUzN5nneiW238AkWAKK4sS8jBRODq9KEn1BzaBBT2N/8L7o84Ve5xm" +
            "sx8TX9AgMBAAE="

    private val modulus: ByteArray = byteArrayOf(0x00.toByte(), 0x91.toByte(), 0xcb.toByte(),
        0x13.toByte(), 0x7e.toByte(), 0x64.toByte(), 0x2b.toByte(), 0x28.toByte(), 0x2a.toByte(),
        0xa2.toByte(), 0x5f.toByte(), 0x33.toByte(), 0x18.toByte(), 0x6d.toByte(), 0x28.toByte(),
        0x9e.toByte(), 0xea.toByte(), 0xcb.toByte(), 0xdd.toByte(), 0xb5.toByte(), 0xe2.toByte(),
        0x1a.toByte(), 0x2c.toByte(), 0x86.toByte(), 0xe2.toByte(), 0xe7.toByte(), 0x07.toByte(),
        0xeb.toByte(), 0x67.toByte(), 0x3a.toByte(), 0xee.toByte(), 0x80.toByte(), 0x69.toByte(),
        0x11.toByte(), 0xdc.toByte(), 0x0e.toByte(), 0x16.toByte(), 0xeb.toByte(), 0x15.toByte(),
        0x12.toByte(), 0xb3.toByte(), 0x11.toByte(), 0xcf.toByte(), 0xa7.toByte(), 0x95.toByte(),
        0xab.toByte(), 0x4c.toByte(), 0x61.toByte(), 0x6c.toByte(), 0xfd.toByte(), 0x4b.toByte(),
        0x90.toByte(), 0x04.toByte(), 0x9c.toByte(), 0x95.toByte(), 0xa5.toByte(), 0x29.toByte(),
        0x06.toByte(), 0xe4.toByte(), 0x1b.toByte(), 0x2d.toByte(), 0xff.toByte(), 0x9c.toByte(),
        0x6a.toByte(), 0x03.toByte(), 0x60.toByte(), 0xd1.toByte(), 0x5c.toByte(), 0xf2.toByte(),
        0x2d.toByte(), 0x50.toByte(), 0xaf.toByte(), 0x48.toByte(), 0x77.toByte(), 0x96.toByte(),
        0x0c.toByte(), 0x34.toByte(), 0x44.toByte(), 0x3e.toByte(), 0x84.toByte(), 0xcc.toByte(),
        0xbf.toByte(), 0xb5.toByte(), 0x49.toByte(), 0xe0.toByte(), 0xfa.toByte(), 0x0a.toByte(),
        0x1d.toByte(), 0xee.toByte(), 0x8c.toByte(), 0xb3.toByte(), 0xbd.toByte(), 0x3a.toByte(),
        0x9b.toByte(), 0xf7.toByte(), 0xcf.toByte(), 0x83.toByte(), 0x1f.toByte(), 0x52.toByte(),
        0x9e.toByte(), 0x66.toByte(), 0x09.toByte(), 0xb1.toByte(), 0x30.toByte(), 0x2b.toByte(),
        0xb8.toByte(), 0x69.toByte(), 0x8a.toByte(), 0x7c.toByte(), 0x5d.toByte(), 0x63.toByte(),
        0xaa.toByte(), 0x88.toByte(), 0x02.toByte(), 0x93.toByte(), 0x3f.toByte(), 0x54.toByte(),
        0x7f.toByte(), 0x7e.toByte(), 0x28.toByte(), 0x23.toByte(), 0x5f.toByte(), 0x86.toByte(),
        0x62.toByte(), 0x6c.toByte(), 0x79.toByte(), 0x89.toByte(), 0xd1.toByte(), 0xf0.toByte(),
        0x64.toByte(), 0xad.toByte(), 0xc0.toByte(), 0x24.toByte(), 0x90.toByte(), 0x5b.toByte(),
        0x5c.toByte(), 0x92.toByte(), 0xf4.toByte(), 0x57.toByte(), 0xdf.toByte(), 0x6f.toByte(),
        0x2b.toByte(), 0xd5.toByte(), 0x7c.toByte(), 0xa4.toByte(), 0x6b.toByte(), 0xba.toByte(),
        0xd7.toByte(), 0xf3.toByte(), 0xf6.toByte(), 0x9a.toByte(), 0xfb.toByte(), 0x4d.toByte(),
        0xc4.toByte(), 0x2b.toByte(), 0x3b.toByte(), 0x09.toByte(), 0x12.toByte(), 0x5e.toByte(),
        0xca.toByte(), 0x38.toByte(), 0x0a.toByte(), 0x8c.toByte(), 0x1c.toByte(), 0x31.toByte(),
        0xe2.toByte(), 0xfd.toByte(), 0xe1.toByte(), 0x78.toByte(), 0xb8.toByte(), 0xda.toByte(),
        0xa1.toByte(), 0x88.toByte(), 0xf5.toByte(), 0xde.toByte(), 0x16.toByte(), 0x80.toByte(),
        0xe4.toByte(), 0x0f.toByte(), 0x46.toByte(), 0x02.toByte(), 0x25.toByte(), 0xb8.toByte(),
        0x24.toByte(), 0x0d.toByte(), 0x4e.toByte(), 0x96.toByte(), 0x4b.toByte(), 0x17.toByte(),
        0xfc.toByte(), 0x0a.toByte(), 0x1b.toByte(), 0x2b.toByte(), 0x46.toByte(), 0xe0.toByte(),
        0x77.toByte(), 0xae.toByte(), 0xd2.toByte(), 0x53.toByte(), 0xc8.toByte(), 0x99.toByte(),
        0x82.toByte(), 0xa5.toByte(), 0x89.toByte(), 0xff.toByte(), 0x14.toByte(), 0xe6.toByte(),
        0xef.toByte(), 0x84.toByte(), 0x7b.toByte(), 0x6f.toByte(), 0x9d.toByte(), 0x55.toByte(),
        0x4c.toByte(), 0xcd.toByte(), 0xe6.toByte(), 0x79.toByte(), 0xde.toByte(), 0x89.toByte(),
        0x6d.toByte(), 0xb7.toByte(), 0xf0.toByte(), 0x09.toByte(), 0x16.toByte(), 0x00.toByte(),
        0xa2.toByte(), 0xb8.toByte(), 0xb1.toByte(), 0x2f.toByte(), 0x23.toByte(), 0x05.toByte(),
        0x13.toByte(), 0x83.toByte(), 0xab.toByte(), 0xd2.toByte(), 0x84.toByte(), 0x9f.toByte(),
        0x50.toByte(), 0x73.toByte(), 0x68.toByte(), 0x10.toByte(), 0x53.toByte(), 0xd8.toByte(),
        0xdf.toByte(), 0xfc.toByte(), 0x2f.toByte(), 0xba.toByte(), 0x3c.toByte(), 0xe1.toByte(),
        0x57.toByte(), 0xb9.toByte(), 0xc6.toByte(), 0x6b.toByte(), 0x31.toByte(), 0xf1.toByte(),
        0x35.toByte(), 0xfd.toByte())

    private val publicExponent: Int = 0x10001
}
