package eu.h2020.helios_social.core.messaging.streamr

import com.google.gson.annotations.SerializedName

enum class StreamrMessageType {
    @SerializedName("call")
    CALL,
    @SerializedName("return")
    RETURN,
    @SerializedName("return-error")
    RETURN_ERROR,
    @SerializedName("event")
    EVENT,
    @SerializedName("stream")
    STREAM,
}
