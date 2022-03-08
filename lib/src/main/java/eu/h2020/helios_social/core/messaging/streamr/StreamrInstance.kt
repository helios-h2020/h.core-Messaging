package eu.h2020.helios_social.core.messaging.streamr

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.*
import java.util.HashMap
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

import android.content.res.AssetManager
import java.security.MessageDigest
import java.util.concurrent.*

class StreamrInstance(val args: Array<String>) : StreamrInstanceIo.Listener {
    companion object {
        const val TAG = "StreamrInstance"

        init {
            val libPath: String? = System.getProperty("java.library.path")
            Log.i(TAG, "LibPath: $libPath")
            try {
                System.loadLibrary("node_bridge")
                Log.d(TAG, "libnode_bridge loaded")
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception when loading a library - node_bridge $e");
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Unsatisfied link exception - node_bridge $e")
            } catch (e: NullPointerException) {
                Log.e(TAG, "Null pointer exception - node_bridge $e")
            }
            try {
                System.loadLibrary("node")
                Log.d(TAG, "libnode loaded")
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception when loading a library - node $e");
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Unsatisfied link exception - node $e")
            } catch (e: NullPointerException) {
                Log.e(TAG, "Null pointer exception - node $e")
            }
            try {
                System.loadLibrary("dl")
                Log.d(TAG, "libdl loaded")
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception when loading a library - dl $e");
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Unsatisfied link exception - dl $e")
            } catch (e: NullPointerException) {
                Log.e(TAG, "Null pointer exception - dl $e")
            }
        }

        fun prepareNodeAsset(
            ctx: Context,
            assetName: String,
            manager: AssetManager = ctx.assets
        ): File {
            val updateTime = ctx.packageManager
                .getPackageInfo(ctx.packageName, 0)
                .lastUpdateTime

            val digest = MessageDigest.getInstance("SHA-1")
                .digest("$assetName-$updateTime".toByteArray())
                .joinToString(separator = "") {
                    "%02x".format(it)
                }

            val target = File(ctx.codeCacheDir, digest)

            if (target.isDirectory && target.canRead()) {
                Log.i(TAG, "Nodejs asset $assetName => ${target.path} exists, skip extraction.")

                return target
            }

            manager.open(assetName).use {
                deleteAndUnzip(it, target)
            }

            return target
        }

        /**
         * Utility function that extracts up stream asset zip into the application dir
         */
        fun deleteAndUnzip(input: InputStream, target: File) {
            if (target.exists()) {
                target.deleteRecursively()
            }

            Log.i(TAG, "Extract files into ${target.path}")
            target.mkdirs()

            val zipStream = ZipInputStream(input)
            while (true) {
                val entry = zipStream.nextEntry ?: break

                try {
                    val dst = File(target, entry.name).canonicalFile

                    if (entry.isDirectory) {
                        if (!dst.exists()) {
                            dst.mkdirs()
                        }

                        continue
                    }

                    if (dst.parentFile?.exists() == false) {
                        dst.parentFile!!.mkdirs()
                    }

                    FileOutputStream(dst).use {
                        zipStream.copyTo(it)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, e.toString())

                    throw e
                } finally {
                    zipStream.closeEntry()
                }
            }
        }

        external fun getFileDescriptorInt(fd: FileDescriptor): Int
        external fun setFileDescriptorInt(fd: FileDescriptor, descriptor: Int): Int
    }

    private var nodeThread: Thread? = null
    private var ioThread: Thread? = null
    private var io: StreamrInstanceIo? = null
    private var nodeStartCalled: Boolean = false
    private val callExecutor: ExecutorService by lazy {
        Executors.newFixedThreadPool(10)
    }

    private var methodCallSeq = 1
    private fun nextCallSequence(): String = "${TAG}-${hashCode()}-${methodCallSeq++}"

    private val callMap = ConcurrentHashMap<String, (Any?, Any?) -> Unit>()
    private val callableMap = ConcurrentHashMap<String, (Array<Any?>) -> Any?>()

    val events = LinkedBlockingQueue<StreamrMessage>(200)

    //
    // JNI parts
    //
    private external fun startNodeWithArguments(args: Array<String>): Int
    external fun passFileDescriptor(name: String, fd: FileDescriptor): Boolean
    external fun passDescriptorUrl(name: String, path: String): Boolean
    external fun getDescriptorMap(): HashMap<String, String>
    external fun nodeStarted(): Boolean
    external fun nodeRunning(): Boolean

