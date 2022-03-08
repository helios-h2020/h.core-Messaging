package eu.h2020.helios_social.core.messaging;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonParseException;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.h2020.helios_social.core.messaging.data.HeliosConversation;
import eu.h2020.helios_social.core.messaging.data.HeliosConversationList;
import eu.h2020.helios_social.core.messaging.data.HeliosMessagePart;
import eu.h2020.helios_social.core.messaging.data.HeliosTopicContext;
import eu.h2020.helios_social.core.messaging.data.JsonMessageConverter;
import eu.h2020.helios_social.core.messaging.data.StorageHelperClass;
import eu.h2020.helios_social.core.messaging.db.HeliosMessageStore;
import eu.h2020.helios_social.core.messaging.streamr.HeliosMessagingStreamrLibp2p;
import eu.h2020.helios_social.core.messaging.sync.HeartbeatDataException;
import eu.h2020.helios_social.core.messaging.sync.HeartbeatManager;
import eu.h2020.helios_social.core.messaging.sync.SyncManager;
import eu.h2020.helios_social.core.messaging.HeliosDirectMessaging;
import eu.h2020.helios_social.core.messaging.HeliosEgoTag;
import eu.h2020.helios_social.core.messaging.HeliosMessageLibp2pPubSub;
import eu.h2020.helios_social.core.messaging.HeliosMessagingReceiver;
import eu.h2020.helios_social.core.messaging.HeliosNetworkAddress;
import kotlin.Unit;

import static eu.h2020.helios_social.core.messaging.MessagingConstants.HELIOS_DIRECT_CHAT_FILE_PROTO;
import static eu.h2020.helios_social.core.messaging.MessagingConstants.HELIOS_DIRECT_CHAT_PROTO;
import static eu.h2020.helios_social.core.messaging.MessagingConstants.HELIOS_CHAT_SYNC_PROTO;
import static eu.h2020.helios_social.core.messaging.MessagingConstants.HELIOS_STATUS_PROTO;

public class ReliableHeliosMessagingNodejsLibp2pImpl implements HeliosMessaging, HeliosDirectMessaging, HeliosConnect {
    private static final String TAG = "ReliableHeliosMessagingNodejsLibp2pImpl";
    private static ReliableHeliosMessagingNodejsLibp2pImpl sInstance = new ReliableHeliosMessagingNodejsLibp2pImpl();
    private android.content.Context mContext = null;
    // private HeliosMessagingNodejsLibp2p mHeliosMessagingNodejs = HeliosMessagingNodejsLibp2p.getInstance();
    private HeliosMessagingStreamrLibp2p mHeliosMessagingNodejs = HeliosMessagingStreamrLibp2p.getInstance();
    private boolean mConnected = false;
    private ConnectivityManager.NetworkCallback mNetworkCallback = null;
    private ConnectivityManager mConnectivityManager;
    private String mActiveNetworkInterface = null;
    private AtomicBoolean mReconnecting = new AtomicBoolean();

    private HeartbeatManager mHeartbeatManager = HeartbeatManager.getInstance();
    private HeliosIdentityInfo mHeliosIdentityInfo = null;

    private static final String STATUS_IS_ONLINE = "STATUS_IS_ONLINE";
    private static final String ACK_STATUS_IS_ONLINE = "ACK_STATUS_IS_ONLINE";
    private static final String PUB_SUB_PACKAGED = "PUB_SUB_PACKAGED";
    private HashMap<String, HeliosMessageListener> mSubscribers = new HashMap<>();
    private HashMap<String, HeliosMessagingReceiver> mDirectMessageReceivers = new HashMap<>();
    private HeliosReceiver mHeliosReceiver = new HeliosReceiver();

    private HeliosDirectMessagingImpl mHeliosDirectMessaging = new HeliosDirectMessagingImpl();
    private HeliosMessageStore mChatMessageStore = null;
    private boolean mFilterHeartbeatMsg = true;
    private boolean mFilterJoinMsg = true;
    private boolean mRegisteredSyncReceiver = false;
    private ExecutorService mExecutorService;

    public void setFilterJoinMsg(boolean filter) {
        mFilterJoinMsg = filter;
    }

    /**
     * Get instance of this class.
     *
     * @return {@link ReliableHeliosMessagingNodejsLibp2pImpl}
     */
    public static ReliableHeliosMessagingNodejsLibp2pImpl getInstance() {
        return sInstance;
    }

