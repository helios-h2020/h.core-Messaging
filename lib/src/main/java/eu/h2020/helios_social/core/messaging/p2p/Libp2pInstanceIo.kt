package eu.h2020.helios_social.core.messaging.p2p

import android.util.Log
import com.google.gson.JsonParseException
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class Libp2pInstanceIo(val input: InputStream, val output: OutputStream) : Runnable {
    interface Listener {
        fun onReturn(msg: Libp2pMessageReturn) {}
        fun onEvent(msg: Libp2pMessageEvent) {}
        fun onCall(msg: Libp2pMessageCall) {}
        fun onStream(msg: Libp2pMessageStream) {}
    }

    companion object {
        const val TAG = "Libp2pInstanceIo"
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

    fun send(msg: Libp2pMessage) {
        Log.d(TAG, "send")
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
            Log.i(TAG, "Started go-libp2p io thread, wait latch.")
            startLatch.countDown()
            Log.i(TAG, "Started go-libp2p io thread.")
            while (!Thread.currentThread().isInterrupted && shouldRun.get()) {
                val line = reader.readLine() ?: break
                try {
                    val msg = Libp2pMessage.fromJson(line) ?: continue

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
            Log.i(TAG, "Received go-libp2p io thread interrupt: $e")
        } finally {
            Log.i(TAG, "Stopping go-libp2p io thread.")
            running.set(false)
        }
    }

    private fun onMessage(msg: Libp2pMessage) {
        Log.d(TAG, "onMessage")
        synchronized(messageListeners) {
            when (msg.type) {
                Libp2pMessageType.RETURN -> messageListeners.forEach { it.onReturn(msg as Libp2pMessageReturn) }
                Libp2pMessageType.RETURN_ERROR -> messageListeners.forEach { it.onReturn(msg as Libp2pMessageReturn) }
                Libp2pMessageType.EVENT -> messageListeners.forEach { it.onEvent(msg as Libp2pMessageEvent) }
                Libp2pMessageType.CALL -> messageListeners.forEach { it.onCall(msg as Libp2pMessageCall) }
                Libp2pMessageType.STREAM -> messageListeners.forEach { it.onStream(msg as Libp2pMessageStream) }
                null -> Unit
            }
        }
    }

    fun addListener(listener: Listener) {
        Log.d(TAG, "addListener")
        synchronized(messageListeners) {
            messageListeners.add(listener)
        }
    }

    fun removeListener(listener: Listener) {
        Log.d(TAG, "removeListener")
        synchronized(messageListeners) {
            messageListeners.remove(listener)
        }
    }
}