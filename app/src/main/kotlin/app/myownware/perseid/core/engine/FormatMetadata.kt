package app.myownware.perseid.core.engine

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

private val formatMetadataReader = ObjectMapper().reader()

internal fun readMetadata(json: String): JsonNode = formatMetadataReader.readTree(json)

internal fun JsonNode.mediaOptions(): MediaOptions {
    val formats = path("formats").toList().ifEmpty { listOf(this) }
    return mapMediaFormats(formats.mapNotNull { it.toStreamFormat() })
}

internal fun JsonNode.toStreamFormat(): StreamFormat? {
    val id = path("format_id").asText("").takeIf { it.isNotBlank() } ?: return null
    val exactBytes = positiveLong("filesize")
    val estimate = positiveLong("filesize_approx")
    return StreamFormat(
        id = id,
        extension = path("ext").asText(""),
        videoCodec = path("vcodec").asText(null),
        audioCodec = path("acodec").asText(null),
        height = resolution(),
        size = exactBytes?.let { MediaSize(it, false) } ?: estimate?.let { MediaSize(it, true) },
    )
}

private fun JsonNode.resolution(): Int? {
    val height = path("height").asInt().takeIf { it > 0 } ?: return null
    val width = path("width").asInt().takeIf { it > 0 }
    return minOf(height, width ?: height)
}

internal fun JsonNode.metadataText(key: String): String? = path(key).asText(null)?.takeIf { it.isNotBlank() }

private fun JsonNode.positiveLong(key: String): Long? =
    path(key).takeIf { it.isIntegralNumber && it.canConvertToLong() }?.asLong()?.takeIf { it > 0 }
