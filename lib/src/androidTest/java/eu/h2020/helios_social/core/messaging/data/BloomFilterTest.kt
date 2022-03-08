package eu.h2020.helios_social.core.messaging.data

import android.util.Log
import com.google.common.hash.BloomFilter
import junit.framework.TestCase
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.time.ZonedDateTime
import java.util.*
import java.util.stream.Collectors

/**
 * Bloom filter is used to list missing messages.
 */
class BloomFilterTest {

    lateinit var espooTopic: HeliosConversation
    lateinit var existingTopic: HeliosConversation
    lateinit var evenTopic: HeliosConversation
    lateinit var oddTopic: HeliosConversation

    @Before
    fun setUp() {
        espooTopic = HeliosConversation()
        for (i in testMessages.indices) {
            val message = buildHeliosMessagePart(testMessages[i])
            espooTopic.addMessage(message)
        }

        existingTopic = HeliosConversation()
        for (i in existingMessages.indices) {
            val message = buildHeliosMessagePart(existingMessages[i])
            existingTopic.addMessage(message)
        }

        evenTopic = HeliosConversation()
        oddTopic = HeliosConversation()
        for (i in testMessages.indices) {
            val message = buildHeliosMessagePart(testMessages[i])
            if (i % 2 == 0) {
                evenTopic.addMessage(message)
            } else {
                oddTopic.addMessage(message)
            }
        }
    }

    @Test
    fun testBloomMissingListIsNotEmpty() {
        var newYearDay = ZonedDateTime.parse("2021-01-01T10:41:04.528+03:00[Europe/Helsinki]")
        var bloomFilter: BloomFilter<String> = existingTopic.getMessageBloom(newYearDay)

        val recentMessages: List<HeliosMessagePart> = espooTopic.getMessagesAfter(newYearDay)
        val missingMessages: List<HeliosMessagePart> = recentMessages
            .stream()
            .filter({ storedMsg: HeliosMessagePart -> storedMsg.messageType == HeliosMessagePart.MessagePartType.MESSAGE })
            .filter({ storedMsg: HeliosMessagePart ->
                !bloomFilter.mightContain(
                    storedMsg.getUuid()
                )
            })
            .collect(Collectors.toList())
        Assert.assertTrue(missingMessages.size > 0)
    }

    @Test
    fun testBloomAllMissingsAreListed() {
        var newYearDay = ZonedDateTime.parse("2021-01-01T10:41:04.528+03:00[Europe/Helsinki]")
        var bloomFilter: BloomFilter<String> = existingTopic.getMessageBloom(newYearDay)

        val recentMessages: List<HeliosMessagePart> = espooTopic.getMessagesAfter(newYearDay)
        val missingMessages: List<HeliosMessagePart> = recentMessages
            .stream()
            .filter({ storedMsg: HeliosMessagePart -> storedMsg.messageType == HeliosMessagePart.MessagePartType.MESSAGE })
            .filter({ storedMsg: HeliosMessagePart ->
                !bloomFilter.mightContain(
                    storedMsg.getUuid()
                )
            })
            .collect(Collectors.toList())

        for (i in 0..28) {
            val uuid: String = testMessages[i][0]
            val found = uuidFoundFromTheList(uuid, missingMessages)
            Assert.assertTrue(found)
        }
    }

    @Test
    fun testBloomNonExistingIsNotFound() {
        var newYearDay = ZonedDateTime.parse("2021-01-01T10:41:04.528+03:00[Europe/Helsinki]")
        var bloomFilter: BloomFilter<String> = existingTopic.getMessageBloom(newYearDay)

        val recentMessages: List<HeliosMessagePart> = espooTopic.getMessagesAfter(newYearDay)
        val missingMessages: List<HeliosMessagePart> = recentMessages
            .stream()
            .filter({ storedMsg: HeliosMessagePart -> storedMsg.messageType == HeliosMessagePart.MessagePartType.MESSAGE })
            .filter({ storedMsg: HeliosMessagePart ->
                !bloomFilter.mightContain(
                    storedMsg.getUuid()
                )
            })
            .collect(Collectors.toList())

        val found = uuidFoundFromTheList("Should not be found", missingMessages)
        Assert.assertTrue(!found)
    }

