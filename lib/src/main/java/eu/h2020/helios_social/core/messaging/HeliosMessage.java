package eu.h2020.helios_social.core.messaging;

/**
 * A class to encapsulate HELIOS messages. The current implementation assumes that
 * this is a string. However, it is possible to add more structure to HELIOS messages
 * e.g., JSON format could be used. In addition, a media file name can be added in case
 * of media file is attached to this message.
 */
public class HeliosMessage {
    private String msg;
    private String mediaFileName;

    /**
     * Constructor for a message with only text message. Sets the mediaFileName as null.
     *
     * @param msg Text message.
     */
    public HeliosMessage(String msg) {
        this.msg = msg;
        this.mediaFileName = null;
    }

    /**
     * Constructor for a message with media file attached to it.
     *
     * @param msg Text message.
     * @param mediaFileName file name for the attached media file. It can be accessed with this
     *                      name via the {@link eu.h2020.helios_social.core.storage.HeliosStorageUtils} module.
     */
    public HeliosMessage(String msg, String mediaFileName) {
        this.msg = msg;
        this.mediaFileName = mediaFileName;
    }

    /**
     * Get the text part of the message.
     * @return text
     */
    public String getMessage() {
        return msg;
    }

    /**
     * Get the media file name attached
     * @return media file name or null.
     */
    public String getMediaFileName() {
        return mediaFileName;
    }
}
