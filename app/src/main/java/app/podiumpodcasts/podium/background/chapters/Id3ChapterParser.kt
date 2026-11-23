package app.podiumpodcasts.podium.background.chapters

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.id3.UrlLinkFrame

data class ChapterEntry(
    val startMs: Long,
    val endMs: Long?,
    val title: String?,
    val url: String?
)

/**
 * Extracts chapters from ID3 `CHAP` frames, as decoded by Media3's MP3 extractor into a
 * track format's metadata.
 *
 * Chapters are sorted by start time, de-duplicated by start time and, when [durationMs] is
 * known, limited to the episode's length. Returns an empty list when fewer than two
 * chapters remain, since a single chapter offers nothing to navigate.
 */
@OptIn(UnstableApi::class)
fun parseId3Chapters(
    metadata: Iterable<Metadata>,
    durationMs: Long?
): List<ChapterEntry> {
    val knownDuration = durationMs?.takeIf { it > 0 }

    val chapters = metadata
        .flatMap { entries -> (0 until entries.length()).map { entries.get(it) } }
        .filterIsInstance<ChapterFrame>()
        .map { frame -> frame.toChapterEntry() }
        .filter { it.startMs >= 0 && (knownDuration == null || it.startMs < knownDuration) }
        .sortedBy { it.startMs }
        .distinctBy { it.startMs }

    return if(chapters.size < 2) emptyList() else chapters
}

@OptIn(UnstableApi::class)
private fun ChapterFrame.toChapterEntry(): ChapterEntry {
    val subFrames = (0 until subFrameCount).map { getSubFrame(it) }

    val title = subFrames
        .filterIsInstance<TextInformationFrame>()
        .firstOrNull { it.id == "TIT2" }
        ?.values?.firstOrNull()
        ?.trim()?.takeIf { it.isNotEmpty() }

    val url = subFrames
        .filterIsInstance<UrlLinkFrame>()
        .firstOrNull()
        ?.url
        ?.trim()?.takeIf { it.isNotEmpty() }

    val start = startTimeMs.toLong()
    // unset end times are encoded as 0xFFFFFFFF, i.e. -1 as an Int
    val end = endTimeMs.toLong().takeIf { it > start }

    return ChapterEntry(
        startMs = start,
        endMs = end,
        title = title,
        url = url
    )
}
