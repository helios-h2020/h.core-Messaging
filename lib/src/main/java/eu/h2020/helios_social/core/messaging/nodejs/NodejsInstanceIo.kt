package eu.h2020.helios_social.core.messaging.nodejs

import android.util.Log
import com.google.gson.JsonParseException
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class NodejsInstanceIo(val input: InputStream, val output: OutputStream) : Runnable {
    interface Listener {
        fun onReturn(msg: NodejsMessageReturn) {}
        fun onEvent(msg: NodejsMessageEvent) {}
        fun onCall(msg: NodejsMessageCall) {}
        fun onStream(msg: NodejsMessageStream) {}
    }

    companion object {
        const val TAG = "NodejsInstanceIo"
    }

    constructor(inputFd: FileDescriptor, outputFd: FileDescriptor) : this(
            FileInputStream(inputFd),
            FileOutputStream(outputFd)
    )

    val startLatch = CountDownLatch(1)

    private val started = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private val shouldRun = AtomicBoolean(true)
    private val messageListeners = Collections.synchronizedList(LinkedList<Listener>())
    private val writer = OutputStreamWriter(output, StandardCharsets.UTF_8)
    private var runner: Thread? = null

    val isStarted: Boolean
        get() = started.get()

    val isRunning: Boolean
        get() = running.get()

    fun stop() {
        shouldRun.set(false)
        runner?.interrupt()
    }

    fun send(msg: NodejsMessage) {
        send(msg.toJson())
    }

    private fun send(data: String) {
        writer.write(data)
        writer.write('\n'.code)
        writer.flush()
    }

    override fun run() {
        started.set(true)
        running.set(true)
        runner = Thread.currentThread()

        val reader = BufferedReader(
                InputStreamReader(input, StandardCharsets.UTF_8),
                128 * 1024
        )

        try {
            Log.i(TAG, "Started nodejs io thread, wait latch.")
            startLatch.countDown()
            Log.i(TAG, "Started nodejs io thread.")
            while (!Thread.currentThread().isInterrupted && shouldRun.get()) {
                val line = reader.readLine() ?: break
                try {
                    val msg = NodejsMessage.fromJson(line) ?: continue

                    onMessage(msg)
                } catch (e: JsonParseException) {
                    Log.e(TAG, "Error parsing json message from node, raw message:\n$line")
                    Log.e(TAG, e.message ?: "<no message>")

                    throw e
                }
            }
        } catch (e: IOException) {
            // Error reading from stream, no problem.
        } catch (e: InterruptedException) {
            Log.i(TAG, "Received nodejs io thread interrupt: $e")
        } finally {
            Log.i(TAG, "Stopping nodejs io thread.")
            running.set(false)
        }
    }

    private fun onMessage(msg: NodejsMessage) {
        synchronized(messageListeners) {
            when (msg.type) {
                NodejsMessageType.RETURN -> messageListeners.forEach { it.onReturn(msg as NodejsMessageReturn) }
                NodejsMessageType.RETURN_ERROR -> messageListeners.forEach { it.onReturn(msg as NodejsMessageReturn) }
                NodejsMessageType.EVENT -> messageListeners.forEach { it.onEvent(msg as NodejsMessageEvent) }
                NodejsMessageType.CALL -> messageListeners.forEach { it.onCall(msg as NodejsMessageCall) }
                NodejsMessageType.STREAM -> messageListeners.forEach { it.onStream(msg as NodejsMessageStream) }
                null -> Unit
            }
        }
    }

    fun addListener(listener: Listener) {
        synchronized(messageListeners) {
            messageListeners.add(listener)
        }
    }

    fun removeListener(listener: Listener) {
        synchronized(messageListeners) {
            messageListeners.remove(listener)
        }
    }
}