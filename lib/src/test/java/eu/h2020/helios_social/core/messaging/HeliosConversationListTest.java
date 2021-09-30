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
import java.util.ArrayList;
import java.util.UUID;

import eu.h2020.helios_social.core.messaging.data.HeliosConversation;
import eu.h2020.helios_social.core.messaging.data.HeliosConversationList;
import eu.h2020.helios_social.core.messaging.data.HeliosMessagePart;
import eu.h2020.helios_social.core.messaging.data.HeliosTopicContext;

import static junit.framework.TestCase.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest({HeliosConversationList.class , Log.class})
public class HeliosConversationListTest {
    HeliosMessagePart conv1TestMsg1;
    HeliosMessagePart conv1TestMsg2;
    HeliosMessagePart conv2TestMsg1;
    HeliosMessagePart conv2TestMsg2;

    @Before
    public void setUp() {
        PowerMockito.mockStatic(Log.class);
        this.conv1TestMsg1 = new HeliosMessagePart("test1", "sender",
                UUID.randomUUID().toString(), "receiver", ZonedDateTime.now().toString());
        this.conv1TestMsg2 = new HeliosMessagePart("test2", "sender",
                UUID.randomUUID().toString(), "receiver", ZonedDateTime.now().toString());
        this.conv2TestMsg1 = new HeliosMessagePart("test1", "sender",
                UUID.randomUUID().toString(), "receiver", ZonedDateTime.now().toString());
        this.conv2TestMsg2 = new HeliosMessagePart("test2", "sender",
                UUID.randomUUID().toString(), "receiver", ZonedDateTime.now().toString());
    }

    @Test
    public void addConversationTest() {
        HeliosConversation conv1 = new HeliosConversation();
        conv1.addMessage(this.conv1TestMsg1);
        conv1.addMessage(this.conv1TestMsg2);
        conv1.topic.topic = new String("CONVERSATION1");
        HeliosConversation conv2 = new HeliosConversation();
        conv2.addMessage(this.conv2TestMsg1);
        conv2.addMessage(this.conv2TestMsg2);
        conv2.topic.topic = new String("CONVERSATION2");

        HeliosConversationList conversations = new HeliosConversationList();
        conversations.addConversation(conv1);
        conversations.addConversation(conv2);

        ArrayList<HeliosTopicContext> topics = conversations.getTopics();
        assertTrue(topics.size() == 2);
        ArrayList<HeliosConversation> convs = conversations.getConversations();
        assertTrue(convs.size() == 2);
    }

    @After
    public void tearDown() {

    }
}
