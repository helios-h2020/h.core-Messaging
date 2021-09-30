package eu.h2020.helios_social.core.messaging.data;

import android.util.Log;

import java.util.ArrayList;

/**
 * Stores a conversation list and provides methods to query topics and conversations separately.
 */
public class HeliosConversationList {
    private static final String TAG = "HeliosConversationList";
    private static HeliosConversationList sInstance = new HeliosConversationList();

    //not synchronized
    private ArrayList<HeliosConversation> conversations;
    private ArrayList<HeliosTopicContext> topics;
    //Collections.synchronizedList(new ArrayList
    private final Object lock = new Object();

    /**
     * Constructor.
     */
    public HeliosConversationList() {
        conversations = new ArrayList<>();
        topics = new ArrayList<>();
    }

    /**
     * Add conversation and update topic.
     *
     * @param conv {@link HeliosConversation}
     */
    public void addConversation(HeliosConversation conv) {
        Log.d(TAG, "addConversation: " + conv.topic.topic);
        synchronized (lock) {
            conversations.add(conv);
            topics.add(conv.topic);
        }
    }

    /**
     * Replace whole conversation.
     *
     * @param conversationArr array of {@link HeliosConversation}
     */
    public void replaceConversations(ArrayList<HeliosConversation> conversationArr) {
        Log.d(TAG, "replaceConversations()");
        synchronized (lock) {
            conversations.clear();
            topics.clear();
            for (HeliosConversation conversation : conversationArr) {
                addConversation(conversation);
            }
        }
    }

    /**
     * Get list of topics.
     *
     * @return ArrayList of {@link HeliosTopicContext}
     */
    public ArrayList<HeliosTopicContext> getTopics() {
        return topics;
    }

    /**
     * Get list of conversations.
     *
     * @return ArrayList of {@link HeliosConversation}
     */
    public ArrayList<HeliosConversation> getConversations() {
        return conversations;
    }

    /**
     * Get specific conversation by topic.
     *
     * @param topicName to search.
     * @return {@link HeliosConversation} or null if not found.
     */
    public HeliosConversation getConversation(String topicName) {
        Log.d(TAG, "getConversation: " + topicName);

        synchronized (lock) {
            for (int i = 0; i < conversations.size(); i++) {
                HeliosConversation conversation = conversations.get(i);

                if (conversation.topic.topic.equals(topicName)) {
                    return conversation;
                }
            }
            return null;
        }
    }

    /**
     * Get specific conversation by topic UUID.
     *
     * @param topicUUID UUID to search.
     * @return {@link HeliosConversation} or null if not found (or no UUID for topic).
     */
    public HeliosConversation getConversationByTopicUUID(String topicUUID) {
        synchronized (lock) {
            if (topicUUID == null) {
                return null;
            }

            for (int i = 0; i < conversations.size(); i++) {
                HeliosConversation conversation = conversations.get(i);
                if (topicUUID.equals(conversation.topic.uuid)) {
                    return conversation;
                }
            }
            return null;
        }
    }

    /**
     * Delete a specific conversation by topic UUID.
     *
     * @param topicUUID to search.
     * @return true or false depending on deletion.
     */
    public boolean deleteConversationByTopicUUID(String topicUUID) {
        synchronized (lock) {
            if (topicUUID == null) {
                return false;
            }
            boolean res = false;
            HeliosConversation conversation = null;
            for (HeliosConversation conv : conversations) {
                if (conv.topic.uuid != null) {
                    if (conv.topic.uuid.equals(topicUUID)) {
                        conversation = conv;
                    }
                }
            }
            HeliosTopicContext tpc = null;
            for (HeliosTopicContext top : topics) {
                if (top.uuid != null) {
                    if (top.uuid.equals(topicUUID)) {
                        tpc = top;
                    }
                }
            }
            // TODO: sync
            if (conversation != null && tpc != null) {
                topics.remove(tpc);
                conversations.remove(conversation);
                res = true;
            }
            return res;
        }
    }

    /**
     * Get instance of this class.
     *
     * @return {@link HeliosConversationList}
     */
    public static HeliosConversationList getInstance() {
        return sInstance;
    }

}
