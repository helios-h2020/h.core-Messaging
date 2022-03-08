package eu.h2020.helios_social.core.messaging.p2p

import android.content.Context
import android.content.Intent
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import eu.h2020.helios_social.core.messaging.*
import eu.h2020.helios_social.core.messaging.crypto.RsaPrivateKeyReader
import eu.h2020.helios_social.core.messaging.crypto.RsaKeyPairGenerator
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashSet
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

import heliosapi.Heliosapi
import heliosapi.HeliosNetwork
import org.json.JSONObject
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import javax.security.auth.x500.X500Principal
import java.security.*
import java.security.spec.RSAKeyGenParameterSpec

/**
 * This class is handling pubsub communications utilziing P2P interface class Libp2pInstance.
 *
 * This class is adapted from nodejs-specific version HeliosMessagingNodejsLibp2p. There are
 * still structures that are from nodejs implementation and should be removed.
 */
class Libp2pMessaging : HeliosMessaging {
    var directMessaging: HeliosDirectMessaging? = null
    private var node: Libp2pInstance? = null
    private val subscribers = ConcurrentHashMap<String, HeliosMessageListener>()
    private var eventPump: Thread? = null
    var peerId: String? = null
    var privatePeerId: String? = null

    var connectionCount = 0
        private set

    var context: Context? = null

    companion object {
        const val TAG = "Libp2pMessaging"
        const val SHARED_PREFERENCES_FILE = "helios-node-libp2p-prefs"

        @JvmStatic
        val instance: Libp2pMessaging by lazy {
            Libp2pMessaging()
        }
    }

