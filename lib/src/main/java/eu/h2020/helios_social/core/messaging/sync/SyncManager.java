package eu.h2020.helios_social.core.messaging.sync;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.h2020.helios_social.core.messaging.MessagingConstants;
import eu.h2020.helios_social.core.messaging.data.HeliosMessagePart;
import eu.h2020.helios_social.core.messaging.data.JsonMessageConverter;
import eu.h2020.helios_social.core.messaging.db.HeliosMessageStore;
import eu.h2020.helios_social.core.messaging.HeliosMessagingReceiver;
import eu.h2020.helios_social.core.messaging.HeliosNetworkAddress;
import eu.h2020.helios_social.core.storage.HeliosStorageUtils;
import eu.h2020.helios_social.core.messaging.streamr.HeliosMessagingStreamrLibp2p;

/**
 * Singleton manager class that takes care of handling direct message resending to
 * other Helios users. The manager has a list of nodes that needs to be synced and
 * will then try to send direct messages to those nodes.
 */
public class SyncManager {
    private static final String TAG = "SyncManager";
    private static SyncManager sInstance = new SyncManager();
    // private HeliosMessagingNodejsLibp2p mHeliosMessagingNodejs = HeliosMessagingNodejsLibp2p.getInstance();
    private HeliosMessagingStreamrLibp2p mHeliosMessagingNodejs = HeliosMessagingStreamrLibp2p.getInstance();
    private final AtomicBoolean mSyncInProgress = new AtomicBoolean(false);
    private Boolean mEnabled = false; // Disable SyncManager for SpringAPp builds

    public static SyncManager getInstance() {
        return sInstance;
    }

    /**
     * Send direct messages to a user who has returned online.
     *
     * @param context Application context of the program (getApplicationContext())
     * @param uuid The UUID of the message recipient
     * @param networkId The network Id of the message recipient
     * @param store HeliosMessageStore object (can be null)
     * @param map A hash map of direct message receivers (can be null)
     */
    public void syncDirectMessages(Context context, String uuid, String networkId, HeliosMessageStore store, HashMap<String, HeliosMessagingReceiver> map) {
        if (!mEnabled) {
            return;
        }
        // Are we already syncing messages to this user
        if (hasNode(uuid)) {
            Log.d(TAG, "Already syncing to: " + uuid);
            return;
        }
        // TODO: Add timestamp or status in order to restart sync if taking too long.
        boolean added = addNode(uuid);
        if (!added) {
            Log.d(TAG, "Unable to add the sender UUID to the sync queue");
            return;
        }

        List<HeliosMessagePart> unsent = getUnsentMessages(store, uuid, networkId, 7);
        if (unsent.size() == 0) {
            return;
        }

        startSendingDirectMessages(context, unsent, uuid, networkId, store, map);
    }

    /**
     * Resend pubsub messages.
     * @param messages The list of messages to be sent
     * @param address Network address of the recipient
     */
    public void syncMessages(Iterable<HeliosMessagePart> messages, HeliosNetworkAddress address) {
        if (!mEnabled) {
            return;
        }
        if (!mSyncInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "sync already in progress");
            return;
        }
        Log.d(TAG, "start sync");

        new Thread(() -> {
            try {
                for (HeliosMessagePart message : messages) {
                    HeliosMessagePart syncMsg = new HeliosMessagePart(message);
                    syncMsg.originalType = message.messageType;
                    syncMsg.messageType = HeliosMessagePart.MessagePartType.PUBSUB_SYNC_RESEND;

                    byte[] data = JsonMessageConverter.getInstance().convertToJson(syncMsg).getBytes(StandardCharsets.UTF_8);
                    Log.d(TAG, "Send resend " + syncMsg.getUuid() + " sync to " + address.getNetworkId());
                    sendDirect(address, MessagingConstants.HELIOS_CHAT_SYNC_PROTO, data);
                    Log.d(TAG, "Send resend done.");
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not resend messages to " + address.getNetworkId() + ": " + e.getMessage());
            } finally {
                Log.d(TAG, "sync finished to " + address.getNetworkId());
                mSyncInProgress.set(false);
            }
        }).start();
    }

