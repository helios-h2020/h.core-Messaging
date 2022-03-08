package eu.h2020.helios_social.core.messaging.p2p

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import eu.h2020.helios_social.core.messaging.HeliosIdentityInfo
import java.io.*

import eu.h2020.helios_social.core.messaging.HeliosNetworkAddress
import eu.h2020.helios_social.core.messaging.P2pPubSubMessage
import eu.h2020.helios_social.core.messaging.crypto.HexDump
import eu.h2020.helios_social.core.messaging.crypto.RsaKeyPairGenerator
import eu.h2020.helios_social.core.messaging.crypto.RsaPrivateKeyReader
import eu.h2020.helios_social.core.messaging.crypto.RsaPublicKeyWriter
import go.Seq
import heliosapi.*
import java.util.concurrent.*
import heliosapi.Heliosapi
import java.nio.charset.StandardCharsets
import heliosapi.FindOptions
import heliosapi.NetworkAddr
import org.json.JSONObject
import java.security.interfaces.RSAPrivateKey
import java.math.BigInteger
import java.util.*
import kotlin.collections.ArrayList


/**
 * This is the main interface to go-libp2p library. Other modules can use p2p* methods to invoke
 * go-libp2p functions through HeliosAPI go-libp2p API. The class also implements Callback
 * interface that is used by go-libp2p to process messages. The class also implements
 * Libp2pInstanceIo.Listener interface that is adapted from NodejsInstanceIo.Listener.
 *
 * The class is dapted from the nodejs adapter class NodejsInstance and also implements a couple
 * of methods that are called from the message library.
 *
 * TODO: Cleanup old stuff that is inherited fron nodejs but is no longer used.
 */
