package eu.h2020.helios_social.core.messaging.nodejs

import com.google.gson.annotations.SerializedName

enum class NodejsMessageType {
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