    //
    // A list of nodes that need to be synchronized and methods manipulating the list
    //

    private BlockingDeque<String> mSyncNodeQue = new LinkedBlockingDeque<>();

    private boolean hasNode(String uuid) {
        return mSyncNodeQue.contains(uuid);
    }

    private boolean addNode(String uuid) {
        boolean added = true;
        try {
            if (!hasNode(uuid)) {
                mSyncNodeQue.putLast(uuid);
            }
        } catch (InterruptedException e) {
            added = false;
        }
        return added;
    }

    private boolean removeNode(String uuid) {
        boolean removed = false;
        if (hasNode(uuid)) {
            removed = mSyncNodeQue.remove(uuid);
        }
        return removed;
    }

    /**
     * Get a list of unsent messages to a certain user identified by the UUID
     *
     * @param store HeliosMessageStore to query messages
     * @param uuid The UUID of the message recipient
     * @param networkId The networkId of the message recipient
     * @param days Specify how many days old messages will be sent
     * @return The list of unsent messages
     */
    private List<HeliosMessagePart> getUnsentMessages(HeliosMessageStore store, String uuid, String networkId, int days) {
        List<HeliosMessagePart> unsent = new ArrayList<>();

        // This checks all messages from recent days (number of days as a parameter)
        ZonedDateTime sinceTs = ZonedDateTime.now().minusDays(days);

        Log.d(TAG, "getUnsentMessages loadMessages networkId: " + networkId);
        // Load messages with user's networkId
        List<HeliosMessagePart> messages = store.loadMessages(networkId, sinceTs.toEpochSecond());
        for (int i = 0; i < messages.size(); i++) {
            HeliosMessagePart message = messages.get(i);
            if (message.to.equals(networkId) && !message.msgReceived) {
                unsent.add(message);
            }
        }
        Log.d(TAG, "getUnsentMessages unsent.size:" + unsent.size());

        Log.d(TAG, "getUnsentMessages loadMessages uuid: " + uuid);
        // Load messages with user's UUID, if stored
        List<HeliosMessagePart> messages2 = store.loadMessages(uuid, sinceTs.toEpochSecond());
        for (int i = 0; i < messages2.size(); i++) {
            HeliosMessagePart message = messages2.get(i);
            if (message.to.equals(uuid) && !message.msgReceived) {
                unsent.add(message);
            }
        }
        Log.d(TAG, "getUnsentMessages unsent.size:" + unsent.size());
        if (unsent.size() == 0) {
            removeNode(uuid);
        }
        return unsent;
    }

    /**
     * Send a message using Helios P2P transport
     * @param addr Network address of the recipient
     * @param proto Protocol that will be used
     * @param data message payload
     */
    private void sendDirect(HeliosNetworkAddress addr, String proto, byte[] data) {
        if (mHeliosMessagingNodejs == null) {
            return;
        }
        mHeliosMessagingNodejs.getDirectMessaging().sendTo(addr, proto, data);
    }

