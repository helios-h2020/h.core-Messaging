package eu.h2020.helios_social.core.messaging;

import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import eu.h2020.helios_social.core.messaging.data.HeliosConversation;
import eu.h2020.helios_social.core.messaging.data.HeliosMessagePart;

import static junit.framework.TestCase.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest({HeliosConversation.class , Log.class})
public class HeliosConversationTest {
    HeliosMessagePart testMsg1;
    HeliosMessagePart testMsg2;

    @Before
    public void setUp() {
        PowerMockito.mockStatic(Log.class);
        this.testMsg1 = new HeliosMessagePart("test1", "sender",
                UUID.randomUUID().toString(), "receiver", ZonedDateTime.now().toString());
        this.testMsg2 = new HeliosMessagePart("test2", "sender",
                UUID.randomUUID().toString(), "receiver", ZonedDateTime.now().toString());
    }

    @Test
    public void addMessageTest() {
        boolean added;
        HeliosConversation conversation = new HeliosConversation();
        added = conversation.addMessage(testMsg1);
        assertTrue(added);
        added= conversation.addMessage(testMsg2);
        assertTrue(added);
        // Do not add again
        added = conversation.addMessage(testMsg2);
        assertTrue(!added);
        // We have added two messages
        List<HeliosMessagePart> messages = conversation.getMessagesAfter(0);
        assertTrue(messages.size() == 2);
    }

    @Test
    public void setReceivedFlagTest() {
        HeliosConversation conversation = new HeliosConversation();
        // The message is first not received
        assertTrue(!testMsg1.msgReceived);
        conversation.addMessage(testMsg1);
        conversation.addMessage(testMsg2);
        // The message is still not received
        assertTrue(!testMsg1.msgReceived);
        conversation.setMessageReceivedValue(testMsg1.uuid, true);
        // The message is now received
        assertTrue(testMsg1.msgReceived);
        conversation.setMessageReceivedValue(testMsg1.uuid, false);
        // The message is again not received
        assertTrue(!testMsg1.msgReceived);
    }

    @After
    public void tearDown() {

    }
}