    @Test
    fun testBloomOldMesageIsNotFound() {
        var threshold = ZonedDateTime.parse("2021-09-11T07:27:49.552+03:00[Europe/Helsinki]")
        var bloomFilter: BloomFilter<String> = existingTopic.getMessageBloom(threshold)

        val recentMessages: List<HeliosMessagePart> = espooTopic.getMessagesAfter(threshold)
        val missingMessages: List<HeliosMessagePart> = recentMessages
            .stream()
            .filter({ storedMsg: HeliosMessagePart -> storedMsg.messageType == HeliosMessagePart.MessagePartType.MESSAGE })
            .filter({ storedMsg: HeliosMessagePart ->
                !bloomFilter.mightContain(
                    storedMsg.getUuid()
                )
            })
            .collect(Collectors.toList())

        val found = uuidFoundFromTheList("87911589-0c24-4b7a-8d36-dbf5b26cc2f4", missingMessages)
        Assert.assertTrue(!found)
    }

    @Test
    fun testBloomDisjointSets() {
        var newYearDay = ZonedDateTime.parse("2021-01-01T10:41:04.528+03:00[Europe/Helsinki]")
        var bloomFilter: BloomFilter<String> = evenTopic.getMessageBloom(newYearDay)

        val recentMessages: List<HeliosMessagePart> = oddTopic.getMessagesAfter(newYearDay)
        val missingMessages: List<HeliosMessagePart> = recentMessages
            .stream()
            .filter({ storedMsg: HeliosMessagePart -> storedMsg.messageType == HeliosMessagePart.MessagePartType.MESSAGE })
            .filter({ storedMsg: HeliosMessagePart ->
                !bloomFilter.mightContain(
                    storedMsg.getUuid()
                )
            })
            .collect(Collectors.toList())

        for (i in testMessages.indices) {
            if (i % 2 == 1) {
                val uuid: String = testMessages[i][0]
                val found = uuidFoundFromTheList(uuid, missingMessages)
                Assert.assertTrue(found)
            }
        }
    }

    @After
    fun tearDown() {
    }

    private fun buildHeliosMessagePart(arr: Array<String>) : HeliosMessagePart {
        var message = HeliosMessagePart(arr[1], arr[3], arr[2], arr[4], arr[5], null)
        message.uuid = arr[0]
        return message
    }

    private fun uuidFoundFromTheList(uuid: String, list: List<HeliosMessagePart>): Boolean {
        for (item in list) {
            if (item.uuid == uuid) {
                return true
            }
        }
        return false
    }

