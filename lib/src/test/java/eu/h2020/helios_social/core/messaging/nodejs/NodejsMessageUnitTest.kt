package eu.h2020.helios_social.core.messaging.nodejs

import org.junit.Test

import org.junit.Assert.*

/**
 * Local unit tests for NodejsMessage, these should be quite trivial
 */
class NodejsMessageUnitTest {
    @Test
    fun construct_Types() {
        val msg1 = NodejsMessageCall(
            "id-1",
            data = listOf(1, 2, 3),
            name = "call1"
        )
        assertEquals(NodejsMessageType.CALL, msg1.type)
        assertEquals(msg1.data, listOf(1, 2, 3))
        assertTrue(msg1.isValid())

        val msg2 = NodejsMessageReturn(
            "id-1",
            data = listOf(1, 2, 3, 4),
            name = "call2"
        )
        assertEquals(NodejsMessageType.RETURN, msg2.type)
        assertEquals(listOf(1, 2, 3, 4), msg2.data)
        assertTrue(msg2.isValid())

        val msg3 = NodejsMessageEvent(
            "id-1",
            data = byteArrayOf(1, 2),
            name = "event1"
        )
        assertEquals(NodejsMessageType.EVENT, msg3.type)
        assertEquals(byteArrayOf(1, 2).toList(), msg3.data?.toList())
        assertTrue(msg3.isValid())

        val msg4 = NodejsMessageStream(
            "id-1",
            data = byteArrayOf(123),
            name = "stream",
            close = true
        )
        assertEquals(NodejsMessageType.STREAM, msg4.type)
        assertEquals(byteArrayOf(123).toList(), msg4.data?.toList())
        assertTrue(msg4.isValid())
    }

    @Test
    fun json_Serializes() {
        val msg = NodejsMessageStream(
            "id-1",
            data = byteArrayOf(123),
            name = "streamName",
            close = false
        )

        val json = msg.toJson()

        assertEquals(
            "{\"type\":\"stream\",\"id\":\"id-1\",\"name\":\"streamName\",\"data\":[123],\"close\":false}",
            json
        )
    }

    @Test
    fun json_ExcludesNull() {
        val msg = NodejsMessageCall(
            "id-1",
            name = "callName",
            data = null
        )

        val json = msg.toJson()

        assertEquals("{\"type\":\"call\",\"id\":\"id-1\",\"name\":\"callName\",\"data\":[]}", json)
    }

    @Test
    fun json_CreatesCall1() {
        val json = "{\"type\":\"call\",\"id\":\"id-1\",\"name\":\"callName\"}"

        val msg = NodejsMessage.fromJson(json)

        assertNotNull(msg)
        assertTrue(msg is NodejsMessageCall)
        assertEquals(NodejsMessageType.CALL, msg!!.type)
        assertEquals("id-1", msg.id)
        assertEquals("callName", msg.name)
        assertEquals(null, msg.data)
        assertEquals(null, msg.close)
        assertFalse(msg.isValid())
    }

    @Test
    fun json_CreatesCall2() {
        val json = "{\"type\":\"call\",\"id\":\"id-1\",\"name\":\"callName\",\"close\":false,\"data\":[]}"

        val msg = NodejsMessage.fromJson(json)

        assertNotNull(msg)
        assertTrue(msg is NodejsMessageCall)
        assertEquals(NodejsMessageType.CALL, msg!!.type)
        assertEquals("id-1", msg.id)
        assertEquals("callName", msg.name)
        assertEquals(null, msg.close)
        assertEquals(emptyList<Int>(), msg.data)
        assertTrue(msg.isValid())
    }

    @Test
    fun json_Creates2() {
        val json =
            "{\"type\":\"event\",\"id\":\"id-1\",\"name\":\"eventName\",\"close\":false,\"data\":[1,1,3]}"

        val msg = NodejsMessage.fromJson(json)

        assertNotNull(msg)
        assertEquals(NodejsMessageType.EVENT, msg!!.type)
        assertEquals("id-1", msg.id)
        assertEquals("eventName", msg.name)
        assertEquals(listOf<Byte>(1, 1, 3), (msg.data as? ByteArray)?.toList())
        assertEquals(false, msg.close)
        assertTrue(msg.isValid())
    }

    @Test
    fun json_BadJson() {
        val json = "{\"type\":\"event\",\"name\":\"eventName\"}"

        val msg = NodejsMessage.fromJson(json)

        assertNotNull(msg)
        assertEquals(NodejsMessageType.EVENT, msg!!.type)
        assertEquals(null, msg.id)
        assertEquals("eventName", msg.name)
        assertFalse(msg.isValid())
    }

    @Test
    fun jsonDeserializeTypeMatch_Event() {
        val out = NodejsMessageEvent(
            "id-1",
            data = "1234".toByteArray(),
            name = "event1"
        )
        val json = out.toJson()

        val msg = NodejsMessage.fromJson(json)
        assertTrue(msg is NodejsMessageEvent)

        val msgT = msg as NodejsMessageEvent

        assertNotNull(msgT.data)
        assertTrue(msgT.data is ByteArray)
        assertEquals(out.data?.toList(), msgT.data?.toList())
    }

    @Test
    fun jsonDeserializeTypeMatch_Call() {}

    @Test
    fun jsonDeserializeTypeMatch_Return() {}

    @Test
    fun jsonDeserializeTypeMatch_() {}
}
