package eu.h2020.helios_social.core.messaging.p2p

import com.google.gson.annotations.SerializedName

enum class Libp2pMessageType {
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
