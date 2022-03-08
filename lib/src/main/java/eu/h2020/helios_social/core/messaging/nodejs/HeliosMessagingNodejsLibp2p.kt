package eu.h2020.helios_social.core.messaging.nodejs

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import eu.h2020.helios_social.core.messaging.*
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashSet
import kotlin.concurrent.thread
import kotlin.concurrent.withLock


class HeliosMessagingNodejsLibp2p : HeliosMessaging {
    var directMessaging: HeliosDirectMessaging? = null
    private var node: NodejsInstance? = null
    private val subscribers = ConcurrentHashMap<String, HeliosMessageListener>()
    private var eventPump: Thread? = null
    var peerId: String? = null
    var privatePeerId: String? = null

    var connectionCount = 0
        private set

    var context: Context? = null

    companion object {
        const val TAG = "HeliosMessagingNodejsLibp2p"
        const val NODE_JS_PACKAGE = "node-files.zip"
        const val NODE_JS_ENTRY = "lib/mobile-client.js"
        const val SHARED_PREFERENCES_FILE = "helios-node-libp2p-prefs"

        @JvmStatic
        val instance: HeliosMessagingNodejsLibp2p by lazy {
            HeliosMessagingNodejsLibp2p()
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

        val nodeDir = NodejsInstance.prepareNodeAsset(appContext, NODE_JS_PACKAGE)
        val nodeInstance = NodejsInstance(arrayOf(File(nodeDir, NODE_JS_ENTRY).path))
        val dm = HeliosDirectMessagingNodejsLibp2p(nodeInstance)

        directMessaging = dm
        node = nodeInstance

        nodeInstance.registerCallable("receive-message", dm::receiveMessage)

        eventPump = thread(name = "helios-libp2p-event") {

            val gson = GsonBuilder()
                .enableComplexMapKeySerialization()
                .create()!!

            Log.i(TAG, "Starting helios libp2p event pump.")
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val msg = nodeInstance.events.take()

                    if (msg !is NodejsMessageEvent) {
                        continue
                    }

                    if (msg.name == "pubsub:message") {
                        val listener = subscribers[msg.id] ?: continue
                        Log.d(TAG, "Pump pubsub: ${msg.stringData}")
                        val pubsubmsg =
                            gson.fromJson(
                                msg.stringData,
                                P2pPubSubMessage::class.java
                            ) ?: continue

                        if (pubsubmsg.topic.isNullOrEmpty()) {
                            continue
                        }

                        val topic = HeliosTopic(
                            pubsubmsg.topic.first().substring("/helios/pubsub/".length),
                            ""
                        )
                        val message = HeliosMessageLibp2pPubSub(
                            pubsubmsg.getStringData(),
                            networkAddress = HeliosNetworkAddress(networkId = pubsubmsg.networkId)
                        )


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

        nodeInstance.start()

        val privateNetworkId =
            sharedPreferences.getString("privateNetworkId", null)
        //sharedPreferences.getString("${identity.userUUID}-privateNetworkId", null)
        val networkIdentity = mapOf(
            "egoId" to identity.userUUID,
            "networkId" to privateNetworkId
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

        val rv = nodeInstance.callMethod("start", arrayOf(networkIdentity, clientOptions)) as? Map<*, *>
        val receivedPrivateId = rv?.get("privateNetworkId") as? String?
        peerId = rv?.get("networkId") as? String

        Log.i(TAG, "Received peer id: ${rv?.javaClass?.name} => $rv")
        Log.i(
            TAG,
            "Received private peer id: ${receivedPrivateId?.javaClass?.name} => $receivedPrivateId"
        )

        if (receivedPrivateId != null) with(sharedPreferences.edit()) {
            //putString("${identity.userUUID}-privateNetworkId", receivedPrivateId)
            putString("privateNetworkId", receivedPrivateId)
            commit()
        }

        privatePeerId = receivedPrivateId

        Log.i(TAG, "Publish ego: /helios/ego/peer/${identity.userUUID}")
        // TODO
        // thread {
            try {
                nodeInstance.callMethod(
                    "provide-service",
                    arrayOf("/helios/ego/peer/" + identity.userUUID)
                )
                Log.i(TAG, "Published ego: /helios/ego/peer/${identity.userUUID}")
            } catch (e: RuntimeException) {
                Log.e(TAG, "Error in initial publish ego: /helios/ego/peer/${identity.userUUID}: ${e.message}")
            }
       //  }
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
    }

    override fun publish(topic: HeliosTopic, message: HeliosMessage) {
        var topicName = "/helios/pubsub"

        topicName += if (topic.topicName.isEmpty() || topic.topicName.startsWith('/')) "" else "/"
        topicName += topic.topicName

        node?.callMethod(
            "publish", arrayOf(
                topicName,
                message.message
            )
        )
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

        val subscriptionId = node?.callMethod("subscribe", arrayOf(topicName))

        if (subscriptionId == null || subscriptionId !is String) {
            Log.e(
                TAG,
                "Subscription received bad or null identifier, will be ignored: $subscriptionId"
            )
            return
        }

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
            node?.callMethod("unsubscribe", arrayOf(it))
        }
    }

    fun findPeer(peerId: String, options: Map<String, *>?): HeliosNetworkAddress {
        val rv = node?.callMethod("find-peer", arrayOf(peerId, options)) as? List<*> ?: emptyList<String>()
        Log.d(TAG, "Found addresses for $peerId: $rv")

        return HeliosNetworkAddress(
            networkId = peerId,
            networkAddress = rv.filterIsInstance<String>().toMutableList(),
        )
    }

    fun findService(protocolId: String): List<HeliosNetworkAddress> {
        val rv = node?.callMethod("find-service", arrayOf(protocolId)) as? List<*>
        Log.d(TAG, "Found services for $protocolId: $rv")

        return emptyList()
    }

    fun provideService(protocolId: String) {
        node?.callMethod("provide-service", arrayOf(protocolId))
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
}
