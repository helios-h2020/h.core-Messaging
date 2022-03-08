package eu.h2020.helios_social.core.messaging.streamr

import android.util.Log
import com.google.gson.JsonElement
import eu.h2020.helios_social.core.messaging.HeliosDirectMessaging
import eu.h2020.helios_social.core.messaging.HeliosMessagingReceiver
import eu.h2020.helios_social.core.messaging.HeliosNetworkAddress
import java.io.FileDescriptor
import java.lang.Exception
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


class HeliosDirectMessagingStreamrLibp2p(private val node: StreamrInstance) : HeliosDirectMessaging {
    private var receivers = emptyMap<String, HeliosMessagingReceiver>().toMutableMap()
    private val lock = ReentrantLock()
    private val myId = UUID.randomUUID().toString()

    companion object {
        const val TAG = "HeliosDirectMessagingNodejsLibp2p"
        val executor = Executors.newFixedThreadPool(5)
    }

    override fun sendTo(address: HeliosNetworkAddress, protocolId: String, data: ByteArray) {
        Log.d(TAG, "sendTo address: $address")
        node.callMethod("send-to", arrayOf(address, protocolId, data))
        Log.d(TAG, "sendTo address finished: $address")
    }

    override fun sendToFuture(
        address: HeliosNetworkAddress,
        protocolId: String,
        data: ByteArray
    ): Future<Unit> {
        Log.d(TAG, "sendToFuture address: $address")
        return executor.submit<Unit> {
            sendTo(address, protocolId, data)
        }
    }

    override fun addReceiver(protocolId: String, receiver: HeliosMessagingReceiver) {
        lock.withLock {
            receivers[protocolId] = receiver
            node.callMethod("add-receiver", arrayOf(protocolId, myId))
        }
    }

    override fun removeReceiver(protocolId: String) {
        lock.withLock {
            receivers.remove(protocolId)
            node.callMethod("remove-receiver", arrayOf(protocolId))
        }
    }

    override fun resolve(egoId: String): HeliosNetworkAddress {
        Log.d(TAG, "Resolve $egoId")
        try {
            val rv = node.callMethod("resolve", arrayOf(egoId)) ?: HeliosNetworkAddress(egoId)
            Log.d(TAG, "Resolved to: $rv")
            if (rv is JsonElement) {
                return HeliosNetworkAddress.fromJson(rv) ?: HeliosNetworkAddress(egoId)
            }

            @Suppress("UNCHECKED_CAST")
            return HeliosNetworkAddress.fromMap(rv as Map<String, Any?>)
        } catch (e: Exception) {
            Log.d(TAG, "Error resolving", e)
        }

        return HeliosNetworkAddress(egoId)
    }

    override fun resolveFuture(egoId: String): Future<HeliosNetworkAddress> {
        return executor.submit<HeliosNetworkAddress> {
            return@submit resolve(egoId)
        }
    }

    internal fun receiveMessage(args: Array<Any?>): Any? {
        if (args.size < 3) {
            return null
        }

        try {
            val protoId = args[1] as String
            val receiver = receivers[protoId] ?: return null

            @Suppress("UNCHECKED_CAST")
            val addr = HeliosNetworkAddress.fromMap(args[0] as Map<String, Any?>)

            @Suppress("UNCHECKED_CAST")
            val data = (args[2] as Double).toInt()

            val fd = FileDescriptor()

            StreamrInstance.setFileDescriptorInt(fd, data)

            receiver.receiveMessage(addr, protoId, fd)
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving direct message", e)
        }

        return null
    }
}