    /**
     * Set context to be used when binding/unbinding service.
     *
     * @param ctx {@link android.content.Context}
     */
    public void setContext(Context ctx) {
        Log.d(TAG, "setContext");
        mContext = ctx;

        mExecutorService = Executors.newFixedThreadPool(7);
        mChatMessageStore = new HeliosMessageStore(mContext);
        // Expire by default stored messages older than a week
        mChatMessageStore.deleteExpiredEntries(ZonedDateTime.now().minusDays(7).toInstant().toEpochMilli());

        mHeliosMessagingNodejs.setContext(mContext);

        // Setup connectivity listener
        mNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                String name = getNetworkInterfaceName(network);
                Log.d(TAG, "NETWORK AVAILABLE MESSAGE " + name);
                Toast.makeText(mContext, "Network available " + name, Toast.LENGTH_LONG).show();
                Network activeNetwork = mConnectivityManager.getActiveNetwork();
                if (activeNetwork != null) {
                    String active = getNetworkInterfaceName(activeNetwork);
                    if (!active.equals(mActiveNetworkInterface)) {
                        try {
                            reconnect();
                        } catch (HeliosMessagingException e) {
                            Log.d(TAG, "Reconnection failed + e");
                            Toast.makeText(mContext, "Reconnection failed", Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }

            @Override
            public void onLost(Network network) {
                Log.d(TAG, "NETWORK LOST MESSAGE");
                Toast.makeText(mContext, "Network lost - reconnect", Toast.LENGTH_LONG).show();
                Network activeNetwork = mConnectivityManager.getActiveNetwork();
                if (activeNetwork != null) {
                    String active = getNetworkInterfaceName(activeNetwork);
                    if (!active.equals(mActiveNetworkInterface)) {
                        new Thread(() -> {
                            try {
                                reconnect();
                            } catch (HeliosMessagingException e) {
                                Log.d(TAG, "Reconnection failed + e");
                                Toast.makeText(mContext, "Reconnection failed", Toast.LENGTH_LONG).show();
                            }
                        }).start();
                    }
                }

            }

        };
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        NetworkRequest nr = builder.build();
        mConnectivityManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network active = mConnectivityManager.getActiveNetwork();
        if (active != null) {
            mActiveNetworkInterface = getNetworkInterfaceName(active);
        }
        mConnectivityManager.registerNetworkCallback(nr, mNetworkCallback);
    }

    private void reconnect() throws HeliosMessagingException {
        boolean alreadyReconnecting = mReconnecting.getAndSet(true);
        if (alreadyReconnecting) {
            return;
        }

        Log.d(TAG, "DEACTIVATE HEARTBEAT MANAGER");
        mHeartbeatManager.deactivate();
        // mChatMessageStore.closeDatabase();
        Log.d(TAG, "STOP MESSAGING NODE");
        mHeliosMessagingNodejs.stop();
        mConnected = false;

        // Log.d(TAG, "NETWORK INTERFACE LOST");
        // mHeliosMessagingNodejs.networkConnectionLost();

        try {
            Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
            while (networks.hasMoreElements()) {
                NetworkInterface network = (NetworkInterface)networks.nextElement();
                String name = network.getDisplayName();
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                Log.d(TAG, "Network interface " + name);
                while (addresses.hasMoreElements()) {
                    InetAddress address = (InetAddress)addresses.nextElement();
                    Log.d(TAG, "  Addr: " + address.toString());
                }
            }
        } catch (SocketException e) {
            Log.d(TAG, "Socket exception " + e);
        }

        Network activeNetwork = null;
        String activeNetworkInterface = null;
        do {
            activeNetwork = mConnectivityManager.getActiveNetwork();
            if (activeNetwork != null) {
                activeNetworkInterface = getNetworkInterfaceName(activeNetwork);
                Log.d(TAG, "Active interface is now " + activeNetworkInterface);
            } else {
                Log.d(TAG, "NO ACTIVE NETWORK");
                SystemClock.sleep(1000);
            }
        } while (activeNetwork == null);
        mActiveNetworkInterface = activeNetworkInterface;
        //Log.d(TAG, "NETWORK INTERFACE ACTIVE");
        //mHeliosMessagingNodejs.networkConnectionFound();

        Log.d(TAG, "START MESSAGING NODE");
        mHeliosMessagingNodejs.start(mHeliosIdentityInfo, mContext);
        Log.d(TAG, "ADD INTERNAL RECEIVER");
        addDirectReceiverInternal();
        mConnected = true;
        Log.d(TAG, "ACTIVATE HEARTBEAT MANAGER");
        mHeartbeatManager.activate();

        Log.d(TAG, "RECONNECT DONE");
        mReconnecting.set(false);
    }

    private String getNetworkInterfaceName(Network network) {
        LinkProperties link = mConnectivityManager.getLinkProperties(network);
        if (link == null) {
            return new String("Unknown");
        }
        List<LinkAddress> addresses = link.getLinkAddresses();
        String name = new String(link.getInterfaceName());
        for (LinkAddress address: addresses) {
            name += " " + address.toString();
        }
        Log.d(TAG, "Name: " + name);
        return name;
    }

    /**
     * Expire old messages from message cache.
     *
     * @param threshold Messages older than this time will be removed
     * @throws HeliosMessagingException Uninitialized message store connection
     */
    public void expire(ZonedDateTime threshold) throws HeliosMessagingException {
        // An attempt to call this method before calling setContext method
        if (mChatMessageStore == null) {
            throw new HeliosMessagingException("Message store is not initialized", null);
        }
        mChatMessageStore.deleteExpiredEntries(threshold.toInstant().toEpochMilli());
    }

    private HeliosMessagePart createHeliosMessagePart(HeliosTopic topic, HeliosMessage message) {
        return createHeliosMessagePart(topic, message, HeliosMessagePart.MessagePartType.MESSAGE);
    }

    private HeliosMessagePart createHeliosMessagePart(HeliosTopic topic, HeliosMessage message, HeliosMessagePart.MessagePartType messageType) {
        String ts = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now());

        HeliosMessagePart msg = new HeliosMessagePart(message.getMessage(), mHeliosIdentityInfo.getNickname(), mHeliosIdentityInfo.getUserUUID(), topic.getTopicName(), ts, messageType);
        msg.senderNetworkId = mHeliosMessagingNodejs.getPeerId();
        msg.mediaFileName = message.getMediaFileName();

        return msg;
    }

    private HeliosMessagePart createDMHeliosMessagePart(HeliosNetworkAddress address, @NotNull byte[] data) {
        String ts = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now());

        // Support JSON format
        String stringData = new String(data, StandardCharsets.UTF_8);
        HeliosMessagePart msg = new HeliosMessagePart(stringData, mHeliosIdentityInfo.getNickname(), mHeliosIdentityInfo.getUserUUID(), address.getNetworkId(), ts);
        msg.senderNetworkId = mHeliosMessagingNodejs.getPeerId();
        return msg;
    }

    // TODO: New methods, move to interface once approved
    // -------------------------------------
    /**
     * Get the current interval used to send heartbeats to group topics.
     * @return current heartbeat interval in milliseconds
     */
    public int getHeartbeatInterval() {
        return mHeartbeatManager.getHeartbeatInterval();
    }

    /**
     * Set heartbeat interval used to send heartbeats to group topics.
     *
     * @param milliseconds New interval in milliseconds (must be >100)
     */
    public void setHeartbeatInterval(int milliseconds) {
        mHeartbeatManager.setHeartbeatInterval(milliseconds);
    }

