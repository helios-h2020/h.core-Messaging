package eu.h2020.helios_social.core.messaging.sync;

import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.hash.BloomFilter;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Collectors;

import eu.h2020.helios_social.core.messaging.HeliosConnect;
import eu.h2020.helios_social.core.messaging.HeliosIdentityInfo;
import eu.h2020.helios_social.core.messaging.HeliosMessage;
import eu.h2020.helios_social.core.messaging.HeliosMessaging;
import eu.h2020.helios_social.core.messaging.HeliosMessagingException;
import eu.h2020.helios_social.core.messaging.HeliosTopic;
import eu.h2020.helios_social.core.messaging.data.HeliosConversation;
import eu.h2020.helios_social.core.messaging.data.HeliosConversationList;
import eu.h2020.helios_social.core.messaging.data.HeliosMessagePart;
import eu.h2020.helios_social.core.messaging.data.JsonMessageConverter;
import eu.h2020.helios_social.core.messaging.HeliosDirectMessaging;
import eu.h2020.helios_social.core.messaging.HeliosEgoTag;
import eu.h2020.helios_social.core.messaging.HeliosNetworkAddress;

/**
 * This class takes care of sending periodic heartbeat messages to pubsub topics.
 */
public class HeartbeatManager {
    private static final String TAG = "HeartbeatManager";
    private static HeartbeatManager sInstance = new HeartbeatManager();
    private HandlerThread mHeartbeatHandlerThread = null;
    private Handler mHeartbeatHandler = null;
    private int mHeartbeatCounter = 1;
    private int mHeartbeatInterval = 60 * 1000; // 1 minute
    private int mHeartbeatDelay = 3300; // 3.3 seconds
    private Boolean mActive = false;
    private Hashtable<String, Hashtable<String, HeliosEgoTag>> mHeartbeatUsers = new Hashtable<>();
    private Hashtable<String, HeliosEgoTag> mHeartbeatUsersDm = new Hashtable<>();
    private Boolean mEnabled = false; // Disable HeartbeatManager for SpringApp version

    public static HeartbeatManager getInstance() {
        return sInstance;
    }

    private HeartbeatManager() {
        if (mEnabled) {
            mHeartbeatHandlerThread = new HandlerThread("HeartbeatHandlerThread");
            mHeartbeatHandlerThread.start();
            mHeartbeatHandler = new Handler(mHeartbeatHandlerThread.getLooper());
        }
    }

    /**
     * @deprecated
     * This is no longer needed as functionality has been moved to private constructor.
     */
    @Deprecated
    public void init() {
    }

    public boolean isHeartbeatMsg(HeliosMessagePart msg) {
        return (msg.messageType == HeliosMessagePart.MessagePartType.HEARTBEAT);
    }

    public boolean isJoinMsg(HeliosMessagePart msg) {
        return (msg.messageType == HeliosMessagePart.MessagePartType.JOIN);
    }

    /**
     * Sends a status message to the HeliosNetworkAddress. Provided connector object must be connected in order to send.
     *
     * @param messaging HeliosDirectMessaging object
     * @param connector HeliosConnect object
     * @param proto protocol to use
     * @param statusMsg status message to send
     * @param address recipient HeliosNetworkAddress
     */
    public void sendIsOnlineTo(HeliosDirectMessaging messaging, HeliosConnect connector, String proto, String statusMsg, HeliosNetworkAddress address) {
        if (mEnabled) {
            // TODO: Should we have a pool
            new Thread(() -> {
                try {
                    Log.d(TAG, "sendIsOnlineTo:" + address.getNetworkId() + " thread id:" + Thread.currentThread().getId());
                    if (connector.isConnected()) {
                        messaging.sendTo(address, proto, statusMsg.getBytes());
                    } else {
                        Log.d(TAG, "sendIsOnlineTo: connector is not connected.");
                    }
                } catch (RuntimeException e) {
                    Log.e(TAG, "Could not sendIsOnlineTo " + address.getNetworkId() + ": " + e.getMessage());
                } finally {
                    Log.d(TAG, "sendIsOnlineTo finished to " + address.getNetworkId());
                }
            }).start();
        }
    }

