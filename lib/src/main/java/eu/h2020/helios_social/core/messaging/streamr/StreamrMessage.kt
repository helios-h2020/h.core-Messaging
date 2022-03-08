package eu.h2020.helios_social.core.messaging.streamr

import com.google.gson.*
import java.lang.reflect.Type


interface StreamrMessage {
    var type: StreamrMessageType?
    var id: String?
    var name: String?
    val data: Any?
    var close: Boolean?
    var error: Any?

    fun toJson(): String
    fun isValid(): Boolean

    class MsgDeserializer : JsonDeserializer<StreamrMessage> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext
        ): StreamrMessage {
            val objType = context.deserialize<StreamrMessageType>(
                json?.asJsonObject?.get("type"),
                StreamrMessageType::class.java
            )

            return when (objType) {
                StreamrMessageType.EVENT -> context.deserialize<StreamrMessageEvent>(
                    json,
                    StreamrMessageEvent::class.java
                )
                StreamrMessageType.STREAM -> context.deserialize<StreamrMessageEvent>(
                    json,
                    StreamrMessageStream::class.java
                )
                StreamrMessageType.RETURN -> context.deserialize<StreamrMessageEvent>(
                    json,
                    StreamrMessageReturn::class.java
                )
                StreamrMessageType.RETURN_ERROR -> context.deserialize<StreamrMessageEvent>(
                    json,
                    StreamrMessageReturn::class.java
                )
                StreamrMessageType.CALL -> context.deserialize<StreamrMessageEvent>(
                    json,
                    StreamrMessageCall::class.java
                )
                null -> StreamrMessageUntyped()
            }
        }
    }

    companion object {
        private val gsonBuilder = GsonBuilder()
        private val gson: Gson

        init {
            gson = gsonBuilder
                .registerTypeAdapter(StreamrMessage::class.java, MsgDeserializer())
                .enableComplexMapKeySerialization()
                .create()
        }

        fun fromJson(data: String): StreamrMessage? {
            return gson.fromJson(data, StreamrMessage::class.java)
        }

        fun toJson(msg: StreamrMessage): String {
            return gson.toJson(msg)
        }
    }
}

class StreamrMessageUntyped(
    override var type: StreamrMessageType? = null,
    override var id: String? = null,
    override var name: String? = null,
    override var data: Any? = null,
    override var close: Boolean? = null,
    override var error: Any? = null
) : StreamrMessage {
    override fun toJson(): String = StreamrMessage.toJson(this)
    override fun isValid(): Boolean = true
}