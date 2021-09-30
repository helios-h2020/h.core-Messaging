package eu.h2020.helios_social.core.messaging.db;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.room.Room;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;

import eu.h2020.helios_social.core.messaging.data.HeliosMessagePart;
import eu.h2020.helios_social.core.messaging.data.HeliosTopicContext;

/**
 * This class takes care of storing and retrieving HELIOS messages from Room database.
 */
public class HeliosMessageStore {
    private static final String TAG = "HeliosMessageStore";
    private HeliosDatabase mDatabase;
    private HeliosDataDao mHeliosDataDao;

    /**
     * Initialize database instance
     *
     * @param ctx Application context
     */
    public HeliosMessageStore(Context ctx) {
        this.mDatabase = Room.databaseBuilder(ctx, HeliosDatabase.class, "messageDB")
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration()
                .build();
        this.mHeliosDataDao = mDatabase.heliosDataDao();
    }

    /**
     * Add HELIOS message to Room database
     *
     * @param message HELIOS message
     */
    public void addMessage(HeliosMessagePart message) {
        HeliosData data = convertToDBEntry(message);
        mHeliosDataDao.addMessages(data);
    }

    /**
     * Set message received status.
     *
     * @param uuid UUID of the message to modify.
     * @param value New value for the received field.
     */
    public void setReceivedField(String uuid, boolean value) {
        mHeliosDataDao.setReceivedField(uuid, value);
    }

    /**
     * Load HELIOS messages from Room database
     *
     * @param topic Topic name to be requested.
     * @return Array list of HeliosMessagePart objects
     */
    public ArrayList<HeliosMessagePart> loadMessages(String topic) {
        List<HeliosData> reply = mHeliosDataDao.loadMessages(topic);
        ArrayList<HeliosMessagePart> messages = new ArrayList<HeliosMessagePart>();
        ListIterator<HeliosData> it = reply.listIterator();
        while (it.hasNext()) {
            HeliosMessagePart msg = convertFromDBEntry(it.next());
            messages.add(msg);
        }
        return messages;
    }

    /**
     * Load HELIOS messages from Room database that are newer than given timestamp.
     *
     * @param sinceTs timestamp to compare messages.
     * @param topic Topic name to be requested.
     * @return Array list of HeliosMessagePart objects
     */
    public ArrayList<HeliosMessagePart> loadMessages(String topic, long sinceTs) {
        List<HeliosData> reply = mHeliosDataDao.loadMessages(topic, sinceTs);
        ArrayList<HeliosMessagePart> messages = new ArrayList<HeliosMessagePart>();
        ListIterator<HeliosData> it = reply.listIterator();
        while (it.hasNext()) {
            HeliosMessagePart msg = convertFromDBEntry(it.next());
            messages.add(msg);
        }
        return messages;
    }

    /**
     * Load HELIOS direct message topics from Room database (having UUID).
     *
     * @return Array list of HeliosTopicContext objects
     */
    public ArrayList<HeliosTopicContext> loadDirectMessageTopics() {
        List<String> topicNames = mHeliosDataDao.getTopics();
        ArrayList<HeliosTopicContext> topics = new ArrayList<HeliosTopicContext>();
        ListIterator<String> it = topicNames.listIterator();
        while (it.hasNext()) {
            try {
                String topicStr = it.next();
                if(!TextUtils.isEmpty(topicStr)) {
                    String testUUID = UUID.fromString(topicStr).toString();
                    HeliosTopicContext topicContext = new HeliosTopicContext(topicStr, "", "", "");
                    topicContext.uuid = topicStr;
                    topics.add(topicContext);
                }
            } catch (IllegalArgumentException e) {
                // Ignore, intended if topic is not UUID
            }
        }
        return topics;
    }

    /**
     * Delete expired database row entries that are older than the threshold time that is
     * given as a parameter.
     *
     * @param milliseconds Expiration threshold time as milliseconds from Epoch
     */
    public void deleteExpiredEntries(long milliseconds) {
        mHeliosDataDao.deleteExpiredEntries(milliseconds);
    }

    /**
     * Close database connection
     */
    public void closeDatabase() {
    }

    /**
     * Convert text string timestamp value to numerical value (milliseconds)
     *
     * @param timestamp Timestamp as text string
     * @return Timestamp in milliseconds
     */
    private long convertTimestampToMilliseconds(String timestamp) {
        long milliseconds;

        // Extract Epoch milliseconds as long int from ZonedDateTime text format
        if (timestamp != null) {
            try {
                milliseconds = ZonedDateTime.parse(timestamp).toInstant().toEpochMilli();
            } catch (DateTimeParseException e) {
                milliseconds = ZonedDateTime.now().toInstant().toEpochMilli();
                Log.d(TAG, "Cannot parse timestamp - setting as current time");
            }
        } else {
            milliseconds = ZonedDateTime.now().toInstant().toEpochMilli();
            Log.d(TAG, "Timestamp missing - setting as current time");
        }
        return milliseconds;
    }

    /**
     * Convert internal HeliosMessagePart to Room database entity
     *
     * @param message HeliosMessagePart instance
     * @return HeliosData instance
     */
    private HeliosData convertToDBEntry(HeliosMessagePart message) {
        HeliosData data = new HeliosData();

        // This test is a workaround to a case if somebody is sending a message without
        // a message type using the old version of the client.
        data.mMessageType = (message.messageType == null) ? 0 : message.messageType.ordinal();
        data.mReceived = message.msgReceived ? true : false;
        data.mSenderName = message.senderName;
        data.mSenderUUID = message.senderUUID;
        data.mTopic = message.to;
        data.mTimestamp = message.ts;
        data.mMessageUUID = message.getUuid();
        data.mMilliseconds = convertTimestampToMilliseconds(message.ts);
        data.mMediaFilename = (message.mediaFileName != null) ? message.mediaFileName : null;
        data.mMessage = message.msg;
        data.mProtocol = message.protocol;
        data.mOriginalType = (message.originalType == null) ? 0 : message.originalType.ordinal();
        data.mSenderNetworkId = message.senderNetworkId;

        return data;
    }

    /**
     * Convert Room databse HeliosData to HeliosMessagePart internall data structure
     *
     * @param data HeliosData instance
     * @return HeliosMessagePart instance
     */
    private HeliosMessagePart convertFromDBEntry(HeliosData data) {
        HeliosMessagePart msg = new HeliosMessagePart(data.mMessage,
                                                      data.mSenderName,
                                                      data.mSenderUUID,
                                                      data.mTopic,
                                                      data.mTimestamp,
                                                      HeliosMessagePart.IntToMessagePartType(data.mMessageType));
        msg.uuid = data.mMessageUUID;
        msg.mediaFileName = data.mMediaFilename;
        msg.msgReceived = data.mReceived;
        msg.protocol = data.mProtocol;
        msg.originalType = HeliosMessagePart.IntToMessagePartType(data.mOriginalType);
        msg.senderNetworkId = data.mSenderNetworkId;
        return msg;
    }
}
