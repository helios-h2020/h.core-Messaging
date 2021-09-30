package eu.h2020.helios_social.core.messaging

import java.io.Serializable

data class HeliosEgoTag(
    val egoId: String?,
    val networkId: String?,
    val tag: String,
    val timestamp: Long?
) : Serializable, Comparable<HeliosEgoTag> {
    companion object {
        private val comparator = compareBy<HeliosEgoTag> {
            it.tag
        }.thenBy {
            it.egoId ?: ""
        }.thenBy {
            it.networkId ?: ""
        }.thenBy {
            it.timestamp ?: 0
        }
    }

    override fun compareTo(other: HeliosEgoTag) = comparator.compare(this, other)
    fun hashKey() = "${egoId ?: ""}/${networkId ?: ""}/$tag"
}
