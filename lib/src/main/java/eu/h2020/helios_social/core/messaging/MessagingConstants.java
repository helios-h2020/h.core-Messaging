package eu.h2020.helios_social.core.messaging;

/**
 * A class for constants used in the Messaging module.
 */
public class MessagingConstants {
    // HELIOS general
    public static final String HELIOS_RECEIVED_FILENAME_START = "helios-received-";
    public static final String HELIOS_RECEIVED_DATETIME_PATTERN = "YYYYMMddhhmmss";
    // Message headers
    public static final String HELIOS_HEADER_JSON = "json";
    public static final String HELIOS_HEADER_MEDIA = "media";
    public static long MAX_UPLOAD_SIZE_BYTES = 1024*1024*20L; // 20 MB
    public static int RECONNECT_TIME_MILLISECONDS = 10*1000;  // 10 seconds
}
