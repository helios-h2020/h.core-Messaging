package eu.h2020.helios_social.core.messaging.p2p

import android.os.ParcelFileDescriptor
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


class Libp2pDirectMessaging(private val node: Libp2pInstance) : HeliosDirectMessaging {
    private var receivers = emptyMap<String, HeliosMessagingReceiver>().toMutableMap()
    private val lock = ReentrantLock()
    private val myId = UUID.randomUUID().toString()

    companion object {
        const val TAG = "Libp2pDirectMessaging"
        val executor = Executors.newFixedThreadPool(5)
    }

    override fun sendTo(address: HeliosNetworkAddress, protocolId: String, data: ByteArray) {
        Log.d(TAG, "XYZ SENDTO address: $address")
        node.libP2pSendTo(address, protocolId, data)
        Log.d(TAG, "XYZ SENDTO address finished: $address")
    }

    override fun sendToFuture(
        address: HeliosNetworkAddress,
        protocolId: String,
        data: ByteArray
    ): Future<Unit> {
        Log.d(TAG, "XYZ SENDTOFUTURE address: $address")
        return executor.submit<Unit> {
            sendTo(address, protocolId, data)
        }
    }

    override fun addReceiver(protocolId: String, receiver: HeliosMessagingReceiver) {
        Log.d(TAG, "XYZ ADD RECEIVER $protocolId")
        lock.withLock {
            receivers[protocolId] = receiver
            node.libP2pAddReceiver(protocolId)
        }
    }

    override fun removeReceiver(protocolId: String) {
        Log.d(TAG, "XYZ REMOVE RECEIVER $protocolId")
        lock.withLock {
            receivers.remove(protocolId)
            node.libP2pRemoveReceiver(protocolId)
        }
    }

    override fun resolve(egoId: String): HeliosNetworkAddress {
        Log.d(TAG, "XYZ RESOLVE $egoId")
        try {
            val rv = node.libP2pResolve(egoId) ?: HeliosNetworkAddress(egoId)
            Log.d(TAG, "Resolved to: $rv")
            return rv
            // TODO: Unclear how this should work
            // if (rv is JsonElement) {
            //     return HeliosNetworkAddress.fromJson(rv) ?: HeliosNetworkAddress(egoId)
            //}

            // @Suppress("UNCHECKED_CAST")
            // return HeliosNetworkAddress.fromMap(rv as Map<String, Any?>)
        } catch (e: Exception) {
            Log.d(TAG, "Error resolving", e)
        }

        return HeliosNetworkAddress(egoId)
    }

    override fun resolveFuture(egoId: String): Future<HeliosNetworkAddress> {
        Log.d(TAG, "XYZ RESOLVE FUTURE $egoId")
        return executor.submit<HeliosNetworkAddress> {
            return@submit resolve(egoId)
        }
    }

    /**
     * Receive direct messages
     */
    internal fun receiveMessage(args: Array<Any?>): Any? {
        Log.d(TAG, "XYZ INTERNAL RECEIVE MESSAGE")
        if (args.size < 3) {
            return null
        }

        try {
            val protoId = args[1] as String
            val receiver = receivers[protoId] ?: return null

            @Suppress("UNCHECKED_CAST")
            val addr = HeliosNetworkAddress.fromMap(args[0] as Map<String, Any?>)

            @Suppress("UNCHECKED_CAST")
            val fd: FileDescriptor = args[2] as FileDescriptor

            receiver.receiveMessage(addr, protoId, fd)
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving direct message", e)
        }

        return null
    }
}
