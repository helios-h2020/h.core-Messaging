package eu.h2020.helios_social.core.messaging;

/**
 * A listener interface for incoming HELIOS messages.
 */
public interface HeliosMessageListener {
    /**
     * This is an interface to a callback function that is called when a message has been received
     * from the network.
     *
     * @param topic HeliosTopic, where the message was received.
     * @param message HeliosMessage, representing the message.
     */
    void showMessage(HeliosTopic topic, HeliosMessage message);
}
