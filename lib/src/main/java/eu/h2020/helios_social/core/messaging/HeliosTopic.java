package eu.h2020.helios_social.core.messaging;

/**
 * A class to encapsulate HELIOS topic. The current implementation assumes
 * that a topic has name and password fields. Future implementation could add
 * more.
 */
public class HeliosTopic {
    private String topicName;
    private String topicPassword;

    /**
     * Constructor for HeliosTopic
     * @param topicName name for the topic.
     * @param topicPassword optional password for the topic.
     */
    public HeliosTopic(String topicName, String topicPassword) {
        this.topicName = topicName;
        this.topicPassword = topicPassword;
    }

    /**
     * Get the topic name of this topic.
     * @return topic name.
     */
    public String getTopicName() {
        return topicName;
    }

    /**
     * Get the password of the topic.
     * @return password.
     */
    public String getTopicPassword() {
        return topicPassword;
    }
}

