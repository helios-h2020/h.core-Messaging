package eu.h2020.helios_social.core.messaging.db;

import android.content.Context;
import android.util.Log;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.ZonedDateTime;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class MessageStoreTest {
    private static final String TAG = "MessageStoreTest";
    private HeliosDataDao heliosDao;
    private HeliosDatabase db;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, HeliosDatabase.class).build();
        heliosDao = db.heliosDataDao();
    }

    /**
     * Insert one entry and test that we will get only one entry
     * @throws Exception
     */
    @Test
    public void addOneEntity() throws Exception {
        HeliosData data = createTestData1();
        heliosDao.addMessages(data);

        List<HeliosData> messages = heliosDao.dumpDB();
        assertTrue(messages.size() == 1);
    }

    /**
     * Insert one entry and test data fields
     * @throws Exception
     */
    @Test
    public void addOneEntityCheckData() throws Exception {
        HeliosData data = createTestData1();
        heliosDao.addMessages(data);

        List<HeliosData> messages = heliosDao.dumpDB();
        if (messages.size() != 1) {
            fail();
        }
        HeliosData msg = messages.get(0);
        if (msg.mMessageType != 1) {
            fail();
        }
        if (msg.mOriginalType != 1) {
            fail();
        }
        if (msg.mReceived != true) {
            fail();
        }
        if (!msg.mSenderName.equals("Test11")) {
            fail();
        }
        if (!msg.mSenderUUID.equals("18ef9bce-70a4-445a-be6c-fd60792d6983")) {
            fail();
        }
        if (!msg.mTopic.equals("test")) {
            fail();
        }
        if (!msg.mTimestamp.equals("2021-02-11T05:52:45.248Z")) {
            fail();
        }
        if (msg.mMilliseconds != ZonedDateTime.parse(msg.mTimestamp).toInstant().toEpochMilli()) {
            fail();
        }
        if (!msg.mMessageUUID.equals("dace7176-481d-4ab2-b022-7b3d1c28a604")) {
            fail();
        }
        if (!msg.mMediaFilename.equals("name")) {
            fail();
        }
        if (!msg.mMessage.equals("test message")) {
            fail();
        }
        if (!msg.mProtocol.equals("UNKNOWN")) {
            fail();
        }
        if (!msg.mSenderNetworkId.equals("UNKNOWN")) {
            fail();
        }
        assertTrue(1 + 1 == 2);
    }

    /**
     * Toggle received field
     */
    @Test
    public void updateReceivedField() {
        HeliosData data = createTestData1();
        heliosDao.addMessages(data);
        boolean original = data.mReceived;
        heliosDao.setReceivedField(data.mMessageUUID, !original);
        List<HeliosData> messages = heliosDao.dumpDB();
        if (messages.size() != 1) {
            fail();
        }
        HeliosData msg = messages.get(0);
        assertTrue(original != msg.mReceived);
    }

    @Test
    public void queryMessagesFromTopic() {
        heliosDao.addMessages(createTestData1());
        heliosDao.addMessages(createTestData2());
        heliosDao.addMessages(createTestData3());
        heliosDao.addMessages(createTestData4());
        List<HeliosData> messages = heliosDao.loadMessages(new String("test"));
        if (messages == null) {
            Log.e(TAG, "No messages returned");
            fail();
        }
        if (messages.size() != 3) {
            Log.e(TAG, "Wrong number of messages " + messages.size());
            fail();
        }
        if (!messages.get(0).mMessageUUID.equals("288d41ba-6f47-11eb-a21a-338b73eb3979")) {
            fail();
        }
        if (!messages.get(1).mMessageUUID.equals("dace7176-481d-4ab2-b022-7b3d1c28a604")) {
            fail();
        }
        if (!messages.get(2).mMessageUUID.equals("ec38ff4c-6f46-11eb-8b54-9bee13b4aad9")) {
            fail();
        }
        assertTrue(1 + 1 == 2);
    }

    @Test
    public void queryMessagesSince() {
        heliosDao.addMessages(createTestData1());
        heliosDao.addMessages(createTestData2());
        heliosDao.addMessages(createTestData3());
        heliosDao.addMessages(createTestData4());
        List<HeliosData> messages = heliosDao.loadMessages(new String("test"),
                ZonedDateTime.parse("2021-02-11T00:00:00.000Z").toInstant().toEpochMilli());
        if (messages == null) {
            Log.e(TAG, "No messages returned");
            fail();
        }
        if (messages.size() != 2) {
            Log.e(TAG, "Wrong number of messages " + messages.size());
            fail();
        }
        if (!messages.get(0).mMessageUUID.equals("dace7176-481d-4ab2-b022-7b3d1c28a604")) {
            fail();
        }
        if (!messages.get(1).mMessageUUID.equals("ec38ff4c-6f46-11eb-8b54-9bee13b4aad9")) {
            fail();
        }
        assertTrue(1 + 1 == 2);
    }

    @Test
    public void queryTopics() {
        heliosDao.addMessages(createTestData1());
        heliosDao.addMessages(createTestData2());
        heliosDao.addMessages(createTestData3());
        heliosDao.addMessages(createTestData4());
        List<String> topics = heliosDao.getTopics();
        if (topics == null) {
            Log.e(TAG, "No topics found");
            fail();
        }
        if (topics.size() != 2) {
            Log.e(TAG, "Wrong number of topics " + topics.size());
            fail();
        }
        if (!topics.get(0).equals("another")) {
            fail();
        }
        if (!topics.get(1).equals("test")) {
            fail();
        }
        assertTrue(1 + 1 == 2);
    }

    @Test
    public void deleteOldMessages() {
        heliosDao.addMessages(createTestData1());
        heliosDao.addMessages(createTestData2());
        heliosDao.addMessages(createTestData3());
        heliosDao.addMessages(createTestData4());
        heliosDao.deleteExpiredEntries(ZonedDateTime.parse("2021-02-11T00:00:00.000Z").toInstant().toEpochMilli());
        List<HeliosData> messages = heliosDao.loadMessages(new String("test"));
        if (messages == null) {
            Log.e(TAG, "No messages returned");
            fail();
        }
        if (messages.size() != 2) {
            Log.e(TAG, "Wrong number of messages " + messages.size());
            fail();
        }
        if (!messages.get(0).mMessageUUID.equals("dace7176-481d-4ab2-b022-7b3d1c28a604")) {
            fail();
        }
        if (!messages.get(1).mMessageUUID.equals("ec38ff4c-6f46-11eb-8b54-9bee13b4aad9")) {
            fail();
        }
        assertTrue(1 + 1 == 2);
    }

    @After
    public void tearDown() throws IOException {
        db.close();
    }

    private HeliosData createTestData1() {
        HeliosData data = new HeliosData();
        data.mMessageType = 1;
        data.mOriginalType = 1;
        data.mReceived = true;
        data.mSenderName = "Test11";
        data.mSenderUUID = "18ef9bce-70a4-445a-be6c-fd60792d6983";
        data.mTopic = "test";
        data.mTimestamp = "2021-02-11T05:52:45.248Z";
        data.mMilliseconds = ZonedDateTime.parse(data.mTimestamp).toInstant().toEpochMilli();
        data.mMessageUUID = "dace7176-481d-4ab2-b022-7b3d1c28a604";
        data.mMediaFilename = "name";
        data.mMessage = "test message";
        data.mProtocol = "UNKNOWN";
        data.mSenderNetworkId = "UNKNOWN";
        return data;
    }

    private HeliosData createTestData2() {
        HeliosData data = new HeliosData();
        data.mMessageType = 1;
        data.mOriginalType = 1;
        data.mReceived = true;
        data.mSenderName = "Test12";
        data.mSenderUUID = "c2e8c6d6-6f46-11eb-9d0b-53b93f437c3e";
        data.mTopic = "test";
        data.mTimestamp = "2021-02-12T05:52:45.248Z";
        data.mMilliseconds = ZonedDateTime.parse(data.mTimestamp).toInstant().toEpochMilli();
        data.mMessageUUID = "ec38ff4c-6f46-11eb-8b54-9bee13b4aad9";
        data.mMediaFilename = null;
        data.mMessage = "new test message";
        data.mProtocol = "UNKNOWN";
        data.mSenderNetworkId = "UNKNOWN";
        return data;
    }

    private HeliosData createTestData3() {
        HeliosData data = new HeliosData();
        data.mMessageType = 1;
        data.mOriginalType = 1;
        data.mReceived = true;
        data.mSenderName = "Test2";
        data.mSenderUUID = "c2e8c6d6-6f46-11eb-9d0b-53b93f437c3e";
        data.mTopic = "test";
        data.mTimestamp = "2021-02-10T05:52:45.248Z";
        data.mMilliseconds = ZonedDateTime.parse(data.mTimestamp).toInstant().toEpochMilli();
        data.mMessageUUID = "288d41ba-6f47-11eb-a21a-338b73eb3979";
        data.mMediaFilename = null;
        data.mMessage = "new message from sender Test2";
        data.mProtocol = "UNKNOWN";
        data.mSenderNetworkId = "UNKNOWN";
        return data;
    }

    private HeliosData createTestData4() {
        HeliosData data = new HeliosData();
        data.mMessageType = 1;
        data.mOriginalType = 1;
        data.mReceived = true;
        data.mSenderName = "Test2";
        data.mSenderUUID = "c2e8c6d6-6f46-11eb-9d0b-53b93f437c3e";
        data.mTopic = "another";
        data.mTimestamp = "2021-02-13T04:52:45.248Z";
        data.mMilliseconds = ZonedDateTime.parse(data.mTimestamp).toInstant().toEpochMilli();
        data.mMessageUUID = "adda1726-6f47-11eb-a9ce-f7377436fa2d";
        data.mMediaFilename = null;
        data.mMessage = "new message from sender Test2";
        data.mProtocol = "UNKNOWN";
        data.mSenderNetworkId = "UNKNOWN";
        return data;
    }
}