    /**
     * Checks if given HeliosNetworkAddress is seen and if so, adds it to a list of HeliosEgoTag
     * containing the last timestamp (System.currentTimeMillis()) the address has been seen.
     * If not seen, it is not returned. HeliosEgoTag's networkId matches the HeliosNetworkAddresses networkId.
     *
     * @param userHeliosAddresses ArrayList of HeliosNetworkAddress
     * @return List of HeliosEgoTag
     */
    public List<HeliosEgoTag> getCurrentOnlineStatus(ArrayList<HeliosNetworkAddress> userHeliosAddresses) {
        Log.d(TAG, "getCurrentOnlineStatus");

        ArrayList<HeliosEgoTag> arr = new ArrayList<>();
        if (mEnabled) {
            if (userHeliosAddresses != null) {
                for (HeliosNetworkAddress a : userHeliosAddresses) {
                    Log.d(TAG, "getCurrentOnlineStatus do we have address: " + a.getNetworkId());
                    if (a.getNetworkId() != null && mHeartbeatUsersDm.containsKey(a.getNetworkId())) {
                        arr.add(mHeartbeatUsersDm.get(a.getNetworkId()));
                    }
                }
            }
        }
        return arr;
    }

    /**
     * Get a list of online users for a topic. Their last seen timestamp is available for checking
     * when they have been seen.
     *
     * @param topic Name of the topic
     * @return List of online users as HeliosEgoTag
     */
    public List<HeliosEgoTag> getTopicOnlineUsers(String topic) {
        // Check that topic is not empty or null
        if (!mEnabled || TextUtils.isEmpty(topic)) {
            return new ArrayList<>();
        }

        if (mHeartbeatUsers.containsKey(topic)) {
            return new ArrayList<>(mHeartbeatUsers.get(topic).values());
        }

        return new ArrayList<>();
    }

    /**
     * Add/update online user for a topic.
     *
     * @param topic Topic name
     * @param msg Helios message
     */
    public void addTopicOnlineUser(String topic, HeliosMessagePart msg) {
        if (mEnabled) {
            if (!mHeartbeatUsers.containsKey(topic)) {
                mHeartbeatUsers.put(topic, new Hashtable<>());
            }
            // TODO: Should verify networkId
            HeliosEgoTag egoTag = createEgoTag(msg);
            mHeartbeatUsers.get(topic).put(msg.senderUUID, egoTag);

            // Also update individual network id status table
            Log.d(TAG, "--updateUserOnline getNetworkId: " + egoTag.getNetworkId() + " - " + msg.senderNetworkId);
            mHeartbeatUsersDm.put(egoTag.getNetworkId(), egoTag);
        }
    }

    /**
     * Update online status of a peer in general.
     *
     * @param address HeliosNetworkAddress
     * @param msg HeliosMessagePart
     */
    public void updateUserOnline(HeliosNetworkAddress address, HeliosMessagePart msg) {
        if (mEnabled) {
            Log.d(TAG, "updateUserOnline getNetworkId: " + address.getNetworkId() + " - " + msg.senderNetworkId);
            HeliosEgoTag egoTag = createEgoTag(msg);
            mHeartbeatUsersDm.put(egoTag.getNetworkId(), egoTag);
        }
    }

    /**
     * Get current heartbeat interval value (default 60*1000 = 1 minute)
     * @return Heartbeat interval in milliseconds
     */
    public int getHeartbeatInterval() {
        return mHeartbeatInterval;
    }

    /**
     * Set heartbeat interval value as milliseconds
     * @param milliseconds New heartbeat interval
     */
    public void setHeartbeatInterval(int milliseconds) {
        //
        if (milliseconds > 100) {
            mHeartbeatInterval = milliseconds;
        } else {
            Log.d(TAG, "Invalid heartbeat interval value " + milliseconds);
        }
    }

