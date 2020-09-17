package eu.h2020.helios_social.core.messaging;

/**
 * An interface for HELIOS messaging operations that an underlying message implementation should
 * implement in order to connect, disconnect, publish and subscribe to messages.
 */
public interface HeliosMessaging {
    /**
     * Connect to the network using provided information.
     *
     * @param connection {@link HeliosConnectionInfo} connection info.
     * @param identity {@link HeliosIdentityInfo}
     * @throws HeliosMessagingException connect operation failed.
     */
    void connect(HeliosConnectionInfo connection, HeliosIdentityInfo identity)
            throws HeliosMessagingException;

    /**
     * Disconnect from the previously connected network.
     *
     * @param connection {@link HeliosConnectionInfo}
     * @param identity {@link HeliosIdentityInfo}
     * @throws HeliosMessagingException disconnect operation failed.
     */
    void disconnect(HeliosConnectionInfo connection, HeliosIdentityInfo identity)
            throws HeliosMessagingException;

    /**
     * Publish a message for others to receive. A message is sent to a specific topic.
     *
     * @param topic {@link HeliosTopic} information.
     * @param message {@link HeliosMessage} to be published.
     * @throws HeliosMessagingException publish operation failed.
     */
    void publish(HeliosTopic topic, HeliosMessage message)
            throws HeliosMessagingException;

    /**
     * Subscribe to a given topic to receive messages via listener. Implementation can add a new
     * listener only and re-use the existing topic, if one exists.
     *
     * @param topic {@link HeliosTopic}
     * @param listener {@link HeliosMessageListener}
     * @throws HeliosMessagingException subscribe operation failed.
     */
    void subscribe(HeliosTopic topic, HeliosMessageListener listener)
            throws HeliosMessagingException;

    /**
     * Unsubscribe from a topic fully, closes all listeners to it.
     * @param topic {@link HeliosTopic} to unsubscribe.
     * @throws HeliosMessagingException unsubscribe operation failed.
     */
    void unsubscribe(HeliosTopic topic)
            throws HeliosMessagingException;

    /**
     * Unsubscribe a a specific listener (e.g., if a topic has multiple listeners).
     * @param listener {@link HeliosMessageListener}
     * @throws HeliosMessagingException unsubscribeListener operation failed.
     */
    void unsubscribeListener(HeliosMessageListener listener)
            throws HeliosMessagingException;

    /**
     * Search content for a given pattern.
     * @param pattern {@link HeliosTopicMatch }
     * @return List of {@link HeliosTopic}
     * @throws HeliosMessagingException search operation failed.
     */
    HeliosTopic[] search(HeliosTopicMatch pattern)
            throws HeliosMessagingException;
}