    /**
     * Send a status message (STATUS_IS_ONLINE) to the given HeliosNetworkAddress (networkId).
     * If the other peer is online, it will send a status message with ACK_STATUS_IS_ONLINE as the msg.
     *
     * @param address HeliosNetworkAddress to send the status to.
     */
    public void sendOnlineStatusTo(HeliosNetworkAddress address) {
        mHeartbeatManager.sendIsOnlineTo(this, this, HELIOS_STATUS_PROTO, STATUS_IS_ONLINE, address);
    }

    /**
     * Send sendOnlineStatusTo to a list of addresses.
     *
     * @param addresses List of HeliosNetworkAddress
     */
    public void sendOnlineStatusTo(List<HeliosNetworkAddress> addresses) {
        for(HeliosNetworkAddress address: addresses) {
            sendOnlineStatusTo(address);
        }
    }

    /**
     * Get a list of online users for a topic. Their last seen timestamp is available for checking
     * when they have been seen (HeliosEgoTag timestamp).
     *
     * @param topic Name of the topic
     * @return List of online users as HeliosEgoTag
     */
    public List<HeliosEgoTag> getTopicOnlineUsers(String topic) {
        return mHeartbeatManager.getTopicOnlineUsers(topic);
    }

    /**
     * Checks if given HeliosNetworkAddress is seen and if so, adds it to a list of HeliosEgoTag
     * containing the last timestamp (System.currentTimeMillis()) the address has been seen.
     * If address is not seen, it is not returned. HeliosEgoTag's networkId matches the HeliosNetworkAddresses networkId.
     *
     * @param userHeliosAddresses ArrayList of HeliosNetworkAddress
     * @return List of HeliosEgoTag
     */
    public List<HeliosEgoTag> getCurrentOnlineStatus(ArrayList<HeliosNetworkAddress> userHeliosAddresses) {
        return mHeartbeatManager.getCurrentOnlineStatus(userHeliosAddresses);
    }


    // HeliosMessagingNodejsLibp2p methods
    // -------------------------------------

    /**
     * Get list of tags and related peers - called by any interested activity
     *
     * @return List<HeliosEgoTag> a list of peers announcing the subscribed tags
     */
    public List<HeliosEgoTag> getTags() {
        if (mHeliosMessagingNodejs == null) {
            return new LinkedList<>();
        }

        return mHeliosMessagingNodejs.getTags();
    }

    public void announceTag(String tag) {

        Log.d(TAG, "announceTag:" + tag);
        if (mHeliosMessagingNodejs == null) {
            return;
        }

        mHeliosMessagingNodejs.announceTag(tag);
    }

    public void unannounceTag(String tag) {
        Log.d(TAG, "unannounceTag:" + tag);
        if (mHeliosMessagingNodejs == null) {
            return;
        }

        mHeliosMessagingNodejs.unannounceTag(tag);
    }

    public void observeTag(String tag) {
        Log.d(TAG, "observeTag:" + tag);
        if (mHeliosMessagingNodejs == null) {
            return;
        }

        mHeliosMessagingNodejs.observeTag(tag);
    }

    public void unobserveTag(String tag) {
        Log.d(TAG, "unobserveTag:" + tag);
        if (mHeliosMessagingNodejs == null) {
            return;
        }

        mHeliosMessagingNodejs.unobserveTag(tag);
    }

    /**
     * Stop messaging client.
     */
    public void stop() {
        mHeartbeatManager.stop();
        mChatMessageStore.closeDatabase();

        mHeliosMessagingNodejs.stop();
        mConnected = false;
    }

    // HeliosMessaging methods
    // -------------------------------------
    @Override
    public void connect(HeliosConnectionInfo connection, HeliosIdentityInfo identity) throws HeliosMessagingException {
        if (mConnected) {
            Log.d(TAG, "connect() already connected. Disconnect first if connection info changed.");
            return;
        }
        mHeliosIdentityInfo = new HeliosIdentityInfo(identity.getNickname(), identity.getUserUUID());

        mHeliosMessagingNodejs.connect(connection, mHeliosIdentityInfo);
        Log.d(TAG, "mHeliosMessagingNodejs.connect done");

        addDirectReceiverInternal();
        Log.d(TAG, "mHeartbeatManager.start");
        mHeartbeatManager.start(this, this, mHeliosIdentityInfo);

        mConnected = true;
    }

    @Override
    public void disconnect(HeliosConnectionInfo connection, HeliosIdentityInfo identity) throws HeliosMessagingException {
        mHeliosMessagingNodejs.disconnect(connection, identity);
    }

    /**
     * Publish (oublish-subscribe) group message in topic
     * @param topic {@link HeliosTopic} Group topic
     * @param message {@link HeliosMessage} JSON format message to be published.(must be JSON)
     * @throws HeliosMessagingException thrown if non-JSON message
     */
    @Override
    public void publish(HeliosTopic topic, HeliosMessage message) throws HeliosMessagingException {
        HeliosMessagePart msgPart = null;

        // Check if message is already a HeliosMessagePart
        try {
            msgPart = JsonMessageConverter.getInstance().readHeliosMessagePart(message.getMessage());
        } catch (Exception e) {
            throw new HeliosMessagingException("JSON parsing failed", e);
        }

        // If provided message.getMessage() was already a HeliosMessagePart
        if (isRepackNeeded(msgPart)) {
            // Publish was not a HeliosMessagePart, creating new HeliosMessagePart first
            HeliosMessagePart heliosMessagePart = createHeliosMessagePart(topic, message);
            String mediaFileName = heliosMessagePart.mediaFileName;
            heliosMessagePart.mediaFileName = null;
            heliosMessagePart.protocol = PUB_SUB_PACKAGED;
            HeliosMessage newHeliosMessage = new HeliosMessage(JsonMessageConverter.getInstance().convertToJson(heliosMessagePart), mediaFileName);
            mHeliosMessagingNodejs.publish(topic, newHeliosMessage);
        } else {
            // Already HeliosMessagePart message
            mHeliosMessagingNodejs.publish(topic, message);
        }

        // Store to DB not required here.
        // Message is saved when the client receives it themselves from the subscribe (even local)
        // Otherwise, we could check in there for duplicate and store here.
    }

