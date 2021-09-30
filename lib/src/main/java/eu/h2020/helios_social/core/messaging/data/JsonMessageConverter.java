package eu.h2020.helios_social.core.messaging.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple JSON converter for serializing {@link HeliosMessagePart} messages to JSON and back.
 * Also provides a method to convert an ArrayList of {@link HeliosConversation}.
 */
public class JsonMessageConverter {
    private static final String TAG = "JsonMessageConverter";
    private static JsonMessageConverter sInstance = new JsonMessageConverter();
    private GsonBuilder gsonBuilder;
    private Gson gson;

    private JsonMessageConverter() {
        gsonBuilder = new GsonBuilder();
        gsonBuilder.setDateFormat("M/d/yy hh:mm a");
        gson = gsonBuilder.create();
    }

    public static JsonMessageConverter getInstance() {
        return sInstance;
    }

    /**
     * Convert {@link HeliosMessagePart} to JSON String.
     *
     * @param msg {@link HeliosMessagePart} to convert.
     * @return JSON String.
     */
    public String convertToJson(HeliosMessagePart msg) {
        return gson.toJson(msg);
    }

    /**
     * Read a JSON message of {@link HeliosMessagePart}
     *
     * @param json JSON to convert into {@link HeliosMessagePart}
     * @return {@link HeliosMessagePart}
     * @throws JsonSyntaxException if JSON string parsing failed
     */
    public HeliosMessagePart readHeliosMessagePart(String json) throws JsonSyntaxException {
        return gson.fromJson(json, HeliosMessagePart.class);
    }

    /**
     * Convert ArrayList of {@link HeliosConversation} to JSON.
     *
     * @param conversations ArrayList of {@link HeliosConversation}
     * @return JSON String.
     */
    public String convertConversationListToJson(ArrayList<HeliosConversation> conversations) {
        return gson.toJson(conversations);
    }

    /**
     * Read a JSON message containing list of {@link HeliosConversation}.
     *
     * @param json JSON to convert into ArrayList of {@link HeliosConversation}
     * @return ArrayList of {@link HeliosConversation}
     */
    public ArrayList<HeliosConversation> readConversationList(String json) {
        Type type = new TypeToken<List<HeliosConversation>>() {}.getType();
        return gson.fromJson(json, type);
    }
}