    @Suppress("unused")
    fun callMethod(name: String, arguments: Array<Any?> = emptyArray()): Any? {
        val callId = nextCallSequence()
        val msg = StreamrMessageCall(
            callId,
            name,
            arguments.toList()
        )
        val s = Semaphore(0)
        var value: Any? = null
        var error: Any? = null
        val handler: (Any?, Any?) -> Unit = { v, err ->
            value = v
            error = err
            callMap.remove(callId)
            s.release()
        }

        callMap[callId] = handler
        io!!.send(msg)

        s.acquire()
        if (error != null) {
            // throw RuntimeException("$error")
            Log.e(TAG, "Error in callMethod " + name);
            return null
        }

        return value
    }

    inline fun <reified T> callTyped(
        name: String,
        arguments: Array<Any?> = emptyArray()
    ): T? {
        val value = callMethod(name, arguments)

        if (value !is T) {
            return null
        }

        return value
    }

    fun registerCallable(name: String, fn: (Array<Any?>) -> Any?) {
        callableMap[name] = fn
    }

    @Suppress("unused")
    fun sendEvent(name: String, data: ByteArray?) {
        val msg = StreamrMessageEvent(
            nextCallSequence(),
            name,
            data ?: emptyArray<Byte>().toByteArray()
        )

        io!!.send(msg)
    }

    @Synchronized
    fun start() {
        if (nodeStartCalled || nodeStarted()) {
            throw RuntimeException("Nodejs can only start once.")
        }

        // Change bcrypto backend to JS via ENV
        Os.setenv("NODE_BACKEND", "js", true)
        Os.setenv("DEBUG", "helios*", true)

        val fdForLocal = FileDescriptor()
        val fdForForeign = FileDescriptor()
        try {
            Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_STREAM, 0, fdForLocal, fdForForeign)

            passFileDescriptor("inout", fdForForeign)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating socket pair: $e")

            if (fdForForeign.valid()) {
                Os.close(fdForForeign)
            }

            if (fdForLocal.valid()) {
                Os.close(fdForLocal)
            }

            return
        }

        val io = StreamrInstanceIo(fdForLocal, fdForLocal)
        val latch = CountDownLatch(1)
        ioThread = Thread(io, "nodejs-io-thread")

        this.io = io
        io.addListener(this)
        ioThread?.start()
        io.startLatch.await()

        nodeThread = thread(name = "nodejs-thread") {
            try {
                Log.i(TAG, "Starting libnode.")

                latch.countDown()
                val exitValue = startNodeWithArguments(args)
                Log.i(TAG, "Exit libnode: $exitValue")
            } catch (e: Exception) {
                Log.i(TAG, "Exit libnode with exception: $e")
            }
        }

        nodeStartCalled = true
        latch.await()
    }

    @Synchronized
    fun stop() {
        nodeThread?.interrupt()
        io?.stop()
        ioThread?.interrupt()
        io?.stop()
        io?.removeListener(this)

        // We should not receive any more callbacks so we can fail all waiters
        // No need to fail callable methods from node to us, since node is already gone.
        for (id in callMap.keys()) {
            onReturn(StreamrMessageReturn(id, name = "node-instance-stop-error", error = "Node stop, call abort."))
        }

        io = null
        ioThread = null
        nodeThread = null
    }

    override fun onReturn(msg: StreamrMessageReturn) {
        callMap[msg.id]?.invoke(msg.data, msg.error)
    }

    override fun onEvent(msg: StreamrMessageEvent) {
        events.put(msg)
    }

    override fun onCall(msg: StreamrMessageCall) {
        val fn = callableMap[msg.name ?: ""] ?: return
        val args = msg.data?.toTypedArray() ?: emptyArray()

        // Messages from node.js are processed in a single thread, use a separate
        // executor pool to handle calls so we won't block
        callExecutor.execute {
            var rv: Any? = null
            var err: Any? = null

            try {
                rv = fn.invoke(args);
            } catch (e: Exception) {
                err = e.message
            }

            // FIXME: This may not be thread safe on stop, but we're not really calling anything from node
            io?.send(
                StreamrMessageReturn(
                    msg.id,
                    msg.name,
                    rv,
                    err
                )
            )
        }
    }
}