    private val testMessages = arrayOf(
        arrayOf<String>("87911589-0c24-4b7a-8d36-dbf5b26cc2f4", "chat",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-07T10:41:04.528+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("81fa073e-b0ba-4376-839b-4570f3847883", "Otaniemi",
            "3c94e78c-c550-46e3-b1bb-44dfeb9657d9", "mjk30", "Espoo",
            "2021-09-07T11:04:16.882+03:00[Europe/Helsinki]", "MESSAGE",
            "QmVASM2rtSisTCDc2Lu2CuWJnB7sXAhgC54o2HXLrHd5jY"),
        arrayOf<String>("0955f88b-64f5-4015-b92e-1d948d14bbdb", "Taas toimii",
            "dd4fa3fe-05bb-48a3-810e-5deb4f119724", "mjk29", "Espoo",
            "2021-09-07T11:04:26.459+03:00[Europe/Helsinki]", "MESSAGE",
            "QmaR8ZLi7sh9psjqyXLLDG1svmpiiaanP8RuEFw1DUwtL2"),
        arrayOf<String>("6a49844c-5d01-4a6a-a717-d9da12972d80", "hei",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-07T11:04:55.668+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("63952850-2679-451c-8e19-a9af67cb84b6", "Lintuvaara",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-08T14:47:25.997+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("ed834254-c52b-487d-96ca-66fd2e6200f6", "Kilo",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-10T06:22:55.205+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("5a8662ab-9b2a-496b-8cca-5ca1f75f6344", "Karamalmi",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-10T06:24:04.138+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("cc992797-a75d-4bb7-829e-0a36a70c87f4", "Tuomarila",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-10T07:41:11.845+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("0bcd4ac8-9972-497f-9670-a1903e3aadb1", "Espoon keskus",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-10T07:42:46.16+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("4e174cd5-4186-47df-a84f-50de47933cf9", "Latokaski",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-10T07:43:25.33+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("6071a3b8-12bd-4958-ba66-e2ef75c96035", "Nöykkiö",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-10T07:43:47.706+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("e1574433-2690-4e1d-8ea9-a1ac1e95acf0", "Kaitaa",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-10T07:44:05.733+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("b78ac495-dc55-4918-9890-888fdc8763e6", "Matinkylä",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-10T07:44:59.221+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("8c0fbd86-f4c1-48c3-a050-5fc247370d2e", "Laajalahti",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-10T07:56:43.751+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("3e144a1e-39e4-4536-afb6-3d986542b612", "Uusimäki",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-10T07:57:17.935+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("e923b196-4db2-416e-8403-454974e33dce", "Datakeskus",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-11T07:04:07.866+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("c405d858-8691-4ce8-ba24-1c8a31278cb0", "Hakametsä",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-11T07:27:49.552+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("37f299b1-4f99-4255-859c-1f65d3f5475c", "Ylöjärvi",
            "e164196e-9d15-49a8-abfc-ad698aa55a91", "mjk29w", "Espoo",
            "2021-09-13T08:38:17.177+03:00[Europe/Helsinki]", "MESSAGE",
            "QmRXb6Ai5N5ZXhxViEKTwFyxmjKgTiHHePmUux7qvwMa2Y"),
        arrayOf<String>("29b8f1d5-c598-4de7-a790-d2c2b5886c26", "Näsinneula",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-13T08:38:41.087+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("85069cd1-6ca8-4488-8147-b304447fb732", "Otaniemi",
            "45b260bc-d298-4ac7-bd1f-1c4676a291ca", "mjk30w", "Espoo",
            "2021-09-13T10:36:17.88+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdUsmftFxpanosHGEcrxjRiz3qZ8oPEZ7sTR4xf8aCTXS"),
        arrayOf<String>("8b7327b1-54d9-4c59-bbbc-c0c468478e53", "Tapiola",
            "e164196e-9d15-49a8-abfc-ad698aa55a91", "mjk29w", "Espoo",
            "2021-09-13T13:59:27.311+03:00[Europe/Helsinki]", "MESSAGE",
            "QmRXb6Ai5N5ZXhxViEKTwFyxmjKgTiHHePmUux7qvwMa2Y"),
        arrayOf<String>("606ebdd7-520c-4061-97f1-92bd9e3a2673", "Latokaski",
            "e164196e-9d15-49a8-abfc-ad698aa55a91", "mjk29w", "Espoo",
            "2021-09-13T14:00:31.994+03:00[Europe/Helsinki]", "MESSAGE",
            "QmRXb6Ai5N5ZXhxViEKTwFyxmjKgTiHHePmUux7qvwMa2Y"),
        arrayOf<String>("97757f24-39d0-420d-9999-84b01fca8e09", "Kilo",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-13T14:00:50.035+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("4fa6a57e-f8ff-496c-a4f7-b9e6dad42474", "Matinkylä",
            "e164196e-9d15-49a8-abfc-ad698aa55a91", "mjk29w", "Espoo",
            "2021-09-13T14:13:04.458+03:00[Europe/Helsinki]", "MESSAGE",
            "QmRXb6Ai5N5ZXhxViEKTwFyxmjKgTiHHePmUux7qvwMa2Y"),
        arrayOf<String>("194b686f-dac7-4994-af21-6301e3bd19c8", "Olari",
            "e164196e-9d15-49a8-abfc-ad698aa55a91", "mjk29w", "Espoo",
            "2021-09-13T14:13:10.073+03:00[Europe/Helsinki]", "MESSAGE",
            "QmRXb6Ai5N5ZXhxViEKTwFyxmjKgTiHHePmUux7qvwMa2Y"),
        arrayOf<String>("10176b26-15ce-43e4-a208-15629ca8538d", "Kuitinmäki",
            "45b260bc-d298-4ac7-bd1f-1c4676a291ca", "mjk30w", "Espoo",
            "2021-09-13T14:15:44.569+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdUsmftFxpanosHGEcrxjRiz3qZ8oPEZ7sTR4xf8aCTXS"),
        arrayOf<String>("fdd4e62b-d307-43da-acbb-9409c705e724", "Nöykkiö",
            "45b260bc-d298-4ac7-bd1f-1c4676a291ca", "mjk30w", "Espoo",
            "2021-09-13T14:22:06.424+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdUsmftFxpanosHGEcrxjRiz3qZ8oPEZ7sTR4xf8aCTXS"),
        arrayOf<String>("21ecda3b-01c3-4912-8c13-1284932ff4d7", "Kaitaa",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-13T14:22:17.116+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("be127023-de83-4ed1-a717-d4b239b9e7e1", "Viherlaakso",
            "45b260bc-d298-4ac7-bd1f-1c4676a291ca", "mjk30w", "Espoo",
            "2021-09-13T15:03:25.27+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdUsmftFxpanosHGEcrxjRiz3qZ8oPEZ7sTR4xf8aCTXS"),
        arrayOf<String>("180da8a7-e4d7-4dc2-9cd3-8dcb0ca05ce3", "Niipperi",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-13T15:03:35.905+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("8b002ac8-8a05-45b1-8c30-db1598046afd", "Tuomarila",
            "e164196e-9d15-49a8-abfc-ad698aa55a91", "mjk29w", "Espoo",
            "2021-09-13T15:10:43.329+03:00[Europe/Helsinki]", "MESSAGE",
            "QmRXb6Ai5N5ZXhxViEKTwFyxmjKgTiHHePmUux7qvwMa2Y"),
        arrayOf<String>("a6d5545f-e38e-414d-8720-b3cb566de48b", "Suvela",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-13T15:13:08.063+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH")
    )

    private val existingMessages = arrayOf(
        arrayOf<String>("180da8a7-e4d7-4dc2-9cd3-8dcb0ca05ce3", "Niipperi",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-13T15:03:35.905+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH"),
        arrayOf<String>("8b002ac8-8a05-45b1-8c30-db1598046afd", "Tuomarila",
            "e164196e-9d15-49a8-abfc-ad698aa55a91", "mjk29w", "Espoo",
            "2021-09-13T15:10:43.329+03:00[Europe/Helsinki]", "MESSAGE",
            "QmRXb6Ai5N5ZXhxViEKTwFyxmjKgTiHHePmUux7qvwMa2Y"),
        arrayOf<String>("a6d5545f-e38e-414d-8720-b3cb566de48b", "Suvela",
            "863cd1bf-d7a3-4cfc-ad48-e440ca3ad856", "mjktab", "Espoo",
            "2021-09-13T15:13:08.063+03:00[Europe/Helsinki]", "MESSAGE",
            "QmdX7eCw5jZAhcFbYw5ngT7ZVqJBQb2q98dCJ3MfqkqcAH")
    )
}