package eu.h2020.helios_social.core.messaging;

/**
 * A class to encapsulate identity information. This should contain at least
 * the nickname of the user.
 */
public class HeliosIdentityInfo {
    private String mNickname;
    private String mUserUUID;

    /**
     * Constructor for the class.
     * @param nickname nick name to be used for the user.
     * @param userUUID Unique identifier for the user as String presentation of {@link java.util.UUID}.
     */
    public HeliosIdentityInfo(String nickname, String userUUID) {
        this.mNickname = nickname;
        this.mUserUUID = userUUID;
    }

    /**
     * Return the nick name.
     * @return nick name.
     */
    public String getNickname() {
        return mNickname;
    }

    /**
     * Return user {@link java.util.UUID}
     * @return String representation of user {@link java.util.UUID}.
     */
    public String getUserUUID() {
        return mUserUUID;
    }
}
