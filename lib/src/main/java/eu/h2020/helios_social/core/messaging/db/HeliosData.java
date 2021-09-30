package eu.h2020.helios_social.core.messaging.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Helios message storage database data entity. This is used in Room persistence library
 */
@Entity(tableName="messages")
public class HeliosData {

    @PrimaryKey(autoGenerate = true)
    public long mId;

    @ColumnInfo(name = "Message_Type")
    public int mMessageType;

    @ColumnInfo(name = "Original_Type")
    public int mOriginalType;

    @ColumnInfo(name = "Received")
    public boolean mReceived;

    @ColumnInfo(name = "Sender_Name")
    public String mSenderName;

    @ColumnInfo(name = "Sender_UUID")
    public String mSenderUUID;

    @ColumnInfo(name = "Protocol")
    public String mProtocol;

    @ColumnInfo(name = "Topic")
    public String mTopic;

    @ColumnInfo(name = "Timestamp")
    public String mTimestamp;

    @ColumnInfo(name = "Message_UUID")
    public String mMessageUUID;

    @ColumnInfo(name = "Milliseconds")
    public long mMilliseconds;

    @ColumnInfo(name = "Media_Filename")
    public String mMediaFilename;

    @ColumnInfo(name="Sender_NetworkId")
    public String mSenderNetworkId;

    @ColumnInfo(name = "Message")
    public String mMessage;

}