    /**
     * Start periodic heartbeat messages to groups
     *
     * @param messaging Caller must pass HeliosMessaging object
     * @param connector Caller must pass HeliosConnect object
     */
    public void start(HeliosMessaging messaging, HeliosConnect connector, HeliosIdentityInfo identity) {
        if (mEnabled) {
            Log.d(TAG, "start()");
            mActive = true;
            mHeartbeatHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "mHeartbeatHandler.run() " + Thread.currentThread().getId());
                    if (connector.isConnected()) {
                        ZonedDateTime sinceTs = ZonedDateTime.now().minusDays(7);
                        // FIXME: Is this iteration thread safe?
                        ArrayList<HeliosConversation> conversationList = HeliosConversationList.getInstance().getConversations();
                        for (int i = 0; i < conversationList.size(); i++) {
                            HeliosConversation conversation = conversationList.get(i);
                            // Only send HEARTBEAT to groups, i.e., now without UUID
                            if (TextUtils.isEmpty(conversation.topic.uuid)) {
                                Log.d(TAG, "mHeartbeat send to topic:" + conversation.topic.topic);
                                HeliosMessagePart heartbeatMsg = createNewMessage(conversation.topic.topic,
                                        "heartbeat " + mHeartbeatCounter,
                                        identity,
                                        HeliosMessagePart.MessagePartType.HEARTBEAT);
                                if (heartbeatMsg == null) {
                                    Log.e(TAG, "Identity info is missing - heartbeat message is not sent");
                                    return;
                                }
                                heartbeatMsg.mediaFileData = conversation.formatBloom(sinceTs);
                                heartbeatMsg.sinceTs = sinceTs.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

                                try {
                                    if (mActive) {
                                        messaging.publish(new HeliosTopic(heartbeatMsg.to, heartbeatMsg.to),
                                                new HeliosMessage(JsonMessageConverter.getInstance().convertToJson(heartbeatMsg)));
                                        Log.d(TAG, "heartbeat sent to:" + heartbeatMsg.to);
                                    } else {
                                        Log.d(TAG, "skip heartbeat to:" + heartbeatMsg.to);
                                    }
                                } catch (HeliosMessagingException e) {
                                    Log.e(TAG, "heartbeat.run error sending to:" + heartbeatMsg.to);
                                    e.printStackTrace();
                                }
                            }
                        }
                        mHeartbeatCounter++;
                    }
                    mHeartbeatHandler.postDelayed(this, mHeartbeatInterval);
                }
            }, mHeartbeatDelay);
        }
    }

    /**
     * Stop heartbeat messages
     */
    public void stop() {
        if (mEnabled) {
            mHeartbeatHandlerThread.quit();
        }
    }

    public void activate() {
        mActive = true;
    }

    public void deactivate() {
        mActive = false;
    }

    public Boolean isActive() {
        return mActive;
    }

    /**
     * Analyze heartbeat message payload to check whether the sender has missed old messages
     *
     * @param msg          Helios heartbeat message
     * @param conversation Discussion group
     * @return List of messages that the sender does not have but we have
     * @throws HeartbeatDataException No heartbeat payload found (ok for fresh start)
     */
    public List<HeliosMessagePart> collectMissingMessages(HeliosMessagePart msg, HeliosConversation conversation) throws HeartbeatDataException {
        if (mEnabled) {
            Log.d(TAG, "collectMissingMessages()");

            if ((msg == null) || !isHeartbeatMsg(msg) || (msg.mediaFileData == null) || (msg.mediaFileData.length == 0)) {
                throw new HeartbeatDataException();
            }
            BloomFilter<String> filter = HeliosConversation.parseBloom(msg.mediaFileData);

            ZonedDateTime sinceTs = msg.sinceTs == null ?
                    ZonedDateTime.now().minusDays(7) :
                    ZonedDateTime.parse(msg.sinceTs, DateTimeFormatter.ISO_ZONED_DATE_TIME);

            List<HeliosMessagePart> recentMessages = conversation.getMessagesAfter(sinceTs);
            List<HeliosMessagePart> missingMessages = recentMessages
                    .stream()
                    .filter(storedMsg -> storedMsg.messageType == HeliosMessagePart.MessagePartType.MESSAGE)
                    .filter(storedMsg -> !filter.mightContain(storedMsg.getUuid()))
                    .collect(Collectors.toList());
            return missingMessages;
        } else {
            return new ArrayList<HeliosMessagePart>();
        }
    }

    /**
     * Send message delayed
     * @param messaging HeliosMessaging handle that is used to publish delayed
     * @param topic
     * @param msg
     * @param identity
     * @param msgType
     * @param delay
     */
    public void sendDelayed(HeliosMessaging messaging, String topic, String msg, HeliosIdentityInfo identity, HeliosMessagePart.MessagePartType msgType, int delay) {
        if (mEnabled) {
            mHeartbeatHandler.postDelayed(() -> {
                HeliosMessagePart message = createNewMessage(topic, msg, identity, msgType);
                try {
                    messaging.publish(new HeliosTopic(message.to, message.to), new HeliosMessage(JsonMessageConverter.getInstance().convertToJson(message)));
                } catch (HeliosMessagingException e) {
                    e.printStackTrace();
                }
            }, delay);
        }
    }

    private HeliosMessagePart createNewMessage(String topic, String msg, HeliosIdentityInfo identity, HeliosMessagePart.MessagePartType msgType) {
        String timestamp = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now());
        if (identity == null) {
            Log.e(TAG, "Identity info is null - message part is not created");
            return null;
        }
        HeliosMessagePart message = new HeliosMessagePart(msg, identity.getNickname(), identity.getUserUUID(), topic, timestamp, msgType);
        return message;
    }

    private HeliosEgoTag createEgoTag(HeliosMessagePart msg) {
        return new HeliosEgoTag(msg.senderUUID, msg.senderNetworkId, msg.senderName, System.currentTimeMillis());
    }
}
