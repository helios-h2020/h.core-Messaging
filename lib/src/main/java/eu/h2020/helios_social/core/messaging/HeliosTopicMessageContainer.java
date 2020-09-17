package eu.h2020.helios_social.core.messaging;

/**
 * A class to encapsulate {@link HeliosMessage} and {@link HeliosTopic} into one container.
 */
public class HeliosTopicMessageContainer {
    private HeliosMessage heliosMessage;
    private HeliosTopic heliosTopic;

    public HeliosTopicMessageContainer( HeliosTopic topic, HeliosMessage message) {
        this.heliosTopic = topic;
        this.heliosMessage = message;
    }

    public HeliosMessage getHeliosMessage() {
        return heliosMessage;
    }

    public HeliosTopic getHeliosTopic() {
        return heliosTopic;
    }

}