    @Override
    public void subscribe(HeliosTopic topic, HeliosMessageListener listener) throws HeliosMessagingException {
        // Create topic structure if not existing
        joinNewTopic(topic.getTopicName());

        // Subscribe internally to this topic, to provide sync
        mHeliosMessagingNodejs.subscribe(topic, mHeliosReceiver);

        // Add to map of subscribes to send message to actual listener
        mSubscribers.put(topic.getTopicName(), listener);

        // Could notify with a join-message to topic?
    }

    /**
     * Internally create a new structure for a new topic, if not existing.
     *
     * @param topic TopicName to check
     */
    private void joinNewTopic(String topic) {
        Log.d(TAG, "joinNewTopic :" + topic);

        if (!TextUtils.isEmpty(topic)) {
            ArrayList<HeliosTopicContext> arrTopics = HeliosConversationList.getInstance().getTopics();
            for (int i = 0; i < arrTopics.size(); i++) {
                HeliosTopicContext tpc = arrTopics.get(i);
                if (tpc.topic.equals(topic)) {
                    Log.d(TAG, "Topic already exists, not creating a new:" + topic);
                    return;
                }
            }
            createConversation(topic);
        }
    }

    private void createConversation(String topicName) {
        Log.d(TAG, "createConversation with topic :" + topicName);
        HeliosConversation defaultConversation = new HeliosConversation();
        defaultConversation.topic = new HeliosTopicContext(topicName, "-", "-", "-");
        HeliosConversationList.getInstance().addConversation(defaultConversation);
    }

    @Override
    public void unsubscribe(HeliosTopic topic) throws HeliosMessagingException {
        mHeliosMessagingNodejs.unsubscribe(topic);
    }

    @Override
    public void unsubscribeListener(HeliosMessageListener listener) throws HeliosMessagingException {
        mHeliosMessagingNodejs.unsubscribeListener(listener);
    }

    @Override
    public HeliosTopic[] search(HeliosTopicMatch pattern) throws HeliosMessagingException {
        return mHeliosMessagingNodejs.search(pattern);
    }

    /**
     * Add receivers for currently supported internal protocols.
     */
    private void addDirectReceiverInternal() {
        Log.d(TAG, "check add internal receiver for sync");
        // register internal protocols
        // HELIOS_CHAT_SYNC_PROTO
        if (!mRegisteredSyncReceiver) {
            Log.d(TAG, "adding receiver to HELIOS_CHAT_SYNC_PROTO");
            // Register to real messagingNodeJs with internal receiver
            mHeliosMessagingNodejs.getDirectMessaging().addReceiver(HELIOS_CHAT_SYNC_PROTO, mDirectHeliosMessagingReceiver);
            mHeliosMessagingNodejs.getDirectMessaging().addReceiver(HELIOS_STATUS_PROTO, mDirectHeliosMessagingReceiver);
            mRegisteredSyncReceiver = true;
        }
    }

    /**
     * Return HeliosDirectMessaging implementation for user.
     *
     * @return HeliosDirectMessaging implementation.
     */
    public HeliosDirectMessaging getDirectMessaging() {
        return mHeliosDirectMessaging;
    }

    private class HeliosDirectMessagingImpl implements HeliosDirectMessaging {
        @Override
        public void addReceiver(@NotNull String protocolId, @NotNull HeliosMessagingReceiver receiver) {
            ReliableHeliosMessagingNodejsLibp2pImpl.this.addReceiver(protocolId, receiver);
        }

        @Override
        public void removeReceiver(@NotNull String protocolId) {
            ReliableHeliosMessagingNodejsLibp2pImpl.this.removeReceiver(protocolId);
        }

        @NotNull
        @Override
        public HeliosNetworkAddress resolve(@NotNull String egoId) {
            return ReliableHeliosMessagingNodejsLibp2pImpl.this.resolve(egoId);
        }

        @NotNull
        @Override
        public Future<HeliosNetworkAddress> resolveFuture(@NotNull String egoId) {
            return ReliableHeliosMessagingNodejsLibp2pImpl.this.resolveFuture(egoId);
        }

        @Override
        public void sendTo(@NotNull HeliosNetworkAddress address, @NotNull String protocolId, @NotNull byte[] data) {
            ReliableHeliosMessagingNodejsLibp2pImpl.this.sendTo(address, protocolId, data);
        }

        @NotNull
        @Override
        public Future<Unit> sendToFuture(@NotNull HeliosNetworkAddress address, @NotNull String protocolId, @NotNull byte[] data) {
            return ReliableHeliosMessagingNodejsLibp2pImpl.this.sendToFuture(address, protocolId, data);
        }
    }

    // HeliosDirectMessaging methods
    // -------------------------------------
    @NotNull
    @Override
    public HeliosNetworkAddress resolve(@NotNull String egoId) {
        return mHeliosMessagingNodejs.getDirectMessaging().resolve(egoId);
    }

    @NotNull
    @Override
    public Future<HeliosNetworkAddress> resolveFuture(@NotNull String egoId) {
        return mHeliosMessagingNodejs.getDirectMessaging().resolveFuture(egoId);
    }