class Libp2pInstance(private val identity: HeliosIdentityInfo, private val appContext: Context) :
    Libp2pInstanceIo.Listener, Callback {

    val TAG = "Libp2pInstance"
    val DEFAULT_TIMEOUT = 60000L
    private var nodeThread: Thread? = null
    private var ioThread: Thread? = null
    private var io: Libp2pInstanceIo? = null
    private val callExecutor: ExecutorService by lazy {
        Executors.newFixedThreadPool(10)
    }

    private var methodCallSeq = 1
    private fun nextCallSequence(): String = "${TAG}-${hashCode()}-${methodCallSeq++}"

    private val callMap = ConcurrentHashMap<String, (Any?, Any?) -> Unit>()
    private val callableMap = ConcurrentHashMap<String, (Array<Any?>) -> Any?>()

    val events = LinkedBlockingQueue<Libp2pMessage>(200)

    // Handle for go-libp2p HeliosAPI class
    private lateinit var p2pNode: HeliosNetwork
    private var p2pNodeInitialized: Boolean = false
    private var p2pNodeStarted: Boolean = false

    /*
     * Subscription identifier is integer as string that is mapped to topic name
     */
    private var subscriptionId2Name = ConcurrentHashMap<String, String>()
    private var subscriptionName2Id = ConcurrentHashMap<String, String>()
    private var subscriptionName2TopicHandle = ConcurrentHashMap<String, HeliosPubSubTopic>()
    private var subscriptionIndex: Int = 0

    private var directMessaging: Libp2pDirectMessaging? = null

    // *********************************************************************************************
    // HeliosAPI Callback interface
    // *********************************************************************************************

    /**
     * Pubsub messages are processed in this callback function.
     */
    override fun forwardPubSubMessage(message: ByteArray) {
        Log.d(TAG, "forwardPubSubMessage")
        val s = String(message, StandardCharsets.UTF_8)
        Log.d(TAG, "Got message: " + s)
        try {
            val jObject = JSONObject(s)
            val receivedFrom: String? = jObject.get("ReceivedFrom") as String?
            if (receivedFrom == null) {
                Log.d(TAG, "Message sender was not found")
            } else {
                Log.d(TAG, "Received from " + receivedFrom)
            }
            val data: ByteArray = decodeB64Field(jObject, "data")
            val dataStr = String(data, StandardCharsets.UTF_8)
            Log.d(TAG, "DATA STRING: " + dataStr)
            val pubSubMsg = JSONObject(dataStr)
            // This is pubsub message
            if (pubSubMsg.has("to")) {
                val name: String = pubSubMsg.getString("to")
                var topicName = "/helios/pubsub"
                topicName += if (name.isEmpty() || name.startsWith('/')) "" else "/"
                topicName += name
                val subscriptionId = getSubscriptionId(topicName)
                Log.d(TAG, "Topic name=" + topicName + " Topic id=" + subscriptionId)
                var topicList: List<String> = listOf(topicName)
                // TODO: Remove Gson conversion. This is now parsed in Libp2pMessaging
                val pubsub = P2pPubSubMessage(receivedFrom!!, subscriptionId, topicList, data)
                val gson: Gson = Gson()
                val jsonString = gson.toJson(pubsub)
                val event =
                    Libp2pMessageEvent(subscriptionId, "pubsub:message", jsonString.toByteArray())
                onEvent(event)
            } else if (pubSubMsg.has("egoId") && pubSubMsg.has("networkId") &&
                       pubSubMsg.has("tag") && pubSubMsg.has("timestamp")) {
                val nid: String = pubSubMsg.getString("networkId")
                Log.d(TAG, "NETWORK IDENTIFIER INFORMATION $nid")
                val addresses: List<HeliosNetworkAddress> = libP2pFindPeer(nid, null)
                for (address in addresses) {
                    Log.d(TAG, "Address: " + address.toString())
                }
            } else {
                Log.d(TAG, "UNKNOWN PUBSUB MESSAGE")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Json parsing failed $e")
        }
    }

    /**
     * Direct messages are processed in this callback function
     */
    @Override
    override fun processStream(peerId: String, protocol: String, rfd: Long, wfd: Long)  {
        Log.d(TAG, "processStream $protocol from $peerId")
        var mutableMap: MutableMap<String, Any?> = mutableMapOf()
        mutableMap.put("egoId", null)
        mutableMap.put("networkId", peerId)
        mutableMap.put("privateKey", null)
        mutableMap.put("publicKey", null)
        mutableMap.put("networkAddress", null)
        val reader = ParcelFileDescriptor.fromFd(rfd.toInt())
        var args = Array<Any?>(3){ null }
        args[0] = mutableMap.toMap()
        args[1] = protocol
        args[2] = reader.getFileDescriptor()

        if (directMessaging == null) {
            Log.e(TAG, "Internal error - direct messaging handler is not set")
            reader.close()
            return
        }
        directMessaging?.receiveMessage(args)
        reader.close()
    }

    // *********************************************************************************************
    // Libp2pInstanceIo.Listener interface
    // *********************************************************************************************

    override fun onReturn(msg: Libp2pMessageReturn) {
        callMap[msg.id]?.invoke(msg.data, msg.error)
    }

    override fun onEvent(msg: Libp2pMessageEvent) {
        events.put(msg)
    }

    override fun onCall(msg: Libp2pMessageCall) {
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
                Log.e(TAG, "Invoke exception $e")
                err = e.message
            }

            // FIXME: This may not be thread safe on stop, but we're not really calling anything from node
            io?.send(
                Libp2pMessageReturn(
                    msg.id,
                    msg.name,
                    rv,
                    err
                )
            )
        }
    }

    // *********************************************************************************************
    // Methods adapted from NodejsInstance. These are called from the messaging module.
    // *********************************************************************************************

    fun registerCallable(name: String, fn: (Array<Any?>) -> Any?) {
        callableMap[name] = fn
    }

    /**
     * Because the Callback interface is implemented in this module, we need a way to pass control
     * to Libp2pDirectMessaging object that has registered receivers for different messages.
     */
    fun passDirectMessagingObject(dm: Libp2pDirectMessaging) {
        directMessaging = dm
    }

    @Suppress("unused")
    fun sendEvent(name: String, data: ByteArray?) {
        val msg = Libp2pMessageEvent(
            nextCallSequence(),
            name,
            data ?: emptyArray<Byte>().toByteArray()
        )

        io!!.send(msg)
    }

    @Synchronized
    fun start() {
        if (p2pNodeStarted) {
            throw RuntimeException("go-libp2p can only start once.")
        }

        // TODO: Initializing context here. Is this correct location?
        Seq.setContext(appContext)

        // Change bcrypto backend to JS via ENV
        Os.setenv("NODE_BACKEND", "js", true)
        Os.setenv("DEBUG", "helios*", true)

        val fdForLocal = FileDescriptor()
        val fdForForeign = FileDescriptor()
        try {
            Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_STREAM, 0, fdForLocal, fdForForeign)

            // passFileDescriptor("inout", fdForForeign)
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

        val io = Libp2pInstanceIo(fdForLocal, fdForLocal)
        ioThread = Thread(io, "go-libp2p-io-thread")

        this.io = io
        io.addListener(this)
        ioThread?.start()
        io.startLatch.await()
        Log.d(TAG, "I/O thread has started")
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
            onReturn(Libp2pMessageReturn(id, name = "node-instance-stop-error", error = "Node stop, call abort."))
        }

        p2pNode.stop()
        p2pNodeInitialized = false
        p2pNodeStarted = false
        io = null
        ioThread = null
        nodeThread = null
    }

    // *********************************************************************************************
    // Add here go-libp2p HELIOS API adapter functions. These methods are called from
    // HELIOS messaging library code.
    // *********************************************************************************************

    fun libP2pSendTo(address: HeliosNetworkAddress, protocolId: String, data: ByteArray) {
        val networkIdString: String? = address.networkId
        if (networkIdString == null) {
            Log.e(TAG, "NetworkId not found. Message is not sent")
            return
        }
        try {
            val networkId = Heliosapi.newNetworkID()
            networkId.setID(networkIdString)
            val p2pAddr: NetworkAddr? = p2pNode.findPeer(networkId, createFindOptions())
            if (p2pAddr == null) {
                Log.e(TAG, "Cannot resolve address using networkId. Message not sent")
                return
            }
            // TODO: Check can we directly pass this protocolId to libp2p?
            p2pAddr.setProtocol(protocolId)
            val str = HexDump.dumpHexString(data)
            Log.d(TAG, "Data to be sent:\n" + str)
            Log.d(TAG, "SendTo $networkIdString with protocol $protocolId")
            p2pNode.sendTo(p2pAddr, data, DEFAULT_TIMEOUT)
        } catch (e: Exception) {
            Log.e(TAG, "Message sending failed - exception caught $e")
        }
    }

    fun libP2pResolve(egoId: String): HeliosNetworkAddress? {
        Log.d(TAG, "Resolve egoId $egoId")
        try {
            val p2pAddr: NetworkAddr? = p2pNode.resolve(egoId)
            if (p2pAddr == null) {
                return null
            }
            return convertAddressP2pToHelios(p2pAddr)
        } catch (e: Exception) {
            Log.e(TAG, "Resolve failed - excepion caught $e")
            return null
        }
    }

    fun libP2pAddReceiver(protocolId: String) {
        // TODO: node.js code version had another parameter myId. Is it also needed here?
        Log.d(TAG, "Add receiver for protocol $protocolId")
        p2pNode.addReceiver(protocolId)
    }

    fun libP2pRemoveReceiver(protocolId: String) {
        Log.d(TAG, "Remove receiver for protocol $protocolId")
        p2pNode.removeReceiver(protocolId)
    }

    fun libP2pProvideService(serviceId: String) {
        Log.d(TAG, "Provide service $serviceId")
        try {
            p2pNode.provideService(serviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Provide service failed -exception caught $e")
        }
    }

    fun libP2pFindService(serviceId: String): List<HeliosNetworkAddress> {
        Log.d(TAG, "Find service $serviceId")
        var list: MutableList<HeliosNetworkAddress> = mutableListOf<HeliosNetworkAddress>()
        var findOptions = createFindOptions()
        try {
            val addresses: NetworkAddrs = p2pNode.findService(serviceId, findOptions)
            val count = addresses.size()
            for (i in 0..(count - 1)) {
                val addr: NetworkAddr = addresses.get(i)
                val hAddr = convertAddressP2pToHelios(addr)
                list.add(hAddr)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Find service failed - exception caught $e")
        }
        return list
    }

    fun libP2pPublish(topicName: String, msg: String) {
        try {
            Log.d(TAG, "Publish to topic $topicName message $msg")
            var topic: HeliosPubSubTopic = p2pNode.joinPubSub(topicName)
            topic.publish(msg.toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "Publishing failed $e")
        }
    }

    fun libP2pSubscribe(topicName: String): String? {
        Log.d(TAG, "Subscribe to $topicName")
        if (subscriptionName2TopicHandle.containsKey(topicName)) {
            Log.e(TAG, "Warning - Subscribing multiple times to " + topicName)
        }
        var topic: HeliosPubSubTopic = p2pNode.joinPubSub(topicName)
        try {
            p2pNode.subscribe(topic)
            subscriptionName2TopicHandle.put(topicName, topic)
            // TODO: Is there any native support for subscription id?
            Log.d(TAG, " HeliosPubSubTopic " + topic.string())
            val subscriptionId = getSubscriptionId(topicName)
            return subscriptionId
        } catch (e: Exception) {
            Log.e(TAG, "Subscription failed $e")
            return null
        }
    }

    fun libP2pUnsubscribe(topicName: String) {
        val topic: HeliosPubSubTopic? = subscriptionName2TopicHandle.get(topicName)
        if (topic == null) {
            Log.e(TAG, "Trying to unsubscribe a topic $topicName that was not subscribed")
        } else {
            try {
                p2pNode.leavePubSub(topic)
                subscriptionName2TopicHandle.remove(topicName)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to leave pubsub - exception caught $e")
            }
        }
    }

    // TODO: It is unclear what these options could be. Should somehow affect to FindOptions?
    fun libP2pFindPeer(peer: String, op: Map<String, *>?): MutableList<HeliosNetworkAddress> {
        Log.d(TAG, "Find peer $peer")
        try {
            val findOptions: FindOptions = createFindOptions(op)
            val networkId = Heliosapi.newNetworkID()
            networkId.setID(peer);
            val addr: NetworkAddr = p2pNode.findPeer(networkId, findOptions)
            val hAddr: HeliosNetworkAddress = convertAddressP2pToHelios(addr)
            var list: MutableList<HeliosNetworkAddress> = mutableListOf<HeliosNetworkAddress>()
            list.add(hAddr)
            return list
        } catch (e: Exception) {
            Log.e(TAG, "Find peer failed - exception caught $e")
            return mutableListOf<HeliosNetworkAddress>()
        }
    }

    // TODO: Very weird parameter and return types! Define more explict types if possible
    fun libP2pStart(netIdentity: Map<*,*>, op: Map<*,*>): Map<*,*>? {
        try {
            val egoId: String? = netIdentity.get("egoId") as? String
            var networkId: String? = netIdentity.get("networkId") as? String
            var privateNetworkId: String? = netIdentity.get("privateNetworkId") as? String
            val listenAddrs: ArrayList<String>? = getStringList(op.get("listenAddrs") as? ArrayList<*>)
            val bootstrapAddrs: ArrayList<String>? = getStringList(op.get("bootstrapAddrs") as? ArrayList<*>)
            val swarmKeyProtocol = op.get("swarmKeyProtocol") as? String
            val swarmKeyEncoding = op.get("swarmKeyEncoding") as? String
            val swarmKeyData = op.get("swarmKeyData") as? String
            val swarmKey = swarmKeyProtocol + "\n" + swarmKeyEncoding + "\n" + swarmKeyData
            var privateKey: String? = null
            var publicKey: String? = null

            if (listenAddrs == null) {
                Log.e(TAG, "Unable to get listen addresses")
                return null
            }
            if (bootstrapAddrs == null) {
                Log.e(TAG, "Bootstrap addresses not found")
                return null
            }
            for (addr in listenAddrs) {
                Log.d(TAG, "Listen: $addr")
            }
            for (addr in bootstrapAddrs) {
                Log.d(TAG, "Bootstrap: $addr")
            }

            // We expect that privateNetworkId information comes from the caller. It should
            // be created during the first invocation.
            if (privateNetworkId == null) {
                val kg = RsaKeyPairGenerator()
                privateKey = kg.getPrivateKey()
                publicKey = kg.getPublicKey()
            } else {
                val json: JSONObject = JSONObject(privateNetworkId)
                privateKey = json.getString("privKey")
                if (privateKey == null) {
                    Log.e(TAG, "PRIVATE KEY NOT FOUND IN p2pStart")
                    return null
                }
            }
            Log.d(TAG, "Start...")
            createP2pNode(egoId!!, privateKey, listenAddrs, bootstrapAddrs, swarmKey)

            if (privateNetworkId == null) {
                networkId = p2pNode.getID()
                privateNetworkId = buildPrivateNetworkIdString(networkId, privateKey, publicKey!!)
            }
            val networkAddr: NetworkAddr = p2pNode.resolve(egoId)
            Log.d(TAG, "Found peer: " + networkAddr.getNetworkID());
            Log.d(TAG, "Found " + networkAddr.sizeAddrs() + " addresses");

            val result = mapOf(
                "privateNetworkId" to privateNetworkId,
                "networkId" to networkId
            )
            Log.d(TAG, "Started...")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Exception caught during node start $e")
            return null
        }
    }

    fun libP2pNetworkLost() {
        Log.d(TAG, "PLACEHOLDER - Stop libp2p networking")
        // p2pNode.stop()
        // Log.d(TAG, "libbp2p networking stopped")
    }

    fun libP2pNetworkReconnect() {
        Log.d(TAG, "PLACEHOLDER - Start libp2p networking")
        // p2pNode.start()
        // Log.d(TAG, "Libp2p networking started")
    }

    // *********************************************************************************************
    // Internal utility methods to be used with go-libp2p communications
    // *********************************************************************************************

    /**
     * Create go-libp2p node that is used in P2P communications
     *
     * TODO: This is from go-libp2p-android testNodeCreation method. Check how it fits.
     */
    private fun createP2pNode(
        egoId: String, privateKey: String,
        listenAddrs: ArrayList<String>,
        bootstrapAddrs: ArrayList<String>,
        swarmKey: String
    ) {
        if (!p2pNodeInitialized) {
            p2pNode = Heliosapi.newHeliosNetwork(egoId, privateKey, swarmKey.toByteArray())
            Log.d(TAG, "REGISTER GO-LIBP2P CALLBACK")
            p2pNode.registerCallback(this)
            p2pNodeInitialized = true
        }
        for (i in listenAddrs.indices) {
            val addr: String = listenAddrs[i]
            p2pNode.addListenAddress(addr)
        }
        for (i in bootstrapAddrs.indices) {
            val addr: String = bootstrapAddrs[i]
            p2pNode.addBootstrapAddress(addr)
        }
        try {
            p2pNode.start()
            p2pNodeStarted = true
        } catch (e: Exception) {
            Log.e(TAG, "Start failed - exception caught $e")
        }
    }

    /**
     * Address format conversion from go-libp2p address format to HELIOS type
     * TODO: NetworkAddr has also a protocol field that is missing from HeliosNetworkAddress
     */
    private fun convertAddressP2pToHelios(address: NetworkAddr): HeliosNetworkAddress {
        val egoId: String? = address.egoID
        val networkId: String? = address.networkID
        val privateKeyData: ByteArray? = address.privateKey
        val publicKeyData: ByteArray? = address.publicKey
        var privateKey: String? = null
        var publicKey: String? = null
        if (privateKeyData != null) {
            privateKey = String(privateKeyData, StandardCharsets.UTF_8)
        }
        if (publicKeyData != null) {
            publicKey = String(publicKeyData, StandardCharsets.UTF_8)
        }
        val count: Long = address.sizeAddrs()
        var networkAddress: MutableList<String>? = null
        if (count > 0) {
            var addrs: MutableList<String> = mutableListOf<String>()
            for (i in 0..(count - 1)) {
                addrs.add(address.getAddr(i))
            }
            networkAddress = addrs
        }
        return HeliosNetworkAddress(egoId, networkId, privateKey, publicKey, networkAddress)
    }

    private fun createFindOptions(op: Map<String, *>? = null) : FindOptions {
        var findOptions = FindOptions()
        if (op == null) {
            findOptions.setIncludeSelf(false)
            findOptions.setMaxCount(20L)
            findOptions.setTimeout(DEFAULT_TIMEOUT)
            return findOptions
        }
        val includeSelf: Boolean = op.getOrDefault("includeSelf", false) as Boolean
        val maxCount: Long = op.getOrDefault("maxCount", 20L) as Long
        val timeout: Long = op.getOrDefault("timeout", DEFAULT_TIMEOUT) as Long
        findOptions.setIncludeSelf(includeSelf)
        findOptions.setMaxCount(maxCount)
        findOptions.setTimeout(timeout)
        return findOptions
    }

    /**
     * Subscription id is a string identifier bound to a topic name. The values are string
     * representations of numeric values e.g., "1", "2", "3",...
     */
    @Synchronized
    private fun getSubscriptionId(topicName: String): String {
        val id: String? = subscriptionName2Id.get(topicName)
        if (id != null) {
            return id
        }
        subscriptionIndex++ // If mapping is not found then get next integer value
        val newId: String = subscriptionIndex.toString()
        subscriptionId2Name.put(newId, topicName)
        subscriptionName2Id.put(topicName, newId)
        return newId
    }

    /**
     * Reverse mapping from subscription ids ("1", "2", "3",...) is also given.
     */
    private fun getTopicName(subscriptionId: String): String? {
        return subscriptionName2Id.get(subscriptionId)
    }

    private fun decodeB64Field(json: JSONObject, key: String): ByteArray {
        val b64data: String = json.getString(key)
        val data: ByteArray = Base64.decode(b64data, Base64.DEFAULT)
        return data
    }

    /**
     * Generate privateNetworkId JSON string that will be saved in shared preferences.
     *
     * Normally, privateNetworkId is stored in SharedPreferences. However, it must be created
     * once during the first invocation of the program. The privateNetworkId consist of the
     * networkId and private public key pair.
     *
     * Data formats are specified in:
     *   https://github.com/libp2p/specs/blob/master/peer-ids/peer-ids.md
     */
    private fun buildPrivateNetworkIdString(id: String, priv: String, pub: String): String {
        var builder = StringBuilder()
        builder.append("{")
        builder.append("\"id\":")
        builder.append("\"" + id  + "\",")
        builder.append("\"privKey\":")
        builder.append("\"" + priv  + "\",")
        builder.append("\"pubKey\":")
        builder.append("\"" + pub  + "\"")
        builder.append("}")
        return builder.toString()
    }

    private fun joinListElements(list: List<ByteArray>): ByteArray {
        var size = 0
        for (buffer in list) {
            size += buffer.size
        }
        var result: ByteArray = ByteArray(size)
        var pos = 0
        for (buffer in list) {
            System.arraycopy(buffer, 0, result, pos, buffer.size)
            pos += buffer.size
        }
        return result
    }

    private fun getStringList(list: ArrayList<*>?): ArrayList<String>? {
        if (list == null) {
            return null
        }
        val result = list.filterIsInstance<String>()
        return ArrayList<String>(result)
    }
}
