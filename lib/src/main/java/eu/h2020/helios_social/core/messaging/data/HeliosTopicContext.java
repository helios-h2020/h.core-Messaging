package eu.h2020.helios_social.core.messaging.data;

/**
 * A class storing the information of a HELIOS topic in the current TestClient.
 * Stores the topic name, last timestamp, last message, participants of this topic and is used to present
 * current topics in the GUI side.
 */
public class HeliosTopicContext {
    public String uuid = null;
    public String topic;
    public String lastMsg;
    public String participants;
    public String ts;

    public HeliosTopicContext() {
    }

    /**
     * Constructor.
     *
     * @param topic topic as String
     * @param lastMsg last message in this topic.
     * @param participants participants in this topic
     * @param ts latest timestamp.
     */
    public HeliosTopicContext(String topic, String lastMsg, String participants, String ts) {
        this.uuid = null;
        this.topic = topic;
        this.lastMsg = lastMsg;
        this.participants = participants;
        this.ts = ts;
    }
}