    /**
     * Connect to the Libp2p P2P network and start the service. Note that
     * this is not actually a "connect", this imply starts the service.
     */
    @Synchronized
    fun start(identity: HeliosIdentityInfo, appContext: Context = context!!) {
        if (node != null) {
            Log.d(TAG, "Node messaging already started, return no-op.")
            return
        }
        val sharedPreferences = appContext.getSharedPreferences(
            SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE
        )!!

        val nodeInstance = Libp2pInstance(identity, appContext)
        val dm = Libp2pDirectMessaging(nodeInstance)

        directMessaging = dm
        node = nodeInstance

        // TODO: Old soon obsolete interface
        // nodeInstance.registerCallable("receive-message", dm::receiveMessage)

        // Pass just created Libp2pDirectMessaging object to Libp2pInstance
        nodeInstance.passDirectMessagingObject(dm)

        // This event pump thread is used to process incoming events that are coming from go-libp2p.
        // The events are received from go-libp2p callback that is in Libp2pInstance and is putting
        // the events to the event queue that is read in this thread. This thread is then calling
        // a listener class that is used to show the message.
        eventPump = thread(name = "helios-libp2p-event") {

            val gson = GsonBuilder()
                .enableComplexMapKeySerialization()
                .create()!!

            Log.i(TAG, "Starting helios libp2p event pump.")
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val msg = nodeInstance.events.take()

                    if (msg !is Libp2pMessageEvent) {
                        continue
                    }

                    if (msg.name == "pubsub:message") {
                        Log.d(TAG, "PUBSUB MESSAGE TAKEN " + msg.id)
                        if (subscribers.containsKey(msg.id)) {
                            Log.d(TAG, "Key " + msg.id + " was found")
                        } else {
                            Log.d(TAG, "Key " + msg.id + " was NOT found")
                            val keys: List<String> = subscribers.keys().toList()
                            for (key in keys) {
                                Log.d(TAG, "Key " + key)
                            }
                        }
                        val listener = subscribers[msg.id] ?: continue
                        Log.d(TAG, "Pump pubsub: ${msg.stringData}")
                        val pubsubmsg =
                            gson.fromJson(
                                msg.stringData,
                                P2pPubSubMessage::class.java
                            ) ?: continue
                        Log.d(TAG, "pubsubmsg created")

                        if (pubsubmsg.topic.isNullOrEmpty()) {
                            continue
                        }
                        Log.d(TAG, "topic name is found")

                        val topic = HeliosTopic(
                            pubsubmsg.topic.first().substring("/helios/pubsub/".length),
                            ""
                        )
                        Log.d(TAG, "topic is created")

                        val message = HeliosMessageLibp2pPubSub(
                            pubsubmsg.getStringData(),
                            networkAddress = HeliosNetworkAddress(networkId = pubsubmsg.networkId)
                        )
                        Log.d(TAG, "message is created")

                        listener.showMessage(topic, message)
                        continue
                    }

                    if (msg.name == "peer:discovery") {
                        val peer = gson.fromJson(
                            msg.stringData,
                            P2pPeerInfoMessage::class.java
                        ) ?: continue
                        Log.i(TAG, "Discover peer id: $peer")
                        continue
                    }

                    if (msg.name == "peer:connect") {
                        val peer = gson.fromJson(
                            msg.stringData,
                            P2pPeerInfoMessage::class.java
                        ) ?: continue
                        connectionCount++
                        Log.i(TAG, "Connection with peer ($connectionCount): $peer")
                        continue
                    }

                    if (msg.name == "peer:disconnect") {
                        val peer = gson.fromJson(
                            msg.stringData,
                            P2pPeerInfoMessage::class.java
                        ) ?: continue
                        connectionCount--
                        Log.i(TAG, "Disconnect from peer ($connectionCount): $peer")
                        continue
                    }

                    if (msg.name == "ego:announce") {
                        val announce = msg.stringData

                        Log.i(TAG, "Announced ego id $announce")
                        continue
                    }
                } catch (e: JsonParseException) {
                    // Invalid JSON, log and move on.
                    Log.e(TAG, "Invalid JSON from events: ${e.message}")
                } catch (e: InterruptedException) {
                    Log.e(TAG, "Helios libp2p event pump interrupted: ${e.message}")
                    break
                }
            }
            Log.i(TAG, "Stopping helios libp2p event pump, stop node instance.")
            nodeInstance.stop()
        }

        // This is only creating the interface to go-libp2p. There is separate method libP2pStart
        // to start communications. This is more like "connct".
        nodeInstance.start()

        // Test key pair creation here
        // val kg = RsaKeyPairGenerator()
        // var keyStr: String = kg.getPrivateKey()
        // Log.d(TAG, "Generated key parsed: " + keyStr)

        // If privateNetworkId is not stored let it be null we must have p2pNode
        // in order to get the networkId. The node is created in nodeInstance.libP2pStart
        // and only after that we can contruct privateNetworkId and networkId.
        var privateNetworkId = sharedPreferences.getString("privateNetworkId", null)
        if (privateNetworkId == null) {
            Log.d(TAG, "privateNetworkId not yet set")
        }
        val networkId = extractNetworkId(privateNetworkId)
        if (networkId == null) {
            Log.e(TAG, "networkId not yet set")
        }
        val networkIdentity = mapOf(
            "egoId" to identity.userUUID,
            "networkId" to networkId,
            "privateNetworkId" to privateNetworkId
        )

        val sharedRelayAddresses = sharedPreferences.getStringSet("relayAddresses", null)
        val relayAddresses : List<String>
        if (sharedRelayAddresses == null) {
            relayAddresses = listOf("/ip4/130.188.225.47/tcp/28327/ws/p2p/QmUFSesHRPzJxJzLccTtYc4QNoiDSEe3r6KgBwLdTrxpKh")
        } else {
            relayAddresses = sharedRelayAddresses.toList()
        }

        val listenAddrs = ArrayList<String>()
        val bootstrapAddrs = ArrayList<String>(relayAddresses)
        listenAddrs.add("/ip6/::/tcp/0")
        listenAddrs.add("/ip4/0.0.0.0/tcp/0")
        val listIterator = sharedRelayAddresses?.iterator()
        if (listIterator != null) {
            while (listIterator.hasNext()) {
                listenAddrs.add(listIterator.next() + "/p2p-circuit")
            }
        }
        val swarmKeyProtocol = sharedPreferences.getString("swarmKeyProtocol", "/key/swarm/psk/1.0.0/")
        val swarmKeyEncoding = sharedPreferences.getString("swarmKeyEncoding", "/base16/")
        val swarmKeyData = sharedPreferences.getString("swarmKeyData", "bc22c182f9f47f3daebf961a0f5242e4731ae7bf566869b530b527d9ef2d3fb0")
        val clientOptions = mapOf(
                "starNodes" to emptyList<String>(),
                "listenAddrs" to listenAddrs,
                "bootstrapAddrs" to bootstrapAddrs,
                "swarmKeyProtocol" to swarmKeyProtocol,
                "swarmKeyEncoding" to swarmKeyEncoding,
                "swarmKeyData" to swarmKeyData,
        )

        Log.d("DEBUG", listenAddrs.toString())
        Log.d("DEBUG", bootstrapAddrs.toString())

        // This is sending a start command to go-libp2p
        val rv = nodeInstance.libP2pStart(networkIdentity, clientOptions)
        val receivedPrivateId = rv?.get("privateNetworkId") as? String?
        peerId = rv?.get("networkId") as? String

        Log.i(TAG, "Received peer id: ${rv?.javaClass?.name} => $rv")
        Log.i(TAG, "Received private peer id: ${receivedPrivateId?.javaClass?.name} => $receivedPrivateId")

        if (receivedPrivateId != null) with(sharedPreferences.edit()) {
            putString("privateNetworkId", receivedPrivateId)
            commit()
        }

        privatePeerId = receivedPrivateId

        Log.i(TAG, "Publish ego: /helios/ego/peer/${identity.userUUID}")
        // TODO; Check this thread usage. Threading was previously disabled
        thread {
            try {
                nodeInstance.libP2pProvideService("/helios/ego/peer/" + identity.userUUID)
                Log.i(TAG, "Published ego: /helios/ego/peer/${identity.userUUID}")
            } catch (e: RuntimeException) {
                Log.e(
                    TAG,
                    "Error in initial publish ego: /helios/ego/peer/${identity.userUUID}: ${e.message}"
                )
            }
        }
    }

    @Synchronized
    fun stop() {
        lock.withLock {
            announceThread?.interrupt()
            announceThread = null
        }

        eventPump?.interrupt()
        eventPump = null
        node?.stop()
        node = null
    }

    override fun publish(topic: HeliosTopic, message: HeliosMessage) {
        var topicName = "/helios/pubsub"

        topicName += if (topic.topicName.isEmpty() || topic.topicName.startsWith('/')) "" else "/"
        topicName += topic.topicName
        node?.libP2pPublish(topicName, message.message)
    }

    override fun search(pattern: HeliosTopicMatch?): Array<HeliosTopic> {
        TODO("Not yet implemented")
    }

    override fun connect(connection: HeliosConnectionInfo?, identity: HeliosIdentityInfo?) {
        start(identity!!)
    }

    override fun disconnect(connection: HeliosConnectionInfo?, identity: HeliosIdentityInfo?) {
        // Disonnect is a no-op
    }

    override fun subscribe(topic: HeliosTopic, listener: HeliosMessageListener) {
        // FIXME: may miss some events, block event queue processing during sub?
        var topicName = "/helios/pubsub"

        topicName += if (topic.topicName.isEmpty() || topic.topicName.startsWith('/')) "" else "/"
        topicName += topic.topicName

        val subscriptionId = node?.libP2pSubscribe(topicName)
        if (subscriptionId == null) {
            Log.e(
                TAG,
                "Subscription received bad or null identifier, will be ignored: $subscriptionId"
            )
            return
        }
        // TODO: This will fail as subscriptionId in our case is not index
        subscribers[subscriptionId] = listener
    }

    override fun unsubscribe(topic: HeliosTopic?) {
        //TODO("Not yet implemented")
    }

    override fun unsubscribeListener(listener: HeliosMessageListener?) {
        val subs = subscribers
            .filter { it.value === listener }
            .keys
            .toList()

        subs.forEach {
            node?.libP2pUnsubscribe(it)
        }
    }

    fun findPeer(peerId: String, options: Map<String, *>?): HeliosNetworkAddress {
        val rv = node?.libP2pFindPeer(peerId, options) as? List<*> ?: emptyList<String>()
        Log.d(TAG, "Found addresses for $peerId: $rv")

        return HeliosNetworkAddress(
            networkId = peerId,
            networkAddress = rv.filterIsInstance<String>().toMutableList(),
        )
    }

    fun findService(protocolId: String): List<HeliosNetworkAddress> {
        val rv = node?.libP2pFindService(protocolId)
        Log.d(TAG, "Found services for $protocolId: $rv")

        // TODO: Why is empty list returned? This was in nodejs code.
        return emptyList()
    }

    fun provideService(protocolId: String) {
        node?.libP2pProvideService(protocolId)
    }

    fun networkConnectionLost() {
        node?.libP2pNetworkLost()
    }

    fun networkConnectionFound() {
        node?.libP2pNetworkReconnect()
    }

    private val lock = ReentrantLock()
    private val announceTags = HashSet<String>()
    private var announceThread: Thread? = null
    fun announceTag(tag: String) {
        lock.withLock {
            announceTags.add(tag)

            if (announceThread == null) {
                announceThread = thread(start = false) {
                    announcer()
                }
                announceThread?.start()
            }
        }
    }

    fun unannounceTag(tag: String) {
        lock.withLock {
            announceTags.remove(tag)
        }
    }

    private fun announcer() {
        val gson = Gson()

        while (true) {
            try {
                lock.withLock {
                    val timestamp = System.currentTimeMillis()
                    announceTags.forEach {
                        // FIXME: This will cause a huge number of updates
                        // FIXME: time should also be handled by the receiver
                        val topic = HeliosTopic("/helios/tag/$it", "")
                        val htag = HeliosEgoTag(
                            egoId = "",
                            networkId = peerId,
                            tag = it,
                            timestamp = timestamp
                        )

                        try {
                            publish(topic, HeliosMessage(gson.toJson(htag)))
                        } catch (e: RuntimeException) {
                            Log.e(TAG, "Error publishing tag $topic: ${e.message}");
                        }
                    }
                }

                Thread.sleep(60_000)
            } catch (e: InterruptedException) {
                // quit thread
                return;
            }
        }
    }

    private val _tags = ConcurrentHashMap<String, HeliosEgoTag>()
    val tags: LinkedList<HeliosEgoTag>
        get() = LinkedList<HeliosEgoTag>().apply {
            this.addAll(_tags.values)
        }

    fun observeTag(tag: String) {
        val topic = HeliosTopic("/helios/tag/$tag", "")
        val gson = Gson()

        subscribe(topic, object : HeliosMessageListener {
            override fun showMessage(topic: HeliosTopic?, message: HeliosMessage?) {
                try {
                    val htag: HeliosEgoTag =
                        gson.fromJson(message?.message, HeliosEgoTag::class.java) ?: return
                    _tags[htag.hashKey()] = htag

                    // Send the whole LinkedList to our local app (without permissions now)
                    val sendIntent: Intent = Intent().apply {
                        action = "helios_tag_list_update"
                        putExtra("helios_tag_list_update", tags)
                    }
                    context?.let {
                        LocalBroadcastManager.getInstance(it).sendBroadcast(sendIntent)
                    };

                } catch (e: JsonParseException) {
                    // Ignore...
                }
            }
        })
    }

    fun unobserveTag(tag: String) {
        val topic = HeliosTopic("/helios/tag/$tag", "")
        unsubscribe(topic)
    }

    /**
     * Private Network Id includes three fields: id, privateKey, and publicKey. This helper function
     * can be used to extract the id part.
     */
    private fun extractNetworkId(privateNetworkId: String?): String? {
        if (privateNetworkId == null) {
            return null
        }
        val json = JSONObject(privateNetworkId)
        val networkId = json.getString("id")
        return networkId
    }
}
