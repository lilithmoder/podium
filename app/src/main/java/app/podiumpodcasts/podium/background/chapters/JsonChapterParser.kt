package app.podiumpodcasts.podium.background.chapters

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong

/**
 * Podcasting 2.0 JSON chapters file (`application/json+chapters`), see
 * https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/examples/chapters/jsonChapters.md
 */
@Serializable
private data class JsonChaptersFile(
    val chapters: List<JsonChapter> = emptyList()
)

@Serializable
private data class JsonChapter(
    /** seconds, float precision; the only required field */
    val startTime: Double? = null,
    val endTime: Double? = null,
    val title: String? = null,
    val img: String? = null,
    val url: String? = null,
    /** false marks a "silent" chapter that must not be shown */
    val toc: Boolean? = null
)

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * Parses a JSON chapters file. Chapters with `toc: false` are dropped, since the spec says
 * they must not be shown to the user. The result is passed through [normalizeChapters].
 *
 * @throws kotlinx.serialization.SerializationException on malformed input
 */
fun parseJsonChapters(
    text: String,
    durationMs: Long?
): List<ChapterEntry> {
    val file = json.decodeFromString<JsonChaptersFile>(text)

    val chapters = file.chapters
        .filter { it.toc != false }
        .mapNotNull { chapter ->
            val start = chapter.startTime ?: return@mapNotNull null
            val startMs = (start * 1000).roundToLong()

            ChapterEntry(
                startMs = startMs,
                endMs = chapter.endTime?.let { (it * 1000).roundToLong() }?.takeIf { it > startMs },
                title = chapter.title?.trim()?.takeIf { it.isNotEmpty() },
                url = chapter.url?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = chapter.img?.trim()?.takeIf { it.isNotEmpty() }
            )
        }

    return normalizeChapters(chapters, durationMs)
}
