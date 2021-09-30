package eu.h2020.helios_social.core.messaging.data;

import android.util.Log;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;

import java.util.Locale;
import java.util.UUID;

/**
 * Basic class to store a message that is sent to other HELIOS users - in a serialized format.
 * See {@link JsonMessageConverter}.
 */
public class HeliosMessagePart {
    private static final String TAG = "HeliosMessagePart";
    public String uuid;
    public String msg;
    public String senderUUID;
    public String senderName;
    public String to;
    public String ts;
    public MessagePartType messageType;
    public String mediaFileName;
    public byte[] mediaFileData;
    // LocalizedDateTime to be built based on received ts.
    private String localeTs;
    public boolean msgReceived;
    public String sinceTs;
    public MessagePartType originalType;
    public String senderNetworkId;
    public String protocol;

    public enum MessagePartType {
        MESSAGE,
        HEARTBEAT,
        JOIN,
        LEAVE,
        PUBSUB_SYNC_RESEND,
        INVALID
    }

    public static MessagePartType IntToMessagePartType(int val)
    {
        switch (val) {
            case 0:
                return MessagePartType.MESSAGE;
            case 1:
                return MessagePartType.HEARTBEAT;
            case 2:
                return MessagePartType.JOIN;
            case 3:
                return MessagePartType.LEAVE;
            case 4:
                return MessagePartType.PUBSUB_SYNC_RESEND;
        }
        return MessagePartType.INVALID;
    }

    /**
     * Constructor for a HeliosMessagePart.
     *
     * @param msg        message
     * @param senderName sender nick name
     * @param senderUUID sender UUID
     * @param to         recipient of the message.
     * @param ts         local timestamp.
     */
    public HeliosMessagePart(String msg, String senderName, String senderUUID, String to, String ts) {
        this(msg, senderName, senderUUID, to, ts, MessagePartType.MESSAGE);
    }

    /**
     * Constructor for a HeliosMessagePart.
     *
     * @param msg         message
     * @param senderName  sender nick name
     * @param senderUUID  sender UUID
     * @param to          recipient of the message.
     * @param ts          local timestamp.
     * @param messageType Type or purpose of this message
     */
    public HeliosMessagePart(String msg, String senderName, String senderUUID, String to, String ts, MessagePartType messageType) {
        this.uuid = UUID.randomUUID().toString();
        this.msg = msg;
        this.senderName = senderName;
        this.senderUUID = senderUUID;
        this.to = to;
        this.ts = ts;
        this.messageType = messageType == null ? MessagePartType.MESSAGE : messageType;
        this.mediaFileName = null;
        this.mediaFileData = null;
        this.msgReceived = false;
    }

    /**
     * Copy constructor - create a copy of the original message
     *
     * @param message Original message
     */
    public HeliosMessagePart(HeliosMessagePart message) {
        this.uuid = message.uuid;
        this.msg = message.msg;
        this.senderUUID = message.senderUUID;
        this.senderName = message.senderName;
        this.to = message.to;
        this.ts = message.ts;
        this.messageType = message.messageType;
        this.mediaFileName = message.mediaFileName;
        if ((message.mediaFileData != null) && (message.mediaFileData.length > 0)) {
            this.mediaFileData = message.mediaFileData.clone();
        }
        this.localeTs = message.localeTs;
        this.msgReceived = message.msgReceived;
        this.sinceTs = message.sinceTs;
        this.originalType = message.originalType;
        this.senderNetworkId = message.senderNetworkId;
        this.protocol = message.protocol;
    }


    /**
     * Return localized timestamp from the ZonedDateTime. When a message is received, this will be
     * constructed by local settings (LocalizedDateTime).
     *
     * @return localized timestamp string.
     */
    public String getLocaleTs() {
        // TODO: make getter and setter for ts.

        if (localeTs == null) {
            try {
                ZonedDateTime dt = ZonedDateTime.parse(ts);
                localeTs = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault()).withLocale(Locale.getDefault()).format(dt);
                Log.d(TAG, "setNewDate " + localeTs);
            } catch (DateTimeParseException ex) {
                // use plain text if old format
                localeTs = ts;
                Log.d(TAG, "old format error using old " + localeTs);
            }
        }
        return localeTs;
    }

    /**
     * Get the UUID of this message.
     * @return String representation of the UUID.
     */
    public String getUuid() {
        return uuid;
    }

    @Override
    public String toString() {
        return TAG + "['type': " + messageType + ", 'msg:'" + msg + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof HeliosMessagePart) {
            if (this.uuid != null) {
                HeliosMessagePart anotherMessage = (HeliosMessagePart) obj;
                return this.uuid.equals(anotherMessage.uuid);
            }
        }
        return false;
    }

    /**
     * Parse zoned sender timestamp and convert it as milliseconds from Unix Epoch time.
     *
     * @return Message send time as milliseconds from Unix Epoch time
     */
    public long getTimestampAsMilliseconds() {
        // TODO: We should store this, so we would not create it every time it is asked.
        long millis;
        if (ts != null) {
            try {
                millis = java.time.ZonedDateTime.parse(ts).toInstant().toEpochMilli();
            } catch (DateTimeParseException e) {
                millis = java.time.ZonedDateTime.now().toInstant().toEpochMilli();
                Log.d(TAG, "Cannot parse timestamp - setting as current time");
            }
        } else {
            millis = java.time.ZonedDateTime.now().toInstant().toEpochMilli();
            Log.d(TAG, "Timestamp missing - setting as current time");
        }
        return millis;
    }
}