    @Override
    public void sendTo(@NotNull HeliosNetworkAddress address, @NotNull String protocolId, @NotNull byte[] data) {
        // Handle specific protocolIds separately
        if (HELIOS_DIRECT_CHAT_FILE_PROTO.equals(protocolId)) {
            Log.d(TAG, "sendTo protocolId: HELIOS_DIRECT_CHAT_FILE_PROTO");
            mHeliosMessagingNodejs.getDirectMessaging().sendTo(address, protocolId, data);
        } else if (HELIOS_DIRECT_CHAT_PROTO.equals(protocolId)) {
            Log.d(TAG, "sendTo protocolId: HELIOS_DIRECT_CHAT_PROTO");
            mHeliosMessagingNodejs.getDirectMessaging().sendTo(address, protocolId, data);
        } else if (HELIOS_STATUS_PROTO.equals(protocolId)) {
            Log.d(TAG, "sendTo protocolId: HELIOS_STATUS_PROTO");
            // Convert to HeliosMessagePart
            HeliosMessagePart heliosMessagePart = createDMHeliosMessagePart(address, data);
            heliosMessagePart.protocol = protocolId;

            try {
                Log.d(TAG, "sendTo status start.");
                mHeliosMessagingNodejs.getDirectMessaging().sendTo(address, protocolId, JsonMessageConverter.getInstance().convertToJson(heliosMessagePart).getBytes());
                Log.d(TAG, "sendTo status done.");
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "HELIOS_STATUS_PROTO sendTo ERROR:" + e.toString());
                //throw e;
            }
        } else {
            Log.d(TAG, "sendTo protocolId: " + protocolId);
            // Handle other protocols (non-TestClient protocols)

            // Convert to HeliosMessagePart in order to sync
            HeliosMessagePart heliosMessagePart = createDMHeliosMessagePart(address, data);
            heliosMessagePart.protocol = protocolId;

            // Now sending the message using mHeliosMessagingNodejs connection. The code is using
            // sendTo method that does not always throw an exeception. That is why we will first
            // store the message as undelivered and change the flag (msgReceived) as true, which
            // means that the message is considered to be delivered.
            // TODO: Exception handling cleanup is needed. Catch-part is never run?
            try {
                Log.d(TAG, "sendTo start.");
                heliosMessagePart.msgReceived = false;
                mChatMessageStore.addMessage(heliosMessagePart);
                Log.d(TAG, "sendTo preliminary store.");
                mHeliosMessagingNodejs.getDirectMessaging().sendTo(address, protocolId, JsonMessageConverter.getInstance().convertToJson(heliosMessagePart).getBytes());
                Log.d(TAG, "sendTo done.");
                heliosMessagePart.msgReceived = true;
                mChatMessageStore.addMessage(heliosMessagePart);
                Log.d(TAG, "sendTo final store.");
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "sendTo ERROR:" + e.toString());
                heliosMessagePart.msgReceived = false;
                mChatMessageStore.addMessage(heliosMessagePart);
                Log.e(TAG, "sendTo error occurred, saved message for later sync.");
                throw e;
            }
        }

        // TODO: Do we have undelivered messages to address? Should we try to sync?
    }

    @NotNull
    @Override
    public Future<Unit> sendToFuture(@NotNull HeliosNetworkAddress address, @NotNull String protocolId, @NotNull byte[] data) {
        Log.d(TAG, "sendToFuture protocolId:" + protocolId);
        // Use internal executor to be able to store and sync these messages.
        @SuppressWarnings("unchecked")
        Future<Unit> res = (Future<Unit>) mExecutorService.submit(() -> {
            Log.d(TAG, "sendToFuture start protocolId:" + protocolId);
            sendTo(address, protocolId, data);
            Log.d(TAG, "sendToFuture done protocolId:" + protocolId);
        });
        return res;
    }

    @Override
    public void addReceiver(@NotNull String protocolId, @NotNull HeliosMessagingReceiver receiver) {
        Log.d(TAG, "addReceiver protocolId:" + protocolId);

        // Register receiver internally
        mDirectMessageReceivers.put(protocolId, receiver);

        // Let's not add any internal receivers again to mHeliosMessagingNodejs
        if(!HELIOS_CHAT_SYNC_PROTO.equals(protocolId) &&
                !HELIOS_STATUS_PROTO.equals(protocolId) ) {

            // Register new real receivers to real messagingNodeJs with internal receiver
            mHeliosMessagingNodejs.getDirectMessaging().addReceiver(protocolId, mDirectHeliosMessagingReceiver);
        } else {
            Log.d(TAG, "addReceiver, internal receivers not added again:" + protocolId);
        }
    }

    @Override
    public void removeReceiver(@NotNull String protocolId) {
        Log.d(TAG, "removeReceiver protocolId:" + protocolId);

        // Let's not remove any internal receivers from mHeliosMessagingNodejs
        if(!HELIOS_CHAT_SYNC_PROTO.equals(protocolId) &&
              !HELIOS_STATUS_PROTO.equals(protocolId) ) {

            // Remove real receiver
            mHeliosMessagingNodejs.getDirectMessaging().removeReceiver(protocolId);
        } else {
            Log.d(TAG, "removeReceiver, internal receiver not removed:" + protocolId);
        }

        // Remove also internal receiver
        mDirectMessageReceivers.remove(protocolId);
    }

    /**
     * Handle HELIOS_STATUS_PROTO received in DM internally. Actions:
     * 1) Save status for the seen peer.
     * 2) Report back our online status.
     * 3) Sync messages to the peer, if undelivered.
     *
     * @param address HeliosNetworkAddress from which we got a status
     * @param data data - JSON HeliosMessagePart
     */
    private void handleStatusProtoFromPeer(HeliosNetworkAddress address, byte[] data) {
        Log.d(TAG, "Received status from " + address.getNetworkId());
        // TODO: Should handle lightly since this is called by node thread.
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            //Log.d(TAG, "Received status: " + json);
            HeliosMessagePart msg = JsonMessageConverter.getInstance().readHeliosMessagePart(json);
            Log.d(TAG, "Received status msg: " + msg.msg);
            Log.d(TAG, "Received status senderNetworkId: " + msg.senderNetworkId);
            //Log.d(TAG, "Received status msg.senderUUID: " + msg.senderUUID);

            // Is this peer in our contacts?
            boolean syncDM = false;

            if(STATUS_IS_ONLINE.equals(msg.msg)){
                // Update seen status for peer
                mHeartbeatManager.updateUserOnline(address, msg);

                // Send ACK (also tell the other we are online)
                mHeartbeatManager.sendIsOnlineTo(this, this, HELIOS_STATUS_PROTO, ACK_STATUS_IS_ONLINE, address);

                syncDM = true;
            } else if(ACK_STATUS_IS_ONLINE.equals(msg.msg)){
                // Update seen status for peer
                mHeartbeatManager.updateUserOnline(address, msg);
                syncDM = true;
            }

            if(syncDM) {
                // Check DM sync status and sync if messages not delivered to this peer.
                // SyncManager searches both msg.senderUUID and msg.senderNetworkId messages.
                SyncManager syncMgr = SyncManager.getInstance();
                syncMgr.syncDirectMessages(mContext, msg.senderUUID,
                        msg.senderNetworkId, mChatMessageStore, mDirectMessageReceivers);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Error handling status proto: " + e.getMessage());
        }
    }

    /**
     * Get the current public peer id of the client.
     * Note: You need to call connect before calling this, or peer id is null.
     *
     * @return The public peer id or null if not connected.
     */
    public String getPeerId() {
        if( mConnected ) {
            return mHeliosMessagingNodejs.getPeerId();
        } else {
            return null;
        }
    }

    private HeliosMessagingReceiver mDirectHeliosMessagingReceiver = new HeliosMessagingReceiver() {
        @Override
        public void receiveMessage(@NotNull HeliosNetworkAddress address, @NotNull String protocolId, @NotNull FileDescriptor fd) {
            Log.d(TAG, "receiveMessage FileDescriptor()");

            ByteArrayOutputStream ba = new ByteArrayOutputStream();
            try (FileInputStream fileInputStream = new FileInputStream(fd)) {
                int byteRead;
                while ((byteRead = fileInputStream.read()) != -1) {
                    ba.write(byteRead);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            receiveMessage(address, protocolId, ba.toByteArray());
        }

        private void handleSyncProto(HeliosNetworkAddress address, byte[] data) {
            Log.d(TAG, "Received resend sync from " + address.getNetworkId());
            try {
                String json = new String(data, StandardCharsets.UTF_8);
                Log.d(TAG, "Received resend sync: " + json);
                HeliosMessagePart msg = JsonMessageConverter.getInstance().readHeliosMessagePart(json);
                HeliosTopic topic = new HeliosTopic(msg.to, "");
                HeliosMessage tempMsg = new HeliosMessage(json);

                // Forward synced msg to regular pub-sub handler
                mHeliosReceiver.showMessage(topic, tempMsg);

                mHeartbeatManager.updateUserOnline(address, msg);
            } catch (RuntimeException e) {
                Log.e(TAG, "Error handling resend sync proto: " + e.getMessage());
            }
        }

        @Override
        public void receiveMessage(@NotNull HeliosNetworkAddress address, @NotNull String protocolId, @NotNull byte[] data) {
            Log.d(TAG, "receiveMessage()");
            Log.d(TAG, "address:" + address);
            Log.d(TAG, "protocolId:" + protocolId);
            /*String temp = new String(data, StandardCharsets.UTF_8);
            if (!protocolId.equals(HELIOS_DIRECT_CHAT_FILE_PROTO)) {
                Log.d(TAG, "receiveMessage temp message: " + temp);
            }*/
            Log.d(TAG, "-------------------------------");
            // TODO: How to handle if we don't "know" the user? Handle known users?

            // Sync messages received in DM are handled separately
            if (HELIOS_CHAT_SYNC_PROTO.equals(protocolId)) {
                handleSyncProto(address, data);
                return;
            }

            // Status messages received in DM are handled separately
            if (HELIOS_STATUS_PROTO.equals(protocolId)) {
                handleStatusProtoFromPeer(address, data);
                // TODO: Now sending status to any registered listeners, but not storing to db.
                //return;
            }

            String message = null;
            if (!MessagingConstants.HELIOS_DIRECT_CHAT_FILE_PROTO.equals(protocolId) &&
                    !MessagingConstants.HELIOS_DIRECT_CHAT_PROTO.equals(protocolId) &&
                    !MessagingConstants.HELIOS_STATUS_PROTO.equals(protocolId)){
                try {
                    Log.d(TAG, "receiveMessage protocolId:" + protocolId + ", address:" + address.getNetworkId());
                    String messageJson = new String(data, StandardCharsets.UTF_8);
                    HeliosMessagePart msgPart = JsonMessageConverter.getInstance().readHeliosMessagePart(messageJson);
                    msgPart.msgReceived = true;
                    message = msgPart.msg;
                    HeliosTopic topic = new HeliosTopic("DIRECT_PROTO", "");
                    Log.d(TAG, "receiveMessage protocolId:" + protocolId + ", msgPart.msg:" + msgPart.msg);

                    // We could save online status in the beginning, from HeliosNetworkAddress & ts only.
                    mHeartbeatManager.updateUserOnline(address, msgPart);
                    
                    storeHeliosMessage(topic, msgPart, true, address);
                } catch (Exception e) {
                    // TODO: notify error
                    Log.e(TAG, "receiveMessage Exception while reading HeliosMessagePart: " + e.toString());
                    return;
                }
            }

            // Check the internal receivers, though already stored above if known
            if (mDirectMessageReceivers.containsKey(protocolId)) {
                Log.d(TAG, "receiveMessage Internal receiver found for protocolId: " + protocolId);
                //TODO: do we need to extract original data?

                // Don't forward HELIOS_DIRECT_CHAT_FILE_PROTO now
                if (HELIOS_DIRECT_CHAT_PROTO.equals(protocolId)) {
                    Log.d(TAG, "receiveMessage forwarding message.data: " + HELIOS_DIRECT_CHAT_PROTO);
                    mDirectMessageReceivers.get(protocolId).receiveMessage(address, protocolId, data);
                } else if (HELIOS_DIRECT_CHAT_FILE_PROTO.equals(protocolId)) {
                    Log.d(TAG, "receiveMessage forwarding HELIOS_DIRECT_CHAT_FILE_PROTO: " + protocolId);
                    mDirectMessageReceivers.get(protocolId).receiveMessage(address, protocolId, data);
                } else if(HELIOS_STATUS_PROTO.equals(protocolId)) {
                    Log.d(TAG, "receiveMessage status forwarding: " + protocolId);
                    mDirectMessageReceivers.get(protocolId).receiveMessage(address, protocolId, data);
                } else {
                    Log.d(TAG, "receiveMessage forwarding message.getBytes: " + protocolId);
                    mDirectMessageReceivers.get(protocolId).receiveMessage(address, protocolId, message.getBytes());
                }
            } else {
                Log.d(TAG, "receiveMessage No internal receiver for protocolId: " + protocolId);
            }
        }
    };

    @Override
    public boolean isConnected() {
        return mConnected;
    }

    /**
     * Receive all subscribes from general pub-sub
     */
    private class HeliosReceiver implements HeliosMessageListener {

        @Override
        public void showMessage(HeliosTopic heliosTopic, HeliosMessage heliosMessage) {
            Log.d(TAG, "HeliosReceiver showMessage() topic:" + heliosTopic.getTopicName());

            // Convert message part from JSON
            HeliosMessagePart msg = null;
            HeliosNetworkAddress networkAddress = null;
            try {
                msg = JsonMessageConverter.getInstance().readHeliosMessagePart(heliosMessage.getMessage());

                // In case there was a media file, update its name accordingly to local saved file.
                if (null != heliosMessage.getMediaFileName()) {
                    msg.mediaFileName = heliosMessage.getMediaFileName();
                }

                if (msg.messageType == HeliosMessagePart.MessagePartType.PUBSUB_SYNC_RESEND) {
                    // FIXME: allows anyone to impersonate anyone else
                    // FIXME: this duplicates UserNetworkMap handling below
                    // Do we handle msg.senderNetworkId etc.

                    msg.messageType = msg.originalType;
                    // RESEND only for group / pubsub messages
                } else if (heliosMessage instanceof HeliosMessageLibp2pPubSub) {
                    // TODO fix better handling for direct/sub messages.
                    // TODO fix handling users not seen before -- privacy.

                    HeliosMessageLibp2pPubSub msgPubSub = (HeliosMessageLibp2pPubSub) heliosMessage;
                    networkAddress = msgPubSub.getNetworkAddress();
                    String networkId = msgPubSub.getNetworkAddress().getNetworkId();
                    Log.d(TAG, "getNetworkAddress:" + msgPubSub.getNetworkAddress());
                    //Log.d(TAG, "getNetworkId:" + networkId);
                    //Log.d(TAG, "getMessage:" + msgPubSub.getMessage());
                    msg.senderNetworkId = networkId;
                }
            } catch (JsonParseException e) {
                // TODO: notify error
                Log.e(TAG, "showMessage JsonParseException while reading heliosMessage: " + e.toString());
                return;
            }

            // We received a message, store it internally.
            boolean stored = storeHeliosMessage(heliosTopic, msg, false, networkAddress);
            // Pass the info to listeners if stored (not duplicate)
            if (stored) {
                // Update mediaFileName reference if needed.
                if (null != heliosMessage.getMediaFileName()) {
                    HeliosMessage newMsg = new HeliosMessage(JsonMessageConverter.getInstance().convertToJson(msg), heliosMessage.getMediaFileName());
                    showMessageToListener(heliosTopic, newMsg);
                } else {
                    showMessageToListener(heliosTopic, heliosMessage);
                }
            }
        }
    }

    private void showMessageToListener(HeliosTopic topic, HeliosMessage message) {
        try {
            //TODO: Not handling multiple same topics with separate subscribes
            // Handle normal messages for any subscriber of this topic.
            // Fetch the correct listener
            if (mSubscribers.containsKey(topic.getTopicName())) {
                Log.d(TAG, "showMessageToListener: Internal receiver found for topic: " + topic.getTopicName());

                HeliosMessagePart msgPart = JsonMessageConverter.getInstance().readHeliosMessagePart(message.getMessage());
                Log.d(TAG, "showMessageToListener: protocol:" + msgPart.protocol);
                //Log.d(TAG, "showMessageToListener: ##:" + message.getMessage());

                // Extract the correct data encapsulated into HeliosMessagePart and deliver it to subscriber
                if (PUB_SUB_PACKAGED.equals(msgPart.protocol)) {
                    mSubscribers.get(topic.getTopicName()).showMessage(topic, new HeliosMessage(msgPart.msg, message.getMediaFileName()));
                } else {
                    mSubscribers.get(topic.getTopicName()).showMessage(topic, message);
                }
            } else {
                Log.d(TAG, "showMessageToListener: No subscriber for topic: " + topic.getTopicName());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "showMessageToListener error:" + e.toString());
        }
    }

    private boolean storeHeliosMessage(HeliosTopic heliosTopic, HeliosMessagePart msg, boolean isDirectMessage, HeliosNetworkAddress senderAddress) {
        Log.d(TAG, "storeHeliosMessage");
        boolean stored = false;
        // Update message to singleton
        ArrayList<HeliosConversation> conversationList = HeliosConversationList.getInstance().getConversations();
        for (int i = 0; i < conversationList.size(); i++) {
            HeliosConversation conversation = conversationList.get(i);
            if (isDirectMessage) {
                // DirectMessage
                // If uuid, it is direct chat
                if (!TextUtils.isEmpty(conversation.topic.uuid)) {
                    Log.d(TAG, "topic.uuid  " + conversation.topic.uuid);
                    Log.d(TAG, "msg.senderUUID  " + msg.senderUUID);
                    if (conversation.topic.uuid.equals(msg.senderUUID)) {
                        Log.d(TAG, "update message to topic " + conversation.topic.topic);
                        Log.d(TAG, "update message to uuid " + conversation.topic.uuid);
                        stored = conversation.addMessage(msg);
                        Log.d(TAG, "msg.senderUUID:" + msg.senderUUID + " to: " + msg.to + " msg:" + msg.msg);
                        if (stored)
                            mChatMessageStore.addMessage(msg);
                        break;
                    }
                }
            } else {
                // Pub-sub message
                if (conversation.topic.topic.equals(heliosTopic.getTopicName())) {
                    Log.d(TAG, "update message to topic " + heliosTopic.getTopicName());
                    Log.d(TAG, "update message senderAddress " + senderAddress);
                    if ((msg.messageType == HeliosMessagePart.MessagePartType.HEARTBEAT) && (senderAddress != null)) {
                        try {
                            Log.d(TAG, "update message senderAddress " + senderAddress);
                            List<HeliosMessagePart> hasMissing = mHeartbeatManager.collectMissingMessages(msg, conversation);
                            Log.d(TAG, "update hasMissing: " + hasMissing.size());
                            if (!hasMissing.isEmpty()) {
                                for (HeliosMessagePart missingMsg : hasMissing) {
                                    Log.i(TAG, "Sender " + msg.senderName + " is missing " + missingMsg.msg);
                                }
                                // Trigger a sync message to heartbeat sender
                                SyncManager syncMgr = SyncManager.getInstance();
                                syncMgr.syncMessages(hasMissing, senderAddress);
                            }
                        } catch (HeartbeatDataException e) {
                            Log.d(TAG, "Heartbeat message without payload");
                        }
                    }

                    // Check if we need to sync direct messages to this user
                    //TODO: Now only with HEARTBEAT or JOIN, should check when user is actually online.
                    Log.d(TAG, "update msg.messageType:" + msg.messageType);
                    Log.d(TAG, "SyncManager msg.senderUUID:" + msg.senderUUID);
                    //Log.d(TAG, "SyncManager mHeliosIdentityInfo.getUserUUID:" + mHeliosIdentityInfo.getUserUUID());
                    if (mHeliosIdentityInfo != null && !TextUtils.isEmpty(msg.senderUUID)) {
                        // Don't sync with self
                        String myUUID = mHeliosIdentityInfo.getUserUUID();
                        if (myUUID == null) {
                            Log.e(TAG, "UUID identity is null - sync attempt failed");
                        } else if (!myUUID.equals(msg.senderUUID)) {
                            if (msg.messageType == HeliosMessagePart.MessagePartType.HEARTBEAT || msg.messageType == HeliosMessagePart.MessagePartType.JOIN) {
                                SyncManager syncMgr = SyncManager.getInstance();
                                syncMgr.syncDirectMessages(mContext, msg.senderUUID,
                                        msg.senderNetworkId, mChatMessageStore, mDirectMessageReceivers);
                            }
                        }
                    }

                    if (mFilterHeartbeatMsg && msg.messageType == HeliosMessagePart.MessagePartType.HEARTBEAT) {
                        break;
                    }
                    if (mFilterJoinMsg && msg.messageType == HeliosMessagePart.MessagePartType.JOIN) {
                        break;
                    }
                    stored = conversation.addMessage(msg);
                    if (stored)
                        mChatMessageStore.addMessage(msg);
                    break;
                }
            }
        }

        if (!stored) {
            Log.d(TAG, "addMessage done, duplicate >");
            // FIXME: separate view sync from storage, display shoudl hook into the conversation, now will display duplicates on resend
            //return false;
        }

        Log.d(TAG, "addMessage done >");
        // If this message is filtered, don't update topic info
        if (mFilterHeartbeatMsg && msg.messageType == HeliosMessagePart.MessagePartType.HEARTBEAT) {
            Log.d(TAG, "addMessage HEARTBEAT, not updating topic");
            return false;
        }
        if (mFilterJoinMsg && msg.messageType == HeliosMessagePart.MessagePartType.JOIN) {
            Log.d(TAG, "addMessage JOIN, not updating topic");
            return false;
        }

        boolean topicFound = false;
        // Update topic to singleton
        ArrayList<HeliosTopicContext> arrTopics = HeliosConversationList.getInstance().getTopics();
        for (int i = 0; i < arrTopics.size(); i++) {
            HeliosTopicContext topicContext = arrTopics.get(i);
            if (isDirectMessage) {
                // Only 1-1 has uuid set.
                if (!TextUtils.isEmpty(topicContext.uuid)) {
                    if (topicContext.uuid.equals(msg.senderUUID)) {
                        Log.d(TAG, "update topic desc to topic.uuid  " + topicContext.uuid);
                        topicFound = true;
                        topicContext.lastMsg = msg.msg;
                        // Update also user's name if changed..
                        topicContext.topic = msg.senderName;
                        topicContext.participants = msg.senderName + ":" + msg.msg;
                        topicContext.ts = msg.getLocaleTs();
                        break;
                    }
                }
            } else {
                if (topicContext.topic.equals(heliosTopic.getTopicName())) {
                    Log.d(TAG, "update topic desc to topic name " + topicContext.topic);
                    topicFound = true;
                    topicContext.lastMsg = msg.msg;
                    topicContext.participants = msg.senderName + ":" + msg.msg;
                    topicContext.ts = msg.getLocaleTs();
                    break;
                }
            }
        }
        Log.d(TAG, "update topic done >");

        if (!topicFound) {
            Log.d(TAG, "## store helios message: could not find/update topic for: " + heliosTopic.getTopicName() + " from:" + msg.senderName);
        }

        // TODO: Fix better handling for direct messages, first message.
        // If this is a new 1-1 chat started by someone, we create a new topic for it and save the uuid
        // of the user to the topic.
        if (!topicFound && isDirectMessage) {
            Log.d(TAG, "topic NOT FOUND, adding topic " + heliosTopic.getTopicName());
            HeliosConversation newConversation = new HeliosConversation();
            newConversation.topic = new HeliosTopicContext(heliosTopic.getTopicName(), msg.msg, msg.to, msg.getLocaleTs());
            newConversation.topic.uuid = msg.senderUUID;
            newConversation.topic.lastMsg = msg.msg;
            newConversation.topic.participants = msg.senderName + ":" + msg.msg;
            newConversation.topic.ts = msg.getLocaleTs();
            stored = newConversation.addMessage(msg);
            HeliosConversationList.getInstance().addConversation(newConversation);
            if (stored)
                mChatMessageStore.addMessage(msg);
        }

        return stored;
    }

    /**
     * A couple of ad-hoc tests to check if converted HeliosMessagePart lacks
     * data fields that should always be present.
     * TODO: Add better detection of message types
     * @param message Helios message object
     * @return true if the message needs to be repacked
     */
    private boolean isRepackNeeded(HeliosMessagePart message) {
        if (message == null) {
            return true;
        }
        if ((message.uuid == null) && (message.ts == null)) {
            return true;
        }
        return false;
    }
}
