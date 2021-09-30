package eu.h2020.helios_social.core.messaging.nodejs

import android.system.Os
import android.system.OsConstants

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.*
import java.util.concurrent.CountDownLatch

class NodejsInstanceIoUnitTest {
    private var remoteInput: InputStream? = null
    private var remoteOutput: OutputStream? = null
    private var io: NodejsInstanceIo? = null

    // Some fixture
    val msg1 = NodejsMessageReturn(
        id = "id-1",
        name = "name-1",
        data = mapOf(Pair("asd", "sdf"))
    )
    val msg2 = NodejsMessageCall(
        id = "id-2",
        name = "name-2",
        data = null
    )
    val msg3 = NodejsMessageEvent(
        id = "id-3",
        name = "name-3",
        data = arrayOf<Byte>(1,2).toByteArray()
    )
    val msg4 = NodejsMessageStream(
        id = "id-4",
        name = "name-4",
        data = "data\n".toByteArray()
    )


    private fun createIo(): Triple<OutputStream, InputStream, NodejsInstanceIo> {
        val remoteInput = PipedInputStream()
        val remoteOutput = PipedOutputStream()
        val localInput = PipedInputStream(remoteOutput)
        val localOutput = PipedOutputStream(remoteInput)

        val obj = NodejsInstanceIo(localInput, localOutput)

        return Triple(remoteOutput, remoteInput, obj)
    }

    @Before
    fun createTestIo() {
        val (output, input, io) = createIo()

        remoteOutput = output
        remoteInput = input
        this.io = io
    }

    @After
    fun closeTestIo() {
        remoteInput?.close()
        remoteOutput?.close()
        io?.stop()

        remoteOutput = null
        remoteInput = null
        io = null
    }


    @Test
    fun construct_IoFd() {
        val fd1 = FileDescriptor()
        val fd2 = FileDescriptor()

        val obj = NodejsInstanceIo(fd1, fd2)

        assertFalse(obj.isRunning)
        assertFalse(obj.isStarted)
        assertEquals(obj.startLatch.count, 1)
    }

    @Test
    fun construct_IoStreams() {
        val input = ByteArrayInputStream(ByteArray(1024))
        val output = ByteArrayOutputStream()

        val obj = NodejsInstanceIo(input, output)

        assertFalse(obj.isRunning)
        assertFalse(obj.isStarted)
        assertEquals(obj.startLatch.count, 1)
    }


    @Test(timeout = 1000)
    fun thread_run() {
        val t = Thread(io)

        try {
            assertFalse(io!!.isRunning)
            assertFalse(io!!.isStarted)

            t.start()
            io!!.startLatch.await()
            assertTrue(io!!.isStarted)
            assertTrue(io!!.isRunning)

            t.interrupt()
            t.join()

            assertFalse(io!!.isRunning)
            assertTrue(io!!.isStarted)
        } finally {
            t.interrupt()
            t.join()
        }
    }

    @Test(timeout = 2000)
    fun receive_messageReceive() {
        val t = Thread(io)
        var count = 4
        val latch = CountDownLatch(count)


        io?.addListener(object : NodejsInstanceIo.Listener {
            override fun onReturn(msg: NodejsMessageReturn) {
                count--
                assertTrue(count >= 0)
                assertEquals(msg1.data, msg.data)
                assertEquals(msg1.id, msg.id)
                assertEquals(msg1.error, msg.error)
                assertEquals(msg1.type, msg.type)
                assertEquals(msg1.close, msg.close)
                assertEquals(msg1.name, msg.name)

                latch.countDown()
            }

            override fun onCall(msg: NodejsMessageCall) {
                count--
                assertTrue(count >= 0)
                assertEquals(msg2.data, msg.data)
                assertEquals(msg2.id, msg.id)
                assertEquals(msg2.error, msg.error)
                assertEquals(msg2.type, msg.type)
                assertEquals(msg2.close, msg.close)
                assertEquals(msg2.name, msg.name)
                latch.countDown()
            }

            override fun onEvent(msg: NodejsMessageEvent) {
                count--
                assertTrue(count >= 0)
                assertEquals(msg3.data?.toList(), msg.data?.toList())
                assertEquals(msg3.id, msg.id)
                assertEquals(msg3.error, msg.error)
                assertEquals(msg3.type, msg.type)
                assertEquals(msg3.close, msg.close)
                assertEquals(msg3.name, msg.name)
                latch.countDown()
            }

            override fun onStream(msg: NodejsMessageStream) {
                count--
                assertTrue(count >= 0)
                assertEquals(msg4.data?.toList(), msg.data?.toList())
                assertEquals(msg4.id, msg.id)
                assertEquals(msg4.error, msg.error)
                assertEquals(msg4.type, msg.type)
                assertEquals(msg4.close, msg.close)
                assertEquals(msg4.name, msg.name)
                latch.countDown()
            }
        })


        try {
            t.start()
            io!!.startLatch.await()

            val writer = remoteOutput!!.writer()

            writer.write(msg1.toJson())
            writer.write("\n")
            writer.flush()
            writer.write(msg2.toJson() + "\n")
            writer.flush()
            writer.write(msg3.toJson() + "\n" + msg4.toJson() + "\n")
            writer.flush()

            latch.await()

            assertEquals(0, count)
        } finally {
            t.interrupt()
            t.join()
        }
    }

    @Test(timeout = 2000)
    fun receive_messageSend() {
        val t = Thread(io)

        try {
            t.start()
            io!!.startLatch.await()

            io!!.send(msg1)

            val line = remoteInput!!.bufferedReader().readLine()

            assertEquals(msg1.toJson(), line)
        } finally {
            t.interrupt()
            t.join()
        }
    }
}