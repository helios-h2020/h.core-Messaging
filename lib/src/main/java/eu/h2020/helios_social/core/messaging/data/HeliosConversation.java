package eu.h2020.helios_social.core.messaging.data;

import android.util.Base64;
import android.util.Base64InputStream;
import android.util.Base64OutputStream;
import android.util.Log;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import eu.h2020.helios_social.core.messaging.HeliosMessagingException;

/**
 * Class to store a conversation list of {@link HeliosMessagePart} in a specific {@link HeliosTopicContext}.
 */
public class HeliosConversation {
    private static final String TAG = "HeliosConversation";
    public ArrayList<HeliosMessagePart> messages;
    public HeliosTopicContext topic;
    private final Object lock = new Object();

    /**
     * Constructor.
     */
    public HeliosConversation() {
        messages = new ArrayList<>();
        topic = new HeliosTopicContext();
    }

    /**
     * Add a message to this conversation.
     *
     * @param msg {@link HeliosMessagePart}
     * @return boolean value if message was already present in this conversation using message UUID.
     */
    public boolean addMessage(HeliosMessagePart msg) {
        boolean found = false;
        synchronized (lock) {
            for (HeliosMessagePart a : messages) {
                if (a.getUuid().equals(msg.getUuid())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                messages.add(msg);
                // TODO: Only sort if needed, we could assume only delayed messages require sorting.
                sortMessageList();
            }
        }

        return !found;
    }

    /**
     * Sets message's received boolean to given value if message available in this conversation.
     *
     * @param uuid Message UUID to set received value.
     * @param val New value for received.
     */
    public void setMessageReceivedValue(String uuid, boolean val) {
        synchronized (lock) {
            for (HeliosMessagePart a : messages) {
                if (a.getUuid().equals(uuid)) {
                    Log.d(TAG, "setReceivedField msg:" + a.msg +" msgReceived was:" + a.msgReceived);
                    a.msgReceived = val;
                    break;
                }
            }
        }
    }

    /**
     * Location where to join the list
     */
    public enum JoinLocation {
        APPEND,
        PREPEND
    }
    /**
     * Joins a list of Helios messages to the beginning of the conversation message list. This
     * operation is used to add old messages from a cache to the conversation. Doesn't add duplicates.
     *
     * @param msglist The list of messages to be added.
     * @param loc Where to join the list end (APPEND) or beginning (PREPEND)
     */
    public void joinMessages(ArrayList<HeliosMessagePart> msglist, JoinLocation loc) {
        Log.d(TAG, "joinMessages start");
        synchronized (lock) {
            // Remove already existing messages from msglist
            for (HeliosMessagePart a : messages) {
                if (msglist.contains(a)) {
                    msglist.remove(a);
                }
            }
            Log.d(TAG, "Number of messages to join: " + msglist.size());

            if (loc == JoinLocation.PREPEND) {
                messages.addAll(0, msglist);
            } else {
                messages.addAll(msglist);
            }
            sortMessageList();
        }
        Log.d(TAG, "joinMessages end" );
    }

    /**
     * Get the latest message by the senders message time stamp `ts` in {@link HeliosMessagePart} or
     * `null` if not found.
     *
     * @return {@link HeliosMessagePart} latest message or null
     */
    public HeliosMessagePart getLatestMessage() {
        synchronized (lock) {
            HeliosMessagePart latest = null;
            long latestTs = 0;

            for (HeliosMessagePart msg : messages) {
                try {
                    if (msg.ts == null || msg.ts.isEmpty()) {
                        continue;
                    }

                    long ts = ZonedDateTime.parse(msg.ts).toEpochSecond();
                    if (ts >= latestTs) {
                        latestTs = ts;
                        latest = msg;
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Could not compare ts in messages " + topic.topic + ": " + e.getMessage());
                }
            }

            return latest;
        }
    }

    public List<HeliosMessagePart> getMessagesAfter(ZonedDateTime dateTime) {
        return getMessagesAfter(dateTime.toEpochSecond());
    }

    public List<HeliosMessagePart> getMessagesAfter(long epoch) {
        synchronized (lock) {
            return getMessageStream(epoch).collect(Collectors.toList());
        }
    }

    private Stream<HeliosMessagePart> getMessageStream(long epoch) {
        return messages.stream().filter(msg -> {
            try {
                return msg.ts != null &&
                        msg.messageType == HeliosMessagePart.MessagePartType.MESSAGE &&
                        ZonedDateTime.parse(msg.ts).toEpochSecond() > epoch;
            } catch (Exception e) {
                Log.d(TAG, "Could not compare ts in messages " + topic.topic + ": " + e.getMessage());
                return false;
            }
        });
    }


    public BloomFilter<String> getMessageBloom(ZonedDateTime dateTime) {
        synchronized (lock) {
            Stream<HeliosMessagePart> msgs = getMessageStream(dateTime.toEpochSecond());
            BloomFilter<String> filter = BloomFilter.create(
                    Funnels.stringFunnel(StandardCharsets.UTF_8),
                    1000, 0.01);

            msgs.map(msg -> {
                    //Log.i(TAG, "Add " + msg.getUuid() + "/" + msg.msg + " to filter set");
                    return msg.getUuid();
                })
                .forEach(filter::put);

            return filter;
        }
    }

    public byte[] formatBloom(ZonedDateTime dateTime) {
        return formatBloom(getMessageBloom(dateTime));
    }

    public static byte[] formatBloom(BloomFilter<String> filter) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        try {
            filter.writeTo(os);
        } catch (IOException e) {
            Log.e(TAG, "Error serailizing message state filter: " + e.getMessage());
        }

        return os.toByteArray();
    }

    public static BloomFilter<String> parseBloom(byte[] data) {
        ByteArrayInputStream is = new ByteArrayInputStream(data);

        try {
            BloomFilter<String> filter = BloomFilter.readFrom(
                    is,
                    Funnels.stringFunnel(StandardCharsets.UTF_8));

            return filter;
        } catch (IOException e) {
            Log.e(TAG, "Error parsing message state filter: " + e.getMessage());
            // FIXME: provide a set of reasonable exceptions
            throw new RuntimeException("Error parsing message state filter: " + e.getMessage());
        }
    }

    private void sortMessageList() {
        messages.sort(new Comparator<HeliosMessagePart>() {
            @Override
            public int compare(HeliosMessagePart o1, HeliosMessagePart o2) {
                long t1 = o1.getTimestampAsMilliseconds();
                long t2 = o2.getTimestampAsMilliseconds();
                if (t1 < t2)
                    return -1;
                if (t1 > t2)
                    return 1;
                return 0;
            }
        });
    }
}
