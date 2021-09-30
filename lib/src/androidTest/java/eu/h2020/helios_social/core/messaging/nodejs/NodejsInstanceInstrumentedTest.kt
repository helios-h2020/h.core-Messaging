package eu.h2020.helios_social.core.messaging.nodejs

import android.content.res.AssetManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.util.concurrent.BlockingQueue

/**
 * Local unit tests for NodejsInstance
 */
@RunWith(AndroidJUnit4::class)
class NodejsInstanceInstrumentedTest {
    @Test
    @Suppress("ConstantConditionIf")
    fun instance_constructs() {
        val obj = NodejsInstance(arrayOf("-e", "console.info('Done!');"))

        assertFalse(obj.nodeRunning())
        assertFalse(obj.nodeStarted())
        assertNotNull(obj.events)
        assertTrue(obj.events.isEmpty())
    }

    @Test
    fun instance_canPassNamed() {
        val obj = NodejsInstance(arrayOf("-e", "console.info('Done!');"))

        val map1 = obj.getDescriptorMap()

        assertTrue(obj.passDescriptorUrl("input", "test://input"))
        assertTrue(obj.passDescriptorUrl("output", "test://output"))

        val map2 = obj.getDescriptorMap()
        assertNotSame(map1, map2)
        assertTrue(map1.isEmpty())
        assertEquals(2, map2.size)
        assertEquals("test://input", map2["input"])
        assertEquals("test://output", map2["output"])
    }

    @Test
    fun instance_canPassFd() {
        val obj = NodejsInstance(arrayOf("-e", "console.info('Done!');"))
        val fd = FileDescriptor.out

        assertTrue(obj.passFileDescriptor("fd", fd))

        val map = obj.getDescriptorMap()
        assertNotNull(map["fd"])
        assertTrue(map["fd"] is String)
        assertTrue(map["fd"]!!.matches("""^fd://[0-9]+$""".toRegex()))
        assertEquals(1, map.size)
    }

    @Test
    fun instance_starts() {
        val obj = NodejsInstance(arrayOf("-e", "console.info('Done!');"))

        assertFalse(obj.nodeStarted())
        assertFalse(obj.nodeRunning())

        obj.start()
        // This is a fragile test, wait for node.js to actually start and exit.
        // A simple task, commonly less than one second.
        Thread.sleep(1000)

        assertTrue(obj.nodeStarted())
        assertFalse(obj.nodeRunning())
    }

    @Test
    fun instance_pingPong() {
        val ctx = InstrumentationRegistry.getInstrumentation().context

        val file = File(ctx.codeCacheDir, "ping-pong.js")

        file.deleteOnExit()
        ctx.assets.open("ping-pong.js").use {
            it.copyTo(file.outputStream())
        }

        val obj = NodejsInstance(arrayOf(file.path))

        obj.start()
        val rv = obj.callMethod("ping", arrayOf("a-ping"))
        Log.i("NodejsInstanceTest", "Return value: $rv")
        assertEquals("a-ping-pong", rv)
        obj.stop()
    }

    @Test
    fun instance_pingMePong() {
        val ctx = InstrumentationRegistry.getInstrumentation().context

        val file = File(ctx.codeCacheDir, "ping-pong.js")

        file.deleteOnExit()
        ctx.assets.open("ping-pong.js").use {
            it.copyTo(file.outputStream())
        }

        val obj = NodejsInstance(arrayOf(file.path))

        val fn = {
            args: Array<Any?> ->
                (args[0] as String) + "-my-pong"
        }
        obj.registerCallable("ping", fn)

        obj.start()
        val rv = obj.callMethod("ping-me", arrayOf("another-ping"))
        Log.i("NodejsInstanceTest", "Return value: $rv")
        assertEquals("another-ping-my-pong", rv)
        obj.stop()
    }
}