    /**
     * Send media file
     * @param context Application context of the program (getApplicationContext())
     * @param filename Media filename
     * @param address Network address of the recipient
     */
    private void sendMediaFile(Context context, String filename, HeliosNetworkAddress address) {
        byte[] cacheMediaFileData = HeliosStorageUtils.getFileBytes(context.getFilesDir(), filename);
        if (cacheMediaFileData.length > 0) {
            Log.d(TAG, "syncDirectMsgTo sending mediaFile:" + filename);
            // Sending a file. HELIOS_DIRECT_CHAT_FILE_PROTO
            Log.d(TAG, "Sending a file >");
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                // TODO FIX a proper way for file sending
                out.write(filename.getBytes(StandardCharsets.UTF_8));
                out.write(0);
                out.write(cacheMediaFileData);

                Log.d(TAG, "syncDirectMsgTo HELIOS_DIRECT_CHAT_FILE_PROTO start");
                sendDirect(address, MessagingConstants.HELIOS_DIRECT_CHAT_FILE_PROTO, out.toByteArray());
                Log.d(TAG, "syncDirectMsgTo HELIOS_DIRECT_CHAT_FILE_PROTO end");
            } catch (Exception e) {
                Log.e(TAG, "syncDirectMsgTo Error sending file:" + e.toString());
            }
        } else {
            Log.e(TAG, "syncDirectMsgTo did not get file bytes:" + filename);
        }
    }

    /**
     * Send the direct messages that have not been sent yet as a separate thread.
     *
     * @param context Application context of the program (getApplicationContext())
     * @param messages List of unsent messages
     * @param uuid UUID of the recipient
     * @param networkId Network address of the recipient
     * @param store HeliosMessageStore to update the message (can be null)
     * @param map A hash map of direct message receivers (can be null)
     */
    private void startSendingDirectMessages(Context context, List<HeliosMessagePart> messages, String uuid, String networkId, HeliosMessageStore store, HashMap<String, HeliosMessagingReceiver> map) {
        new Thread(() -> {
            HeliosNetworkAddress address = new HeliosNetworkAddress();
            address.setNetworkId(networkId);
            try {
                // TODO: Sync batched
                Log.d(TAG, "syncDirectMsgTo start sending:");
                for (HeliosMessagePart syncMsg : messages) {
                    if (!TextUtils.isEmpty(syncMsg.protocol)) {
                        Log.d(TAG, "syncDirectMsgTo sending message:" + syncMsg.msg);
                        Log.d(TAG, "syncDirectMsgTo sending syncMsg.protocol:" + syncMsg.protocol);

                        if(MessagingConstants.HELIOS_DIRECT_CHAT_PROTO.equals(syncMsg.protocol)) {
                            sendDirect(address, MessagingConstants.HELIOS_DIRECT_CHAT_PROTO, syncMsg.msg.getBytes());
                        } else if(MessagingConstants.HELIOS_DIRECT_CHAT_FILE_PROTO.equals(syncMsg.protocol)) {
                            if (syncMsg.mediaFileName != null) {
                                Log.d(TAG, "syncDirectMsgTo sending media file to:" + syncMsg.to);
                                sendMediaFile(context, syncMsg.mediaFileName, address);
                            }
                        } else {
                            // Other protocol
                            // should we copy msg while syncing?
                            sendDirect(address, syncMsg.protocol, JsonMessageConverter.getInstance().convertToJson(syncMsg).getBytes());
                        }
                        Log.d(TAG, "syncDirectMsgTo send resend done.");

                        // update the flag that this message has been sent.
                        syncMsg.msgReceived = true;
                        syncMsg.mediaFileData = null;

                        // Store success to Room storage
                        if (store != null) {
                            store.setReceivedField(syncMsg.getUuid(), true);
                        }

                        // Check the internal receivers and notify listener with a HELIOS_SYNC_DM_ACK_PROTO message
                        // Only for HELIOS_DIRECT_CHAT_PROTO now
                        if(syncMsg.protocol.equals(MessagingConstants.HELIOS_DIRECT_CHAT_PROTO) || syncMsg.protocol.equals(MessagingConstants.HELIOS_DIRECT_CHAT_FILE_PROTO)){
                            if ((map != null) && map.containsKey(MessagingConstants.HELIOS_DIRECT_CHAT_PROTO)) {
                                Log.d(TAG, "syncDirectMsgTo send resend, notifying receiver with HELIOS_SYNC_DM_ACK_PROTO.");
                                map.get(MessagingConstants.HELIOS_DIRECT_CHAT_PROTO).receiveMessage(address, MessagingConstants.HELIOS_SYNC_DM_ACK_PROTO, JsonMessageConverter.getInstance().convertToJson(syncMsg).getBytes());
                            }
                        }
                    } else {
                        Log.e(TAG, "syncDirectMsgTo syncMsg.protocol not defined for msg.uuid: " + syncMsg.uuid);
                    }
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "syncDirectMsgTo Could not resend messages to " + address.getNetworkId() + ": " + e.getMessage());
                removeNode(uuid);
            } finally {
                removeNode(uuid);
                Log.d(TAG, "syncDirectMsgTo end send to " + address.getNetworkId());
            }
        }).start();
    }

}
